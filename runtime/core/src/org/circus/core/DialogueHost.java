package org.circus.core;

import java.io.*;
import java.util.*;

/** DD dialogue module, not a complete game host. UI, audio and scene commands remain errors.
 * The caller supplies a known normal-play context; this class does not initialize a game.
 */
public final class DialogueHost implements MesVm.FlowHost {
    public final TextCodec codec;
    public String familyName,givenName;
    public boolean suppressMessageWait,skipNextMessageWait;
    public String shownSpeaker="",shownText="";
    public final List<String> backlog=new ArrayList<>();
    private String speaker="",body="";
    private boolean previousText,rendered;
    private int reason;
    private long ticket;
    public DialogueHost(TextCodec codec){this.codec=Objects.requireNonNull(codec);}
    public int waitReason(){return reason;}
    public long waitTicket(){return ticket;}
    public boolean rendered(){return rendered;}
    public boolean waiting(){return reason!=0;}
    public boolean beforeInstruction(Profile profile,int opcode)throws IOException {
        if(!Dc3Host.supports(profile.id))throw new IOException("Dialogue host is not verified for "+profile.id);
        if(waiting())return true;
        if(previousText){previousText=false;if(opcode!=Dc3Host.waitMessageOpcode(profile.id)){beginWait(8);return true;}}
        return false;
    }
    private void beginWait(int nativeReason)throws IOException {
        if(ticket==Long.MAX_VALUE)throw new IOException("Dialogue event counter exhausted");
        reason=nativeReason;ticket++;rendered=false;shownSpeaker=speaker;shownText=body;
        String entry=shownSpeaker.isEmpty()?shownText:shownSpeaker+"\n"+shownText;
        backlog.add(entry);if(backlog.size()>200)backlog.remove(0);
    }
    public boolean signal(long expected,MesVm.HostSignal signal){
        if(!waiting()||ticket!=expected)return false;
        if(signal==MesVm.HostSignal.MEDIA_COMPLETE)return false;
        if(signal==MesVm.HostSignal.TEXT_COMPLETE)rendered=true;
        else if(!rendered){rendered=true;if(reason==8)finishWait();return true;} // First tap completes typewriter display.
        else {finishWait();return true;}
        if(reason==8)finishWait();
        return true;
    }
    private void finishWait(){reason=0;previousText=false;speaker="";}
    /** The session retains the complete caller before lending the VM to system scripts. */
    public void suspend(){reason=0;previousText=false;rendered=false;shownText=shownSpeaker="";}
    public boolean command(String op,int[] args,byte[] bytes)throws IOException {
        if(waiting())throw new IOException("Command while dialogue is waiting");
        switch(op){
        case "text.speaker":speaker=NativeText.plain(bytes,codec,familyName,givenName,true);return true;
        case "text.body":body=NativeText.plain(bytes,codec,familyName,givenName,false);previousText=true;return true;
        case "wait.message":
            if(suppressMessageWait)return true;
            if(skipNextMessageWait){skipNextMessageWait=false;return true;}
            beginWait(6);return true;
        default:return false;
        }
    }
    public int read(int bank,int index)throws IOException{throw new IOException("Dialogue host has no special variable "+bank+":"+index);}
    public void write(int bank,int index,int value)throws IOException{throw new IOException("Dialogue host has no special variable "+bank+":"+index);}
    public boolean allowNamedJump()throws IOException{throw new IOException("Dialogue host has no native jump context");}
    public boolean beforeTransfer(int mode,String name){return false;}
    public void save(DataOutput out)throws IOException {
        out.writeInt(0x444c4731);out.writeUTF(codec.fingerprint);
        writeNullable(out,familyName);writeNullable(out,givenName);
        out.writeBoolean(suppressMessageWait);out.writeBoolean(skipNextMessageWait);
        out.writeUTF(speaker);out.writeUTF(body);out.writeUTF(shownSpeaker);out.writeUTF(shownText);
        out.writeBoolean(previousText);out.writeBoolean(rendered);out.writeInt(reason);out.writeLong(ticket);
        out.writeInt(backlog.size());for(String line:backlog)out.writeUTF(line);
    }
    private static void writeNullable(DataOutput out,String value)throws IOException{out.writeBoolean(value!=null);if(value!=null)out.writeUTF(value);}
    private static String readNullable(DataInput in)throws IOException{return in.readBoolean()?in.readUTF():null;}
    public void load(DataInput in)throws IOException {
        if(in.readInt()!=0x444c4731||!in.readUTF().equals(codec.fingerprint))throw new IOException("Dialogue character map or state format changed");
        familyName=readNullable(in);givenName=readNullable(in);
        suppressMessageWait=in.readBoolean();skipNextMessageWait=in.readBoolean();
        speaker=in.readUTF();body=in.readUTF();shownSpeaker=in.readUTF();shownText=in.readUTF();
        previousText=in.readBoolean();rendered=in.readBoolean();reason=in.readInt();ticket=in.readLong();
        if((reason!=0&&reason!=6&&reason!=8)||ticket<0||(reason!=0&&(ticket==0||previousText)))throw new IOException("Invalid dialogue wait state");
        int count=in.readInt();if(count<0||count>200)throw new IOException("Dialogue backlog limit");backlog.clear();for(int i=0;i<count;i++)backlog.add(in.readUTF());
    }
}
