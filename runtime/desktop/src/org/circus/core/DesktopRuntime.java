package org.circus.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** One PC application, version-specific native bindings, one media/input/save platform. */
public final class DesktopRuntime implements AutoCloseable {
    public final DesktopEngine engine;public final DesktopMedia media;
    private String problem="";private Object mediaVm;
    public DesktopRuntime(Path game,boolean audit)throws Exception{
        GameResources files=new GameResources(new LocalGameStorage(game));
        if(UnityCircusResources.candidate(files))engine=new DesktopUnity(new UnityCircusResources(files),audit);
        else {
        GameVersion version=GameVersion.detect(files);
        CircusResources resources=new CircusResources(files,version.profile());TextCodec codec=GameText.detect(files,version.profile());
        // D.C.III D.D. / With You / P.P. share one host; only the opcode numbering and the
        // resource layout move between them, and both are carried by the profile.
        if(Dc3Host.supports(version.profile()))engine=new DesktopDd(resources,codec,audit);
        else if(version.profileId.equals("dc2dm-9d72")||PcHost.supports(version.profile()))engine=new DesktopLegacy(resources,codec,audit);
        else throw new IOException("Unsupported profile "+version.profileId);
        }
        media=new DesktopMedia(engine);mediaVm=engine.executionIdentity();
    }
    public void tick(long elapsed)throws Exception{if(!media.playingMovie())engine.tick(elapsed);if(mediaVm!=engine.executionIdentity()){if(media.inBackground()&&!engine.takeRestored())media.rebase();else media.reset();mediaVm=engine.executionIdentity();}media.sync();}
    public void click(int x,int y,boolean right)throws Exception{if(media.playingMovie())media.finishMovie();else engine.click(x,y,right);}
    public void close(){media.close();try{engine.persist();}catch(IOException e){System.err.println(e);}}
    private void audit(Path out)throws Exception{
        Files.createDirectories(out);boolean title=false,body=false,choice=false,actor=false,saved=false;int lines=0,choiceAt=0;String previous="",lastMenu="";
        try(BufferedWriter trace=Files.newBufferedWriter(out.resolve("trace.jsonl"),StandardCharsets.UTF_8)){
            for(int n=0;n<100000;n++){
                tick(1000);String location=engine.scriptName()+"@"+Integer.toHexString(engine.scriptOffset());
                trace.write(Json.text(Json.obj("pc",location,"ticket",engine.ticket(),"text_wait",engine.textWaiting(),"menu",engine.menuWaiting())));trace.newLine();
                if(engine.menuWaiting()){
                    java.util.List<String> actions=new ArrayList<>();for(DesktopEngine.Control b:engine.controls())actions.add(b.action);
                    if(!lastMenu.equals(location)){System.out.println(Json.text(Json.obj("menu",location,"actions",actions)));lastMenu=location;}
                    String script=engine.scriptName();String selected=null;
                    if(!title){for(String a:new String[]{"window","03","sce_start","start","story","sce"})if(actions.contains(a)){selected=a;break;}
                        if(selected!=null&&(selected.equals("start")||selected.equals("sce_start")||selected.equals("story")||selected.equals("sce"))){ImageIO.write(engine.render(),"png",out.resolve("01-title.png").toFile());title=true;}
                    }else if(lines==0&&actions.contains("sel01"))selected="sel01";
                    else if(lines==0&&actions.contains("sce01"))selected="sce01";
                    else if(lines==0&&actions.contains("sceselect01"))selected="sceselect01";
                    else if(lines==0&&actions.contains("menu01"))selected="menu01"; // D.C.III W.Y.: the branch select opens with menu01 before sel01.
                    else if(lines>0&&!choice){ImageIO.write(engine.render(),"png",out.resolve("04-choice.png").toFile());selected=actions.isEmpty()?null:actions.get(0);choice=true;choiceAt=lines;}
                    // The other games on the DC2PC engine name their menus freely; the first one that goes forward will do.
                    if(selected==null&&PcHost.pc2Family(engine.resources.profileId)&&(!title||lines==0))for(String a:actions)if(!a.startsWith("return")&&!a.startsWith("end")&&!a.startsWith(":")&&!java.util.Arrays.asList("exit","load","config","option","name","menu0","rmain","kaisou","extra","minigame","ex_anime").contains(a)){selected=a;break;}
                    if(selected!=null&&!title&&!actions.contains("window")&&!actions.contains("03"))title=true;
                    if(selected==null)throw new IOException("Audit needs original menu action at "+location+": "+actions);
                    engine.choose(selected);continue;
                }
                if(engine.textWaiting()&&!location.equals(previous)){previous=location;lines++;if(!body&&!engine.body().trim().isEmpty()){ImageIO.write(engine.render(),"png",out.resolve("02-first-text.png").toFile());body=true;}
                    if(!actor&&engine.actorVisible()){ImageIO.write(engine.render(),"png",out.resolve("06-character.png").toFile());actor=true;}
                    if(lines==20){BufferedImage before=engine.render();engine.save(out.resolve("roundtrip.sav"));engine.restore(out.resolve("roundtrip.sav"));media.reset();BufferedImage after=engine.render();int w=engine.width,h=engine.height;if(!Arrays.equals(before.getRGB(0,0,w,h,null,0,w),after.getRGB(0,0,w,h,null,0,w)))throw new IOException("Save/restore pixels changed");ImageIO.write(after,"png",out.resolve("03-restored.png").toFile());saved=true;}
                }
                if(choice&&actor&&saved&&lines>=choiceAt+20)break;
                if(engine.waiting())engine.click(engine.width/2,engine.height/2,false);
                if(n%1000==0)System.out.println(Json.text(Json.obj("iterations",n,"lines",lines,"location",location)));
                if(engine.halted())throw new IOException("Unexpected script halt "+location);
            }
        }
        boolean pass=title&&body&&choice&&actor&&saved&&lines>=choiceAt+20;
        Map<String,Object> result=Json.obj("profile",engine.resources.profileId,"original_title",title,"dialogue_waits",lines,"first_choice_clicked",choice,"dialogues_after_choice",choice?lines-choiceAt:0,"character_rendered",actor,"save_pixel_roundtrip",saved,"images",engine.loadedImages,"audio_decoded",engine.loadedAudio,"audible_output_verified",false,"movie_completion_simulated",true,"original_exe_run",false,"pass",pass);
        Files.write(out.resolve("result.json"),(Json.text(result)+"\n").getBytes(StandardCharsets.UTF_8));System.out.println(Json.text(result));if(!pass)throw new IOException("Startup/choice/save contract incomplete");
    }
    private void window(){
        if(DesktopMac.ACTIVE)media.playInBackground();
        JFrame frame=new JFrame(DesktopMac.pinnedGame()!=null?DesktopMac.appName():"DC Runtime — "+engine.resources.profileId);
        try{java.net.URL icon=DesktopRuntime.class.getResource("/launcher_logo.png");if(icon!=null)frame.setIconImage(ImageIO.read(icon));}catch(IOException ignored){}
        final DesktopMac.Scaler scaler=new DesktopMac.Scaler();JPanel panel=new JPanel(){protected void paintComponent(Graphics g){super.paintComponent(g);try{double s=Math.min(getWidth()/(double)engine.width,getHeight()/(double)engine.height);int w=(int)(engine.width*s),h=(int)(engine.height*s);BufferedImage movie=media.movieFrame();BufferedImage image=movie!=null?movie:engine.render();if(DesktopMac.ACTIVE)scaler.draw((Graphics2D)g,this,image,(getWidth()-w)/2,(getHeight()-h)/2,w,h);else g.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);}catch(Exception e){problem=e.toString();}if(!problem.isEmpty()){g.setColor(new Color(0,0,0,220));g.fillRect(0,0,getWidth(),70);g.setColor(Color.RED);g.drawString(problem,10,30);}}};panel.setPreferredSize(new Dimension(engine.width,engine.height));panel.setBackground(Color.BLACK);
        MouseAdapter mouse=new MouseAdapter(){private Point map(MouseEvent e){double s=Math.min(panel.getWidth()/(double)engine.width,panel.getHeight()/(double)engine.height);return new Point((int)((e.getX()-(panel.getWidth()-engine.width*s)/2)/s),(int)((e.getY()-(panel.getHeight()-engine.height*s)/2)/s));}public void mousePressed(MouseEvent e){try{Point p=map(e);if(SwingUtilities.isLeftMouseButton(e)&&!media.playingMovie())engine.pointerDown(p.x,p.y);else engine.cancelPointer();}catch(Exception ex){problem=ex.toString();}}public void mouseDragged(MouseEvent e){try{if(!media.playingMovie()){Point p=map(e);engine.pointerMove(p.x,p.y);}}catch(Exception ex){problem=ex.toString();}}public void mouseReleased(MouseEvent e){try{Point p=map(e);if(media.playingMovie()||SwingUtilities.isRightMouseButton(e)){engine.cancelPointer();click(p.x,p.y,SwingUtilities.isRightMouseButton(e));}else engine.pointerUp(p.x,p.y);}catch(Exception ex){problem=ex.toString();}}public void mouseMoved(MouseEvent e){try{Point p=map(e);engine.hover(p.x,p.y);}catch(Exception ex){problem=ex.toString();}}};final double[] wheel={0};final long[] wheelAt={0};panel.addMouseWheelListener(e->{try{if(!DesktopMac.ACTIVE){engine.scroll(e.getWheelRotation());return;}/* A trackpad sends fractions in a burst; a whole line of scroll becomes one step. */wheel[0]+=e.getPreciseWheelRotation();if(Math.abs(wheel[0])>=1&&System.currentTimeMillis()-wheelAt[0]>120){engine.scroll(wheel[0]<0?-1:1);wheel[0]=0;wheelAt[0]=System.currentTimeMillis();}}catch(Exception ex){problem=ex.toString();}});panel.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){engine.rapid=false;engine.cancelPointer();}});panel.addMouseListener(mouse);panel.addMouseMotionListener(mouse);
        Path quick=engine.saveDirectory.resolve("quick.sav");panel.setFocusable(true);panel.addKeyListener(new KeyAdapter(){public void keyTyped(KeyEvent e){if(engine.editingText()&&!e.isControlDown()&&!Character.isISOControl(e.getKeyChar()))engine.typeText(String.valueOf(e.getKeyChar()));}public void keyPressed(KeyEvent e){if(!engine.editingText()&&e.getKeyCode()==KeyEvent.VK_CONTROL)engine.rapid=true;try{if(!engine.editingText()&&!media.playingMovie())engine.navKey(e.getKeyCode());}catch(Exception ex){problem=ex.toString();}}public void keyReleased(KeyEvent e){try{if(engine.editingText()){engine.editKey(e.getKeyCode());return;}if(!media.playingMovie()&&engine.functionKey(e.getKeyCode()))return;if(e.getKeyCode()==KeyEvent.VK_CONTROL)engine.rapid=false;else if(e.getKeyCode()==KeyEvent.VK_F5)engine.save(quick);else if(e.getKeyCode()==KeyEvent.VK_F9){if(Files.exists(quick)){engine.restore(quick);media.reset();problem="";}}else if(e.getKeyCode()==KeyEvent.VK_F11)engine.fullscreenRequested=!engine.fullscreenRequested;else if(e.getKeyCode()==KeyEvent.VK_A)engine.auto=!engine.auto;else if(e.getKeyCode()==KeyEvent.VK_SPACE||e.getKeyCode()==KeyEvent.VK_ENTER){if(media.playingMovie())click(engine.width/2,engine.height/2,false);else engine.keyPress(e.getKeyCode()==KeyEvent.VK_SPACE);}else if(e.getKeyCode()==KeyEvent.VK_ESCAPE||DesktopMac.ACTIVE&&e.getKeyCode()==KeyEvent.VK_NUMPAD0)click(0,0,true);}catch(Exception ex){problem=ex.toString();}}});
        final boolean[] full={false};final Rectangle[] windowBounds={null};GraphicsDevice display=frame.getGraphicsConfiguration().getDevice();
        final long[] last={System.nanoTime()};javax.swing.Timer timer=new javax.swing.Timer(DesktopMac.ACTIVE?DesktopMac.FRAME_DELAY:16,e->{long now=System.nanoTime();long elapsed=(now-last[0])/1000000;last[0]+=elapsed*1000000;elapsed=Math.min(250,elapsed);if(problem.isEmpty())try{tick(elapsed);if(full[0]!=engine.fullscreenRequested){full[0]=engine.fullscreenRequested;if(full[0]){windowBounds[0]=frame.getBounds();display.setFullScreenWindow(frame);}else{display.setFullScreenWindow(null);if(windowBounds[0]!=null)frame.setBounds(windowBounds[0]);}}if(engine.windowSizeReset){engine.windowSizeReset=false;frame.pack();}if(engine.exitRequested())frame.dispose();}catch(Exception ex){problem=ex.toString();ex.printStackTrace();}panel.repaint();});
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){timer.stop();if(display.getFullScreenWindow()==frame)display.setFullScreenWindow(null);close();}});if(DesktopMac.ACTIVE)DesktopMac.menu(frame,panel,e->switchGame(frame));frame.add(panel);frame.pack();frame.setLocationByPlatform(true);frame.setVisible(true);panel.requestFocusInWindow();timer.start();
    }
    private static Path chooseGame(){JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("选择 D.C. 游戏目录（包含 AdvData）");chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setAcceptAllFileFilterUsed(false);return chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile().toPath():null;}
    private void switchGame(JFrame frame){
        Path game=chooseGame();if(game==null)return;
        try{engine.persist();DesktopRuntime next=new DesktopRuntime(game,false);DesktopMac.remember(game);frame.dispose();next.window();}
        catch(Exception e){e.printStackTrace();JOptionPane.showMessageDialog(frame,e.getMessage(),"DC Runtime 启动失败",JOptionPane.ERROR_MESSAGE);}
    }
    public static void main(String[] args)throws Exception{
        boolean macApp=DesktopMac.ACTIVE&&args.length<3,remembered=false;if(macApp)DesktopMac.prepare();
        if(macApp&&args.length==0&&DesktopMac.pinnedGame()!=null){Path pinned=DesktopMac.waitForPinnedGame();if(pinned==null)return;args=new String[]{pinned.toString()};}
        if(macApp&&args.length==0){Path last=DesktopMac.rememberedGame();if(last!=null){args=new String[]{last.toString()};remembered=true;}}
        if(args.length==0){final Path[] selected={null};SwingUtilities.invokeAndWait(()->{try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}selected[0]=chooseGame();});if(selected[0]==null)return;args=new String[]{selected[0].toString()};}
        if(args.length!=1&&!(args.length==3&&args[1].equals("--audit")))throw new IllegalArgumentException("dc_runtime <game-directory> [--audit output]");
        try{DesktopRuntime app=new DesktopRuntime(Paths.get(args[0]),args.length==3);if(args.length==3){try{app.audit(Paths.get(args[2]));}finally{app.close();}}else{if(macApp){if(DesktopMac.pinnedGame()==null)DesktopMac.remember(Paths.get(args[0]));DesktopMac.quitByClosingWindows();}SwingUtilities.invokeLater(app::window);}}
        catch(Exception e){if(args.length==3)throw e;e.printStackTrace();JOptionPane.showMessageDialog(null,e.getMessage(),"DC Runtime 启动失败",JOptionPane.ERROR_MESSAGE);if(remembered){DesktopMac.forgetGame();main(new String[0]);}}
    }
}
