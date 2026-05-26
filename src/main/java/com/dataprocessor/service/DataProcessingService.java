package com.dataprocessor.service;

import com.dataprocessor.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataProcessingService {

    private List<DataRecord> records = new ArrayList<>();
    private DatasetMetadata metadata = null;

    public synchronized void setDataset(DatasetMetadata metadata, List<DataRecord> records) {
        this.metadata = metadata;
        this.records = new ArrayList<>(records);
    }

    public synchronized void clearDataset() {
        this.metadata = null;
        this.records.clear();
    }

    public synchronized DatasetMetadata getMetadata() {
        return metadata;
    }

    public synchronized List<DataRecord> getRecords() {
        return records;
    }

    public static class FilterRule {
        private String column;
        private String operator; // EQUALS, CONTAINS, GREATER_THAN, LESS_THAN, STARTS_WITH
        private String value;

        public FilterRule() {}

        public FilterRule(String column, String operator, String value) {
            this.column = column;
            this.operator = operator;
            this.value = value;
        }

        public String getColumn() { return column; }
        public void setColumn(String column) { this.column = column; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class QueryRequest {
        private List<FilterRule> filters = new ArrayList<>();
        private String sortBy;
        private String sortDirection = "asc"; // asc or desc
        private int page = 1;
        private int pageSize = 20;

        public List<FilterRule> getFilters() { return filters; }
        public void setFilters(List<FilterRule> filters) { this.filters = filters; }
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    public static class QueryResult {
        private final List<DataRecord> data;
        private final long totalRecords;
        private final int page;
        private final int totalPages;

        public QueryResult(List<DataRecord> data, long totalRecords, int page, int totalPages) {
            this.data = data;
            this.totalRecords = totalRecords;
            this.page = page;
            this.totalPages = totalPages;
        }

        public List<DataRecord> getData() { return data; }
        public long getTotalRecords() { return totalRecords; }
        public int getPage() { return page; }
        public int getTotalPages() { return totalPages; }
    }

    /**
     * Executes queries, filters, sorting, and pagination using high-performance Java Streams.
     */
    public synchronized QueryResult query(QueryRequest request) {
        if (metadata == null || records.isEmpty()) {
            return new QueryResult(Collections.emptyList(), 0, request.getPage(), 0);
        }

        // 1. Filter using Streams
        List<DataRecord> filtered = records.stream()
            .filter(record -> matchesFilters(record, request.getFilters(), metadata.columns()))
            .collect(Collectors.toList());

        long totalCount = filtered.size();

        // 2. Sort and Paginate using Streams
        List<DataRecord> processed = filtered.stream()
            .sorted(getComparator(request.getSortBy(), request.getSortDirection(), metadata.columns()))
            .skip((long) (request.getPage() - 1) * request.getPageSize())
            .limit(request.getPageSize())
            .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) totalCount / request.getPageSize());

        return new QueryResult(processed, totalCount, request.getPage(), totalPages);
    }

    /**
     * Aggregates stats dynamically over numerical and categorical fields using Java Streams.
     */
    public synchronized ProcessingStats calculateStatistics() {
        if (records == null || records.isEmpty() || metadata == null) {
            return new ProcessingStats(Collections.emptyMap(), Collections.emptyMap());
        }

        Map<String, ProcessingStats.NumericStats> numericStatsMap = new LinkedHashMap<>();
        Map<String, ProcessingStats.CategoricalStats> categoricalStatsMap = new LinkedHashMap<>();

        for (ColumnMetadata col : metadata.columns()) {
            String colName = col.name();
            if (col.type() == DataType.NUMERIC) {
                DoubleSummaryStatistics stats = records.stream()
                    .map(r -> r.getValue(colName))
                    .filter(Objects::nonNull)
                    .mapToDouble(v -> (Double) v)
                    .summaryStatistics();

                numericStatsMap.put(colName, new ProcessingStats.NumericStats(
                    stats.getSum(),
                    stats.getAverage(),
                    stats.getMin() == Double.POSITIVE_INFINITY ? 0 : stats.getMin(),
                    stats.getMax() == Double.NEGATIVE_INFINITY ? 0 : stats.getMax(),
                    stats.getCount()
                ));
            } else {
                Map<String, Long> distribution = records.stream()
                    .map(r -> r.getValue(colName))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

                long uniqueCount = distribution.size();

                // Get top 10 most frequent items using streams
                Map<String, Long> topDistribution = distribution.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                    ));

                categoricalStatsMap.put(colName, new ProcessingStats.CategoricalStats(
                    uniqueCount,
                    topDistribution
                ));
            }
        }

        return new ProcessingStats(numericStatsMap, categoricalStatsMap);
    }

    private boolean matchesFilters(DataRecord record, List<FilterRule> filters, List<ColumnMetadata> columns) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }

        for (FilterRule filter : filters) {
            String colName = filter.getColumn();
            String op = filter.getOperator();
            String targetVal = filter.getValue();

            if (colName == null || op == null || targetVal == null) continue;

            Optional<ColumnMetadata> colOpt = columns.stream()
                .filter(c -> c.name().equalsIgnoreCase(colName))
                .findFirst();
            if (colOpt.isEmpty()) continue;

            DataType type = colOpt.get().type();
            Object actualVal = record.getValue(colOpt.get().name());

            if (actualVal == null) {
                return false;
            }

            boolean matched = false;
            switch (type) {
                case NUMERIC:
                    try {
                        double actNum = (Double) actualVal;
                        double targetNum = Double.parseDouble(targetVal);
                        switch (op.toUpperCase()) {
                            case "EQUALS":
                                matched = Math.abs(actNum - targetNum) < 1e-9;
                                break;
                            case "GREATER_THAN":
                                matched = actNum > targetNum;
                                break;
                            case "LESS_THAN":
                                matched = actNum < targetNum;
                                break;
                            default:
                                matched = false;
                        }
                    } catch (Exception e) {
                        matched = false;
                    }
                    break;

                case DATE:
                    try {
                        LocalDate actDate = (LocalDate) actualVal;
                        LocalDate targetDate = LocalDate.parse(targetVal);
                        switch (op.toUpperCase()) {
                            case "EQUALS":
                                matched = actDate.equals(targetDate);
                                break;
                            case "GREATER_THAN":
                                matched = actDate.isAfter(targetDate);
                                break;
                            case "LESS_THAN":
                                matched = actDate.isBefore(targetDate);
                                break;
                            default:
                                matched = false;
                        }
                    } catch (Exception e) {
                        matched = false;
                    }
                    break;

                case BOOLEAN:
                    try {
                        boolean actBool = (Boolean) actualVal;
                        boolean targetBool = Boolean.parseBoolean(targetVal);
                        if (op.equalsIgnoreCase("EQUALS")) {
                            matched = actBool == targetBool;
                        }
                    } catch (Exception e) {
                        matched = false;
                    }
                    break;

                case STRING:
                default:
                    String actStr = actualVal.toString().toLowerCase();
                    String targetStr = targetVal.toLowerCase();
                    switch (op.toUpperCase()) {
                        case "EQUALS":
                            matched = actStr.equals(targetStr);
                            break;
                        case "CONTAINS":
                            matched = actStr.contains(targetStr);
                            break;
                        case "STARTS_WITH":
                            matched = actStr.startsWith(targetStr);
                            break;
                        default:
                            matched = false;
                    }
                    break;
            }

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private Comparator<DataRecord> getComparator(String sortBy, String direction, List<ColumnMetadata> columns) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            return Comparator.comparing(DataRecord::getId, Comparator.comparingInt(Integer::parseInt));
        }

        Optional<ColumnMetadata> colOpt = columns.stream()
            .filter(c -> c.name().equalsIgnoreCase(sortBy))
            .findFirst();

        if (colOpt.isEmpty()) {
            return Comparator.comparing(DataRecord::getId, Comparator.comparingInt(Integer::parseInt));
        }

        ColumnMetadata column = colOpt.get();
        String colName = column.name();
        DataType type = column.type();

        Comparator<DataRecord> comp = (r1, r2) -> {
            Object v1 = r1.getValue(colName);
            Object v2 = r2.getValue(colName);

            if (v1 == null && v2 == null) return 0;
            if (v1 == null) return 1; // nulls last
            if (v2 == null) return -1;

            switch (type) {
                case NUMERIC:
                    return Double.compare((Double) v1, (Double) v2);
                case BOOLEAN:
                    return Boolean.compare((Boolean) v1, (Boolean) v2);
                case DATE:
                    return ((LocalDate) v1).compareTo((LocalDate) v2);
                case STRING:
                default:
                    return ((String) v1).compareToIgnoreCase((String) v2);
            }
        };

        if ("desc".equalsIgnoreCase(direction)) {
            comp = comp.reversed();
        }

        return comp;
    }
}
