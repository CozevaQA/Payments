package report;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import runner.Main;

public class CSVBackup {

	public Properties properties = new Properties();
	public String fullPath;
	public boolean headerWritten = false;
	private static final String TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());

	public CSVBackup(String customerName, String method) throws IOException {
		FileInputStream file = new FileInputStream(Main.configPath);
		properties.load(file);

		String folderPath = properties.getProperty("backupFolderPath")+ File.separator + customerName;

		if ("Health Net".equals(customerName)) {

			if ("Payment HTML".equals(method)) {
				folderPath += File.separator + "PaymentHTML";
			}

			else {
				folderPath += File.separator + "Registry";
			}

		}

		File destDir = new File(folderPath);
		if (!destDir.exists()) {
			destDir.mkdirs();
		}

		String fileName = TIMESTAMP + ".csv";
		fullPath = destDir + File.separator + fileName;
	}

	public void takeBackup(List<String> headers, List<List<String>> dataRows) {
		File csvFile = new File(fullPath);
		boolean fileAlreadyExists = csvFile.exists();

		try (FileWriter csvWriter = new FileWriter(fullPath, true)) {

			if (!fileAlreadyExists && !headerWritten) {
				for (int i = 0; i < headers.size(); i++) {
					String value = headers.get(i).replace("\"", "\"\"");
					csvWriter.append("\"").append(value).append("\"");
					if (i < headers.size() - 1)
						csvWriter.append(",");
				}
				csvWriter.append("\n");
				headerWritten = true;
			}

			for (List<String> row : dataRows) {
				for (int i = 0; i < row.size(); i++) {
					String value = row.get(i).replace("\"", "\"\"");
					csvWriter.append("\"").append(value).append("\"");
					if (i < row.size() - 1)
						csvWriter.append(",");
				}
				csvWriter.append("\n");
			}

			csvWriter.flush();
			System.out.println("CSV backup updated at: " + fullPath);
		} catch (IOException e) {
			System.err.println("Failed to write CSV: " + e.getMessage());
		}
	}
}
