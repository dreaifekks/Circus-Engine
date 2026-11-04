package org.circus.core;

import java.io.*;
import java.util.*;

/** Earlier 800x600 CIRCUS host. Numeric opcodes stay in independently reviewed profiles.
 * It shares rendering primitives with DM, not DM's variable layout or system commands. */
public final class PcHost extends Dc2Host {
    public final PcNameInput nameInput=new PcNameInput();public String familyName,givenName;public final boolean second;public final Pc2Map map=new Pc2Map();public final PcCredits pcCredits=new PcCredits();
    private final List<String> voiceGroup=new ArrayList<>();
    private final byte[] globals=new byte[100],cg=new byte[1600],seen=new byte[1600],voiceEnabled=new byte[128],scratch=new byte[100];
    /** Set by the Mac app, where the DC2PC input path follows the exe. */
    public static boolean followsExe;
    /** Ctrl as a physical key, and the toolbar's skip button. 41B110: register 25 forces normal speed over both. */
    public boolean ctrlHeld,skipButton;
    public boolean fast()throws IOException{return read(99,25)==0&&(ctrlHeld||skipButton);}
    /** 41E360: register 251 says whether the running script was read before; 41E2B0 marks a script read on leaving it. */
    private int scriptSeen;
    /** 41EC40: opcode 0x2D calls a script. DesktopLegacy opens it, and the caller goes on from the next instruction. */
    public String callRequest;
    /** B86704: 0x64 and 0x65 write here; opcode 0x2B moves it to the live actor (423A46). Slides only move the live one. */
    private final int[][] pending={{0,0,100},{0,0,100},{0,0,100},{0,0,100}};
    private boolean logFromChoice;private Map<String,String> saveTitles;
    @Override public boolean keepsOnlyLogText(){return exe();}
    private boolean exe(){return followsExe&&second;}
    private boolean dirty,waitDismissable;private long dirtyRevision,clockMillis,waitAnchor;private List<String> musicTitles;
    /** 42448F: a timed wait repaints only if the script drew something since the last repaint. */
    private void noteDrawing(String op,int[] a){
        if(!op.equals("pc.wait"))waitDismissable=false;
        if(op.equals("pc.background"))ui.quadTemplates.get(0).clear();
        if(op.equals("pc.background")||op.equals("pc.quad")||op.equals("pc.quad_template")&&a[0]==0||op.equals("pc.transition")&&a[0]==10){dirty=true;dirtyRevision=scene.revision;}
    }
    /** 42448F, 422548: 200 ms beats with the first run at once. Bit 1 halves the beat, bit 2 anchors to the clock, bit 3 is 10 ms. */
    private long timedWaitMillis(int[] a){
        if((a[1]&4)!=0){if(a[0]==0){waitAnchor=clockMillis;return 0;}long target=waitAnchor+200L*a[0];waitAnchor=target;return Math.max(0,target-clockMillis);}
        if(a[0]==0)return -1;
        if((a[1]&8)!=0)return 10L*a[0];
        if((a[1]&2)!=0)return 100L*(a[0]*2-1);
        return 200L*(a[0]-1);
    }
    /** 41D672, 423470: sixteen levels of 40 ms, stepping 1, 2, 4 or 8 by the screen speed setting. */
    private long fadeMillis()throws IOException{return fast()?80:new long[]{640,320,160,80}[Math.max(0,Math.min(3,read(99,12)))];}
    /** 4252BA and the 0x0D table: true if this effect is handled here. */
    private boolean transitionPc2(int[] a)throws IOException{
        switch(a[0]){
            case 10:{ // 4252BA bakes quads into the background unseen, so they keep the pictures loaded right now.
                List<Dc2Ui.Quad> baked=Dc2Ui.copyQuads(ui.quads);for(Dc2Ui.Quad q:baked)if(q.type==0)q.text=scene.slots[q.slot()];
                ui.quadTemplates.get(0).addAll(baked);ui.quads.clear();return true;
            }
            case 5:case 12:scene.publish(ui,0,0);return true;
            case 0:case 1:case 2:{long ms=fadeMillis();if(a[0]==2){speaker="";body="";}scene.publish(ui,a[0],ms);delay(ms);return true;}
            case 3:{long ms=80L*Math.max(1,a[1]);scene.publish(ui,3,ms);delay(ms);return true;}
            case 4:case 6:{long ms=fastEffect()?160:360;scene.publish(ui,a[0],ms);delay(ms);return true;}
            case 13:{ // 41DB70
                int least=a[1]==0?0:a[1]==1?-191:-340,inc=maskStep(),b=0,gain=0,t=0;boolean more=true;
                while(more){t++;b+=inc;if(t>1)gain+=b;more=t==1||least+gain<16;if(b>16)b=16;}
                long ms=t*40L;scene.publish(ui,130+a[1],ms);delay(ms);return true;
            }
            case 14:{ // 413C0E, 419310: not hurried by Ctrl or the speed setting, and without effects it is a fade to black.
                speaker="";body="";
                if(read(99,20)==0){long ms=fadeMillis();scene.publish(ui,2,ms);delay(ms);return true;}
                long ms=PcShatter.ticks(a[1],scene.transitionSerial+1L)*40L;scene.publish(ui,140+a[1],ms);delay(ms);Arrays.fill(scene.slots,2,7,"");return true;
            }
            case 51:case 52:{long ms=fastEffect()?160:a[0]==51?360:280;scene.beginShake(a[0],a[1],ms);scene.publish(ui,0,0);delay(ms);return true;} // 4215FC, 4217FF
            case 20:case 21:case 22:{
                int[] rates={64,64,32,16,8,4,2,1};
                long ms=a[1]<8?40L*((256+2*rates[a[1]]-1)/(2*rates[a[1]])):(a[1]*5-35)*4L*256;
                if(a[0]==20){speaker="";body="";}
                scene.publish(ui,a[0]==20?20:200+a[0],ms);delay(ms);return true;
            }
            default:return false;
        }
    }
    private boolean fastEffect()throws IOException{return read(99,12)==3||fast();}
    /** 41DB70: how far the wipe front moves each 40 ms. */
    public int maskStep()throws IOException{int speed=read(99,12);return speed==3||fast()?8:speed==2?4:speed==1?2:1;}
    public int maskGain(int ticks)throws IOException{int inc=maskStep(),b=0,gain=0;for(int t=1;t<=ticks;t++){b+=inc;if(t>1)gain+=b;if(b>16)b=16;}return gain;}
    /** 42E7BB, 407889: a save row shows the title listed for the script it was made in. */
    public String saveTitle(String name)throws IOException{
        if(saveTitles==null){
            saveTitles=new HashMap<>();
            for(String path:resources.files.paths())if(path.equalsIgnoreCase("dc2pc.exe")){
                Dc2NativeData.Pe pe=new Dc2NativeData.Pe(resources.files.read(path,4*1024*1024));
                if(pe.string(0x4783f8).equals("fst_1216_d1_cmn"))for(int at=0x4783f8;at<0x536ad8;at+=0x208){String key=pe.string(at);if(!key.isEmpty())saveTitles.put(key,pe.string(at+0x104));}
            }
        }
        String title=saveTitles.get(name);return title==null?"":title;
    }
    /** 408A70: the music page title bar shows this name from a string table in the exe. */
    public String musicTitle()throws IOException{
        if(musicTitles==null){
            musicTitles=new ArrayList<>();
            for(String path:resources.files.paths())if(path.equalsIgnoreCase("dc2pc.exe")){Dc2NativeData.Pe pe=new Dc2NativeData.Pe(resources.files.read(path,4*1024*1024));for(int n=0;n<74;n++)musicTitles.add(pe.string(0x536b28+n*0x104));}
        }
        int n=variables.read(3,5);return n>=0&&n<musicTitles.size()?musicTitles.get(n):"";
    }
    @Override public boolean effectsEnabled()throws IOException{return read(99,exe()?20:10)!=0;}
    @Override protected long fadeOutMillis(){return exe()?600:super.fadeOutMillis();}
    /** The exe ignores clicks while a fade runs (40AF0A) and in timed waits without bit 0 (422BBF); a click on a line stops its voice (420360). */
    @Override public boolean signal(long expected,MesVm.HostSignal signal)throws IOException{
        if(exe()&&signal==MesVm.HostSignal.ADVANCE&&expected==ticket){
            if(!textWaiting&&!presentationWaiting&&movie.isEmpty()&&remaining!=0&&!(timedInput&&waitDismissable))return false;
            if(textWaiting&&!presentationWaiting)play(5,"",false);
        }
        return super.signal(expected,signal);
    }
    public boolean waitDismissable(){return waitDismissable;}
    /** Line shown when the log opens: the one before the current line, or from a choice, which has no line of its own, the last one. */
    private int newestLogLine(){return history.entries.size()-(logFromChoice?1:2);}
    /** 41FFC0: the log opens only if there is an earlier line to show. */
    public boolean hasEarlierLine(boolean fromChoice){return history.entries.size()>=(fromChoice?1:2);}
    public void scrollHistory(int direction){
        if(direction<0)history.selected=Math.max(0,history.selected-1);
        else if(history.selected>=newestLogLine()){history.active=ui.waiting=false;returnRequested=true;return;}
        else history.selected++;
        showHistory();
    }
    public void replayHistoryVoice()throws IOException{if(history.selected<0||history.selected>=history.entries.size())return;Dc2History.Entry e=history.entries.get(history.selected);if(e.voice!=null&&!e.voice.isEmpty())playVoice(e.voice);}
    /** Progress written before the exe was followed holds another game's defaults and an all-open music list. */
    private void migrateProgressPc2()throws IOException{
        if(read(99,7)==20&&read(99,8)==23&&read(99,9)==27&&read(99,11)==11&&read(99,12)==2&&read(99,16)==7&&read(99,20)==0)defaultsPc2();
        boolean allOpen=true;for(byte b:voiceEnabled)allOpen&=b==1;if(allOpen)Arrays.fill(voiceEnabled,(byte)0);
    }
    private void defaultsPc2(){registers.put(7,25);registers.put(8,25);registers.put(9,29);registers.put(11,8);registers.put(12,1);registers.put(16,8);registers.put(20,1);}
    public final ScriptList list=new ScriptList();private boolean gridTarget;private String musicEndAction="";
    private int spriteFlags;private int randomState=0x63c25769;private boolean timedInput;
    private boolean pendingRegionFlag;
    /** Original D.C.II (2006) shares the PC host but keeps its own draw-batch caps. */
    private final boolean original;
    // PC1 4304F8 / PC2 500F30 defaults (native bytes are G,R,B,0). START assigns later indices separately.
    private static final int[] PC_PALETTE={0xff000000,0xff0000ff,0xffff0000,0xffff00ff,0xff00ff00,0xff00ffff,0xffffff00,0xffffffff,0xff854e28,0xffccccff,0xff774400,0xffff7f7f,0xffcccccc};
    public final int[] gridBounds=new int[8];
    public int gridSlot(int index)throws IOException{return read(99,0)*(second?8:5)+index;}
    @Override public byte[] menuActionForGrid(int index)throws IOException{
        if(list.active()){ScriptList.Item item=list.at(read(99,246),index,grid.size());if(item==null||item.action.isEmpty())return null;registers.put(253,index);registers.put(254,item.id);gridWaiting=ui.waiting=false;musicEndAction="";if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);return item.action.getBytes(Binary.SJIS);}
        if(!gridWaiting||index<0||index>=grid.size())throw new IOException("PC grid selection outside page");int slot=gridSlot(index);
        if(saveGrid&&slot<2||!saveGrid&&!availableSaves.contains(slot))return null;
        registers.put(253,index);registers.put(254,slot);registers.put(246,0);gridWaiting=ui.waiting=false;
        if(gridClickSound!=255)play(1,resources.named("sound",gridClickSound),false);
        return (saveGrid?"selsave":"selsave2").getBytes(Binary.SJIS);
    }
    @Override public byte[] pollMenuAction(){if(ui.waiting&&!audioActive[0]&&!musicEndAction.isEmpty()){byte[] a=musicEndAction.getBytes(Binary.SJIS);musicEndAction="";ui.waiting=gridWaiting=false;ui.actions[0]=ui.actions[1]="";return a;}return null;}
    public boolean hasTimedInput(){return timedInput&&remaining!=0;}
    /** 4189D0 / 422DA0: older engines browse one message in the original message window. */
    public void openHistory()throws IOException{openHistory(false);}
    public void openHistory(boolean fromChoice)throws IOException{logFromChoice=fromChoice;history.active=true;history.selected=Math.max(0,newestLogLine());ui.waiting=true;ui.buttons.clear();ui.buttons.addAll(Dc2Ui.copyButtons(ui.buttonTemplates.get(messageMode==0?1:4)));showHistory();nextTicket();}
    private void showHistory(){if(history.entries.isEmpty())return;Dc2History.Entry e=history.entries.get(history.selected);speaker=e.speaker;body=e.body;}
    public static boolean supports(Profile p){return p.id.equals("dc2pc-5769")||p.id.equals("dcpc-2063")||p.id.equals("dc2-3363");}
    public PcHost(CircusResources r,TextCodec c){super(r,c);second=r.profile.id.equals("dc2pc-5769");original=r.profile.id.equals("dc2-3363");if(original)ui.quadLimit=60;System.arraycopy(PC_PALETTE,0,ui.palette,0,PC_PALETTE.length);boolean cn=c.charset.name().equalsIgnoreCase("GBK");familyName=second?(cn?"樱内":"桜内"):(cn?"朝仓":"朝倉");givenName=second?(cn?"义之":"義之"):(cn?"纯一":"純一");gridHoverSound=gridClickSound=255;Arrays.fill(voiceEnabled,(byte)1);registers.put(13,0);registers.put(19,0);registers.put(25,0);registers.put(26,8);registers.put(27,8);if(exe()){Arrays.fill(voiceEnabled,(byte)0);defaultsPc2();history.limit=999;scene.pauseEffectsInTransition=true;}}
    /** 424D87: a choice stops the skip button. mode 9 keeps the toolbar under the options. */
    public boolean choiceWaiting(){return ui.waiting&&choiceCount>0&&!confirming&&!gridWaiting&&!history.active&&!nameInput.active&&!map.active;}
    @Override public void enterSystem()throws IOException{super.enterSystem();if(exe())choiceCount=0;}
    /** 426875: the summary's "scene" entry leaves the menu for the script's label "new" instead of the suspended line. */
    public void leaveForLabel()throws IOException{registers.put(19,0);textWaiting=presentationWaiting=false;ui.waiting=false;ui.buttons.clear();remaining=0;choiceCount=0;body=speaker="";history.clear();scene.effect(0,0);play(0,"",false);music=-1;}
    /** 425261: effect 9 takes the screen as it is for the background, and empties layers 0 to 5. */
    @Override protected void captureUi()throws IOException{
        UiCapture screen=exe()&&captureRenderer!=null?captureRenderer.screen():null;
        if(screen==null){super.captureUi();return;}
        uiCapture=screen;ui.quads.clear();ui.quadTemplates.get(0).clear();scene.slots[1]="@ui-capture";Arrays.fill(scene.slots,2,10,"");scene.backgroundX=scene.backgroundY=0;scene.scrollStep=0;scene.publish(ui,0,0);
    }
    @Override public void elapse(long ms){clockMillis+=Math.max(0,ms);super.elapse(ms);pcCredits.elapse(ms);if(map.active)map.elapsed+=Math.max(0,ms);}
    @Override public boolean waiting(){return callRequest!=null||pcCredits.active||super.waiting();}
    @Override public boolean internalMenuAction(long expected,int button)throws IOException{
        if(nameInput.active&&expected==ticket){String a=button<0?"@name:cancel":ui.buttons.get(button).action;nameAction(a);return true;}
        if(history.active&&expected==ticket){String a=button<0?"@log:exit":button<ui.buttons.size()?ui.buttons.get(button).action:"";
            if(a.equals(":up")){history.selected=Math.max(0,history.selected-1);showHistory();}
            else if(a.equals(":down")){if(history.selected>=newestLogLine()){history.active=ui.waiting=false;returnRequested=true;}else{history.selected++;showHistory();}}
            else if(a.equals("@log:exit")){history.active=ui.waiting=false;returnRequested=true;}
            else if(a.equals(":close")||a.equals(":change"))return false;
            else return true;return true;
        }
        if(!map.active)return super.internalMenuAction(expected,button);if(expected!=ticket)return false;
        if(button<0){if(map.selected>=0){map.selected=-1;map.buttons(ui);}else if(exe()&&map.elapsed>=500){variables.write(5,40,99);map.active=false;ui.buttons.clear();ui.waiting=false;}return true;} // 410ED2: right click leaves the map for the menu.
        if(button>=ui.buttons.size())throw new IOException("Map button index");String action=ui.buttons.get(button).action;
        if(action.equals("@map:yes")){variables.write(5,40,map.selected);map.active=false;ui.buttons.clear();ui.waiting=false;}
        else if(action.equals("@map:no")){map.selected=-1;map.buttons(ui);}
        else if(action.startsWith("@map:")){map.selected=Integer.parseInt(action.substring(5));map.buttons(ui);}
        else throw new IOException("Unknown native map action");play(1,resources.named("sound",223),false);return true;
    }
    public void nameAction(String action)throws IOException{int result=nameInput.action(action);if(result==1){familyName=nameInput.family.trim();givenName=nameInput.given.trim();}nameInput.build(ui);nextTicket();}
    @Override public boolean beforeInstruction(Profile p,int op)throws IOException{if(!p.id.equals(resources.profile.id))throw new IOException("PC host profile mismatch");return waiting();}
    @Override public byte[] menuAction(long expected,int button)throws IOException{
        if(confirming&&ui.waiting&&expected==ticket){String a=button<0?":n":button<ui.buttons.size()?ui.buttons.get(button).action:"";if(a.equals(":y"))a=ui.actions[2];else if(a.equals(":n"))a=ui.actions[3];return a.isEmpty()?null:a.getBytes(Binary.SJIS);}
        // PC1 419310..419379 / PC2 423A4C..423AB5: action0 is right/Esc,
        // action1 is left/Enter; these are separate from confirmation slots 2/3.
        if(button<0&&(ui.waiting||hasTimedInput())&&expected==ticket){String action=ui.actions[button==-2?1:0];return action.isEmpty()?null:action.getBytes(Binary.SJIS);}
        if(button<0)return null;return super.menuAction(expected,button);
    }
    @Override public void acceptMenuAction(int button)throws IOException{if(button<0&&!confirming)ui.actions[button==-2?1:0]="";timedInput=false;musicEndAction="";super.acceptMenuAction(button);}
    @Override public int read(int b,int i)throws IOException{
        if(i<0)throw new IOException("Negative PC variable index");
        if(b==100)return at(globals,i);if(b==97)return at(cg,i);if(b==98)return at(seen,i);if(b==95)return second?at(voiceEnabled,i):1;if(b==90)return at(scratch,i);
        if(b!=99)return 0;
        switch(i){case 3:return 1;case 4:return fullscreen?1:0;case 242:return skipButton?1:0;case 243:return catalogIndex("images",scene.slots[1]);case 244:return music;
        case 245:return ui.hovered;case 246:return list.active()?registers.getOrDefault(246,0):gridWaiting?2:0;case 247:return 0;case 248:return audioActive[1]?0:1;case 249:return audioActive[5]?0:1;case 250:return audioActive[0]?0:1;
        case 251:if(exe())return scriptSeen;return script==null?-1:catalogIndex("scenario_scripts",script.name);case 255:return registers.containsKey(255)?registers.get(255):0;
        default:return registers.containsKey(i)?registers.get(i):0;}
    }
    private static int at(byte[] b,int i){return i<b.length?b[i]&255:0;}
    @Override public void write(int b,int i,int v)throws IOException{
        if(i<0)throw new IOException("Negative PC variable index");
        byte[] target=b==100?globals:b==97?cg:b==98?seen:b==95&&second?voiceEnabled:b==90?scratch:null;
        if(target!=null){if(i<target.length)target[i]=(byte)v;return;}
        if(b==99&&i==242&&exe()){skipButton=v!=0;return;} // 41B6A3
        if(b==99&&i<256)registers.put(i,v); // Native setters for unknown banks have no effect.
    }
    @Override protected void unlock(String table,String name,int bank)throws IOException{int n=catalogIndex(table,name);if(n>=0)write(bank,n,1);}
    @Override public boolean beforeTransfer(int mode,String name)throws IOException{
        if(mode<1||mode>3)throw new IOException("PC transfer mode "+mode);
        ui.clearCurrent();grid.clear();list.clear();gridTarget=false;musicEndAction="";gridWaiting=confirming=false;remaining=0;body=speaker="";textWaiting=presentationWaiting=false;
        if(mode!=2){ui.actions[0]=ui.actions[1]="";}
        if(mode==1){scene.reset();ui.quads.clear();ui.quadTemplates.get(0).clear();}
        if(exe()){int n=catalogIndex("scenario_scripts",name);scriptSeen=n>=0?at(seen,n):0;if(scriptSeen==0&&mode!=2)skipButton=false;if(script!=null)unlock("scenario_scripts",script.name,98);choiceCount=0;history.clear();} // 41E2B0, 41E360, 41E571, 41E669
        else unlock("scenario_scripts",name,98);return true;
    }
    /** Resource selector codes are not texture slots; both generations use distinct maps. */
    private int slot(int value)throws IOException{
        if(second){if(value==0)return 1;if(value>=1&&value<=4)return value+1;if(value>=6&&value<=8)return value+4;if(value>=11&&value<=14)return value+10;}
        else {if(value==0)return 1;if(value>=1&&value<=3)return value+9;if(value==4)return 21;if(value==5)return 23;if(value==6)return 24;}
        throw new IOException("Unimplemented PC texture selector "+value);
    }
    private void quad(int[] a,boolean sprite)throws IOException{
        if(a.length!=8)throw new IOException("PC quad extension missing");
        int[] q=a.clone();
        // PC1 5/7 and PC2 20/21 describe a slider's variables/detents, not texture coordinates.
        if(sprite&&((!second&&(a[7]==5||a[7]==7))||(second&&(a[7]==20||a[7]==21)))){q[7]=a[7]==20?5:a[7]==21?7:a[7];ui.quad(q,true);if(exe()&&a[7]==21)ui.last().flags=12;return;}
        if(sprite&&second){if(q[7]==22)q[7]=12;else if(q[7]==23)q[7]=6;}
        boolean fill=!sprite&&(q[6]&64)!=0;
        if(!fill){q[2]=sprite?a[0]-a[2]:a[2];q[3]=sprite?a[1]-a[3]:a[3];if(!sprite){q[4]=a[4]-a[2];q[5]=a[5]-a[3];}q[6]=(q[6]&~63)|slot(q[6]&63);}
        if(sprite){q[6]|=spriteFlags;spriteFlags=0;if(q[7]==9)q[7]=99;}
        if(sprite&&gridTarget&&!grid.isEmpty()){Dc2Ui.Quad quad=ScriptList.sprite(q);quad.x+=ui.x;quad.y+=ui.y;grid.get(grid.size()-1).sprites.add(quad);}else ui.quad(q,sprite);
    }
    private void templates(int selector,boolean buttons)throws IOException{
        if(buttons){gridTarget=false;if(selector<0||selector>4)throw new IOException("PC button template "+selector);int[] cap={0,8,4,10,8};if(selector!=0){List<Dc2Ui.Button> t=ui.buttonTemplates.get(selector);t.clear();t.addAll(Dc2Ui.copyButtons(ui.buttons.subList(0,Math.min(cap[selector],ui.buttons.size()))));}ui.buttons.clear();ui.hovered=ui.pressed=-1;return;}
        if(selector==255&&second){ui.quads.clear();return;}int[] cap={0,10,5,second?30:20,10,5,second?30:20,second?30:20};
        // D.C.II 2006 415F20..41614C clamps each batch at 10/5/30/10/5/30/30 for selectors 1..7.
        if(original)cap=new int[]{0,10,5,30,10,5,30,30};
        if(selector<0||selector>255)throw new IOException("PC draw template "+selector);if(selector>0&&selector<cap.length){List<Dc2Ui.Quad> t=ui.quadTemplates.get(selector);t.clear();t.addAll(Dc2Ui.copyQuads(ui.quads.subList(0,Math.min(cap[selector],ui.quads.size()))));}ui.quads.clear();
    }
    @Override public void addChoice(byte[] label,byte[] caption)throws IOException{
        List<Dc2Ui.Button> templates=ui.buttonTemplates.get(3);int n=++choiceCount;if(n>=templates.size())throw new IOException("PC choice template missing");Dc2Ui.Button b=Dc2Ui.copyButtons(Collections.singletonList(templates.get(n))).get(0);b.action=TextCodec.cp932(label);
        for(Dc2Ui.Quad q:b.sprites)if(q.type==2){q.text=codec.decode(caption);q.width=caption.length*14+1;}ui.buttons.add(b);
    }
    /** PC2 412860 stores text state 9; 4132E3 makes it visible at rest, like image state 9. */
    private void normalizeButtonText(Dc2Ui.Quad q){if(second&&q.type==2&&q.state==9)q.state=99;}
    private void normalizeButtonText(List<Dc2Ui.Button> buttons){for(Dc2Ui.Button b:buttons)for(Dc2Ui.Quad q:b.sprites)normalizeButtonText(q);}
    @Override public boolean command(String op,int[] a,byte[] bytes)throws IOException{
        if(exe())noteDrawing(op,a);
        timedInput=false;
        String text=new String(bytes,Binary.SJIS);
        switch(op){
        case "dm.audio.voice":lineVoice=voiceStops(text)?"":text;playVoice(text);return true;
        case "pc.character":case "pc.character_overlay":if(exe()&&a.length>0&&a[0]>=0&&a[0]<4&&op.equals("pc.character")){boolean done=super.command("dm.scene.character",a,bytes);if(!text.isEmpty()&&!text.equals("--")){int[] c=pending[a[0]];int[] live=scene.characters[a[0]];live[0]=c[0];live[1]=c[1];live[2]=c[2]==0?100:c[2];}return done;}{
            boolean overlay=op.equals("pc.character_overlay");
            int[] slot=a.length>0?a:new int[]{overlay?1:0};
            return super.command(overlay?"dm.scene.character_overlay":"dm.scene.character",slot,bytes);
        }
        case "dm.ui.button_voice":super.command(op,a,bytes);if(followsExe&&second)ui.last().clickSound=255;return true; // 4248F8: a click voice replaces the click sound.
        case "ui.music_end_action":musicEndAction=text;return true;
        case "host.command_with_string":{
            gridTarget=false;super.command(op,a,bytes);
            // D.C.II 29h sets BE8BAC=1 and 4Eh consumes it on the region it just created.
            if(pendingRegionFlag){ui.last().flags|=1;pendingRegionFlag=false;}
            return true;
        }
        case "dm.ui.grid_rect":gridTarget=true;return super.command(op,a,bytes);
        // 414EF1 sets BE8BAC=1; 414F61 then consumes it to flag the region the next 4Eh creates.
        case "pc.region_flag":pendingRegionFlag=true;return true;
        case "pc.grid.item":gridTarget=false;list.add(a,slot(a[6]));return true;
        case "grid.item_action":list.last().action=text;return true;
        // 41D696 writes the preceding entry's x/y and normal/hover palettes,
        // consuming the next raw string record even when there is no entry.
        case "pc.grid.caption":{
            if(!second||a.length!=4)throw new IOException("PC2 list caption arguments");
            if(list.items.isEmpty())return true;
            ScriptList.Item item=list.last();item.sprites.removeIf(q->q.type==2);
            String caption=prose(bytes).replace("$f",familyName);
            for(int n=0;n<2;n++){Dc2Ui.Quad q=new Dc2Ui.Quad();q.type=2;q.x=a[0];q.y=a[1];q.flags=a[2+n];q.state=n==0?99:1;q.text=caption;q.width=bytes.length*14;q.height=28;item.sprites.add(q);}
            return true;
        }
        case "text.speaker":speaker=prose(bytes);speakerPending=true;return true;
        case "text.body":scene.publish(ui,0,0);if(!speakerPending||messageMode!=0)speaker="";speakerPending=false;body=prose(bytes);unlock("scenario_scripts",script.name,98);if(!body.isEmpty())history.append(speaker,body,lineVoice,script.name,pc);lineVoice="";presentationWaiting=true;textRendered=false;nextTicket();return true;
        case "ui.button_text":case "dm.ui.text":{
            boolean sprite=op.equals("ui.button_text");ui.text(a,new byte[0],sprite);Dc2Ui.Quad q=sprite?ui.last().sprites.get(ui.last().sprites.size()-1):ui.quads.get(ui.quads.size()-1);
            // The 2006 scene-replay lists prefix every label with "@s" (4674 of 4685 cases) and the
            // native sprite keeps the marker inside its 14-pitch width. D.C.II P.C stores the same
            // labels without it, so draw the bare text and keep the native width.
            byte[] label=bytes;
            if(sprite&&label.length>2&&label[0]=='@'&&label[1]=='s')label=java.util.Arrays.copyOfRange(label,2,label.length);
            q.text=codec.decode(label);if(sprite){q.width=bytes.length*14+1;normalizeButtonText(q);}return true;}
        case "pc.movie":{
            // D.C.II writes "advdata\movie\name.mpg"; normalise before the prefix check so the
            // path is not prefixed twice.
            String path=text.replace('\\','/');
            if(!path.toLowerCase(Locale.ROOT).startsWith("advdata/"))path="AdvData/"+path;
            movie=Binary.safePath(path);movieGeneration++;play(0,"",false);nextTicket();return true;
        }
        case "pc.history":case "pc.checkpoint":checkpoint=a[0]|a[1]<<8;if(op.equals("pc.history"))readCheckpoints.add(script.name+":"+checkpoint);return true;
        case "pc.reset":variables.clearOrdinary();variables.clearHistory();return true;
        case "pc.save_context":return true; // The session owns a complete caller snapshot, including the current sentence.
        case "pc.fullscreen":windowRequest=a[0];fullscreen=a[0]!=0;return true; // Android owns the full-screen surface.
        case "pc.sprite_opaque":spriteFlags=128;return true;
        case "pc.button_hover_voice":return true; // Hover sounds have no corresponding hover event on a touchscreen.
        case "pc.rapid_binding":return true; // Native rapid-only decoration; ordinary drawing remains enabled.
        case "pc.button_template":templates(a[0],true);return true;
        case "pc.quad_template":templates(a[0],false);return true;
        case "pc.resource":case "pc.resource_payload":if(a[0]<0||a[0]>3)throw new IOException("PC message resource slot");scene.slots[21+a[0]]=text;return true;
        // Original D.C.II 414B9D switches on the argument into three fixed handles 45E808/45E80C/
        // 45E810, i.e. 0 -> 21, 2 -> 22, 3 -> 23. There is no handle for 1, so the PC slot formula
        // above does not apply to this profile.
        case "dm.system.resource_handle":{
            int slot=a[0]==0?21:a[0]==2?22:a[0]==3?23:-1;
            if(slot<0)throw new IOException("DM resource handle "+a[0]);
            scene.slots[slot]=text;return true;
        }
        case "pc.image6":case "pc.image7":case "pc.image8":scene.slots[10+op.charAt(op.length()-1)-'6']=text.equals("-")?"":text;return true;
        case "pc.background":super.command("dm.scene.background",a,bytes);return super.command("dm.scene.preload",new int[]{1},bytes);
        case "pc.character_pair":return super.command("dm.scene.character",a,bytes);
        case "pc.portrait_pair":scene.slots[18+a[0]]=text.equals("-")?"":text;return true;
        // Original D.C.II 416896 encodes no slot byte: the string record alone picks the actor,
        // so 32h drives slot 0 and 33h drives the overlay slot.
        case "pc.quad":quad(a,false);return true;
        // Original D.C.II 416151 records the 51h companion as {sx, sy, right, bottom}: the unflagged
        // form stores right-sx/bottom-sy, while 40h keeps both raw and reuses the low bits as a colour.
        // That is the same contract quad() already applies for pc.quad.
        case "dc2.quad":if(a.length!=8)throw new IOException("D.C.II region needs the 51h extension");quad(a,false);return true;
        case "pc.button_sprite":quad(a,true);return true;
        case "pc.origin":ui.x=a[2]==0?a[0]:ui.x+a[0];ui.y=a[2]==0?a[1]:ui.y+a[1];return true;
        case "pc.action0":case "pc.action1":case "pc.action2":case "pc.action3":ui.actions[op.charAt(op.length()-1)-'0']=text;return true;
        case "pc.ui.wait":if(a[1]==1){delay(200L*a[0]);return true;}ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "pc.choice.begin":choiceMode=a[0];choiceCount=0;ui.buttons.clear();return true;
        case "pc.choice.wait":if(exe())skipButton=false;ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "pc.wait":timedInput=true;if(exe()){if(dirty&&dirtyRevision==scene.revision)scene.publish(ui,0,0);dirty=false;waitDismissable=(a[1]&1)!=0;delay(timedWaitMillis(a));return true;}scene.publish(ui,0,0);delay(a[0]==0?-1:200L*a[0]);return true;
        case "dm.ui.commit":if(exe())return true;return super.command(op,a,bytes); // 424DC2 draws off screen.
        case "dm.audio.music":{boolean done=super.command(op,a,bytes);if(exe()&&a[0]>=0&&a[0]<100&&read(99,14)!=0)write(95,a[0],1);return done;} // 429046: heard tracks unlock in the music page.
        case "pc.effect":
            if(!second&&(a[0]==1||a[0]==3||a[0]==4)){scene.slots[16]="system/"+(a[0]==3?"YUKI01":a[0]==1?"SACRA01":"SACRA02");scene.effect(1,a[1]);return true;}
            if(second&&(a[0]==1||a[0]==3||a[0]==4||a[0]==11||a[0]==12)){scene.slots[16]="system/"+(a[0]==12?"SAKU_YUKI02":a[0]==11?"SAKU_YUKI01":a[0]==3?"YUKI01":a[0]==1?"SACRA01":"SACRA02");scene.effect(1,a[1]);return true;}
            return super.command("dm.scene.effect",a,bytes);
        case "pc.transition":if(exe()&&transitionPc2(a))return true;if(a[0]==13){if(a[1]!=0&&a[1]!=1&&a[1]!=2)throw new IOException("PC2 mask variant "+a[1]);int minimum=a[1]==0?0:a[1]==1?(second?-191:-199):(second?-340:-348);int gain=0,ticks=1;while(minimum+gain<16){ticks++;gain+=Math.min(16,ticks);}scene.publish(ui,130+a[1],ticks*40L);delay(scene.transitionDuration);return true;}if(a[0]==7){return true;} // Native creates/releases a temporary transition bitmap, not a timed transition.
            if(second&&(a[0]==21||a[0]==22)){int[] rates={64,64,32,16,8,4,2,1};int duration=a[1]<8?40*(256+rates[a[1]]-1)/rates[a[1]]:(a[1]*5-35)*8*256;scene.publish(ui,200+a[0],duration);delay(duration);return true;}
            if(a[0]==9){captureUi();return true;} // Captured caller is a logical frame instead of a native render target.
            if(a[0]==10||a[0]==12||a[0]==14){scene.publish(ui,0,200L*Math.max(1,a[1]));delay(scene.transitionDuration);return true;}return super.command("dm.scene.transition",a,bytes);
        case "pc.scroll":if(a[2]==2){scene.portraitX=a[0];scene.portraitY=a[1];return true;}
            if(exe()&&a[2]!=1){ // 41F4E0: 17 ms steps; it cannot be clicked past.
                int speed=read(99,12),step=a[2]==0?(fast()||speed==3?16:speed==2?8:speed==1?4:2):Math.max(1,a[2]-8);
                long distance=Math.max(Math.abs((long)a[0]-scene.backgroundX),Math.abs((long)a[1]-scene.backgroundY));
                scene.targetX=a[0];scene.targetY=a[1];scene.scrollStep=step;scene.scrollPeriod=17;scene.scrollClock=0;
                delay(((distance+step-1)/step+(a[3]>0?Math.max(0,17-a[3]):0))*17);return true;
            }
            return super.command("dm.scene.scroll",a,bytes);
        case "pc.actor_position":if(exe()&&a[2]>=0&&a[2]<4){pending[a[2]]=new int[]{a[0],a[1],100};return true;}if(second){int n=a[2];if(n>=0&&n<4){scene.characters[n][0]=a[0];scene.characters[n][1]=a[1];scene.characters[n][2]=100;return true;}}else{scene.characters[0][0]=a[0];scene.characters[0][1]=a[1];scene.characters[1][0]=a[2];scene.characters[1][1]=a[3];return true;}throw new IOException("PC actor position "+Arrays.toString(a));
        case "pc.actor_transform":
            if(exe()&&a[3]>=0&&a[3]<4){ // 42620D, 421C40: argument 2 is ignored and the zoom stays.
                int[] c=scene.characters[a[3]];int speed=read(99,12),step=fast()||speed==3?64:speed==2?32:speed==1?16:8;
                long ms=(Math.max(Math.abs(a[0]-c[0]),Math.abs(a[1]-c[1]))+step-1)/step*10L;
                scene.beginSlide(a[3],c[0],c[1],step);c[0]=a[0];c[1]=a[1];scene.publish(ui,0,0);delay(ms);return true;
            }
            if(exe()&&a[3]>=16&&a[3]<20){int n=a[3]-16;pending[n][2]=a[0];scene.characters[n][3]=a[1];scene.characters[n][4]=a[2];return true;}
            if(second&&(a[3]&16)!=0)return super.command("dm.scene.character_transform",a,bytes);if(second&&a[3]<4){int n=a[3];int distance=Math.max(Math.abs(a[0]-scene.characters[n][0]),Math.abs(a[1]-scene.characters[n][1]));scene.characters[n][0]=a[0];scene.characters[n][1]=a[1];scene.characters[n][2]=a[2];long ms=200;scene.publish(ui,0,ms);delay(ms);return true;}throw new IOException("PC actor transform "+Arrays.toString(a));
        case "pc.sound8":return super.command("dm.audio.sound",new int[]{a[0]==255?-1:a[0],a[1]},bytes);
        case "pc.save_caption":if(exe()){callRequest=text;nextTicket();return true;}checkpointLabel=text;return true;
        case "pc.persist":switch(a[0]){
            case 0:loadRequest=read(99,254);nextTicket();return true;
            case 8:windowRequest=a[1];fullscreen=a[1]!=0;return true;
            case 2:case 3:case 5:case 10:case 12:case 17:return true;
            case 4:if(a[1]==1)autosaveGeneration++;else{saveRequest=read(99,254);nextTicket();}return true;
            case 6:variables.clearHistory();if(exe())history.clear();return true;case 7:if(!exe())skipAllowed=a[1]!=0;return true;case 11:messageMode=a[1];if(exe())history.clear();return true;case 13:inputMode=a[1];return true;
            default:throw new IOException("Unimplemented PC persistence "+Arrays.toString(a));}
        case "pc.wait.media_mask":if((a[0]&~7)!=0)throw new IOException("PC native media timer mask "+a[0]);return super.command("wait.audio",a,bytes); // 41ED22 / 4230E0: music=1, voice=2, SE=4.
        case "pc.voice_group.add":if(voiceGroup.size()<10)voiceGroup.add(text);return true;
        case "pc.voice_group.play":
            if(a[0]==2){voiceGroup.clear();return true;}if(voiceGroup.isEmpty())return true;
            if(a[0]==0){randomState=randomState*214013+2531011;lineVoice=voiceGroup.get(((randomState>>>16)&32767)%voiceGroup.size());playVoice(lineVoice);}
            // 41CC10 uses device channels 3..9; 427680 uses 4..9. Group
            // playback checks the global voice switch, not individual mutes.
            else if(a[0]==1){lineVoice=MixedVoice.reference(voiceGroup.subList(0,Math.min(voiceGroup.size(),second?6:7)));voicePan=0;play(5,lineVoice,false);}return true;
        case "pc.random":randomState^=randomState<<13;randomState^=randomState>>>17;randomState^=randomState<<5;registers.put(255,a[0]==0?randomState&255:Math.floorMod(randomState,a[0]));return true;
        case "pc.grid.begin":grid.clear();return true;
        case "pc.grid.configure":if(a.length!=8)throw new IOException("PC grid extension missing");System.arraycopy(a,0,gridBounds,0,8);grid.clear();list.clear();gridTarget=false;gridWaiting=false;registers.put(253,-1);registers.put(254,-1);return true;
        case "pc.grid.wait":if(list.active()){list.columns=grid.size()>1&&grid.get(0).top==grid.get(1).top?2:1;registers.put(246,list.clamp(read(99,246),grid.size()));}gridWaiting=ui.waiting=true;scene.publish(ui,0,0);nextTicket();return true;
        case "pc.grid.sound":gridHoverSound=a[0];gridClickSound=a[1];return true;
        case "pc.grid.voice":if(!grid.isEmpty())grid.get(grid.size()-1).voice=text;return true;
        case "pc.confirm_position":confirmX=a[0];confirmY=a[1];confirmKind=a[3];return true;
        case "pc.prompt_position":confirmX=a[0]*8;confirmY=a[1]*8;return true;
        case "pc.confirm":{
            List<Dc2Ui.Button> list=Dc2Ui.copyButtons(ui.buttonTemplates.get(2));if(list.size()<3)throw new IOException("PC original confirmation template missing");Dc2Ui.Button panel=list.get(0);int dx=confirmX-panel.left,dy=confirmY-panel.top;boolean caption=false;
            for(Dc2Ui.Button b:list){b.left+=dx;b.right+=dx;b.top+=dy;b.bottom+=dy;for(Dc2Ui.Quad q:b.sprites){q.x+=dx;q.y+=dy;if(b==panel&&q.type==2){q.text=codec.decode(bytes);q.width=Math.max(1,bytes.length*11);caption=true;}}}
            if(!caption)throw new IOException("PC confirmation text anchor missing");ui.buttons.clear();ui.buttons.addAll(list);confirming=ui.waiting=true;gridWaiting=false;nextTicket();return true;
        }
        case "pc.command":
            if(!second&&a[0]==8){nameInput.prepare(resources,codec);nameInput.open(familyName,givenName);nameInput.build(ui);scene.publish(ui,0,0);nextTicket();return true;}if(second&&a[0]==9){if(exe())list.textStyle=3;return true;} /* 407C90: the music list, whose title bar 408A70 draws. */
            if(second&&a[0]==7){list.textStyle=4;return true;} // 408911 -> 407340: Scene list renderer; argument 1 is ignored.
            if(second&&a[0]==20){map.open(variables);map.buttons(ui);scene.publish(ui,0,0);nextTicket();return true;}if(a[0]==6){saveGrid=a[1]==0;return true;}
            if(second&&a[0]==10){endingClock=0;return true;} // 408960: records timeGetTime for the ending service.
            if(second&&a[0]==11){if(a[1]==0)pcCredits.active=false;else{pcCredits.start(a[1],endingClock);scene.publish(ui,0,0);nextTicket();}return true;}
            if(!second&&a[0]==10){pcCredits.preparePc1();return true;} // 405180 prepares twelve original 800x1200 strips.
            if(!second&&a[0]==11){pcCredits.active=true;scene.publish(ui,0,0);nextTicket();return true;}
            if(a[0]==1){calendar(a[1]);return true;}
            if(a[0]==2&&(a[1]==0||a[1]==1)){rank(a[1]);return true;}
            if(a[0]!=7&&a[0]!=8&&a[0]!=9&&a[0]!=10&&a[0]!=11&&a[0]!=100&&a[0]!=101)return true; // Explicit default return in 404EE0 / 408560.
            throw new IOException("Unimplemented PC native system command "+Arrays.toString(a));
        default:return super.command(op,a,bytes);
        }
    }
    /** The native Scene renderer indexes the scenario catalog by the list entry id.
     * Empty actions are locked entries and must not reveal their date or decoration. */
    public String sceneListName(ScriptList.Item item)throws IOException{
        if(!second||list.textStyle!=4||item.action.isEmpty())return "";
        List<NamesCatalog.Entry> entries=resources.names.table("scenario_scripts");
        return item.id>=0&&item.id<entries.size()?entries.get(item.id).name:"";
    }
    /** 407360: this specific set receives the original special-scene background. */
    static boolean sceneListSpecial(String name){
        String[] names={"ind_0110_e1_rno","fst_1224_f1_snb","ind_0102_f1_mhr","ind_0121_e2_ais","ind_0120_f2_erk","ind_0118_f2_erk","ind_0128_f2_myk","ind_0122_f3_myk","ind_0124_f1_mya","ind_0120_f3_mya","ind_0211_f1_akn","ind_0115_f2_akn","ind_0129_f2_anz","ind_0120_f1_mnt","ind_0116_f1_mnt","ind_0127_f1_nnk","ind_0116_f1_nnk","ind_0127_f3_kko","ind_0128_a1_yme","ind_0121_f2_yme","ind_0120_f3_yme","ind_0401_f1_otm"};
        for(String value:names)if(value.equalsIgnoreCase(name))return true;return false;
    }
    /** 4078B0 / 407F48: date or original route-period label, independent of the title. */
    static String sceneListDate(String name){
        if(name.isEmpty())return "";
        if(name.charAt(0)=='0'||name.charAt(0)=='1')return " dacapo";
        int separator=name.indexOf('_');String value=separator<0?"":name.substring(separator+1);int date=0;
        for(int i=0;i<value.length()&&value.charAt(i)>='0'&&value.charAt(i)<='9';i++)date=date*10+value.charAt(i)-'0';
        if(date==0||date>1300||date==400||value.equals("0216_b1_anz")||value.equals("0305_b1_akn")||value.equals("0125_a1_rno"))return " epilogue";
        if(date/100==4)return " alfine";
        if(date==1)return " dacapo";if(date==2)return " alfine";if(date==3)return " epilogue";
        return date>=100?String.format(Locale.ROOT,"%2d月%2d日",date/100,date%100):"";
    }
    /** Native 404EE0 / 408560; calendar fields are script-visible route state. */
    private void calendar(int mode)throws IOException{
        if(mode!=0&&mode!=1)return;
        if(mode==0){String n=script.name.replace('\\','/');n=n.substring(n.lastIndexOf('/')+1).toUpperCase(Locale.ROOT);
            if(second){
                if(n.length()<9||!Character.isDigit(n.charAt(4))){variables.write(9,0,0);variables.write(9,1,0);variables.write(9,3,0);variables.write(9,20,5);}
                else{int month=Integer.parseInt(n.substring(4,6)),day=Integer.parseInt(n.substring(6,8));variables.write(9,0,month);variables.write(9,1,day);variables.write(9,3,n.charAt(8)-'A');
                    int season=month==7&&day<22?(n.startsWith("S_GM0721E")||n.startsWith("S_GM0721F")?1:0):n.startsWith("S_AS")||n.startsWith("S_HS")?0:n.startsWith("S_GM")?1:2;
                    variables.write(9,80,season);variables.write(9,20,n.startsWith("S_AS")?3:n.startsWith("S_GM")?4:n.startsWith("S_TM")||n.startsWith("S_TT")?0:n.startsWith("S_MM")?2:n.startsWith("S_KN")||n.startsWith("S_KH")?1:5);
                }
            }else{if(n.length()<5)throw new IOException("PC calendar filename too short: "+n);variables.write(9,0,(n.charAt(0)-'0')*10+n.charAt(1)-'0');variables.write(9,1,(n.charAt(2)-'0')*10+n.charAt(3)-'0');char period=n.charAt(4);variables.write(9,3,period=='B'?1:period=='C'||period=='D'?2:0);}
            if(!second)return;
        }
        int days=variables.read(9,1),month=variables.read(9,0);if(second&&month>12){month%=10;days+=365;}
        int[] lengths={31,28,31,30,31,30,31,31,30,31,30};for(int n=0;n<lengths.length&&n+1<month;n++)days+=lengths[n];variables.write(9,2,(days+(second?5:1))%7);
    }
    private void rank(int mode)throws IOException{
        // PC2's native loop reads thirteen words, including the adjacent constant bytes.
        // Preserve the observed binary's comparison exactly; do not invent new route priorities.
        int[] priorities=second?new int[]{6,5,4,3,2,1,1654170256,-1417526131,1351985282,27799,0,0,0}:new int[]{12,13,11,10,7,8,0,5,4,3,6,9,0};
        int score=mode==0?0:2099,index=mode==0?0:15;for(int n=0;n<priorities.length;n++){int value=variables.read(0,n)*100+priorities[n];if(value>score){score=value;index=n;}}variables.write(9,10,index);
    }
    @Override protected void playVoice(String source)throws IOException{if(voiceStops(source)){play(5,"",false);return;}if(exe()&&fast()&&variables.read(8,25)!=1)return; /* 42640F: skipping plays no voices. */String name=source;voicePan=name.startsWith("<")?-1000:name.startsWith(">")?1000:0;if(name.startsWith("<")||name.startsWith(">")||name.startsWith("="))name=name.substring(1);voiceCharacter=PcVoice.character(name,second);play(5,PcVoice.forced(name,second)||read(100,60+voiceCharacter)==0?name:"",false);}
    @Override public float voiceGain()throws IOException{return audio[5].startsWith("mix:")||PcVoice.forced(audio[5],second)||read(100,60+PcVoice.character(audio[5],second))==0?1:0;}
    private String prose(byte[] raw)throws IOException{return codec.decode(raw).replace("$n",givenName).replace("$s",familyName);}
    @Override public void copyPersistentFrom(Dc2Host other)throws IOException{super.copyPersistentFrom(other);PcHost p=(PcHost)other;familyName=p.familyName;givenName=p.givenName;skipButton=p.skipButton;saveTitles=p.saveTitles;musicTitles=p.musicTitles;System.arraycopy(p.globals,0,globals,0,globals.length);System.arraycopy(p.cg,0,cg,0,cg.length);System.arraycopy(p.seen,0,seen,0,seen.length);System.arraycopy(p.voiceEnabled,0,voiceEnabled,0,voiceEnabled.length);}
    private void savePc(DataOutput o)throws IOException{o.writeInt(0x50435032);o.write(globals);o.write(cg);o.write(seen);o.write(voiceEnabled);o.write(scratch);o.writeInt(spriteFlags);o.writeInt(randomState);o.writeUTF(familyName);o.writeUTF(givenName);}
    private void loadPc(DataInput i)throws IOException{int format=i.readInt();if(format!=0x50435031&&format!=0x50435032)throw new IOException("PC state format");i.readFully(globals);i.readFully(cg);i.readFully(seen);i.readFully(voiceEnabled);i.readFully(scratch);spriteFlags=i.readInt();randomState=i.readInt();if(format>=0x50435032){familyName=i.readUTF();givenName=i.readUTF();if(familyName.length()>4||givenName.length()>4)throw new IOException("Saved player name too long");}}
    @Override public void save(DataOutput o)throws IOException{super.save(o);o.writeInt(0x50434836);savePc(o);o.writeBoolean(timedInput);for(int n:gridBounds)o.writeInt(n);map.save(o);pcCredits.save(o);o.writeInt(voiceGroup.size());for(String voice:voiceGroup)o.writeUTF(voice);list.save(o);o.writeBoolean(gridTarget);o.writeUTF(musicEndAction);nameInput.save(o);for(int[] c:pending)for(int v:c)o.writeInt(v);o.writeInt(scriptSeen);}
    @Override public void load(DataInput i)throws IOException{super.load(i);int format=i.readInt();if(format!=0x50434832&&format!=0x50434833&&format!=0x50434834&&format!=0x50434835&&format!=0x50434836)throw new IOException("PC host state version");loadPc(i);timedInput=i.readBoolean();for(int n=0;n<8;n++)gridBounds[n]=i.readInt();map.load(i);voiceGroup.clear();if(format>=0x50434833){pcCredits.load(i);int count=i.readInt();if(count<0||count>10)throw new IOException("PC voice group count");while(count-->0)voiceGroup.add(i.readUTF());}if(format>=0x50434834){list.load(i);gridTarget=i.readBoolean();musicEndAction=i.readUTF();}if(format>=0x50434835){nameInput.load(i);if(nameInput.active)nameInput.prepare(resources,codec);}
        if(format>=0x50434836){for(int[] c:pending)for(int n=0;n<3;n++)c[n]=i.readInt();scriptSeen=i.readInt();}
        else if(exe()){ // Saves from before the slide fix can hold actors of zero size.
            for(int[][] actors:new int[][][]{scene.characters,scene.shown.actors,scene.previous.actors})for(int[] c:actors)if(c[2]==0)c[2]=100;
            for(int n=0;n<4;n++)pending[n]=new int[]{scene.characters[n][0],scene.characters[n][1],scene.characters[n][2]};
        }
        // Older phone snapshots contain the unconverted native text state in both the active menu and START templates.
        normalizeButtonText(ui.buttons);for(List<Dc2Ui.Button> template:ui.buttonTemplates)normalizeButtonText(template);
        // Before native defaults were initialized, all thirteen entries were white. Preserve any nondefault saved palette.
        boolean missing=true;for(int n=0;n<PC_PALETTE.length;n++)missing&=ui.palette[n]==0xffffffff;if(missing)System.arraycopy(PC_PALETTE,0,ui.palette,0,PC_PALETTE.length);
    }
    @Override public void savePersistent(DataOutput o)throws IOException{super.savePersistent(o);savePc(o);}
    @Override public void loadPersistent(DataInput i)throws IOException{super.loadPersistent(i);loadPc(i);if(exe()){migrateProgressPc2();registers.put(25,0);}} // Register 25 locks input for one scene and must not outlive it.
}
