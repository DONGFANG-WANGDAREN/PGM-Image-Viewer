package com.DONGFANG_WANGDAREN.Reader_RX;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import androidx.annotation.NonNull;

import java.io.File;

public final class AppStoragePaths {

    private AppStoragePaths() {
    }

    @NonNull
    public static File resolveBaseDirectory(@NonNull Context context) {
        if (canUsePreferredBaseDirectory(context)) {
            File preferredDirectory = resolvePreferredBaseDirectory(context);
            if (preferredDirectory.exists() || preferredDirectory.mkdirs()) {
                return preferredDirectory;
            }
        }

        File fallbackDirectory = resolveFallbackBaseDirectory(context);
        if (fallbackDirectory.exists() || fallbackDirectory.mkdirs()) {
            return fallbackDirectory;
        }
        return fallbackDirectory;
    }

    public static boolean canUsePreferredBaseDirectory(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return false;
        }
        File preferredDirectory = resolvePreferredBaseDirectory(context);
        if (preferredDirectory.exists()) {
            return true;
        }
        return preferredDirectory.mkdirs();
    }

    @NonNull
    public static File resolveLogsDirectory(@NonNull Context context) {
        File directory = new File(resolveBaseDirectory(context), "log");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    @NonNull
    public static File resolveHistoryDirectory(@NonNull Context context) {
        File directory = new File(resolveBaseDirectory(context), "history");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private static File resolvePreferredBaseDirectory(@NonNull Context context) {
        return new File(Environment.getExternalStorageDirectory(), buildAppFolderName(context));
    }

    @NonNull
    private static File resolveFallbackBaseDirectory(@NonNull Context context) {
        File externalFilesDirectory = context.getExternalFilesDir(null);
        if (externalFilesDirectory != null) {
            return new File(externalFilesDirectory, buildAppFolderName(context));
        }
        return new File(context.getFilesDir(), buildAppFolderName(context));
    }

    @NonNull
    private static String buildAppFolderName(@NonNull Context context) {
        CharSequence applicationLabel = context.getApplicationInfo().loadLabel(context.getPackageManager());
        String normalized = applicationLabel == null ? "Reader RX" : applicationLabel.toString().trim();
        if (normalized.isEmpty()) {
            normalized = "Reader RX";
        }
        return normalized.replace("/", "_");
    }
}
