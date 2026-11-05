package org.circus.core;
import java.io.*;import java.nio.*;import java.nio.charset.*;import java.util.zip.*;
/** Table-driven double-byte charsets: the iOS class library ships neither CP932 nor GBK. Tables come from tools/gentmp. */
final class TableCharset extends Charset {
    final char[] dec=new char[65536],enc=new char[65536];private final int a0,a1,b0,b1;
    static final TableCharset CP932=new TableCharset("windows-31j","cp932.bin.gz",0x81,0x9F,0xE0,0xFC);
    private static TableCharset gbk;
    static Charset named(String n){if(n.equals("windows-31j"))return CP932;if(n.equals("GBK")){if(gbk==null)gbk=new TableCharset("GBK","gbk.bin.gz",0x81,0xFE,0x81,0xFE);return gbk;}return Charset.forName(n);}
    private TableCharset(String name,String table,int a0,int a1,int b0,int b1){super(name,new String[0]);this.a0=a0;this.a1=a1;this.b0=b0;this.b1=b1;
        try(InputStream raw=TableCharset.class.getResourceAsStream(table)){if(raw==null)throw new IOException("Missing charset table "+table);DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)));for(int i=0;i<65536;i++)dec[i]=in.readChar();for(int i=0;i<65536;i++)enc[i]=in.readChar();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @Override public boolean contains(Charset c){return c==this||"US-ASCII".equals(c.name());}
    private boolean lead(int b){return b>=a0&&b<=a1||b>=b0&&b<=b1;}
    @Override public CharsetDecoder newDecoder(){return new CharsetDecoder(this,1f,1f){@Override protected CoderResult decodeLoop(ByteBuffer in,CharBuffer out){while(in.hasRemaining()){int p=in.position(),b=in.get(p)&255,n=1,v=b;if(lead(b)){if(in.remaining()<2)return CoderResult.UNDERFLOW;v=b<<8|in.get(p+1)&255;n=2;}char c=dec[v];if(c==0xFFFD)return CoderResult.unmappableForLength(n);if(!out.hasRemaining())return CoderResult.OVERFLOW;out.put(c);in.position(p+n);}return CoderResult.UNDERFLOW;}};}
    @Override public CharsetEncoder newEncoder(){return new CharsetEncoder(this,1.1f,2f){@Override protected CoderResult encodeLoop(CharBuffer in,ByteBuffer out){while(in.hasRemaining()){char c=in.get(in.position());int v=enc[c];if(v==0&&c!=0)return CoderResult.unmappableForLength(1);int n=v>255?2:1;if(out.remaining()<n)return CoderResult.OVERFLOW;if(n==2)out.put((byte)(v>>8));out.put((byte)v);in.position(in.position()+1);}return CoderResult.UNDERFLOW;}};}
}
