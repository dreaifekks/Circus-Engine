package org.circus.core;

/** Self-authored samples through the mix path, without an audio device. */
public final class DesktopMixerTests {
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] block(){return new byte[DesktopMixer.BLOCK_FRAMES*4];}
    private static int left(byte[] b,int frame){return (short)((b[frame*4]&255)|b[frame*4+1]<<8);}
    private static int right(byte[] b,int frame){return (short)((b[frame*4+2]&255)|b[frame*4+3]<<8);}
    private static byte[] level(int frames,int value){byte[] p=new byte[frames*4];for(int i=0;i<frames;i++){p[i*4]=(byte)value;p[i*4+1]=(byte)(value>>8);p[i*4+2]=(byte)value;p[i*4+3]=(byte)(value>>8);}return p;}
    private static DesktopMixer.Track started(DesktopMixer m,byte[] pcm,int rate,int channels,int bits,boolean loop,float volume,int pan){DesktopMixer.Track t=new DesktopMixer.Track(pcm,rate,channels,bits,loop);t.volume(volume,pan);t.startAtVolume();m.play(t);return t;}
    public static void run(){
        byte[] out=block();
        DesktopMixer silent=new DesktopMixer();silent.render(out);check(left(out,0)==0&&right(out,100)==0,"An idle mixer outputs silence");

        DesktopMixer m=new DesktopMixer();DesktopMixer.Track t=started(m,level(1000,16384),44100,2,16,false,1,0);m.render(out);
        check(Math.abs(left(out,10)-16384)<3&&Math.abs(right(out,10)-16384)<3,"16-bit stereo passes at full volume");
        check(t.framePosition()==DesktopMixer.BLOCK_FRAMES,"Position counts played frames");
        for(int n=0;n<4;n++)m.render(out);check(t.finished(),"A one-shot track finishes at its end");
        m.render(out);check(left(out,0)==0,"A finished track leaves the mix");

        m=new DesktopMixer();t=started(m,level(100,8192),44100,2,16,true,1,0);for(int n=0;n<3;n++)m.render(out);
        check(!t.finished()&&left(out,50)>8000&&t.framePosition()<100,"A loop wraps and keeps playing");

        m=new DesktopMixer();started(m,level(1000,16384),44100,2,16,false,.5f,0);m.render(out);check(Math.abs(left(out,10)-8192)<3,"Volume is linear amplitude");
        m=new DesktopMixer();started(m,level(1000,16384),44100,2,16,false,1,2000);m.render(out);check(left(out,10)<right(out,10)&&Math.abs(right(out,10)-16384)<3,"Positive pan keeps the right channel");
        m=new DesktopMixer();started(m,level(1000,16384),44100,2,16,false,1,-2000);m.render(out);check(left(out,10)>right(out,10),"Negative pan keeps the left channel");

        m=new DesktopMixer();started(m,level(1000,30000),44100,2,16,false,1,0);started(m,level(1000,30000),44100,2,16,false,1,0);m.render(out);check(left(out,10)==32767,"The sum clips instead of wrapping");

        m=new DesktopMixer();byte[] mono8=new byte[1000];java.util.Arrays.fill(mono8,(byte)192);started(m,mono8,22050,1,8,false,1,0);m.render(out);
        check(Math.abs(left(out,10)-16384)<300&&left(out,10)==right(out,10),"8-bit mono reaches both channels");
        t=new DesktopMixer.Track(new byte[22050*4],22050,2,16,false);m=new DesktopMixer();m.play(t);m.render(out);check(t.framePosition()==DesktopMixer.BLOCK_FRAMES/2,"22 kHz advances at half speed");
        check(t.lengthMicros()==1000000,"Length comes from the source rate");

        m=new DesktopMixer();t=started(m,level(44100,16384),44100,2,16,false,1,0);t.seekMicros(500000);m.render(out);check(t.framePosition()==22050+DesktopMixer.BLOCK_FRAMES,"A seek lands on the requested time");
        check(t.positionMicros()>500000&&t.positionMicros()<510000,"Position reads back in microseconds");

        m=new DesktopMixer();t=started(m,level(44100,16384),44100,2,16,true,1,0);m.render(out);t.stop();m.render(out);
        check(left(out,0)>16000&&Math.abs(left(out,DesktopMixer.BLOCK_FRAMES-1))<200,"A stop ramps down instead of clicking");
        m.render(out);check(left(out,0)==0,"A stopped track leaves the mix");
    }
    public static void main(String[] args){run();System.out.println("Mixer "+checks+" checks PASS");}
}
