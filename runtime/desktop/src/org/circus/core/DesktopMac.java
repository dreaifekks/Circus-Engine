package org.circus.core;

import java.awt.AlphaComposite;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;

/** What only the Mac app needs: Application Support paths, the remembered game and the menu bar. */
final class DesktopMac {
    static final boolean ACTIVE=System.getProperty("os.name","").startsWith("Mac");
    private static final Path SUPPORT=Paths.get(System.getProperty("user.home"),"Library","Application Support","DC Runtime");
    private static final Path LAST_GAME=SUPPORT.resolve("last-game.txt");

    /** A 16 ms Swing timer fires about every 20 ms on a Mac; 14 ms gives the intended 60 frames a second. */
    static final int FRAME_DELAY=14;

    private DesktopMac(){}

    /**
     * Smooth scaling on a BufferedImage replaced every frame runs in software, at 65 ms a frame in full screen.
     * Copied to a VolatileImage first, the GPU scales it in about 2 ms. Whole multiples stay sharp.
     */
    static final class Scaler {
        private VolatileImage target;
        void draw(Graphics2D g,JComponent owner,BufferedImage image,int x,int y,int w,int h){
            if(target==null||target.getWidth()!=image.getWidth()||target.getHeight()!=image.getHeight()||target.validate(owner.getGraphicsConfiguration())==VolatileImage.IMAGE_INCOMPATIBLE)target=owner.createVolatileImage(image.getWidth(),image.getHeight());
            Graphics2D copy=target.createGraphics();
            try{copy.setComposite(AlphaComposite.Src);copy.drawImage(image,0,0,null);}finally{copy.dispose();}
            double device=g.getTransform().getScaleX()*w/image.getWidth();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,device==Math.rint(device)?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR:RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(target,x,y,w,h,null);
        }
    }

    /** Runs before AWT starts: from an app the working directory is /, so the build/ defaults cannot work. */
    static void prepare(){
        if(System.getProperty("dc.saveRoot")==null)System.setProperty("dc.saveRoot",SUPPORT.resolve("saves").toString());
        if(System.getProperty("dc.cacheRoot")==null)System.setProperty("dc.cacheRoot",SUPPORT.resolve("cache").toString());
        PcHost.followsExe=true;
        System.setProperty("apple.laf.useScreenMenuBar","true");
        System.setProperty("apple.awt.application.name","DC Runtime");
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(ReflectiveOperationException|UnsupportedLookAndFeelException e){System.err.println(e);}
        Thread fonts=new Thread(DesktopMac::warmFonts,"dc-font-warmup");fonts.setDaemon(true);fonts.start();
    }

    /** The first CJK glyph makes macOS build its font fallback list, which stalled the first line of text for about 340 ms. */
    private static void warmFonts(){
        Graphics2D g=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try{for(String family:new String[]{"Microsoft YaHei","Hiragino Sans"}){g.setFont(new Font(family,Font.PLAIN,24));g.drawString("桜あア简體",0,32);}}finally{g.dispose();}
    }

    /** Cmd+Q closes the window, as the close button does, so progress is persisted. The API is newer than --release 8. */
    @SuppressWarnings({"unchecked","rawtypes"})
    static void quitByClosingWindows(){
        try{
            Class strategy=Class.forName("java.awt.desktop.QuitStrategy");
            Desktop.class.getMethod("setQuitStrategy",strategy).invoke(Desktop.getDesktop(),Enum.valueOf(strategy,"CLOSE_ALL_WINDOWS"));
        }catch(ReflectiveOperationException e){System.err.println(e);}
    }

    static Path rememberedGame(){
        try{
            Path game=Paths.get(new String(Files.readAllBytes(LAST_GAME),StandardCharsets.UTF_8));
            return Files.isDirectory(game)?game:null;
        }catch(IOException e){return null;}
    }

    static void remember(Path game){
        try{
            Files.createDirectories(SUPPORT);
            Files.write(LAST_GAME,game.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
        }catch(IOException e){System.err.println(e);}
    }

    static void forgetGame(){
        try{Files.deleteIfExists(LAST_GAME);}catch(IOException e){System.err.println(e);}
    }

    /** Function keys belong to the system on a Mac (F11 shows the desktop), so each one also gets a menu shortcut. */
    static void menu(JFrame frame,JComponent keys,ActionListener chooseGame){
        JMenu game=new JMenu("游戏");
        game.add(item("选择其他游戏目录…",KeyEvent.VK_O,0,chooseGame));
        game.addSeparator();
        game.add(key("快速保存（F5）",KeyEvent.VK_S,0,keys,KeyEvent.VK_F5));
        game.add(key("快速读取（F9）",KeyEvent.VK_L,0,keys,KeyEvent.VK_F9));
        game.add(key("全屏（F11）",KeyEvent.VK_F,InputEvent.CTRL_DOWN_MASK,keys,KeyEvent.VK_F11));
        JMenuBar bar=new JMenuBar();
        bar.add(game);
        frame.setJMenuBar(bar);
    }

    private static JMenuItem key(String label,int shortcut,int modifiers,JComponent keys,int code){
        return item(label,shortcut,modifiers,e->{KeyEvent press=new KeyEvent(keys,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,code,KeyEvent.CHAR_UNDEFINED);for(KeyListener listener:keys.getKeyListeners())listener.keyReleased(press);});
    }

    private static JMenuItem item(String label,int shortcut,int modifiers,ActionListener action){
        JMenuItem item=new JMenuItem(label);
        item.setAccelerator(KeyStroke.getKeyStroke(shortcut,InputEvent.META_DOWN_MASK|modifiers));
        item.addActionListener(action);
        return item;
    }
}
