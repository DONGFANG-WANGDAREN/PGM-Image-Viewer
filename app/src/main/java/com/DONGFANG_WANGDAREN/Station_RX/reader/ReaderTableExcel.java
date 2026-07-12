package com.DONGFANG_WANGDAREN.Station_RX.reader;

import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public final class ReaderTableExcel {

    private static final String TAG = "ReaderTableExcel";
    private static final String XLSX_ENTRY_WORKBOOK = "xl/workbook.xml";
    private static final String XLSX_ENTRY_WORKBOOK_RELS = "xl/_rels/workbook.xml.rels";
    private static final String XLSX_ENTRY_SHARED_STRINGS = "xl/sharedStrings.xml";

    private ReaderTableExcel() {
    }

    @NonNull
    public static ReaderTextPlain.PreviewTextResult readPreview(
            @NonNull InputStream inputStream,
            @NonNull String fileName,
            int maxCharacters
    ) throws IOException {
        String normalizedFileName = fileName.toLowerCase(Locale.US);
        if (normalizedFileName.endsWith(".xls")) {
            return readXlsPreview(inputStream, maxCharacters);
        }
        return readXlsxPreview(inputStream, maxCharacters);
    }

    @NonNull
    public static ReaderTextPlain.PreviewTextResult readAll(
            @NonNull InputStream inputStream,
            @NonNull String fileName
    ) throws IOException {
        return readPreview(inputStream, fileName, Integer.MAX_VALUE);
    }

    @NonNull
    private static ReaderTextPlain.PreviewTextResult readXlsPreview(
            @NonNull InputStream inputStream,
            int maxCharacters
    ) throws IOException {
        Workbook workbook = null;
        try {
            workbook = Workbook.getWorkbook(inputStream);
            StringBuilder builder = new StringBuilder();
            boolean truncated = false;
            Sheet[] sheets = workbook.getSheets();
            for (int sheetIndex = 0; sheetIndex < sheets.length; sheetIndex++) {
                Sheet sheet = sheets[sheetIndex];
                if (!appendLine(builder, "[Sheet] " + sheet.getName(), maxCharacters)) {
                    truncated = true;
                    break;
                }
                for (int row = 0; row < sheet.getRows(); row++) {
                    StringBuilder rowBuilder = new StringBuilder();
                    for (int column = 0; column < sheet.getColumns(); column++) {
                        if (column > 0) {
                            rowBuilder.append('\t');
                        }
                        Cell cell = sheet.getCell(column, row);
                        rowBuilder.append(normalizeCellValue(cell.getContents()));
                    }
                    if (!appendLine(builder, rowBuilder.toString(), maxCharacters)) {
                        truncated = true;
                        break;
                    }
                }
                if (truncated) {
                    break;
                }
                if (sheetIndex < sheets.length - 1 && !appendLine(builder, "", maxCharacters)) {
                    truncated = true;
                    break;
                }
            }
            return new ReaderTextPlain.PreviewTextResult(builder.toString(), truncated);
        } catch (BiffException exception) {
            throw new IOException("Failed to read XLS file.", exception);
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    @NonNull
    private static ReaderTextPlain.PreviewTextResult readXlsxPreview(
            @NonNull InputStream inputStream,
            int maxCharacters
    ) throws IOException {
        try {
            Map<String, byte[]> entries = unzipEntries(inputStream);
            ArrayList<String> sharedStrings = parseSharedStrings(entries.get(XLSX_ENTRY_SHARED_STRINGS));
            Map<String, String> sheetTargets = parseWorkbookRelationships(entries.get(XLSX_ENTRY_WORKBOOK_RELS));
            ArrayList<SheetDescriptor> sheets = parseWorkbook(entries.get(XLSX_ENTRY_WORKBOOK), sheetTargets);

            StringBuilder builder = new StringBuilder();
            boolean truncated = false;
            for (int index = 0; index < sheets.size(); index++) {
                SheetDescriptor sheetDescriptor = sheets.get(index);
                if (!appendLine(builder, "[Sheet] " + sheetDescriptor.name, maxCharacters)) {
                    truncated = true;
                    break;
                }
                byte[] sheetBytes = entries.get(sheetDescriptor.targetPath);
                if (sheetBytes == null) {
                    if (!appendLine(builder, "[Missing worksheet data]", maxCharacters)) {
                        truncated = true;
                    }
                } else if (!appendSheetRows(builder, sheetBytes, sharedStrings, maxCharacters)) {
                    truncated = true;
                }
                if (truncated) {
                    break;
                }
                if (index < sheets.size() - 1 && !appendLine(builder, "", maxCharacters)) {
                    truncated = true;
                    break;
                }
            }
            return new ReaderTextPlain.PreviewTextResult(builder.toString(), truncated);
        } catch (XmlPullParserException exception) {
            throw new IOException("Failed to read XLSX file.", exception);
        }
    }

    @NonNull
    private static Map<String, byte[]> unzipEntries(@NonNull InputStream inputStream) throws IOException {
        HashMap<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[8192];
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                int readCount;
                while ((readCount = zipInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, readCount);
                }
                entries.put(zipEntry.getName(), outputStream.toByteArray());
            }
        }
        return entries;
    }

    @NonNull
    private static ArrayList<String> parseSharedStrings(@Nullable byte[] sharedStringsBytes)
            throws IOException, XmlPullParserException {
        ArrayList<String> sharedStrings = new ArrayList<>();
        if (sharedStringsBytes == null) {
            return sharedStrings;
        }
        XmlPullParser parser = newParser(sharedStringsBytes);
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "si".equals(parser.getName())) {
                sharedStrings.add(parseSharedStringItem(parser));
            }
            eventType = parser.next();
        }
        return sharedStrings;
    }

    @NonNull
    private static String parseSharedStringItem(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        StringBuilder builder = new StringBuilder();
        int depth = parser.getDepth();
        int eventType = parser.next();
        while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth && "si".equals(parser.getName()))) {
            if (eventType == XmlPullParser.START_TAG && "t".equals(parser.getName())) {
                builder.append(parser.nextText());
                eventType = parser.getEventType();
                continue;
            }
            eventType = parser.next();
        }
        return builder.toString();
    }

    @NonNull
    private static Map<String, String> parseWorkbookRelationships(@Nullable byte[] relBytes)
            throws IOException, XmlPullParserException {
        HashMap<String, String> relationships = new HashMap<>();
        if (relBytes == null) {
            return relationships;
        }
        XmlPullParser parser = newParser(relBytes);
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "Relationship".equals(parser.getName())) {
                String id = parser.getAttributeValue(null, "Id");
                String target = parser.getAttributeValue(null, "Target");
                if (id != null && target != null) {
                    relationships.put(id, normalizeWorksheetTarget(target));
                }
            }
            eventType = parser.next();
        }
        return relationships;
    }

    @NonNull
    private static ArrayList<SheetDescriptor> parseWorkbook(
            @Nullable byte[] workbookBytes,
            @NonNull Map<String, String> relationships
    ) throws IOException, XmlPullParserException {
        if (workbookBytes == null) {
            throw new IOException("Workbook metadata not found.");
        }
        ArrayList<SheetDescriptor> sheets = new ArrayList<>();
        XmlPullParser parser = newParser(workbookBytes);
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "sheet".equals(parser.getName())) {
                String name = parser.getAttributeValue(null, "name");
                String relationshipId = getRelationshipId(parser);
                if (name != null && relationshipId != null) {
                    String targetPath = relationships.get(relationshipId);
                    if (targetPath != null) {
                        sheets.add(new SheetDescriptor(name, targetPath));
                    }
                }
            }
            eventType = parser.next();
        }
        return sheets;
    }

    private static boolean appendSheetRows(
            @NonNull StringBuilder builder,
            @NonNull byte[] sheetBytes,
            @NonNull ArrayList<String> sharedStrings,
            int maxCharacters
    ) throws IOException, XmlPullParserException {
        XmlPullParser parser = newParser(sheetBytes);
        TreeMap<Integer, String> rowCells = new TreeMap<>();
        String currentCellType = null;
        String currentCellRef = null;
        String currentCellValue = "";
        int rowDepth = -1;
        int cellDepth = -1;
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tagName = parser.getName();
                if ("row".equals(tagName)) {
                    rowCells.clear();
                    rowDepth = parser.getDepth();
                } else if ("c".equals(tagName)) {
                    currentCellType = parser.getAttributeValue(null, "t");
                    currentCellRef = parser.getAttributeValue(null, "r");
                    currentCellValue = "";
                    cellDepth = parser.getDepth();
                } else if ("v".equals(tagName)) {
                    currentCellValue = parser.nextText();
                    eventType = parser.getEventType();
                    continue;
                } else if ("t".equals(tagName) && "inlineStr".equals(currentCellType)) {
                    currentCellValue = parser.nextText();
                    eventType = parser.getEventType();
                    continue;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tagName = parser.getName();
                if ("c".equals(tagName) && parser.getDepth() == cellDepth) {
                    int columnIndex = parseColumnIndex(currentCellRef);
                    rowCells.put(columnIndex, resolveCellValue(currentCellType, currentCellValue, sharedStrings));
                } else if ("row".equals(tagName) && parser.getDepth() == rowDepth) {
                    if (!appendLine(builder, buildRowText(rowCells), maxCharacters)) {
                        return false;
                    }
                }
            }
            eventType = parser.next();
        }
        return true;
    }

    @NonNull
    private static String buildRowText(@NonNull TreeMap<Integer, String> rowCells) {
        if (rowCells.isEmpty()) {
            return "";
        }
        StringBuilder rowBuilder = new StringBuilder();
        int lastColumnIndex = rowCells.lastKey();
        for (int columnIndex = 0; columnIndex <= lastColumnIndex; columnIndex++) {
            if (columnIndex > 0) {
                rowBuilder.append('\t');
            }
            String value = rowCells.get(columnIndex);
            if (value != null) {
                rowBuilder.append(normalizeCellValue(value));
            }
        }
        return rowBuilder.toString();
    }

    @NonNull
    private static String resolveCellValue(
            @Nullable String cellType,
            @Nullable String rawValue,
            @NonNull ArrayList<String> sharedStrings
    ) {
        if (rawValue == null) {
            return "";
        }
        if ("s".equals(cellType)) {
            try {
                int index = Integer.parseInt(rawValue);
                if (index >= 0 && index < sharedStrings.size()) {
                    return sharedStrings.get(index);
                }
            } catch (NumberFormatException ignored) {
                return rawValue;
            }
        }
        if ("b".equals(cellType)) {
            return "1".equals(rawValue) ? "TRUE" : "FALSE";
        }
        return rawValue;
    }

    private static boolean appendLine(@NonNull StringBuilder builder, @NonNull String line, int maxCharacters) {
        int extraLength = line.length();
        if (builder.length() > 0) {
            extraLength += 1;
        }
        if (builder.length() + extraLength > maxCharacters) {
            int remain = maxCharacters - builder.length();
            if (remain <= 0) {
                return false;
            }
            if (builder.length() > 0 && remain > 0) {
                builder.append('\n');
                remain--;
            }
            if (remain > 0) {
                builder.append(line, 0, Math.min(remain, line.length()));
            }
            return false;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
        return true;
    }

    private static int parseColumnIndex(@Nullable String cellReference) {
        if (cellReference == null || cellReference.isEmpty()) {
            return 0;
        }
        int value = 0;
        int length = cellReference.length();
        for (int index = 0; index < length; index++) {
            char character = cellReference.charAt(index);
            if (!Character.isLetter(character)) {
                break;
            }
            value = (value * 26) + (Character.toUpperCase(character) - 'A' + 1);
        }
        return Math.max(0, value - 1);
    }

    @NonNull
    private static String normalizeWorksheetTarget(@NonNull String target) {
        if (target.startsWith("/")) {
            return target.substring(1);
        }
        if (target.startsWith("xl/")) {
            return target;
        }
        return "xl/" + target;
    }

    @Nullable
    private static String getRelationshipId(@NonNull XmlPullParser parser) {
        for (int index = 0; index < parser.getAttributeCount(); index++) {
            String attributeName = parser.getAttributeName(index);
            if ("id".equals(attributeName)) {
                return parser.getAttributeValue(index);
            }
        }
        return null;
    }

    @NonNull
    private static XmlPullParser newParser(@NonNull byte[] bytes) throws XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(new ByteArrayInputStream(bytes), "UTF-8");
        return parser;
    }

    @NonNull
    private static String normalizeCellValue(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static final class SheetDescriptor {

        @NonNull
        private final String name;
        @NonNull
        private final String targetPath;

        private SheetDescriptor(@NonNull String name, @NonNull String targetPath) {
            this.name = name;
            this.targetPath = targetPath;
        }
    }
}
