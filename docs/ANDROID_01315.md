# Android 0.13.15：W.Y. 原生资源路径修复

覆盖安装 `build/distribution/circus-runtime-0.13.15-android-dev.apk`，可以读取之前的解释器存档，原版进度导入保持可用。

用户报错：`WY_2_1226_A1_SRR @0xE24C: Invalid resource path segment`。附近台词为「听到我这么说，夏露露松了一口气，然后回头看向我。」

## 原因与改动

实际图片请求来自同一脚本 `0xE1A4` 的 `image.load [2]`：

```text
BST\\SRR_0F1_013_B,BST\SRR_F1
```

第一段有连续两个反斜杠，Windows 将它们视作一个目录分隔符。旧解释器将其替换成两个正斜杠后，因空目录段而拒绝。图片先被排入场景队列，随后绘制时才读取，所以界面显示的 VM 位置比实际图片指令靠后。

现在原生图片、归档引用、音频（含分块导出）及 MES 影片路径先合并内部重复分隔符，再检查相对路径。严格的文件清单和 SAF 文档路径校验没有改动；绝对路径、UNC、盘符、NUL、`.`、`..`、过长路径和末尾目录分隔符仍拒绝。

原脚本、游戏资源和存档格式不变；旧存档中保留的原始图片引用也在读取时兼容。不会跳过图片或将未知指令当作无效果处理。

## 验证

- 使用旧版资源类重现同一个路径错误。
- W.Y. 中文原脚本 SHA-256：`8dacd426bd075aea00b385a0213aac6cc8c158c1130455c1dc55a34f307f6d2a`。从原版 START 初始化界面后，进入该章节入口，推进 528 条对白，覆盖用户台词和故障处之后 12 次对白等待。桌面及 Android 正式 `RuntimeActivity`／Canvas 路径通过。
- 报错立绘与单分隔符引用的尺寸、偏移和全部像素一致。跨故障立绘的存读档保留台词、PC 和渲染像素。
- 扫描两部的有效原版／中文图片载入指令：W.Y. 共 1 处，P.P. 共 20 处（13 个不同引用）。全部与正常引用逐像素一致。
- 1096 项 JVM／Android ART 核心断言、Canvas 7 项及遮罩 484 项通过。新增混合／重复分隔符、CRXD 像素、普通与分块音频一致性，以及非法路径拒绝测试。

测试推进脚本时间并模拟音频／影片完成；章节入口使用标题初始化后的测试状态，不等于整条路线或实体手机帧率验收。桌面范围包录制需要额外保留资源字节，使用较大的测试堆；Android 实测仍使用既有的 256 MiB Java 堆配置。

本地证据在 `build/wy-path-01315/`：`baseline.log`、`fixed/result.txt`、`android-path.txt`、两部 `*-corpus.txt` 与 `android-core.txt`。`WyResourcePathAudit` 可复现章节；`Dc3FamilyInstrumentation -e pathRegression true` 运行 Android 检查。所有写入位于测试目录，原游戏保持只读，范围包和游戏画面不上传 GitHub。
