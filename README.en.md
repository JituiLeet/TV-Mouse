# TV Mouse

[简体中文](./README.md)　|　[English](./README.en.md)

Control the mouse pointer on Android TV with a remote and operate other apps like using a mouse.

## Features

- Floating mouse pointer: move with the D-pad, click with OK, and hold OK to perform a long press.
- Pointer sensitivity and size controls in More Settings.
- Simplified main screen with only Enable/Disable Pointer, Hide/Show Pointer, and More Settings.
- Fixed Scroll: press Volume Down + Menu to enable it. Once enabled, it can only be disabled by holding Back for 3 seconds.
- Shortcut Settings: configure button combinations or a held button and trigger duration for opening settings, enabling Fixed Scroll, and hiding/showing the pointer. Defaults: Volume Up + Menu opens settings; Volume Down + Menu enables Fixed Scroll.
- Custom pointer appearances: import 128 × 128 pixel PNG or JPG images, set an appearance as the pointer, rename it, or delete it. Normal and Fixed Scroll modes can share an appearance.
- Chinese and English interfaces, following the system language by default with manual switching available.

## Usage

Grant overlay and accessibility permissions before enabling the mouse. The main screen provides pointer enable/disable, hide/show, and More Settings. More Settings includes sensitivity, pointer size, appearance, language, and permission shortcuts.

By default, press Volume Up + Menu to open settings and Volume Down + Menu to enable Fixed Scroll. While active, hold Back for 3 seconds to disable it. Customize shortcuts in More Settings → Shortcut Settings. If the accessibility service is interrupted or encounters an exception, the app attempts to disable key filtering to reduce the risk of remote keys remaining blocked.


## Compatibility

Android 4.4 (API 19) through Android 15 (API 35).

## License

This project is open source under the MIT License. See [LICENSE](LICENSE) for details.
