package com.dataprocessor.model;

import java.util.Map;

public class ProcessingStats {
    public static record NumericStats(
        double sum,
        double average,
        double min,
        double max,
        long count
    ) {}

    public static record CategoricalStats(
        long uniqueCount,
        Map<String, Long> valueDistribution
    ) {}

    private final Map<String, NumericStats> numericColumnStats;
    private final Map<String, CategoricalStats> categoricalColumnStats;

    public ProcessingStats(Map<String, NumericStats> numericColumnStats, Map<String, CategoricalStats> categoricalColumnStats) {
        this.numericColumnStats = numericColumnStats;
        this.categoricalColumnStats = categoricalColumnStats;
    }

    public Map<String, NumericStats> getNumericColumnStats() {
        return numericColumnStats;
    }

    public Map<String, CategoricalStats> getCategoricalColumnStats() {
        return categoricalColumnStats;
    }
}
