# Station RX

`Station RX` is an Android local file reader built to open a wide range of common file types from one place.

It can open files from the in-app picker and also receive system `VIEW` intents as an "Open with" target.

## Core Features

- Opens local files from the system file picker.
- Handles system "Open with" requests for supported file types.
- Searches text content with match highlighting and previous/next navigation.
- Switches Markdown files between source view and rendered preview.
- Pretty-prints JSON and XML files.
- Applies basic syntax highlighting to code-like text files.
- Views images and PGM files in-app with zoom support.
- Plays video and audio files in-app.
- Opens PDF, DOCX, and Excel files in-app, with the option to use a system app when available.
- Prompts before opening APK files in the system installer.
- Saves recent files so they can be reopened quickly from the history panel.
- Opens large files on a background thread to keep the UI responsive.
- Provides a built-in **file transfer** tool for LAN browser access.

## Supported Formats

### Opened In-App

- PGM: `.pgm`
- Images: `.jpg` `.jpeg` `.png` `.bmp` `.webp` `.gif` `.heic` `.heif`
- Text: `.txt` `.json` `.xml` `.yaml` `.yml` `.java` `.swift` `.kt` `.kts` `.md` `.markdown` `.csv` `.log` `.ini` `.cfg` `.conf` `.properties` `.gradle` `.css` `.js` `.ts` `.html` `.htm`
- Word documents: `.docx`
- Excel spreadsheets: `.xls` `.xlsx`
- Video: `.mp4` `.m4v` `.mov` `.mkv` `.webm` `.avi` `.3gp` `.mpeg` `.mpg`
- Audio: `.mp3` `.wav` `.flac` `.m4a` `.aac` `.ogg` `.opus` `.amr` `.wma`
- PDF: `.pdf`

### Opened With External Apps

- Documents: `.doc` `.ppt` `.pptx` `.wps` `.odt` `.ods` `.odp` `.rtf`

### Install Or Block

- Opens the system installer: `.apk`
- Blocked from direct opening: `.ipa` `.exe` `.msi` `.dmg` `.pkg` `.deb` `.rpm`

## Reading Features

- Shows live match counts while searching text.
- Supports previous and next search navigation.
- Renders Markdown preview in-app.
- Toggles JSON and XML between raw text and pretty-printed output.
- Adjusts text size for easier reading.
- Truncates oversized text, DOCX, and Excel previews to keep the viewer responsive.

## Tools & File Transfer

The tools menu is opened from the main screen and currently contains:

- **WebSocket Dashboard** — shows the reader card and the current file transfer service state.
- **File Transfer** — opens the LAN web login flow and browser file manager.

### File Transfer & Web Login

The HTTP server provides a web login flow designed for use from another device on the same LAN:

1. On the other device, open `http://<device-ip>:8081/web-login`. The page shows a QR code that identifies the browser session.
2. In the app, open **Tools → File Transfer** and point the camera at the QR code, or wait for the page to generate a login session and then confirm it from the phone.
3. After the app confirms the session, the browser is automatically logged in and redirected to the file manager.

From the web interface you can:

- View detailed device information.
- Browse files in the app-managed file transfer directory.
- Download files or view text and image files online.
- Upload files into `Station RX/文件传输/yyyy-MM-dd/`.

The browser session is authenticated with a randomly generated token stored in a cookie. Each QR code is single-use and expires after a few minutes if not scanned. After the web page is opened, the browser reports client metadata back to the app, including IP, browser name, platform, language, timezone, screen size, current page, and last seen time.

### WebSocket Dashboard

The dashboard shows two cards:

- **Reader** — current file name, file path, open mode, file size, and memory usage.
- **File Transfer** — current file transfer URL and the latest browser-side state.

When the file transfer card is tapped, it opens a dedicated detail page instead of the scan-login dialog.

### File Transfer Detail Page

The file transfer detail page is used to inspect the currently connected or pending computer session. It shows:

- whether the HTTP file transfer service is running
- the current web login URL
- the current connection state
- the latest computer summary (`browser / platform`)
- browser, platform, language, timezone, and screen resolution
- remote IP address
- current browser page (`web-login` or `files`)
- session created time, login confirmed time, and last active time

## Configuration

Folder names, file naming formats, and many runtime constants are centralized in [`app_config.json`](app/src/main/assets/app_config.json). The file is loaded when the app starts and covers:

- Storage folder names (`Station RX`, `日志`, `历史记录`, `文件传输`).
- Log, history, and file transfer date naming formats.
- HTTP port for the LAN file transfer service.
- Text preview limits, history record limits, and text size range.

When editing naming formats, any non-date literal (for example `Log_` or `Chat_`) must be wrapped in single quotes for `SimpleDateFormat`:

```json
"logFileNameFormat": "'Log'_HH-mm_yyyy-MM-dd"
```

## App Logs

Runtime logs are written to external storage so they can be inspected without Android Studio:

```
/Station RX/日志/yyyy-MM-dd/Log_HH-mm_yyyy-MM-dd.txt
```

A new file is created each time the app starts. Logs follow the Android Studio Logcat format.

## Storage Layout

Current app-managed file output uses this structure:

```
/Station RX/
  日志/
    yyyy-MM-dd/
      Log_HH-mm_yyyy-MM-dd.txt
  历史记录/
    History.json
  文件传输/
    yyyy-MM-dd/
      <uploaded files>
```
