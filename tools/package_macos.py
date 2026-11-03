"""Build a self-contained arm64 macOS app, with no game data.

One command: python3 tools/package_macos.py
The first run downloads Temurin and compiles FFmpeg into build/macos-tools/; later runs reuse them.
"""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess

from build_desktop import build, ROOT

APP_NAME = 'DC Runtime'
# Homebrew's JDK links /opt/homebrew freetype and harfbuzz, so its JRE would not be self-contained.
JDK = ('jdk-21.0.13+9',
       'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz',
       'bf036b35b071bfb2f54f2c0af8ee8d88afa95ee6eb3dce02c0f94cfc2524d04a')
FFMPEG = ('FFmpeg-n8.1.1',
          'https://github.com/FFmpeg/FFmpeg/archive/refs/tags/n8.1.1.tar.gz',
          '918929faccab2700c9cc7be2531c0b74f054a4dd5d3862bc485b0ab17af76486')
# No external libraries and no encoders beyond the two raw formats DesktopMedia reads from its pipes.
FFMPEG_CONFIGURE = ['--disable-autodetect', '--disable-doc', '--disable-network', '--disable-ffplay', '--disable-ffprobe',
                    '--disable-avdevice', '--disable-devices', '--disable-encoders', '--enable-encoder=rawvideo,pcm_s16le',
                    '--disable-muxers', '--enable-muxer=rawvideo,pcm_s16le']
TOOLS = ROOT / 'build/macos-tools'
SYSTEM_LIBRARIES = ('/usr/lib/', '/System/Library/', '@rpath/', '@loader_path/', '@executable_path/')


def run(*command, **options):
    subprocess.run([str(c) for c in command], check=True, **options)


def unpack(name, url, sha256):
    target = TOOLS / name
    if target.exists():
        return target
    TOOLS.mkdir(parents=True, exist_ok=True)
    archive = TOOLS / (name + '.tar.gz')
    if not archive.exists():
        partial = archive.with_suffix('.part')
        run('curl', '-fL', '--retry', '3', '-o', partial, url)
        actual = subprocess.run(['shasum', '-a', '256', str(partial)], capture_output=True, text=True, check=True).stdout.split()[0]
        if actual != sha256:
            partial.unlink()
            raise SystemExit(f'{url}: expected SHA-256 {sha256}, got {actual}')
        partial.rename(archive)
    run('tar', '-xzf', archive, '-C', TOOLS)
    return target


def build_ffmpeg():
    source = unpack(*FFMPEG)
    binary = source / 'ffmpeg'
    if not binary.exists():
        # Without this, FFmpeg takes its version from the enclosing git repository.
        (source / 'VERSION').write_text(FFMPEG[0].split('-')[1], encoding='utf-8')
        run('./configure', *FFMPEG_CONFIGURE, cwd=source)
        run('make', f'-j{os.cpu_count()}', 'ffmpeg', cwd=source)
    return binary


def foreign_libraries(path):
    """Linked libraries that a Mac without Homebrew or a JDK would not have."""
    lines = subprocess.run(['otool', '-L', str(path)], capture_output=True, text=True, check=True).stdout.splitlines()[1:]
    return [line.split()[0] for line in lines if not line.strip().startswith(SYSTEM_LIBRARIES)]


def is_macho(path):
    with path.open('rb') as f:
        return f.read(4) in {b'\xcf\xfa\xed\xfe', b'\xca\xfe\xba\xbe'}


def check_self_contained(app):
    problems = []
    for path in app.rglob('*'):
        if path.is_file() and not path.is_symlink() and is_macho(path):
            archs = subprocess.run(['lipo', '-archs', str(path)], capture_output=True, text=True, check=True).stdout.split()
            if 'arm64' not in archs:
                problems.append(f'{path}: {archs}')
            problems += [f'{path}: {library}' for library in foreign_libraries(path)]
    if problems:
        raise SystemExit('App is not self-contained:\n' + '\n'.join(problems))


def make_icon(work):
    logo = ROOT / 'runtime/android/res/drawable-nodpi/launcher_logo.png'
    iconset = work / 'app.iconset'
    iconset.mkdir()
    for size in [16, 32, 128, 256, 512]:
        for scale, suffix in [(1, ''), (2, '@2x')]:
            run('sips', '-z', size * scale, size * scale, logo, '--out', iconset / f'icon_{size}x{size}{suffix}.png',
                stdout=subprocess.DEVNULL)
    run('iconutil', '-c', 'icns', iconset, '-o', work / 'app.icns')
    return work / 'app.icns'


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jdk', type=Path, help='JDK 21 home; defaults to a downloaded Temurin')
    parser.add_argument('--ffmpeg', type=Path, help='self-contained arm64 ffmpeg; defaults to one compiled from source')
    parser.add_argument('--version', default='1.0.0', help='CFBundleVersion; the first number must be at least 1')
    args = parser.parse_args()
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('package_macos.py needs an Apple Silicon Mac')

    jdk = args.jdk or unpack(*JDK) / 'Contents/Home'
    ffmpeg = args.ffmpeg or build_ffmpeg()
    if foreign_libraries(ffmpeg):
        raise SystemExit(f'{ffmpeg} links {foreign_libraries(ffmpeg)}; use a static build')
    _, classes = build(jdk)

    dist = ROOT / 'build/distribution'
    dist.mkdir(parents=True, exist_ok=True)
    app = dist / (APP_NAME + '.app')
    work = ROOT / 'build/macos-package'
    for stale in [app, work]:
        shutil.rmtree(stale, ignore_errors=True)
    (work / 'input').mkdir(parents=True)

    icon = ROOT / 'runtime/android/res/drawable-nodpi/launcher_logo.png'
    run(jdk / 'bin/jar', '--create', '--file', work / 'input/dc-runtime.jar', '--main-class', 'org.circus.core.DesktopRuntime',
        '-C', classes, '.', '-C', icon.parent, icon.name)
    run(jdk / 'bin/jlink', '--add-modules', 'java.desktop,jdk.charsets', '--strip-debug', '--no-header-files', '--no-man-pages',
        '--compress=zip-6', '--output', work / 'jre')
    check_self_contained(work / 'jre')
    run(jdk / 'bin/jpackage', '--type', 'app-image', '--name', APP_NAME, '--dest', dist, '--input', work / 'input',
        '--main-jar', 'dc-runtime.jar', '--runtime-image', work / 'jre', '--app-version', args.version,
        '--icon', make_icon(work), '--mac-package-identifier', 'com.dreaife.dc-runtime',
        '--java-options', '-Xmx1536m', '--java-options', '-Dfile.encoding=UTF-8',
        '--java-options', '-Ddc.ffmpeg=$APPDIR/../Helpers/ffmpeg')

    contents = app / 'Contents'
    (contents / 'Helpers').mkdir()
    shutil.copy2(ffmpeg, contents / 'Helpers/ffmpeg')
    for name in ['LICENSE', 'THIRD_PARTY.md']:
        shutil.copy2(ROOT / name, contents / 'Resources' / name)
    shutil.copy2(jdk / 'NOTICE', contents / 'Resources/JRE-NOTICE')
    ffmpeg_version = subprocess.run([str(ffmpeg), '-version'], capture_output=True, text=True, check=True).stdout
    (contents / 'Resources/FFMPEG-BUILD.txt').write_text(ffmpeg_version, encoding='utf-8')

    check_self_contained(app)
    run('codesign', '--force', '--deep', '--sign', '-', app)
    run('codesign', '--verify', '--deep', '--strict', app)
    size = sum(p.stat().st_size for p in app.rglob('*') if p.is_file() and not p.is_symlink())
    print(json.dumps({'app': str(app), 'bytes': size, 'open': f'open "{app}"'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
