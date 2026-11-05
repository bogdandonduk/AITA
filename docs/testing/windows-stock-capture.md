# Windows local stock recording

The Windows client includes a selected-window recorder, an accessibility reader and local OCR. No cloud recognition service, account, API key or model download is needed. The reader runs as the logged-in Windows user. It does not request administrator rights, disable Windows security, press keys, move the source window, or scroll another application.

The import screen explains the scope and requires the user to choose a currently visible top-level window. It then opens a small draggable, non-activating overlay. The user switches to that source application and presses **Start recording**. They scroll the stock list and expand records manually. **Pause** stops acquisition, **Resume** restarts it, and **Stop** closes the helper. Switching to an unrelated window pauses recording. Minimizing, closing or losing access to the selected window ends the recording and retains already recognized rows for review. A reused Windows handle is checked against its original process ID.

The import must always be reviewed before writing stock. Recognition is imperfect; text hidden offscreen or behind collapsed details cannot be imported. Quantity, price, currency and barcode ambiguity must remain reviewable rather than silently guessed.

## Recognition and privacy

1. `UIAutomationClient` reads only the selected window's bounded control tree. Password controls, offscreen controls and partially clipped/out-of-window cell rectangles are excluded; the reader never clamps a rectangle while keeping hidden full-cell text. UI Automation does not change the source application.
2. Custom-painted tables can expose only toolbar text. Every fifth sampled view receives an OCR check regardless of barcode-like toolbar text, and sparse accessibility views use OCR immediately. Identical PNGs are deduplicated before OCR. The helper renders that specific HWND using `PrintWindow(PW_CLIENTONLY | PW_RENDERFULLCONTENT)` and sends a bounded PNG through a local process pipe. It never falls back to desktop-wide `CopyFromScreen` capture.
3. A separate JVM process runs pinned Tess4J 5.18.0/Tesseract 5.5.2. If the native recognizer crashes or hangs, it cannot crash AITA's main JVM. OCR processing is limited to 18 seconds per frame. The helper's 22-second busy watchdog prevents broken accessibility providers from hanging indefinitely.
4. The selected app must remain foreground at capture and delivery. The overlay uses `WS_EX_NOACTIVATE` and cannot steal source keyboard focus. A frame in flight is discarded when the in-app Pause or Stop action is used.
5. Raw images and recognized words remain in process memory and bounded pipes. Screenshots are not saved, uploaded, added to crash diagnostics or written into logs. Fixed error codes are localized; native exception messages and screen titles are never emitted to diagnostics.

Current bounds: 6 million pixels per selected client area, 2,000 text elements per frame, 5,000 accessibility nodes per traversal, one capture in flight, nominal 1-second sampling, and 1,800 delivered frames per recording. Reviewing/importing and starting another recording supports larger inventories without unbounded memory growth. The parser/review layer separately bounds accepted rows.

Windows' Win32 accessibility and `PrintWindow` APIs do not provide a universal browser-like capture-permission prompt. Explicit AITA consent/window selection is the permission step. Windows can refuse protected/elevated applications. AITA reports that refusal and never self-elevates. Some hardware-rendered or protected windows do not render through `PrintWindow`; the app reports no readable text rather than attempting a broader screen capture.

## Packaged offline models

Models are JVM-only resources. English, Russian, Kazakh, Kyrgyz, Tajik and Uzbek (Latin and Cyrillic) use the official `tesseract-ocr/tessdata_fast` release 4.1.0, commit `65727574dfcd264acbb0c3e07860e4e9e9b22185`, under Apache-2.0. The seven files total approximately 32 MiB. Their sizes, SHA-256 digests, source and license are in `composeApp/src/jvmMain/resources/stock-capture/tessdata/manifest.json` and `LICENSE`.

On first OCR use, the bundled models are copied to the user's `.aita/stock-ocr/65727574` directory and verified against the manifest. Corrupt files are replaced from bundled resources. There is no network fetch. Models are not client stock caches and contain no user data.

Tess4J carries `win32-x86-64/libtesseract552.dll`; transitive Lept4J 1.23.0 carries `win32-x86-64/libleptonica1870.dll`. PE import inspection identifies Microsoft `MSVCP140.dll`, `VCRUNTIME140.dll` and `VCRUNTIME140_1.dll` in addition to standard supported Windows OS DLLs. They must be included with the packaged JRE; a global system installation is not assumed. The native libraries run only in the worker. The worker uses the installed `AITA.exe --aita-local-ocr` early launcher dispatch because packaged jlink runtimes may omit `java.exe`.

## Verification

Portable checks:

```sh
./gradlew :composeApp:jvmTest --tests kz.aita.StockCaptureProtocolTest
```

Windows build machine (no screen capture):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\testing\verify-windows-stock-capture.ps1 -AppImage .\composeApp\build\compose\binaries\main\app\AITA
```

The Windows check compiles the C#/PowerShell helper, verifies all model hashes, checks pinned OCR jars, requires the three Microsoft runtime DLLs in the built application, and recognizes a generated bilingual PNG through the packaged launcher. The packaged OCR check is a hard gate. It then runs a bounded real-recorder probe against its own synthetic windows: actual UIA frame delivery, clipping, pause/resume, unrelated-window foreground pause, nonactivating overlay, selected-window OCR bitmap and Stop. Both Windows workflows run this gate after creating the application image and before packaging/publication.

A service/session-0, locked or otherwise noninteractive desktop returns **UNSUPPORTED**, never a native-capture PASS. The gate prints that distinction and writes `build/windows-stock-capture-verification.json`, preserved with build diagnostics. A native probe failure on an interactive desktop fails the build. A skipped native probe still leaves physical capture acceptance pending; the independent packaged OCR check cannot be skipped. Linux-only C# compilation does not establish native capture behavior.

## Primary API references

- [Microsoft UI Automation: obtaining elements](https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-obtainingelements)
- [Microsoft PrintWindow](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-printwindow)
- [Microsoft extended window styles](https://learn.microsoft.com/en-us/windows/win32/winmsg/extended-window-styles)
- [Microsoft Windows.Media.Ocr restrictions](https://learn.microsoft.com/en-us/uwp/api/windows.media.ocr): desktop use requires package identity, so it is not the EXE/MSI implementation here.
- [Tess4J source and releases](https://github.com/nguyenq/tess4j)
- [Official Tesseract fast models](https://github.com/tesseract-ocr/tessdata_fast/tree/65727574dfcd264acbb0c3e07860e4e9e9b22185)

## Repeatable synthetic Windows acceptance

Use a Windows desktop where no real store work is in progress. The fixture creates only artificial rows. It never searches for or captures another existing window.

```powershell
# Automatic real-recorder checks: UIA frames, clipping, pause/resume, foreground
# ownership, local OCR bitmap and Stop. Opens/closes only its own synthetic windows.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\testing\run-windows-stock-capture-fixture.ps1 -Verify

# Keep the synthetic table open for a manual AITA recording:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\testing\run-windows-stock-capture-fixture.ps1
```

Then use a disposable AITA test store:

1. Open stock → screen import, read the local-only explanation, select **AITA synthetic stock fixture · NOT CUSTOMER DATA**, and prepare recording. Switch to the fixture and press **Start recording** on the floating panel.
2. Scroll slowly, overlapping a few rows between views. Barcode `4601234567893` and title `Молоко 2,5%` must retain their text; `12,5` must be interpreted as 12.5, and kg/g remain distinct. Repeated visible rows must not double quantities. Expand the synthetic details and verify conflicts remain reviewable.
3. Press **Pause**. Scroll several pages. No newly visible rows should enter review. Press **Resume** and verify new rows appear. The floating panel must never redirect typing into itself or change source scroll position.
4. Switch to another application. Recording must pause, and none of that application's content may appear. Returning to the fixture requires **Resume**. Drag the overlay across the fixture; its own text must never enter recognition.
5. Enable **Custom-painted table** and record its visible rows. These must arrive through local OCR. Review remains required for uncertain text. Repeat offline; capture/recognition must not depend on network access. Import can then use AITA's ordinary offline stock mutation queue.
6. Minimize or close the fixture. AITA must show a localized explanation and retain collected rows. Restart with another fixture, then leave the import sheet; the overlay/helper/OCR worker must disappear.
7. Keep the fixture unchanged for a minute. Frame count must stop growing after duplicate frames are detected; there must be no accumulating worker processes. No PNGs, captured titles or recognized rows should appear in diagnostic logs.

The automatic `-Verify` test must run in an interactive desktop session. A service/session-0 or locked desktop can legitimately reject UI Automation/capture, and must not be reported as passing physical acceptance.

The probe has a 45-second in-process watchdog and a 55-second parent-process deadline. Raw frame messages stay in its in-memory observer and are never printed to CI logs or written as screenshots. No external application is selected or captured.

The expected app-local runtime DLLs are documented by [Eclipse Temurin's Windows ReDist build configuration](https://github.com/adoptium/temurin-build/wiki/Eclipse-Temurin-Windows-Visual-Studio%2C-Toolset%2C-SDK-and-ReDist-versions): `msvcp140.dll`, `vcruntime140.dll`, and `vcruntime140_1.dll`. The gate verifies their presence in the actual generated runtime; it does not depend on a developer machine's global Visual C++ installation or copy arbitrary DLLs from System32.
