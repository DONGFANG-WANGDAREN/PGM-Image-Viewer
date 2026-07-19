package com.DONGFANG_WANGDAREN.Station_RX.ui.activity;


import com.DONGFANG_WANGDAREN.Station_RX.R;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;
import com.DONGFANG_WANGDAREN.Station_RX.app.AppLogger;
import com.DONGFANG_WANGDAREN.Station_RX.reader.ParserPicturePgm;
import com.DONGFANG_WANGDAREN.Station_RX.reader.ReaderDocumentDocx;
import com.DONGFANG_WANGDAREN.Station_RX.reader.ReaderTableExcel;
import com.DONGFANG_WANGDAREN.Station_RX.reader.ReaderTextPlain;
import com.DONGFANG_WANGDAREN.Station_RX.storage.AppStoragePaths;
import com.DONGFANG_WANGDAREN.Station_RX.ui.view.ViewPictureZoom;
import com.DONGFANG_WANGDAREN.Station_RX.ui.view.ViewSeekBarVertical;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.google.android.material.button.MaterialButton;
import com.rajat.pdfviewer.PdfViewerActivity;
import com.rajat.pdfviewer.util.CacheStrategy;
import com.rajat.pdfviewer.util.saveTo;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class ActivityPictureViewer extends AppCompatActivity {

    private static final String TAG = "ActivityPictureViewer";
    private static final String OPEN_MODE_PGM = "pgm";
    private static final String OPEN_MODE_TEXT = "text";
    private static final String OPEN_MODE_DOCX = "docx";
    private static final String OPEN_MODE_IMAGE = "image";
    private static final String OPEN_MODE_VIDEO = "video";
    private static final String OPEN_MODE_AUDIO = "audio";
    private static final String OPEN_MODE_PDF = "pdf";
    private static final String OPEN_MODE_SPREADSHEET = "spreadsheet";
    private static final String OPEN_MODE_INSTALLER_APK = "installer_apk";
    private static final String OPEN_MODE_INSTALLER_BLOCKED = "installer_blocked";
    private static final String OPEN_MODE_EXTERNAL = "external";
    private static final String OPEN_MODE_UNSUPPORTED = "unsupported";
    private static final String JSON_KEY_URI = "uri";
    private static final String JSON_KEY_FILE_NAME = "file_name";
    private static final String JSON_KEY_FILE_PATH = "file_path";
    private static final String JSON_KEY_FILE_SIZE = "file_size";
    private static final int TEXT_QUICK_SCROLL_MAX = 1000;

    private ActivityResultLauncher<String[]> launcherOpenDocument;
    private ActivityResultLauncher<Intent> launcherManageAllFilesAccess;
    private MaterialButton buttonOpenPgm;
    private MaterialButton buttonOpenWebSocket;
    private ViewPictureZoom viewPictureZoom;
    private View layoutTextSearch;
    private EditText editTextSearch;
    private TextView textViewSearchCount;
    private MaterialButton buttonSearchPrevious;
    private MaterialButton buttonSearchNext;
    private View layoutTextActions;
    private MaterialButton buttonToggleMarkdownPreview;
    private MaterialButton buttonPrettyPrint;
    private MaterialButton buttonTextZoomIn;
    private MaterialButton buttonTextZoomOut;
    private ScrollView scrollViewText;
    private View layoutTextQuickScroll;
    private ViewSeekBarVertical seekBarTextQuickScroll;
    private TextView textViewFileContent;
    private TextView textViewTextScrollPosition;
    private WebView webViewMarkdownPreview;
    private PlayerView playerViewFile;
    private LinearLayout layoutEmptyState;
    private TextView textViewEmptyTitle;
    private TextView textViewEmptyMessage;
    private TextView textViewCurrentFileTitle;
    private TextView textViewCurrentFilePath;
    private View layoutHistoryPanel;
    private LinearLayout layoutHistoryList;
    private TextView textViewHistoryEmpty;
    private View viewHistoryScrim;
    private MaterialButton buttonOpenHistory;
    private LayoutInflater layoutInflater;
    @Nullable
    private OnBackPressedCallback historyBackCallback;
    @Nullable
    private ExoPlayer playerMedia;
    private final ArrayList<HistoryRecord> historyRecords = new ArrayList<>();
    private final ArrayList<Integer> searchMatchStarts = new ArrayList<>();
    private final ArrayList<Integer> searchMatchEnds = new ArrayList<>();
    private final ExecutorService executorOpenFile = Executors.newSingleThreadExecutor();
    private String currentTextContent = "";
    private String currentOriginalTextContent = "";
    private int currentSearchIndex = -1;
    private int currentOpenRequestId = 0;
    private String currentFileName = "";
    @Nullable
    private Uri currentOpenedFileUri;
    private String currentOpenMode = OPEN_MODE_UNSUPPORTED;
    private boolean currentFileIsMarkdown;
    private boolean currentTextLargeMode;
    private boolean currentTextSupportsPrettyPrint;
    private boolean currentTextPrettyPrinted;
    private boolean markdownPreviewMode;
    private boolean updatingTextQuickScrollFromCode;
    private boolean startupIntentHandled;
    private float currentTextSizeSp = AppConfig.get().getDefaultTextSizeSp();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_viewer);
        AppLogger.i(TAG, "onCreate. intentAction=" + String.valueOf(getIntent().getAction()));

        historyBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setHistoryPanelVisible(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, historyBackCallback);

        launcherOpenDocument = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        return;
                    }
                    tryTakePersistableReadPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    handleSelectedFile(uri, null, null, true, false);
                }
        );
        launcherManageAllFilesAccess = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (hasAllFilesAccessPermission()) {
                        continueStartupAfterStoragePermission();
                        return;
                    }
                    showToast(R.string.toast_storage_permission_denied);
                    finish();
                }
        );

        buttonOpenPgm = findViewById(R.id.button_open_pgm);
        buttonOpenWebSocket = findViewById(R.id.button_open_websocket);
        viewPictureZoom = findViewById(R.id.view_picture_zoom);
        layoutTextSearch = findViewById(R.id.layout_text_search);
        editTextSearch = findViewById(R.id.edit_text_search);
        textViewSearchCount = findViewById(R.id.text_view_search_count);
        buttonSearchPrevious = findViewById(R.id.button_search_previous);
        buttonSearchNext = findViewById(R.id.button_search_next);
        layoutTextActions = findViewById(R.id.layout_text_actions);
        buttonToggleMarkdownPreview = findViewById(R.id.button_toggle_markdown_preview);
        buttonPrettyPrint = findViewById(R.id.button_pretty_print);
        buttonTextZoomIn = findViewById(R.id.button_text_zoom_in);
        buttonTextZoomOut = findViewById(R.id.button_text_zoom_out);
        scrollViewText = findViewById(R.id.scroll_view_text);
        layoutTextQuickScroll = findViewById(R.id.layout_text_quick_scroll);
        seekBarTextQuickScroll = findViewById(R.id.seek_bar_text_quick_scroll);
        textViewFileContent = findViewById(R.id.text_view_file_content);
        textViewTextScrollPosition = findViewById(R.id.text_view_text_scroll_position);
        webViewMarkdownPreview = findViewById(R.id.web_view_markdown_preview);
        playerViewFile = findViewById(R.id.player_view_file);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        textViewEmptyTitle = findViewById(R.id.text_view_empty_title);
        textViewEmptyMessage = findViewById(R.id.text_view_empty_message);
        textViewCurrentFileTitle = findViewById(R.id.text_view_current_file_title);
        textViewCurrentFilePath = findViewById(R.id.text_view_current_file_path);
        layoutHistoryPanel = findViewById(R.id.layout_history_panel);
        layoutHistoryList = findViewById(R.id.layout_history_list);
        textViewHistoryEmpty = findViewById(R.id.text_view_history_empty);
        viewHistoryScrim = findViewById(R.id.view_history_scrim);
        buttonOpenHistory = findViewById(R.id.button_open_history);
        layoutInflater = LayoutInflater.from(this);

        WebSettings webSettings = webViewMarkdownPreview.getSettings();
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptEnabled(false);

        playerViewFile.setUseController(true);
        playerViewFile.setControllerAutoShow(true);
        playerViewFile.setShowNextButton(false);
        playerViewFile.setShowPreviousButton(false);
        playerViewFile.setShowFastForwardButton(true);
        playerViewFile.setShowRewindButton(true);

        buttonOpenPgm.setOnClickListener(view -> launcherOpenDocument.launch(new String[]{"*/*"}));
        buttonOpenWebSocket.setOnClickListener(view -> {
            setHistoryPanelVisible(false);
            startActivity(new Intent(this, ActivityTools.class));
        });
        buttonOpenHistory.setOnClickListener(view -> setHistoryPanelVisible(true));
        viewHistoryScrim.setOnClickListener(view -> setHistoryPanelVisible(false));
        buttonSearchPrevious.setOnClickListener(view -> moveToSearchMatch(-1));
        buttonSearchNext.setOnClickListener(view -> moveToSearchMatch(1));
        buttonToggleMarkdownPreview.setOnClickListener(view -> {
            markdownPreviewMode = !markdownPreviewMode;
            applyCurrentTextPresentation();
        });
        buttonPrettyPrint.setOnClickListener(view -> togglePrettyPrint());
        buttonTextZoomIn.setOnClickListener(view -> adjustTextSize(2f));
        buttonTextZoomOut.setOnClickListener(view -> adjustTextSize(-2f));
        scrollViewText.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> updateTextQuickScrollState());
        seekBarTextQuickScroll.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingTextQuickScrollFromCode) {
                    return;
                }
                scrollTextToQuickScrollProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateTextQuickScrollState();
            }
        });
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshSearchResults();
            }
        });

        loadHistoryRecords();
        renderHistoryRecords();
        updateCurrentFileInfo(null, null);
        setHistoryPanelVisible(false);
        setTextSearchVisible(false);
        requestAllFilesAccessPermissionIfNeeded();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AppLogger.i(TAG, "onNewIntent. action=" + String.valueOf(intent.getAction()));
        startupIntentHandled = false;
        requestAllFilesAccessPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAllFilesAccessPermission() && !startupIntentHandled) {
            continueStartupAfterStoragePermission();
        }
    }

    private void handleIncomingIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return;
        }
        AppLogger.i(TAG, "Handle VIEW intent. uri=" + uri);
        tryTakePersistableReadPermission(uri, intent.getFlags());
        handleSelectedFile(uri, null, null, true, false);
    }

    private void requestAllFilesAccessPermissionIfNeeded() {
        if (hasAllFilesAccessPermission()) {
            continueStartupAfterStoragePermission();
            return;
        }
        showToast(R.string.toast_storage_permission_required);
        launcherManageAllFilesAccess.launch(buildManageAllFilesAccessIntent());
    }

    private void continueStartupAfterStoragePermission() {
        if (startupIntentHandled) {
            return;
        }
        AppLogger.refreshStorageLocation(this);
        startupIntentHandled = true;
        handleIncomingIntent(getIntent());
    }

    private boolean hasAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        return Environment.isExternalStorageManager();
    }

    @NonNull
    private Intent buildManageAllFilesAccessIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return new Intent();
        }
        Intent appSpecificIntent = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        if (appSpecificIntent.resolveActivity(getPackageManager()) != null) {
            return appSpecificIntent;
        }
        return new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
    }

    private void handleSelectedFile(
            @Nullable Uri uri,
            @Nullable String storedFileName,
            @Nullable String storedFilePath,
            boolean saveToHistory,
            boolean promptDeleteOnMissing
    ) {
        if (uri == null) {
            return;
        }

        String fileName = storedFileName;
        if (isNullOrEmpty(fileName)) {
            fileName = getDisplayName(uri);
        }
        if (isNullOrEmpty(fileName)) {
            fileName = getString(R.string.current_file_unknown_name);
        }

        String filePath = storedFilePath;
        if (isNullOrEmpty(filePath)) {
            filePath = resolveDisplayPath(uri);
        }

        String mimeType = getNormalizedMimeType(uri);
        String openMode = resolveOpenMode(fileName, mimeType);
        if (OPEN_MODE_UNSUPPORTED.equals(openMode) && shouldProbeDocx(fileName, mimeType)) {
            openMode = detectOpenModeFromContent(uri, openMode);
        }
        AppLogger.i(TAG, "Open requested. name=" + fileName + ", mime=" + String.valueOf(mimeType) + ", mode=" + openMode + ", uri=" + uri);

        if (OPEN_MODE_UNSUPPORTED.equals(openMode)) {
            showToast(R.string.toast_only_supported_file);
            return;
        }

        long fileSize = getFileSize(uri);

        try {
            verifyFileExists(uri);
            if (OPEN_MODE_INSTALLER_APK.equals(openMode)) {
                updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
                updateCurrentFileInfo(fileName, filePath, fileSize);
                if (saveToHistory) {
                    recordHistory(uri.toString(), fileName, filePath, fileSize);
                }
                showApkInstallDialog(uri);
                return;
            }
            if (OPEN_MODE_INSTALLER_BLOCKED.equals(openMode)) {
                updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
                showToast(R.string.toast_installer_unsupported);
                return;
            }
            if (shouldOfferDocumentChoice(openMode)) {
                if (hasExternalViewer(uri, mimeType)) {
                    showDocumentOpenChoiceDialog(uri, mimeType, fileName, filePath, openMode, saveToHistory, promptDeleteOnMissing, fileSize);
                    return;
                }
                performInAppOpen(uri, fileName, filePath, openMode, saveToHistory, promptDeleteOnMissing, fileSize);
                return;
            }
            if (OPEN_MODE_EXTERNAL.equals(openMode)) {
                if (!openWithExternalApp(uri, mimeType)) {
                    showToast(R.string.toast_no_external_viewer);
                    return;
                }
                updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
                updateCurrentFileInfo(fileName, filePath, fileSize);
                if (saveToHistory) {
                    recordHistory(uri.toString(), fileName, filePath, fileSize);
                }
                return;
            }
        } catch (FileNotFoundException exception) {
            AppLogger.e(TAG, "File missing. uri=" + uri, exception);
            if (promptDeleteOnMissing) {
                showMissingHistoryDialog(uri.toString(), fileName);
            } else {
                showToast(R.string.toast_open_failed);
            }
            return;
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            AppLogger.e(TAG, "Pre-open check failed. uri=" + uri, exception);
            showToast(R.string.toast_open_failed);
            return;
        }

        openFileInBackground(uri, fileName, filePath, openMode, saveToHistory, promptDeleteOnMissing, fileSize);
    }

    private void performInAppOpen(
            @NonNull Uri uri,
            @NonNull String fileName,
            @NonNull String filePath,
            @NonNull String openMode,
            boolean saveToHistory,
            boolean promptDeleteOnMissing,
            long fileSize
    ) {
        if (OPEN_MODE_PDF.equals(openMode)) {
            try {
                startActivity(
                        PdfViewerActivity.Companion.launchPdfFromPath(
                                this,
                                uri.toString(),
                                fileName,
                                saveTo.ASK_EVERYTIME,
                                false,
                                true,
                                null,
                                CacheStrategy.MAXIMIZE_PERFORMANCE
                        )
                );
                updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
                updateCurrentFileInfo(fileName, filePath, fileSize);
                if (saveToHistory) {
                    recordHistory(uri.toString(), fileName, filePath, fileSize);
                }
            } catch (ActivityNotFoundException exception) {
                AppLogger.e(TAG, "Failed to launch PDF viewer activity.", exception);
                showToast(R.string.toast_open_failed);
            }
            return;
        }
        openFileInBackground(uri, fileName, filePath, openMode, saveToHistory, promptDeleteOnMissing, fileSize);
    }

    private void showDocumentOpenChoiceDialog(
            @NonNull Uri uri,
            @Nullable String mimeType,
            @NonNull String fileName,
            @NonNull String filePath,
            @NonNull String openMode,
            boolean saveToHistory,
            boolean promptDeleteOnMissing,
            long fileSize
    ) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_document_open_title)
                .setMessage(R.string.dialog_document_open_message)
                .setPositiveButton(R.string.dialog_open_in_app, (dialog, which) ->
                        performInAppOpen(uri, fileName, filePath, openMode, saveToHistory, promptDeleteOnMissing, fileSize))
                .setNegativeButton(R.string.dialog_open_in_system, (dialog, which) -> {
                    if (!openWithExternalApp(uri, mimeType)) {
                        showToast(R.string.toast_no_external_viewer);
                        return;
                    }
                    updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
                    updateCurrentFileInfo(fileName, filePath, fileSize);
                    if (saveToHistory) {
                        recordHistory(uri.toString(), fileName, filePath, fileSize);
                    }
                })
                .show();
    }

    private void showApkInstallDialog(@NonNull Uri uri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_apk_install_title)
                .setMessage(R.string.dialog_apk_install_message)
                .setPositiveButton(R.string.dialog_yes, (dialog, which) -> launchApkInstaller(uri))
                .setNegativeButton(R.string.dialog_no, null)
                .show();
    }

    private void launchApkInstaller(@NonNull Uri uri) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            AppLogger.e(TAG, "Failed to launch APK installer.", exception);
            showToast(R.string.toast_install_apk_failed);
        }
    }

    @NonNull
    static String resolveOpenMode(@Nullable String fileName, @Nullable String mimeType) {
        String openModeFromName = resolveOpenModeFromName(fileName);
        if (openModeFromName != null) {
            return openModeFromName;
        }
        String openModeFromMimeType = resolveOpenModeFromMimeType(mimeType);
        if (openModeFromMimeType != null) {
            return openModeFromMimeType;
        }
        return OPEN_MODE_UNSUPPORTED;
    }

    @Nullable
    private static String resolveOpenModeFromName(@Nullable String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalizedFileName = fileName.toLowerCase(Locale.US);
        if (matchesExtension(normalizedFileName, ".pgm", ".pmg")) {
            return OPEN_MODE_PGM;
        }
        if (matchesExtension(normalizedFileName, ".docx")) {
            return OPEN_MODE_DOCX;
        }
        if (matchesExtension(normalizedFileName, ".pdf")) {
            return OPEN_MODE_PDF;
        }
        if (matchesExtension(normalizedFileName, ".xls", ".xlsx")) {
            return OPEN_MODE_SPREADSHEET;
        }
        if (matchesExtension(
                normalizedFileName,
                ".txt", ".json", ".xml", ".yaml", ".yml", ".java", ".swift", ".kt", ".kts",
                ".md", ".markdown", ".csv", ".log", ".ini", ".cfg", ".conf", ".properties",
                ".gradle", ".css", ".js", ".ts", ".html", ".htm"
        )) {
            return OPEN_MODE_TEXT;
        }
        if (matchesExtension(
                normalizedFileName,
                ".jpg", ".jpeg", ".png", ".bmp", ".webp", ".gif", ".heic", ".heif"
        )) {
            return OPEN_MODE_IMAGE;
        }
        if (matchesExtension(
                normalizedFileName,
                ".mp4", ".m4v", ".mov", ".mkv", ".webm", ".avi", ".3gp", ".mpeg", ".mpg"
        )) {
            return OPEN_MODE_VIDEO;
        }
        if (matchesExtension(
                normalizedFileName,
                ".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".amr", ".wma"
        )) {
            return OPEN_MODE_AUDIO;
        }
        if (matchesExtension(normalizedFileName, ".apk")) {
            return OPEN_MODE_INSTALLER_APK;
        }
        if (matchesExtension(normalizedFileName, ".ipa", ".exe", ".msi", ".dmg", ".pkg", ".deb", ".rpm")) {
            return OPEN_MODE_INSTALLER_BLOCKED;
        }
        if (matchesExtension(
                normalizedFileName,
                ".doc", ".ppt", ".pptx", ".wps", ".odt", ".ods", ".odp", ".rtf"
        )) {
            return OPEN_MODE_EXTERNAL;
        }
        return null;
    }

    @Nullable
    private static String resolveOpenModeFromMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalizedMimeType = mimeType.toLowerCase(Locale.US);
        if ("image/x-portable-graymap".equals(normalizedMimeType)
                || "application/x-portable-graymap".equals(normalizedMimeType)
                || "image/x-portable-anymap".equals(normalizedMimeType)) {
            return OPEN_MODE_PGM;
        }
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(normalizedMimeType)) {
            return OPEN_MODE_DOCX;
        }
        if ("application/pdf".equals(normalizedMimeType)) {
            return OPEN_MODE_PDF;
        }
        if ("application/vnd.ms-excel".equals(normalizedMimeType)
                || "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(normalizedMimeType)) {
            return OPEN_MODE_SPREADSHEET;
        }
        if (normalizedMimeType.startsWith("text/")
                || "application/json".equals(normalizedMimeType)
                || "application/xml".equals(normalizedMimeType)
                || "text/xml".equals(normalizedMimeType)
                || "application/x-yaml".equals(normalizedMimeType)
                || "application/yaml".equals(normalizedMimeType)
                || "text/yaml".equals(normalizedMimeType)
                || "text/x-yaml".equals(normalizedMimeType)
                || "application/javascript".equals(normalizedMimeType)
                || "text/javascript".equals(normalizedMimeType)) {
            return OPEN_MODE_TEXT;
        }
        if (normalizedMimeType.startsWith("image/")) {
            return OPEN_MODE_IMAGE;
        }
        if (normalizedMimeType.startsWith("video/")) {
            return OPEN_MODE_VIDEO;
        }
        if (normalizedMimeType.startsWith("audio/")
                || "application/ogg".equals(normalizedMimeType)) {
            return OPEN_MODE_AUDIO;
        }
        if ("application/vnd.android.package-archive".equals(normalizedMimeType)) {
            return OPEN_MODE_INSTALLER_APK;
        }
        if ("application/x-msdownload".equals(normalizedMimeType)
                || "application/x-apple-diskimage".equals(normalizedMimeType)) {
            return OPEN_MODE_INSTALLER_BLOCKED;
        }
        if ("application/msword".equals(normalizedMimeType)
                || "application/vnd.ms-powerpoint".equals(normalizedMimeType)
                || "application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(normalizedMimeType)
                || "application/rtf".equals(normalizedMimeType)
                || normalizedMimeType.startsWith("application/vnd.")) {
            return OPEN_MODE_EXTERNAL;
        }
        return null;
    }

    private boolean shouldOfferDocumentChoice(@NonNull String openMode) {
        return OPEN_MODE_PDF.equals(openMode)
                || OPEN_MODE_DOCX.equals(openMode)
                || OPEN_MODE_SPREADSHEET.equals(openMode);
    }

    private void showBitmap(@NonNull Bitmap bitmap) {
        stopMediaPlayback();
        setTextSearchVisible(false);
        setTextActionsVisible(false);
        textViewFileContent.setText(null);
        scrollViewText.scrollTo(0, 0);
        scrollViewText.setVisibility(View.GONE);
        webViewMarkdownPreview.setVisibility(View.GONE);
        playerViewFile.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        viewPictureZoom.setVisibility(View.VISIBLE);
        viewPictureZoom.setBitmap(bitmap);
        setTextQuickScrollVisible(false);
    }

    private void showMediaFile(@NonNull Uri uri) {
        setTextSearchVisible(false);
        setTextActionsVisible(false);
        viewPictureZoom.setImageDrawable(null);
        viewPictureZoom.setVisibility(View.GONE);
        textViewFileContent.setText(null);
        scrollViewText.setVisibility(View.GONE);
        webViewMarkdownPreview.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        playerViewFile.setVisibility(View.VISIBLE);
        ExoPlayer exoPlayer = ensureMediaPlayer();
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        exoPlayer.setMediaItem(MediaItem.fromUri(uri));
        exoPlayer.prepare();
        exoPlayer.play();
        setTextQuickScrollVisible(false);
    }

    @NonNull
    private ExoPlayer ensureMediaPlayer() {
        if (playerMedia != null) {
            return playerMedia;
        }
        playerMedia = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10_000L)
                .setSeekForwardIncrementMs(10_000L)
                .build();
        playerMedia.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                AppLogger.e(TAG, "Media playback error.", error);
                showToast(R.string.toast_open_failed);
            }
        });
        playerViewFile.setPlayer(playerMedia);
        return playerMedia;
    }

    private void stopMediaPlayback() {
        if (playerMedia == null) {
            return;
        }
        playerMedia.pause();
        playerMedia.stop();
        playerMedia.clearMediaItems();
    }

    private void releaseMediaPlayer() {
        if (playerMedia == null) {
            return;
        }
        playerViewFile.setPlayer(null);
        playerMedia.release();
        playerMedia = null;
    }

    private void showTextContent(@NonNull String textContent, boolean truncated) {
        stopMediaPlayback();
        currentOriginalTextContent = textContent;
        currentTextContent = currentOriginalTextContent;
        currentFileIsMarkdown = isMarkdownFileName(currentFileName);
        currentTextLargeMode = isLargeTextContent(currentTextContent);
        currentTextSupportsPrettyPrint = isStructuredTextFileName(currentFileName);
        currentTextPrettyPrinted = false;
        markdownPreviewMode = false;
        currentTextSizeSp = AppConfig.get().getDefaultTextSizeSp();
        viewPictureZoom.setImageDrawable(null);
        viewPictureZoom.setVisibility(View.GONE);
        playerViewFile.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        scrollViewText.scrollTo(0, 0);
        searchMatchStarts.clear();
        searchMatchEnds.clear();
        currentSearchIndex = -1;
        editTextSearch.setText(null);
        textViewSearchCount.setText(R.string.search_no_result);
        buttonSearchPrevious.setEnabled(false);
        buttonSearchNext.setEnabled(false);
        layoutTextSearch.setVisibility(currentTextLargeMode ? View.GONE : View.VISIBLE);
        layoutTextActions.setVisibility(currentTextLargeMode ? View.GONE : View.VISIBLE);
        textViewFileContent.setTextIsSelectable(!currentTextLargeMode);
        updateTextActionButtons();
        applyCurrentTextZoom();
        applyCurrentTextPresentation();
        scheduleTextQuickScrollUpdate();
    }

    @Nullable
    private String getDisplayName(@NonNull Uri uri) {
        Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        return cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return uri.getLastPathSegment();
    }

    private void recordHistory(@NonNull String uriString, @NonNull String fileName, @NonNull String filePath) {
        recordHistory(uriString, fileName, filePath, getFileSize(Uri.parse(uriString)));
    }

    private void recordHistory(@NonNull String uriString, @NonNull String fileName, @NonNull String filePath, long fileSize) {
        for (int index = 0; index < historyRecords.size(); index++) {
            if (uriString.equals(historyRecords.get(index).uriString)) {
                historyRecords.remove(index);
                break;
            }
        }
        historyRecords.add(0, new HistoryRecord(uriString, fileName, filePath, fileSize));
        while (historyRecords.size() > AppConfig.get().getMaxHistoryRecords()) {
            historyRecords.remove(historyRecords.size() - 1);
        }
        saveHistoryRecords();
        renderHistoryRecords();
        AppLogger.d(TAG, "History recorded. name=" + fileName + ", path=" + filePath + ", size=" + fileSize);
    }

    private void loadHistoryRecords() {
        historyRecords.clear();
        String rawHistory = readHistoryFromFile();
        if (isNullOrEmpty(rawHistory)) {
            AppLogger.i(TAG, "No history file found.");
            return;
        }
        try {
            JSONArray jsonArray = new JSONArray(rawHistory);
            for (int index = 0; index < jsonArray.length(); index++) {
                JSONObject jsonObject = jsonArray.optJSONObject(index);
                if (jsonObject == null) {
                    continue;
                }
                String uriString = jsonObject.optString(JSON_KEY_URI, null);
                String fileName = jsonObject.optString(JSON_KEY_FILE_NAME, null);
                String filePath = jsonObject.optString(JSON_KEY_FILE_PATH, null);
                long fileSize = jsonObject.optLong(JSON_KEY_FILE_SIZE, -1);
                if (isNullOrEmpty(uriString) || isNullOrEmpty(fileName) || isNullOrEmpty(filePath)) {
                    continue;
                }
                historyRecords.add(new HistoryRecord(uriString, fileName, filePath, fileSize));
            }
            AppLogger.i(TAG, "History loaded. count=" + historyRecords.size());
        } catch (JSONException exception) {
            historyRecords.clear();
            AppLogger.e(TAG, "Failed to parse history file.", exception);
        }
    }

    private void saveHistoryRecords() {
        JSONArray jsonArray = new JSONArray();
        for (HistoryRecord historyRecord : historyRecords) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(JSON_KEY_URI, historyRecord.uriString);
                jsonObject.put(JSON_KEY_FILE_NAME, historyRecord.fileName);
                jsonObject.put(JSON_KEY_FILE_PATH, historyRecord.filePath);
                jsonObject.put(JSON_KEY_FILE_SIZE, historyRecord.fileSize);
                jsonArray.put(jsonObject);
            } catch (JSONException exception) {
                AppLogger.e(TAG, "Skip malformed history entry while saving.", exception);
            }
        }
        writeHistoryToFile(jsonArray.toString());
    }

    private void renderHistoryRecords() {
        layoutHistoryList.removeAllViews();
        textViewHistoryEmpty.setVisibility(historyRecords.isEmpty() ? View.VISIBLE : View.GONE);
        for (HistoryRecord historyRecord : historyRecords) {
            View itemView = layoutInflater.inflate(R.layout.item_history_file, layoutHistoryList, false);
            TextView textViewHistoryFileName = itemView.findViewById(R.id.text_view_history_file_name);
            TextView textViewHistoryFileSize = itemView.findViewById(R.id.text_view_history_file_size);
            TextView textViewHistoryFilePath = itemView.findViewById(R.id.text_view_history_file_path);
            textViewHistoryFileName.setText(historyRecord.fileName);
            textViewHistoryFileSize.setText(formatFileSize(historyRecord.fileSize));
            textViewHistoryFilePath.setText(historyRecord.filePath);
            itemView.setOnClickListener(view -> openHistoryRecord(historyRecord));
            layoutHistoryList.addView(itemView);
        }
    }

    private void openHistoryRecord(@NonNull HistoryRecord historyRecord) {
        setHistoryPanelVisible(false);
        handleSelectedFile(Uri.parse(historyRecord.uriString), historyRecord.fileName, historyRecord.filePath, true, true);
    }

    private void showMissingHistoryDialog(@NonNull String uriString, @NonNull String fileName) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_missing_file_title)
                .setMessage(getString(R.string.dialog_missing_file_message, fileName))
                .setPositiveButton(R.string.dialog_delete_record, (dialogInterface, which) -> {
                    removeHistoryRecord(uriString);
                    dialogInterface.dismiss();
                })
                .setNegativeButton(R.string.dialog_keep_record, (dialogInterface, which) -> dialogInterface.dismiss())
                .show();
    }

    private void removeHistoryRecord(@NonNull String uriString) {
        for (int index = 0; index < historyRecords.size(); index++) {
            if (uriString.equals(historyRecords.get(index).uriString)) {
                historyRecords.remove(index);
                saveHistoryRecords();
                renderHistoryRecords();
                break;
            }
        }
    }

    private void updateCurrentFileInfo(@Nullable String fileName, @Nullable String filePath) {
        updateCurrentFileInfo(fileName, filePath, -1);
    }

    private void updateCurrentFileInfo(@Nullable String fileName, @Nullable String filePath, long fileSize) {
        currentFileName = fileName == null ? "" : fileName;
        if (isNullOrEmpty(fileName)) {
            textViewCurrentFileTitle.setText(R.string.current_file_default_title);
        } else {
            textViewCurrentFileTitle.setText(fileName);
        }
        if (isNullOrEmpty(filePath)) {
            textViewCurrentFilePath.setText(R.string.current_file_default_path);
            return;
        }
        String sizeText = formatFileSize(fileSize);
        if (isNullOrEmpty(sizeText)) {
            textViewCurrentFilePath.setText(filePath);
        } else {
            textViewCurrentFilePath.setText(filePath + " · " + sizeText);
        }
    }

    private long getFileSize(@NonNull Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (!isNullOrEmpty(path)) {
                return new File(path).length();
            }
            return -1;
        }
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            AppLogger.e(TAG, "Failed to query file size. uri=" + uri, exception);
        }
        return -1;
    }

    @NonNull
    private String formatFileSize(long size) {
        if (size < 0) {
            return "";
        }
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.2f KB", size / 1024.0);
        }
        if (size < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", size / (1024.0 * 1024.0));
        }
        return String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private void updateCurrentOpenTarget(@Nullable Uri uri, @NonNull String openMode) {
        currentOpenedFileUri = uri;
        currentOpenMode = openMode;
    }

    private void scheduleTextQuickScrollUpdate() {
        scrollViewText.post(this::updateTextQuickScrollState);
    }

    private void updateTextQuickScrollState() {
        if (scrollViewText.getVisibility() != View.VISIBLE || webViewMarkdownPreview.getVisibility() == View.VISIBLE) {
            setTextQuickScrollVisible(false);
            return;
        }
        int scrollRange = getTextScrollRange();
        if (scrollRange <= 0) {
            setTextQuickScrollVisible(false);
            return;
        }
        setTextQuickScrollVisible(true);
        int progress = Math.round((scrollViewText.getScrollY() * 1f / scrollRange) * TEXT_QUICK_SCROLL_MAX);
        int percent = Math.round((scrollViewText.getScrollY() * 100f) / scrollRange);
        updatingTextQuickScrollFromCode = true;
        seekBarTextQuickScroll.setMax(TEXT_QUICK_SCROLL_MAX);
        seekBarTextQuickScroll.setProgress(progress);
        updatingTextQuickScrollFromCode = false;
        textViewTextScrollPosition.setText(getString(R.string.text_scroll_position, percent));
        updateTextQuickScrollIndicatorPosition(progress);
    }

    private void scrollTextToQuickScrollProgress(int progress) {
        int scrollRange = getTextScrollRange();
        if (scrollRange <= 0) {
            return;
        }
        int targetY = Math.round((progress * 1f / TEXT_QUICK_SCROLL_MAX) * scrollRange);
        scrollViewText.scrollTo(0, targetY);
        int percent = Math.round((targetY * 100f) / scrollRange);
        textViewTextScrollPosition.setText(getString(R.string.text_scroll_position, percent));
        updateTextQuickScrollIndicatorPosition(progress);
    }

    private int getTextScrollRange() {
        if (textViewFileContent.getLayout() == null) {
            return 0;
        }
        return Math.max(0, textViewFileContent.getHeight() - scrollViewText.getHeight());
    }

    private void setTextQuickScrollVisible(boolean visible) {
        layoutTextQuickScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            updatingTextQuickScrollFromCode = true;
            seekBarTextQuickScroll.setProgress(0);
            updatingTextQuickScrollFromCode = false;
            textViewTextScrollPosition.setText(R.string.text_scroll_position_initial);
            textViewTextScrollPosition.setTranslationY(0f);
        }
    }

    private void updateTextQuickScrollIndicatorPosition(int progress) {
        layoutTextQuickScroll.post(() -> {
            int containerHeight = layoutTextQuickScroll.getHeight();
            int seekBarTop = seekBarTextQuickScroll.getTop();
            int seekBarHeight = seekBarTextQuickScroll.getHeight();
            int indicatorHeight = textViewTextScrollPosition.getHeight();
            if (containerHeight <= 0 || seekBarHeight <= 0 || indicatorHeight <= 0) {
                return;
            }
            float progressRatio = TEXT_QUICK_SCROLL_MAX == 0 ? 0f : (progress * 1f / TEXT_QUICK_SCROLL_MAX);
            float thumbCenterY = seekBarTop + ((1f - progressRatio) * seekBarHeight);
            float targetY = thumbCenterY - (indicatorHeight / 2f);
            float clampedY = Math.max(0f, Math.min(targetY, containerHeight - indicatorHeight));
            textViewTextScrollPosition.setTranslationY(clampedY);
        });
    }

    private void setHistoryPanelVisible(boolean visible) {
        layoutHistoryPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        viewHistoryScrim.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (historyBackCallback != null) {
            historyBackCallback.setEnabled(visible);
        }
    }

    private void setTextActionsVisible(boolean visible) {
        layoutTextActions.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            markdownPreviewMode = false;
            currentTextSupportsPrettyPrint = false;
            currentTextPrettyPrinted = false;
        }
        updateTextActionButtons();
    }

    private void updateTextActionButtons() {
        if (layoutTextActions.getVisibility() != View.VISIBLE) {
            buttonToggleMarkdownPreview.setVisibility(View.GONE);
            buttonPrettyPrint.setVisibility(View.GONE);
            buttonTextZoomIn.setVisibility(View.GONE);
            buttonTextZoomOut.setVisibility(View.GONE);
            return;
        }
        buttonTextZoomIn.setVisibility(View.VISIBLE);
        buttonTextZoomOut.setVisibility(View.VISIBLE);
        buttonToggleMarkdownPreview.setVisibility(!currentTextLargeMode && currentFileIsMarkdown ? View.VISIBLE : View.GONE);
        buttonPrettyPrint.setVisibility(!currentTextLargeMode && currentTextSupportsPrettyPrint ? View.VISIBLE : View.GONE);
        buttonToggleMarkdownPreview.setText(markdownPreviewMode ? R.string.markdown_source : R.string.markdown_preview);
        buttonPrettyPrint.setText(currentTextPrettyPrinted ? R.string.text_pretty_raw : R.string.text_pretty_print);
    }

    private void setTextSearchVisible(boolean visible) {
        layoutTextSearch.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            currentTextContent = "";
            currentOriginalTextContent = "";
            currentFileIsMarkdown = false;
            currentTextLargeMode = false;
            currentTextSupportsPrettyPrint = false;
            currentTextPrettyPrinted = false;
            markdownPreviewMode = false;
            currentTextSizeSp = AppConfig.get().getDefaultTextSizeSp();
            searchMatchStarts.clear();
            searchMatchEnds.clear();
            currentSearchIndex = -1;
            editTextSearch.setText(null);
            textViewFileContent.setTextIsSelectable(true);
            textViewFileContent.setText(null);
            webViewMarkdownPreview.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webViewMarkdownPreview.setVisibility(View.GONE);
            scrollViewText.setVisibility(View.GONE);
            setTextQuickScrollVisible(false);
            textViewSearchCount.setText(R.string.search_no_result);
            buttonSearchPrevious.setEnabled(false);
            buttonSearchNext.setEnabled(false);
        }
    }

    private void openFileInBackground(
            @NonNull Uri uri,
            @NonNull String fileName,
            @NonNull String filePath,
            @NonNull String openMode,
            boolean saveToHistory,
            boolean promptDeleteOnMissing,
            long fileSize
    ) {
        int requestId = ++currentOpenRequestId;
        AppLogger.d(TAG, "Background open started. requestId=" + requestId + ", mode=" + openMode + ", file=" + fileName);
        if (OPEN_MODE_TEXT.equals(openMode)) {
            setOpenUiEnabled(false);
            updateCurrentOpenTarget(uri, openMode);
            updateCurrentFileInfo(fileName, filePath, fileSize);
            executorOpenFile.execute(() -> openTextFileWithPreview(uri, fileName, filePath, saveToHistory, promptDeleteOnMissing, requestId, fileSize));
            return;
        }
        showLoadingState();
        executorOpenFile.execute(() -> {
            try {
                OpenedFileContent openedFileContent = parseFileContent(uri, openMode, fileName);
                runOnUiThread(() -> {
                    if (!isActiveOpenRequest(requestId)) {
                        AppLogger.w(TAG, "Drop outdated open result. requestId=" + requestId + ", current=" + currentOpenRequestId);
                        return;
                    }
                    updateCurrentOpenTarget(uri, openMode);
                    updateCurrentFileInfo(fileName, filePath, fileSize);
                    applyOpenedFileContent(openedFileContent);
                    if (saveToHistory) {
                        recordHistory(uri.toString(), fileName, filePath, fileSize);
                    }
                    setOpenUiEnabled(true);
                });
            } catch (FileNotFoundException exception) {
                AppLogger.e(TAG, "Background open missing file. requestId=" + requestId + ", uri=" + uri, exception);
                runOnUiThread(() -> {
                    if (!isActiveOpenRequest(requestId)) {
                        return;
                    }
                    setOpenUiEnabled(true);
                    if (promptDeleteOnMissing) {
                        showMissingHistoryDialog(uri.toString(), fileName);
                    } else {
                        showToast(R.string.toast_open_failed);
                    }
                });
            } catch (IOException | IllegalArgumentException | SecurityException exception) {
                AppLogger.e(TAG, "Background open failed. requestId=" + requestId + ", mode=" + openMode + ", uri=" + uri, exception);
                runOnUiThread(() -> {
                    if (!isActiveOpenRequest(requestId)) {
                        return;
                    }
                    setOpenUiEnabled(true);
                    showToast(R.string.toast_open_failed);
                });
            }
        });
    }

    private void openTextFileWithPreview(
            @NonNull Uri uri,
            @NonNull String fileName,
            @NonNull String filePath,
            boolean saveToHistory,
            boolean promptDeleteOnMissing,
            int requestId,
            long fileSize
    ) {
        try (InputStream previewInputStream = getContentResolver().openInputStream(uri)) {
            if (previewInputStream == null) {
                throw new FileNotFoundException("Input stream is null.");
            }
            ReaderTextPlain.PreviewTextResult previewTextResult =
                    ReaderTextPlain.readUtf8Preview(previewInputStream, AppConfig.get().getTextPreviewInitialCharacters());
            runOnUiThread(() -> {
                if (!isActiveOpenRequest(requestId)) {
                    return;
                }
                showTextContent(previewTextResult.textContent, previewTextResult.truncated);
                if (saveToHistory) {
                    recordHistory(uri.toString(), fileName, filePath, fileSize);
                }
                setOpenUiEnabled(true);
            });
            if (!previewTextResult.truncated || !isActiveOpenRequest(requestId)) {
                return;
            }
        } catch (FileNotFoundException exception) {
            AppLogger.e(TAG, "Text preview open missing file. requestId=" + requestId + ", uri=" + uri, exception);
            runOnUiThread(() -> {
                if (!isActiveOpenRequest(requestId)) {
                    return;
                }
                setOpenUiEnabled(true);
                if (promptDeleteOnMissing) {
                    showMissingHistoryDialog(uri.toString(), fileName);
                } else {
                    showToast(R.string.toast_open_failed);
                }
            });
            return;
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            AppLogger.e(TAG, "Text preview open failed. requestId=" + requestId + ", uri=" + uri, exception);
            runOnUiThread(() -> {
                if (!isActiveOpenRequest(requestId)) {
                    return;
                }
                setOpenUiEnabled(true);
                showToast(R.string.toast_open_failed);
            });
            return;
        }

        try (InputStream fullInputStream = getContentResolver().openInputStream(uri)) {
            if (fullInputStream == null) {
                throw new FileNotFoundException("Input stream is null.");
            }
            String fullText = ReaderTextPlain.readUtf8(fullInputStream);
            runOnUiThread(() -> {
                if (!isActiveOpenRequest(requestId)) {
                    return;
                }
                showTextContent(fullText, false);
            });
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            AppLogger.e(TAG, "Full text background load failed. requestId=" + requestId + ", uri=" + uri, exception);
        }
    }

    private boolean isActiveOpenRequest(int requestId) {
        return !isFinishing() && requestId == currentOpenRequestId;
    }

    @NonNull
    private OpenedFileContent parseFileContent(@NonNull Uri uri, @NonNull String openMode, @NonNull String fileName) throws IOException {
        if (OPEN_MODE_VIDEO.equals(openMode) || OPEN_MODE_AUDIO.equals(openMode)) {
            return OpenedFileContent.forMedia(uri);
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Input stream is null.");
            }
            if (OPEN_MODE_PGM.equals(openMode)) {
                ParserPicturePgm.DataPicturePgm dataPicturePgm = ParserPicturePgm.parse(inputStream);
                Bitmap bitmap = Bitmap.createBitmap(dataPicturePgm.getWidth(), dataPicturePgm.getHeight(), Bitmap.Config.ARGB_8888);
                bitmap.setPixels(
                        dataPicturePgm.getArgbPixels(),
                        0,
                        dataPicturePgm.getWidth(),
                        0,
                        0,
                        dataPicturePgm.getWidth(),
                        dataPicturePgm.getHeight()
                );
                return OpenedFileContent.forBitmap(bitmap);
            }
            if (OPEN_MODE_IMAGE.equals(openMode)) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) {
                    throw new IOException("Failed to decode bitmap.");
                }
                return OpenedFileContent.forBitmap(bitmap);
            }
            if (OPEN_MODE_DOCX.equals(openMode)) {
                return OpenedFileContent.forText(new ReaderTextPlain.PreviewTextResult(ReaderDocumentDocx.readText(inputStream), false));
            }
            if (OPEN_MODE_SPREADSHEET.equals(openMode)) {
                return OpenedFileContent.forText(ReaderTableExcel.readAll(inputStream, fileName));
            }
            if (OPEN_MODE_TEXT.equals(openMode)) {
                return OpenedFileContent.forText(ReaderTextPlain.readUtf8Preview(inputStream, Integer.MAX_VALUE));
            }
        }
        throw new IOException("Unsupported in-app open mode.");
    }

    private void applyOpenedFileContent(@NonNull OpenedFileContent openedFileContent) {
        if (openedFileContent.bitmap != null) {
            showBitmap(openedFileContent.bitmap);
            return;
        }
        if (openedFileContent.mediaUri != null) {
            showMediaFile(openedFileContent.mediaUri);
            return;
        }
        showTextContent(openedFileContent.textContent == null ? "" : openedFileContent.textContent, openedFileContent.truncated);
    }

    private void showLoadingState() {
        setOpenUiEnabled(false);
        updateCurrentOpenTarget(null, OPEN_MODE_UNSUPPORTED);
        stopMediaPlayback();
        setTextSearchVisible(false);
        setTextActionsVisible(false);
        viewPictureZoom.setImageDrawable(null);
        viewPictureZoom.setVisibility(View.GONE);
        scrollViewText.setVisibility(View.GONE);
        webViewMarkdownPreview.setVisibility(View.GONE);
        playerViewFile.setVisibility(View.GONE);
        setTextQuickScrollVisible(false);
        layoutEmptyState.setVisibility(View.VISIBLE);
        textViewEmptyTitle.setText(R.string.loading_title);
        textViewEmptyMessage.setText(R.string.loading_message);
    }

    private void setOpenUiEnabled(boolean enabled) {
        buttonOpenPgm.setEnabled(enabled);
        buttonOpenHistory.setEnabled(enabled);
    }

    private void tryTakePersistableReadPermission(@NonNull Uri uri, int flags) {
        int takeFlags = flags & (
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        if ((takeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException exception) {
            AppLogger.w(TAG, "Persistable permission unavailable. uri=" + uri);
        }
    }

    private void verifyFileExists(@NonNull Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new FileNotFoundException("Input stream is null.");
            }
        }
    }

    @Nullable
    private String getNormalizedMimeType(@NonNull Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        return mimeType == null ? null : mimeType.toLowerCase(Locale.US);
    }

    @NonNull
    private String detectOpenModeFromContent(@NonNull Uri uri, @NonNull String fallbackOpenMode) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream != null && ReaderDocumentDocx.isDocxFile(inputStream)) {
                return OPEN_MODE_DOCX;
            }
        } catch (IOException | SecurityException ignored) {
        }
        return fallbackOpenMode;
    }

    private boolean shouldProbeDocx(@Nullable String fileName, @Nullable String mimeType) {
        if (fileName != null && fileName.toLowerCase(Locale.US).endsWith(".docx")) {
            return false;
        }
        return mimeType == null || mimeType.isEmpty() || "application/octet-stream".equals(mimeType) || "*/*".equals(mimeType);
    }

    private boolean hasExternalViewer(@NonNull Uri uri, @Nullable String mimeType) {
        return !getExternalResolveInfos(createViewIntent(uri, mimeType)).isEmpty()
                || (!"*/*".equals(mimeType) && !getExternalResolveInfos(createViewIntent(uri, "*/*")).isEmpty());
    }

    private boolean openWithExternalApp(@NonNull Uri uri, @Nullable String mimeType) {
        if (tryOpenWithExternalApp(uri, mimeType)) {
            return true;
        }
        if (!"*/*".equals(mimeType)) {
            return tryOpenWithExternalApp(uri, "*/*");
        }
        return false;
    }

    private boolean tryOpenWithExternalApp(@NonNull Uri uri, @Nullable String mimeType) {
        Intent intent = createViewIntent(uri, mimeType);
        List<ResolveInfo> resolveInfos = getExternalResolveInfos(intent);
        AppLogger.d(TAG, "External candidates. mime=" + String.valueOf(mimeType) + ", count=" + resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            Intent explicitIntent = new Intent(intent);
            explicitIntent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            try {
                startActivity(explicitIntent);
                return true;
            } catch (ActivityNotFoundException exception) {
                AppLogger.e(TAG, "External launch failed. package=" + resolveInfo.activityInfo.packageName, exception);
            }
        }
        return false;
    }

    @NonNull
    private Intent createViewIntent(@NonNull Uri uri, @Nullable String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType == null ? "*/*" : mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    @NonNull
    private List<ResolveInfo> getExternalResolveInfos(@NonNull Intent intent) {
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ArrayList<ResolveInfo> externalResolveInfos = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            if (getPackageName().equals(resolveInfo.activityInfo.packageName)) {
                continue;
            }
            externalResolveInfos.add(resolveInfo);
        }
        return externalResolveInfos;
    }

    @Nullable
    private String readHistoryFromFile() {
        File historyFile = getHistoryFile();
        if (!historyFile.exists()) {
            return null;
        }
        try (InputStream inputStream = new java.io.FileInputStream(historyFile)) {
            byte[] rawBytes = inputStream.readAllBytes();
            return new String(rawBytes, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to read history file. path=" + historyFile.getAbsolutePath(), exception);
            return null;
        }
    }

    private void writeHistoryToFile(@NonNull String historyJson) {
        File historyFile = getHistoryFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(new java.io.FileOutputStream(historyFile, false), StandardCharsets.UTF_8)) {
            writer.write(historyJson);
            writer.flush();
        } catch (IOException exception) {
            AppLogger.e(TAG, "Failed to write history file. path=" + historyFile.getAbsolutePath(), exception);
        }
    }

    @NonNull
    private File getHistoryFile() {
        return new File(AppStoragePaths.resolveHistoryDirectory(this), AppConfig.get().getHistoryFileName());
    }

    private void togglePrettyPrint() {
        if (!currentTextSupportsPrettyPrint) {
            return;
        }
        if (currentTextPrettyPrinted) {
            currentTextContent = currentOriginalTextContent;
            currentTextPrettyPrinted = false;
            refreshSearchResults();
            updateTextActionButtons();
            return;
        }
        String prettyText = formatStructuredText(currentOriginalTextContent);
        if (prettyText == null) {
            showToast(R.string.toast_pretty_print_failed);
            return;
        }
        currentTextContent = prettyText;
        currentTextPrettyPrinted = true;
        refreshSearchResults();
        updateTextActionButtons();
    }

    @Nullable
    private String formatStructuredText(@NonNull String textContent) {
        String normalizedFileName = currentFileName.toLowerCase(Locale.US);
        if (matchesExtension(normalizedFileName, ".json")) {
            try {
                return new JSONObject(textContent).toString(4);
            } catch (JSONException ignored) {
            }
            try {
                return new JSONArray(textContent).toString(4);
            } catch (JSONException ignored) {
                return null;
            }
        }
        if (matchesExtension(normalizedFileName, ".xml")) {
            try {
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                try {
                    transformerFactory.setAttribute("indent-number", 4);
                } catch (IllegalArgumentException ignored) {
                }
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                StringWriter stringWriter = new StringWriter();
                transformer.transform(new StreamSource(new StringReader(textContent)), new StreamResult(stringWriter));
                return stringWriter.toString().trim();
            } catch (Exception exception) {
                return null;
            }
        }
        return null;
    }

    private void adjustTextSize(float deltaSp) {
        currentTextSizeSp = Math.max(AppConfig.get().getMinTextSizeSp(), Math.min(AppConfig.get().getMaxTextSizeSp(), currentTextSizeSp + deltaSp));
        applyCurrentTextZoom();
    }

    private void applyCurrentTextZoom() {
        textViewFileContent.setTextSize(currentTextSizeSp);
        int textZoom = Math.round((currentTextSizeSp / AppConfig.get().getDefaultTextSizeSp()) * 100f);
        webViewMarkdownPreview.getSettings().setTextZoom(textZoom);
        scheduleTextQuickScrollUpdate();
    }

    private void refreshSearchResults() {
        searchMatchStarts.clear();
        searchMatchEnds.clear();
        if (currentTextLargeMode) {
            currentSearchIndex = -1;
            textViewFileContent.setText(currentTextContent, TextView.BufferType.NORMAL);
            updateSearchControls();
            scheduleTextQuickScrollUpdate();
            return;
        }
        String query = editTextSearch.getText().toString();
        if (currentTextContent.isEmpty()) {
            textViewFileContent.setText(null);
            updateSearchControls();
            return;
        }
        if (query.isEmpty()) {
            currentSearchIndex = -1;
            applyCurrentTextPresentation();
            updateSearchControls();
            return;
        }
        String normalizedContent = currentTextContent.toLowerCase(Locale.US);
        String normalizedQuery = query.toLowerCase(Locale.US);
        int searchStart = 0;
        while (searchStart <= normalizedContent.length() - normalizedQuery.length()) {
            int matchStart = normalizedContent.indexOf(normalizedQuery, searchStart);
            if (matchStart < 0) {
                break;
            }
            searchMatchStarts.add(matchStart);
            searchMatchEnds.add(matchStart + normalizedQuery.length());
            searchStart = matchStart + normalizedQuery.length();
        }
        if (searchMatchStarts.isEmpty()) {
            currentSearchIndex = -1;
        } else if (currentSearchIndex < 0 || currentSearchIndex >= searchMatchStarts.size()) {
            currentSearchIndex = 0;
        }
        applySearchHighlight();
        updateSearchControls();
    }

    private void applyCurrentTextPresentation() {
        updateTextActionButtons();
        if (!currentTextLargeMode && currentFileIsMarkdown && markdownPreviewMode) {
            showMarkdownPreview();
            return;
        }
        showTextSourceWithOptionalHighlight();
    }

    private void showTextSourceWithOptionalHighlight() {
        webViewMarkdownPreview.setVisibility(View.GONE);
        scrollViewText.setVisibility(View.VISIBLE);
        if (currentTextLargeMode) {
            textViewFileContent.setText(currentTextContent, TextView.BufferType.NORMAL);
            scheduleTextQuickScrollUpdate();
            return;
        }
        if (searchMatchStarts.isEmpty()) {
            textViewFileContent.setText(buildStyledSourceText(currentTextContent));
            scheduleTextQuickScrollUpdate();
            return;
        }
        applySearchHighlight();
    }

    private void showMarkdownPreview() {
        scrollViewText.setVisibility(View.GONE);
        webViewMarkdownPreview.setVisibility(View.VISIBLE);
        setTextQuickScrollVisible(false);
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlBody = renderer.render(parser.parse(currentTextContent));
        String html = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"
                + "<style>body{font-family:sans-serif;padding:16px;color:#0F172A;background:#FFFFFF;line-height:1.6;}"
                + "pre{background:#F8FAFC;padding:12px;border-radius:8px;overflow:auto;}"
                + "code{background:#F1F5F9;padding:2px 4px;border-radius:4px;}"
                + "img{max-width:100%;height:auto;}blockquote{border-left:4px solid #CBD5E1;padding-left:12px;color:#475569;}"
                + "table{border-collapse:collapse;width:100%;}th,td{border:1px solid #CBD5E1;padding:8px;}</style>"
                + "</head><body>" + htmlBody + "</body></html>";
        webViewMarkdownPreview.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    private void moveToSearchMatch(int direction) {
        if (searchMatchStarts.isEmpty()) {
            return;
        }
        currentSearchIndex += direction;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchMatchStarts.size() - 1;
        } else if (currentSearchIndex >= searchMatchStarts.size()) {
            currentSearchIndex = 0;
        }
        applySearchHighlight();
        updateSearchControls();
    }

    private void applySearchHighlight() {
        if (currentTextContent.isEmpty()) {
            textViewFileContent.setText(null);
            setTextQuickScrollVisible(false);
            return;
        }
        if (currentTextLargeMode) {
            textViewFileContent.setText(currentTextContent, TextView.BufferType.NORMAL);
            scheduleTextQuickScrollUpdate();
            return;
        }
        if (searchMatchStarts.isEmpty()) {
            textViewFileContent.setText(buildStyledSourceText(currentTextContent));
            scheduleTextQuickScrollUpdate();
            return;
        }
        SpannableString spannableString = new SpannableString(buildStyledSourceText(currentTextContent));
        int highlightColor = Color.parseColor("#80FACC15");
        int selectedColor = Color.parseColor("#F59E0B");
        int selectedTextColor = Color.BLACK;
        for (int index = 0; index < searchMatchStarts.size(); index++) {
            int start = searchMatchStarts.get(index);
            int end = searchMatchEnds.get(index);
            spannableString.setSpan(new BackgroundColorSpan(index == currentSearchIndex ? selectedColor : highlightColor), start, end, 0);
            if (index == currentSearchIndex) {
                spannableString.setSpan(new ForegroundColorSpan(selectedTextColor), start, end, 0);
            }
        }
        textViewFileContent.setText(spannableString);
        scheduleTextQuickScrollUpdate();
        scrollToCurrentSearchMatch();
    }

    @NonNull
    private SpannableString buildStyledSourceText(@NonNull String textContent) {
        SpannableString spannableString = new SpannableString(textContent);
        if (!isCodeLikeFileName(currentFileName)) {
            return spannableString;
        }
        applyRegexSpan(spannableString, Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), Color.parseColor("#F59E0B"), false);
        applyRegexSpan(spannableString, Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"), Color.parseColor("#22C55E"), false);
        applyRegexSpan(spannableString, Pattern.compile("\\b(true|false|null)\\b"), Color.parseColor("#38BDF8"), true);
        applyRegexSpan(spannableString, Pattern.compile("\\b(class|public|private|protected|static|final|void|new|return|if|else|switch|case|break|continue|for|while|try|catch|finally|throw|throws|extends|implements|import|package|interface|enum|struct|func|var|let|const|object|data|when|override)\\b"), Color.parseColor("#A78BFA"), true);
        applyRegexSpan(spannableString, Pattern.compile("(?m)^\\s*(//.*|#.*)$"), Color.parseColor("#94A3B8"), false);
        applyRegexSpan(spannableString, Pattern.compile("(?m)^\\s*\"[^\"]+\"\\s*:"), Color.parseColor("#60A5FA"), true);
        return spannableString;
    }

    private void applyRegexSpan(@NonNull Spannable spannable, @NonNull Pattern pattern, int color, boolean bold) {
        Matcher matcher = pattern.matcher(spannable);
        while (matcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bold) {
                spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void updateSearchControls() {
        int matchCount = searchMatchStarts.size();
        int selectedNumber = currentSearchIndex >= 0 ? currentSearchIndex + 1 : 0;
        textViewSearchCount.setText(getString(R.string.search_result_count, selectedNumber, matchCount));
        boolean hasMatches = matchCount > 0;
        buttonSearchPrevious.setEnabled(hasMatches);
        buttonSearchNext.setEnabled(hasMatches);
    }

    private void scrollToCurrentSearchMatch() {
        if (currentSearchIndex < 0 || currentSearchIndex >= searchMatchStarts.size()) {
            return;
        }
        int matchStart = searchMatchStarts.get(currentSearchIndex);
        textViewFileContent.post(() -> {
            Layout layout = textViewFileContent.getLayout();
            if (layout == null) {
                return;
            }
            int line = layout.getLineForOffset(matchStart);
            int targetY = Math.max(layout.getLineTop(line) - (scrollViewText.getHeight() / 3), 0);
            scrollViewText.smoothScrollTo(0, targetY);
        });
    }

    @NonNull
    private String resolveDisplayPath(@NonNull Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme()) && !isNullOrEmpty(uri.getPath())) {
            return toParentPath(uri.getPath());
        }
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            if (!isNullOrEmpty(documentId)) {
                return toReadableDocumentPath(documentId);
            }
        }
        String decodedPath = Uri.decode(uri.getPath());
        if (!isNullOrEmpty(decodedPath)) {
            return toParentPath(decodedPath);
        }
        return Uri.decode(uri.toString());
    }

    @NonNull
    private String toReadableDocumentPath(@NonNull String documentId) {
        String decodedDocumentId = Uri.decode(documentId);
        int separatorIndex = decodedDocumentId.indexOf(':');
        if (separatorIndex < 0) {
            return toParentPath(decodedDocumentId);
        }
        String storageName = decodedDocumentId.substring(0, separatorIndex);
        String relativePath = decodedDocumentId.substring(separatorIndex + 1);
        String parentPath = toParentPath(relativePath);
        if (isNullOrEmpty(parentPath)) {
            return "/" + storageName;
        }
        return "/" + storageName + "/" + parentPath;
    }

    @NonNull
    private String toParentPath(@Nullable String fullPath) {
        if (isNullOrEmpty(fullPath)) {
            return getString(R.string.current_file_default_path);
        }
        int lastSeparatorIndex = fullPath.lastIndexOf('/');
        if (lastSeparatorIndex <= 0) {
            return fullPath;
        }
        return fullPath.substring(0, lastSeparatorIndex);
    }

    private static boolean isNullOrEmpty(@Nullable String value) {
        return value == null || value.isEmpty();
    }

    private static boolean matchesExtension(@NonNull String fileName, @NonNull String... extensions) {
        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMarkdownFileName(@Nullable String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalizedFileName = fileName.toLowerCase(Locale.US);
        return matchesExtension(normalizedFileName, ".md", ".markdown");
    }

    private boolean isStructuredTextFileName(@Nullable String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalizedFileName = fileName.toLowerCase(Locale.US);
        return matchesExtension(normalizedFileName, ".json", ".xml");
    }

    private boolean isCodeLikeFileName(@Nullable String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalizedFileName = fileName.toLowerCase(Locale.US);
        return matchesExtension(
                normalizedFileName,
                ".json", ".xml", ".java", ".swift", ".kt", ".kts", ".js", ".ts",
                ".html", ".htm", ".css", ".yaml", ".yml", ".properties", ".gradle", ".md"
        );
    }

    private boolean isLargeTextContent(@NonNull String textContent) {
        return textContent.length() >= AppConfig.get().getLargeTextInteractionThreshold();
    }

    @Override
    protected void onDestroy() {
        currentOpenRequestId++;
        executorOpenFile.shutdownNow();
        stopMediaPlayback();
        releaseMediaPlayer();
        webViewMarkdownPreview.destroy();
        AppLogger.i(TAG, "onDestroy.");
        super.onDestroy();
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    private void showToast(@NonNull String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static final class HistoryRecord {

        @NonNull
        private final String uriString;
        @NonNull
        private final String fileName;
        @NonNull
        private final String filePath;
        private final long fileSize;

        private HistoryRecord(@NonNull String uriString, @NonNull String fileName, @NonNull String filePath, long fileSize) {
            this.uriString = uriString;
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
        }
    }

    private static final class OpenedFileContent {

        @Nullable
        private final Bitmap bitmap;
        @Nullable
        private final String textContent;
        @Nullable
        private final Uri mediaUri;
        private final boolean truncated;

        private OpenedFileContent(@Nullable Bitmap bitmap, @Nullable String textContent, @Nullable Uri mediaUri, boolean truncated) {
            this.bitmap = bitmap;
            this.textContent = textContent;
            this.mediaUri = mediaUri;
            this.truncated = truncated;
        }

        @NonNull
        private static OpenedFileContent forBitmap(@NonNull Bitmap bitmap) {
            return new OpenedFileContent(bitmap, null, null, false);
        }

        @NonNull
        private static OpenedFileContent forText(@NonNull ReaderTextPlain.PreviewTextResult previewTextResult) {
            return new OpenedFileContent(null, previewTextResult.textContent, null, previewTextResult.truncated);
        }

        @NonNull
        private static OpenedFileContent forMedia(@NonNull Uri mediaUri) {
            return new OpenedFileContent(null, null, mediaUri, false);
        }
    }
}
