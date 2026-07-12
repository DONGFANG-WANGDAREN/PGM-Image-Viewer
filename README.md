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
- Provides a built-in **WebSocket chat room** that can be opened from the tools menu.

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

## Tools & WebSocket Chat Room

The tools menu is opened from the main screen and currently contains:

- **WebSocket Dashboard** — lists the running local WebSocket service and opens a detailed statistics page.
- **Chat Room** — starts a local WebSocket server and a built-in HTTP monitor page.
- **Scan Web Login** — scans a QR code displayed on another device to authorize its browser session.
- **Rust Server** — starts an experimental Rust-based server that runs in parallel with the Java chat room.

In the chat room:

- The app runs a WebSocket server (default port `8080`) and an HTTP server (default port `8081`).
- Other apps or browsers on the same network can connect to `ws://<device-ip>:8080`.
- Browsers can open `http://<device-ip>:8081` to view and send messages.
- Messages are broadcast to all connected clients, with sender names shown.
- The app can send text and images selected from the gallery.
- System messages notify when users enter or leave the chat room.
- The service runs in the foreground with a persistent notification. It keeps running after leaving the chat page or sending the app to the background, and only stops when the user presses **Stop** in the notification or the in-app **Stop Server** button, or when the app process is killed.
- The notification content is updated with the latest incoming message preview, so users can see new activity at a glance.

### Web Login & File Manager

The HTTP server provides a web login flow designed for use from another device on the same LAN:

1. On the other device, open `http://<device-ip>:8081/web-login`. The page shows a QR code that identifies the browser session.
2. In the app, open **Tools → Scan Web Login** and point the camera at the QR code.
3. After the app confirms the scan, the browser is automatically logged in and redirected to the file manager.

From the web interface you can:

- View detailed device information.
- Browse files in external storage.
- Download files or view text and image files online.
- Upload files from the browser to the current folder.

The browser session is authenticated with a randomly generated token stored in a cookie. Each QR code is single-use and expires after a few minutes if not scanned.

### WebSocket Dashboard

The dashboard shows the running local service and, when opened, displays real-time statistics grouped into:

- **Service** — running status, uptime, messages received/sent, total/active/peak users, WebSocket/HTTP ports
- **Network** — WiFi connection status, link speed, signal strength, bytes received/sent
- **Device** — estimated app CPU usage
- **Users** — per-user list showing online/offline status, stay duration, message count, and last active time

> Ports, sender names, and message types can be changed in [`app_config.json`](app/src/main/assets/app_config.json).

### Rust Server (Experimental)

The **Rust Server** is an experimental Axum-based server embedded in the app. It runs on the Java HTTP port plus `1000` (default `9081`) and uses the same web assets as the Java chat room. It is intended to eventually mirror the Java chat room but is still a work in progress.

What currently works:

- WebSocket chat at `/ws?name=...` with text messages and enter/leave system messages.
- Web chat page (`/`) with text and image sending.
- File manager page (`/files`) with file listing, download, preview, and chunked upload.
- Device information page via `/api/device`.

Known issues and limitations (not being changed at the moment):

- **Authentication**: the Rust server does not enforce the token/cookie login used by the Java file manager. The web file manager is publicly accessible while the Rust server is running.
- **Chat logs**: chat messages are not written to the `WebSocket/Chat/...` log files (the Java server still does this).
- **WebSocket port**: the Rust WebSocket is served on the Rust HTTP port (`/ws`), not on a separate dedicated WebSocket port.
- **Image uploads**: the whole image is loaded into memory before being saved.
- **QR web login**: the Rust server does not support the scan-to-login flow.

## Chat Logs

Every time the chat room is opened, a new chat log file is created at:

```
/Station RX/WebSocket/Chat/yyyy-MM-dd/Chat_HH-mm-ss_yyyy-MM-dd.txt
```

Sent images are stored separately at:

```
/Station RX/WebSocket/Chat/Images/
```

Logs are written in plain text and are not loaded back into the app.

## Configuration

Folder names, file naming formats, and many runtime constants are centralized in [`app_config.json`](app/src/main/assets/app_config.json). The file is loaded when the app starts and covers:

- Storage folder names (`Station RX`, `Log`, `History`, `WebSocket/Chat`, `Images`).
- Log, history, and chat file naming formats.
- WebSocket and HTTP ports, sender names, message types, and image URL prefix.
- Text preview limits, history record limits, and text size range.

When editing naming formats, any non-date literal (for example `Log_` or `Chat_`) must be wrapped in single quotes for `SimpleDateFormat`:

```json
"logFileNameFormat": "'Log'_HH-mm_yyyy-MM-dd"
```

## App Logs

Runtime logs are written to external storage so they can be inspected without Android Studio:

```
/Station RX/Log/yyyy-MM-dd/Log_HH-mm_yyyy-MM-dd.txt
```

A new file is created each time the app starts. Logs follow the Android Studio Logcat format.
