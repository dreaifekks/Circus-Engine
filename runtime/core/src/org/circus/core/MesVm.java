package org.circus.core;

import java.io.*;
import java.util.*;

/** Cooperative, bounded interpreter. Unknown instructions stop with a precise diagnostic. */
public final class MesVm {
    public interface Library { MesScript script(String name)throws IOException; }
    public interface Host extends Variables.Special {
        boolean command(String semantic,int[] args,byte[] bytes)throws IOException;
        boolean allowNamedJump()throws IOException;
        boolean beforeTransfer(int mode,String name)throws IOException;
        void save(DataOutput out)throws IOException;
        void load(DataInput in)throws IOException;
    }
    /** Presentation is acknowledged separately from user advancement. */
    public interface FlowHost extends Host {
        boolean beforeInstruction(Profile profile,int opcode)throws IOException;
        boolean waiting();
        long waitTicket();
        boolean signal(long ticket,HostSignal signal)throws IOException;
    }
    public interface ContextHost extends FlowHost {
        void attach(Variables variables);
        void position(MesScript script,int pc);
        void elapse(long milliseconds);
    }
    public interface ChoiceHost extends Host {
        void addChoice(byte[] label,byte[] caption)throws IOException;
        void selectChoice(byte[] label,int index)throws IOException;
    }
    public interface MenuHost extends FlowHost {
        byte[] menuAction(long ticket,int button)throws IOException;
        void acceptMenuAction(int button)throws IOException;
        default boolean internalMenuAction(long ticket,int button)throws IOException{return false;}
        default byte[] pollMenuAction()throws IOException{return null;}
        default byte[] automaticMenuAction(){return null;}
    }
    /** A platform may replace a desktop menu script with its own title screen. */
    public interface TitleHost extends Host { boolean returnToTitle(int mode,String script,byte[] entryLabel)throws IOException; }
    public enum HostSignal { TEXT_COMPLETE, ADVANCE, MEDIA_COMPLETE }
    public enum Status { RUNNING, WAIT_CLICK, WAIT_CHOICE, WAIT_TIME, WAIT_HOST, HALTED, FAULT }
    private final Library library;private final Host host;
    public final Variables variables;
    private MesScript script;private int pc;private byte[] pendingLabel=new byte[0];
    private Status status=Status.RUNNING;private String error="";private long remainingMs;
    public MesVm(Library lib,Host host,String entry)throws IOException{
        this.library=lib;this.host=host;script=lib.script(entry);this.variables=new Variables(host,script.profile.variableModel);pc=script.firstOffset;
        if(host instanceof ContextHost)((ContextHost)host).attach(variables);
    }
    /** The script a snapshot stands in, read without restoring it. */
    public static String scriptOf(byte[] snapshot)throws IOException{
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(snapshot));in.readInt();in.readUTF();return in.readUTF();
    }
    public boolean hasLabel(byte[] label){return script.label(label)!=null;}
    /** Host-owned jump to a label of the current script. */
    public void jump(String label)throws IOException{
        Integer target=script.label(label.getBytes(Binary.SJIS));if(target==null)throw new IOException("Label missing: "+label);pc=target;status=Status.RUNNING;
    }
    public Status status(){return status;} public String error(){return error;}
    public String scriptName(){return script.name;} public int offset(){return pc;}
    public String profileId(){return script.profile.id;}
    /** Host-owned system call; the caller snapshot is retained by the session. */
    public void enterSystem(String name)throws IOException {
        MesScript target=library.script(Binary.safePath(name));
        if(!target.profile.id.equals(script.profile.id))throw new IOException("Cross-profile system call");
        script=target;pc=target.firstOffset;pendingLabel=new byte[0];remainingMs=0;status=Status.RUNNING;
    }
    public long remainingMillis(){return remainingMs;}
    public void advance(){if(status==Status.WAIT_CLICK)status=Status.RUNNING;}
    public boolean signalHost(long ticket,HostSignal signal)throws IOException {
        if(status!=Status.WAIT_HOST||!(host instanceof FlowHost))return false;
        FlowHost flow=(FlowHost)host;boolean accepted=flow.signal(ticket,signal);
        if(accepted&&!flow.waiting())status=Status.RUNNING;return accepted;
    }
    public void elapse(long milliseconds){if(status==Status.WAIT_TIME){remainingMs=Math.max(0,remainingMs-Math.max(0,milliseconds));if(remainingMs==0)status=Status.RUNNING;}if(host instanceof ContextHost){((ContextHost)host).elapse(Math.max(0,milliseconds));if(status==Status.WAIT_HOST&&!((ContextHost)host).waiting())status=Status.RUNNING;}}
    public void choose(byte[] label,int index)throws IOException {
        if(status!=Status.WAIT_CHOICE)throw new IOException("VM is not waiting for a choice");
        Integer dest=script.label(label);if(dest==null)throw new IOException("Choice target missing");
        if(host instanceof ChoiceHost)((ChoiceHost)host).selectChoice(label,index);
        else host.write(99,245,index);
        pc=dest;status=Status.RUNNING;
    }
    /** Resolve the original script label before accepting an input event. */
    public boolean activateMenu(long ticket,int button)throws IOException {
        if(status!=Status.WAIT_HOST||!(host instanceof MenuHost))return false;
        MenuHost menu=(MenuHost)host;if(menu.internalMenuAction(ticket,button))return true;byte[] action=menu.menuAction(ticket,button);if(action==null)return false;
        Integer target=script.label(action);if(target==null)throw new IOException("Unsupported UI action: "+new String(action,Binary.SJIS));
        menu.acceptMenuAction(button);pc=target;status=Status.RUNNING;return true;
    }
    public boolean activateGrid(long ticket,int index)throws IOException {
        if(!(host instanceof FlowHost)||status!=Status.WAIT_HOST||ticket!=((FlowHost)host).waitTicket())return false;
        byte[] action=host instanceof Dc2Host?((Dc2Host)host).menuActionForGrid(index):host instanceof Dc3Host?((Dc3Host)host).menuActionForGrid(index):null;if(action==null)return false;Integer target=script.label(action);if(target==null)throw new IOException("Grid action label missing");pc=target;status=Status.RUNNING;return true;
    }
    private void emit(String op,int[] args,byte[] bytes)throws IOException{if(!host.command(op,args,bytes))throw new IOException("Unimplemented host effect: "+op);}
    /** Instruction trace used by the reverse-engineering probes; off unless -Dcircus.trace is set. */
    static final boolean TRACE=System.getProperty("circus.trace")!=null;
    private void trace(MesScript s,int at,int opcode,String op,int[] args){if(!TRACE)return;if(!s.name.toLowerCase(Locale.ROOT).contains(System.getProperty("circus.trace").toLowerCase(Locale.ROOT)))return;
        StringBuilder b=new StringBuilder(String.format("  %05X %02X %-26s",at,opcode,op));for(int v:args)b.append(' ').append(v);System.out.println(b);}
    public Status run(int budget){
        if(budget<=0||budget>100000)throw new IllegalArgumentException("Instruction budget must be 1..100000");
        if(status==Status.WAIT_HOST&&host instanceof MenuHost){try{byte[] action=((MenuHost)host).pollMenuAction();if(action!=null){Integer target=script.label(action);if(target==null)throw new IOException("Timed menu target missing");pc=target;status=Status.RUNNING;}}catch(IOException e){status=Status.FAULT;error=script.name+": "+e.getMessage();}}
        for(int i=0;i<budget && status==Status.RUNNING;i++){
            try{step();}catch(IOException|RuntimeException e){status=Status.FAULT;error=script.name+" @0x"+Integer.toHexString(pc)+": "+e.getMessage();}
        }
        return status;
    }
    private void step()throws IOException {
        MesScript.Record r=script.at(pc);Profile p=script.profile;String op=p.operation(r.opcode);int next=r.end;
        if(host instanceof ContextHost)((ContextHost)host).position(script,pc);
        if(host instanceof FlowHost&&((FlowHost)host).beforeInstruction(p,r.opcode)){status=Status.WAIT_HOST;return;}
        int[] args=r.args;
        if(r.opcode!=0 && next<script.length() && script.at(next).opcode==p.extension){
            MesScript.Record ext=script.at(next);int n=args.length;args=Arrays.copyOf(args,n+4);System.arraycopy(ext.args,0,args,n,4);next=ext.end;
        }
        trace(script,pc,r.opcode,op,args);
        if(op.equals("unknown"))throw new IOException(String.format("Unsupported opcode 0x%02X (%s)",r.opcode,p.id));
        switch(op){
        case "stream.stop": status=Status.HALTED;return;
        case "system.exit":emit(op,args,r.bytes);status=Status.HALTED;return;
        case "control.marker": case "label.named": case "arguments.extend": case "choice.caption": break;
        case "checkpoint":{
            int id=args[0]|args[1]<<8;if(id>=script.headerMetadata.length)throw new IOException("Checkpoint outside metadata table");
            emit(op,new int[]{id,script.headerMetadata[id]},new byte[0]);break;
        }
        case "variable.update":case "variable.update_indirect":{
            int mode=args[3];if(mode>=0 && mode<=2){int v=args[2];if(mode!=0){int old=variables.read(args[0],args[1]);v=mode==1?old+v:old-v;}variables.write(args[0],args[1],v);}
            else if(mode==3&&op.equals("variable.update_indirect"))emit("variable.indirect_result",new int[]{variables.read(args[2],variables.read(args[0],args[1]))},new byte[0]);
            break;
        }
        case "variable.copy": variables.write(args[0],args[1],variables.read(args[2],args[3]));break;
        case "variable.add_variable": variables.write(args[0],args[1],variables.read(args[0],args[1])+variables.read(args[2],args[3]));break;
        case "variable.sum_list":{
            int sum=0,steps=0;
            while(next<script.length() && script.at(next).opcode!=p.companion){
                MesScript.Record term=script.at(next);if(term.end-term.offset!=9 || term.args.length!=4)throw new IOException("Sum term is not nine bytes");
                sum+=variables.read(term.args[0],term.args[1]);next=term.end;if(++steps>100000)throw new IOException("Sum list limit");
            }
            if(next>=script.length())throw new IOException("Sum terminator missing");variables.write(args[0],args[1],sum);break;
        }
        case "branch.compare":{
            int left=variables.read(args[0],args[1]),right=args[2],mode=args[3];
            if((mode&128)!=0){right=variables.read(right>>8,right&255);mode&=127;}
            boolean pass;switch(mode){case 0:pass=left==right;break;case 1:pass=left!=right;break;case 2:pass=left>right;break;case 3:pass=left<right;break;case 4:pass=left>=right;break;case 5:pass=left<=right;break;default:throw new IOException("Unknown comparison mode");}
            MesScript.Record c=script.at(next);if(c.opcode!=p.companion||c.args.length!=2)throw new IOException("Missing conditional companion");
            next=c.end;if(!pass)next=script.scanBlock(next,(byte)c.args[0]);break;
        }
        case "block.marker":if(args[0]==0)next=script.scanBlock(next,(byte)args[1]);break;
        case "block.marker_unsigned":if(args[0]==0)next=script.scanBlock(next,args[1]);break;
        case "pc.system_call":{MesScript.Record name=script.at(next);if(name.opcode!=0x46)throw new IOException("System call without its name");next=name.end;emit(op,args,name.bytes);break;}
        case "jump.named":if(host.allowNamedJump()){Integer target=script.label(r.bytes);if(target!=null)next=target;}break;
        case "jump.named_required":{
            Integer target=script.requiredLabel(r.bytes);if(target==null)throw new IOException("Required named label missing");next=target;break;
        }
        case "pc.character_pair":case "pc.portrait_pair":{emit(op,new int[]{0},r.bytes);if(next<script.length()&&script.at(next).opcode==0x46){MesScript.Record pair=script.at(next);emit(op,new int[]{1},pair.bytes);next=pair.end;}break;}
        case "script.entry_label":pendingLabel=r.bytes.clone();break;
        case "pc.script.transfer":case "dm.script.transfer":case "dm.script.transfer_alt":{
            // D.C.II encodes the mode byte only when the profile has a string8 class; 2C..48 carry none.
            int mode=op.equals("pc.script.transfer")?(args.length==0?1:args[0]==4?2:args[0]==3||args[0]==5?3:1):op.equals("dm.script.transfer_alt")?2:1;
            if(mode==1&&next<script.length()){MesScript.Record following=script.at(next);if(following.opcode==p.companion&&following.args[0]==1)mode=3;}
            String name=Binary.safePath(new String(r.bytes,Binary.SJIS));MesScript target=library.script(name);
            if(!target.profile.id.equals(p.id))throw new IOException("Cross-profile transfer rejected");
            Integer dest=pendingLabel.length==0?target.firstOffset:target.requiredLabel(pendingLabel);
            if(dest==null)throw new IOException("Required entry label missing");
            if(!host.beforeTransfer(mode,name))throw new IOException("Unverified DM transition effects");
            script=target;next=dest;pendingLabel=new byte[0];break;
        }
        case "script.transfer":case "script.transfer_alt":{
            int mode=op.equals("script.transfer")?0:1;String name=new String(r.bytes,Binary.SJIS);Binary.safePath(name);
            MesScript target=library.script(name);if(!target.profile.id.equals(p.id))throw new IOException("Cross-profile transfer rejected");
            if(!host.beforeTransfer(mode,name))throw new IOException("Unverified script transition effects");
            if(host instanceof TitleHost&&((TitleHost)host).returnToTitle(mode,name,pendingLabel)){pendingLabel=new byte[0];status=Status.HALTED;return;}
            script=target;next=script.firstOffset;Integer label=script.label(pendingLabel);if(label!=null)next=label;pendingLabel=new byte[0];break;
        }
        case "dm.save.checkpoint":{
            MesScript.Record payload=script.at(next);if(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart)throw new IOException("Invalid DM checkpoint label");
            emit(op,args,payload.bytes);next=payload.end;break;
        }
        case "dd.grid.text":{if(args[3]==99)emit(op,args,new byte[0]);else{MesScript.Record payload=script.at(next);if(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart)throw new IOException("List caption missing");emit(op,args,payload.bytes);next=payload.end;}break;}
        case "ui.text":if(args[2]==0){emit(op,args,new byte[0]);break;}
        case "pc.grid.caption":case "pc.save_context":case "pc.resource_payload":case "dm.system.resource_handle":case "dm.ui.text":case "ui.button_text":case "host.command_with_string":case "scene.checkpoint":{            MesScript.Record payload=script.at(next);if(payload.opcode<p.stringStart||payload.opcode>=p.wordsStart)throw new IOException("Invalid UI string payload");
            emit(op,args,op.equals("ui.text")||op.equals("dm.ui.text")?script.nativeText(payload):payload.bytes);next=payload.end;break;
        }
        case "ui.confirm":case "dm.ui.confirm":{
            emit(op,args,r.bytes);if(host instanceof MenuHost){byte[] action=((MenuHost)host).automaticMenuAction();if(action!=null){Integer target=script.label(action);if(target==null)throw new IOException("Automatic UI label missing");next=target;}}break;
        }
        case "text.speaker":case "text.body":emit(op,args,script.nativeText(r));break;
        case "choice.item":{
            if(!(host instanceof ChoiceHost))throw new IOException("Choice-aware host required");
            MesScript.Record caption=script.at(next);
            if(!p.operation(caption.opcode).equals("choice.caption"))throw new IOException("Choice caption does not match "+p.id);
            ((ChoiceHost)host).addChoice(r.bytes,script.nativeText(caption));next=caption.end;break;
        }
        case "choice.show":emit(op,args,new byte[0]);if(args[0]==0)status=Status.WAIT_CHOICE;break;
        case "wait.audio":{
            int mask=args[0];while(next<script.length()&&script.profile.operation(script.at(next).opcode).equals(op)){MesScript.Record term=script.at(next);mask|=(byte)term.args[0];next=term.end;}
            emit(op,new int[]{mask},new byte[0]);break;
        }
        case "wait.message":
            if(!(host instanceof FlowHost))throw new IOException("Native wait requires a flow-aware host");
            emit(op,args,new byte[0]);break;
        case "demo.wait_click":requireDemo();status=Status.WAIT_CLICK;break;
        case "demo.wait_choice":requireDemo();emit(op,args,new byte[0]);status=Status.WAIT_CHOICE;break;
        case "demo.delay":requireDemo();if(args[0]<0)throw new IOException("Negative delay");remainingMs=args[0];status=remainingMs==0?Status.RUNNING:Status.WAIT_TIME;break;
        case "demo.text":case "demo.scene":case "demo.audio":requireDemo();emit(op,args,r.bytes);break;
        default:emit(op,args,r.bytes);break;
        }
        if(status!=Status.HALTED && !script.boundary(next))throw new IOException("Next PC outside code");pc=next;
        if(host instanceof FlowHost&&((FlowHost)host).waiting())status=Status.WAIT_HOST;
    }
    private void requireDemo()throws IOException{if(!script.profile.demo)throw new IOException("Demo operation in native profile");}
    /** Portable engine save format, deliberately not the original game's save format. */
    public byte[] snapshot()throws IOException{
        if(status==Status.FAULT)throw new IOException("Cannot save faulted VM");
        ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(raw);
        out.writeInt(0x43565331);out.writeUTF(script.profile.id);out.writeUTF(script.name);out.writeUTF(script.sha256);
        out.writeInt(pc);out.writeUTF(status.name());out.writeLong(remainingMs);out.writeInt(pendingLabel.length);out.write(pendingLabel);
        variables.save(out);host.save(out);out.flush();byte[] body=raw.toByteArray();out.writeUTF(Binary.sha256(body));out.flush();return raw.toByteArray();
    }
    /** Restore only into a NEW VM/host pair; publish that pair after this succeeds. */
    public void restore(byte[] saved)throws IOException{
        if(saved.length>32*1024*1024 || saved.length<66)throw new IOException("Invalid save size");
        int bodyLength=saved.length-66;DataInputStream digest=new DataInputStream(new ByteArrayInputStream(saved,bodyLength,66));
        if(!digest.readUTF().equals(Binary.sha256(Arrays.copyOf(saved,bodyLength))))throw new IOException("Save checksum mismatch");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(saved,0,bodyLength));
        if(in.readInt()!=0x43565331)throw new IOException("Unsupported save format");
        String profile=in.readUTF(),name=in.readUTF(),hash=in.readUTF();if(!profile.equals(script.profile.id))throw new IOException("Save profile mismatch");
        MesScript target=library.script(Binary.safePath(name));if(!target.profile.id.equals(profile))throw new IOException("Restored library returned a different profile");if(!target.sha256.equals(hash))throw new IOException("Script changed since save");
        int pos=in.readInt();if(!target.boundary(pos))throw new IOException("Invalid saved PC");
        Status state;try{state=Status.valueOf(in.readUTF());}catch(IllegalArgumentException e){throw new IOException("Invalid saved status",e);}
        if(state==Status.FAULT)throw new IOException("Fault status cannot be restored");long time=in.readLong();if(time<0||time>32767)throw new IOException("Invalid saved delay");
        int length=in.readInt();if(length<0||length>65536)throw new IOException("Invalid pending label length");byte[] pending=new byte[length];in.readFully(pending);
        variables.load(in);host.load(in);if(in.available()!=0)throw new IOException("Trailing save payload");
        boolean hostWaiting=host instanceof FlowHost&&((FlowHost)host).waiting();
        if((state==Status.WAIT_HOST)!=hostWaiting)throw new IOException("Saved VM/host wait mismatch");
        script=target;pc=pos;status=state;remainingMs=time;pendingLabel=pending;error="";
    }
}
