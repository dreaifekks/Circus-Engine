"""Build the iPad app for one Apple developer team, with no game data, and optionally install it.

  python3 tools/package_ios.py --team TEAMID --game DIR --device UDID --install               name and icon from the game
  python3 tools/package_ios.py --team TEAMID --game DIR --device UDID --install --copy-game   also copies the game over

The app runs the Mac runtime: runtime/core and DesktopLegacy are compiled unchanged by MobiVM, with AWT, Java Sound
and Swing replaced by runtime/ios. Needs Xcode, Maven, and a device registered with the team. See docs/IOS.md.
"""
import argparse
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess
import tempfile

from build_desktop import ROOT
from game_identity import describe
from package_macos import JDK, unpack

WORK = ROOT / 'build/ios'
IOS = ROOT / 'runtime/ios'
DESKTOP = ROOT / 'runtime/desktop/src/org/circus/core'
# The part of the Mac runtime that plays DC2PC and the games on its engine.
SHARED = ['DesktopLegacy', 'DesktopEngine', 'DesktopCaptures', 'DesktopSave']
SIGNING_PROJECT = '''// !$*UTF8*$!
{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {
A1 = {isa = PBXBuildFile; fileRef = A2; };
A2 = {isa = PBXFileReference; path = main.m; sourceTree = "<group>"; lastKnownFileType = sourcecode.c.objc; };
A3 = {isa = PBXFileReference; explicitFileType = wrapper.application; path = P.app; sourceTree = BUILT_PRODUCTS_DIR; includeInIndex = 0; };
A4 = {isa = PBXGroup; children = (A2, A3); sourceTree = "<group>"; };
A5 = {isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = (A1); runOnlyForDeploymentPostprocessing = 0; };
A6 = {isa = PBXNativeTarget; buildConfigurationList = A9; buildPhases = (A5); buildRules = (); dependencies = (); name = P; productName = P; productReference = A3; productType = "com.apple.product-type.application"; };
A7 = {isa = PBXProject; attributes = {TargetAttributes = {A6 = {ProvisioningStyle = Automatic; }; }; }; buildConfigurationList = B1; compatibilityVersion = "Xcode 14.0"; mainGroup = A4; targets = (A6); };
A8 = {isa = XCBuildConfiguration; name = Debug; buildSettings = {PRODUCT_NAME = P; PRODUCT_BUNDLE_IDENTIFIER = "@BUNDLE@"; INFOPLIST_FILE = Info.plist; SDKROOT = iphoneos; TARGETED_DEVICE_FAMILY = "1,2"; IPHONEOS_DEPLOYMENT_TARGET = 15.0; CODE_SIGN_STYLE = Automatic; DEVELOPMENT_TEAM = "@TEAM@"; CODE_SIGN_IDENTITY = "Apple Development"; }; };
A9 = {isa = XCConfigurationList; buildConfigurations = (A8); defaultConfigurationName = Debug; };
B2 = {isa = XCBuildConfiguration; name = Debug; buildSettings = {}; };
B1 = {isa = XCConfigurationList; buildConfigurations = (B2); defaultConfigurationName = Debug; };
}; rootObject = A7; }
'''


def run(*command, **options):
    subprocess.run([str(c) for c in command], check=True, **options)


def sources():
    """Copy the shared sources, pointing AWT at runtime/ios and the two charset lookups at the tables."""
    for name in ('coresrc', 'macsrc'):
        shutil.rmtree(WORK / name, ignore_errors=True)
    shutil.copytree(ROOT / 'runtime/core/src', WORK / 'coresrc')
    for name, old, new in (('Binary', 'Charset.forName("windows-31j")', 'TableCharset.CP932'),
                           ('TextCodec', 'charset=Charset.forName(encoding)', 'charset=TableCharset.named(encoding)')):
        path = WORK / f'coresrc/org/circus/core/{name}.java'
        text = path.read_text(encoding='utf-8')
        if old not in text:
            raise SystemExit(f'{name}.java no longer has {old}')
        path.write_text(text.replace(old, new), encoding='utf-8')
    out = WORK / 'macsrc/org/circus/core'
    out.mkdir(parents=True)
    for name in SHARED:
        text = (DESKTOP / f'{name}.java').read_text(encoding='utf-8')
        text = re.sub(r'^import (javax\.swing|javax\.sound\.sampled|javax\.imageio|java\.awt)[^;]*;\n', '', text, flags=re.M)
        text = text.replace('\nimport ', '\nimport ios.awt.*;\nimport ', 1)
        text = re.sub(r'\bjava\.awt\.(?:[a-z]+\.)?', 'ios.awt.', text)
        (out / f'{name}.java').write_text(text, encoding='utf-8')


def resources(java):
    target = WORK / 'resources'
    shutil.rmtree(target, ignore_errors=True)
    shutil.copytree(ROOT / 'runtime/core/resources', target)
    for charset, name in (('windows-31j', 'cp932'), ('GBK', 'gbk')):
        run(java, ROOT / 'tools/CharsetTables.java', charset, target / f'org/circus/core/{name}.bin.gz')


def native():
    out = WORK / 'native'
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir()
    sdk = subprocess.check_output(['xcrun', '--sdk', 'iphoneos', '--show-sdk-path'], text=True).strip()
    third_party = ROOT / 'runtime/native/third_party'
    objects = []
    for source in sorted((IOS / 'native').glob('*.c')):
        target = out / f'{source.stem}.o'
        run('xcrun', '-sdk', 'iphoneos', 'clang', '-arch', 'arm64', '-isysroot', sdk, '-miphoneos-version-min=15.0',
            '-O2', '-w', f'-I{third_party}', '-c', source, '-o', target)
        objects.append(target)
    run('libtool', '-static', '-o', out / 'libcircus.a', *objects, stderr=subprocess.DEVNULL)


def icon(source):
    """Compile an image sips can read into Assets.car, and return its Info.plist keys. iOS fills transparency with black."""
    base = WORK / 'icon'
    shutil.rmtree(base, ignore_errors=True)
    catalog = base / 'Assets.xcassets/AppIcon.appiconset'
    catalog.mkdir(parents=True)
    png, flat = base / 'source.png', base / 'flat.jpg'
    for command in (['-s', 'format', 'png', source, '--out', png],
                    ['-s', 'format', 'jpeg', '-s', 'formatOptions', '100', png, '--out', flat],
                    ['-s', 'format', 'png', '-z', '1024', '1024', flat, '--out', catalog / 'icon.png']):
        run('sips', *command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (catalog / 'Contents.json').write_text(json.dumps({'images': [{'filename': 'icon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}],
                                                       'info': {'author': 'xcode', 'version': 1}}))
    (base / 'Assets.xcassets/Contents.json').write_text(json.dumps({'info': {'author': 'xcode', 'version': 1}}))
    partial = base / 'partial.plist'
    run('xcrun', 'actool', base / 'Assets.xcassets', '--compile', WORK / 'resources', '--platform', 'iphoneos', '--minimum-deployment-target', '15.0',
        '--app-icon', 'AppIcon', '--target-device', 'ipad', '--target-device', 'iphone', '--output-partial-info-plist', partial, stdout=subprocess.DEVNULL)
    return plistlib.loads(partial.read_bytes())


def project(name, bundle, extra):
    shutil.copy(IOS / 'pom.xml', WORK / 'pom.xml')
    shutil.copy(IOS / 'robovm.xml', WORK / 'robovm.xml')
    text = (IOS / 'Info.plist.xml').read_text(encoding='utf-8').replace('@NAME@', name).replace('@BUNDLE@', bundle)
    if extra:
        keys = plistlib.dumps(extra).decode('utf-8')
        keys = keys[keys.index('<dict>') + len('<dict>'):keys.rindex('</dict>')]
        text = text[:text.rindex('</dict>')] + keys + text[text.rindex('</dict>'):]
    (WORK / 'Info.plist.xml').write_text(text, encoding='utf-8')


def compile_app(java_home):
    env = {**os.environ, 'JAVA_HOME': str(java_home)}
    for path in (WORK / 'target/robovm', WORK / 'target/robovm.tmp'):
        shutil.rmtree(path, ignore_errors=True)
    run('mvn', '-q', '-B', f'-Dios.src={IOS / "src"}', 'com.mobidevelop.robovm:robovm-maven-plugin:3.0.0:create-ipa',
        '-Drobovm.arch=arm64', '-Drobovm.iosSkipSigning=true', cwd=WORK, env=env)
    return WORK / 'target/robovm/DCRuntime.ipa'


def profile(team, bundle):
    """Let Xcode make or fetch the team profile, by building an empty app with automatic signing."""
    base = WORK / 'signing'
    shutil.rmtree(base, ignore_errors=True)
    (base / 'P.xcodeproj').mkdir(parents=True)
    (base / 'main.m').write_text('int main(void){return 0;}\n')
    with open(base / 'Info.plist', 'wb') as f:
        plistlib.dump({'CFBundleExecutable': '$(EXECUTABLE_NAME)', 'CFBundleIdentifier': '$(PRODUCT_BUNDLE_IDENTIFIER)', 'CFBundleName': 'P',
                       'CFBundlePackageType': 'APPL', 'CFBundleVersion': '1', 'CFBundleShortVersionString': '1'}, f)
    (base / 'P.xcodeproj/project.pbxproj').write_text(SIGNING_PROJECT.replace('@BUNDLE@', bundle).replace('@TEAM@', team))
    run('xcodebuild', '-quiet', '-project', 'P.xcodeproj', '-target', 'P', '-configuration', 'Debug', '-sdk', 'iphoneos',
        '-allowProvisioningUpdates', f'SYMROOT={base / "out"}', f'OBJROOT={base / "obj"}', 'build', cwd=base)
    app = base / 'out/Debug-iphoneos/P.app'
    details = subprocess.run(['codesign', '-dvv', app], capture_output=True, text=True).stderr
    identity = re.search(r'^Authority=(Apple Development.*)$', details, re.M).group(1)
    decoded = subprocess.check_output(['security', 'cms', '-D', '-i', app / 'embedded.mobileprovision'])
    return app / 'embedded.mobileprovision', plistlib.loads(decoded)['Entitlements'], identity


def sign(ipa, team, bundle):
    payload = Path(tempfile.mkdtemp(dir=WORK, prefix='payload-'))
    run('unzip', '-q', ipa, '-d', payload)
    app = payload / 'Payload/DCRuntime.app'
    provision, entitlements, identity = profile(team, bundle)
    shutil.copy(provision, app / 'embedded.mobileprovision')
    entitlements['application-identifier'] = f'{team}.{bundle}'
    with open(payload / 'entitlements.plist', 'wb') as f:
        plistlib.dump(entitlements, f)
    run('xattr', '-cr', app)
    run('codesign', '-f', '-s', identity, '--entitlements', payload / 'entitlements.plist', '--generate-entitlement-der', '--timestamp=none', app, stderr=subprocess.DEVNULL)
    run('codesign', '--verify', '--strict', app)
    target = WORK / 'DCRuntime.app'
    shutil.rmtree(target, ignore_errors=True)
    shutil.move(app, target)
    shutil.rmtree(payload)
    return target


def install(app, device):
    # The device link sometimes refuses the first attempt while a previous run is winding down.
    for attempt in range(4):
        result = subprocess.run(['xcrun', 'devicectl', 'device', 'install', 'app', '--device', device, str(app)], capture_output=True, text=True)
        if result.returncode == 0:
            return
    raise SystemExit(result.stdout + result.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--team', required=True, help='Apple developer team ID')
    parser.add_argument('--bundle-id', default='org.circus.dcruntime', help='changing it starts a new app, with no game or saves')
    parser.add_argument('--game', type=Path, help='game directory that supplies the name and icon')
    parser.add_argument('--name', help='name under the icon; defaults to the name of the game')
    parser.add_argument('--icon', type=Path, help="any image sips can read; defaults to the icon in the game's exe")
    parser.add_argument('--device', help='device UDID, from xcrun devicectl list devices')
    parser.add_argument('--install', action='store_true', help='install on --device')
    parser.add_argument('--copy-game', action='store_true', help='copy --game into the app on --device, about 4 GB')
    args = parser.parse_args()
    if (args.install or args.copy_game) and not args.device:
        parser.error('--install and --copy-game need --device')
    if args.copy_game and not args.game:
        parser.error('--copy-game needs --game')
    identity = describe(args.game) if args.game else None
    WORK.mkdir(parents=True, exist_ok=True)
    home = unpack(*JDK) / 'Contents/Home'
    sources()
    resources(home / 'bin/java')
    native()
    source = args.icon
    if source is None and identity and identity.icon:
        source = WORK / 'game-icon.ico'
        source.write_bytes(identity.icon)
    extra = icon(source) if source else {}
    project(args.name or (identity.name if identity else 'DC Runtime'), args.bundle_id, extra)
    app = sign(compile_app(home), args.team, args.bundle_id)
    print(app)
    if args.install:
        install(app, args.device)
        print('installed on', args.device)
    if args.copy_game:
        run('xcrun', 'devicectl', 'device', 'copy', 'to', '--device', args.device, '--domain-type', 'appDataContainer',
            '--domain-identifier', args.bundle_id, '--source', args.game, '--destination', f'Documents/{args.game.name}')
        print('copied', args.game)


if __name__ == '__main__':
    main()
