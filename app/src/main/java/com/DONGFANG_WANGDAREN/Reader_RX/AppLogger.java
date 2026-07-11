package com.DONGFANG_WANGDAREN.Reader_RX;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppLogger {

    private static final String TAG = "AppLogger";
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Nullable
    private static volatile File sessionLogFile;
    @Nullable
    private static volatile File sessionLogDirectory;
    @Nullable
    private static volatile Context applicationContext;
    private static volatile boolean initialized;

    private AppLogger() {
    }

    public static void init(@NonNull Context context) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            applicationContext = context.getApplicationContext();
            initialized = true;

            File logFile = createSessionLogFile(applicationContext);
            sessionLogFile = logFile;
            sessionLogDirectory = logFile == null ? null : logFile.getParentFile();
            if (logFile != null) {
                i(TAG, "Logger initialized. File=" + logFile.getAbsolutePath());
            } else {
                w(TAG, "Logger initialized without external file output.");
            }
        }
    }

    public static void refreshStorageLocation(@NonNull Context context) {
        File newLogFile;
        File previousLogFile;
        synchronized (LOCK) {
            applicationContext = context.getApplicationContext();
            previousLogFile = sessionLogFile;
            newLogFile = createSessionLogFile(applicationContext);
            if (newLogFile != null && previousLogFile != null && !newLogFile.equals(previousLogFile)) {
                migrateLogFile(previousLogFile, newLogFile);
            }
            sessionLogFile = newLogFile;
            sessionLogDirectory = newLogFile == null ? null : newLogFile.getParentFile();
        }
        if (newLogFile != null) {
            i(TAG, "Logger storage refreshed. File=" + newLogFile.getAbsolutePath());
        } else {
            w(TAG, "Logger storage refresh failed.");
        }
    }

    private static void migrateLogFile(@NonNull File source, @NonNull File destination) {
        EXECUTOR.execute(() -> {
            try {
                java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | UnsupportedOperationException exception) {
                Log.e(TAG, "Failed to migrate log file to new location.", exception);
            }
        });
    }

    public static void d(@NonNull String tag, @NonNull String message) {
        log(Log.DEBUG, tag, message, null);
    }

    public static void i(@NonNull String tag, @NonNull String message) {
        log(Log.INFO, tag, message, null);
    }

    public static void w(@NonNull String tag, @NonNull String message) {
        log(Log.WARN, tag, message, null);
    }

    public static void e(@NonNull String tag, @NonNull String message) {
        log(Log.ERROR, tag, message, null);
    }

    public static void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        log(Log.ERROR, tag, message, throwable);
    }

    @Nullable
    public static File getSessionLogFile() {
        return sessionLogFile;
    }

    @Nullable
    public static File getSessionLogDirectory() {
        return sessionLogDirectory;
    }

    private static void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        String text = buildLogLine(priority, tag, message, throwable);
        Log.println(priority, tag, text);
        writeToFile(text);
    }

    @NonNull
    private static String buildLogLine(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
        Date now = new Date();
        String packageName = applicationContext != null ? applicationContext.getPackageName() : "?";
        StringBuilder builder = new StringBuilder();
        builder.append(format(now, "yyyy-MM-dd HH:mm:ss.SSS"))
                .append(' ')
                .append(Process.myPid())
                .append('-')
                .append(Process.myTid())
                .append('/')
                .append(packageName)
                .append(' ')
                .append(priorityToLetter(priority))
                .append('/')
                .append(tag)
                .append(": ")
                .append(message);

        if (throwable != null) {
            builder.append('\n').append(stackTraceToString(throwable));
        }
        return builder.toString();
    }

    private static void writeToFile(@NonNull String text) {
        File logFile = sessionLogFile;
        if (logFile == null) {
            return;
        }

        EXECUTOR.execute(() -> {
            try (FileWriter fileWriter = new FileWriter(logFile, true)) {
                fileWriter.write(text);
                fileWriter.write('\n');
                fileWriter.flush();
            } catch (IOException exception) {
                Log.e(TAG, "Failed to append log file.", exception);
            }
        });
    }

    @Nullable
    private static File createSessionLogFile(@Nullable Context context) {
        if (context == null) {
            return null;
        }

        File logsDirectory = AppStoragePaths.resolveLogsDirectory(context);
        Date now = new Date();
        File dayDirectory = new File(logsDirectory, format(now, "yyyy-MM-dd"));
        if (!ensureDirectory(dayDirectory)) {
            return null;
        }

        String baseName = "Log_" + format(now, "HH-mm") + "_" + format(now, "yyyy-MM-dd");
        File logFile = findUniqueLogFile(dayDirectory, baseName);
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
        return logFile;
    }

    private static boolean ensureDirectory(@NonNull File directory) {
        return directory.exists() || directory.mkdirs();
    }

    @NonNull
    private static File findUniqueLogFile(@NonNull File directory, @NonNull String baseName) {
        File candidate = new File(directory, baseName + ".txt");
        if (!candidate.exists()) {
            return candidate;
        }
        int index = 1;
        while (true) {
            candidate = new File(directory, baseName + "_" + index + ".txt");
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
            if (index > 9999) {
                return new File(directory, baseName + "_" + System.currentTimeMillis() + ".txt");
            }
        }
    }

    @NonNull
    private static String format(@NonNull Date date, @NonNull String pattern) {
        return new SimpleDateFormat(pattern, Locale.US).format(date);
    }

    @NonNull
    private static String stackTraceToString(@NonNull Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        return stringWriter.toString();
    }

    @NonNull
    private static String priorityToLetter(int priority) {
        switch (priority) {
            case Log.VERBOSE:
                return "V";
            case Log.DEBUG:
                return "D";
            case Log.INFO:
                return "I";
            case Log.WARN:
                return "W";
            case Log.ERROR:
                return "E";
            case Log.ASSERT:
                return "A";
            default:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? Integer.toString(priority) : "?";
        }
    }
}
