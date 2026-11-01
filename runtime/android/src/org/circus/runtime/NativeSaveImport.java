package org.circus.runtime;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.circus.core.*;

/** Explicit, read-only original-save source; only merged progress is written privately. */
final class NativeSaveImport {
    static final String SOURCE="AdvData/SaveData/system.dat";
    private static final Object LOCK=new Object();
    static final class Request {
        final CircusResources data;final TextCodec codec;final Path root,legacyProgress;final List<String> sources;
        Request(CircusResources data,TextCodec codec,Path root,Path legacy)throws IOException{this(data,codec,root,legacy,true);}
        Request(CircusResources data,TextCodec codec,Path root,Path legacy,boolean requireSource)throws IOException{this.data=data;this.codec=codec;this.root=root;legacyProgress=legacy;sources=NativeProgress.sourcePaths(data.files,data.profileId);if(requireSource&&sources.isEmpty())throw new IOException("未找到原版 system.dat。可返回游戏卡片，选择“导入原版进度（选文件）”直接点选文件。目录查找支持 AdvData/SaveData、SaveData、游戏根目录，以及 W.Y. 的 dc3wy、P.P. 的 dc3pp 文件夹。");}
        Preview prepare(String source)throws IOException{if(!sources.contains(source))throw new IOException("无效的原版进度来源");Preview p=NativeSaveImport.prepare(data,codec,root,source);p.legacyProgress=legacyProgress;requireMigrated(p.directory.resolve("progress.dat"),legacyProgress);return p;}
    }
    static final class Preview {
        final CircusResources data;final TextCodec codec;final Path directory;
        final NativeProgress progress;final NativeProgress.Summary additions;
        final String sourcePath,sourceHash,identity;final int storySlots;
        Path legacyProgress;SelectedSaveFile.Source selectedSource;
        Preview(CircusResources data,TextCodec codec,Path directory,NativeProgress progress,String path,String hash,int slots)throws IOException{
            this.data=data;this.codec=codec;this.directory=directory;this.progress=progress;sourcePath=path;sourceHash=hash;storySlots=slots;
            identity=DesktopEngine.mesIdentity(data);additions=progress.preview(readTarget(this));
        }
        String description(){String action=progress.source.total==0?"原版系统存档中没有可导入的解锁记录。":additions.total==0?"手机已包含这些解锁记录，无需重复导入，不会修改文件。":"将合并已解锁进度，通关计数保留较高值；导入前备份手机现有系统进度。手机设置和已有剧情存档保留。";return "来源："+sourcePath+"\n版本："+data.profileId+"\n\n"+counts(additions)+"\n（统计内部标记，不等于路线或图片数量）\n\n"+action+"\n\n同目录检测到 "+storySlots+" 个原版剧情存档槽位；此版本仅导入系统解锁进度，不转换剧情槽位或已读文本记录。";}
    }
    static final class Result {
        final NativeProgress.Summary added;final Path backup;
        Result(NativeProgress.Summary added,Path backup){this.added=added;this.backup=backup;}
        String description(){return (added.total==0?"手机已经包含这些进度，无需重复导入。":counts(added)+"\n\n重新启动游戏后可查看鉴赏和通关进度。")+(backup==null?"":"\n\n原手机系统进度已备份："+backup.getFileName());}
    }
    static String counts(NativeProgress.Summary s){return "新增或提高的通关标记："+s.completion+"\n新增 CG 标记："+s.cg+"\n新增场景标记："+s.scenes+"\n新增音乐标记："+s.music;}
    static Request inspect(Context context,GameLibrary.Entry entry)throws IOException{
        return inspect(context,entry,true);
    }
    private static Request inspect(Context context,GameLibrary.Entry entry,boolean folderSearch)throws IOException{
        if(!entry.configured)throw new IOException("请先保存此游戏的配置，再导入原版进度");
        if(UnityCircusResources.ID.equals(entry.profile))throw new IOException("DC3R Unity 请使用游戏卡片的“导入原版存档”入口。");
        if(!NativeProgress.supports(entry.profile))throw new IOException("原版进度导入支持 DCPC、DC2（2006）、DC2PC、DC2DM、DC3DD、DC3WY、DC3PP 的已验证版本；此游戏版本尚未适配");
        GameResources files=new GameResources(new DocumentTree(context.getContentResolver(),Uri.parse(entry.tree),folderSearch),entry.executable);
        Profile profile=GameVersion.detect(files).profile();if(!profile.id.equals(entry.profile))throw new IOException("游戏目录版本已改变，请重新配置游戏");
        TextCodec codec=GameText.select(files,profile,entry.encoding,entry.configuration);
        String oldKey=profile.id.equals("dc3dd-a5a8")?"dc3dd":profile.id.equals("dc2dm-9d72")?"dc2dm":profile.id;
        File old=new GameSaves(context,entry.tree,oldKey).directory;
        Path legacy=profile.id.equals("dc3dd-a5a8")?new File(context.getApplicationInfo().dataDir,"shared_prefs/"+old.getName()+".xml").toPath():new File(old,"progress.dm").toPath();
        return new Request(new CircusResources(files,profile),codec,new File(context.getFilesDir(),"shared-saves").toPath(),legacy,folderSearch);
    }
    /** A picker selection is sufficient: placement and extension do not select a save format. */
    static Result importFile(Context context,GameLibrary.Entry entry,Uri uri)throws IOException{
        SelectedSaveFile.Source source=SelectedSaveFile.open(context.getContentResolver(),uri);
        Request request=inspect(context,entry,false);
        Preview preview=prepareFile(request.data,request.codec,request.root,source);preview.legacyProgress=request.legacyProgress;
        return apply(preview);
    }
    /** Explicit destination and source keep the same importer testable without touching phone saves. */
    static Preview prepareFile(CircusResources data,TextCodec codec,Path root,SelectedSaveFile.Source source)throws IOException{
        byte[] raw=SelectedSaveFile.read(source,NativeProgress.fileSize(data.profileId));NativeProgress progress;
        try{progress=NativeProgress.parse(data.profileId,raw);}catch(IOException failure){throw new IOException("所选文件不是当前游戏的有效原版系统进度（允许改名）。\n"+failure.getMessage(),failure);}
        synchronized(LOCK){Preview preview=new Preview(data,codec,DesktopEngine.mesSaveDirectory(root,data),progress,SelectedSaveFile.label(source.name()),Binary.sha256(raw),0);preview.selectedSource=source;return preview;}
    }
    /** Separate root allows instrumentation to exercise the shipping service without user saves. */
    static Preview prepare(CircusResources data,TextCodec codec,Path root)throws IOException{
        List<String> sources=NativeProgress.sourcePaths(data.files,data.profileId);if(sources.size()!=1)throw new IOException("请选择原版系统存档来源（找到 "+sources.size()+" 份）");return prepare(data,codec,root,sources.get(0));
    }
    static Preview prepare(CircusResources data,TextCodec codec,Path root,String source)throws IOException{
        if(!NativeProgress.sourcePaths(data.files,data.profileId).contains(source))throw new IOException("无效的原版进度来源");
        byte[] raw=data.files.read(source,NativeProgress.fileSize(data.profileId));NativeProgress parsed=NativeProgress.parse(data.profileId,raw);
        int slots=0;for(String path:data.files.paths())if(NativeProgress.storySlot(data.profileId,source,path))slots++;
        synchronized(LOCK){return new Preview(data,codec,DesktopEngine.mesSaveDirectory(root,data),parsed,source,Binary.sha256(raw),slots);}
    }
    static NativeProgress.Target readTarget(Preview preview)throws IOException{
        NativeProgress.Target target=new NativeProgress.Target(preview.data,preview.codec);Path file=preview.directory.resolve("progress.dat");if(Files.exists(file))target.load(read(file));return target;
    }
    private static byte[] read(Path file)throws IOException{try(InputStream in=Files.newInputStream(file)){return Binary.read(in,16*1024*1024);}}
    static void requireMigrated(Path target,Path legacy)throws IOException{if(legacy!=null&&!Files.exists(target)&&(Files.exists(legacy)||Files.exists(legacy.resolveSibling(legacy.getFileName()+".bak"))))throw new IOException("检测到尚未迁移的旧版手机进度。请先启动此游戏，再返回游戏列表，让旧进度迁移完成后重新导入。");}
    static Result apply(Preview preview)throws IOException{
        synchronized(LOCK){
            requireMigrated(preview.directory.resolve("progress.dat"),preview.legacyProgress);
            if(!preview.identity.equals(DesktopEngine.mesIdentity(preview.data)))throw new IOException("游戏脚本已变化，请重新选择存档");
            byte[] source=preview.selectedSource==null?preview.data.files.read(preview.sourcePath,NativeProgress.fileSize(preview.data.profileId)):SelectedSaveFile.read(preview.selectedSource,NativeProgress.fileSize(preview.data.profileId));
            if(!preview.sourceHash.equals(Binary.sha256(source)))throw new IOException("原版存档在导入前发生变化，请重新选择文件");
            Path file=preview.directory.resolve("progress.dat");byte[] previous=Files.exists(file)?read(file):null;
            NativeProgress.Target target=new NativeProgress.Target(preview.data,preview.codec);if(previous!=null)target.load(previous);
            NativeProgress.Summary added=preview.progress.apply(target);if(added.total==0)return new Result(added,null);
            byte[] merged=target.save();NativeProgress.Target verified=new NativeProgress.Target(preview.data,preview.codec);verified.load(merged);if(preview.progress.preview(verified).total!=0)throw new IOException("导入进度校验失败");
            Path backup=null;if(previous!=null){backup=preview.directory.resolve("progress.before-native-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".dat");DesktopEngine.atomic(backup,previous);}
            DesktopEngine.atomic(file,merged);return new Result(added,backup);
        }
    }
}
