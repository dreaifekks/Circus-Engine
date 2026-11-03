package org.circus.core;

import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sound.sampled.*;

/** Java Sound output and bounded FFmpeg pipes. No Windows game binaries are loaded. */
public final class DesktopMedia implements AutoCloseable {
    private final DesktopEngine engine;
    private final int[] sought=new int[6];
    private final int[] heard={-1,-1,-1,-1,-1,-1};
    private final Clip[] clips=new Clip[6];private Movie movie;
    private int movieGeneration=-1;private long movieTicket;
    private boolean movieLoading;private final AtomicLong movieRequest=new AtomicLong();private final AtomicReference<Movie> movieReady=new AtomicReference<>();
    public final Set<String> decodedMovies=new TreeSet<>();
    // The Mac app reads and decodes on these daemon threads, and plays through DesktopMixer.
    private static final ThreadFactory DAEMON=task->{Thread t=new Thread(task,"dc-audio");t.setDaemon(true);return t;};
    private static final ExecutorService LOADERS=Executors.newFixedThreadPool(3,DAEMON),RELEASERS=Executors.newCachedThreadPool(DAEMON);
    private boolean background;
    private final AtomicLongArray request=new AtomicLongArray(6);
    private final AtomicReferenceArray<Loaded> ready=new AtomicReferenceArray<>(6);
    private final AtomicReference<Exception> failure=new AtomicReference<>();
    private final DesktopMixer.Track[] tracks=new DesktopMixer.Track[6];private DesktopMixer.Track olderEffect,loopEffect;private final String[] names=new String[6];
    private static final class Loaded {final long id;final DesktopMixer.Track track;Loaded(long id,DesktopMixer.Track track){this.id=id;this.track=track;}}
    public DesktopMedia(DesktopEngine e){engine=e;}
    /** Audits keep the default: every sound is playing when sync returns. */
    public void playInBackground(){background=true;}
    private static byte[] pcm(Xpcm audio)throws Exception{return audio.codec.equals("ogg")?decodeOgg(audio.payload):audio.payload;}
    private static Clip open(Xpcm audio,byte[] pcm)throws Exception{
        AudioFormat format=audio.codec.equals("ogg")?new AudioFormat(44100,16,2,true,false):new AudioFormat(audio.sampleRate,audio.bits,audio.channels,audio.bits==16,false);
        Clip clip=AudioSystem.getClip();try{clip.open(format,pcm,0,pcm.length);}catch(Exception e){clip.close();throw e;}return clip;
    }
    /** Runs on the event thread: everything read from the engine is read here, the slow work happens on a loader. */
    private void load(int ch)throws IOException{
        final String name=engine.audio(ch);engine.loadedAudio.add(name);
        final long resume=engine.takeAudioResume(ch);if(resume>0)engine.audioPosition(ch,resume,0);
        final boolean loop=engine.audioLoop(ch);final float volume=engine.volume(ch);final int pan=engine.pan(ch);sought[ch]=engine.audioSeekGeneration(ch);
        // Unity bundles cannot be exercised here without game data, so their reads stay on this thread.
        final Xpcm read=engine.resources instanceof UnityCircusResources?engine.resources.audio(name):null;
        final long id=request.get(ch);names[ch]=name;
        LOADERS.execute(()->{
            try{
                if(request.get(ch)!=id)return;
                Xpcm audio=read!=null?read:engine.resources.audio(name);byte[] pcm=pcm(audio);
                if(request.get(ch)!=id)return;
                DesktopMixer.Track track=audio.codec.equals("ogg")?new DesktopMixer.Track(pcm,44100,2,16,loop):new DesktopMixer.Track(pcm,audio.sampleRate,audio.channels,audio.bits,loop);
                track.volume(volume,pan);track.startAtVolume();if(resume>0)track.seekMicros(resume);
                DesktopMixer.shared().play(track);publish(ch,new Loaded(id,track));
            }catch(Exception e){if(request.get(ch)==id)failure.compareAndSet(null,e);}
        });
    }
    /** Loaders can finish out of order: the newest request stays, and the other track must not keep playing. */
    private void publish(int ch,Loaded mine){
        Loaded loser=mine;
        synchronized(ready){Loaded held=ready.get(ch);if(held==null||held.id<mine.id){ready.set(ch,mine);loser=held;}}
        if(loser!=null)loser.track.stop();
    }
    /**
     * 4281D0, 428216, 42430A: DC2PC.exe keeps a looping effect on its own channel and plays one shots on another.
     * A stop request ends all of them; otherwise a new effect cuts off only the older of two still sounding.
     */
    private void keepEffectRinging(){
        DesktopMixer.Track playing=tracks[1];
        if(!PcHost.followsExe||!engine.resources.profileId.equals("dc2pc-5769"))return;
        if(engine.audio(1).isEmpty()||!engine.audioActive(1)){stopRinging();return;}
        if(engine.audioLoop(1)&&loopEffect!=null){loopEffect.stop();loopEffect=null;}
        if(playing==null||playing.finished())return;
        tracks[1]=null;
        if(playing.loops()){if(loopEffect!=null)loopEffect.stop();loopEffect=playing;return;}
        if(olderEffect!=null)olderEffect.stop();
        olderEffect=playing;
    }
    private void stopRinging(){if(olderEffect!=null){olderEffect.stop();olderEffect=null;}if(loopEffect!=null){loopEffect.stop();loopEffect=null;}}
    /** A closed menu resumes the same story, so whatever still matches keeps sounding instead of restarting. */
    public void rebase()throws IOException{
        for(int ch=0;ch<6;ch++){
            int generation=engine.audioGeneration(ch);
            if(tracks[ch]!=null&&engine.audioActive(ch)&&engine.audio(ch).equals(names[ch])){heard[ch]=generation;engine.takeAudioResume(ch);}
            else{stop(ch);heard[ch]=-1;}
        }
        movieGeneration=-1;
    }
    public boolean inBackground(){return background;}
    private void syncTrack(int ch)throws Exception{
        if(heard[ch]!=engine.audioGeneration(ch)){
            if(ch==1)keepEffectRinging();
            stop(ch);heard[ch]=engine.audioGeneration(ch);
            if(!engine.audio(ch).isEmpty()&&engine.audioActive(ch))load(ch);
            return;
        }
        Loaded loaded=ready.getAndSet(ch,null);if(loaded!=null){if(loaded.id==request.get(ch))tracks[ch]=loaded.track;else loaded.track.stop();}
        DesktopMixer.Track track=tracks[ch];if(track==null)return;
        if(sought[ch]!=engine.audioSeekGeneration(ch)){sought[ch]=engine.audioSeekGeneration(ch);track.seekMicros(track.lengthMicros()*Math.max(0,Math.min(100,engine.audioSeekPercent(ch)))/100);}
        engine.audioPosition(ch,track.positionMicros(),track.lengthMicros());track.volume(engine.volume(ch),engine.pan(ch));
        if(!engine.audioActive(ch)){stop(ch);return;}
        if(!engine.audioLoop(ch)&&track.finished()){engine.audioCompleted(ch,heard[ch]);stop(ch);}
    }
    public static String ffmpeg()throws IOException{String executable=System.getProperty("dc.ffmpeg",System.getenv("DC_FFMPEG"));if(executable==null||executable.isEmpty())throw new IOException("FFmpeg is required for Ogg/movie playback. Set DC_FFMPEG to its executable.");return executable;}
    private static Process process(String... args)throws IOException{List<String> command=new ArrayList<>();command.add(ffmpeg());Collections.addAll(command,"-nostdin","-hide_banner","-loglevel","error");Collections.addAll(command,args);Process p=new ProcessBuilder(command).start();Thread errors=new Thread(()->{try(InputStream in=p.getErrorStream()){byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){if(total<16384)System.err.write(b,0,Math.min(n,16384-total));total+=n;}}catch(IOException ignored){}},"dc-media-diagnostics");errors.setDaemon(true);errors.start();return p;}
    public static byte[] decodeOgg(byte[] ogg)throws Exception{
        Process p=process("-i","pipe:0","-vn","-f","s16le","-ac","2","-ar","44100","pipe:1");
        final IOException[] error={null};Thread input=new Thread(()->{try(OutputStream out=p.getOutputStream()){out.write(ogg);}catch(IOException e){error[0]=e;}},"dc-ogg-input");input.setDaemon(true);input.start();
        try{byte[] pcm=Binary.read(p.getInputStream(),128*1024*1024);int exit=p.waitFor();input.join();if(exit!=0||error[0]!=null)throw new IOException("Ogg decoder failed ("+exit+")",error[0]);return pcm;}finally{p.destroy();}
    }
    public void sync()throws Exception{
        Exception failed=failure.getAndSet(null);if(failed==null&&background)failed=DesktopMixer.shared().failure();if(failed!=null)throw new IOException("Audio playback failed",failed);
        for(int ch=0;ch<6;ch++){
            if(background){syncTrack(ch);continue;}
            if(heard[ch]!=engine.audioGeneration(ch)){
                stop(ch);heard[ch]=engine.audioGeneration(ch);if(engine.audio(ch).isEmpty()||!engine.audioActive(ch))continue;
                Xpcm audio=engine.resources.audio(engine.audio(ch));engine.loadedAudio.add(engine.audio(ch));byte[] pcm=pcm(audio);
                if(engine.audit){engine.audioCompleted(ch,heard[ch]);continue;}
                Clip clip=open(audio,pcm);clips[ch]=clip;long resume=engine.takeAudioResume(ch);if(resume>0)clip.setMicrosecondPosition(Math.min(resume,Math.max(0,clip.getMicrosecondLength()-1)));sought[ch]=engine.audioSeekGeneration(ch);if(engine.audioLoop(ch))clip.loop(Clip.LOOP_CONTINUOUSLY);else clip.start();
            }
            Clip clip=clips[ch];if(clip!=null){if(sought[ch]!=engine.audioSeekGeneration(ch)){sought[ch]=engine.audioSeekGeneration(ch);clip.setMicrosecondPosition(clip.getMicrosecondLength()*Math.max(0,Math.min(100,engine.audioSeekPercent(ch)))/100);}engine.audioPosition(ch,clip.getMicrosecondPosition(),clip.getMicrosecondLength());gain(clip,engine.volume(ch));balance(clip,engine.pan(ch));if(!engine.audioActive(ch)){stop(ch);continue;}if(!engine.audioLoop(ch)&&!clip.isRunning()&&clip.getFramePosition()>=clip.getFrameLength()){engine.audioCompleted(ch,heard[ch]);stop(ch);}}
        }
        if(!engine.movie().isEmpty()&&movieGeneration!=engine.movieGeneration()){
            movieGeneration=engine.movieGeneration();movieTicket=engine.ticket();for(int ch=0;ch<6;ch++)stop(ch);
            if(engine.audit){engine.resources.files.slice(engine.movie(),0,4);decodedMovies.add(engine.movie());engine.mediaCompleted(movieTicket);return;}
            decodedMovies.add(engine.movie());
            if(background)loadMovie();else movie=new Movie(engine.resources.files,engine.movie(),engine.width,engine.height,0);
        }
        Movie opened=movieReady.getAndSet(null);if(opened!=null){if(movieLoading&&opened.id==movieRequest.get()){movie=opened;movieLoading=false;}else closeMovie(opened);}
        if(movie!=null){if(movie.error!=null)throw new IOException("Movie playback failed",movie.error);if(movie.videoDone&&movie.audioDone)finishMovie();}
    }
    private static void balance(Line line,int pan){FloatControl.Type type=line.isControlSupported(FloatControl.Type.BALANCE)?FloatControl.Type.BALANCE:FloatControl.Type.PAN;if(line.isControlSupported(type)){FloatControl c=(FloatControl)line.getControl(type);float value=pan==0?0:Math.copySign((float)(1-Math.pow(10,-Math.abs(pan)/2000.0)),pan);c.setValue(Math.max(c.getMinimum(),Math.min(c.getMaximum(),value)));}}
    private static void gain(Line line,float value){if(line.isControlSupported(FloatControl.Type.MASTER_GAIN)){FloatControl gain=(FloatControl)line.getControl(FloatControl.Type.MASTER_GAIN);float db=value<=0?gain.getMinimum():(float)(20*Math.log10(value));gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),db)));}}
    public BufferedImage movieFrame(){return movie==null?null:movie.frame;}
    public long audioFrames(int ch){return background?(tracks[ch]==null?0:tracks[ch].framePosition()):clips[ch]==null?0:clips[ch].getLongFramePosition();}
    public long movieAudioFrames(){return movie==null||movie.line==null?0:movie.line.getLongFramePosition();}
    public boolean playingMovie(){return movie!=null||movieLoading;}
    public void finishMovie()throws IOException{dropMovie();engine.mediaCompleted(movieTicket);}
    private void loadMovie(){
        final GameResources files=engine.resources.files;final String name=engine.movie();final int width=engine.width,height=engine.height;
        final long id=movieRequest.incrementAndGet();movieLoading=true;
        LOADERS.execute(()->{
            try{Movie opened=new Movie(files,name,width,height,id);Movie loser=movieReady.getAndSet(opened);if(loser!=null)closeMovie(loser);}
            catch(Exception e){if(movieRequest.get()==id)failure.compareAndSet(null,e);}
        });
    }
    private void closeMovie(Movie m){if(background)RELEASERS.execute(m::close);else m.close();}
    private void dropMovie(){movieRequest.incrementAndGet();movieLoading=false;if(movie!=null){closeMovie(movie);movie=null;}}
    private void stop(int n){
        request.incrementAndGet(n);Loaded loaded=ready.getAndSet(n,null);if(loaded!=null)loaded.track.stop();
        if(tracks[n]!=null){if(n==0)tracks[n].fadeOut();else tracks[n].stop();tracks[n]=null;}
        if(clips[n]!=null){clips[n].stop();clips[n].close();clips[n]=null;}
    }
    public void reset(){for(int n=0;n<6;n++)stop(n);Arrays.fill(heard,-1);movieGeneration=-1;dropMovie();stopRinging();}
    public void close(){reset();}
    /** Two decoder pipes, at most one published video frame; neither pipe accumulates the movie. */
    private static final class Movie implements AutoCloseable {
        final long id;final Path file;final Process video,audio;volatile SourceDataLine line;
        volatile BufferedImage frame;volatile Exception error;volatile boolean closed,videoDone,audioDone;
        Movie(GameResources resources,String name,int width,int height,long id)throws Exception{
            this.id=id;
            Path dir=Paths.get(System.getProperty("dc.cacheRoot","build/desktop/media-cache"));Files.createDirectories(dir);file=Files.createTempFile(dir,"movie-",".mpg");
            try(OutputStream out=Files.newOutputStream(file)){resources.copy(name,out,1024L*1024*1024);}
            Process v=null,a=null;try{v=process("-i",file.toAbsolutePath().toString(),"-an","-vf","scale="+width+":"+height+":force_original_aspect_ratio=decrease,pad="+width+":"+height+":(ow-iw)/2:(oh-ih)/2,fps=30","-pix_fmt","bgr24","-f","rawvideo","pipe:1");a=process("-i",file.toAbsolutePath().toString(),"-vn","-ac","2","-ar","44100","-f","s16le","pipe:1");}catch(Exception e){if(v!=null)v.destroy();if(a!=null)a.destroy();Files.deleteIfExists(file);throw e;}video=v;audio=a;
            Thread at=new Thread(()->{try{AudioFormat format=new AudioFormat(44100,16,2,true,false);SourceDataLine sink=AudioSystem.getSourceDataLine(format);line=sink;sink.open(format,32768);sink.start();try(InputStream in=new AudioInputStream(audio.getInputStream(),format,AudioSystem.NOT_SPECIFIED)){byte[] b=new byte[8192];int n;while(!closed&&(n=in.read(b))>=0)for(int off=0;off<n&&!closed;)off+=sink.write(b,off,n-off);}if(!closed){sink.drain();if(audio.waitFor()!=0)throw new IOException("Movie audio decoder failed");}}catch(Exception e){if(!closed)error=e;}finally{if(line!=null)line.close();audioDone=true;}},"dc-movie-audio");
            Thread vt=new Thread(()->{try(InputStream in=video.getInputStream()){long start=System.nanoTime();int count=0;while(!closed){BufferedImage img=new BufferedImage(width,height,BufferedImage.TYPE_3BYTE_BGR);byte[] bytes=((DataBufferByte)img.getRaster().getDataBuffer()).getData();int off=0,n;while(off<bytes.length&&(n=in.read(bytes,off,bytes.length-off))>=0)off+=n;if(off==0)break;if(off!=bytes.length)throw new EOFException("Truncated movie frame");long target=start+(count++*1000000000L/30);while(!closed&&System.nanoTime()<target)Thread.sleep(Math.min(10,Math.max(1,(target-System.nanoTime())/1000000)));if(!closed)frame=img;}if(!closed&&video.waitFor()!=0)throw new IOException("Movie video decoder failed");}catch(Exception e){if(!closed)error=e;}finally{videoDone=true;}},"dc-movie-video");
            at.setDaemon(true);vt.setDaemon(true);at.start();vt.start();
        }
        public void close(){closed=true;video.destroy();audio.destroy();if(line!=null){line.stop();line.flush();line.close();}try{Files.deleteIfExists(file);}catch(IOException e){file.toFile().deleteOnExit();}}
    }
}
