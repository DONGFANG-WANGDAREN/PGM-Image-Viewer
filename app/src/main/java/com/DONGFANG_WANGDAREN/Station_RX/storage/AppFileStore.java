package com.DONGFANG_WANGDAREN.Station_RX.storage;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.DONGFANG_WANGDAREN.Station_RX.app.AppConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppFileStore {

    private static final String LEGACY_ENGLISH_LOGS_FOLDER = "Logs";
    private static final String LEGACY_LOCALIZED_LOGS_FOLDER = "日志";
    private static final String LEGACY_LOCALIZED_HISTORY_FOLDER = "历史记录";
    private static final String LEGACY_ENGLISH_FILE_TRANSFER_FOLDER = "File Transfer";
    private static final String LEGACY_LOCALIZED_FILE_TRANSFER_FOLDER = "文件传输";

    private AppFileStore() {
    }

    /**
     * 创建当前会话日志文件。
     * 路径范例：/storage/emulated/0/Station RX/日志/2026-08-10/Log_14-30_2026-08-10.txt
     * 文件名范例：Log_14-30_2026-08-10.txt
     */
    @Nullable
    public static File createSessionLogFile(@Nullable Context context) {
        if (context == null) {
            return null;
        }
        AppConfig config = AppConfig.get();
        File logsDirectory = resolveManagedDirectory(
                context,
                config.getLogsFolder(),
                "Log",
                LEGACY_ENGLISH_LOGS_FOLDER,
                LEGACY_LOCALIZED_LOGS_FOLDER
        );
        String dayFolderName = formatNow(config.getLogDayFolderFormat());
        File dayDirectory = ensureDirectory(new File(logsDirectory, dayFolderName));
        String baseName = normalizeFileName(formatNow(config.getLogFileNameFormat()), "Log");
        String extension = normalizeExtension(config.getLogFileExtension(), ".txt");
        File logFile = buildUniqueFile(dayDirectory, baseName, extension);
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
        return logFile;
    }

    /**
     * 追加写入一条日志。
     * 路径范例：/storage/emulated/0/Station RX/日志/2026-08-10/Log_14-30_2026-08-10.txt
     * 文件名范例：Log_14-30_2026-08-10.txt
     */
    public static void appendLogLine(@NonNull File logFile, @NonNull String text) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)) {
            writer.write(text);
            writer.write('\n');
            writer.flush();
        }
    }

    /**
     * 迁移日志文件到新的日志位置。
     * 路径范例：从 /storage/emulated/0/Station RX/日志/2026-08-10/Log_14-30_2026-08-10.txt
     * 复制到 /storage/emulated/0/Station RX/日志/2026-08-10/Log_14-31_2026-08-10.txt
     * 文件名范例：Log_14-31_2026-08-10.txt
     */
    public static void migrateLogFile(@NonNull File source, @NonNull File destination) throws IOException {
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 读取历史记录 JSON。
     * 路径范例：/storage/emulated/0/Station RX/历史记录/History.json
     * 文件名范例：History.json
     */
    @Nullable
    public static String readHistoryJson(@NonNull Context context) throws IOException {
        File historyFile = getHistoryFile(context);
        if (!historyFile.exists()) {
            historyFile = getLegacyHistoryFile(context);
        }
        if (!historyFile.exists()) {
            historyFile = getLegacyLocalizedHistoryFile(context);
        }
        if (!historyFile.exists()) {
            return null;
        }
        try (InputStream inputStream = new FileInputStream(historyFile)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 覆盖写入历史记录 JSON。
     * 路径范例：/storage/emulated/0/Station RX/历史记录/History.json
     * 文件名范例：History.json
     */
    public static void writeHistoryJson(@NonNull Context context, @NonNull String historyJson) throws IOException {
        File historyFile = getHistoryFile(context);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(historyFile, false), StandardCharsets.UTF_8)) {
            writer.write(historyJson);
            writer.flush();
        }
    }

    /**
     * 获取当前历史记录文件。
     * 路径范例：/storage/emulated/0/Station RX/历史记录/History.json
     * 文件名范例：History.json
     */
    @NonNull
    public static File getHistoryFile(@NonNull Context context) {
        File historyDirectory = resolveManagedDirectory(
                context,
                AppConfig.get().getHistoryFolder(),
                "History",
                LEGACY_LOCALIZED_HISTORY_FOLDER
        );
        return new File(historyDirectory, normalizeFileName(AppConfig.get().getHistoryFileName(), "History.json"));
    }

    /**
     * 获取旧版历史记录文件，给历史迁移兼容使用。
     * 路径范例：/storage/emulated/0/Station RX/History/History.json
     * 文件名范例：History.json
     */
    @NonNull
    public static File getLegacyHistoryFile(@NonNull Context context) {
        return new File(new File(AppStoragePaths.resolveBaseDirectory(context), "History"), "History.json");
    }

    @NonNull
    private static File getLegacyLocalizedHistoryFile(@NonNull Context context) {
        return new File(new File(AppStoragePaths.resolveBaseDirectory(context), LEGACY_LOCALIZED_HISTORY_FOLDER), "History.json");
    }

    /**
     * 获取网页文件管理器的浏览根目录。
     * 优先展示主存储根目录；如果系统未授予完整存储访问，则回退到应用自身目录。
     */
    @NonNull
    @SuppressWarnings("deprecation")
    public static File getWebFileBrowserRootDirectory(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            File externalStorageRoot = Environment.getExternalStorageDirectory();
            if (externalStorageRoot.exists() || externalStorageRoot.mkdirs()) {
                return externalStorageRoot;
            }
        }
        return AppStoragePaths.resolveBaseDirectory(context);
    }

    @NonNull
    public static String getWebFileBrowserRootName(@NonNull Context context) {
        File rootDirectory = getWebFileBrowserRootDirectory(context);
        String absolutePath = rootDirectory.getAbsolutePath();
        if ("/storage/emulated/0".equals(absolutePath) || "/sdcard".equalsIgnoreCase(absolutePath)) {
            return "Internal Storage";
        }
        String name = rootDirectory.getName();
        return name == null || name.trim().isEmpty() ? "Internal Storage" : name.trim();
    }

    /**
     * 解析并校验网页文件管理器当前目录；为空时默认回到浏览根目录。
     */
    @Nullable
    public static File resolveWebFileBrowserDirectory(@NonNull Context context, @Nullable String requestedPath) {
        File directory;
        if (requestedPath == null || requestedPath.trim().isEmpty()) {
            directory = getWebFileBrowserRootDirectory(context);
        } else {
            directory = new File(requestedPath);
        }
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }
        return isUnderWebFileBrowserRoot(context, directory) ? directory : null;
    }

    /**
     * 判断某个文件或目录是否在网页文件管理器浏览根目录下。
     */
    public static boolean isUnderWebFileBrowserRoot(@NonNull Context context, @NonNull File file) {
        try {
            String canonicalRoot = getWebFileBrowserRootDirectory(context).getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            return canonicalFile.equals(canonicalRoot)
                    || canonicalFile.startsWith(canonicalRoot + File.separator);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * 创建文件传输分片上传的临时文件。
     * 路径范例：/data/user/0/com.DONGFANG_WANGDAREN.Station_RX/cache/web_uploads/abc123.tmp
     * 文件名范例：abc123.tmp
     */
    @NonNull
    public static File createUploadTempFile(@NonNull Context context, @NonNull String uploadId) throws IOException {
        File cacheDirectory = ensureDirectory(new File(context.getCacheDir(), "web_uploads"));
        String normalizedId = normalizeFileName(uploadId, "upload");
        File tempFile = new File(cacheDirectory, normalizedId + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        if (!tempFile.createNewFile()) {
            throw new IOException("Failed to create upload temp file: " + tempFile.getAbsolutePath());
        }
        return tempFile;
    }

    /**
     * 把一个上传分片追加到临时文件。
     * 路径范例：/data/user/0/com.DONGFANG_WANGDAREN.Station_RX/cache/web_uploads/abc123.tmp
     * 文件名范例：abc123.tmp
     */
    public static void appendUploadChunk(@NonNull File tempFile, @NonNull File chunkFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(chunkFile);
             OutputStream outputStream = new FileOutputStream(tempFile, true)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
    }

    /**
     * 直接保存网页上传的文件到目标目录，并处理重名。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test.pdf
     * 文件名范例：test.pdf；若重名则变成 test_1.pdf
     */
    @NonNull
    public static File saveUploadedFile(@NonNull File targetDirectory, @NonNull String fileName, @NonNull File sourceFile) throws IOException {
        File destination = buildUniqueFile(
                targetDirectory,
                splitBaseName(fileName),
                splitExtension(fileName)
        );
        Files.copy(sourceFile.toPath(), destination.toPath());
        return destination;
    }

    /**
     * 把分片临时文件落成最终上传文件，并处理重名。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test.pdf
     * 文件名范例：test.pdf；若重名则变成 test_1.pdf
     */
    @NonNull
    public static File finalizeUploadedTempFile(@NonNull File targetDirectory, @NonNull String fileName, @NonNull File tempFile) throws IOException {
        File destination = buildUniqueFile(
                targetDirectory,
                splitBaseName(fileName),
                splitExtension(fileName)
        );
        if (!tempFile.renameTo(destination)) {
            Files.copy(tempFile.toPath(), destination.toPath());
            tempFile.delete();
        }
        return destination;
    }

    public static void cleanupObsoleteDirectories(@NonNull Context context) {
        File baseDirectory = AppStoragePaths.resolveBaseDirectory(context);
        deleteDirectoryIfEmpty(new File(baseDirectory, LEGACY_ENGLISH_FILE_TRANSFER_FOLDER));
        deleteDirectoryIfEmpty(new File(baseDirectory, LEGACY_LOCALIZED_FILE_TRANSFER_FOLDER));
    }

    /**
     * 确保目录存在。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10
     * 文件名范例：目录下的文件例如 demo.zip
     */
    @NonNull
    private static File ensureDirectory(@NonNull File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    @NonNull
    private static File resolveManagedDirectory(
            @NonNull Context context,
            @Nullable String configuredName,
            @NonNull String fallbackName,
            @Nullable String... legacyNames
    ) {
        File baseDirectory = AppStoragePaths.resolveBaseDirectory(context);
        File targetDirectory = new File(baseDirectory, normalizeDirectoryName(configuredName, fallbackName));
        migrateLegacyDirectoryIfNeeded(baseDirectory, targetDirectory, legacyNames);
        return ensureDirectory(targetDirectory);
    }

    private static void migrateLegacyDirectoryIfNeeded(
            @NonNull File baseDirectory,
            @NonNull File targetDirectory,
            @Nullable String... legacyNames
    ) {
        if (targetDirectory.exists() || legacyNames == null || legacyNames.length == 0) {
            return;
        }
        for (String legacyName : legacyNames) {
            if (legacyName == null || legacyName.trim().isEmpty()) {
                continue;
            }
            File legacyDirectory = new File(baseDirectory, legacyName);
            if (!legacyDirectory.exists() || !legacyDirectory.isDirectory()) {
                continue;
            }
            if (legacyDirectory.renameTo(targetDirectory)) {
                return;
            }
            try {
                Files.move(legacyDirectory.toPath(), targetDirectory.toPath());
                return;
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean deleteDirectoryIfEmpty(@NonNull File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return directory.delete();
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (!deleteDirectoryIfEmpty(child)) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return directory.delete();
    }

    /**
     * 在目标目录下生成不重名的最终文件名。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test_1.pdf
     * 文件名范例：test.pdf、test_1.pdf
     */
    @NonNull
    private static File buildUniqueFile(@NonNull File directory, @NonNull String baseName, @NonNull String extension) {
        String normalizedBaseName = normalizeFileName(baseName, "file");
        String normalizedExtension = normalizeExtension(extension, "");
        File candidate = new File(directory, normalizedBaseName + normalizedExtension);
        if (!candidate.exists()) {
            return candidate;
        }
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(directory, normalizedBaseName + "_" + index + normalizedExtension);
            index++;
        }
        return candidate;
    }

    /**
     * 从完整文件名里拆出主文件名。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test.pdf
     * 文件名范例：输入 test.pdf，输出 test
     */
    @NonNull
    private static String splitBaseName(@NonNull String fileName) {
        String normalized = normalizeFileName(fileName, "file");
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0) {
            return normalized;
        }
        return normalized.substring(0, dotIndex);
    }

    /**
     * 从完整文件名里拆出扩展名。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test.pdf
     * 文件名范例：输入 test.pdf，输出 .pdf
     */
    @NonNull
    private static String splitExtension(@NonNull String fileName) {
        String normalized = normalizeFileName(fileName, "file");
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == normalized.length() - 1) {
            return "";
        }
        return normalizeExtension(normalized.substring(dotIndex), "");
    }

    /**
     * 规范化目录名，避免目录层级被意外带进去。
     * 路径范例：/storage/emulated/0/Station RX/历史记录
     * 文件名范例：目录名 历史记录
     */
    @NonNull
    private static String normalizeDirectoryName(@Nullable String name, @NonNull String fallback) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized.replace("/", "_");
    }

    /**
     * 规范化文件名，避免把路径分隔符写进文件名。
     * 路径范例：/storage/emulated/0/Station RX/日志/2026-08-10/Log_14-30_2026-08-10.txt
     * 文件名范例：Log_14-30_2026-08-10.txt
     */
    @NonNull
    private static String normalizeFileName(@Nullable String fileName, @NonNull String fallback) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        return normalized
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_");
    }

    /**
     * 规范化扩展名，保证扩展名以点开头。
     * 路径范例：/storage/emulated/0/Station RX/文件传输/2026-08-10/test.pdf
     * 文件名范例：.pdf
     */
    @NonNull
    private static String normalizeExtension(@Nullable String extension, @NonNull String fallback) {
        String normalized = extension == null ? "" : extension.trim();
        if (normalized.isEmpty()) {
            normalized = fallback;
        }
        if (!normalized.isEmpty() && !normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        return normalized.replace("/", "").replace("\\", "").replace(":", "");
    }

    /**
     * 按配置格式生成当前时间字符串，用于目录名或文件名。
     * 路径范例：/storage/emulated/0/Station RX/日志/2026-08-10/Log_14-30_2026-08-10.txt
     * 文件名范例：2026-08-10、Log_14-30_2026-08-10
     */
    @NonNull
    private static String formatNow(@NonNull String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(new Date());
    }
}
