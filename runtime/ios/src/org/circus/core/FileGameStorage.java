package org.circus.core;
import java.io.*;
import java.util.*;
/** java.io only: older class libraries have no java.nio.file. */
public final class FileGameStorage implements GameResources.Storage {
    private final File root;private final Set<String> paths=new TreeSet<>();
    public FileGameStorage(File root)throws IOException{this.root=root.getCanonicalFile();walk(this.root,"");}
    private void walk(File d,String prefix)throws IOException{File[] all=d.listFiles();if(all==null)throw new IOException("Cannot list "+d);for(File f:all){String rel=prefix+f.getName();if(f.isDirectory())walk(f,rel+"/");else{String lower="/"+rel.toLowerCase(Locale.ROOT)+"/";if(!lower.contains("/savedata/")&&!lower.contains("/backup/")&&!lower.contains("/backups/"))paths.add(rel);}}}
    private File file(String name)throws IOException{return new File(root,Binary.safePath(name));}
    public Set<String> paths(){return Collections.unmodifiableSet(paths);}
    public byte[] read(String name,int limit)throws IOException{try(InputStream in=new FileInputStream(file(name))){return Binary.read(in,limit);}}
    public Archive.Source source(String name)throws IOException{final File p=file(name);return new Archive.Source(){
        @Override public Archive.Reader openReader()throws IOException{final RandomAccessFile f=new RandomAccessFile(p,"r");return new Archive.Reader(){
            public long size()throws IOException{return f.length();}
            public synchronized byte[] read(long at,int count)throws IOException{Binary.range(at,count,f.length());byte[] b=new byte[count];f.seek(at);f.readFully(b);return b;}
            public void close()throws IOException{f.close();}};}
        public long size(){return p.length();}
        public byte[] read(long o,int n)throws IOException{try(RandomAccessFile f=new RandomAccessFile(p,"r")){Binary.range(o,n,f.length());byte[] b=new byte[n];f.seek(o);f.readFully(b);return b;}}
    };}
}
