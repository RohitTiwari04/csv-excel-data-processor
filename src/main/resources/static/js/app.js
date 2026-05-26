// Streamlytics - Frontend Application Controller

// Global App State
let datasetMetadata = null;
let currentColumns = [];
let activeFilters = [];
let sortBy = "";
let sortDirection = "asc";
let currentPage = 1;
const pageSize = 12;
let globalStats = null;
let currentChart = null;

// DOM Elements
const dropZone = document.getElementById("dropZone");
const fileInput = document.getElementById("fileInput");
const sectionUpload = document.getElementById("sectionUpload");
const sectionDashboard = document.getElementById("sectionDashboard");
const progressContainer = document.getElementById("progressContainer");
const progressBar = document.getElementById("progressBar");
const progressPercent = document.getElementById("progressPercent");
const progressStatus = document.getElementById("progressStatus");
const uploadError = document.getElementById("uploadError");
const uploadErrorText = document.getElementById("uploadErrorText");
const statusDot = document.getElementById("statusDot");
const statusText = document.getElementById("statusText");
const btnResetAll = document.getElementById("btnResetAll");

// Dashboard Elements
const metaFileName = document.getElementById("metaFileName");
const metaFileSize = document.getElementById("metaFileSize");
const metaRowCount = document.getElementById("metaRowCount");
const metaParseSpeed = document.getElementById("metaParseSpeed");
const schemaList = document.getElementById("schemaList");
const filterColumn = document.getElementById("filterColumn");
const filterOperator = document.getElementById("filterOperator");
const filterValue = document.getElementById("filterValue");
const btnAddFilterRule = document.getElementById("btnAddFilterRule");
const filterRulesList = document.getElementById("filterRulesList");
const emptyFiltersText = document.getElementById("emptyFiltersText");
const tableHeaders = document.getElementById("tableHeaders");
const tableRows = document.getElementById("tableRows");
const tableEmptyState = document.getElementById("tableEmptyState");
const tableSearchInput = document.getElementById("tableSearchInput");
const pagStart = document.getElementById("pagStart");
const pagEnd = document.getElementById("pagEnd");
const pagTotal = document.getElementById("pagTotal");
const currentPageIndicator = document.getElementById("currentPage");
const totalPagesIndicator = document.getElementById("totalPages");
const btnPrevPage = document.getElementById("btnPrevPage");
const btnNextPage = document.getElementById("btnNextPage");
const statsRow = document.getElementById("statsRow");
const chartColumnSelect = document.getElementById("chartColumnSelect");
const chartTypeSelect = document.getElementById("chartTypeSelect");
const javaStreamCode = document.getElementById("javaStreamCode");
const btnCopyCode = document.getElementById("btnCopyCode");
const toast = document.getElementById("toast");

// --- 1. Event Listeners Init ---
document.addEventListener("DOMContentLoaded", () => {
    // File Upload Setup
    dropZone.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", handleFileSelect);
    
    // Drag & Drop
    ["dragenter", "dragover"].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.add("active");
        }, false);
    });

    ["dragleave", "drop"].forEach(eventName => {
        dropZone.addEventListener(eventName, (e) => {
            e.preventDefault();
            dropZone.classList.remove("active");
        }, false);
    });

    dropZone.addEventListener("drop", (e) => {
        const dt = e.dataTransfer;
        const files = dt.files;
        if (files.length > 0) {
            uploadFile(files[0]);
        }
    });

    // Sidebar Filter Rules
    btnAddFilterRule.addEventListener("click", addFilterRule);
    
    // Reset App
    btnResetAll.addEventListener("click", resetApplication);
    
    // Table Sorting & Search
    tableSearchInput.addEventListener("input", debounce(handleQuickSearch, 400));
    
    // Pagination Buttons
    btnPrevPage.addEventListener("click", () => {
        if (currentPage > 1) {
            currentPage--;
            queryDataset();
        }
    });
    
    btnNextPage.addEventListener("click", () => {
        currentPage++;
        queryDataset();
    });

    // Chart Selectors
    chartColumnSelect.addEventListener("change", renderVisualChart);
    chartTypeSelect.addEventListener("change", renderVisualChart);

    // Copy Code Console Button
    btnCopyCode.addEventListener("click", copyCompiledCode);
});

// --- 2. Upload Logic ---
function handleFileSelect(e) {
    const files = e.target.files;
    if (files.length > 0) {
        uploadFile(files[0]);
    }
}

function uploadFile(file) {
    // Validate File Extension
    const name = file.name.toLowerCase();
    if (!name.endsWith(".csv") && !name.endsWith(".xlsx") && !name.endsWith(".xls")) {
        showError("Invalid file type. Please upload a CSV or Excel (.xlsx/.xls) spreadsheet.");
        return;
    }

    uploadError.classList.add("hidden");
    progressContainer.classList.remove("hidden");
    progressBar.style.width = "0%";
    progressPercent.innerText = "0%";
    progressStatus.innerText = "Uploading dataset...";

    const formData = new FormData();
    formData.append("file", file);

    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/data/upload", true);

    // Upload Progress
    xhr.upload.onprogress = (e) => {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            progressBar.style.width = percentComplete + "%";
            progressPercent.innerText = percentComplete + "%";
            if (percentComplete === 100) {
                progressStatus.innerText = "Processing streaming data on Java server...";
            }
        }
    };

    xhr.onload = () => {
        if (xhr.status === 200) {
            const response = JSON.parse(xhr.responseText);
            showToast("Dataset processed and cached successfully!", "success");
            setupDashboard(response);
        } else {
            let errMsg = "Upload or parsing failure.";
            try {
                const resp = JSON.parse(xhr.responseText);
                errMsg = resp.error || errMsg;
            } catch (err) {}
            showError(errMsg);
        }
    };

    xhr.onerror = () => {
        showError("Network communication error.");
    };

    xhr.send(formData);
}

function showError(msg) {
    progressContainer.classList.add("hidden");
    uploadErrorText.innerText = msg;
    uploadError.classList.remove("hidden");
    showToast(msg, "error");
}

// --- 3. Dashboard Initialization ---
function setupDashboard(metadata) {
    datasetMetadata = metadata;
    currentColumns = metadata.columns;
    activeFilters = [];
    sortBy = "";
    sortDirection = "asc";
    currentPage = 1;
    tableSearchInput.value = "";

    // Toggle View modes
    sectionUpload.classList.add("hidden");
    sectionDashboard.classList.remove("hidden");
    btnResetAll.classList.remove("hidden");

    // Header Badge update
    statusDot.className = "status-dot green";
    statusText.innerText = `Active: ${metadata.fileName}`;

    // Metadata Display
    metaFileName.innerText = metadata.fileName;
    metaFileName.title = metadata.fileName;
    metaFileSize.innerText = formatBytes(metadata.fileSize);
    metaRowCount.innerText = metadata.rowCount.toLocaleString();
    metaParseSpeed.innerText = metadata.parseTimeMs + " ms";

    // Populate Sidebar select & Schema list
    filterColumn.innerHTML = "";
    schemaList.innerHTML = "";
    chartColumnSelect.innerHTML = "";
    
    currentColumns.forEach(col => {
        // Filter Form option
        const opt = document.createElement("option");
        opt.value = col.name;
        opt.innerText = col.name;
        filterColumn.appendChild(opt);

        // Chart Option
        const chartOpt = document.createElement("option");
        chartOpt.value = col.name;
        chartOpt.innerText = col.name;
        chartColumnSelect.appendChild(chartOpt);

        // Schema List row
        const item = document.createElement("div");
        item.className = "schema-list-item";
        item.innerHTML = `
            <span class="schema-col-name text-truncate" title="${col.name}">${col.name}</span>
            <span class="badge badge-${col.type.toLowerCase()}">${col.type}</span>
        `;
        schemaList.appendChild(item);
    });

    // Populate table headers
    tableHeaders.innerHTML = "";
    currentColumns.forEach(col => {
        const th = document.createElement("th");
        th.innerHTML = `${col.name} <i class="fa-solid fa-sort"></i>`;
        th.addEventListener("click", () => handleTableSort(col.name, th));
        tableHeaders.appendChild(th);
    });

    // Load Data and Charts
    queryDataset();
    fetchStats();
}

// --- 4. Querying & Pagination ---
function queryDataset() {
    const payload = {
        filters: activeFilters,
        sortBy: sortBy,
        sortDirection: sortDirection,
        page: currentPage,
        pageSize: pageSize
    };

    fetch("/api/data/query", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    })
    .then(res => res.json())
    .then(result => {
        renderTableRows(result.data);
        updatePagination(result);
        compileJavaStreamsCode();
    })
    .catch(err => {
        showToast("Error executing query on server.", "error");
    });
}

function renderTableRows(data) {
    tableRows.innerHTML = "";
    
    if (data.length === 0) {
        tableEmptyState.classList.remove("hidden");
        return;
    }
    
    tableEmptyState.classList.add("hidden");

    data.forEach(row => {
        const tr = document.createElement("tr");
        currentColumns.forEach(col => {
            const td = document.createElement("td");
            const val = row.values[col.name];
            
            if (val === null || val === undefined) {
                td.innerHTML = `<span class="text-muted">null</span>`;
            } else if (col.type === "BOOLEAN") {
                td.innerHTML = val ? `<span class="badge badge-boolean">TRUE</span>` : `<span class="badge badge-string" style="background: rgba(239, 68, 68, 0.15); color: #fca5a5;">FALSE</span>`;
            } else if (col.type === "NUMERIC") {
                td.innerText = formatNumber(val);
            } else {
                td.innerText = val;
            }
            tr.appendChild(td);
        });
        tableRows.appendChild(tr);
    });
}

function updatePagination(result) {
    const startIdx = result.totalRecords === 0 ? 0 : (currentPage - 1) * pageSize + 1;
    const endIdx = Math.min(currentPage * pageSize, result.totalRecords);

    pagStart.innerText = startIdx;
    pagEnd.innerText = endIdx;
    pagTotal.innerText = result.totalRecords;
    
    currentPageIndicator.innerText = currentPage;
    totalPagesIndicator.innerText = Math.max(1, result.totalPages);

    btnPrevPage.disabled = currentPage <= 1;
    btnNextPage.disabled = currentPage >= result.totalPages || result.totalPages === 0;
}

function handleTableSort(colName, thElement) {
    // Clear active headers styling
    Array.from(tableHeaders.children).forEach(th => {
        th.className = "";
        th.querySelector("i").className = "fa-solid fa-sort";
    });

    if (sortBy === colName) {
        sortDirection = sortDirection === "asc" ? "desc" : "asc";
    } else {
        sortBy = colName;
        sortDirection = "asc";
    }

    thElement.className = "active-sort";
    thElement.querySelector("i").className = sortDirection === "asc" ? "fa-solid fa-sort-up" : "fa-solid fa-sort-down";
    
    currentPage = 1;
    queryDataset();
}

function handleQuickSearch() {
    const searchVal = tableSearchInput.value.trim();
    
    // Remove any search filters first
    activeFilters = activeFilters.filter(f => f.operator !== "SEARCH_DUMMY");
    
    if (searchVal !== "") {
        // Add dynamic quick search across all columns on server
        // To keep it simple, search inside the first string column in schema
        const firstStrCol = currentColumns.find(c => c.type === "STRING");
        if (firstStrCol) {
            activeFilters.push({
                column: firstStrCol.name,
                operator: "CONTAINS",
                value: searchVal
            });
        }
    }
    
    currentPage = 1;
    queryDataset();
    renderFilterBadges();
}

// --- 5. Filters Management ---
function addFilterRule() {
    const colName = filterColumn.value;
    const operator = filterOperator.value;
    const value = filterValue.value.trim();

    if (value === "") {
        showToast("Please enter a filter criteria value.", "error");
        return;
    }

    // Verify types
    const col = currentColumns.find(c => c.name === colName);
    if (col.type === "NUMERIC" && isNaN(Number(value))) {
        showToast(`Column '${colName}' is numeric. Please input a numerical filter value.`, "error");
        return;
    }

    // Add rule
    activeFilters.push({
        column: colName,
        operator: operator,
        value: value
    });

    filterValue.value = "";
    currentPage = 1;
    
    renderFilterBadges();
    queryDataset();
}

function removeFilterRule(index) {
    activeFilters.splice(index, 1);
    currentPage = 1;
    renderFilterBadges();
    queryDataset();
}

function renderFilterBadges() {
    // Filter out search elements
    const visibleFilters = activeFilters.filter(f => f.operator !== "SEARCH_DUMMY");
    
    if (visibleFilters.length === 0) {
        emptyFiltersText.classList.remove("hidden");
        filterRulesList.querySelectorAll(".filter-rule-badge").forEach(el => el.remove());
        return;
    }

    emptyFiltersText.classList.add("hidden");
    
    // Clear and redraw badges
    filterRulesList.querySelectorAll(".filter-rule-badge").forEach(el => el.remove());
    
    visibleFilters.forEach((rule, idx) => {
        const badge = document.createElement("div");
        badge.className = "filter-rule-badge";
        badge.innerHTML = `
            <span class="rule-info">
                <span class="col">${rule.column}</span> 
                <span class="op">${getOperatorSymbol(rule.operator)}</span> 
                <span class="val">"${rule.value}"</span>
            </span>
            <button class="btn-remove-rule" title="Delete rule"><i class="fa-solid fa-xmark"></i></button>
        `;
        badge.querySelector(".btn-remove-rule").addEventListener("click", () => removeFilterRule(idx));
        filterRulesList.insertBefore(badge, emptyFiltersText);
    });
}

function getOperatorSymbol(op) {
    switch (op) {
        case "EQUALS": return "==";
        case "CONTAINS": return "∋";
        case "GREATER_THAN": return ">";
        case "LESS_THAN": return "<";
        case "STARTS_WITH": return "startsWith";
        default: return op;
    }
}

// --- 6. Stats & Visual Charts ---
function fetchStats() {
    fetch("/api/data/stats")
        .then(res => res.json())
        .then(stats => {
            globalStats = stats;
            renderNumericalCards(stats.numericColumnStats);
            renderVisualChart();
        })
        .catch(err => {
            showToast("Failed to compile dashboard statistics.", "error");
        });
}

function renderNumericalCards(numericStats) {
    statsRow.innerHTML = "";
    
    const columns = Object.keys(numericStats);
    if (columns.length === 0) {
        // If no numerical columns, display basic textual distributions metrics
        statsRow.innerHTML = `
            <div class="stat-card">
                <div class="stat-info">
                    <span class="stat-title">Text Schema</span>
                    <span class="stat-value">${currentColumns.length} Columns</span>
                    <span class="stat-sub">Categorical parsed</span>
                </div>
                <div class="stat-icon-wrapper"><i class="fa-solid fa-list-check"></i></div>
            </div>
            <div class="stat-card">
                <div class="stat-info">
                    <span class="stat-title">Loaded Rows</span>
                    <span class="stat-value">${datasetMetadata.rowCount.toLocaleString()}</span>
                    <span class="stat-sub">Fully streamed to cache</span>
                </div>
                <div class="stat-icon-wrapper"><i class="fa-solid fa-database"></i></div>
            </div>
        `;
        return;
    }

    // Render top 4 numerical indicators
    columns.slice(0, 4).forEach(colName => {
        const stats = numericStats[colName];
        const card = document.createElement("div");
        card.className = "stat-card";
        card.innerHTML = `
            <div class="stat-info">
                <span class="stat-title text-truncate" title="${colName} (${colName.toUpperCase()} MEAN)">Avg. ${colName}</span>
                <span class="stat-value">${formatNumber(stats.average)}</span>
                <span class="stat-sub">Sum: ${formatNumber(stats.sum)}</span>
            </div>
            <div class="stat-icon-wrapper"><i class="fa-solid fa-calculator"></i></div>
        `;
        statsRow.appendChild(card);
    });
}

function renderVisualChart() {
    if (!globalStats) return;

    const column = chartColumnSelect.value;
    const type = chartTypeSelect.value;

    const numericStats = globalStats.numericColumnStats[column];
    const categoricalStats = globalStats.categoricalColumnStats[column];

    let labels = [];
    let datasetData = [];
    let title = "";

    if (numericStats) {
        // Numerical Column: show summary parameters
        labels = ["Sum", "Average", "Minimum", "Maximum"];
        datasetData = [numericStats.sum, numericStats.average, numericStats.min, numericStats.max];
        title = `Summary Statistics of '${column}'`;
    } else if (categoricalStats) {
        // Categorical Column: show frequency distributions of items
        const distribution = categoricalStats.valueDistribution;
        labels = Object.keys(distribution);
        datasetData = Object.values(distribution);
        title = `Frequency Distribution of '${column}' (Top 10)`;
    }

    // Rebuild Canvas
    if (currentChart) {
        currentChart.destroy();
    }

    const ctx = document.getElementById("analyticsChart").getContext("2d");
    
    // Premium theme colors
    const colors = [
        "rgba(99, 102, 241, 0.75)", // Indigo
        "rgba(6, 182, 212, 0.75)",  // Cyan
        "rgba(168, 85, 247, 0.75)", // Purple
        "rgba(16, 185, 129, 0.75)", // Green
        "rgba(245, 158, 11, 0.75)", // Orange
        "rgba(239, 68, 68, 0.75)",  // Red
        "rgba(59, 130, 246, 0.75)"  // Blue
    ];

    const borderColors = colors.map(c => c.replace("0.75", "1"));

    const chartConfig = {
        type: type,
        data: {
            labels: labels,
            datasets: [{
                label: column,
                data: datasetData,
                backgroundColor: type === "pie" ? colors : colors[0],
                borderColor: type === "pie" ? borderColors : borderColors[0],
                borderWidth: 1.5,
                borderRadius: type !== "pie" ? 6 : 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    display: type === "pie",
                    labels: {
                        color: "#9ca3af",
                        font: { family: "Inter" }
                    }
                },
                title: {
                    display: true,
                    text: title,
                    color: "#f3f4f6",
                    font: { family: "Outfit", size: 15, weight: "bold" },
                    padding: { bottom: 20 }
                }
            },
            scales: type === "pie" ? {} : {
                y: {
                    grid: { color: "rgba(255, 255, 255, 0.05)" },
                    ticks: { color: "#9ca3af", font: { family: "Inter" } }
                },
                x: {
                    grid: { display: false },
                    ticks: { color: "#9ca3af", font: { family: "Inter" } }
                }
            }
        }
    };

    currentChart = new Chart(ctx, chartConfig);
}

// --- 7. Live Java Stream Code Compiler ---
function compileJavaStreamsCode() {
    let code = `List<DataRecord> processed = records.stream()\n`;

    // Add filter rules
    if (activeFilters.length > 0) {
        activeFilters.forEach(rule => {
            if (rule.operator === "SEARCH_DUMMY") return;
            const col = currentColumns.find(c => c.name === rule.column);
            
            if (col.type === "NUMERIC") {
                const numVal = Number(rule.value);
                const valStr = numVal.toFixed(1);
                if (rule.operator === "EQUALS") {
                    code += `    .filter(r -> Math.abs((Double) r.getValue("${rule.column}") - ${valStr}) < 1e-9)\n`;
                } else if (rule.operator === "GREATER_THAN") {
                    code += `    .filter(r -> (Double) r.getValue("${rule.column}") > ${valStr})\n`;
                } else if (rule.operator === "LESS_THAN") {
                    code += `    .filter(r -> (Double) r.getValue("${rule.column}") < ${valStr})\n`;
                }
            } else if (col.type === "BOOLEAN") {
                const boolVal = rule.value.toLowerCase() === "true" || rule.value.toLowerCase() === "yes" || rule.value.toLowerCase() === "1";
                code += `    .filter(r -> (Boolean) r.getValue("${rule.column}") == ${boolVal})\n`;
            } else if (col.type === "DATE") {
                if (rule.operator === "EQUALS") {
                    code += `    .filter(r -> ((LocalDate) r.getValue("${rule.column}")).equals(LocalDate.parse("${rule.value}")))\n`;
                } else if (rule.operator === "GREATER_THAN") {
                    code += `    .filter(r -> ((LocalDate) r.getValue("${rule.column}")).isAfter(LocalDate.parse("${rule.value}")))\n`;
                } else if (rule.operator === "LESS_THAN") {
                    code += `    .filter(r -> ((LocalDate) r.getValue("${rule.column}")).isBefore(LocalDate.parse("${rule.value}")))\n`;
                }
            } else {
                // STRING/text filters
                if (rule.operator === "EQUALS") {
                    code += `    .filter(r -> r.getValue("${rule.column}").toString().equalsIgnoreCase("${rule.value}"))\n`;
                } else if (rule.operator === "CONTAINS") {
                    code += `    .filter(r -> r.getValue("${rule.column}").toString().toLowerCase().contains("${rule.value.toLowerCase()}"))\n`;
                } else if (rule.operator === "STARTS_WITH") {
                    code += `    .filter(r -> r.getValue("${rule.column}").toString().toLowerCase().startsWith("${rule.value.toLowerCase()}"))\n`;
                }
            }
        });
    }

    // Add Sorting
    if (sortBy !== "") {
        const col = currentColumns.find(c => c.name === sortBy);
        let castType = "String";
        if (col.type === "NUMERIC") castType = "Double";
        else if (col.type === "BOOLEAN") castType = "Boolean";
        else if (col.type === "DATE") castType = "LocalDate";

        if (sortDirection === "asc") {
            code += `    .sorted(Comparator.comparing(r -> (${castType}) r.getValue("${sortBy}")))\n`;
        } else {
            code += `    .sorted(Comparator.comparing((DataRecord r) -> (${castType}) r.getValue("${sortBy}")).reversed())\n`;
        }
    }

    // Add Pagination
    const skipCount = (currentPage - 1) * pageSize;
    if (skipCount > 0) {
        code += `    .skip(${skipCount})\n`;
    }
    code += `    .limit(${pageSize})\n`;
    code += `    .collect(Collectors.toList());`;

    javaStreamCode.innerHTML = highlightJavaCode(code);
}

function highlightJavaCode(code) {
    // Very lightweight lexical syntax highlighter
    const keywords = ["public", "class", "void", "return", "new", "import", "package", "Double", "String", "Boolean", "LocalDate", "List", "Collectors", "Comparator"];
    const methods = ["stream", "filter", "sorted", "skip", "limit", "collect", "comparing", "reversed", "toList", "parse", "equals", "isAfter", "isBefore", "equalsIgnoreCase", "contains", "startsWith", "toLowerCase", "toString", "getValue"];

    let escaped = code
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");

    // Strings
    escaped = escaped.replace(/"([^"\\]|\\.)*"/g, '<span class="string-literal">$&</span>');

    // Numbers
    escaped = escaped.replace(/\b\d+(\.\d+)?\b/g, '<span class="numeric-literal">$&</span>');

    // Comments
    escaped = escaped.replace(/\/\/.*$/gm, '<span class="comment">$&</span>');

    // Highlight keywords
    keywords.forEach(word => {
        const regex = new RegExp(`\\b${word}\\b`, "g");
        escaped = escaped.replace(regex, `<span class="keyword">${word}</span>`);
    });

    // Highlight methods
    methods.forEach(method => {
        const regex = new RegExp(`\\.${method}\\b`, "g");
        escaped = escaped.replace(regex, `.<span class="method">${method}</span>`);
        
        // Also highlight matches without dots
        const regexAlone = new RegExp(`\\b${method}(?=\\()`, "g");
        escaped = escaped.replace(regexAlone, `<span class="method">${method}</span>`);
    });

    return escaped;
}

function copyCompiledCode() {
    const rawText = javaStreamCode.innerText;
    navigator.clipboard.writeText(rawText)
        .then(() => showToast("Java Stream code copied to clipboard!", "success"))
        .catch(() => showToast("Failed to copy code.", "error"));
}

// --- 8. Helper Functions ---
function resetApplication() {
    fetch("/api/data/reset", { method: "POST" })
        .then(() => {
            datasetMetadata = null;
            currentColumns = [];
            activeFilters = [];
            sortBy = "";
            sortDirection = "asc";
            currentPage = 1;
            globalStats = null;
            if (currentChart) {
                currentChart.destroy();
                currentChart = null;
            }

            sectionDashboard.classList.add("hidden");
            btnResetAll.classList.add("hidden");
            sectionUpload.classList.remove("hidden");
            progressContainer.classList.add("hidden");

            statusDot.className = "status-dot red";
            statusText.innerText = "No Dataset Active";

            showToast("System cache cleared.", "success");
        })
        .catch(err => {
            showToast("Reset request failed.", "error");
        });
}

function formatBytes(bytes, decimals = 2) {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + " " + sizes[i];
}

function formatNumber(num) {
    if (num === null || num === undefined) return "-";
    if (num === Math.floor(num)) return num.toLocaleString();
    return num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function debounce(func, delay) {
    let timeoutId;
    return function (...args) {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => {
            func.apply(this, args);
        }, delay);
    };
}

function showToast(message, type = "success") {
    toast.className = `toast show ${type}`;
    toast.innerHTML = type === "success" 
        ? `<i class="fa-solid fa-circle-check" style="color: var(--success)"></i> ${message}`
        : `<i class="fa-solid fa-circle-xmark" style="color: var(--error)"></i> ${message}`;

    setTimeout(() => {
        toast.classList.remove("show");
    }, 3500);
}
