package org.circus.core;

import java.awt.Desktop;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;

/** What only the Mac app needs: Application Support paths, the remembered game and the menu bar. */
final class DesktopMac {
    static final boolean ACTIVE=System.getProperty("os.name","").startsWith("Mac");
    private static final Path SUPPORT=Paths.get(System.getProperty("user.home"),"Library","Application Support","DC Runtime");
    private static final Path LAST_GAME=SUPPORT.resolve("last-game.txt");

    private DesktopMac(){}

    /** Runs before AWT starts: from an app the working directory is /, so the build/ defaults cannot work. */
    static void prepare(){
        if(System.getProperty("dc.saveRoot")==null)System.setProperty("dc.saveRoot",SUPPORT.resolve("saves").toString());
        if(System.getProperty("dc.cacheRoot")==null)System.setProperty("dc.cacheRoot",SUPPORT.resolve("cache").toString());
        System.setProperty("apple.laf.useScreenMenuBar","true");
        System.setProperty("apple.awt.application.name","DC Runtime");
        try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(ReflectiveOperationException|UnsupportedLookAndFeelException e){System.err.println(e);}
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
