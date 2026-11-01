package org.circus.core;

import java.io.*;

/** Special-bank storage, separate from platform effects and ordinary MES variables. */
public final class Dc3State implements Variables.Special {
    private final byte[] b100=new byte[150],b96=new byte[400],b97=new byte[1200],b98=new byte[650];
    private final int[] b99=new int[256],b93=new int[20],b94=new int[51],b95=new int[51];
    public final int[] musicFavorites=new int[400];
    public int voiceCount;
    /** Runtime-only gallery/route-clear dirty counter; old serialized layouts remain unchanged. */
    public long progressRevision;
    public Dc3State(){this(false);}
    public Dc3State(boolean originalSystem){
        // The original-system host uses 40ED10's initial preference table.
        // Keep the earlier chapter-only defaults for the separate Android demo.
        b100[10]=(byte)(originalSystem?0:1);b96[1]=1;b96[255]=1;b99[12]=5;b99[14]=b99[15]=b99[5]=1;b99[29]=3;
        b99[7]=b99[8]=b99[9]=100;b99[243]=b99[245]=b99[246]=b99[253]=b99[254]=-1;
        b99[203]=1;b99[225]=100;resetVoicePreferences();
        if(originalSystem)resetPreferences();
    }
    /** 40ED10(0): native default table 4A5100; progression bytes are retained. */
    public void resetPreferences(){
        int[] keys={5,7,8,9,10,11,12,13,14,15,16,17,18,26,27,28,29,30,184,185,188,190,191,194,200,201,202,203,205,206,207,208,209,210,211,212,213,214,225,227,237},values={1,65,80,100,1,49,6,0,1,1,7,0,1,3,3,1,3,1,3,0,1,0,0,5,1,1,0,1,1,85,80,0,0,0,0,1,1,0,100,1,1};
        for(int n=0;n<keys.length;n++)b99[keys[n]]=values[n];
        b99[199]=0;b99[198]=5;b99[197]=2;b99[196]=4;
        java.util.Arrays.fill(b93,0);java.util.Arrays.fill(musicFavorites,0);
    }
    public void resetVoicePreferences(){java.util.Arrays.fill(b94,100);java.util.Arrays.fill(b95,1);}
    public void migrateLegacyAudio(){b99[203]=1;b99[225]=100;resetVoicePreferences();}
    private byte[] bank(int b){switch(b){case 100:return b100;case 96:return b96;case 97:return b97;case 98:return b98;default:return null;}}
    private static void index(int i,int n)throws IOException{if(i<0||i>=n)throw new IOException("Special variable index outside verified range: "+i);}
    public int read(int b,int i)throws IOException {
        byte[] bytes=bank(b);if(bytes!=null){index(i,bytes.length);return bytes[i]&255;}
        if(b==93)return i>=0&&i<20?b93[i]:0;
        if(b==94||b==95){if(i==50||(i>=0&&i<voiceCount))return (b==94?b94:b95)[i];return -1;}
        if(b!=99)return 0;index(i,256);
        if(i==217){index(b99[218],20);return b93[b99[218]];}
        // READ kind 2 is a query whose native answer lives in a struct the host does not
        // own (dc3dd-state.json: storage verified, business role unknown). D.C.III D.D.
        // never asks, but With You and P.P. do - their WIN_SIZE queries bank 99 index 3
        // for the window mode - so answer with the stored byte instead of aborting. It
        // defaults to 0, the same value a fresh native session holds there.
        return Dc3StateRules.READ[i]==0?0:b99[i];
    }
    public void write(int b,int i,int value)throws IOException {
        byte[] bytes=bank(b);if(bytes!=null){if(i<0||i>=bytes.length)return;if(b==100&&i==49)write(99,11,value);if(bytes[i]!=(byte)value&&(b==97||b==98||b==100&&i>=100&&i<=110))progressRevision++;bytes[i]=(byte)value;return;}
        if(b==93){if(i>=0&&i<20)b93[i]=value;return;}
        if(b==94||b==95){if(i==50||(i>=0&&i<voiceCount))(b==94?b94:b95)[i]=b==94?Math.min(value,100):value;return;}
        if(b!=99)return;index(i,256);
        if(i==217){index(b99[218],20);b93[b99[218]]=value;return;}
        int kind=Dc3StateRules.WRITE[i];if(kind==0)return;
        if(kind>=2){
            // These native setters update preferences and notify the audio device.
            // The platform reads the same values when updating channel gain.
            if(i==5||i==14||i==15)value=value==0?0:1;
            else if(i==7||i==8)value=Math.max(0,Math.min(100,value));
            else if(i==225)value=Math.min(100,value);
            else if(i==203||i==237){} // Master enable and window preference; consumed by the desktop platform.
            else throw new IOException("Unimplemented native state effect 99:"+i);
        }
        if(i==224)value=value>0?1:0;
        if(i==243||i==245||i==246||i==253||i==254)if(value==255)value=-1;
        b99[i]=value;if(i==10)b99[214]=value==2?1:0;
    }
    public void save(DataOutput out)throws IOException {
        out.writeInt(voiceCount);for(byte[] b:new byte[][]{b100,b96,b97,b98})out.write(b);
        for(int[] b:new int[][]{b99,b93,b94,b95})for(int v:b)out.writeInt(v);
    }
    /** Loading an older story must not roll back gallery progress or current preferences. */
    public void mergeGallery(Dc3State other)throws IOException{
        for(int bank:new int[]{97,98}){byte[] source=other.bank(bank),target=bank(bank);for(int i=0;i<source.length;i++)if(target[i]==0&&source[i]!=0)write(bank,i,source[i]&255);}
    }
    /** Projection of native [state, state+0x4b74): excludes ordinary variables and live UI state. */
    public byte[] settingsSnapshot()throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        for(byte[] b:new byte[][]{b100,b96,b97,b98})out.write(b);
        for(int v:b93)out.writeInt(v);for(int i:Dc3StateRules.SETTINGS)out.writeInt(b99[i]);for(int f:musicFavorites)out.writeInt(f);for(int[] b:new int[][]{b94,b95})for(int v:b)out.writeInt(v);return bytes.toByteArray();
    }
    public boolean acceptsSettingsLength(int n)throws IOException{int length=settingsSnapshot().length;return n==length||n==length-408||n==length-2008;}
    /** Shared by normal play and explicit native-progress import. */
    public byte[] savePersistent()throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();new DataOutputStream(bytes).writeInt(0x44445032);bytes.write(settingsSnapshot());return bytes.toByteArray();}
    public void loadPersistent(byte[] bytes)throws IOException{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(bytes.length>4&&in.readInt()==0x44445032){byte[] data=new byte[bytes.length-4];in.readFully(data);restoreSettings(data);}
        else{restoreSettings(bytes);migrateLegacyAudio();}
    }
    public void restoreSettings(byte[] bytes)throws IOException {
        if(!acceptsSettingsLength(bytes.length))throw new IOException("Invalid settings snapshot");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        for(byte[] b:new byte[][]{b100,b96,b97,b98})in.readFully(b);
        for(int i=0;i<b93.length;i++)b93[i]=in.readInt();for(int i:Dc3StateRules.SETTINGS)b99[i]=in.readInt();if(in.available()>=1600)for(int n=0;n<musicFavorites.length;n++)musicFavorites[n]=in.readInt();if(in.available()==408)for(int[] b:new int[][]{b94,b95})for(int n=0;n<b.length;n++)b[n]=in.readInt();else resetVoicePreferences();
    }
    public void load(DataInput in)throws IOException {
        voiceCount=in.readInt();if(voiceCount<0||voiceCount>50)throw new IOException("Invalid voice count");
        for(byte[] b:new byte[][]{b100,b96,b97,b98})in.readFully(b);
        for(int[] b:new int[][]{b99,b93,b94,b95})for(int i=0;i<b.length;i++)b[i]=in.readInt();
    }
}
