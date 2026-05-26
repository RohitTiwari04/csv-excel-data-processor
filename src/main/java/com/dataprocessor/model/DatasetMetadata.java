package com.dataprocessor.model;

import java.util.List;

public record DatasetMetadata(
    String fileName,
    long fileSize,
    long rowCount,
    long parseTimeMs,
    List<ColumnMetadata> columns
) {}
