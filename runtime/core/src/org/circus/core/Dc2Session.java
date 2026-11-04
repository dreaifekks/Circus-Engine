package org.circus.core;

import java.io.*;

/** A native system script temporarily borrows the story VM; its return restores that caller. */
public final class Dc2Session {
    public MesVm vm;public Dc2Host host;
    private final CircusResources data;private final TextCodec codec;
    private byte[] caller;private String callerScript;
    public Dc2Session(CircusResources data,TextCodec codec)throws IOException{this.data=data;this.codec=codec;host=Dc2Host.create(data,codec);vm=new MesVm(data.scripts(),host,"START");}
    public boolean inSystem(){return caller!=null;}
    public void openNativeSystem()throws IOException{
        if(caller!=null||!host.textWaiting&&!host.ui.waiting)throw new IOException("No stable story context for native menu");
        caller=vm.snapshot();callerScript=vm.scriptName();host.enterSystem();
    }
    public void openSystem(String script)throws IOException{
        if(caller!=null||(!host.textWaiting&&!host.ui.waiting))throw new IOException("No stable story context for system menu");
        byte[] saved=vm.snapshot();String name=vm.scriptName();host.enterSystem();vm.enterSystem(script);caller=saved;callerScript=name;
    }
    /** 41EC40: a script calls another script; the caller goes on from its next instruction when that one returns. */
    public void openCall(String script)throws IOException{
        if(caller!=null)throw new IOException("Nested system call");
        vm.elapse(0);byte[] saved=vm.snapshot();String name=vm.scriptName();host.enterSystem();vm.enterSystem(script);caller=saved;callerScript=name;
    }
    public void returnToStory()throws IOException{
        if(caller==null)throw new IOException("System return without caller");byte[] saved=caller;restore(saved);caller=null;callerScript=null;
    }
    public byte[] storySnapshot()throws IOException{return caller==null?vm.snapshot():caller.clone();}
    public String storyScript(){return caller==null?vm.scriptName():callerScript;}
    public void restore(byte[] saved)throws IOException{
        Dc2Host h=Dc2Host.create(data,codec);MesVm m=new MesVm(data.scripts(),h,"START");m.restore(saved);h.copyPersistentFrom(host);h.captureRenderer=host.captureRenderer;host=h;vm=m;caller=null;callerScript=null;
    }
    /** Returning to MAIN abandons the suspended story. */
    public void captureHistory()throws IOException{
        if(caller!=null||(!host.textWaiting&&!host.presentationWaiting)||host.history.entries.isEmpty()||host.keepsOnlyLogText())return;
        Dc2History.Entry e=host.history.entries.get(host.history.entries.size()-1);if(e.resume.length!=0)return;
        host.history.omitFromSnapshot=true;try{e.resume=vm.snapshot();}finally{host.history.omitFromSnapshot=false;}
        int total=0;for(int n=host.history.entries.size()-1;n>=0;n--){Dc2History.Entry old=host.history.entries.get(n);total+=old.resume.length;if(total>2*1024*1024)old.resume=new byte[0];}
    }
    public void rewindHistory(int index)throws IOException{
        if(index<0||index>=host.history.entries.size())throw new IOException("History entry missing");
        java.util.List<Dc2History.Entry> prefix=new java.util.ArrayList<>(host.history.entries.subList(0,index+1));byte[] snapshot=prefix.get(index).resume;
        if(snapshot.length==0)throw new IOException("History checkpoint expired");restore(snapshot);host.history.entries.addAll(prefix);host.history.active=false;host.history.selected=index;host.history.cursor=Math.max(0,index-3);
    }
    public void observeTransfer(){if(caller!=null&&vm.scriptName().equalsIgnoreCase("MAIN")){caller=null;callerScript=null;host.systemScene=null;}}
}
