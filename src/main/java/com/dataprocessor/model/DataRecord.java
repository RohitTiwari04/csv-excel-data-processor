package com.dataprocessor.model;

import java.util.Map;

public class DataRecord {
    private final String id;
    private final Map<String, Object> values;

    public DataRecord(String id, Map<String, Object> values) {
        this.id = id;
        this.values = values;
    }

    public String getId() {
        return id;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Object getValue(String columnName) {
        return values.get(columnName);
    }
}
