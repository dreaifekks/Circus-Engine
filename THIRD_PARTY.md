# Sources and attribution

This repository contains static analysis tools, a portable Java core, and an
Android development host. It is distributed under GPL-3.0-only; the
full license is in `LICENSE`. Game data is not part of the source distribution.

## MesTextTool

- Author/project: cokkeijigen and MesTextTool contributors.
- License: GNU GPL version 3, see the upstream LICENSE.
- https://github.com/cokkeijigen/MesTextTool
- Format catalog: `MesTextTool/src/mes/script_info.cpp`.
- Header/framing reference: `MesTextTool/src/mes/script_view.cpp`.
- `profiles/dc123.json` is generated from the local source by
  `tools/import_profiles.py`; its source SHA-256 is recorded in the catalog.
- Only DC1/2/3 and related fandisc entries are imported. The framing table does
  not establish opcode execution semantics or compatibility with a full game.

## GARbro

Archive readers, media metadata readers, and Java CRX/CRXD/XPCM decoding algorithms
were implemented or adapted with reference to:

- https://github.com/morkt/GARbro/blob/master/ArcFormats/Circus/ArcPCK.cs
- https://github.com/morkt/GARbro/blob/master/ArcFormats/Circus/ArcCRM.cs
- https://github.com/morkt/GARbro/blob/master/ArcFormats/Circus/ImageCRX.cs
- https://github.com/morkt/GARbro/blob/master/ArcFormats/Circus/ImageCRXD.cs
- https://github.com/morkt/GARbro/blob/master/ArcFormats/Circus/AudioPCM.cs

`XpcmTransform.java` ports the integer FFT and overlap/add transform in AudioPCM.cs, including mode 1/3 decoding. Synthetic nonzero vectors were compared byte for byte against the independent C# implementation. The full MIT notice is also retained in that source file.

The following notice is retained for the referenced/adapted format algorithms:

Copyright (C) 2015-2016 by morkt

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to
deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.

## PL_MPEG

The vendored `runtime/native/third_party/stb_vorbis.c` decodes Ogg Vorbis in the iPad
host. Source: https://github.com/nothings/stb, v1.22, by Sean Barrett, retrieved
2026-11-05. It offers MIT or public domain terms and is used here under either.

The iPad host is compiled by MobiVM (https://github.com/MobiVM/robovm), fetched by
Maven at build time and not vendored. Its compiler is GPL-2.0, its runtime Apache-2.0.

The vendored `runtime/native/third_party/pl_mpeg.h` provides MPEG-1 video and
MPEG Layer II audio decoding. Source: https://github.com/phoboslab/pl_mpeg,
upstream Git blob `b42f53f89f868a697435ab1e15742f370819ffbc`, retrieved 2026-10-26.
The header identifies its author as Dominic Szablewski (https://phoboslab.org)
and declares `SPDX-License-Identifier: MIT`.

Local audio corrections (2026-10-28): dequantize using the number of quantizer
levels instead of levels + 1, and normalize the synthesis output by 2^30.
The original constants produced subband-dependent attenuation and an extra
64/65 output gain. See `docs/ANDROID_0126.md` for independent PCM comparisons.

Copyright (c) Dominic Szablewski

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Other evaluated projects (references only)

- https://github.com/Atho64/arisa32 — MIT; DC4-specific runtime, useful as a
  research reference but not imported into this DC1/2/3 core project.
- https://github.com/lifegpc/msg-tool — optional independent format cross-check.
- https://github.com/xmoezzz/ethornell-rs and https://github.com/xmoezzz/rfvp —
  different engines; architecture references only, no runtime code imported.

## Build and development tools

- Android SDK / D8 / AAPT / APK signer: existing local installation, not redistributed.
  https://developer.android.com/build/building-cmdline
  https://developer.android.com/tools/d8
- Android Storage Access Framework:
  https://developer.android.com/training/data-storage/shared/documents-files
- Frida (optional, installed only under ignored build/probe-python): local observation
  of an explicitly authorized isolated process, never embedded in the APK.
  https://frida.re/docs/javascript-api/

The launcher logo in `runtime/android/res/drawable-nodpi/launcher_logo.png` was
supplied by the user on 2026-10-26 for use as this APK's icon. It is retained
unchanged (SHA-256 `9eeac6a9b61ac25eb3635b45f0052b2ef6a5f7b2d38cef337337f2d80a259361`).
It is separate artwork, not covered by the core code's GPL license declaration.

The synthetic assets in runtime/android/assets/demo are generated by
tools/generate_demo.py. Apart from the explicitly supplied launcher artwork,
no game images, dialogue, audio, executable, fonts, or dynamic-probe copy should
be included in source/APK redistribution.

## REA analysis reference (2026-10-27)

REA (`https://github.com/morluto/rea`), MIT, copyright 2026 morluto, is used as a separately installed static analysis tool. Reference checkout: `624ebb2a1e633ce14b6d6828eef50bf7d7db68ac`; executed npm package: `rea-agents@6.8.0`, with project-local Ghidra 12.1.4. No REA source is embedded in the engine or distributed source package. The checkout and its LICENSE remain in `build/references/rea`; provider binaries and their own notices remain under `build/toolchain` and are excluded from distribution.

## PC distribution components

The local Windows PC test package contains a jlink image built from Eclipse Temurin
OpenJDK 21.0.9+10. Its module license files are retained under `jre/legal`, and the
vendor notice is retained as `JRE-NOTICE`. https://adoptium.net/temurin/

FFmpeg is used as a separate command-line decoder process. The local executable is
`7.1-essentials_build-www.gyan.dev`, obtained from the existing imageio-ffmpeg
installation. Its exact configuration and hash are recorded in `FFMPEG-BUILD.txt`
and `BUILD.json`. This build declares GPL version 3 or later; the full GPL text
is included as `LICENSE`. Upstream: https://ffmpeg.org/ and
https://www.gyan.dev/ffmpeg/builds/ . No FFmpeg or JRE files are copied into the
project's source ZIP; the PC packager accepts user-provided toolchain paths.

## Unity FSB5 Vorbis setup headers

`runtime/core/resources/org/circus/core/fsb5-vorbis-headers.bin.gz` contains the
161 codec setup packets from HearthSim/python-fsb5, converted from its Python
dictionary into a compact binary table. These are public codec configuration
headers, not game audio. Original file SHA-256:
`abb2a177ddad75d4c42f8c19ae20cd86be4145761ff40881136a13652dd3aa70`.
Retrieved 2026-10-29 from
https://github.com/HearthSim/python-fsb5/blob/master/fsb5/vorbis_headers.py .
The FSB5 container documentation and reconstruction reference are from the same
project. The runtime Ogg writer and bounded readers are implemented in Java.

The MIT License (MIT)

Copyright (c) 2016 Simon Pinfold

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

UnityFS and Unity serialized-v17 field layouts were cross-checked against
AssetStudio's published format readers (MIT; reference only, source not copied):
https://github.com/Perfare/AssetStudio .
