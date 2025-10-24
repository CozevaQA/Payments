package report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import runner.Main;

public class ReportGeneratorContextwise {
    private static ReportGeneratorContextwise instance;

    private final XSSFWorkbook workbook = new XSSFWorkbook();
    private final Map<String, Sheet> providerSheets = new LinkedHashMap<>();
    private final Map<String, Integer> rowTracker = new HashMap<>();
    private final Set<String> summaryHeadingsAdded = new HashSet<>();
    private Sheet summarySheet;
    private int summaryRow = 1;

    private CellStyle passStyle;
    private CellStyle failStyle;
    private CellStyle headerStyle;
    private CellStyle sectionHeaderStyle;

    public Properties properties = new Properties();
    private List<String> extraColumns = new ArrayList<>();

    private ReportGeneratorContextwise() throws IOException {
        initializeStyles();
        FileInputStream file = new FileInputStream(Main.configPath);
        properties.load(file);
    }

    public static ReportGeneratorContextwise getInstance() throws IOException {
        if (instance == null) {
            instance = new ReportGeneratorContextwise();
        }
        return instance;
    }

    private void initializeStyles() {
        passStyle = workbook.createCellStyle();
        passStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        passStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font passFont = workbook.createFont();
        passFont.setColor(IndexedColors.BLACK.getIndex());
        passStyle.setFont(passFont);

        failStyle = workbook.createCellStyle();
        failStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        failStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font failFont = workbook.createFont();
        failFont.setColor(IndexedColors.BLACK.getIndex());
        failStyle.setFont(failFont);

        headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);

        sectionHeaderStyle = workbook.createCellStyle();
        Font sectionFont = workbook.createFont();
        sectionFont.setBold(true);
        sectionFont.setColor(IndexedColors.RED.getIndex());
        sectionHeaderStyle.setFont(sectionFont);
    }

    public void setExtraColumns(List<String> columnNames) {
        if (columnNames != null && !columnNames.isEmpty()) {
            this.extraColumns = new ArrayList<>(columnNames);
        } else {
            this.extraColumns.clear();
        }
    }
  /*  private List<String> buildExtraValueList(String... extraValues) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < extraColumns.size(); i++) {
            if (extraValues != null && i < extraValues.length) {
                values.add(extraValues[i] != null ? extraValues[i] : "");
            } else {
                values.add("");
            }
        }
        return values;
    }*/
    private void createSummarySheet() {
        summarySheet = workbook.createSheet("Summary");
        Row header = summarySheet.createRow(0);

        int col = 0;

        Cell cell1 = header.createCell(col++);
        cell1.setCellValue("Context");
        cell1.setCellStyle(headerStyle);
        
        for (String extra : extraColumns) {
            Cell cell = header.createCell(col++);
            cell.setCellValue(extra);
            cell.setCellStyle(headerStyle);
        }

        Cell cell2 = header.createCell(col++);
        cell2.setCellValue("Test Name");
        cell2.setCellStyle(headerStyle);

        Cell cell3 = header.createCell(col++);
        cell3.setCellValue("Status");
        cell3.setCellStyle(headerStyle);

        Cell cell4 = header.createCell(col);
        cell4.setCellValue("Details");
        cell4.setCellStyle(headerStyle);
    }

    public void createProviderSheet(String providerName) {
        if (!providerSheets.containsKey(providerName)) {
            Sheet sheet = workbook.createSheet(providerName);
            Row header = sheet.createRow(0);

            int col = 0;

            for (String extra : extraColumns) {
                Cell cell = header.createCell(col++);
                cell.setCellValue(extra);
                cell.setCellStyle(headerStyle);
            }

            Cell cell0 = header.createCell(col++);
            cell0.setCellValue("Test Name");
            cell0.setCellStyle(headerStyle);

            Cell cell1 = header.createCell(col++);
            cell1.setCellValue("Status");
            cell1.setCellStyle(headerStyle);

            Cell cell2 = header.createCell(col);
            cell2.setCellValue("Details");
            cell2.setCellStyle(headerStyle);

            providerSheets.put(providerName, sheet);
            rowTracker.put(providerName, 1);
        }
    }

    public void logTestResult(String providerName, String testName, String status, String details, String... extraValues) {
    	//List<String> extraData = buildExtraValueList(extraValues);
    	List<String> extraData = Arrays.asList(extraValues);
    	if (summarySheet == null) {
    	    createSummarySheet();
    	}
        if (!providerSheets.containsKey(providerName)) {
            createProviderSheet(providerName);
        }

        Sheet sheet = providerSheets.get(providerName);
        int rowNum = rowTracker.get(providerName);
        Row row = sheet.createRow(rowNum);

        int col = 0;
        
        for (String value : extraData) {
            row.createCell(col++).setCellValue(value);
        }
        
        
        row.createCell(col++).setCellValue(testName != null ? testName : "");

        Cell statusCell = row.createCell(col++);
        statusCell.setCellValue(status != null ? status : "");
        if ("PASS".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(passStyle);
        } else if ("FAIL".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(failStyle);
        }

        row.createCell(col).setCellValue(details != null ? details : "");

        rowTracker.put(providerName, rowNum + 1);

        
        if ("FAIL".equalsIgnoreCase(status)) {
            addToSummary(providerName, testName, status, details,extraData);
        }
    }


    public void logCSVComparisonResult(String context, String testName, String status, String detail, String... extraValues) {
    	//List<String> extraData = buildExtraValueList(extraValues);
    	List<String> extraData = Arrays.asList(extraValues);

    	String sheetName = "CSV Comparison";

		 
        if (!providerSheets.containsKey(sheetName)) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            int col = 0;

            Cell cell0 = header.createCell(col++);
            cell0.setCellValue("Context");
            cell0.setCellStyle(headerStyle);
            
            for (String extra : extraColumns) {
                Cell cell = header.createCell(col++);
                cell.setCellValue(extra);
                cell.setCellStyle(headerStyle);
            }

            Cell cell1 = header.createCell(col++);
            cell1.setCellValue("Test Name");
            cell1.setCellStyle(headerStyle);

            Cell cell2 = header.createCell(col++);
            cell2.setCellValue("Status");
            cell2.setCellStyle(headerStyle);

            Cell cell3 = header.createCell(col);
            cell3.setCellValue("Details");
            cell3.setCellStyle(headerStyle);

            providerSheets.put(sheetName, sheet);
            rowTracker.put(sheetName, 1);
        }

        Sheet sheet = providerSheets.get(sheetName);
        int rowNum = rowTracker.get(sheetName);
        Row row = sheet.createRow(rowNum);

        int col = 0;

        
        for (String value : extraData) {
            row.createCell(col++).setCellValue(value);
        }

        row.createCell(col++).setCellValue(testName);

        Cell statusCell = row.createCell(col++);
        statusCell.setCellValue(status);
        if ("PASS".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(passStyle);
        } else if ("FAIL".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(failStyle);
        }

        row.createCell(col).setCellValue(detail);

        rowTracker.put(sheetName, rowNum + 1);

        if ("FAIL".equalsIgnoreCase(status)) {
            if (!summaryHeadingsAdded.contains(sheetName)) {
                summaryRow++;
                summaryRow++;

                Row headingRow = summarySheet.createRow(summaryRow++);
                Cell headingCell = headingRow.createCell(0);
                headingCell.setCellValue(sheetName);
                headingCell.setCellStyle(sectionHeaderStyle);

                summaryHeadingsAdded.add(sheetName);
            }
            addToSummary(context, testName, status, detail, extraData.subList(1, extraData.size()));
        }
    }


    private void addToSummary(String providerName, String testName, String status, String details,List<String> extraData) {
        Row row = summarySheet.createRow(summaryRow++);
        int col = 0;

        row.createCell(col++).setCellValue(providerName);
        
        for (String value : extraData) {
            row.createCell(col++).setCellValue(value);
        }

        row.createCell(col++).setCellValue(testName != null ? testName : "");

        Cell statusCell = row.createCell(col++);
        statusCell.setCellValue(status != null ? status : "");
        if ("PASS".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(passStyle);
        } else if ("FAIL".equalsIgnoreCase(status)) {
            statusCell.setCellStyle(failStyle);
        }

        row.createCell(col).setCellValue(details != null ? details : "");
    }

    public void writeTable(String providerName, List<String> headers, List<List<String>> rowData) {
        Sheet sheet = providerSheets.get(providerName);
        if (sheet == null) return;

        int currentRow = rowTracker.getOrDefault(providerName, 1);
        currentRow += 2;

        Row headerRow = sheet.createRow(currentRow++);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        for (List<String> row : rowData) {
            Row sheetRow = sheet.createRow(currentRow++);
            for (int i = 0; i < row.size(); i++) {
                sheetRow.createCell(i).setCellValue(row.get(i));
            }
        }

        rowTracker.put(providerName, currentRow);
    }

    public void saveReport(String customerName,String method) {
        String basePath = properties.getProperty("baseFolderPath");
        String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
        String fileName = customerName + "_" + timestamp + ".xlsx";

        String folderPath = basePath + File.separator + todayDate + File.separator + customerName;

        if ("Health Net".equals(customerName)) {
            if (method.equals("Payment HTML")) {
                folderPath += File.separator + "PaymentHTML";
            } else {
                folderPath += File.separator + "Registry";
            }
        }

        File destDir = new File(folderPath);
        if (!destDir.exists()) destDir.mkdirs();

        String fullPath = destDir.getAbsolutePath() + File.separator + fileName;

        for (Sheet sheet : workbook) {
            int colCount = 0;
            for (Row row : sheet) {
                if (row.getLastCellNum() > colCount) {
                    colCount = row.getLastCellNum();
                }
            }
            for (int i = 0; i < colCount; i++) {
                sheet.autoSizeColumn(i);
            }
        }

        try (FileOutputStream out = new FileOutputStream(fullPath)) {
            workbook.write(out);
            workbook.close();
            System.out.println("✅ Report saved: " + fullPath);
        } catch (IOException e) {
            System.err.println("❌ Failed to save Excel report: " + e.getMessage());
        }
    }

 
}
