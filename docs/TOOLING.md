# 解包与静态分析工具

运行时只读游戏目录，不需要事先解包。下面这些工具用于离线检查、导出和取证，
产物一律写进本机 `build/` 或 `analysis/`，都不进版本控制。

## 用法

核心工具只依赖 Python 3.10+ 标准库，在项目目录执行：

```powershell
python -m unittest discover -s tests -v
python -m circus_tools scan D:\gal\DC3DD --out analysis\dc3dd --extract
python -m circus_tools scan D:\gal\DC2DM --out analysis\dc2dm --extract
python -m circus_tools verify analysis\dc3dd
python -m circus_tools names D:\gal\DC3DD\AdvData\GRP\NAMES.DAT --out analysis\dc3dd\names
python -m circus_tools mes D:\gal\DC3DD\AdvData\MES\main.mes --profile dc3dd-a5a8 --out analysis\dc3dd\disassembly\main
python -m circus_tools semantics D:\gal\DC3DD\AdvData\MES --profile dc3dd-a5a8 --out analysis\semantics\dc3dd
python tools/build_report.py
python tools/capture_mes_evidence.py D:\gal\DC3DD\DC3DD.EXE --profile dc3dd-a5a8 --out analysis\evidence\dc3dd
```

只导出单部游戏或单个封包：

```powershell
.\dc_unpack.cmd "D:\gal\D.C.II P.C" build\unpack\my-dc2pc
.\dc_unpack.cmd "D:\gal\DC3DD\AdvData\VOICE.DAT" build\unpack\my-voice
python dc_unpack.py "D:\gal\DC2DM" build\unpack\my-dc2dm
python tools/verify_unpacked.py build\unpack\my-dcpc
```

`--raw-only` 只提取原始资源。输出目录必须为空且不能位于原游戏目录中，工具不覆盖已有解包结果。
单个封包只能提取包内条目；完整图片、音频和脚本需要选整部游戏目录。DC2PC 的 `VOICE2.DAT`
必须与 `VOICE.DAT` 相邻。

`scan` 不加 `--extract` 时只读取、解析和记录哈希。不要用不同选项覆盖同一个已完成的扫描目录：
重扫会重建清单，已有解包文件会保留。需要不同扫描模式时选择新的输出目录。

## 输出

每个扫描目录包含：

| 文件／目录 | 用途 |
|---|---|
| `summary.json` | 统计、错误和分析边界 |
| `inventory.json` | 原文件大小与 SHA-256；排除存档与备份目录 |
| `archives.json` | 封包类型、索引槽与条目统计 |
| `archive-entries.jsonl` | 所有条目的名称、偏移、长度、哈希、资源头信息 |
| `unpacked/` | 解出的原始载荷，不自动转换或播放 |
| `scripts.json` | 每个 MES 的版本和指令分布 |
| `candidate-script-references.json` | 字符串与脚本名的匹配，尚不是已证明的控制流 |
| `crm-links.json` | 差分图像的封包内偏移依赖 |
| `verification.json` | 对原文件和输出文件重新计算哈希后的验证结果 |
| `names/names.json` | 单独解析的资源名目录，含未知字段原始值 |
| `disassembly/` | 选定脚本的结构反汇编，保留原始字节而不解释执行含义 |

整部游戏解包的目录结构：

```text
output/
  bg/ character/ face/ system/ image/    PNG，保留原图像素
  cg/                                  按 NAMES 原版图片索引建立的鉴赏视图
  voice/ bgm/ se/                       可播放 WAV 或原始 Ogg 码流
  script/                              原 MES + 物理指令 JSONL
  movie/ other/                        未改变的媒体与其他数据
  raw/                                 每个条目的原始字节，包括压缩格式
  catalog/names.json                    原版五张索引表及未知字段
  catalog/gallery.json                  原版图片索引到 PNG 的对应关系
  manifest.jsonl                       条目名、原始名字节、源偏移、大小、哈希、导出位置
  sources.jsonl                        原始输入文件完整哈希
  summary.json                         原样提取与格式转换分别统计
  verification.json                    独立重验结果（运行 verify 后生成）
```

文件名加序号并编码，避免归档中的同名、大小写冲突和 `../` 路径；原始名和名字节保存在 manifest。
CRXD／CRXJ 在原 CRM 上下文中合成，归档内别名仍各自保留。PNG 的原生 x/y 原点在 manifest，
不强行把所有立绘裁成相同尺寸；24 位图片的洋红色透明键保留，运行时绘制时再应用。
CG 不是按画面内容猜测，而是由 NAMES 图片表建立独立索引视图；源目录分类仍保留。

`raw_complete` 表示原始条目无损提取，`complete` 表示当前转换过程无错误，二者不同。
MES 的字节记录与人类文本编码也分别处理：无法确定编码时保留 `native_string_hex`、`text=null`，
不替换或伪造原文。

## 已有能力

- 28 份 MES 格式配置、30 个版本号；offset1／offset2 两种头结构和五种参数布局的严格解析。
- 整套脚本版本交集识别，避免标签区字节碰巧匹配其他版本号。
- PCK（包括本地两作 `VOICE.DAT`）和 CRM 的索引读取、原样提取与 SHA-256 验证。
- CRM 空索引槽、共享偏移、CRXD／CRXJ 依赖的保留与检查。
- Python 的 CRX／CRXD／XPCM 元数据检查；Java 核心另有实际解码实现。
- DC2DM／DC3DD 的 `NAMES.DAT` 五张资源名表读取，保留未知数值字段。
- 文件清单、逐脚本指令统计、候选脚本引用、汉化字符映射摘要。
- 可选 PE 静态工具（依赖 `pefile`），读取头部、节区和导入表。
- 带原生证据的 MES 语义映射（DC3DD 19 项、DC3WY 16 项），区分完整子规则与 partial 宿主行为。
- 复合参数、条件 companion、求和列表、UI 附属字符串识别与潜在控制流边检查。
- SHA-256 锁定的 x86 静态证据导出工具（`pefile` + `capstone`），从不加载或运行 EXE。

## 相关文档

第一轮拆包报告见 [STATIC_ANALYSIS.md](STATIC_ANALYSIS.md)，语义表与复现方式见
[MES_SEMANTICS.md](MES_SEMANTICS.md)，VM 规范见 [VM_SPECIFICATION.md](VM_SPECIFICATION.md)，
架构边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。工具来源和许可证见 [THIRD_PARTY.md](../THIRD_PARTY.md)。
