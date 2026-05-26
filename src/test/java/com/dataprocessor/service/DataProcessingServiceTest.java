package com.dataprocessor.service;

import com.dataprocessor.model.ColumnMetadata;
import com.dataprocessor.model.DataRecord;
import com.dataprocessor.model.DataType;
import com.dataprocessor.model.DatasetMetadata;
import com.dataprocessor.model.ProcessingStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DataProcessingServiceTest {

    private DataProcessingService service;

    @BeforeEach
    public void setup() {
        service = new DataProcessingService();

        List<ColumnMetadata> columns = Arrays.asList(
            new ColumnMetadata("Name", DataType.STRING),
            new ColumnMetadata("Department", DataType.STRING),
            new ColumnMetadata("Salary", DataType.NUMERIC),
            new ColumnMetadata("HireDate", DataType.DATE),
            new ColumnMetadata("Active", DataType.BOOLEAN)
        );

        DatasetMetadata metadata = new DatasetMetadata("employees.csv", 1024, 3, 5, columns);

        List<DataRecord> records = Arrays.asList(
            new DataRecord("1", Map.of(
                "Name", "John Doe",
                "Department", "Engineering",
                "Salary", 85000.0,
                "HireDate", LocalDate.parse("2021-03-15"),
                "Active", true
            )),
            new DataRecord("2", Map.of(
                "Name", "Jane Smith",
                "Department", "Sales",
                "Salary", 95000.0,
                "HireDate", LocalDate.parse("2020-07-20"),
                "Active", true
            )),
            new DataRecord("3", Map.of(
                "Name", "Bob Johnson",
                "Department", "Engineering",
                "Salary", 60000.0,
                "HireDate", LocalDate.parse("2022-11-01"),
                "Active", false
            ))
        );

        service.setDataset(metadata, records);
    }

    @Test
    public void testFilteringAndSorting() {
        DataProcessingService.QueryRequest request = new DataProcessingService.QueryRequest();
        request.setFilters(Arrays.asList(
            new DataProcessingService.FilterRule("Department", "EQUALS", "Engineering")
        ));
        request.setSortBy("Salary");
        request.setSortDirection("desc");
        request.setPage(1);
        request.setPageSize(10);

        DataProcessingService.QueryResult result = service.query(request);

        assertEquals(2, result.getTotalRecords());
        assertEquals(2, result.getData().size());
        
        assertEquals("John Doe", result.getData().get(0).getValue("Name"));
        assertEquals("Bob Johnson", result.getData().get(1).getValue("Name"));
    }

    @Test
    public void testNumericStatistics() {
        ProcessingStats stats = service.calculateStatistics();
        
        assertNotNull(stats);
        
        ProcessingStats.NumericStats salaryStats = stats.getNumericColumnStats().get("Salary");
        assertNotNull(salaryStats);
        assertEquals(240000.0, salaryStats.sum());
        assertEquals(80000.0, salaryStats.average());
        assertEquals(60000.0, salaryStats.min());
        assertEquals(95000.0, salaryStats.max());
        assertEquals(3, salaryStats.count());
    }

    @Test
    public void testCategoricalStatistics() {
        ProcessingStats stats = service.calculateStatistics();
        
        assertNotNull(stats);

        ProcessingStats.CategoricalStats deptStats = stats.getCategoricalColumnStats().get("Department");
        assertNotNull(deptStats);
        assertEquals(2, deptStats.uniqueCount());
        assertEquals(2L, deptStats.valueDistribution().get("Engineering"));
        assertEquals(1L, deptStats.valueDistribution().get("Sales"));
    }
}
