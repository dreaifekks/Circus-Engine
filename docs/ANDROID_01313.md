# Android 0.13.13：D.C.III W.Y.／P.P.

本次补齐 `dc3wy-a09f` 与 `dc3pp-9872` 的 Android 接入，以及这两份本地汉化补丁的资源读取。APK 为 `build/distribution/circus-runtime-0.13.13-android-dev.apk`，可覆盖安装，使用原来的开发签名。

## 使用

将整部游戏复制到手机，保留 `AdvData`、`cn_Data` 和启动文件。选择同时含有这些文件夹的游戏根目录：

- W.Y.：`D.C.III With You/D.C.III With You`；EXE 数据文件选择 `DC3WY_CN.exe`。
- P.P.：`D.C.III.P.P【无密码】/D.C.III.P.P(初音岛3PP)`；EXE 数据文件选择 `DC3PP_CN_ruby.exe`。

文字编码选择「自动识别已验证版本」。本地已验证的补丁按脚本内容 SHA-256 识别 GBK，不靠文件夹或 EXE 名猜测。其他汉化版本若指纹不同，可手动选择 GBK；其脚本格式仍须符合对应的 MES 版本。EXE 只读取数据，不在手机上执行 DLL 或 Windows 代码。

新接入的 W.Y.／P.P. 可以使用解释器自己的存读档；这两部原 Windows 系统进度／剧情槽位的导入尚未实现。

## 改动

此前桌面入口已接入两部的操作码表及场景槽布局，Android 正式入口却仍只允许 D.D.。现在两个平台统一使用 `Dc3Host.supports` 判断这一组版本。

汉化补丁将 MES 与 CRX 单独放在 `cn_Data`。运行时优先读取同名的松散补丁文件，不存在时使用原版 `AdvData`，坏补丁会报告错误。GBK 解码应用到正文、姓名宏、选择与 UI 文字；资源名和脚本跳转仍使用原本的编码规则。字符替换只执行一次。

补丁 CRX 的长度字段有的记录完整文件长度，有的记录压缩数据长度；现在同时识别，实际像素预测、zlib 完整性及截断检查继续执行。W.Y.／P.P. 不再尝试读取 D.D. EXE 固定地址的回看头像表。回看使用各自图集的名称及裁切范围，较早图集不使用 D.D. 特有的预览控件。

## 验证与边界

- 本地 W.Y. 的 209 份补丁 MES、33 张 CRX，以及 P.P. 的 317 份 MES、28 张 CRX 均完成结构解析或图片解码。
- 两部均通过桌面和 Android 35 模拟器检查：从原版标题进入中文正文、显示立绘、音频资源解码、保存前后像素一致、右键菜单、原版存档页实际写槽、恢复剧情调用者、回看及返回。
- W.Y. 推进 36 条对白、P.P. 推进 24 条对白后进行菜单与保存检查；测试脚本推进时间并模拟音频／影片完成。Android 使用实际 `RuntimeActivity`、Canvas 和应用私有存档，游戏资源通过精确范围包回放。它不是实体 ARM 手机的声音／帧率验收。
- 1001 项 JVM／Android ART 核心断言，以及 Canvas 与转场遮罩检查通过；新增 GBK 正文、姓名宏、一次字符替换、CRX 完整长度和截断回归。

全路线、全部鉴赏内容、影片字幕与替换影片、回看头像映射及界面精确位置／演出时序尚未逐项验收。Windows P.P. 汉化程序在隔离运行时停在无响应的启动提示，未据此声称完成原版动态画面对照。

证据位于本机 `build/wy-pp-android/`：两部桌面 `result.txt`、`android-wy.txt`、`android-pp.txt`、`android-core.txt` 和截图。`Dc3FamilyAudit` 可记录资源范围包，`Dc3FamilyInstrumentation` 通过正式 Android 入口重放并验证。原版游戏与 PC 存档保持只读，测试只写入隔离目录，游戏数据不进入 APK、源码包或 GitHub。
