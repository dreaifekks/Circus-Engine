package org.circus.core;

import java.io.*;
import java.util.*;

/** DD host: legacy story audit or original-system execution. Unknown submodes stop. */
public final class Dc3Host implements MesVm.ContextHost,MesVm.ChoiceHost,MesVm.TitleHost,MesVm.MenuHost {
    public final Set<Integer> availableSaves=new HashSet<>();
    public final Dc2History history=new Dc2History();private int scriptOffset;private String historyVoice="";
    public boolean returnRequested;public int saveRequest=-1,loadRequest=-1;
    public interface SettingsLoader {void load(Dc3State state)throws IOException;}
    public SettingsLoader settingsLoader;
    public int windowRequest=-1;public boolean fullscreen;public boolean windowSizeReset;private int musicVolumeBackup;
    public UiCapture uiCapture;public UiCapture.Renderer captureRenderer;
    public Dc3Scene.Frame systemScene;
    public void enterSystem(){systemScene=scene.capture();dialogue.suspend();wasDialogue=false;remaining=0;audioWaitMask=0;ui.clear();movie="";audio[5]="";audioActive[5]=false;audioGeneration[5]++;}
    public final Dc3Ui ui=new Dc3Ui();public final boolean originalSystem;
    private byte[] settingsBackup;private boolean settingsEditing;
    private byte[] automaticMenuAction;public final ScriptList list=new ScriptList();public final Dc3Music musicRoom=new Dc3Music();private String musicEndAction="";private long musicPollElapsed;
    public static final class Choice {
        private final byte[] target;public final String caption;
        Choice(byte[] target,String caption){this.target=target.clone();this.caption=caption;}
        public byte[] label(){return target.clone();}
    }
    private final List<Choice> choices=new ArrayList<>();private boolean choiceVisible;
    public List<Choice> choices(){return Collections.unmodifiableList(choices);}
    public boolean choosing(){return choiceVisible;}
    public void addChoice(byte[] label,byte[] caption)throws IOException {
        require(!choiceVisible&&choices.size()<50,"choice construction");
        if(label.length==0||label.length>255)throw new IOException("Invalid choice label");
        choices.add(new Choice(label,NativeText.plain(caption,dialogue.codec,dialogue.familyName,dialogue.givenName,false)));
    }
    public void selectChoice(byte[] label,int index)throws IOException {
        if(!choiceVisible||index<0||index>=choices.size()||!Arrays.equals(choices.get(index).target,label))throw new IOException("Stale or invalid choice");
        state.write(99,245,index);choiceVisible=false;variables.appendHistory();
    }
    public final DialogueHost dialogue;
    public final Dc3Scene scene=new Dc3Scene();
    public final Dc3State state;
    public final Dc3Performance performance=new Dc3Performance();
    public final Dc3Credits credits=new Dc3Credits();public boolean titleRequested,exitRequested;
    public final CircusResources resources;
    public final Dc3Gallery gallery;
    public final String[] audio={"","","","","",""};
    public final int[] audioGeneration=new int[6];
    public final boolean[] audioLoop=new boolean[6];
    private final boolean[] audioActive=new boolean[6];
    private final String[] pendingAudio=new String[6];
    private Variables variables;
    private String scriptName="",checkpointLabel="";
    private int checkpoint,checkpointMetadata,musicId=-1,imageTone;
    private long ticket,remaining;
    private boolean wasDialogue,interruptible;
    private int audioWaitMask;
    private int randomSeed=1;private long musicFadeRemaining,musicFadeDuration;public float musicGain=1;
    public boolean skipAllowed=true;
    public String movie="";
    public int movieGeneration;
    public int autosaveGeneration;
    public Dc3Host(CircusResources resources,TextCodec codec){this(resources,codec,false);}
    /** The D.C.III builds that share this host. Only the opcode numbering moves between
     *  them, and the profile carries it, so nothing else here is build-specific. */
    public static boolean supports(String id){return id.equals("dc3dd-a5a8")||id.equals("dc3wy-a09f")||id.equals("dc3pp-9872");}
    public static boolean supports(Profile p){return p!=null&&supports(p.id);}
    /** wait.message - the opcode that closes a line of dialogue. */
    public static int waitMessageOpcode(String id){return id.equals("dc3pp-9872")?0x07:0x06;}
    public Dc3Layout layout(){return scene.layout;}
    public Dc3Host(CircusResources resources,TextCodec codec,boolean originalSystem){if(resources!=null&&!supports(resources.profile.id))throw new IllegalArgumentException("DD host cannot execute "+resources.profile.id);this.resources=resources;gallery=new Dc3Gallery(resources);this.originalSystem=originalSystem;state=new Dc3State(originalSystem);dialogue=new DialogueHost(codec);dialogue.familyName="朝倉";dialogue.givenName="清隆";if(resources!=null)scene.layout=Dc3Layout.of(resources.profile.id);}
    public String checkpointLabel(){return checkpointLabel;}
    public void attach(Variables variables){this.variables=variables;}
    public void position(MesScript script,int pc){scriptName=script.name;scriptOffset=pc;}
    public long waitTicket(){return ticket;}
    public boolean waiting(){return returnRequested||saveRequest>=0||loadRequest>=0||ui.waiting||remaining!=0||dialogue.waiting()||audioWaitMask!=0||!movie.isEmpty()||credits.active();}
    public long remainingMillis(){return credits.active()?Math.min(1000,credits.end()-credits.elapsed):remaining;}
    public boolean audioIsActive(int channel){return audioActive[channel];}
    public boolean beforeInstruction(Profile profile,int opcode)throws IOException {
        if(!supports(profile.id))throw new IOException("DD host requires a D.C.III semantic profile, got "+profile.id);
        boolean wait=dialogue.beforeInstruction(profile,opcode);syncDialogue();if(wait)publish();return waiting();
    }
    private void syncDialogue()throws IOException {boolean current=dialogue.waiting();if(current&&!wasDialogue){nextTicket();if(originalSystem&&!dialogue.shownText.isEmpty())history.append(dialogue.shownSpeaker,dialogue.shownText,historyVoice,scriptName,scriptOffset);historyVoice="";}wasDialogue=current;}
    private void nextTicket()throws IOException {if(ticket==Long.MAX_VALUE)throw new IOException("Host event counter exhausted");ticket++;}
    private void delay(long millis,boolean canInterrupt)throws IOException {publish();remaining=millis;interruptible=canInterrupt;if(millis!=0)nextTicket();}
    public void elapse(long milliseconds){musicPollElapsed=Math.min(1000,musicPollElapsed+Math.max(0,milliseconds));boolean roll=credits.active();credits.elapse(milliseconds);if(roll&&!credits.active())finishCredits();if(remaining>0)remaining=Math.max(0,remaining-Math.max(0,milliseconds));scene.elapse(milliseconds);if(musicFadeRemaining>0){musicFadeRemaining=Math.max(0,musicFadeRemaining-Math.max(0,milliseconds));musicGain=musicFadeRemaining/(float)musicFadeDuration;if(musicFadeRemaining==0){audio[0]="";audioActive[0]=false;audioGeneration[0]++;musicId=-1;}}performance.elapse(milliseconds,scene,this::perform);pollAudioWait();}
    private void finishCredits(){for(Dc3Scene.Layer l:scene.slots)l.resource="";scene.slots[1].resource="BG/BLACK";scene.quads.clear();scene.published=scene.capture();}
    private void perform(Dc3Performance.Event e){
        if(e.kind==5){musicFadeDuration=musicFadeRemaining=e.value;if(e.value==0){audio[0]="";audioActive[0]=false;audioGeneration[0]++;musicId=-1;}return;}
        int ch=e.kind==4?0:1;if(ch==0){musicId=e.value;musicGain=1;musicFadeRemaining=0;}audio[ch]=e.resource;audioLoop[ch]=e.kind==4||e.kind==8;audioActive[ch]=true;audioGeneration[ch]++;
    }
    public void audioCompleted(int channel,int generation){if(channel>=0&&channel<6&&audioGeneration[channel]==generation)audioActive[channel]=false;pollAudioWait();}
    private void pollAudioWait(){if(audioWaitMask==0)return;boolean busy=(audioWaitMask&1)!=0&&audioActive[0]||(audioWaitMask&4)!=0&&audioActive[5];if((audioWaitMask&2)!=0)for(int i=1;i<=4;i++)busy|=audioActive[i];if(!busy)audioWaitMask=0;}
    public boolean signal(long expected,MesVm.HostSignal signal)throws IOException {
        if(!waiting()||expected!=ticket)return false;
        if(ui.waiting)return false;
        if(credits.active()){if(signal==MesVm.HostSignal.ADVANCE&&credits.skippable){credits.elapsed=credits.end();finishCredits();return true;}return false;}
        if(!movie.isEmpty()){if(signal==MesVm.HostSignal.MEDIA_COMPLETE){movie="";return true;}return false;}
        if(remaining!=0){if(signal==MesVm.HostSignal.ADVANCE&&interruptible){remaining=0;return true;}return false;}
        if(signal==MesVm.HostSignal.ADVANCE){performance.elapse(86400000,scene,this::perform);scene.published=scene.capture();}
        boolean accepted=dialogue.signal(dialogue.waitTicket(),signal);syncDialogue();return accepted;
    }
    private void publish()throws IOException{
        scene.publish();gallery.image(state,scene.slots[1].resource);publishAudio();
    }
    private int voiceIndex(String resource){String suffix=resource.substring(resource.lastIndexOf('_')+1);for(int n=0;n<ui.voiceNames.size();n++)if(suffix.regionMatches(true,0,ui.voiceNames.get(n),0,ui.voiceNames.get(n).length()))return n;return 50;}
    private boolean systemVoice(String resource){return resource.replace('/', '\\').toLowerCase(Locale.ROOT).startsWith("voice\\system");}
    private boolean voiceEnabled(String resource){try{return systemVoice(resource)||state.read(95,voiceIndex(resource))!=0;}catch(IOException e){throw new IllegalStateException(e);}}
    public float voiceGain()throws IOException{if(!voiceEnabled(audio[5]))return 0;return systemVoice(audio[5])&&variables.read(98,9)==0?1:Math.max(0,Math.min(100,state.read(94,voiceIndex(audio[5]))))/100f;}
    private void publishAudio(){for(int i=0;i<audio.length;i++)if(pendingAudio[i]!=null){audio[i]=pendingAudio[i];audioActive[i]=!audio[i].isEmpty()&&(i!=5||voiceEnabled(audio[i]));pendingAudio[i]=null;audioGeneration[i]++;}
    }
    public int read(int bank,int index)throws IOException{if(originalSystem&&bank==99){if(index==21)return availableSaves.contains(2)?1:0;if(index==4)return fullscreen?1:0;}return state.read(bank,index);}
    public void write(int bank,int index,int value)throws IOException{state.write(bank,index,value);}
    public boolean allowNamedJump(){return true;}
    public boolean returnToTitle(int mode,String name,byte[] entry)throws IOException {
        if(originalSystem||mode!=0||!name.equalsIgnoreCase("MAIN"))return false;
        titleRequested=true;for(int i=0;i<audio.length;i++){audio[i]="";audioActive[i]=false;audioGeneration[i]++;}return true;
    }
    public boolean beforeTransfer(int mode,String name)throws IOException {
        // Native 427620 clears selection results; ordinary variables survive.
        if(mode!=0&&!(originalSystem&&mode==1))throw new IOException("Alternate script transfer reset is not implemented");
        // 430896 / 43096E mark the outgoing scenario before 4277C0 loads its successor.
        gallery.completedScript(state,scriptName);
        publish();if(mode==0)scene.resetForTransfer();if(originalSystem){ui.clear();list.clear();musicRoom.caption=false;musicEndAction="";}state.write(99,245,-1);imageTone=0;return true;
    }
    public byte[] pollMenuAction(){if(ui.waiting&&!audioActive[0]&&!musicEndAction.isEmpty()&&musicPollElapsed>500){byte[] a=musicEndAction.getBytes(Binary.SJIS);musicPollElapsed=0;ui.waiting=ui.gridWaiting=false;return a;}return null;}
    public byte[] automaticMenuAction(){byte[] action=automaticMenuAction;automaticMenuAction=null;return action;}
    public boolean hasTimedInput(){return interruptible&&remaining!=0;}
    public byte[] menuAction(long expected,int button)throws IOException{if(!originalSystem||expected!=ticket)return null;if(!ui.waiting){return button==-1&&hasTimedInput()&&!ui.actions[3].isEmpty()?ui.actions[3].getBytes(Binary.SJIS):null;}if(button==-15)return ui.actions[15].isEmpty()?null:ui.actions[15].getBytes(Binary.SJIS);
        if(ui.activeConfirmation>=0){String action=button==-1?":n":button>=0&&button<ui.current.buttons.size()?ui.current.buttons.get(button).action:"";if(action.equals(":y")||action.equals(":n"))return ui.actions[action.equals(":y")?0:1].getBytes(Binary.SJIS);}
        if(button==-1)return ui.actions[3].isEmpty()?null:ui.actions[3].getBytes(Binary.SJIS);if(button<0||button>=ui.current.buttons.size())throw new IOException("Invalid UI button");return ui.current.buttons.get(button).actionBytes();}
    public boolean internalMenuAction(long expected,int button)throws IOException{
        if(history.active&&ui.waiting&&expected==ticket){String a=button<0?"@log:exit":button<ui.current.buttons.size()?ui.current.buttons.get(button).action:"";if(!a.startsWith("@log:"))return false;
            if(a.equals("@log:exit")){history.active=false;ui.waiting=false;returnRequested=true;return true;}
            if(a.equals("@log:preview"))history.previewEnabled=!history.previewEnabled;else if(a.startsWith("@log:select:")){history.selected=Integer.parseInt(a.substring(12));require(history.selected>=0&&history.selected<history.entries.size(),"history selection");}else if(a.equals("@log:first"))history.cursor=0;else if(a.equals("@log:last"))history.cursor=Math.max(0,history.entries.size()-4);else if(a.equals("@log:up"))history.scroll(-1);else if(a.equals("@log:down"))history.scroll(1);else if(a.startsWith("@log:voice:")){int index=Integer.parseInt(a.substring(11));require(index>=0&&index<history.entries.size(),"history voice index");history.selected=index;audio[5]=history.entries.get(index).voice;audioLoop[5]=false;audioActive[5]=!audio[5].isEmpty();audioGeneration[5]++;}Dc3Backlog.build(ui,history,resources.profile.id);return true;}
        if(!originalSystem||!ui.waiting||expected!=ticket||ui.activeConfirmation<0||button<0||button>=ui.current.buttons.size()||!ui.current.buttons.get(button).action.equals(":c"))return false;
        int index=ui.activeConfirmation;state.write(93,index,state.read(93,index)==0?1:0);int pair=index==1?2:index==2?1:index==4?9:index==9?4:index==5?10:index==10?5:-1;if(pair>=0)state.write(93,pair,state.read(93,pair)==0?1:0);
        ui.confirmChecked=state.read(93,index)!=0;menuSound(ui.current.buttons.get(button).clickSound);ui.pressed=-1;nextTicket();return true;
    }
    public int gridSlot(int row)throws IOException{return variables.read(3,20)==1?row:3+state.read(99,0)*10+row;}
    public byte[] menuActionForGrid(int row)throws IOException{
        if(list.active()){ScriptList.Item item=list.at(state.read(99,246),row,ui.grid.size());if(item==null||item.action.isEmpty())return null;state.write(99,253,row);state.write(99,254,item.id);ui.gridWaiting=ui.waiting=false;menuSound(ui.gridClickSound);return item.action.getBytes(Binary.SJIS);}
        if(!ui.gridWaiting||row<0||row>=ui.grid.size())throw new IOException("DD grid selection outside page");int slot=gridSlot(row);
        if(ui.saveGrid&&slot<3||!ui.saveGrid&&!availableSaves.contains(slot))return null;
        state.write(99,246,2);state.write(99,253,row);state.write(99,254,slot);menuSound(ui.gridClickSound);ui.gridWaiting=ui.waiting=false;return "selsave".getBytes(Binary.SJIS);
    }
    private void menuSound(int sound)throws IOException{if(sound!=255){audio[1]=resources.named("sound",sound);audioLoop[1]=false;audioActive[1]=true;audioGeneration[1]++;}}
    public void acceptMenuAction(int button)throws IOException{remaining=0;if(button>=0)state.write(99,245,button);menuSound(button<0?255:ui.current.buttons.get(button).clickSound);ui.closeConfirmation();ui.waiting=false;ui.pressed=ui.hovered=-1;}
    public void hoverMenu(int button)throws IOException{if(!ui.waiting||button==ui.hovered)return;require(button>=-1&&button<ui.current.buttons.size(),"hover button");ui.hovered=button;if(button>=0){int sound=ui.current.buttons.get(button).hoverSound;if(sound!=255){audio[1]=resources.named("sound",sound);audioLoop[1]=false;audioActive[1]=true;audioGeneration[1]++;}}}
    private Dc3Scene.Quad uiQuad(int[] a)throws IOException{require(a.length==8,"UI quad extension");int s=slot(a[6]&63);Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource=scene.pending[s]!=null?scene.pending[s]:scene.slots[s].resource;if(q.resource.isEmpty())q.resource="@slot:"+s;q.x=ui.current.x+a[0];q.y=ui.current.y+a[1];q.sx=a[2];q.sy=a[3];q.width=a[4];q.height=a[5];q.opaque=(a[6]&128)!=0;return q;}
    /** A UI quad keeps "@slot:N" when its slot was still empty at the moment it was built -
     *  a template that outlives the surface, or a panel drawn before the loader ran. The
     *  original reads the slot again when it draws and shows nothing while it is empty, so
     *  an unresolved placeholder resolves to the empty string rather than stopping the game. */
    public String uiSurface(String resource)throws IOException{
        if(resource==null||!resource.startsWith("@slot:"))return resource;
        int s=slot(Integer.parseInt(resource.substring(6)));
        String name=scene.pending[s]!=null?scene.pending[s]:scene.slots[s].resource;
        return name.equals("-")?"":name;
    }
    private void resolveUi(Dc3Scene.Quad q)throws IOException{q.resource=uiSurface(q.resource);}
    private boolean uiCommand(String op,int[] a,byte[] bytes)throws IOException{
        switch(op){
        case "ui.music_end_action":musicEndAction=text(bytes);return true;
        case "dd.grid.item":list.add(a,a[6]);return true;
        case "grid.item_action":list.last().action=text(bytes);return true;
        case "dd.grid.common_sprite":{Dc2Ui.Quad q=ScriptList.sprite(a);if(q.state==-1)list.marker=q;else list.common.add(q);return true;}
        case "dd.grid.item_sprite":list.last().sprites.add(ScriptList.sprite(a));return true;
        case "dd.grid.columns":require(a[0]>0&&a[0]<=50,"list column count");list.columns=a[0];return true;
        case "dd.grid.text":if(a[3]==99)list.textStyle=a[0];else{Dc2Ui.Quad q=new Dc2Ui.Quad();q.type=2;q.x=(short)a[0];q.y=(short)a[1];q.flags=list.textStyle;q.state=a[3]==0?99:a[3];q.text=text(bytes);list.last().sprites.add(q);}return true;
        case "dd.grid.configure":require(a.length==8,"grid bounds");System.arraycopy(a,0,ui.gridBounds,0,8);ui.grid.clear();list.clear();ui.gridWaiting=false;return true;
        case "dd.grid.rect":{Dc3Ui.Button b=new Dc3Ui.Button();b.left=a[0];b.top=a[1];b.right=a[2];b.bottom=a[3];require(ui.grid.size()<50,"grid count");ui.grid.add(b);return true;}
        case "dd.grid.sound":ui.gridHoverSound=a[0];ui.gridClickSound=a[1];return true;
        case "dd.grid.wait":if(list.active())state.write(99,246,list.clamp(state.read(99,246),ui.grid.size()));ui.gridWaiting=true;return uiCommand("ui.wait",a,bytes);
        case "dd.system.return":require(originalSystem,"system return");returnRequested=true;nextTicket();return true;
        case "system.version":ui.version+=text(bytes);require(ui.version.length()<128,"version label length");return true;
        case "ui.template":ui.store(a[0],true);return true;
        case "ui.panel":if(a[0]==0){ui.clear();list.clear();state.write(99,246,0);}else ui.store(a[0],false);return true;
        case "ui.resource":require(a[0]<=4,"UI resource slot");if(!text(bytes).equals("-"))scene.slots[scene.layout.uiBase+a[0]].resource=text(bytes);return true;
        case "host.command_with_string":ui.button(a,bytes);return true;
        case "ui.button_sound":if(a[0]!=255)ui.last().hoverSound=a[0];if(a[1]!=255)ui.last().clickSound=a[1];return true;
        case "ui.button_sprite":{require(a.length==8,"button sprite extension");require(a[7]==99||a[7]>=0&&a[7]<=11,"UI sprite state "+a[7]);Dc3Ui.Sprite s=new Dc3Ui.Sprite();s.quad=uiQuad(a);s.state=a[7];require(ui.last().sprites.size()<1024,"UI sprite count");ui.last().sprites.add(s);return true;}
        case "ui.origin":ui.current.x=a[0];ui.current.y=a[1];return true;
        case "ui.image_quad":require(ui.current.quads.size()<1024,"UI quad count");ui.current.quads.add(uiQuad(a));return true;
        case "ui.palette":require(a[0]>=0&&a[0]<64,"palette index");ui.palette[a[0]]=0xff000000|(a[1]&255)<<16|(a[2]&255)<<8|(a[3]&255);return true;
        case "ui.action":require(a[0]<ui.actions.length,"action selector");ui.actions[a[0]]=text(bytes);return true;
        case "ui.confirm_position":require(a[3]>=0&&a[3]<20,"confirmation kind");ui.confirmX=a[0];ui.confirmY=a[1];ui.confirmKind=a[3];return true;
        case "ui.confirm":automaticMenuAction=null;if(state.read(93,ui.confirmKind)!=0){automaticMenuAction=ui.actions[0].getBytes(Binary.SJIS);return true;}publish();ui.confirmation();ui.confirmChecked=false;nextTicket();return true;
        case "ui.wait":
            for(Dc3Scene.Quad q:ui.current.quads)resolveUi(q);
            for(Dc3Ui.Button b:ui.current.buttons)for(Dc3Ui.Sprite s:b.sprites)if(s.state!=5)resolveUi(s.quad);
            publish();ui.waiting=true;nextTicket();return true;
        case "ui.flush":require(a[0]==0||a[0]==1,"UI overlay commit");return true;
        case "ui.focus":ui.current.focus=true;return true;
        case "ui.navigation":require(a[0]>=0&&a[0]<=3,"UI navigation selector");ui.current.flags|=1<<a[0];return true;
        case "ui.navigation_extra":ui.current.flags|=16;return true;
        case "ui.option_flag":ui.current.flags|=a[0]==0?32:64;return true;
        case "ui.text":if(a[2]==0)return true;
        case "ui.button_text":{require(ui.current.texts.size()<1024,"UI text count");Dc3Ui.Text t=new Dc3Ui.Text();t.args=a.clone();t.text=dialogue.codec.decode(bytes);t.button=op.equals("ui.button_text");ui.current.texts.add(t);return true;}
        case "ui.text_style":ui.current.textStyle=a.clone();return true;
        case "ui.message_position":ui.messageX=a[0];ui.messageY=a[1];return true;
        case "ui.pointer":ui.pointerX=a[0];ui.pointerY=a[1];return true;
        case "audio.voice_register":if(bytes.length==0)ui.voiceNames.clear();else if(!ui.voiceNames.contains(text(bytes))){require(ui.voiceNames.size()<50,"voice registry capacity");ui.voiceNames.add(text(bytes));}state.voiceCount=ui.voiceNames.size();return true;
        default:return false;
        }
    }
    private String text(byte[] bytes)throws IOException{return TextCodec.cp932(bytes);}
    private static void require(boolean condition,String operation)throws IOException{if(!condition)throw new IOException("Unimplemented "+operation);}
    private static int slot(int i)throws IOException{if(i<0||i>=43)throw new IOException("Image slot out of range "+i);return i;}
    private String resource(String name)throws IOException {
        if(name.startsWith("*")){try{return resources.named("images",Integer.parseInt(name.substring(1)));}catch(NumberFormatException e){throw new IOException("Invalid image index",e);}}
        return name;
    }
    public boolean command(String op,int[] a,byte[] bytes)throws IOException {
        if(originalSystem&&uiCommand(op,a,bytes))return true;
        if(op.equals("text.body")){bytes=performance.parse(bytes,state.read(99,26));for(Dc3Performance.Event e:performance.events)if(e.kind==3||e.kind==4||e.kind==8)e.resource=resources.named(e.kind==4?"music":"sound",e.value);}
        if(dialogue.command(op,a,bytes)){syncDialogue();if(dialogue.waiting())publish();return true;}
        switch(op){
        case "system.exit":require(originalSystem,"platform game exit");exitRequested=true;return true;
        case "state.reset_scenario":if(a[0]==0)variables.clearOrdinary();variables.clearHistory();checkpoint=0;checkpointLabel="";checkpointMetadata=0;return true;
        case "state.random":require(a[0]>0,"zero random range");randomSeed=randomSeed*214013+2531011;state.write(99,2,((randomSeed>>>16)&32767)%a[0]);return true;
        case "scene.image_tone":require(a[0]>=0&&a[0]<=2,"image tone "+a[0]);imageTone=a[0];return true;
        case "scene.center_background":scene.centerNextBackground=true;return true;
        case "scene.stop_motion":scene.stop();return true;
        case "scene.record_motion":scene.record(a[0]==0);return true;
        case "scene.replay_motion":scene.replay(a[0]);return true;
        case "scene.character_move":if((a[2]&16)!=0)return true;require(a[2]>=0&&a[2]<=4,"moving character index");scene.move(a[2]+4,a[0],a[1],scene.slots[a[2]+4].alpha,a[3]==0?1000:a[3]*100L);return true;
        case "choice.begin":choices.clear();choiceVisible=false;return true;
        case "choice.show":if(a[0]!=0)return true;require(a[0]==0,"choice replay mode");require(!choices.isEmpty(),"empty choice");publish();choiceVisible=true;return true;
        case "checkpoint":checkpoint=a[0];checkpointMetadata=a[1];variables.appendHistory();return true;
        case "history.range":require(originalSystem||a[0]==1&&a[1]==0,"history.range mode");variables.appendHistory();return true;
        case "scene.checkpoint":checkpointLabel=text(bytes);variables.appendHistory();return true;
        case "dd.audio.seek":require(a[0]==0,"audio seek channel "+a[0]);musicRoom.seek=Math.max(0,Math.min(100,state.read(99,187)+1));musicRoom.seekGeneration++;return true;
        case "system.command":
            if(originalSystem&&a[0]>=200&&a[0]<=202){int track=musicRoom.command(a[0],a[1],variables,state);if(track>=0){pendingAudio[0]=resources.named("music",track);musicId=track;audioLoop[0]=false;musicGain=1;musicFadeRemaining=0;}return true;}
            if(originalSystem&&a[0]==100){return true;} // Caller caption remains part of the suspended story snapshot.
            if(originalSystem&&a[0]==18){
                if(a[1]==0){settingsBackup=state.settingsSnapshot();settingsEditing=true;}
                else if(a[1]==1){require(settingsBackup!=null,"settings comparison without backup");byte[] current=state.settingsSnapshot();current[54]=settingsBackup[54];variables.write(3,0,Arrays.equals(current,settingsBackup)?0:1);settingsEditing=false;}
                else {if(settingsEditing){require(settingsBackup!=null,"settings rollback without backup");state.restoreSettings(settingsBackup);}settingsEditing=false;}
                return true;
            }
            if(originalSystem&&a[0]==6){ui.saveGrid=a[1]==0;return true;}
            if(originalSystem&&a[0]==20)return true; // 40A70F: release thumbnail resources; none are allocated by this host yet.
            if(originalSystem&&a[0]==19&&a[1]==0){variables.write(9,87,availableSaves.contains(state.read(99,254))?0:1);return true;}if(a[0]==9&&a[1]==0){publish();credits.start(resources,variables.read(5,10),state.read(100,110)!=0);nextTicket();return true;}require(a[0]==207&&a[1]==0,"system.command "+Arrays.toString(a));return true; // native switch default at 40B0E9
        case "native.no_effect":return true; // Shared native fallback, not an unknown-op policy.
        case "system.persist":
            switch(a[0]){
            case 0:require(originalSystem,"native load operation");if(availableSaves.contains(state.read(99,254))){loadRequest=state.read(99,254);nextTicket();}return true;
            case 2:case 3:require(originalSystem,"desktop window visibility");return true; // Android owns window lifecycle.
            case 8:require(originalSystem,"desktop window mode");windowRequest=a[1];fullscreen=a[1]!=0;return true;
              case 20:state.resetPreferences();return true;
              case 26:require(settingsLoader!=null,"system settings storage");settingsLoader.load(state);return true; // 42E0FC -> 40EE90: reload system settings, not a save-slot load.
            case 27:windowSizeReset=true;return true;
            case 10:require(originalSystem,"original media availability check");resources.scripts().script("START");return true;
            case 15:require(originalSystem,"input mode reset");ui.inputMode=a[1];state.write(99,195,0);return true;
            case 1:case 9:return true;
            case 4:if(a[1]==0){saveRequest=state.read(99,254);nextTicket();}else autosaveGeneration++;return true;
            case 18:return true; // The portable session already retained display and execution state.
            case 5:case 6:variables.appendHistory();return true;
            case 7:skipAllowed=a[1]!=0;return true;
            default:throw new IOException("Unimplemented system.persist mode "+a[0]);
            }
        case "wait.duration":if(a[0]==254){delay(scene.animationRemaining(),false);return true;}require(a[0]<254,"native input wait 255");delay(a[0]==0&&a[1]!=0?-1:a[0]*200L,a[1]!=0);return true;
        case "image.load":{
            int s=slot(a[0]);if(s==0)return true;
            String name=resource(text(bytes));if(name.equals("--")){require(systemScene!=null,"previous-scene image restore");name=systemScene.layers[s].resource;}
            Dc3Layout l=scene.layout;
            if(l.lead(s))name=name.replace('?',(char)('1'+Math.min(2,variables.read(5,8))));
            if(l.chip(s)){name=name.replace('?',(char)('1'+Math.min(2,variables.read(5,8))));int comma=name.indexOf(',');if(comma>=0){String stem=name.substring(0,comma),suffix="";if(stem.endsWith("*")||stem.endsWith("@")){suffix=stem.substring(stem.length()-1);stem=stem.substring(0,stem.length()-1);}if(!stem.endsWith("_B"))stem+="_B";name=stem+suffix+name.substring(comma);}}
            if(imageTone!=0&&(l.cast(s)||l.chip(s)&&name.regionMatches(true,0,"BST\\",0,4))){int comma=name.indexOf(',');if(comma>=0){String stem=name.substring(0,comma);if(stem.endsWith("*")||stem.endsWith("@"))stem=stem.substring(0,stem.length()-1);name=stem+(imageTone==1?'*':'@')+name.substring(comma);}}
            if(s==1){Arrays.fill(scene.pending,0,l.chipFirst,null);if(name.equalsIgnoreCase("BG\\BLACK")||name.equalsIgnoreCase("BG\\WHITE"))scene.novelMode=false;
                // 42E3B7 -> 441E00 -> 441AF0: a new non-novel background resets its render object.
                if(!scene.novelMode){scene.stop(1);scene.slots[1]=new Dc3Scene.Layer();scene.slots[1].priority=0;}
            }
            scene.pending[s]=name;return true;
        }
        case "scene.background":{
            String name=text(bytes);scene.quads.clear();if(name.startsWith(":n")){scene.novelMode=true;return true;}if(name.startsWith(":r")){scene.novelMode=false;return true;}
            if(name.equals("--")){require(systemScene!=null,"background restore");gallery.image(state,systemScene.layers[1].resource);return true;} // Reuse the caller image queued by 3A.
            gallery.image(state,name); // 42D3C6 -> 40EDC0, exact NAMES entry; preloading alone does not unlock.
            for(int s=scene.layout.spriteFirst;s<scene.layout.spriteFirst+2*scene.layout.spriteCount;s++)scene.slots[s].resource="";
            scene.slots[scene.layout.overlay].resource="";
            return true;
        }
        case "ui.origin":scene.uiX=a[0];scene.uiY=a[1];return true;
        case "ui.image_quad":{
            require(a.length==8,"image quad extension");int s=slot(a[6]&63);String name=scene.pending[s]!=null?scene.pending[s]:scene.slots[s].resource;
            require(!name.isEmpty()&&!name.equals("-"),"quad from empty image slot "+s);require(scene.quads.size()<1024,"quad count limit");
            Dc3Scene.Quad q=new Dc3Scene.Quad();q.resource=name;q.x=scene.uiX+a[0];q.y=scene.uiY+a[1];q.sx=a[2];q.sy=a[3];q.width=a[4];q.height=a[5];q.opaque=(a[6]&128)!=0;scene.quads.add(q);scene.revision++;return true;
        }
        case "scene.character":{
            int s=a[0]+scene.layout.spriteFirst;require(scene.layout.lead(s),"character slot");String name=text(bytes);
            if(name.equals("-")){scene.pending[s]="-";scene.pending[scene.layout.companion(s)]="-";}else require(name.startsWith(".")||name.equals("--")&&systemScene!=null,"character restore/name "+name);
            return true;
        }
        case "scene.character_overlay":require(!text(bytes).equals("--")||systemScene!=null,"overlay restore");if(text(bytes).equals("-"))scene.pending[slot(a[0]+scene.layout.companion(scene.layout.spriteFirst))]="-";return true;
        case "scene.face":if(text(bytes).equals("-")){scene.pending[scene.layout.face]="-";scene.pending[scene.layout.face+1]="-";}return true;
        case "scene.character_transform":{
            int flags=a[3];if(flags==255)return true;int s=flags==0?a[2]+scene.layout.spriteFirst:(flags&15)+scene.layout.spriteFirst;require(scene.layout.lead(s),"character transform slot");
            Dc3Scene.Layer layer=scene.slots[s];
            if(flags==0){scene.stop(s);layer.x=a[0];layer.y=a[1];}
            else if((flags&0xf0)==0x10){layer.scaleX=layer.scaleY=a[0];layer.pivotX=a[1];layer.pivotY=a[2];}
            else if((flags&0xf0)==0x20)layer.priority=Math.max(0,Math.min(100,a[0])); // 441600, ascending render pass at 423919
            else if((flags&0xf0)==0x40){require(a[0]>=0&&a[0]<=2,"masked character entrance");int x=layer.x,y=layer.y;layer.alpha=0;if(a[0]==1)layer.x-=20;if(a[0]==2)layer.x+=20;scene.move(s,x,y,255,200);}
            else throw new IOException("Unimplemented character transform flags "+flags);
            scene.revision++;return true;
        }
        case "scene.camera_speed":scene.camera.speed=a[0];return true;
        case "scene.camera":scene.stop();scene.camera.start(a[0],a[1],a[2],a[3]);return true;
        case "scene.background_move":if(a[2]==1){scene.stop(1);scene.slots[1].x=a[0];scene.slots[1].y=a[1];return true;}scene.move(1,a[0],a[1],scene.slots[1].alpha,a[2]==0?2000:a[2]==99?a[3]*200L:(a[3]+4)*250L);return true;
        case "scene.transition":{
            if(a[0]==9){publish();UiCapture next=SceneCaptures.dd(scene.capture(),SceneCaptures.ddPanel(ui,variables),ui.palette,uiCapture);uiCapture=captureRenderer==null?next:captureRenderer.flatten(next);scene.slots[0].resource="@ui-capture";return true;}
            int mode=a[0];if(mode==32||mode==7||mode==10||mode==11||mode==13||mode==14||mode==23||mode==24||mode==25||mode==30||mode==31)return true;
            if(mode==51||mode==52){for(int i=0;i<(mode==51?4:5);i++)if((a[1]&(1<<i))!=0)scene.shake(i+scene.layout.spriteFirst,mode==51?0:1);return true;}
            if(mode==4||mode==6){for(int i=0;i<43;i++)scene.shake(i,mode==4?0:1);return true;}
            require(mode==0||mode==1||mode==2||mode==3||mode==5||mode==16||mode==17||mode==20||mode==21,"transition mode "+mode);
            scene.transition=mode;scene.transitionParameter=a[1];
            if(mode==5){publish();return true;}
            String mask="";long duration=(a[1]+1)*1000L/3;
            if(mode==3)duration=167; // Five ticks up, five down (407CC0 / 408CCC).
            if(mode==16){mask="system\\effbreak";duration=1000;}
            if(mode==17){mask=a[1]==1?"MSK\\MASK11A":"MSK\\MASK04A";scene.pending[scene.layout.mask]=mask;duration=1000;}
            if(mode==20||mode==21){int m=scene.layout.mask;mask=scene.pending[m]!=null?scene.pending[m]:scene.slots[m].resource;require(!mask.isEmpty()&&!mask.equals("-"),"transition without mask");duration=a[1]*200L;}
            scene.fade.start(mode,a[1],duration,mask,scene.published);
            if(mode==2||mode==16){scene.petals.start(0);scene.fog=false;scene.clouds=false;}
            delay(scene.fade.duration,false);
            return true;
        }
        case "scene.effect":
            if(a[0]==1||a[0]==3||a[0]==9||a[0]==11){scene.petals.start(a[0],a[1]);return true;}
            if(a[0]==16){scene.clouds=a[1]!=0;scene.environmentTime=0;return true;}
            if(a[0]==0){scene.petals.start(0);scene.fog=false;scene.clouds=false;return true;}
            require(a[0]==7,"scene effect "+a[0]);scene.fog=a[1]!=0;if(!scene.fog)scene.pending[scene.layout.fog]="-";scene.revision++;return true;
        case "audio.music":{
            if(a[0]==255){pendingAudio[0]="";musicId=-1;return true;}
            require(a[1]>=0&&a[1]<=2,"music start mode "+a[1]);
            if(a[1]!=1||musicId!=a[0])pendingAudio[0]=resources.named("music",a[0]);audioLoop[0]=a[1]<2;musicId=a[0];musicGain=1;musicFadeRemaining=0;return true;
        }
        case "audio.music_stop":pendingAudio[0]="";musicId=-1;return true;
        case "audio.system_voice":pendingAudio[2]=text(bytes);audioLoop[2]=false;return true;
        case "audio.voice":historyVoice=pendingAudio[5]=text(bytes);return true;
        case "audio.sound":{
            if(a[0]==-2){pendingAudio[5]="";return true;}
            if(a[0]==-1){for(int i=1;i<=4;i++)pendingAudio[i]="";return true;}
            require(a[0]>=0&&(a[1]==0||a[1]==1),"SE loop/control "+Arrays.toString(a));
            for(int i=1;i<=4;i++)if(pendingAudio[i]==null){pendingAudio[i]=resources.named("sound",a[0]);audioLoop[i]=a[1]!=0;break;}return true;
        }
        case "audio.restore_volume":
            if(a[0]==254){publishAudio();return true;}
            if(a[0]==255){if(musicVolumeBackup!=0){state.write(99,7,musicVolumeBackup);musicVolumeBackup=0;}return true;}
            require(a[0]>=0&&a[0]<254,"temporary music volume");musicVolumeBackup=state.read(99,7);state.write(99,7,musicVolumeBackup*a[0]/100);return true;
        case "wait.audio":require((a[0]&~7)==0,"audio wait mask "+a[0]);publish();audioWaitMask=a[0];pollAudioWait();if(audioWaitMask!=0)nextTicket();return true;
        case "media.movie":for(int i=0;i<audio.length;i++)pendingAudio[i]="";publish();movie="AdvData/MOVIE/"+Binary.safePath(text(bytes));movieGeneration++;nextTicket();return true;
        default:return false;
        }
    }
    public void save(DataOutput out)throws IOException {
        out.writeInt(originalSystem?0x4444333e:0x44443337);if(originalSystem){out.writeBoolean(returnRequested);out.writeInt(saveRequest);out.writeInt(loadRequest);out.writeBoolean(systemScene!=null);if(systemScene!=null)systemScene.save(out);ui.save(out);history.save(out);out.writeUTF(historyVoice);out.writeBoolean(exitRequested);out.writeBoolean(settingsEditing);out.writeBoolean(settingsBackup!=null);if(settingsBackup!=null){out.writeInt(settingsBackup.length);out.write(settingsBackup);}}dialogue.save(out);scene.save(out);state.save(out);performance.save(out);credits.save(out);out.writeBoolean(titleRequested);out.writeInt(imageTone);out.writeInt(randomSeed);out.writeLong(musicFadeRemaining);out.writeLong(musicFadeDuration);out.writeFloat(musicGain);
        out.writeBoolean(choiceVisible);out.writeInt(choices.size());for(Choice c:choices){out.writeInt(c.target.length);out.write(c.target);out.writeUTF(c.caption);}
        out.writeUTF(scriptName);out.writeUTF(checkpointLabel);out.writeInt(checkpoint);out.writeInt(checkpointMetadata);out.writeInt(musicId);
        out.writeLong(ticket);out.writeLong(remaining);out.writeBoolean(wasDialogue);out.writeBoolean(interruptible);out.writeInt(autosaveGeneration);
        out.writeInt(audioWaitMask);out.writeBoolean(skipAllowed);out.writeUTF(movie);out.writeInt(movieGeneration);
        for(int i=0;i<audio.length;i++){out.writeUTF(audio[i]);out.writeInt(audioGeneration[i]);out.writeBoolean(audioActive[i]);out.writeBoolean(audioLoop[i]);out.writeBoolean(pendingAudio[i]!=null);if(pendingAudio[i]!=null)out.writeUTF(pendingAudio[i]);}if(originalSystem){list.save(out);out.writeUTF(musicEndAction);musicRoom.save(out,state);out.writeLong(musicPollElapsed);history.saveVisuals(out);out.writeBoolean(false);out.writeInt(musicVolumeBackup);UiCapture.save(out,uiCapture);}
    }
    public void load(DataInput in)throws IOException {
        uiCapture=null;int format=in.readInt();if(originalSystem?(format!=0x4444333a&&format!=0x4444333b&&format!=0x4444333c&&format!=0x4444333d&&format!=0x4444333e):format!=0x44443337)throw new IOException("Native host save version");if(originalSystem){returnRequested=in.readBoolean();saveRequest=in.readInt();loadRequest=in.readInt();if(saveRequest< -1||saveRequest>255||loadRequest< -1||loadRequest>255)throw new IOException("Invalid pending slot");systemScene=null;if(in.readBoolean()){systemScene=new Dc3Scene.Frame();systemScene.load(in);}ui.load(in);if(format>=0x4444333b){history.load(in);historyVoice=in.readUTF();}else{history.clear();historyVoice="";}exitRequested=in.readBoolean();settingsEditing=in.readBoolean();settingsBackup=null;if(in.readBoolean()){int n=in.readInt();if(!state.acceptsSettingsLength(n))throw new IOException("Invalid saved settings backup");settingsBackup=new byte[n];in.readFully(settingsBackup);}if(settingsEditing&&settingsBackup==null)throw new IOException("Missing settings backup");}dialogue.load(in);scene.load(in);state.load(in);performance.load(in);credits.load(in);titleRequested=in.readBoolean();imageTone=in.readInt();if(imageTone<0||imageTone>2)throw new IOException("Invalid image tone");randomSeed=in.readInt();musicFadeRemaining=in.readLong();musicFadeDuration=in.readLong();musicGain=in.readFloat();if(musicFadeRemaining<0||musicFadeRemaining>musicFadeDuration||musicFadeDuration>86400000||!Float.isFinite(musicGain)||musicGain<0||musicGain>1)throw new IOException("Invalid music fade");
        choiceVisible=in.readBoolean();int count=in.readInt();if(count<0||count>50||choiceVisible&&count==0)throw new IOException("Invalid saved choices");choices.clear();for(int i=0;i<count;i++){int n=in.readInt();if(n<1||n>255)throw new IOException("Invalid choice label");byte[] b=new byte[n];in.readFully(b);choices.add(new Choice(b,in.readUTF()));}
        scriptName=in.readUTF();checkpointLabel=in.readUTF();checkpoint=in.readInt();checkpointMetadata=in.readInt();musicId=in.readInt();
        ticket=in.readLong();remaining=in.readLong();wasDialogue=in.readBoolean();interruptible=in.readBoolean();autosaveGeneration=in.readInt();
        audioWaitMask=in.readInt();skipAllowed=in.readBoolean();movie=in.readUTF();movieGeneration=in.readInt();
        if(ticket<0||remaining< -1||remaining>86400000||wasDialogue!=dialogue.waiting())throw new IOException("Invalid native scheduler state");
        for(int i=0;i<audio.length;i++){audio[i]=in.readUTF();audioGeneration[i]=in.readInt();audioActive[i]=in.readBoolean();audioLoop[i]=in.readBoolean();pendingAudio[i]=in.readBoolean()?in.readUTF():null;}if(originalSystem&&format>=0x4444333c){list.load(in);musicEndAction=in.readUTF();musicRoom.load(in,state);musicPollElapsed=format==0x4444333c&&in instanceof DataInputStream&&((DataInputStream)in).available()==0?0:in.readLong();if(musicPollElapsed<0||musicPollElapsed>1000)throw new IOException("Music callback clock");}if(originalSystem&&format>=0x4444333d){history.loadVisuals(in);if(in.readBoolean()){Dc3Scene.Frame captured=new Dc3Scene.Frame();captured.load(in);Dc3Ui.Panel panel=new Dc3Ui.Panel();panel.load(in);uiCapture=SceneCaptures.dd(captured,panel,ui.palette,null);}musicVolumeBackup=in.readInt();if(format>=0x4444333e)uiCapture=UiCapture.load(in);}else if(originalSystem){state.migrateLegacyAudio();if(settingsBackup!=null){Dc3State prior=new Dc3State();prior.restoreSettings(settingsBackup);prior.migrateLegacyAudio();settingsBackup=prior.settingsSnapshot();}}if(originalSystem)state.voiceCount=ui.voiceNames.size();
    }
}
