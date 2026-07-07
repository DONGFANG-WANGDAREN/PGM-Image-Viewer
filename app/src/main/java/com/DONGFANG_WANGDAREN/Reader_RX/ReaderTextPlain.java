package com.DONGFANG_WANGDAREN.Reader_RX;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ReaderTextPlain {

    private static final String TAG = "ReaderTextPlain";

    private ReaderTextPlain() {
    }

    @NonNull
    public static String readUtf8(@NonNull InputStream inputStream) throws IOException {
        return readUtf8Preview(inputStream, Integer.MAX_VALUE).textContent;
    }

    @NonNull
    public static PreviewTextResult readUtf8Preview(@NonNull InputStream inputStream, int maxCharacters) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8)
        );
        StringBuilder contentBuilder = new StringBuilder();
        String line;
        boolean isFirstLine = true;
        boolean truncated = false;

        while ((line = bufferedReader.readLine()) != null) {
            String linePrefix = isFirstLine ? "" : "\n";
            int projectedLength = contentBuilder.length() + linePrefix.length() + line.length();
            if (projectedLength > maxCharacters) {
                int remainingCharacters = maxCharacters - contentBuilder.length();
                if (remainingCharacters > 0 && !isFirstLine) {
                    contentBuilder.append('\n');
                    remainingCharacters--;
                }
                if (remainingCharacters > 0) {
                    contentBuilder.append(line, 0, Math.min(remainingCharacters, line.length()));
                }
                truncated = true;
                break;
            }

            if (!isFirstLine) {
                contentBuilder.append('\n');
            }
            contentBuilder.append(line);
            isFirstLine = false;
        }

        PreviewTextResult result = new PreviewTextResult(contentBuilder.toString(), truncated);
        if (truncated) {
            AppLogger.w(TAG, "Text preview truncated. limit=" + maxCharacters + ", length=" + result.textContent.length());
        } else {
            AppLogger.d(TAG, "Text read completed. length=" + result.textContent.length());
        }
        return result;
    }

    public static final class PreviewTextResult {

        @NonNull
        public final String textContent;
        public final boolean truncated;

        public PreviewTextResult(@NonNull String textContent, boolean truncated) {
            this.textContent = textContent;
            this.truncated = truncated;
        }
    }
}
