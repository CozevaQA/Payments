package report;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

public class CSVComparator {

    ReportGeneratorContextwise report;

    public CSVComparator() throws IOException {
        report = ReportGeneratorContextwise.getInstance();
    }

    public void compareLastTwoCSVs(String customerFolderPath, List<String> keyColumns) {
        try {
            // 1. Get last two CSV files
            List<Path> csvFiles = Files.list(Paths.get(customerFolderPath))
                    .filter(p -> p.toString().endsWith(".csv"))
                    .sorted(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .collect(Collectors.toList());

            if (csvFiles.size() < 2) {
                System.err.println("Not enough CSV files to compare.");
                return;
            }
            
           // Path prevFile = csvFiles.get(csvFiles.size() - 2);
           // Path currentFile = csvFiles.get(csvFiles.size() - 1);

            LocalDate today = LocalDate.now();
            Path currentFile = null;
            Path prevFile = null;

            for (int i = csvFiles.size() - 1; i >= 0; i--) {
                Path file = csvFiles.get(i);
                LocalDate fileDate = Files.getLastModifiedTime(file)
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (currentFile == null && fileDate.isEqual(today)) {
                    currentFile = file;
                } else if (currentFile != null && fileDate.isBefore(today)) {
                    prevFile = file;
                    break;
                }
            }

            if (currentFile == null || prevFile == null) {
                System.err.println("Could not find both current and previous CSV files.");
                return;
            }

            System.out.println("Current file: " + currentFile.getFileName());
            System.out.println("Previous file: " + prevFile.getFileName());

            Map<String, List<String>> prevData = new LinkedHashMap<>();
            Map<String, List<String>> currentData = new LinkedHashMap<>();
            List<String> headers;

            Map<String, Integer> headerIndexMap = new HashMap<>();

            // Read previous file
            try (BufferedReader br = new BufferedReader(new FileReader(prevFile.toFile()))) {
                headers = Arrays.stream(br.readLine().split(","))
                        .map(h -> h.replace("\"", "").trim())
                        .collect(Collectors.toList());

                for (int i = 0; i < headers.size(); i++) {
                    headerIndexMap.put(headers.get(i), i);
                }

                String line;
                while ((line = br.readLine()) != null) {
                    List<String> row = parseCSVRow(line);
                    if (!row.isEmpty()) {
                        String key = getCompositeKey(row, keyColumns, headerIndexMap);
                        prevData.put(key, row);
                    }
                }
            }

            // Read current file
            try (BufferedReader br = new BufferedReader(new FileReader(currentFile.toFile()))) {
                br.readLine(); // skip header
                String line;
                while ((line = br.readLine()) != null) {
                    List<String> row = parseCSVRow(line);
                    if (!row.isEmpty()) {
                        String key = getCompositeKey(row, keyColumns, headerIndexMap);
                        currentData.put(key, row);
                    }
                }
            }

            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(prevData.keySet());
            allKeys.addAll(currentData.keySet());

            int startIndex = keyColumns.size();

            for (String key : allKeys) {
                List<String> prevRow = prevData.get(key);
                List<String> currRow = currentData.get(key);

                String context = extractContext(key); 
                List<String> extraValues = extractExtraValues(currRow != null ? currRow : prevRow, keyColumns, headerIndexMap);

                for (int i = startIndex; i < headers.size(); i++) {
                    String columnName = headers.get(i).trim();
                    String prevVal = (prevRow != null && i < prevRow.size()) ? prevRow.get(i) : "NA";
                    String currVal = (currRow != null && i < currRow.size()) ? currRow.get(i) : "NA";

                    String status = valuesMatch(prevVal, currVal) ? "Pass" : "Fail";
                    String detail = "Prev: " + prevVal + ", Current: " + currVal;

                    report.logCSVComparisonResult(context, columnName + " Match", status, detail,
                            extraValues.toArray(new String[0]));
                }

                report.logCSVComparisonResult("", "", "", "", new String[keyColumns.size()]);
            }

        } catch (IOException e) {
            System.err.println("Error comparing CSVs: " + e.getMessage());
        }
    }

    private List<String> parseCSVRow(String line) {
        try (CSVReader reader = new CSVReader(new StringReader(line))) {
            String[] parsed = reader.readNext();
            if (parsed != null) {
                return Arrays.asList(parsed);
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private String getCompositeKey(List<String> row, List<String> keyColumns, Map<String, Integer> headerIndexMap) {
        List<String> keyParts = new ArrayList<>();
        for (String col : keyColumns) {
            Integer idx = headerIndexMap.get(col);
            if (idx != null && idx < row.size()) {
                keyParts.add(row.get(idx).trim());
            } else {
                keyParts.add("");
            }
        }
        return String.join("|", keyParts);
    }

    private List<String> extractExtraValues(List<String> row, List<String> keyColumns, Map<String, Integer> headerIndexMap) {
        List<String> values = new ArrayList<>();
        for (String col : keyColumns) {
            Integer idx = headerIndexMap.get(col);
            String val = (idx != null && idx < row.size()) ? row.get(idx).trim() : "";
            values.add(val);
        }
       // System.out.println("Key Columns: " + keyColumns);
       // System.out.println("Extracted Extra Values: " + values);
        //System.out.println("Row: " + row);
        return values;
    }

    private String extractContext(String compositeKey) {
        return compositeKey.split("\\|")[0]; // context is the first part
    }

    private boolean valuesMatch(String v1, String v2) {
        try {
            double d1 = Double.parseDouble(v1);
            double d2 = Double.parseDouble(v2);
            return Double.compare(d1, d2) == 0;
        } catch (NumberFormatException e) {
            return v1.equals(v2);
        }
    }
}
