package com.dataprocessor.parser;

import com.dataprocessor.model.ColumnMetadata;
import com.dataprocessor.model.DataRecord;
import com.dataprocessor.model.DataType;
import com.dataprocessor.model.DatasetMetadata;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class CsvParser implements DataParser {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    @Override
    public ParserResult parse(InputStream inputStream, String fileName, long fileSize) throws Exception {
        long startTime = System.currentTimeMillis();

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

        CSVParser csvParser = new CSVParser(reader, csvFormat);
        List<String> headers = csvParser.getHeaderNames();
        List<CSVRecord> csvRecords = csvParser.getRecords();

        // 1. Accumulate raw records
        List<Map<String, String>> rawData = new ArrayList<>();
        for (CSVRecord csvRecord : csvRecords) {
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (String header : headers) {
                if (csvRecord.isMapped(header)) {
                    rowMap.put(header, csvRecord.get(header));
                } else {
                    rowMap.put(header, "");
                }
            }
            rawData.add(rowMap);
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

        csvParser.close();
        return new ParserResult(metadata, records);
    }

    private DataType inferTypeForColumn(List<Map<String, String>> rawData, String column) {
        int checkLimit = Math.min(rawData.size(), 500); // Check up to 500 rows for schema inference
        if (checkLimit == 0) return DataType.STRING;

        boolean allNumeric = true;
        boolean allBoolean = true;
        boolean allDate = true;
        int nonAttrCount = 0;

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
                for (DateTimeFormatter dtf : DATE_FORMATTERS) {
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
                for (DateTimeFormatter dtf : DATE_FORMATTERS) {
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
