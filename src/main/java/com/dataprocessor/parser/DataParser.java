package com.dataprocessor.parser;

import com.dataprocessor.model.DataRecord;
import com.dataprocessor.model.DatasetMetadata;
import java.io.InputStream;
import java.util.List;

public interface DataParser {
    class ParserResult {
        private final DatasetMetadata metadata;
        private final List<DataRecord> records;

        public ParserResult(DatasetMetadata metadata, List<DataRecord> records) {
            this.metadata = metadata;
            this.records = records;
        }

        public DatasetMetadata getMetadata() {
            return metadata;
        }

        public List<DataRecord> getRecords() {
            return records;
        }
    }

    ParserResult parse(InputStream inputStream, String fileName, long fileSize) throws Exception;
}
