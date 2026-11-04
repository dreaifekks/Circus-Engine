"""Build a self-contained arm64 macOS app, with no game data.

  python3 tools/package_macos.py                     a general app that asks for a game directory
  python3 tools/package_macos.py --game DIR          an app for that game, with its name and icon
  python3 tools/package_macos.py --game DIR --sign "Apple Development: You (TEAM)" --install

The app is drag and drop: it can live anywhere, /Applications included. It points at the game directory and holds none
of the game. The first run downloads Temurin and compiles FFmpeg into build/macos-tools/; later runs reuse them.
"""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile

from build_desktop import build, ROOT
from game_identity import describe

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
    # otool reads "name(member)" as a member of a static library, so a game name with brackets needs another path.
    with tempfile.TemporaryDirectory() as folder:
        link = Path(folder) / 'binary'
        link.symlink_to(Path(path).resolve())
        lines = subprocess.run(['otool', '-L', str(link)], capture_output=True, text=True, check=True).stdout.splitlines()[1:]
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


def image_width(path):
    lines = subprocess.run(['sips', '-g', 'pixelWidth', str(path)], capture_output=True, text=True, check=True).stdout
    return int(lines.split()[-1])


def make_icon(work, source):
    """An icns from any image sips can read. Sizes above the source are left for macOS to scale."""
    if source.suffix.lower() == '.icns':
        return source
    png = work / 'icon-source.png'
    run('sips', '-s', 'format', 'png', source, '--out', png, stdout=subprocess.DEVNULL)
    width = image_width(png)
    iconset = work / 'app.iconset'
    iconset.mkdir()
    for size in [16, 32, 128, 256, 512]:
        for scale, suffix in [(1, ''), (2, '@2x')]:
            if size * scale <= width:
                run('sips', '-z', size * scale, size * scale, png, '--out', iconset / f'icon_{size}x{size}{suffix}.png',
                    stdout=subprocess.DEVNULL)
    run('iconutil', '-c', 'icns', iconset, '-o', work / 'app.icns')
    return work / 'app.icns'


def game_directory(path):
    game = path.expanduser().resolve()
    if not game.is_dir():
        raise SystemExit(f'{game}: not a directory; is the disk connected?')
    if not any(p.name.lower() == 'advdata' or p.name.endswith('_Data') for p in game.iterdir() if p.is_dir()):
        raise SystemExit(f'{game}: no AdvData or *_Data directory, so this is not a game directory')
    return game


def install(app, target):
    target.mkdir(parents=True, exist_ok=True)
    destination = target / app.name
    if destination.exists():
        shutil.rmtree(destination)
    run('ditto', app, destination)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jdk', type=Path, help='JDK 21 home; defaults to a downloaded Temurin')
    parser.add_argument('--ffmpeg', type=Path, help='self-contained arm64 ffmpeg; defaults to one compiled from source')
    parser.add_argument('--version', default='1.0.0', help='CFBundleVersion; the first number must be at least 1')
    parser.add_argument('--game', type=Path, help='game directory the app opens; also supplies its name and icon')
    parser.add_argument('--name', help='app name; defaults to the name of the game')
    parser.add_argument('--icon', type=Path, help='icon image or .icns; defaults to the icon in the game\'s exe')
    parser.add_argument('--sign', default='-', metavar='IDENTITY',
                        help='codesign identity, from: security find-identity -v -p codesigning; default ad hoc')
    parser.add_argument('--install', nargs='?', const=Path('/Applications'), type=Path, metavar='DIR',
                        help='also copy the app there, replacing the same name; DIR defaults to /Applications')
    args = parser.parse_args()
    if platform.system() != 'Darwin' or platform.machine() != 'arm64':
        raise SystemExit('package_macos.py needs an Apple Silicon Mac')

    game = game_directory(args.game) if args.game else None
    identity = describe(game) if game else None
    app_name = args.name or (identity.name if identity else APP_NAME)
    if '/' in app_name or ':' in app_name:
        raise SystemExit(f'{app_name}: an app name cannot hold / or :')

    jdk = args.jdk or unpack(*JDK) / 'Contents/Home'
    ffmpeg = args.ffmpeg or build_ffmpeg()
    if foreign_libraries(ffmpeg):
        raise SystemExit(f'{ffmpeg} links {foreign_libraries(ffmpeg)}; use a static build')
    _, classes = build(jdk)

    dist = ROOT / 'build/distribution'
    dist.mkdir(parents=True, exist_ok=True)
    app = dist / (app_name + '.app')
    work = ROOT / 'build/macos-package'
    for stale in [app, work]:
        shutil.rmtree(stale, ignore_errors=True)
    (work / 'input').mkdir(parents=True)

    icon = ROOT / 'runtime/android/res/drawable-nodpi/launcher_logo.png'
    icon_source = args.icon
    if icon_source is None and identity and identity.icon:
        icon_source = work / 'game.ico'
        icon_source.write_bytes(identity.icon)
    if icon_source is None and game:
        print(f'{game}: no icon found in an exe, using the default one')
    run(jdk / 'bin/jar', '--create', '--file', work / 'input/dc-runtime.jar', '--main-class', 'org.circus.core.DesktopRuntime',
        '-C', classes, '.', '-C', icon.parent, icon.name)
    run(jdk / 'bin/jlink', '--add-modules', 'java.desktop,jdk.charsets', '--strip-debug', '--no-header-files', '--no-man-pages',
        '--compress=zip-6', '--output', work / 'jre')
    check_self_contained(work / 'jre')
    # One identifier per game, so that the system keeps each app's permissions and windows apart.
    identifier = 'com.dreaife.dc-runtime' + (f'.{identity.slug}' if identity else '')
    run(jdk / 'bin/jpackage', '--type', 'app-image', '--name', app_name, '--dest', dist, '--input', work / 'input',
        '--main-jar', 'dc-runtime.jar', '--runtime-image', work / 'jre', '--app-version', args.version,
        '--icon', make_icon(work, icon_source or icon), '--mac-package-identifier', identifier,
        '--java-options', '-Xmx1536m', '--java-options', '-Dfile.encoding=UTF-8',
        '--java-options', '-Ddc.ffmpeg=$APPDIR/../Helpers/ffmpeg', '--java-options', '-Ddc.app=$APPDIR/dc-app.txt')

    contents = app / 'Contents'
    # Read by DesktopMac; a file because jpackage would split a path with spaces in --java-options.
    (contents / 'app/dc-app.txt').write_text(f'{app_name}\n{game or ""}\n', encoding='utf-8')
    (contents / 'Helpers').mkdir()
    shutil.copy2(ffmpeg, contents / 'Helpers/ffmpeg')
    for name in ['LICENSE', 'THIRD_PARTY.md']:
        shutil.copy2(ROOT / name, contents / 'Resources' / name)
    shutil.copy2(jdk / 'NOTICE', contents / 'Resources/JRE-NOTICE')
    ffmpeg_version = subprocess.run([str(ffmpeg), '-version'], capture_output=True, text=True, check=True).stdout
    (contents / 'Resources/FFMPEG-BUILD.txt').write_text(ffmpeg_version, encoding='utf-8')

    check_self_contained(app)
    run('codesign', '--force', '--deep', '--sign', args.sign, app)
    run('codesign', '--verify', '--deep', '--strict', app)
    size = sum(p.stat().st_size for p in app.rglob('*') if p.is_file() and not p.is_symlink())
    installed = install(app, args.install) if args.install else None
    final = installed or app
    print(json.dumps({'app': str(final), 'name': app_name, 'game': str(game) if game else None, 'identifier': identifier,
                      'signed_by': args.sign if args.sign != '-' else 'ad hoc', 'bytes': size, 'open': f'open "{final}"'},
                     ensure_ascii=False))


if __name__ == '__main__':
    main()
