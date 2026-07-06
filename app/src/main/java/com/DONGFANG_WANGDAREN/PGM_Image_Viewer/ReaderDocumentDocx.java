package com.DONGFANG_WANGDAREN.PGM_Image_Viewer;

import android.util.Xml;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public final class ReaderDocumentDocx {

    private static final String TAG = "ReaderDocumentDocx";
    private static final String ENTRY_DOCUMENT_XML = "word/document.xml";
    private static final String TAG_TEXT = "t";
    private static final String TAG_PARAGRAPH = "p";
    private static final String TAG_BREAK = "br";
    private static final String TAG_TAB = "tab";

    private ReaderDocumentDocx() {
    }

    @NonNull
    public static String readText(@NonNull InputStream inputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (!ENTRY_DOCUMENT_XML.equals(zipEntry.getName())) {
                    continue;
                }

                String text = normalizeExtractedText(parseDocumentXml(zipInputStream));
                AppLogger.i(TAG, "DOCX text extracted. length=" + text.length());
                return text;
            }
            throw new IOException("DOCX document.xml entry not found.");
        } catch (IOException | RuntimeException | XmlPullParserException exception) {
            AppLogger.e(TAG, "DOCX extraction failed.", exception);
            if (exception instanceof IOException) {
                throw (IOException) exception;
            }
            throw new IOException("DOCX extraction failed.", exception);
        }
    }

    public static boolean isDocxFile(@NonNull InputStream inputStream) {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (ENTRY_DOCUMENT_XML.equals(zipEntry.getName())) {
                    return true;
                }
            }
        } catch (IOException exception) {
            AppLogger.e(TAG, "DOCX probe failed.", exception);
            return false;
        }
        return false;
    }

    @NonNull
    private static String parseDocumentXml(@NonNull InputStream inputStream) throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(inputStream, "UTF-8");

        StringBuilder textBuilder = new StringBuilder();
        boolean paragraphHasContent = false;
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if (TAG_TEXT.equals(tagName)) {
                    String text = parser.nextText();
                    if (!text.isEmpty()) {
                        textBuilder.append(text);
                        paragraphHasContent = true;
                    }
                    eventType = parser.getEventType();
                    continue;
                }
                if (TAG_TAB.equals(tagName)) {
                    textBuilder.append('\t');
                    paragraphHasContent = true;
                } else if (TAG_BREAK.equals(tagName)) {
                    textBuilder.append('\n');
                    paragraphHasContent = true;
                }
            } else if (eventType == XmlPullParser.END_TAG && TAG_PARAGRAPH.equals(parser.getName())) {
                if (paragraphHasContent) {
                    textBuilder.append('\n');
                    paragraphHasContent = false;
                }
            }
            eventType = parser.next();
        }

        return textBuilder.toString();
    }

    @NonNull
    private static String normalizeExtractedText(@NonNull String value) {
        return value
                .replace("\r", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
