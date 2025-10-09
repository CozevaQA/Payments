package molina;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.PaymentHelper;
import report.CSVBackup;
import report.ReportGeneratorContextwise;

public class MolinaPayment extends PaymentHelper {

	ReportGeneratorContextwise report;
	String customer;
	String method;
	CSVBackup csv;

	public MolinaPayment(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
		report = ReportGeneratorContextwise.getInstance();
		report.setExtraColumns(List.of("Lob", "Program"));
	}

	Map<String, List<String[]>> programDetailsfromCSV = loadDataFromCsv(
			"assets/testdata/MolinaDataset/MolinaProgramDetails.csv");

	Map<String, List<Map<String, Object>>> programDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> metricDataMap = new LinkedHashMap<>();
	Set<String> programsfromExtract = new LinkedHashSet<>();
	
	List<String[]> deferredPaymentPrintLogs = new ArrayList<>();

	public void validateMolina(String GroupName) {

		List<String[]> programDetails = programDetailsfromCSV.get(GroupName.trim());
		for (String[] row : programDetails) {
			programsfromExtract.add(row[0].trim());
		}

		openGreenRibIfClosed();
		int lobCount = driver.findElements(By.xpath(properties.getProperty("lobElements"))).size();

		for (int i = 0; i < lobCount; i++) {
			openGreenRibIfClosed();

			List<WebElement> lobList = driver.findElements(By.xpath(properties.getProperty("lobElements")));
			WebElement lob = lobList.get(i);
			lob = wait.until(ExpectedConditions.visibilityOf(lob));
			lob.click();
			driver.findElement(By.xpath(properties.getProperty("apply"))).click();
			String lobName = lob.getText().trim();
			// System.out.println(lobName);

			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			wait.until(ExpectedConditions
					.invisibilityOfElementLocated(By.xpath(properties.getProperty("ajax_preloader"))));

			double earnedPts = 0;
			double potentialPts = 0;
			String programName = null;
			List<Map<String, Object>> programDataList = new ArrayList<>();

			boolean isIncentiveProgramDropdownpresent;
			try {
				wait.until(ExpectedConditions
						.visibilityOfElementLocated(By.xpath(properties.getProperty("incentiveProgramContainer"))));
				isIncentiveProgramDropdownpresent = true;
			} catch (Exception e) {
				isIncentiveProgramDropdownpresent = false;
			}

			if (isIncentiveProgramDropdownpresent) {
				driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
				int programCount = driver.findElements(By.xpath(properties.getProperty("incentiveprograms"))).size();

				driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
				for (int j = 0; j < programCount; j++) {
					driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
					List<WebElement> programList = driver
							.findElements(By.xpath(properties.getProperty("incentiveprograms")));
					WebElement program = programList.get(j);
					program = wait.until(ExpectedConditions.visibilityOf(program));
					program.click();
					programName = program.getText();
					System.out.println(programName);

					WebElement earnPtsElement = wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts"))));
					earnedPts = Double.parseDouble(earnPtsElement.getText());
					System.out.println(earnedPts);

					WebElement potentialPtsElement = wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts"))));
					potentialPts = Double.parseDouble(potentialPtsElement.getText().replace("/", ""));
					System.out.println(potentialPts);

					Map<String, Object> programData = new HashMap<>();
					programData.put("Program", programName);
					programData.put("EarnedPts", earnedPts);
					programData.put("PotentialPts", potentialPts);
					programDataList.add(programData);
					programDataMap.put(lobName, programDataList);
					metricDataMap = getMetricIncentiveDetails(customer);
					compareMolinaPayment(GroupName, lobName, programName);
					metricDataMap.clear();

				}

			} else {

				// String hiddenProgram = null;
				try {

					Thread.sleep(3000);

					List<WebElement> hiddenElements = driver
							.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram")));

					programName = hiddenElements.size() == 1 ? hiddenElements.get(0).getAttribute("data-value")
							: lobName;
					System.out.println(programName);
					WebElement earnPtsElement = wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts"))));
					earnedPts = Double.parseDouble(earnPtsElement.getText());

					WebElement potentialPtsElement = wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts"))));
					potentialPts = Double.parseDouble(potentialPtsElement.getText().replace("/", ""));

				} catch (Exception e) {
					earnedPts = -1.0;
					potentialPts = -1.0;
				}

				Map<String, Object> programData = new HashMap<>();
				programData.put("Program", programName);
				programData.put("EarnedPts", earnedPts);
				programData.put("PotentialPts", potentialPts);
				programDataList.add(programData);
				programDataMap.put(lobName, programDataList);
				metricDataMap = getMetricIncentiveDetails(customer);
				compareMolinaPayment(GroupName, lobName, programName);
				metricDataMap.clear();
			}

			//compareMolinaPayment(GroupName, lobName, programName);
			//metricDataMap.clear();
			
			/*String potentialpoints = earnedPts + "/" + potentialPts;
			if (potentialpoints != "0.00/0.00") {
				if(potentialpoints == "-1.0/-1.0") {
					report.logTestResult(GroupName, "Incentive Points !=0", "Pass", "NA", lobName, programName);
				}
				else {
					report.logTestResult(GroupName, "Incentive Points !=0", "Pass", potentialpoints, lobName, programName);
				}
				
			} else {
				report.logTestResult(GroupName, "Incentive Points !=0", "Fail", potentialpoints, lobName, programName);
			}*/
			

		}

		comparePrograms(GroupName);
		
		for (String[] log : deferredPaymentPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		/*
		 * for (Map.Entry<String, List<Map<String, Object>>> entry :
		 * programDataMap.entrySet()) { String lobName = entry.getKey();
		 * List<Map<String, Object>> programs = entry.getValue();
		 * 
		 * System.out.println("LOB: " + lobName); for (Map<String, Object> program :
		 * programs) { System.out.println("  Program: " + program.get("Program"));
		 * System.out.println("    EarnedPts: " + program.get("EarnedPts"));
		 * System.out.println("    PotentialPts: " + program.get("PotentialPts")); } }
		 */
	}

	public void comparePrograms(String GroupName) {
		for (Map.Entry<String, List<Map<String, Object>>> entry : programDataMap.entrySet()) {
			String key = entry.getKey();
			List<Map<String, Object>> programList = entry.getValue();

			Set<String> actualProgramSet = new LinkedHashSet<>();

			Set<String> referenceSet = new LinkedHashSet<>();
			
			for (Map<String, Object> program : programList) {
				String programName = program.get("Program").toString().trim();
				actualProgramSet.add(programName);
				
				String potentialpoints = program.get("EarnedPts") + "/" + program.get("PotentialPts");
				if (potentialpoints != "0.00/0.00") {
					if(potentialpoints .equals("-1.0/-1.0")) {
						report.logTestResult(GroupName, "Incentive Points !=0", "Pass", "NA", key, programName);
					}
					else {
						report.logTestResult(GroupName, "Incentive Points !=0", "Pass", potentialpoints, key, programName);
					}
					
				} else {
					report.logTestResult(GroupName, "Incentive Points !=0", "Fail", potentialpoints, key, programName);
				}
				
			}
			
			for (String p : programsfromExtract) {
				if ("ALL".equalsIgnoreCase(key)) {
					referenceSet.add(p);
				} else if ("Medi-Cal".equalsIgnoreCase(key) && p.contains("MCD")) {
					referenceSet.add(p);
				} else if ("Marketplace".equalsIgnoreCase(key) && p.contains("MRPL")) {
					referenceSet.add(p);
				}
			}

			boolean countMatches = referenceSet.size() == actualProgramSet.size();
			boolean namesMatch = referenceSet.equals(actualProgramSet);
			String programMatch = (countMatches && namesMatch) ? "Pass" : "Fail";

			report.logTestResult(GroupName, "Program match", programMatch,
					"Expected Set: " + programsfromExtract + "Actual Set:" + actualProgramSet, key, "");

		}

	}

	public void compareMolinaPayment(String GroupName, String Lob, String Program) {
		double totalMetricActualPay = 0, totalMetricPotentialPay = 0;
		for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {

			Map<String, Object> data = entry.getValue();
			totalMetricActualPay += (double) data.get("MetricActualPay");
			totalMetricPotentialPay += (double) data.get("MetricPotentialPay");

		}

		if (!Lob.equals("Medicare")) {

			for (Map<String, Object> data : programDataMap.get(Lob)) {
				if (data.get("Program").equals(Program)) {
				/*	String actualMatch = ((double) data.get("EarnedPts") == totalMetricActualPay) ? "Pass" : "Fail";
					String potentialMatch = ((double) data.get("PotentialPts") == totalMetricPotentialPay) ? "Pass"	: "Fail";

					report.logTestResult(GroupName, "Actual pts match", actualMatch,
							"Registry: " + data.get("EarnedPts") + " , Sum: " + totalMetricActualPay, Lob, Program);

					report.logTestResult(GroupName, "Potential pts match", potentialMatch,
							"Registry: " + data.get("PotentialPts") + " , Sum: " + totalMetricPotentialPay, Lob,
							Program);*/
					
					
					boolean actualMatch = ((double) data.get("EarnedPts") == totalMetricActualPay);
			        boolean potentialMatch = ((double) data.get("PotentialPts") == totalMetricPotentialPay);

			        String ptsMatch = (actualMatch && potentialMatch) ? "Pass" : "Fail";

					
					deferredPaymentPrintLogs.add(new String[] { GroupName, "Actual & Potential pts match", ptsMatch,
			                "Registry Actual: " + data.get("EarnedPts") + " , Sum Actual: " + totalMetricActualPay
			                + " | Registry Potential: " + data.get("PotentialPts") + " , Sum Potential: " + totalMetricPotentialPay,
			                Lob, Program});
					
				}
			}
		}

	}

}
