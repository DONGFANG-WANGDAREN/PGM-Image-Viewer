# Station RX

Last updated: 2026-08-26 16:23

`Station RX` is an Android file viewer with a built-in LAN file transfer service and browser-based file manager.

The current product is focused on two core areas:

1. On-device file viewing
2. LAN browser-based file transfer

## Current Features

### File Viewing

- Open files from the in-app document picker
- Handle Android `VIEW` intents as an "Open with" target
- Save recent files and reopen them from the history panel
- Open the history panel from the history button or by swiping in from the left edge
- Open the built-in app README from **About This App**
- Load text files in two phases to keep the UI responsive
- Show a dedicated too-large state when text files exceed the display limit
- Search text with highlighting and previous/next navigation
- Switch Markdown between source view and rendered preview
- Pretty-print JSON and XML, then switch back to the original text
- Adjust text size
- Use the quick-scroll bar for long text files
- Apply basic highlighting for code-like text files
- View images in-app
- View PGM and PMG images in-app
- Play audio and video in-app
- Open PDF files in-app
- Show an in-viewer not supported message for unsupported file types

### Tools

The tools screen includes a fixed top card plus two tool entries:

- **About This App**: app name, version code, version name, and a button that returns to the home screen and opens the built-in README

- **Dashboard**: live reader and file transfer status
- **File Transfer**: start the local service, copy the browser URL, scan the QR code, or directly confirm the pending browser session from the phone

### Dashboard

The dashboard shows two live cards:

- **Reader**: current file name, path, open mode, detailed file type, MIME type, file size, and memory usage
- **File Transfer**: current file transfer URL, connected and pending client counts, and a live summary of active browser sessions

Tapping the reader card opens the dedicated reader detail screen.

Tapping the file transfer card opens the dedicated detail screen.

### File Transfer Details

The detail page refreshes continuously and shows service status, the current file transfer URL, connection counts, and a live list of browser sessions. Each session can be opened for a dedicated detail view with:

- service status
- current file transfer URL
- current connection state
- browser and system summary
- session ID
- browser name
- platform
- language
- time zone
- screen resolution
- remote IP address
- current page (`File-Transfer` or `files`)
- user agent
- session created time
- login confirmed time
- last active time

### LAN File Transfer Access

The app starts a local HTTP service. The default port comes from `app_config.json` and is currently `8081`.

Access flow:

1. Open `http://<device-ip>:8081/File-Transfer` on another device
2. The page creates a one-time session and shows a QR code
3. On the phone, open **Tools -> File Transfer**
4. Copy the URL, scan the QR code, or confirm the pending session directly in the app
5. After confirmation, the browser is redirected to `/files`

Authentication behavior:

- each QR code is single-use
- if a browser is already authenticated and opens the wrong route, it is redirected to `/files`
- authenticated browsers do not need to log in again while the file transfer service stays running
- all authenticated sessions are cleared when the phone-side file transfer service stops
- successful confirmation writes an auth cookie
- the published LAN address uses the phone's current real local IP address
- the web page reports browser, platform, language, time zone, screen size, page state, and last-seen time back to the app

### Web File Manager

After login, the browser can use the built-in file manager to:

- browse from the internal storage root view exposed by the app
- sort folders before files
- navigate with breadcrumbs
- download files
- preview images in an overlay
- preview text files in an overlay
- upload files
- upload large files in chunks
- show upload and download progress cards
- drag and drop files onto the page or a folder to upload
- view a detailed left sidebar with phone, system, service, and network information
- view the current browser session details directly in the sidebar
- view other connected browser devices in the sidebar, including state, IP, page, language, time zone, resolution, user agent, and activity timestamps

Uploaded files are saved into the directory currently open in the web file manager. By default, that means the current internal storage view shown in the browser.

When a file name already exists, the app appends `_1`, `_2`, and so on instead of overwriting the existing file.

## Supported File Types

### Opened In-App

- Images: `.jpg` `.jpeg` `.png` `.bmp` `.webp` `.gif` `.heic` `.heif` `.pgm` `.pmg`
- Text: `.txt` `.json` `.xml` `.yaml` `.yml` `.java` `.swift` `.kt` `.kts` `.md` `.markdown` `.csv` `.log` `.ini` `.cfg` `.conf` `.properties` `.gradle` `.css` `.js` `.ts` `.html` `.htm`
- Video: `.mp4` `.m4v` `.mov` `.mkv` `.webm` `.avi` `.3gp` `.mpeg` `.mpg`
- Audio: `.mp3` `.wav` `.flac` `.m4a` `.aac` `.ogg` `.opus` `.amr` `.wma`
- PDF: `.pdf`

### Unsupported for Now

- Office formats such as `.docx`, `.xls`, `.xlsx`, `.doc`, `.ppt`, `.pptx`, `.wps`, `.odt`, `.ods`, `.odp`, and `.rtf`
- Installer and package formats such as `.apk`, `.ipa`, `.exe`, `.msi`, `.dmg`, `.pkg`, `.deb`, and `.rpm`
- Any other file type outside the supported image, audio, video, text, and PDF groups, with PGM and PMG treated as supported image formats

## Web Routes

The built-in web routes are:

- `/File-Transfer`: file transfer entry page
- `/File-Transfer-login`: token-based file transfer login endpoint
- `/api/qr.png`: generate the QR code image for the current login session
- `/files`: file manager page
- `/api/web-login-session`: create a web login session
- `/api/session-status`: check whether a session has been confirmed
- `/api/confirm`: confirm web login from the phone
- `/api/web-login-client-info`: report browser metadata
- `/api/web-login-heartbeat`: browser heartbeat
- `/api/files`: get a directory listing
- `/api/download`: download a file
- `/api/view`: preview a file
- `/api/upload`: upload a file directly
- `/api/upload-init`: initialize chunked upload
- `/api/upload-chunk`: upload a chunk
- `/api/upload-finish`: finish an upload
- `/api/device`: read device and network information

## Storage Layout

The current app-managed structure is:

```text
/Station RX/
  Log/
    yyyy-MM-dd/
      Log_HH-mm_yyyy-MM-dd.txt
  History/
    History.json
```

## Configuration

Runtime settings are centralized in [`app/src/main/assets/app_config.json`](app/src/main/assets/app_config.json).

That file currently defines:

- app root folder name
- log folder name and naming pattern
- log file extension
- history folder name and file name
- LAN HTTP port
- text preview limit
- large-text threshold
- max text display bytes
- max history size
- default text size
- text size range

## Permissions

- `MANAGE_EXTERNAL_STORAGE`: required for broad file access on Android 11+
- `CAMERA`: used only for QR-code scanning in the phone-side file transfer flow
- `INTERNET`, `ACCESS_NETWORK_STATE`, and `ACCESS_WIFI_STATE`: used for the LAN file transfer service and network status
- `POST_NOTIFICATIONS`: used for the foreground-service notification on supported Android versions

## Main Entry Points

Key Android entry classes:

- `ActivityPictureViewer`: main screen for file selection, history, and viewing
- `ActivityReaderStats`: reader detail screen
- `ActivityTools`: tools screen
- `ActivityAboutApp`: app information screen
- `ActivityWebSocketDashboard`: dashboard screen
- `ActivityFileTransferStats`: file transfer detail screen
- `ActivityFileTransferClientDetails`: per-connection detail screen
- `WebSocketService`: LAN file transfer service
- `WebHttpRouter`: HTTP routing and API handling

## Current Scope

- Focused on **file viewing + LAN file transfer**
- Includes a browser-based file manager for LAN access
- Chat features have been removed
- Rust-related code has been removed
- The LAN web service uses `HTTP`, not `HTTPS`

## Next Steps

- Add a license
- Review and improve the GitHub repository page
- Evaluate whether the app needs a dedicated icon
- Improve permission prompts
- Improve the reader
- Improve file transfer
- Improve the dashboard
