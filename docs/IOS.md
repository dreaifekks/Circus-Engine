# iPad

`tools/package_ios.py` builds an iPad app that runs the Mac runtime. Tested on an iPad 8 with iPadOS 26.7.2, and only with DC2PC.

```sh
python3 tools/package_ios.py --team TEAMID --game "/path/to/DC2PC" --device UDID --install --copy-game
```

`--game` gives the app the name and icon from the game's exe, as on the Mac. `--copy-game` copies the game into the app, about 4 GB. Without it, put a folder containing `AdvData` in the app's Documents from Finder or Files. Find the UDID with `xcrun devicectl list devices`. The device must be registered with the team, have Developer Mode on, and be unlocked. A personal team's app stops opening after seven days.

## Touch

| Gesture | Mouse or key |
|---|---|
| One finger | Left button |
| Two fingers tapped, or one held for 0.8 s | Right button |
| Two fingers dragged | Wheel |
| Three fingers held | Ctrl, until released |
| Four fingers tapped | Lock Ctrl on or off. A right click also unlocks it |

iPadOS may keep four-finger gestures for itself if multitasking gestures are on.

## How it is built

Java runs through MobiVM, because iOS has no JVM and allows no JIT. The Mac sources are not edited or forked:

- `runtime/core` and `DesktopLegacy`, `DesktopEngine`, `DesktopCaptures` and `DesktopSave` are copied to `build/ios` at build time. Imports of AWT move to `ios.awt`, and the two charset lookups move to `TableCharset`, because MobiVM has neither CP932 nor GBK. A fix in those files reaches the iPad on the next build.
- `ios.awt` is the part of Java2D those files use: rectangle clips, translation and scale, source-over drawing on int ARGB pixels. Glyphs come from CoreText, in `native/glue.c`.
- Pictures drawn smaller are area averaged. This differs from the exe on purpose: `40E3E0` takes one source pixel per target pixel, and the iPad enlarges the screen about 2.7 times after that, so the dropped rows show.
- `IosMedia` stands in for `DesktopMedia`. Ogg is decoded to a WAV file and played by `AVAudioPlayer`, and movies use `pl_mpeg` with the audio as the clock.
- `GameMain` stands in for `DesktopRuntime.window()`. UIKit on iOS 27 stops apps without the scene lifecycle, so it has one.

Only `DesktopLegacy` is connected, so the games on `DesktopDd` and `DesktopUnity` will not run.

## Not done

- A keyboard for `editingText()`, in case a game's name entry needs one.
- Function keys and the Mac menu commands. The game's own toolbar covers saving and loading.

## Checking without touching it

`CIRCUS_PATH` makes the app walk a comma-separated route through real press and release calls, and write `shot-*.png` and `app.log` to Documents. Tokens are menu actions, `tap`, `back`, `ctrl`, `ctrloff`, `script:NAME`, `until:NAME@OFFSET` and `xy-X-Y`. Text in the route may not survive the environment, so it goes by script position.

```sh
xcrun devicectl device process launch --device UDID --terminate-existing \
  --environment-variables '{"CIRCUS_PATH":"03,start,tap,tap,ctrl,script:NAME,until:NAME@OFFSET","CIRCUS_LIMIT":"5"}' BUNDLE
```
