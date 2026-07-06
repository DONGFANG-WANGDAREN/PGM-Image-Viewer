package com.DONGFANG_WANGDAREN.PGM_Image_Viewer;

import androidx.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public final class ParserPicturePgm {

    private static final String TAG = "ParserPicturePgm";

    private ParserPicturePgm() {
    }

    @NonNull
    public static DataPicturePgm parse(@NonNull InputStream inputStream) throws IOException {
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

        String magicNumber = readToken(bufferedInputStream);
        if (!"P2".equals(magicNumber) && !"P5".equals(magicNumber)) {
            throw new IOException("Unsupported PGM format.");
        }

        int width = parsePositiveInt(readToken(bufferedInputStream), "width");
        int height = parsePositiveInt(readToken(bufferedInputStream), "height");
        int maxValue = parsePositiveInt(readToken(bufferedInputStream), "max value");

        if (maxValue > 65535) {
            throw new IOException("Unsupported max value.");
        }

        long pixelCountLong = (long) width * (long) height;
        if (pixelCountLong <= 0L || pixelCountLong > Integer.MAX_VALUE) {
            throw new IOException("Image is too large.");
        }

        int pixelCount = (int) pixelCountLong;
        int[] argbPixels = "P2".equals(magicNumber)
                ? parseAsciiPixels(bufferedInputStream, pixelCount, maxValue)
                : parseBinaryPixels(bufferedInputStream, pixelCount, maxValue);

        AppLogger.i(TAG, "PGM parsed. magic=" + magicNumber + ", width=" + width + ", height=" + height + ", maxValue=" + maxValue);
        return new DataPicturePgm(width, height, argbPixels);
    }

    @NonNull
    private static int[] parseAsciiPixels(InputStream inputStream, int pixelCount, int maxValue) throws IOException {
        int[] argbPixels = new int[pixelCount];
        for (int index = 0; index < pixelCount; index++) {
            int sample = parsePixelValue(readToken(inputStream), maxValue);
            argbPixels[index] = toArgb(scaleToByte(sample, maxValue));
        }
        return argbPixels;
    }

    @NonNull
    private static int[] parseBinaryPixels(InputStream inputStream, int pixelCount, int maxValue) throws IOException {
        int[] argbPixels = new int[pixelCount];
        boolean usesTwoBytes = maxValue > 255;

        for (int index = 0; index < pixelCount; index++) {
            int sample;
            if (usesTwoBytes) {
                int high = readRequiredByte(inputStream);
                int low = readRequiredByte(inputStream);
                sample = (high << 8) | low;
            } else {
                sample = readRequiredByte(inputStream);
            }

            if (sample < 0 || sample > maxValue) {
                throw new IOException("Invalid pixel value.");
            }
            argbPixels[index] = toArgb(scaleToByte(sample, maxValue));
        }

        return argbPixels;
    }

    @NonNull
    private static String readToken(InputStream inputStream) throws IOException {
        StringBuilder tokenBuilder = new StringBuilder();
        int currentByte;

        while (true) {
            currentByte = inputStream.read();
            if (currentByte == -1) {
                throw new EOFException("Unexpected end of file.");
            }
            if (Character.isWhitespace(currentByte)) {
                continue;
            }
            if (currentByte == '#') {
                skipComment(inputStream);
                continue;
            }
            break;
        }

        tokenBuilder.append((char) currentByte);

        while (true) {
            currentByte = inputStream.read();
            if (currentByte == -1 || Character.isWhitespace(currentByte)) {
                break;
            }
            if (currentByte == '#') {
                skipComment(inputStream);
                break;
            }
            tokenBuilder.append((char) currentByte);
        }

        return tokenBuilder.toString();
    }

    private static void skipComment(InputStream inputStream) throws IOException {
        int currentByte;
        while ((currentByte = inputStream.read()) != -1) {
            if (currentByte == '\n' || currentByte == '\r') {
                return;
            }
        }
    }

    private static int parsePositiveInt(String value, String label) throws IOException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IOException("Invalid " + label + ".");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid " + label + ".", exception);
        }
    }

    private static int parsePixelValue(String value, int maxValue) throws IOException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > maxValue) {
                throw new IOException("Pixel value out of range.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid pixel value.", exception);
        }
    }

    private static int readRequiredByte(InputStream inputStream) throws IOException {
        int value = inputStream.read();
        if (value == -1) {
            throw new EOFException("Unexpected end of binary data.");
        }
        return value;
    }

    private static int scaleToByte(int sample, int maxValue) {
        return (int) ((sample * 255L + (maxValue / 2L)) / maxValue);
    }

    private static int toArgb(int grayscale) {
        return 0xFF000000 | (grayscale << 16) | (grayscale << 8) | grayscale;
    }

    public static final class DataPicturePgm {
        private final int width;
        private final int height;
        private final int[] argbPixels;

        DataPicturePgm(int width, int height, int[] argbPixels) {
            this.width = width;
            this.height = height;
            this.argbPixels = argbPixels;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        @NonNull
        public int[] getArgbPixels() {
            return argbPixels.clone();
        }
    }
}
