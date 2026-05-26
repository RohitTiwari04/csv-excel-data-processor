package com.dataprocessor.controller;

import com.dataprocessor.model.ColumnMetadata;
import com.dataprocessor.model.DatasetMetadata;
import com.dataprocessor.model.ProcessingStats;
import com.dataprocessor.parser.CsvParser;
import com.dataprocessor.parser.DataParser;
import com.dataprocessor.parser.ExcelParser;
import com.dataprocessor.service.DataProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataController {

    private final DataProcessingService processingService;

    public DataController(DataProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty. Please select a valid CSV or Excel file."));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            fileName = "uploaded_file";
        }

        long fileSize = file.getSize();
        String lowercaseName = fileName.toLowerCase();
        
        DataParser parser;
        if (lowercaseName.endsWith(".csv")) {
            parser = new CsvParser();
        } else if (lowercaseName.endsWith(".xlsx") || lowercaseName.endsWith(".xls")) {
            parser = new ExcelParser();
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported file type. Only CSV and Excel (.xlsx, .xls) files are supported."));
        }

        try (InputStream is = file.getInputStream()) {
            DataParser.ParserResult result = parser.parse(is, fileName, fileSize);
            processingService.setDataset(result.getMetadata(), result.getRecords());
            return ResponseEntity.ok(result.getMetadata());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error parsing file: " + e.getMessage()));
        }
    }

    @GetMapping("/columns")
    public ResponseEntity<?> getColumns() {
        DatasetMetadata metadata = processingService.getMetadata();
        if (metadata == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(metadata.columns());
    }

    @PostMapping("/query")
    public ResponseEntity<?> queryData(@RequestBody DataProcessingService.QueryRequest request) {
        DatasetMetadata metadata = processingService.getMetadata();
        if (metadata == null) {
            return ResponseEntity.ok(new DataProcessingService.QueryResult(Collections.emptyList(), 0, request.getPage(), 0));
        }
        return ResponseEntity.ok(processingService.query(request));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStatistics() {
        DatasetMetadata metadata = processingService.getMetadata();
        if (metadata == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "No active dataset loaded. Please upload a file first."));
        }
        try {
            ProcessingStats stats = processingService.calculateStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to calculate statistics: " + e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        processingService.clearDataset();
        return ResponseEntity.ok(Map.of("message", "Dataset cleared successfully."));
    }
}
