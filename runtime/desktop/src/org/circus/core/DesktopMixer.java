package org.circus.core;

import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.*;

/**
 * One output line that stays open, mixed in software, for the Mac app.
 * A Clip per sound wakes the audio device on every start (260-390 ms on AirPods), holds the Java Sound
 * lock while it does, and can hold it for 2 s while closing. Adding a Track here takes neither path.
 */
final class DesktopMixer {
    static final int RATE=44100,BLOCK_FRAMES=256,BUFFER_BYTES=8192;
    private static DesktopMixer shared;
    private final CopyOnWriteArrayList<Track> tracks=new CopyOnWriteArrayList<>();
    private final float[] left=new float[BLOCK_FRAMES],right=new float[BLOCK_FRAMES];
    private volatile Exception failure;

    static synchronized DesktopMixer shared(){
        if(shared==null){
            shared=new DesktopMixer();
            Thread output=new Thread(shared::run,"dc-audio-mixer");
            output.setDaemon(true);output.setPriority(Thread.MAX_PRIORITY);output.start();
        }
        return shared;
    }

    Exception failure(){return failure;}
    void play(Track track){tracks.add(track);}

    private void run(){
        AudioFormat format=new AudioFormat(RATE,16,2,true,false);
        try{
            SourceDataLine line=AudioSystem.getSourceDataLine(format);
            line.open(format,BUFFER_BYTES);line.start();
            byte[] block=new byte[BLOCK_FRAMES*4];
            while(true){render(block);line.write(block,0,block.length);}
        }catch(LineUnavailableException|RuntimeException e){failure=e;}
    }

    /** Fills a block of 16-bit stereo. Needs no audio device, so it can be tested directly. */
    void render(byte[] block){
        java.util.Arrays.fill(left,0);java.util.Arrays.fill(right,0);
        for(Track track:tracks)if(!track.mix(left,right))tracks.remove(track);
        for(int i=0;i<BLOCK_FRAMES;i++){
            int l=clamp(left[i]),r=clamp(right[i]);
            block[i*4]=(byte)l;block[i*4+1]=(byte)(l>>8);block[i*4+2]=(byte)r;block[i*4+3]=(byte)(r>>8);
        }
    }
    private static int clamp(float sample){return Math.max(-32768,Math.min(32767,(int)(sample*32767)));}

    static final class Track {
        private final byte[] pcm;private final int rate,channels,frameBytes,frames;private final boolean wide,loop;private final double step;
        private volatile double position,seek=-1;
        private volatile float targetLeft=1,targetRight=1;
        private float gainLeft=1,gainRight=1;
        private volatile boolean stopping,finished;private volatile int fadeBlocks;

        Track(byte[] pcm,int rate,int channels,int bits,boolean loop){
            this.pcm=pcm;this.rate=rate;this.channels=channels;wide=bits==16;this.loop=loop;
            frameBytes=channels*(wide?2:1);frames=pcm.length/frameBytes;step=rate/(double)RATE;
        }
        /** Same curve as the MASTER_GAIN and BALANCE controls of the Clip it replaces. */
        void volume(float volume,int pan){
            float balance=pan==0?0:Math.copySign((float)(1-Math.pow(10,-Math.abs(pan)/2000.0)),pan);
            float gain=Math.max(0,Math.min(1,volume));
            targetLeft=gain*(balance>0?1-balance:1);targetRight=gain*(balance<0?1+balance:1);
        }
        /** The first block plays at this volume instead of ramping up from silence. */
        void startAtVolume(){gainLeft=targetLeft;gainRight=targetRight;}
        void seekMicros(long micros){seek=Math.max(0,Math.min(frames-1,micros*(double)rate/1000000));}
        long positionMicros(){return (long)(position*1000000/rate);}
        long lengthMicros(){return frames*1000000L/rate;}
        long framePosition(){return (long)position;}
        boolean finished(){return finished;}
        void stop(){stopping=true;}
        boolean loops(){return loop;}
        /** 429099: a replaced track falls away over about 120 ms instead of cutting off. */
        void fadeOut(){fadeBlocks=20;}

        private float sample(int frame,int channel){
            int at=frame*frameBytes+(channels>1?channel:0)*(wide?2:1);
            return wide?(short)((pcm[at]&255)|pcm[at+1]<<8)/32768f:((pcm[at]&255)-128)/128f;
        }
        /** Volume ramps across the block so that fades and stops do not click. False once the track is done. */
        private boolean mix(float[] left,float[] right){
            double jump=seek;if(jump>=0){position=jump;seek=-1;}
            double at=position;
            float endLeft=stopping?0:targetLeft,endRight=stopping?0:targetRight;
            int fade=fadeBlocks;if(fade>0){float keep=(fade-1)/(float)fade;endLeft=gainLeft*keep;endRight=gainRight*keep;fadeBlocks=fade-1;if(fade==1)stopping=true;}
            for(int i=0;i<BLOCK_FRAMES;i++){
                if(at>=frames){
                    if(!loop||frames==0){finished=true;break;}
                    at-=frames;
                }
                int frame=(int)at;float l=sample(frame,0),r=sample(frame,1);
                if(step!=1){
                    int next=frame+1<frames?frame+1:loop?0:frame;float part=(float)(at-frame);
                    l+=(sample(next,0)-l)*part;r+=(sample(next,1)-r)*part;
                }
                float through=i/(float)BLOCK_FRAMES;
                left[i]+=l*(gainLeft+(endLeft-gainLeft)*through);
                right[i]+=r*(gainRight+(endRight-gainRight)*through);
                at+=step;
            }
            position=at;gainLeft=endLeft;gainRight=endRight;
            return !finished&&!stopping;
        }
    }
}
