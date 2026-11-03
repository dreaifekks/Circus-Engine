package org.circus.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Read-only filesystem adapter for the local original-data audit. */
public final class LocalGameStorage implements GameResources.Storage {
    private final Path root;private final Set<String> paths=new TreeSet<>();
    public LocalGameStorage(Path root)throws IOException{
        this.root=root.toRealPath();try(java.util.stream.Stream<Path> stream=Files.walk(this.root)){
            Iterator<Path> it=stream.iterator();while(it.hasNext()){Path p=it.next();if(!Files.isRegularFile(p))continue;
                if(!p.toRealPath().startsWith(this.root))throw new IOException("Resource link outside root");
                String rel=this.root.relativize(p).toString().replace('\\','/');String lower="/"+rel.toLowerCase(Locale.ROOT)+"/";
                if(!lower.contains("/savedata/")&&!lower.contains("/backup/")&&!lower.contains("/backups/"))paths.add(rel);
            }
        }
    }
    private Path path(String name)throws IOException{return root.resolve(Binary.safePath(name));}
    public Set<String> paths(){return Collections.unmodifiableSet(paths);}
    public byte[] read(String name,int limit)throws IOException{try(InputStream in=Files.newInputStream(path(name))){return Binary.read(in,limit);}}
    public Archive.Source source(String name)throws IOException{
        final Path p=path(name);return new Archive.Source(){
            @Override public Archive.Reader openReader()throws IOException{
                final RandomAccessFile file=new RandomAccessFile(p.toFile(),"r");return new Archive.Reader(){
                    public long size()throws IOException{return file.length();}
                    public synchronized byte[] read(long at,int count)throws IOException{Binary.range(at,count,file.length());byte[] bytes=new byte[count];file.seek(at);file.readFully(bytes);return bytes;}
                    public void close()throws IOException{file.close();}
                };
            }
            public long size()throws IOException{return Files.size(p);}
            public byte[] read(long offset,int length)throws IOException{try(RandomAccessFile file=new RandomAccessFile(p.toFile(),"r")){Binary.range(offset,length,file.length());byte[] b=new byte[length];file.seek(offset);file.readFully(b);return b;}}
            @Override public void copyTo(long offset,long length,OutputStream out)throws IOException{try(RandomAccessFile file=new RandomAccessFile(p.toFile(),"r")){Binary.range(offset,length,file.length());file.seek(offset);byte[] block=new byte[65536];while(length>0){int n=(int)Math.min(block.length,length);file.readFully(block,0,n);out.write(block,0,n);length-=n;}}}
        };
    }
}
