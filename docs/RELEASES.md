# 版本记录

本文件收录各版本的变更说明。开发构建产物都在本机 `build/` 下，不随仓库分发；
带 `-dev` 的是开发签名包，可以覆盖安装，不要卸载或清除应用数据。

## Android

按时间倒序。每个版本的范围、验证方式和已知缺口见对应 `ANDROID_*.md`。

| 版本 | 安装包 | 内容 |
|---|---|---|
| 0.13.13 | `build/distribution/circus-runtime-0.13.13-android-dev.apk` | 补齐 W.Y.／P.P. 的 Android 入口，支持 `cn_Data` 中文 MES／CRX、GBK 正文／UI 文本和完整文件长度型 CRX，区分回看贴图；两部 Android 启动、正文、立绘、原版菜单存档／恢复、回看通过。详见 [说明](ANDROID_01313.md)。 |
| 0.13.12 | `build/android/circus-core-debug.apk` | 接入 D.C.III With You（`dc3wy-a09f`）与 D.C.III P.P.（`dc3pp-9872`）：按操作码重编号绑定语义表，场景图面布局（立绘／雾／樱花／擦除遮罩／对话框 UI 面）按版本区分。两部均验证启动、正文、选择、立绘、存读档像素往返。 |
| 0.13.11 | `build/android/circus-core-debug.apk` | 接入 D.C.II 2006 原版：按钮文字（`0x50`）、资源句柄（`0x24`）、闹钟音床的裸 WAV、缺素材降级。见 [0.13.11 说明](ANDROID_01311.md) |
| 0.13.10 | `build/distribution/circus-runtime-0.13.10-android-dev.apk` | DC3R Unity 菜单与存档优化：保存只更新当前槽位，菜单贴图共用文件句柄，缩小暂存预览图。长历史／102 槽保存由约 1.16 秒降至 49 毫秒；原画面与旧存档兼容。见 [说明](ANDROID_01310.md) |
| 0.13.9 | `build/distribution/circus-runtime-0.13.9-android-dev.apk` | 原版进度导入简化为「更多 → 导入原版进度（选文件）」，直接选 `system.dat` 或 DC3R 的 `global.dat`，不要求固定目录或原文件名。见 [说明](ANDROID_0139.md) |
| 0.13.8 | `build/distribution/circus-runtime-0.13.8-android-dev.apk` | DC3R 原版通关、鉴赏进度和剧情存档导入（默认保留手机同号存档）；修复 DC2PC Dacapo 近景立绘定位与顺序；减少 DC3R 背景动画逐帧位图分配。见 [说明](ANDROID_0138.md) |
| 0.13.7 | `build/distribution/circus-runtime-0.13.7-android-dev.apk` | 修复 DC3R 选序章后长时间白屏且无声；资源索引共用只读文件句柄。见 [说明](ANDROID_0137.md) |
| 0.13.6 | `build/distribution/circus-runtime-0.13.6-android-dev.apk` | 修复 DC2PC 选择支普通状态文字不显示，补回 DCPC／DC2PC／DC2DM 的原版文字阴影；四个 MES 版本减少启动脚本读取。见 [说明](ANDROID_0136.md) |
| 0.13.5 | `build/distribution/circus-runtime-0.13.5-android-dev.apk` | 原版系统进度导入扩展到 DCPC、DC2PC、DC2DM、DC3DD，按版本合并通关与鉴赏解锁。见 [说明](ANDROID_0135.md) |
| 0.13.4 | `build/distribution/circus-runtime-0.13.4-android-dev.apk` | 修复 DC2PC Dacapo 线切换长 BGM 时的内存溢出；原始 PCM／Ogg 分块写入播放缓存。见 [说明](ANDROID_0134.md) |
| 0.13.3 | `build/distribution/circus-runtime-0.13.3-android-dev.apk` | DC2PC 原版通关／CG／场景进度导入。见 [说明](ANDROID_0133.md) |
| 0.13.2 | `build/distribution/circus-runtime-0.13.2-android-dev.apk` | 修复 DC2PC 打开菜单、存档确认时的截图压缩停顿，保留原存档格式。见 [说明](ANDROID_0132.md) |
| 0.13.1 | `build/distribution/circus-runtime-0.13.1-android-dev.apk` | 双指点按＝鼠标右键，打开或返回原版菜单；单指推进、系统返回键、双指滚动保留。见 [说明](ANDROID_0131.md) |
| 0.13.0 | `build/distribution/circus-runtime-0.13.0-android-dev.apk` | DC3R Unity 初步兼容：直接读 `DC3_Data/StreamingAssets`，用独立的 CircusScr VM 执行原版标题、正文和系统脚本。见 [说明](ANDROID_0130.md) |
| 0.12.6 | `build/distribution/circus-runtime-0.12.6-android-dev.apk` | 影片音频与视频解码／绘制分离，修复画面卡顿导致的音频断续和 MP2 归一化失真。见 [说明](ANDROID_0126.md) |
| 0.12.5 | — | CG／Scene Jump 进度修复与旧存档恢复。见 [说明](ANDROID_0125.md) |
| 0.12.4 | — | 屏幕刷新调度。见 [说明](ANDROID_0124.md) |
| 0.12.3 | — | 遮罩转场优化。见 [说明](ANDROID_0123.md) |
| 0.12.2 | — | DC2DM 近景附属图修复。见 [说明](ANDROID_0122.md) |
| 0.12.1 | — | 快进菜单修复。见 [说明](ANDROID_0121.md) |
| 0.12 | — | 最近游戏列表、独立 EXE 数据选择、DD 再次启动、DM 片头路径。见 [说明](ANDROID_012.md) |

## PC

| 版本 | 内容 |
|---|---|
| 0.13.12-pc-dev | 与 Android 0.13.12 同核心，含 D.C.III With You 与 D.C.III P.P. 接入。产物 `build/distribution/dc-runtime-0.13.12-pc-dev-windows-x64/` |
| 0.13.11-pc-dev | 与 Android 0.13.11 同核心，含 D.C.II 2006 原版接入。产物 `build/distribution/dc-runtime-0.13.11-pc-dev-windows-x64/` |
| 0.11.0-pc-dev | 自包含 Windows x64 解释器测试包，双击选游戏目录，自带 JRE 与 FFmpeg。DCPC、DC2PC、DC2DM、DC3DD 四部均验证原版启动、正文、选择、存读档和到达结尾；0.10 的 48 次路线策略测试通过，0.11 另跑四部各一条完整路线，含实际音视频输出检查。修复连续系统截图丢失底图，并保存冻结的控件、颜色和动画状态（四部均通过嵌套截图、40 次重复捕获及存档后的逐像素比较）。见 [PC 兼容性说明](PC_COMPATIBILITY.md) |
| 0.10 | 补入 PC1 原版姓名编辑、存档缩略图／角色图标、音乐位置续播、逐字显示及显示进度存档、角色语音设置、原版滑条、回看预览与滚动条拖动；保留 0.9 的原脚本鉴赏列表。325 项 JVM 断言、47 项 Python 测试通过。见 [PC 兼容性说明](PC_COMPATIBILITY.md) |
| 0.6.0-core-dev | DC2DM 原版启动、正文、存读档，加入回看／语音重播／回退、CG／场景／音乐鉴赏和原版操作说明。见 [DC2DM 使用及验证说明](DC2DM_PLAYBACK.md) |
| 0.4.1 | DC3DD 原版 `START → WIN_SIZE → MAIN`、角色介绍和部分原版菜单接入。见 [DC3DD 原版系统进度](DC3DD_ORIGINAL_UI.md) |
| 0.3 | DC3DD 原版数据 Android 剧情播放器：七条路线、选择分支、正文与头像、场景演出、BGM／语音／音效、MPEG 影片、片尾、存档和后台恢复。见 [DC3DD_PLAYBACK.md](DC3DD_PLAYBACK.md) |
| 0.2 | 80 项 Java 断言在桌面 JVM 和 Android 15 ART 通过；验证外置字符映射和不支持的 tunnel 配置拒绝。Android UI 测试涵盖正文显式等待、隐式显示完成、存档字节一致、进程结束后读档。 |

### 0.2 的正文覆盖率审计（历史基线）

DC3DD 全量只读文本审计：214 份 MES，20,903 条姓名、31,397 条正文，51,852 条通过当时的纯文本子集；
448 条因尚未支持的 `@b`、`@t`、`@muf`、`@se` 控制停止。1,154 对外置汉化映射影响 29,620 条记录，CP932 解码失败为 0。
这是文本记录覆盖率，不是可玩率或原作宿主语义完成率。

```powershell
python tools/audit_dialogue.py D:\gal\DC3DD --java-home "E:\DevEco Studio\jbr"
```

审计报告在 `build/dialogue-audit/summary.json`，215 个输入文件的前后哈希一致。
该目录包含从本地配置生成的字符表，不进入源码包或 APK。
