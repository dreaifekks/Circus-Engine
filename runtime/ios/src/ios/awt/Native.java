package ios.awt;
import org.robovm.rt.VM;
import org.robovm.rt.bro.Bro;
import org.robovm.rt.bro.annotation.*;
import org.robovm.rt.bro.ptr.BytePtr;
/** The C half of this layer, in native/glue.c and native/ogg_wav.c. */
@Library(Library.INTERNAL)
public final class Native {
    static{Bro.bind(Native.class);}
    private Native(){}
    @Bridge(symbol="circus_font_metrics") private static native void metrics(BytePtr name,float size,@Pointer long out);
    @Bridge(symbol="circus_glyph") private static native float glyph0(BytePtr name,float size,int cp,@Pointer long mask,int w,int h,float x,float baseline);
    @Bridge(symbol="circus_ogg_to_wav") private static native int oggToWav(@Pointer long ogg,int length,BytePtr path);
    @Bridge(symbol="circus_mpeg_open") private static native @Pointer long mpegOpen(BytePtr path);
    @Bridge(symbol="circus_mpeg_size") private static native void mpegSize(@Pointer long h,@Pointer long out);
    @Bridge(symbol="circus_mpeg_next") private static native double mpegNext(@Pointer long h,@Pointer long bgra,int stride);
    @Bridge(symbol="circus_mpeg_close") public static native void mpegClose(@Pointer long h);
    @Bridge(symbol="circus_mpeg_audio_wav") private static native int mpegAudio(BytePtr path,BytePtr wav);
    private static BytePtr z(String s){return BytePtr.toBytePtrZ(s,java.nio.charset.StandardCharsets.UTF_8);}
    static void fontMetrics(String face,float size,float[] out){metrics(z(face),size,VM.getArrayValuesAddress(out));}
    static float glyph(String face,float size,int cp,byte[] mask,int w,int h,float x,float baseline){return glyph0(z(face),size,cp,mask==null?0:VM.getArrayValuesAddress(mask),w,h,x,baseline);}
    public static int oggToWavFile(byte[] ogg,String path){return oggToWav(VM.getArrayValuesAddress(ogg),ogg.length,z(path));}
    public static long mpegOpenFile(String path){return mpegOpen(z(path));}
    public static int[] mpegSize(long h){int[] o=new int[2];mpegSize(h,VM.getArrayValuesAddress(o));return o;}
    /** Time of the frame, or a negative number at the end. A null buffer skips colour conversion. */
    public static double mpegNextFrame(long h,int[] bgra,int stride){return mpegNext(h,bgra==null?0:VM.getArrayValuesAddress(bgra),stride);}
    public static int mpegAudioToWav(String path,String wav){return mpegAudio(z(path),z(wav));}
}
