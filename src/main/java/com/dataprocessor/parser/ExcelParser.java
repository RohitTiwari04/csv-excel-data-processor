package com.dataprocessor.parser;

import com.dataprocessor.model.ColumnMetadata;
import com.dataprocessor.model.DataRecord;
import com.dataprocessor.model.DataType;
import com.dataprocessor.model.DatasetMetadata;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ExcelParser implements DataParser {

    @Override
    public ParserResult parse(InputStream inputStream, String fileName, long fileSize) throws Exception {
        long startTime = System.currentTimeMillis();

        Workbook workbook = WorkbookFactory.create(inputStream);
        if (workbook.getNumberOfSheets() == 0) {
            workbook.close();
            throw new IllegalArgumentException("Excel file contains no sheets.");
        }

        Sheet sheet = workbook.getSheetAt(0);
        int lastRowNum = sheet.getLastRowNum();
        if (lastRowNum < 0) {
            workbook.close();
            throw new IllegalArgumentException("First sheet in Excel file is empty.");
        }

        Row headerRow = null;
        for (int i = 0; i <= lastRowNum; i++) {
            Row r = sheet.getRow(i);
            if (r != null && r.getPhysicalNumberOfCells() > 0) {
                headerRow = r;
                break;
            }
        }

        if (headerRow == null) {
            workbook.close();
            throw new IllegalArgumentException("No header row found in Excel sheet.");
        }

        int headerRowNum = headerRow.getRowNum();
        List<String> headers = new ArrayList<>();
        Map<Integer, String> cellIdxToHeader = new HashMap<>();

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            String headerVal = getCellStringValue(cell).trim();
            if (headerVal.isEmpty()) {
                headerVal = "Column_" + (c + 1);
            }
            headers.add(headerVal);
            cellIdxToHeader.put(c, headerVal);
        }

        // 1. Accumulate raw records
        List<Map<String, String>> rawData = new ArrayList<>();
        for (int r = headerRowNum + 1; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            boolean isEmptyRow = true;
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                String cellVal = getCellStringValue(cell);
                if (cellVal != null && !cellVal.trim().isEmpty()) {
                    isEmptyRow = false;
                }
                rowMap.put(cellIdxToHeader.get(c), cellVal);
            }

            if (!isEmptyRow) {
                rawData.add(rowMap);
            }
        }

        // 2. Infer column data types
        Map<String, DataType> inferredTypes = new LinkedHashMap<>();
        for (String header : headers) {
            inferredTypes.put(header, inferTypeForColumn(rawData, header));
        }

        // 3. Convert raw values to typed objects
        List<DataRecord> records = new ArrayList<>();
        int recordId = 1;
        for (Map<String, String> rawRow : rawData) {
            Map<String, Object> typedValues = new LinkedHashMap<>();
            for (String header : headers) {
                String rawVal = rawRow.get(header);
                typedValues.put(header, parseValue(rawVal, inferredTypes.get(header)));
            }
            records.add(new DataRecord(String.valueOf(recordId++), typedValues));
        }

        long endTime = System.currentTimeMillis();
        long parseTimeMs = endTime - startTime;

        // 4. Construct metadata
        List<ColumnMetadata> columns = new ArrayList<>();
        for (String header : headers) {
            columns.add(new ColumnMetadata(header, inferredTypes.get(header)));
        }

        DatasetMetadata metadata = new DatasetMetadata(
            fileName,
            fileSize,
            records.size(),
            parseTimeMs,
            columns
        );

        workbook.close();
        return new ParserResult(metadata, records);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    if (date != null) {
                        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                        return localDate.toString();
                    }
                }
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                    CellValue cellValue = evaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case STRING:
                            return cellValue.getStringValue();
                        case NUMERIC:
                            double fNum = cellValue.getNumberValue();
                            if (fNum == Math.floor(fNum)) {
                                return String.valueOf((long) fNum);
                            }
                            return String.valueOf(fNum);
                        case BOOLEAN:
                            return String.valueOf(cellValue.getBooleanValue());
                        default:
                            return "";
                    }
                } catch (Exception e) {
                    return "";
                }
            case BLANK:
            default:
                return "";
        }
    }

    private DataType inferTypeForColumn(List<Map<String, String>> rawData, String column) {
        int checkLimit = Math.min(rawData.size(), 500);
        if (checkLimit == 0) return DataType.STRING;

        boolean allNumeric = true;
        boolean allBoolean = true;
        boolean allDate = true;
        int nonAttrCount = 0;

        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (int i = 0; i < checkLimit; i++) {
            String val = rawData.get(i).get(column);
            if (val == null || val.trim().isEmpty()) {
                continue;
            }
            val = val.trim();
            nonAttrCount++;

            if (allNumeric) {
                try {
                    Double.parseDouble(val);
                } catch (NumberFormatException e) {
                    allNumeric = false;
                }
            }

            if (allBoolean) {
                String valLower = val.toLowerCase();
                if (!valLower.equals("true") && !valLower.equals("false") && 
                    !valLower.equals("yes") && !valLower.equals("no") &&
                    !valLower.equals("1") && !valLower.equals("0")) {
                    allBoolean = false;
                }
            }

            if (allDate) {
                boolean parsedDate = false;
                for (DateTimeFormatter dtf : formatters) {
                    try {
                        LocalDate.parse(val, dtf);
                        parsedDate = true;
                        break;
                    } catch (DateTimeParseException ignored) {}
                }
                if (!parsedDate) {
                    allDate = false;
                }
            }
        }

        if (nonAttrCount == 0) {
            return DataType.STRING;
        }

        if (allNumeric) return DataType.NUMERIC;
        if (allBoolean) return DataType.BOOLEAN;
        if (allDate) return DataType.DATE;
        return DataType.STRING;
    }

    private Object parseValue(String value, DataType type) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();

        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
        );

        switch (type) {
            case NUMERIC:
                try {
                    return Double.parseDouble(trimmed);
                } catch (NumberFormatException e) {
                    return null;
                }
            case BOOLEAN:
                String valLower = trimmed.toLowerCase();
                if (valLower.equals("true") || valLower.equals("yes") || valLower.equals("1")) {
                    return Boolean.TRUE;
                }
                if (valLower.equals("false") || valLower.equals("no") || valLower.equals("0")) {
                    return Boolean.FALSE;
                }
                return null;
            case DATE:
                for (DateTimeFormatter dtf : formatters) {
                    try {
                        return LocalDate.parse(trimmed, dtf);
                    } catch (DateTimeParseException ignored) {}
                }
                return null;
            case STRING:
            default:
                return trimmed;
        }
    }
}
