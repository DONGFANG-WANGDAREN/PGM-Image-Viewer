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
