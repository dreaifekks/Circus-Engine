package org.circus.core;
import ios.awt.BufferedImage;
import ios.awt.Native;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.robovm.apple.avfoundation.AVAudioPlayer;
import org.robovm.apple.foundation.NSURL;
/** DesktopMedia in background mode, on AVAudioPlayer and pl_mpeg instead of Java Sound and FFmpeg pipes. Called on the main thread. */
public final class IosMedia {
    private final DesktopEngine engine;private final File cache;
    private final int[] sought=new int[6],heard={-1,-1,-1,-1,-1,-1};private final String[] names=new String[6];
    private final AtomicLongArray request=new AtomicLongArray(6);private final Track[] tracks=new Track[6];private Track olderEffect,loopEffect;
    private final List<Track> fading=new ArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> inbox=new ConcurrentLinkedQueue<>();private final AtomicReference<Exception> failure=new AtomicReference<>();
    private static final ExecutorService LOADERS=Executors.newFixedThreadPool(2,t->{Thread n=new Thread(t,"dc-audio");n.setDaemon(true);return n;});
    private static final AtomicLong SERIAL=new AtomicLong();
    public volatile String lastEvent="";
    private int movieGeneration=-1;private long movieTicket;private boolean movieLoading;private final AtomicLong movieRequest=new AtomicLong();private Movie movie;
    public IosMedia(DesktopEngine e,File cacheDir){engine=e;cache=cacheDir;cache.mkdirs();for(File old:cache.listFiles()!=null?cache.listFiles():new File[0])old.delete();}
    private static final class Track {
        final AVAudioPlayer player;final File file;final boolean loop;long stopAt;
        Track(File f,boolean loop)throws Exception{file=f;this.loop=loop;player=new AVAudioPlayer(new NSURL(f));player.setNumberOfLoops(loop?-1:0);player.prepareToPlay();}
        void volume(float v,int pan){player.setVolume(Math.max(0,Math.min(1,v)));player.setPan(pan==0?0:(float)Math.copySign(1-Math.pow(10,-Math.abs(pan)/2000.0),pan));}
        long positionMicros(){return (long)(player.getCurrentTime()*1000000);}long lengthMicros(){return (long)(player.getDuration()*1000000);}
        void seekMicros(long m){player.setCurrentTime(Math.min(m,Math.max(0,lengthMicros()-1))/1000000.0);}
        boolean finished(){return !player.isPlaying();}boolean loops(){return loop;}
        void stop(){player.stop();file.delete();}
    }
    private File temp(String tag){return new File(cache,tag+"-"+SERIAL.incrementAndGet()+".wav");}
    private void load(int ch)throws IOException{
        final String name=engine.audio(ch);engine.loadedAudio.add(name);final long resume=engine.takeAudioResume(ch);if(resume>0)engine.audioPosition(ch,resume,0);
        final boolean loop=engine.audioLoop(ch);final float volume=engine.volume(ch);final int pan=engine.pan(ch);sought[ch]=engine.audioSeekGeneration(ch);final long id=request.get(ch);names[ch]=name;
        LOADERS.execute(()->{try{
                if(request.get(ch)!=id)return;long t0=System.nanoTime();Xpcm audio=engine.resources.audio(name);File f=temp("a"+ch);
                if(audio.codec.equals("ogg")){int r=Native.oggToWavFile(audio.payload,f.getPath());if(r<=0)throw new IOException("Ogg decode "+r+" "+name);}else try(FileOutputStream o=new FileOutputStream(f)){o.write(audio.wav());}
                final long ms=(System.nanoTime()-t0)/1000000;
                inbox.add(()->{if(request.get(ch)!=id){f.delete();return;}try{Track t=new Track(f,loop);t.volume(volume,pan);if(resume>0)t.seekMicros(resume);boolean ok=t.player.play();tracks[ch]=t;lastEvent="audio ch"+ch+" "+name+" "+audio.codec+" "+ms+"ms vol="+volume+" play="+ok;}catch(Exception e){f.delete();failure.compareAndSet(null,e);}});
            }catch(FileNotFoundException e){lastEvent="audio missing "+name;inbox.add(()->{if(request.get(ch)==id)engine.audioCompleted(ch,engine.audioGeneration(ch));});}
            catch(Exception e){if(request.get(ch)==id)failure.compareAndSet(null,e);}});
    }
    /** 4281D0, 428216, 42430A: a looping effect keeps its own channel; a new effect cuts off only the older of two still sounding. */
    private void keepEffectRinging(){Track playing=tracks[1];if(!engine.twoEffectChannels())return;if(engine.audio(1).isEmpty()||!engine.audioActive(1)){stopRinging();return;}if(engine.audioLoop(1)&&loopEffect!=null){loopEffect.stop();loopEffect=null;}if(playing==null||playing.finished())return;tracks[1]=null;if(playing.loops()){if(loopEffect!=null)loopEffect.stop();loopEffect=playing;return;}if(olderEffect!=null)olderEffect.stop();olderEffect=playing;}
    private void stopRinging(){if(olderEffect!=null){olderEffect.stop();olderEffect=null;}if(loopEffect!=null){loopEffect.stop();loopEffect=null;}}
    public void rebase()throws IOException{for(int ch=0;ch<6;ch++){int g=engine.audioGeneration(ch);if(tracks[ch]!=null&&engine.audioActive(ch)&&engine.audio(ch).equals(names[ch])){heard[ch]=g;engine.takeAudioResume(ch);}else{stop(ch);heard[ch]=-1;}}movieGeneration=-1;}
    public boolean inBackground(){return true;}
    private void syncTrack(int ch)throws Exception{
        if(heard[ch]!=engine.audioGeneration(ch)){if(ch==1)keepEffectRinging();stop(ch);heard[ch]=engine.audioGeneration(ch);if(!engine.audio(ch).isEmpty()&&engine.audioActive(ch))load(ch);return;}
        Track t=tracks[ch];if(t==null)return;
        if(sought[ch]!=engine.audioSeekGeneration(ch)){sought[ch]=engine.audioSeekGeneration(ch);t.seekMicros(t.lengthMicros()*Math.max(0,Math.min(100,engine.audioSeekPercent(ch)))/100);}
        engine.audioPosition(ch,t.positionMicros(),t.lengthMicros());t.volume(engine.volume(ch),engine.pan(ch));
        if(!engine.audioActive(ch)){stop(ch);return;}if(!engine.audioLoop(ch)&&t.finished()){engine.audioCompleted(ch,heard[ch]);stop(ch);}
    }
    public void sync()throws Exception{
        Runnable r;while((r=inbox.poll())!=null)r.run();Exception failed=failure.getAndSet(null);if(failed!=null)throw new IOException("Audio playback failed",failed);
        long now=System.currentTimeMillis();for(Iterator<Track> it=fading.iterator();it.hasNext();){Track t=it.next();if(now>=t.stopAt){t.stop();it.remove();}}
        for(int ch=0;ch<6;ch++)syncTrack(ch);
        if(!engine.movie().isEmpty()&&movieGeneration!=engine.movieGeneration()){movieGeneration=engine.movieGeneration();movieTicket=engine.ticket();for(int ch=0;ch<6;ch++)stop(ch);loadMovie();}
        if(movie!=null){if(movie.error!=null)throw new IOException("Movie playback failed",movie.error);if(movie.videoDone&&movie.audioFinished())finishMovie();}
    }
    public BufferedImage movieFrame(){return movie==null?null:movie.frame;}
    public boolean playingMovie(){return movie!=null||movieLoading;}
    public void finishMovie()throws IOException{dropMovie();engine.mediaCompleted(movieTicket);}
    private void loadMovie(){final String name=engine.movie();final long id=movieRequest.incrementAndGet();movieLoading=true;lastEvent="movie loading "+name;
        LOADERS.execute(()->{try{long t0=System.nanoTime();File video=new File(cache,"movie-"+id+".mpg"),wav=new File(cache,"movie-"+id+".wav");try(OutputStream out=new FileOutputStream(video)){engine.resources.files.copy(name,out,1024L*1024*1024);}int samples=Native.mpegAudioToWav(video.getPath(),wav.getPath());long ms=(System.nanoTime()-t0)/1000000;
                inbox.add(()->{if(!movieLoading||movieRequest.get()!=id){video.delete();wav.delete();return;}try{movie=new Movie(video,wav,samples>0);movieLoading=false;lastEvent="movie "+name+" prepared "+ms+"ms audioSamples="+samples;}catch(Exception e){failure.compareAndSet(null,e);}});
            }catch(Exception e){if(movieRequest.get()==id)failure.compareAndSet(null,e);}});}
    private void dropMovie(){movieRequest.incrementAndGet();movieLoading=false;if(movie!=null){movie.close();movie=null;}}
    private void stop(int n){request.incrementAndGet(n);if(tracks[n]!=null){if(n==0){Track t=tracks[n];t.player.setVolume(0,.25);t.stopAt=System.currentTimeMillis()+300;fading.add(t);}else tracks[n].stop();tracks[n]=null;}}
    public void reset(){for(int n=0;n<6;n++)stop(n);Arrays.fill(heard,-1);movieGeneration=-1;dropMovie();stopRinging();}
    public void close(){reset();}
    /** Video follows the audio clock; audio was decoded to a file up front. One published frame, two buffers. */
    private static final class Movie {
        final File video,wav;final AVAudioPlayer audio;volatile BufferedImage frame;volatile Exception error;volatile boolean closed,videoDone;volatile long frames,dropped;
        Movie(File v,File w,boolean hasAudio)throws Exception{video=v;wav=w;audio=hasAudio?new AVAudioPlayer(new NSURL(w)):null;if(audio!=null){audio.prepareToPlay();audio.play();}Thread t=new Thread(this::decode,"dc-movie");t.setDaemon(true);t.start();}
        boolean audioFinished(){return audio==null||!audio.isPlaying();}
        private void decode(){long h=0;try{h=Native.mpegOpenFile(video.getPath());if(h==0)throw new IOException("Cannot open "+video.getName());int[] size=Native.mpegSize(h);BufferedImage[] buffers={new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_RGB),new BufferedImage(size[0],size[1],BufferedImage.TYPE_INT_RGB)};int which=0;long start=System.nanoTime();boolean skip=false;
                while(!closed){BufferedImage b=buffers[which];double at=Native.mpegNextFrame(h,skip?null:b.data,size[0]*4);if(at<0)break;double clock=audio!=null&&audio.isPlaying()?audio.getCurrentTime():(System.nanoTime()-start)/1e9;
                    if(skip){dropped++;}else{while(!closed&&at>clock+.005){Thread.sleep(Math.max(1,(long)((at-clock)*1000)));clock=audio!=null&&audio.isPlaying()?audio.getCurrentTime():(System.nanoTime()-start)/1e9;}frame=b;which^=1;frames++;}
                    skip=at<clock-.08;}
                videoDone=true;}catch(Exception e){error=e;}finally{if(h!=0)Native.mpegClose(h);video.delete();}}
        void close(){closed=true;if(audio!=null)audio.stop();wav.delete();}
    }
}
