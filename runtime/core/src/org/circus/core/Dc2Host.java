package org.circus.core;
import java.io.*;
import java.util.*;

/** DM native host. Instruction state follows reviewed disassembly; some visual timings are approximate. */
public class Dc2Host implements MesVm.ContextHost,MesVm.MenuHost,MesVm.ChoiceHost {
    // Native DC2DM GRB palette at 4907B8. Index 11 differs from PC/DD.
    private static final int[] DM_PALETTE={0xff000000,0xff0000ff,0xffff0000,0xffff00ff,0xff00ff00,0xff00ffff,0xffffff00,0xffffffff,0xff854e28,0xffccccff,0xff774400,0xffff5f82,0xffcccccc};
    private void defaultDmPalette(boolean restoring){
        if(!resources.profile.id.equals("dc2dm-9d72"))return;
        if(restoring){for(int n=0;n<DM_PALETTE.length;n++)if(ui.palette[n]!=0xffffffff)return;}
        System.arraycopy(DM_PALETTE,0,ui.palette,0,DM_PALETTE.length);
    }
    public final CircusResources resources;
    public final TextCodec codec;
    public final Dc2State state=new Dc2State();
    public final Dc2History history=new Dc2History();public final Dc2MusicRoom musicRoom=new Dc2MusicRoom();
    public boolean replayMode;protected String lineVoice="";
    public final Dc2Ui ui=new Dc2Ui();
    public final Dc2Scene scene=new Dc2Scene();public final Dc2Credits credits=new Dc2Credits();
    public UiCapture uiCapture;public UiCapture.Renderer captureRenderer;public boolean fullscreen;public int windowRequest=-1;private final int[] confirmations=new int[20];private final byte[] voicePreferences=new byte[256];public int voiceCharacter=17,voicePan;private byte[] automaticMenuAction;
    public final Set<Integer> availableSaves=new HashSet<>();
    /** Opcodes whose record shape is verified but whose visual effect is not modelled yet. */
    public final List<String> unmodelled=new ArrayList<>();
    public final Set<String> readCheckpoints=new TreeSet<>();
    public final String[] audio={"","","","","",""};
    public final int[] audioGeneration=new int[6];
    public final boolean[] audioLoop=new boolean[6],audioActive=new boolean[6];
    protected final Map<Integer,Integer> registers=new TreeMap<>(),optionBaseline=new TreeMap<>();
    public int musicAttenuation;public long endingClock,musicFadeRemaining;
    protected long fadeOutMillis(){return followsDm()?600:1700;}
    /** Set by the Mac app, where DM follows DC2DM.exe. PcHost has its own switch, for its own builds. */
    protected boolean followsDm(){return PcHost.followsExe&&resources.profile.id.equals("dc2dm-9d72");}
    public boolean ctrlHeld;
    private boolean dmTimedWait,dmDismissable;private long dmClock,dmAnchor;
    /** 42D019, 432CF1: the first beat runs at once. Bit 0 lets a click end it, bit 1 halves the beat, 4 keeps to the clock. */
    private long dmWaitMillis(int[] a){
        if((a[1]&4)!=0){if(a[0]==0){dmAnchor=dmClock;return 0;}long target=dmAnchor+200L*a[0];dmAnchor=target;return Math.max(0,target-dmClock);}
        if(a[0]==0)return -1;
        if((a[1]&2)!=0)return 100L*(a[0]*2-1);
        return 200L*(a[0]-1);
    }
    /** 4313EF, 42750A: 34 ms steps across 127 levels, the step taken from the speed setting or the argument. */
    private long dmFadeMillis(int a1)throws IOException{
        int[] speed={4,8,16,32},table={32,32,16,8,4,2,1,1};
        int step=ctrlHeld?32:a1==0?speed[Math.max(0,Math.min(3,read(99,12)))]:a1<8?table[a1]:1,per=a1>=8?34*(a1-7):34;
        return (long)per*((126+step)/step);
    }
    public boolean dmTimedWait(){return dmTimedWait&&remaining!=0;}
    private long musicFadeLength=1700;
    public float musicFadeGain(){return musicFadeRemaining==0?1:musicFadeRemaining/(float)musicFadeLength;}
    protected Variables variables;protected MesScript script;protected int pc;
    protected long ticket,remaining;protected int audioMask,checkpoint,music=-1;
    public boolean exitRequested,skipAllowed=true;
    public String speaker="",body="";public boolean textWaiting,textRendered,presentationWaiting,speakerPending;
    public int messageMode,inputMode,waitIndicatorStyle;
    public String movie="",checkpointLabel="";public int movieGeneration,autosaveGeneration;
    protected int choiceMode,choiceCount;
    public boolean returnRequested;public int saveRequest=-1,loadRequest=-1;
    public Dc2Scene.Frame systemScene;
    public final List<Dc2Ui.Button> grid=new ArrayList<>();public boolean gridWaiting,saveGrid;
    public int gridHoverSound=255,gridClickSound=224,confirmX,confirmY,confirmKind;public boolean confirming;
    public final Map<Integer,String> saveLabels=new TreeMap<>();
    public static Dc2Host create(CircusResources r,TextCodec c){return PcHost.supports(r.profile)?new PcHost(r,c):new Dc2Host(r,c);}
    public void enterSystem()throws IOException {
        systemScene=scene.shown;remaining=0;audioMask=0;textWaiting=presentationWaiting=false;movie="";ui.waiting=false;ui.buttons.clear();speaker=body="";registers.put(1,0);registers.put(19,0);play(5,"",false);
    }
    public void copyPersistentFrom(Dc2Host other)throws IOException {System.arraycopy(other.confirmations,0,confirmations,0,20);System.arraycopy(other.voicePreferences,0,voicePreferences,0,256);
        ByteArrayOutputStream b=new ByteArrayOutputStream();other.state.save(new DataOutputStream(b));state.load(new DataInputStream(new ByteArrayInputStream(b.toByteArray())));
        for(int n=5;n<=30;n++)if(other.registers.containsKey(n))registers.put(n,other.registers.get(n));
        for(int n:new int[]{220,221,222,226,236,237})registers.put(n,other.read(99,n));
        availableSaves.clear();availableSaves.addAll(other.availableSaves);saveLabels.clear();saveLabels.putAll(other.saveLabels);readCheckpoints.addAll(other.readCheckpoints);
    }
    public boolean confirmationChecked(){return confirming&&confirmKind>=0&&confirmKind<20&&confirmations[confirmKind]!=0;}
    public void completeSave(){saveRequest=-1;}
    public void savePersistent(DataOutput o)throws IOException {o.writeInt(0x444d5032);state.save(o);for(int n=5;n<=30;n++)o.writeInt(read(99,n));for(int n:new int[]{220,221,222,226,236,237})o.writeInt(read(99,n));o.writeInt(readCheckpoints.size());for(String key:readCheckpoints)o.writeUTF(key);for(int v:confirmations)o.writeInt(v);o.write(voicePreferences);}
    public void loadPersistent(DataInput i)throws IOException {int fmt=i.readInt();if(fmt!=0x444d5031&&fmt!=0x444d5032)throw new IOException("DM progress format");state.load(i);for(int n=5;n<=30;n++)registers.put(n,i.readInt());for(int n:new int[]{220,221,222,226,236,237})registers.put(n,i.readInt());int count=i.readInt();require(count>=0&&count<=100000,"read checkpoint count");while(count-->0)readCheckpoints.add(i.readUTF());if(!(this instanceof PcHost))registers.put(13,0);if(fmt>=0x444d5032){for(int n=0;n<20;n++)confirmations[n]=i.readInt();i.readFully(voicePreferences);}}
    public byte[] menuActionForGrid(int index)throws IOException {
        require(gridWaiting&&index>=0&&index<grid.size(),"grid selection");int slot=1+read(99,0)*10+index;
        if(saveGrid&&slot<=3)return null; // 40F565: reserved entries have an empty save action.
        if(!saveGrid&&!availableSaves.contains(slot))return null;
        registers.put(246,0);registers.put(253,index);registers.put(254,slot);play(1,resources.named("sound",gridClickSound),false);
        gridWaiting=false;ui.waiting=false;return "selsave".getBytes(Binary.SJIS);
    }

    public void addChoice(byte[] label,byte[] caption)throws IOException {
        require(choiceMode==1,"choice layout "+choiceMode);
        List<Dc2Ui.Button> templates=ui.buttonTemplates.get(3);int index=++choiceCount;require(index<templates.size(),"choice template count");
        Dc2Ui.Button b=Dc2Ui.copyButtons(Collections.singletonList(templates.get(index))).get(0);b.action=text(label);
        for(Dc2Ui.Quad q:b.sprites)if(q.type==2){q.text=TextCodec.cp932(caption);q.width=caption.length*11+1;}
        ui.buttons.add(b);
    }
    public void selectChoice(byte[] label,int index)throws IOException{ui.buttons.clear();ui.waiting=false;}
    public Dc2Host(CircusResources resources,TextCodec codec){if(!resources.profile.id.equals("dc2dm-9d72")&&!PcHost.supports(resources.profile))throw new IllegalArgumentException("DM profile required");this.resources=resources;this.codec=codec;defaultDmPalette(false);for(int k=0;k<128;k++){voicePreferences[k*2]=1;voicePreferences[k*2+1]=99;}int[] defaults={0,0,0,1,1,1,1,20,23,27,1,11,2,0,1,1,7,0,1,0,0,0,0,0,0,0,8,8,0,17,0};for(int n=0;n<defaults.length;n++)registers.put(n,defaults[n]);for(int n:new int[]{215,220,221,222,226,227,228,234,236,237,242,243,244,245,246,253,254,255})registers.put(n,n==220?1:n==245||n==253||n==254?255:0);if(followsDm())registers.put(30,1);}
    public void attach(Variables v){variables=v;}
    public void position(MesScript s,int offset){script=s;pc=offset;}
    public boolean beforeInstruction(Profile p,int opcode)throws IOException{if(!p.id.equals("dc2dm-9d72"))throw new IOException("DM profile required");return waiting();}
    public boolean waiting(){return credits.active()||returnRequested||saveRequest>=0||loadRequest>=0||remaining!=0||audioMask!=0||ui.waiting||textWaiting||presentationWaiting||!movie.isEmpty();}
    public long waitTicket(){return ticket;}
    /** Diagnostics for tools: which of the wait conditions is currently holding. */
    public long remainingMs(){return remaining;}
    public int audioMask(){return audioMask;}
    public long remainingMillis(){return remaining;}
    protected void nextTicket()throws IOException{if(ticket==Long.MAX_VALUE)throw new IOException("DM event counter exhausted");ticket++;}
    public void elapse(long ms){dmClock+=Math.max(0,ms);if(musicFadeRemaining>0){musicFadeRemaining=Math.max(0,musicFadeRemaining-ms);if(musicFadeRemaining==0){play(0,"",false);music=-1;}}endingClock+=ms;boolean rolling=credits.active();credits.elapse(ms);if(rolling&&!credits.active())try{state.write(100,29,1);}catch(IOException impossible){throw new IllegalStateException(impossible);}scene.elapse(ms);if(remaining>0)remaining=Math.max(0,remaining-ms);pollAudio();}
    protected void delay(long ms)throws IOException{remaining=ms;if(ms!=0)nextTicket();}
    public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{if(expected!=ticket||!waiting()||ui.waiting)return false;
        if(followsDm()&&signal==MesVm.HostSignal.ADVANCE){ // 4334E0: fades and plain waits take no click. 42C1A2: moving on stops the voice.
            if(!textWaiting&&!presentationWaiting&&movie.isEmpty()&&remaining!=0&&!(dmTimedWait&&dmDismissable))return false;
            if(textWaiting&&!presentationWaiting&&read(99,222)==0)play(5,"",false);
        }
        if(!movie.isEmpty()&&signal==MesVm.HostSignal.MEDIA_COMPLETE){movie="";return true;}
        if(remaining!=0&&signal==MesVm.HostSignal.ADVANCE){scene.elapse(86400000);remaining=0;return true;}
        if(presentationWaiting&&(signal==MesVm.HostSignal.TEXT_COMPLETE||signal==MesVm.HostSignal.ADVANCE)){presentationWaiting=false;textRendered=true;return true;}
        if(textWaiting&&signal==MesVm.HostSignal.ADVANCE){textWaiting=false;return true;}
        return false;
    }
    public void audioCompleted(int ch,int generation){if(ch>=0&&ch<6&&audioGeneration[ch]==generation){audioActive[ch]=false;if(ch==0&&musicRoom.active&&!musicRoom.stopped){if(musicRoom.mode==0){musicRoom.stopped=true;musicRoom.buttons(ui);}else{if(musicRoom.mode!=1)musicRoom.next(1);roomPlay();}}}pollAudio();}
    private void pollAudio(){boolean busy=(audioMask&1)!=0&&audioActive[0]||(audioMask&2)!=0&&audioActive[5];if((audioMask&4)!=0)for(int n=1;n<5;n++)busy|=audioActive[n];if(!busy)audioMask=0;}
    protected void play(int ch,String name,boolean loop){if(ch==0)musicFadeRemaining=0;audio[ch]=name;audioLoop[ch]=loop;audioActive[ch]=!name.isEmpty();audioGeneration[ch]++;}
    public int read(int bank,int index)throws IOException{if(bank==94||bank==95){require(index>=0&&index<128,"voice character");return voicePreferences[index*2+(bank==94?1:0)]&255;}
        if(bank==99){switch(index){
            case 4:return fullscreen?1:0;case 3:case 219:return 1; // Android fullscreen/window exists; no desktop fullscreen transition.
            case 217:int selected=registers.getOrDefault(218,0);require(selected>=0&&selected<20,"confirmation selector");return confirmations[selected];
            case 218:return registers.getOrDefault(218,0);
            case 21:return availableSaves.contains(1)?1:0; // 4278D0(1): newest quick save, resolved by portable slot store.
            case 227:return registers.get(227); // 434542 toggles the system toolbar; initial state is zero.
            case 228:return replayMode?1:0; // C29180: replay entry, inactive during ordinary new game.
            case 223:return followsDm()&&ctrlHeld?1:0; // 424820: physical Ctrl key held; touchscreen has no held Ctrl key.
            case 17:return registers.get(17); // 78F400: scene-start prompt, captured default.
            case 12:return registers.get(12); // 78F3FC: captured transition/scroll speed.
            case 26:case 27:return registers.get(index); // 11 - captured text/auto speed (3).
            case 29:return registers.get(29); // 20 - captured auto delay (3).
            case 13:return registers.containsKey(13)?registers.get(13):0;
            case 245:case 246:return registers.get(index);
            case 251:return scriptId();
            case 255:return state.read(99,255);
            case 244:return music;
            case 248:return followsDm()?(audioActive[1]?0:1):audioActive[1]?1:0; // 4294CB: 1 means idle, and these are SE, voice, BGM.
            case 249:return followsDm()?(audioActive[5]?0:1):audioActive[0]?1:0;
            case 250:return followsDm()?(audioActive[0]?0:1):audioActive[5]?1:0;
            default:if(registers.containsKey(index))return registers.get(index);
        }}return state.read(bank,index);
    }
    public void write(int bank,int index,int value)throws IOException{if(bank==94||bank==95){require(index>=0&&index<128,"voice character");voicePreferences[index*2+(bank==94?1:0)]=(byte)value;return;}if(bank==99&&index==218){require(value>=0&&value<20,"confirmation selector");registers.put(218,value);return;}if(bank==99&&index==217){confirmations[read(99,218)]=value;return;}if(bank==99&&index==255){state.indirectResult(value==255?-1:value);return;}if(bank==99&&registers.containsKey(index)){registers.put(index,value);return;}state.write(bank,index,value);}
    private int scriptId()throws IOException{if(script==null)return -1;List<NamesCatalog.Entry> list=resources.names.table("scenario_scripts");for(int n=0;n<list.size();n++)if(list.get(n).name.equalsIgnoreCase(script.name))return n;return -1;}
    /** The Mac app's PC log only reads back text and voice, so no rewind snapshots or previews are kept. */
    public boolean keepsOnlyLogText(){return false;}
    public boolean allowNamedJump(){return true;}
    public boolean beforeTransfer(int mode,String name)throws IOException{
        require(mode>=1&&mode<=3,"transfer mode "+mode);
        if(script!=null&&script.name.toUpperCase(Locale.ROOT).startsWith("SCE_")&&catalogIndex("scenario_scripts",name)>=0)replayMode=true;
        if(name.equalsIgnoreCase("MAIN")||name.equalsIgnoreCase("SC_END"))replayMode=false;
        unlock("scenario_scripts",name,98); // 432887 -> 428270 records entry into a scenario. // 428FCA..429198
        ui.buttons.clear();grid.clear();gridWaiting=confirming=false;ui.hovered=ui.pressed=-1;ui.waiting=false;checkpoint=0;speaker="";body="";textWaiting=false;
        if(mode!=2){Arrays.fill(ui.actions,2,ui.actions.length,"");variables.write(6,90,255);}
        if(mode==1){scene.reset();ui.quads.clear();}return true;
    }
    public byte[] automaticMenuAction(){byte[] a=automaticMenuAction;automaticMenuAction=null;return a;}
    public byte[] menuAction(long expected,int button)throws IOException{
        if(followsDm()&&button<0&&!ui.waiting&&remaining!=0&&expected==ticket){String timed=ui.actions[button==-2?2:3];return timed.isEmpty()?null:timed.getBytes(Binary.SJIS);} // 4333B0: right goes to slot 3, left to slot 2.
        if((!ui.waiting&&remaining>=0)||expected!=ticket)return null;if(button<0){String back=ui.actions[confirming?1:3];return back.isEmpty()?null:back.getBytes(Binary.SJIS);}require(button<ui.buttons.size(),"button index");String action=ui.buttons.get(button).action;if(confirming){if(action.equals(":y"))action=ui.actions[0];else if(action.equals(":n"))action=ui.actions[1];}return action.isEmpty()?null:action.getBytes(Binary.SJIS);}
    public void acceptMenuAction(int button)throws IOException{remaining=0;if(button>=0){registers.put(245,button);int sound=ui.buttons.get(button).clickSound;if(sound!=255)play(1,resources.named("sound",sound),false);if(!ui.buttons.get(button).voice.isEmpty())playVoice(ui.buttons.get(button).voice);}ui.waiting=false;ui.pressed=ui.hovered=-1;choiceCount=0;gridWaiting=confirming=false;}
    public boolean effectsEnabled()throws IOException{return read(99,10)!=0;}
    public void hoverMenu(int button)throws IOException{if(button==ui.hovered)return;require(button>=-1&&button<ui.buttons.size(),"hover");ui.hovered=button;if(button>=0){registers.put(245,button);int sound=ui.buttons.get(button).hoverSound;if(sound!=255)play(1,resources.named("sound",sound),false);}}
    private static void require(boolean b,String detail)throws IOException{if(!b)throw new IOException("Unimplemented DM "+detail);}
    private String text(byte[] b)throws IOException{return TextCodec.cp932(b);}
    protected String imageName(String s)throws IOException{if(s.startsWith("*")){try{return resources.named("images",Integer.parseInt(s.substring(1)));}catch(NumberFormatException e){throw new IOException("Invalid DM image index",e);}}return s;}
    protected int voiceCharacter(String name){
        // 436360: SYS folder, then three-byte suffix after the last underscore.
        if(name.replace('/', '\\').toUpperCase(Locale.ROOT).contains("\\SYS"))return 18;
        String[] tags={"otm","yme","kko","nnk","mnt","anz","akn","mya","myk","erk","wtr","sgn","mik","hiv","skr","rcc","yuk"};int at=name.lastIndexOf('_');
        if(at>=0&&name.length()>=at+4){String tag=name.substring(at+1,at+4);for(int n=0;n<tags.length;n++)if(tag.equalsIgnoreCase(tags[n]))return n;}
        return 17;
    }
    private boolean voiceForced;
    /**
     * The 2006 system scripts clear the voice channel with "--" right after suppressing the BGM and
     * effect channels. Neither host may treat it as a resource name.
     */
    protected static boolean voiceStops(String source){return source.equals("-")||source.equals("--");}
    protected void playVoice(String source)throws IOException{
        if(voiceStops(source)){play(5,"",false);return;}
        String name=source;voicePan=name.startsWith("<")?-1000:name.startsWith(">")?1000:0;if(name.startsWith("<")||name.startsWith(">")||name.startsWith("="))name=name.substring(1);
        voiceCharacter=voiceCharacter(name);voiceForced=name.startsWith("#")||name.toUpperCase(Locale.ROOT).contains("SYS");if(name.startsWith("#"))name=name.substring(1);int semi=name.indexOf(';');if(semi>=0){voiceCharacter=Math.max(0,Math.min(127,name.charAt(0)-'a'));name=name.substring(semi+1);}
        play(5,voiceForced||read(95,voiceCharacter)!=0?name:"",false);
    }
    public float voiceGain()throws IOException{if(!voiceForced&&!audio[5].toUpperCase(Locale.ROOT).contains("SYS")&&read(95,voiceCharacter)==0)return 0;return Math.max(0,Math.min(99,read(94,voiceCharacter)))/99f;}
    /** Effect 9 snapshots the displayed composition into the background texture. */
    protected void captureUi()throws IOException {
        scene.publish(ui,0,0);Dc2Scene.Frame captured=new Dc2Scene.Frame();ByteArrayOutputStream bytes=new ByteArrayOutputStream();scene.shown.save(new DataOutputStream(bytes));captured.load(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for(int n=0;n<ui.buttons.size();n++){Dc2Ui.Button b=ui.buttons.get(n);boolean slider=false;for(Dc2Ui.Quad q:b.sprites)slider|=q.state==5;
            for(Dc2Ui.Quad q:b.sprites)if(q.state==99||q.state==0||q.state==1&&n==ui.hovered||q.state==2&&!slider&&(b.action.equals(":c")?confirmationChecked():n==ui.pressed)){
                Dc2Ui.Quad copy=Dc2Ui.copyQuads(Collections.singletonList(q)).get(0);if(copy.type==2){copy.width=23;copy.height=copy.flags;}captured.quads.add(copy);
            }
        }
        SceneCaptures.legacySliders(captured,ui,variables);UiCapture next=SceneCaptures.legacy(captured,scene,ui,variables,effectsEnabled(),uiCapture);uiCapture=captureRenderer==null?next:captureRenderer.flatten(next);
        scene.slots[1]="@ui-capture";Arrays.fill(scene.slots,2,10,"");scene.backgroundX=scene.backgroundY=0;
    }
    public boolean command(String op,int[] a,byte[] bytes)throws IOException{
        if(followsDm()&&!op.equals("dm.wait.duration"))dmTimedWait=dmDismissable=false;
        switch(op){
        // Record shape and PC effect are verified from the 2006 handler, but the engine state it
        // touches is not modelled yet. Recording it keeps a missing model from masquerading as
        // "no effect" while still letting the rest of the script run.
        case "native.unmodelled":unmodelled.add(String.format("%s %s %s",script==null?"?":script.name,Integer.toHexString(pc),Arrays.toString(a)));return true;
        case "system.exit":exitRequested=true;return true;
        case "dm.system.return":returnRequested=true;nextTicket();return true;
        // 411670 branches on kinds 0..6 (4117F1 is the last). Kinds 5 and 6 have no model here,
        // so they are recorded instead of being drawn as some neighbouring effect.
        case "dm.scene.effect":{
            if(a[0]==5||a[0]==6){unmodelled.add("effect "+a[0]+","+a[1]);return true;}
            scene.effect(a[0],a[1]);return true;
        }
        case "dm.ui.grid_begin":grid.clear();gridWaiting=false;registers.put(246,0);registers.put(253,255);registers.put(254,255);return true;
        case "dm.ui.grid_rect":{Dc2Ui.Button b=new Dc2Ui.Button();b.left=a[0];b.top=a[1];b.right=a[2];b.bottom=a[3];require(grid.size()<50,"grid capacity");grid.add(b);return true;}
        case "dm.ui.grid_sound":gridHoverSound=a[0];gridClickSound=a[1];return true;
        case "dm.ui.grid_wait":gridWaiting=ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "dm.ui.confirm_position":require(a[2]==0,"confirmation mode");confirmX=a[0];confirmY=a[1];confirmKind=a[3];return true;
        case "dm.ui.confirm":{
            if(confirmKind>=0&&confirmKind<20&&confirmations[confirmKind]!=0){automaticMenuAction=ui.actions[0].getBytes(Binary.SJIS);return true;}
            List<Dc2Ui.Button> list=Dc2Ui.copyButtons(ui.buttonTemplates.get(2));require(list.size()>=3,"confirmation template");
            Dc2Ui.Button panel=list.get(0);int dx=confirmX-panel.left,dy=confirmY-panel.top;
            for(Dc2Ui.Button b:list){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(Dc2Ui.Quad q:b.sprites){q.x+=dx;q.y+=dy;}}
            List<Dc2Ui.Quad> p=new ArrayList<>();if(!panel.sprites.isEmpty())p.add(panel.sprites.get(0));if(confirmKind>=0&&confirmKind+2<panel.sprites.size())p.add(panel.sprites.get(confirmKind+2));for(Dc2Ui.Quad q:p)q.state=99;panel.sprites.clear();panel.sprites.addAll(p);
            ui.buttons.clear();ui.buttons.addAll(list);confirming=ui.waiting=true;gridWaiting=false;nextTicket();return true;
        }
        case "dm.choice.begin":choiceMode=a[0];choiceCount=0;ui.buttons.clear();return true;
        case "text.speaker":speaker=Dc2Text.plain(bytes,codec);speakerPending=true;return true;
        case "dm.movie":movie="AdvData/MOVIE/"+Binary.safePath(text(bytes));movieGeneration++;play(0,"",false);nextTicket();return true; // 432D17..432D31 passes MOVIE to native path builder 422C20.
        case "dm.save.checkpoint":checkpointLabel=text(bytes);return true;
        case "text.body":scene.publish(ui,0,0);if(!speakerPending||messageMode!=0)speaker="";speakerPending=false;body=Dc2Text.plain(bytes,codec);unlock("scenario_scripts",script.name,98);if(!body.isEmpty())history.append(speaker,body,lineVoice,script.name,pc);lineVoice="";presentationWaiting=true;textRendered=false;scene.revision++;nextTicket();return true;
        case "dm.wait.message":textWaiting=true;nextTicket();return true;
        case "variable.indirect_result":state.indirectResult(a[0]);return true;
        case "system.version":ui.version+=text(bytes);require(ui.version.length()<128,"version length");return true;
        case "dm.ui.quad_template":ui.storeQuads(a[0],a[1]);if(a[0]==0&&scene.mode==0)scene.mode=2;return true;
        case "dm.ui.button_template":ui.storeButtons(a[0]);return true;
        case "dm.ui.resource":require(a[0]>=0&&a[0]<5,"UI slot");if(!text(bytes).equals("-"))scene.slots[21+a[0]]=text(bytes);return true;
        case "host.command_with_string":ui.button(a,bytes);return true;
        case "dm.ui.button_voice":ui.last().voice=text(bytes);if(followsDm())ui.last().clickSound=255;return true; // 42D834
        // 40DE20 indexes the button table with 988C9C-1 and returns before writing when that is negative,
        // so LOAD/SAVE (which open their page with 54h before creating any 4Eh button) must stay a no-op.
        case "dm.ui.button_sound":{if(ui.buttons.isEmpty())return true;ui.last().hoverSound=a[0];ui.last().clickSound=a[1];return true;}
        case "dm.ui.button_sprite":ui.quad(a,true);return true;
        case "ui.button_text":ui.text(a,bytes,true);return true;
        case "dm.ui.text":ui.text(a,bytes,false);return true;
        case "dm.ui.image_quad":ui.quad(a,false);return true;
        case "dm.ui.binding":ui.binding=(a[0]<<8)+a[1];return true;
        case "dm.ui.navigation":ui.last().navigation=a.clone();return true;
        case "dm.ui.palette":require(a[0]>=0&&a[0]<64,"palette");ui.palette[a[0]]=0xff000000|(a[1]&255)<<16|(a[2]&255)<<8|(a[3]&255);return true;
        case "dm.ui.origin":switch(a[2]){case 0:ui.x=a[0];ui.y=a[1];break;case 1:ui.x+=a[0];ui.y+=a[1];break;case 2:ui.sourceX=a[0];ui.sourceY=a[1];break;case 3:int v=variables.read(a[3]>>8,a[3]&255);ui.sourceX=v*a[0];ui.sourceY=v*a[1];break;default:throw new IOException("DM origin mode");}return true;
        case "dm.ui.animate":require(a[1]<=256,"character animation");if(!ui.quads.isEmpty()){Dc2Ui.Quad q=ui.quads.get(ui.quads.size()-1);q.animation=a[0];q.frames=a[1];q.step=a[2];q.period=a[3];}return true;
        case "dm.ui.action":require(a[0]>=0&&a[0]<ui.actions.length,"action slot");ui.actions[a[0]]=text(bytes);return true;
        case "dm.ui.commit":scene.publish(ui,0,0);return true;
        case "dm.ui.wait":require(a[0]==0||a[0]==2,"menu mode");ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "checkpoint":checkpoint=a[0];return true;
        case "dm.history.mark":checkpoint=a[0]|a[1]<<8;readCheckpoints.add(script.name+":"+checkpoint);unlock("scenario_scripts",script.name,98);return true;
        case "dm.state.reset":if(a[0]==0){history.clear();replayMode=false;variables.clearOrdinary();for(int n=0;n<=20;n++)variables.write(10,n,state.read(100,n));}variables.clearHistory();return true;
        case "dm.scene.preload":{
            int slot=a[0];String name=imageName(text(bytes));require(slot>=0&&slot<=20,"preload slot "+slot);
            if(slot==0){resources.scripts().script(name);return true;}
            if(name.equals("--")){if(systemScene!=null)scene.slots[slot]=systemScene.slots[slot];return true;}if(name.equals("-")||name.isEmpty()){scene.slots[slot]="";return true;}scene.slots[slot]=name;unlock("images",name,97);return true;
        }
        case "dm.scene.background":scene.mode=1;ui.x=ui.y=ui.sourceX=ui.sourceY=0;scene.backgroundX=scene.backgroundY=0;ui.quads.clear();for(int n=2;n<=9;n++)scene.slots[n]="";speaker="";body="";return true;
        case "dm.scene.portrait":if(bytes.length==0||text(bytes).equals("-")){scene.slots[18]=scene.slots[19]="";scene.revision++;}return true;
        case "dm.scene.character":case "dm.scene.character_overlay":{int n=a[0];require(n>=0&&n<4,"character slot");int slot=n+(op.equals("dm.scene.character")?2:6);String name=text(bytes);if(name.equals("--")&&systemScene!=null){scene.slots[slot]=systemScene.slots[slot];System.arraycopy(systemScene.actors[n],0,scene.characters[n],0,6);}else if(name.equals("-")){scene.slots[slot]="";if(slot<6)scene.slots[slot+4]="";}else if(!name.isEmpty()&&!name.equals(".")&&!name.equals("--")){scene.slots[slot]=imageName(name);unlock("images",scene.slots[slot],97);}scene.revision++;return true;}
        case "dm.scene.character_move":{
            int n=a[2];if(n==-1){for(int base:new int[]{2,6}){String temp=scene.slots[base];scene.slots[base]=scene.slots[base+1];scene.slots[base+1]=temp;}return true;}
            if((n&16)!=0){n&=15;require(n<4,"character gesture");scene.actorEffect[n]=(a[0]&15)==1?51:(a[0]&15)==2?52:0;return true;}
            require(n>=0&&n<4,"character move index");scene.characters[n][0]=a[0];scene.characters[n][1]=a[1];return true;
        }
        case "dm.scene.character_transform":{int mode=a[3],n=mode==0?a[2]:mode&15;if(mode==255)return true;require(n>=0&&n<4,"character transform index");int[] c=scene.characters[n];if(mode==0){c[0]=a[0];c[1]=a[1];c[2]=100;c[3]=c[4]=0;}else if((mode&16)!=0){c[2]=a[0];c[3]=a[1];c[4]=a[2];}else if((mode&32)!=0)c[5]=a[0];return true;}
        case "dm.scene.background_origin":require(a[3]==0,"background transform");scene.portraitX=a[0];scene.portraitY=a[1];return true;
        case "dm.scene.scroll":if(a[2]==1){scene.backgroundX=a[0];scene.backgroundY=a[1];scene.scrollStep=0;return true;}require(a[2]==0||a[2]>=8,"scroll mode");scene.targetX=a[0];scene.targetY=a[1];scene.scrollStep=a[2]==0?(followsDm()?(ctrlHeld?16:new int[]{2,4,8,16}[Math.max(0,Math.min(3,read(99,12)))]):8):Math.max(1,a[2]-8);scene.scrollPeriod=34;scene.scrollClock=0;delay((Math.max(Math.abs((long)a[0]-scene.backgroundX),Math.abs((long)a[1]-scene.backgroundY))+scene.scrollStep-1)/scene.scrollStep*34+340);return true;
        case "dm.scene.transition":{
            int kind=a[0];
            if(kind==9){captureUi();return true;}
            if(kind==51||kind==52){for(int n=0;n<4;n++)if((a[1]&(1<<n))!=0)scene.actorEffect[n]=kind;return true;}
            require(kind==0||kind==1||kind==2||kind==3||kind==4||kind==5||kind==6||kind==8||kind==16||kind==17||kind==20||kind==21||kind==30||kind==31||kind==32,"transition "+Arrays.toString(a));
            if(kind==32)return true;
            long duration=(kind==30||kind==31)?0:kind==5?68:kind==6?306:200L*Math.max(1,a[1]);
            if(followsDm()&&kind<=2)duration=dmFadeMillis(a[1]);else if(followsDm()&&kind==3)duration=68L*a[1];else if(followsDm()&&kind==4)duration=306; // 431750, 4315B2
            if(kind==2||kind==16||kind==17||kind==20){speaker="";body="";}
            scene.publish(ui,kind,duration);delay(duration);return true;
        }
        case "dm.wait.duration":if(followsDm()){if(scene.differs(ui))scene.publish(ui,0,0);dmDismissable=(a[1]&1)!=0;dmTimedWait=true;delay(dmWaitMillis(a));return true;}scene.publish(ui,0,0);delay(a[0]==0?-1:a[0]*200L);return true;
        case "dm.wait.mode":if(a[0]==0){if(choiceCount>0){ui.waiting=true;scene.publish(ui,0,0);nextTicket();}else delay(-1);}else waitIndicatorStyle=a[1];return true;
        case "dm.audio.voice_prepare":return true; // 436940 loads the following voice into the native cache; no playback.
        case "dm.audio.voice":case "dm.audio.voice_wait":{
            String voice=text(bytes);lineVoice=voice.equals("--")||voice.equals("-")?"":voice;playVoice(voice);return true;
        }
        case "dm.audio.completion_action":require(bytes.length==0,"music completion label");return true; // 4305CA clears C22490 on music-room exit.
        case "dm.audio.music_attenuation":musicAttenuation=a[0];return true;
        // 42E91C -> 435E60 starts BGM fade; 432A9E subsequently waits for its completion.
        case "dm.audio.fade_music":if(audioActive[0]&&musicFadeRemaining==0)musicFadeRemaining=musicFadeLength=fadeOutMillis();return true;
        case "dm.audio.sound":if(a[0]==-2){play(5,"",false);return true;}if(a[0]==-1){if(followsDm()&&audioActive[1]&&audio[1].equals(resources.named("sound",223)))return true; /* 436120: a stop lets the 223 sound finish. */play(1,"",false);return true;}require(a[0]>=0,"sound control");play(1,resources.named("sound",a[0]),a[1]!=0);return true;
        case "dm.audio.music":if(a[0]==255){play(0,"",false);music=-1;return true;}require(a[0]>=0&&a[0]<400&&a[1]>=0&&a[1]<=2,"music mode");if(a[1]!=1||music!=a[0])play(0,resources.named("music",a[0]),a[1]!=2);music=a[0];state.write(96,music,1);return true;
        case "wait.audio":audioMask=a[0];pollAudio();if(audioMask!=0)nextTicket();return true;
        case "dm.system.persist":switch(a[0]){
            case 2:case 3:return true; // Desktop visibility; Activity owns the window.
            case 0:loadRequest=read(99,254);nextTicket();return true;
            case 4:if(a[1]==1)autosaveGeneration++;else {saveRequest=read(99,254);nextTicket();}return true;
            case 5:case 6:variables.appendHistory();return true;
            case 7:skipAllowed=a[1]!=0;return true;
            case 8:windowRequest=a[1];fullscreen=a[1]!=0;return true;
            case 10:resources.scripts().script("START");return true;
            case 11:messageMode=a[1];return true;
            case 15:inputMode=a[1];return true;
            case 20:for(int n=5;n<=30;n++)registers.put(n,new int[]{1,1,20,23,27,1,11,2,0,1,1,7,0,1,0,0,0,0,0,0,0,8,8,0,17,followsDm()?1:0}[n-5]);return true;
            case 18:return true; // Saved display state is owned by the session snapshot.
            default:throw new IOException("Unimplemented DM persistence command "+Arrays.toString(a));
        }
        case "dm.system.command":if(a[0]==7){if(a[1]==0){history.open();ui.waiting=true;historyButtons();scene.publish(ui,0,0);nextTicket();}else {history.active=false;play(5,"",false);}return true;}if(a[0]==213){
            musicRoom.prepare(resources,codec);musicRoom.active=true;musicRoom.stopped=true;musicRoom.select(Math.max(0,Math.min(72,variables.read(3,27))));musicRoom.mode=Math.min(3,variables.read(9,30));if(variables.read(3,28)==0){variables.write(3,28,1);musicRoom.mode=1;}musicRoom.buttons(ui);ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        }if(a[0]==209){credits.start(resources,endingClock);if(!credits.active())state.write(100,29,1);nextTicket();return true;}if(a[0]==208){endingClock=0;return true;}if(a[0]==18){if(a[1]==0){optionBaseline.clear();optionBaseline.putAll(registers);}else{boolean changed=false;for(int n=5;n<=30;n++)changed|=!Objects.equals(registers.get(n),optionBaseline.get(n));variables.write(9,35,changed?1:0);}return true;}if(a[0]==207){
            int year=variables.read(5,28)==0?2059:2000+variables.read(5,28),month=Math.max(1,variables.read(5,26));String archive=String.format(java.util.Locale.ROOT,"SYSTEM/calender%02d.PCK",year-2059);
            if(!resources.files.paths().contains(("AdvData/GRP/"+archive).toLowerCase(java.util.Locale.ROOT)))archive="SYSTEM/calenderdef.PCK";
            Archive pack=resources.files.archive("AdvData/GRP/"+archive);require(month<=pack.entries.size(),"calendar month");
            scene.slots[1]="pck:"+archive+"#"+(month-1);variables.write(5,29,0);scene.publish(ui,0,0);delay(1600);return true;
        }if(a[0]==6){ // 404F50 case 6: a[1] is the page kind. SAVE.mes sends (6,0), LOAD.mes (6,1).
            saveGrid=a.length>1?a[1]==0:script.name.equalsIgnoreCase("SAVE");return true;}if(a[0]==100){require(read(99,1)==0,"save context text");return true;}if(a[0]>=214)return true;if(a[0]==19&&a[1]==0){variables.write(9,87,availableSaves.contains(read(99,254))?0:1);return true;}
        // Original D.C.II packs 50 sub-commands into 404F50; only the ones above are modelled. Recording the
        // rest keeps a missing model visible instead of aborting the save/load screen that issued it.
        if(resources.profile.id.equals("dc2-3363")){unmodelled.add("system "+Arrays.toString(a));return true;}
        throw new IOException("Unimplemented DM command "+Arrays.toString(a));
        case "native.no_effect":return true;
        default:return false;
        }
    }
    protected int catalogIndex(String table,String reference)throws IOException {
        String name=reference.replace('\\','/').toUpperCase(Locale.ROOT);int dot=name.indexOf('.');if(dot>=0)name=name.substring(0,dot);
        for(NamesCatalog.Entry e:resources.names.table(table))if(e.name.replace('\\','/').equalsIgnoreCase(name))return e.index;return -1;
    }
    protected void unlock(String table,String name,int bank)throws IOException {int index=catalogIndex(table,name);if(index>=0)state.write(bank,index,1);}
    public void toolbarSound(Dc2Ui.Button b)throws IOException {if(b.clickSound!=255)play(1,resources.named("sound",b.clickSound),false);if(!b.voice.isEmpty())playVoice(b.voice);}
    public void historyButtons(){
        ui.buttons.clear();Dc2MusicRoom.button(ui,"@log:exit",936,504,1020,572,0,0,10);
        Dc2MusicRoom.button(ui,"@log:up",120,136,191,176,156,0,10);
        Dc2MusicRoom.button(ui,"@log:down",120,512,191,552,84,0,10);
        Dc2MusicRoom.button(ui,"@log:up",944,88,984,112,-1,0,10);Dc2MusicRoom.button(ui,"@log:down",944,444,984,468,-1,0,10);
        if(history.selected>=0&&history.selected<history.entries.size()&&history.entries.get(history.selected).resume.length>0)Dc2MusicRoom.button(ui,"@log:rewind",28,66,188,110,0,204,10);
        for(int n=history.cursor;n<Math.min(history.entries.size(),history.cursor+4);n++)Dc2MusicRoom.button(ui,"@log:voice:"+n,200,25+(n-history.cursor)*133,880,145+(n-history.cursor)*133,-1,0,10);
    }
    private void roomPlay(){musicRoom.stopped=false;music=Dc2NativeData.MUSIC[musicRoom.selected];play(0,musicRoom.tracks.get(musicRoom.selected),false);musicRoom.buttons(ui);}
    public boolean internalMenuAction(long expected,int button)throws IOException {
        if(expected!=ticket||!ui.waiting)return false;
        if(confirming&&button>=0&&button<ui.buttons.size()&&ui.buttons.get(button).action.equals(":c")){require(confirmKind>=0&&confirmKind<20,"confirmation kind");confirmations[confirmKind]=confirmations[confirmKind]==0?1:0;nextTicket();return true;}
        if(!history.active&&!musicRoom.active)return false;
        String a=button<0?history.active?"@log:exit":"@music:exit":button<ui.buttons.size()?ui.buttons.get(button).action:"";
        if(a.startsWith("@log:")){
            if(a.equals("@log:exit")){history.active=false;ui.waiting=false;ui.buttons.clear();play(5,"",false);}
            else if(a.equals("@log:up"))history.scroll(-1);
            else if(a.equals("@log:down"))history.scroll(1);
            else if(a.equals("@log:rewind"))history.rewindRequest=history.selected;
            else if(a.startsWith("@log:voice:")){int n=Integer.parseInt(a.substring(11));history.selected=n;playVoice(history.entries.get(n).voice);}
            if(history.active)historyButtons();return true;
        }
        if(a.startsWith("@music:")){
            if(a.equals("@music:exit")){variables.write(3,27,musicRoom.selected);variables.write(7,98,musicRoom.selected);variables.write(9,30,musicRoom.mode);musicRoom.active=false;ui.waiting=false;ui.buttons.clear();}
            else if(a.equals("@music:prev")||a.equals("@music:next")){musicRoom.next(a.endsWith("prev")?-1:1);if(!musicRoom.stopped)roomPlay();}
            else if(a.equals("@music:play")){if(musicRoom.stopped)roomPlay();else{play(0,"",false);music=-1;musicRoom.stopped=true;}}
            else if(a.startsWith("@music:track:")){musicRoom.select(Integer.parseInt(a.substring(13)));musicRoom.resetShuffle();roomPlay();}
            else {int mode=a.endsWith("one")?1:a.endsWith("all")?2:3;musicRoom.mode=musicRoom.mode==mode?0:mode;musicRoom.resetShuffle();}
            if(musicRoom.active)musicRoom.buttons(ui);return true;
        }return false;
    }
    public void save(DataOutput o)throws IOException{o.writeInt(0x444d4836);o.writeLong(endingClock);o.writeLong(musicFadeRemaining);o.writeInt(musicAttenuation);o.writeInt(optionBaseline.size());for(Map.Entry<Integer,Integer> e:optionBaseline.entrySet()){o.writeInt(e.getKey());o.writeInt(e.getValue());}o.writeBoolean(returnRequested);o.writeInt(saveRequest);o.writeInt(loadRequest);o.writeBoolean(systemScene!=null);if(systemScene!=null)systemScene.save(o);Dc2Ui.saveButtons(o,grid);o.writeBoolean(gridWaiting);o.writeBoolean(saveGrid);o.writeInt(gridHoverSound);o.writeInt(gridClickSound);o.writeInt(confirmX);o.writeInt(confirmY);o.writeInt(confirmKind);o.writeBoolean(confirming);o.writeUTF(codec.fingerprint);state.save(o);ui.save(o);scene.save(o);credits.save(o);o.writeLong(ticket);o.writeLong(remaining);for(int v:new int[]{audioMask,checkpoint,music,messageMode,inputMode})o.writeInt(v);o.writeBoolean(exitRequested);o.writeBoolean(skipAllowed);o.writeBoolean(textWaiting);o.writeBoolean(textRendered);o.writeBoolean(presentationWaiting);o.writeBoolean(speakerPending);o.writeInt(waitIndicatorStyle);o.writeUTF(movie);o.writeInt(movieGeneration);o.writeUTF(checkpointLabel);o.writeInt(autosaveGeneration);o.writeInt(choiceMode);o.writeInt(choiceCount);o.writeUTF(speaker);o.writeUTF(body);o.writeInt(registers.size());for(Map.Entry<Integer,Integer> e:registers.entrySet()){o.writeInt(e.getKey());o.writeInt(e.getValue());}o.writeInt(readCheckpoints.size());for(String s:readCheckpoints)o.writeUTF(s);for(int n=0;n<6;n++){o.writeUTF(audio[n]);o.writeInt(audioGeneration[n]);o.writeBoolean(audioLoop[n]);o.writeBoolean(audioActive[n]);}o.writeBoolean(replayMode);o.writeUTF(lineVoice);history.save(o);musicRoom.save(o);history.saveVisuals(o);for(int v:confirmations)o.writeInt(v);o.write(voicePreferences);o.writeInt(voiceCharacter);o.writeInt(voicePan);o.writeBoolean(voiceForced);o.writeBoolean(false);UiCapture.save(o,uiCapture);}
    public void load(DataInput i)throws IOException{uiCapture=null;int format=i.readInt();if(format!=0x444d4832&&format!=0x444d4833&&format!=0x444d4834&&format!=0x444d4835&&format!=0x444d4836)throw new IOException("DM host format");endingClock=i.readLong();musicFadeRemaining=i.readLong();musicAttenuation=i.readInt();optionBaseline.clear();int opts=i.readInt();require(opts>=0&&opts<=256,"option baseline");while(opts-->0)optionBaseline.put(i.readInt(),i.readInt());returnRequested=i.readBoolean();saveRequest=i.readInt();loadRequest=i.readInt();systemScene=i.readBoolean()?new Dc2Scene.Frame():null;if(systemScene!=null)systemScene.load(i);grid.clear();grid.addAll(Dc2Ui.loadButtons(i));gridWaiting=i.readBoolean();saveGrid=i.readBoolean();gridHoverSound=i.readInt();gridClickSound=i.readInt();confirmX=i.readInt();confirmY=i.readInt();confirmKind=i.readInt();confirming=i.readBoolean();if(!i.readUTF().equals(codec.fingerprint))throw new IOException("DM character map mismatch");state.load(i);ui.load(i);scene.load(i);credits.load(i);ticket=i.readLong();remaining=i.readLong();audioMask=i.readInt();checkpoint=i.readInt();music=i.readInt();messageMode=i.readInt();inputMode=i.readInt();exitRequested=i.readBoolean();skipAllowed=i.readBoolean();textWaiting=i.readBoolean();textRendered=i.readBoolean();presentationWaiting=i.readBoolean();speakerPending=i.readBoolean();waitIndicatorStyle=i.readInt();movie=i.readUTF();if(!(this instanceof PcHost)&&movie.regionMatches(true,0,"AdvData/",0,8)&&movie.indexOf('/',8)<0&&!movie.isEmpty())movie="AdvData/MOVIE/"+Binary.safePath(movie.substring(8));movieGeneration=i.readInt();checkpointLabel=i.readUTF();autosaveGeneration=i.readInt();choiceMode=i.readInt();choiceCount=i.readInt();speaker=i.readUTF();body=i.readUTF();registers.clear();int n=i.readInt();require(n>=0&&n<=256,"register count");while(n-->0)registers.put(i.readInt(),i.readInt());readCheckpoints.clear();n=i.readInt();require(n>=0&&n<=100000,"read count");while(n-->0)readCheckpoints.add(i.readUTF());for(n=0;n<6;n++){audio[n]=i.readUTF();audioGeneration[n]=i.readInt();audioLoop[n]=i.readBoolean();audioActive[n]=i.readBoolean();}if(format>=0x444d4833){replayMode=i.readBoolean();lineVoice=i.readUTF();history.load(i);musicRoom.load(i);if(musicRoom.active)musicRoom.prepare(resources,codec);}if(format>=0x444d4834){history.loadVisuals(i);for(int k=0;k<20;k++)confirmations[k]=i.readInt();i.readFully(voicePreferences);voiceCharacter=i.readInt();voicePan=i.readInt();voiceForced=format>=0x444d4835&&i.readBoolean();require(voiceCharacter>=0&&voiceCharacter<128,"saved voice character");if(i.readBoolean()){Dc2Scene.Frame captured=new Dc2Scene.Frame();captured.load(i);uiCapture=SceneCaptures.legacy(captured,scene,ui,variables,read(99,10)!=0,null);}}if(format>=0x444d4836)uiCapture=UiCapture.load(i);for(int key:new int[]{215,243,244,255})if(!registers.containsKey(key))registers.put(key,0);defaultDmPalette(true);}
}
