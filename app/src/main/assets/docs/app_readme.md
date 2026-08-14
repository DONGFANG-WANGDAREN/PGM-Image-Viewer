# Station RX

Last updated: 2026-08-14 17:24

`Station RX` is an Android file viewer with built-in LAN file transfer and browser access.

The project has already removed all chat-related and Rust-related features. The current product is focused on two core areas:

1. On-device file viewing
2. LAN browser-based file transfer

## Current Features

### File Viewing

- Open files from the in-app document picker
- Handle Android `VIEW` intents as an "Open with" target
- Save recent files and reopen them from the history panel
- Load large text files in two phases to keep the UI responsive
- Skip displaying text files that exceed the size limit and show a too-large message instead
- Search text with highlighting and previous/next navigation
- Switch Markdown between source view and rendered preview
- Pretty-print JSON and XML
- Adjust text size
- Apply basic highlighting for code-like text files
- View images in-app
- Play audio and video in-app
- Open PDF files in-app
- Show an in-viewer not supported message for unsupported file types

### Tools

The tools screen includes a fixed top card plus two tool entries:

- **About This App**: app name, version code, version name, and a button that returns to the home screen and opens the built-in README

- **Dashboard**
- **File Transfer**

### Dashboard

The dashboard shows two live cards:

- **Reader**: current file name, path, open mode, file size, and memory usage
- **File Transfer**: current file transfer URL, connected and pending client counts, and a live summary of active browser sessions

Tapping the file transfer card opens the dedicated detail screen.

### File Transfer Details

The detail page refreshes continuously and shows service status plus a live list of all browser sessions. Each session can be opened for a dedicated detail view with:

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
4. Scan the QR code or confirm the pending session directly in the app
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
- preview images online
- preview text files online
- upload files
- upload large files in chunks
- drag and drop files onto the page or a folder to upload
- view a detailed left sidebar with phone, system, service, and network information
- view the current browser session details directly in the sidebar
- view other connected browser devices in the sidebar, including state, IP, page, language, time zone, resolution, user agent, and activity timestamps

Uploaded files are saved to:

```text
/Station RX/File Transfer/yyyy-MM-dd/
```

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
- `/files`: file manager page
- `/api/web-login-session`: create a web login session
- `/api/session-status`: check whether a session has been confirmed
- `/api/confirm`: confirm web login from the phone
- `/api/web-login-client-info`: report browser metadata
- `/api/web-login-heartbeat`: browser heartbeat
- `/api/files`: get a directory listing
- `/api/download`: download a file
- `/api/view`: preview a file
- `/api/upload-init`: initialize chunked upload
- `/api/upload-chunk`: upload a chunk
- `/api/upload-finish`: finish an upload
- `/api/device`: read device and network information

## Storage Layout

The current app-managed structure is:

```text
/Station RX/
  Logs/
    yyyy-MM-dd/
      Log_HH-mm_yyyy-MM-dd.txt
  History/
    History.json
  File Transfer/
    yyyy-MM-dd/
      <uploaded files>
```

## Configuration

Runtime settings are centralized in `app/src/main/assets/app_config.json`.

That file currently defines:

- app root folder name
- log folder name and naming pattern
- history folder name and file name
- file transfer folder name and day-folder pattern
- LAN HTTP port
- text preview limit
- large-text threshold
- max history size
- text size range

## Main Entry Points

Key Android entry classes:

- `ActivityPictureViewer`: main screen for file selection, history, and viewing
- `ActivityTools`: tools screen
- `ActivityWebSocketDashboard`: dashboard screen
- `ActivityFileTransferStats`: file transfer detail screen
- `WebSocketService`: LAN file transfer service
- `WebHttpRouter`: HTTP routing and API handling

## Current Scope

- Focused on **file viewing + LAN file transfer**
- Chat features have been removed
- Rust-related code has been removed
- The LAN web service uses `HTTP`, not `HTTPS`

## Known Issues

- Large `.txt` files above `5 MB` may fail to open and can currently cause a crash. This is a known issue and has not been fixed yet.
