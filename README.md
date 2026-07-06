# PGM Image Viewer

An Android app that selects and opens many common file types from local storage.

- PGM files open in-app and support pinch zoom.
- Common image files such as `.jpg`, `.jpeg`, `.png`, `.bmp`, `.gif`, `.webp`, `.heic`, and `.heif` open in-app.
- Text-like files such as `.txt`, `.json`, `.xml`, `.yaml`, `.yml`, `.java`, `.swift`, `.kt`, `.md`, `.csv`, `.log`, `.properties`, `.gradle`, `.html`, `.css`, and `.js` open in-app as plain text.
- `.docx` files are read in-app with Apache POI and shown as extracted text content.
- If a provider gives an incorrect MIME type for a `.docx` file, the app also probes the file content and still tries to open it as DOCX text.
- Common video files such as `.mp4`, `.m4v`, `.mov`, `.mkv`, `.webm`, `.avi`, `.3gp`, `.mpeg`, and `.mpg` play in-app.
- PDF, Word, Excel, PowerPoint, and similar document files are forwarded to an available external viewer app on the device.
- If no suitable external app exists for a file type, the app shows the toast message `No app is available to open this file type.`.
- Heavy in-app parsing now runs on a background thread so multi-megabyte files do not block the UI thread while opening.
- Very large text and DOCX previews are truncated in-app to keep the page responsive.
- The app creates a root-level folder using the app name, then writes logs under `<AppName>/log/YYYY-MM-DD/HH-mm__YYYY-MM-DD.txt`.
- Recent file history is also persisted under `<AppName>/history/history-records.json`.
- Root-level writing depends on Android all-files access. If that permission is not granted, the app temporarily falls back to an app-private directory instead.
- Code-like text files such as JSON, XML, Java, Swift, JS, TS, YAML, and Markdown now use in-app syntax coloring for a cleaner source view.
- Markdown files also provide a preview toggle so users can switch between raw source and rendered preview.
- The top title area shows the currently displayed file name and its location path.
- When a text-like file is open, a search bar appears above the preview area with previous/next match navigation and highlight.
- A history panel can be shown or hidden from the bottom button. The panel is attached to the outermost layer of the screen and overlays the page without shrinking the main file display.
- The history list keeps file name and path records so the user can reopen files quickly.
- Tapping a history entry hides the history panel immediately before reopening the selected file.
- If a file in history has been deleted, the record is kept first and the app asks whether to remove that history entry.
