# Circus Engine

用**原版脚本和原版素材**驱动 D.C.（ダ・カーポ）系列 1／2／3 的兼容运行时。
选一个游戏目录，执行该版本自己的启动、标题、正文、系统菜单和存读档；
不运行原 Windows EXE，不附带任何游戏数据。

仓库：[kesmeey/Circus-Engine](https://github.com/kesmeey/Circus-Engine)。
项目目录与命令行工具仍沿用 `circus-runtime`／`dc_runtime` 命名。

## 现在能玩的游戏

有独立宿主、由原版脚本驱动的一共 8 个入口。Windows 包和 Android APK 用的是同一份核心，
两边都能打开。

| 作品 | 数据格式 | 目前能跑到哪 |
|---|---|---|
| D.C. 〜ダ・カーポ〜 P.C. | `dcpc-2063` | 启动／正文／选择／存读档／到片尾 |
| D.C.II 〜ダ・カーポII〜 P.C. | `dc2pc-5769` | 启动／正文／选择／存读档／到片尾 |
| D.C.II 〜ダ・カーポII〜（2006 原版） | `dc2-3363` | 启动／正文／存读档／系统菜单／CG Mode／场景回想／闹钟设定；整条路线尚未验证 |
| D.C.II Dearest Marriage | `dc2dm-9d72` | 启动／正文／存读档／回看与语音重播／CG·场景·音乐鉴赏／到片尾 |
| D.C.III DreamDays | `dc3dd-a5a8` | 七条剧情路线到片尾 |
| D.C.III With You | `dc3wy-a09f` | 启动／正文／选择／存读档；整条路线尚未验证 |
| D.C.III P.P. | `dc3pp-9872` | 启动／正文／选择／存读档；整条路线尚未验证 |
| D.C.III R（Unity 5.5 版） | `dc3r-unity-550` | 标题／章节选择／正文／系统菜单；全路线尚未验证 |

「到片尾」指在本机完整跑通并返回标题；不等于原版所有演出时序和设置副作用都已复刻。
各版本的验证细节和缺口见 [版本记录](docs/RELEASES.md)。

### 只能识别，不能运行

解包器和格式表覆盖 26 种 MES 变体（D.C. White Season、Summer Vacation、After Seasons、
D.C.II To You／Spring Celebration 等），可以扫描、解包、反汇编、
导出图片和音频；但运行时没有对应宿主，选中会直接报 `Unsupported profile`。

范围只到 D.C. 1／2／3 及其 FD，不含 D.C.4／5。其他作品的格式支持不等于能运行。

## 跑起来

### Android

```powershell
python tools/build_android.py --java-home "E:\DevEco Studio\jbr"
```

产物 `build/android/circus-core-debug.apk`：API 26 起，含 arm64-v8a／armeabi-v7a／x86_64，
开发签名，不含游戏资源。需要 Android SDK（build-tools 36、platform 36）与 NDK 29。

1. 把**整部游戏目录**复制到手机，不要只拷 `AdvData`。
2. 「添加游戏目录」选包含 `AdvData` 的那一层；Unity 版选包含 `DC3_Data` 的目录。
3. 首次会识别版本并让你确认文字编码，之后点「启动游戏」，由原版脚本进标题。

SAF 只申请读取权限；存档和解码缓存写应用私有目录。

### Windows

```powershell
python tools/package_desktop.py --jdk <JDK21> --ffmpeg <ffmpeg.exe> --version 0.13.11-pc-dev
```

产物 `build/distribution/dc-runtime-0.13.11-pc-dev-windows-x64/`，双击 `DC Runtime.cmd`，
再选游戏目录。包内自带 JRE 和 FFmpeg，不需要另装 Java；存档写在包目录的 `saves/`。

## 操作（Android）

| 操作 | 作用 |
|---|---|
| 单指轻点 | 推进正文／点击按钮 |
| 双指轻点 | 鼠标右键，打开或返回原版菜单 |
| 双指上下滑 | 回看列表滚动 |
| 系统返回键 | 回上一级 |

## 存档

手机存档不是原 Windows 格式。原版的系统进度（通关、CG、场景解锁）可以导入：
游戏卡片「更多 → 导入原版进度」，支持 DCPC／DC2（2006）／DC2PC／DC2DM／DC3DD／DC3R。
原版剧情槽位目前只有 DC3R 能转换，其余版本只合并系统进度。

## 已知限制

- 在桌面 JVM 和 Android 模拟器上验证；实体 ARM 手机和扬声器听感尚未验证。
- 部分演出时序、字体细节和系统设置副作用还没逐项对照原版。
- 汉化版未翻译的日文页会显示乱码（例如 D.C.II 的 `kaiso_01`／`kaiso_02`）。
  这是补丁把字形表改成 GBK 顺序造成的，在原版补丁下同样如此，不是这里的额外损失。
- 缺素材时对应槽位留空并记名，不中断整帧：本机 D.C.II 只有 `scene_sam.CRX`、
  缺 `scene_sam_01..30`，所以场景回想的缩略图是空的。

## 仓库结构

```
runtime/core/       平台无关核心：MES 解析、VM、变量、资源、存档
runtime/desktop/    桌面宿主与 Canvas 渲染
runtime/android/    Android 宿主
runtime/native/     MPEG 解码 JNI
circus_tools/       扫描／解包／反汇编（Python）
profiles/           格式表与语义表，编译期生成 Profiles.java
tools/              构建、打包、验证脚本
docs/               VM 规范、各版本兼容性、版本记录
```

新增作品要加版本绑定和宿主行为，复用资源、变量、跳转和存档基础设施，
不需要按每部作品重做一套 APK。

## 构建与测试

需要 Python 3.10+、JDK 17+；打包 Android 另需上述 SDK／NDK。脚本不下载工具链、不依赖 Gradle。

```powershell
python -m unittest discover -s tests            # 47 项 Python 测试
python tools/build_android.py --java-home ...   # APK，内含 997 项 JVM 断言
python tools/package_desktop.py ...             # Windows 包
```

构建报告和签名校验在 `build/android/`，模拟器证据在 `build/android-smoke/`。

## 文档

- [版本记录](docs/RELEASES.md)｜[解包与静态分析工具](docs/TOOLING.md)
- [PC 兼容性说明](docs/PC_COMPATIBILITY.md)｜[VM 规范](docs/VM_SPECIFICATION.md)｜[架构边界](docs/ARCHITECTURE.md)
- [MES 语义表](docs/MES_SEMANTICS.md)｜[静态分析](docs/STATIC_ANALYSIS.md)

## 边界与许可

游戏输入只读；不发布游戏数据、启动程序、原文脚本或采集副本。
原 EXE 只做静态分析，从不执行。`build/`、`analysis/` 是本机生成物，不在仓库里。
第三方组件来源和许可证见 [THIRD_PARTY.md](THIRD_PARTY.md)。
