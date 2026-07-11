# Reader RX

`Reader RX` is an Android local file reader built to open a wide range of common file types from one place.

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

- **Chat Room** — starts a local WebSocket server and a built-in HTTP monitor page.

In the chat room:

- The app runs a WebSocket server (default port `8080`) and an HTTP server (default port `8081`).
- Other apps or browsers on the same network can connect to `ws://<device-ip>:8080`.
- Browsers can open `http://<device-ip>:8081` to view and send messages.
- Messages are broadcast to all connected clients, with sender names shown.
- The app can send text and images selected from the gallery.
- System messages notify when users enter or leave the chat room.

> Ports, sender names, and message types can be changed in [`app_config.json`](app/src/main/assets/app_config.json).

## Chat Logs

Every time the chat room is opened, a new chat log file is created at:

```
/Reader RX/WebSocket/Chat/yyyy-MM-dd/Chat_HH-mm-ss_yyyy-MM-dd.txt
```

Sent images are stored separately at:

```
/Reader RX/WebSocket/Chat/Images/
```

Logs are written in plain text and are not loaded back into the app.

## Configuration

Folder names, file naming formats, and many runtime constants are centralized in [`app_config.json`](app/src/main/assets/app_config.json). The file is loaded when the app starts and covers:

- Storage folder names (`Reader RX`, `Log`, `History`, `WebSocket/Chat`, `Images`).
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
/Reader RX/Log/yyyy-MM-dd/Log_HH-mm_yyyy-MM-dd.txt
```

A new file is created each time the app starts. Logs follow the Android Studio Logcat format.
