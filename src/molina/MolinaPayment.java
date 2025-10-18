package molina;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
			System.out.println(lobName);

			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			wait.until(ExpectedConditions
					.invisibilityOfElementLocated(By.xpath(properties.getProperty("ajax_preloader"))));

			double earnedPts = 0;
			double potentialPts = 0;
			String programName = null;
			List<Map<String, Object>> programDataList = new ArrayList<>();

			if (!lobName.equals("Medicare")) {

				boolean isIncentiveProgramDropdownpresent;
				int programCount = 0;

				try {
					wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("incentiveProgramContainer"))));

					isIncentiveProgramDropdownpresent = true;

					driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
					programCount = driver.findElements(By.xpath(properties.getProperty("incentiveprograms"))).size();

					driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();

				} catch (Exception e) {
					isIncentiveProgramDropdownpresent = false;

					programCount = driver.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram")))
							.size();
				}

				programCount = programCount != 0 ? programCount : 1;

				for (int j = 0; j < programCount; j++) {
					if (isIncentiveProgramDropdownpresent) {
						driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
						List<WebElement> programList = driver
								.findElements(By.xpath(properties.getProperty("incentiveprograms")));
						WebElement program = programList.get(j);
						program = wait.until(ExpectedConditions.visibilityOf(program));
						program.click();
						programName = program.getText();
					} else {
						List<WebElement> hiddenProgramList = driver
								.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram")));

						programName = hiddenProgramList.size() >= 1
								? hiddenProgramList.get(j).getAttribute("data-value")
								: "";

						/*
						 * if(hiddenProgramList.size() >= 1) { programName =
						 * hiddenProgramList.get(j).getAttribute("data-value"); }
						 */

					}

					List<Object> incentiveDollarAmaount = Arrays.asList(-1.0, -1.0, "N/A");
					try {
						List<WebElement> incentiveCard_hide = driver
								.findElements(By.xpath(properties.getProperty("incentiveCard_hide")));

						if (incentiveCard_hide.size() == 1) {
							driver.findElement(By.xpath(properties.getProperty("incentiveCard"))).click();
						}

						WebElement earnPtsElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts"))));
						earnedPts = Double.parseDouble(earnPtsElement.getText());

						WebElement potentialPtsElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts"))));
						potentialPts = Double.parseDouble(potentialPtsElement.getText().replace("/", ""));

						driver.findElement(By.xpath(properties.getProperty("incentiveCard"))).click();
						incentiveDollarAmaount = extractPotentialPayout();
					} catch (Exception e) {
						earnedPts = -1.0;
						potentialPts = -1.0;
					}

					System.out.println(programName);
					System.out.println(earnedPts);
					System.out.println(potentialPts);
					System.out.println(incentiveDollarAmaount.get(0));
					System.out.println(incentiveDollarAmaount.get(1));
					System.out.println(incentiveDollarAmaount.get(2));

					Map<String, Object> programData = new HashMap<>();
					programData.put("Program", programName);
					programData.put("EarnedPts", earnedPts);
					programData.put("PotentialPts", potentialPts);
					programData.put("EarnAmaount", incentiveDollarAmaount.get(0));
					programData.put("PotentialAmaount", incentiveDollarAmaount.get(1));
					programData.put("PotentialPayout", incentiveDollarAmaount.get(2));
					programDataList.add(programData);
					programDataMap.put(lobName, programDataList);

					if (!incentiveDollarAmaount.get(2).equals("N/A")) {
						metricDataMap = getMetricIncentiveDetails(customer);
						compareMolinaPayment(GroupName, lobName, programName);
						metricDataMap.clear();
					}
				}
			}

			else {
                // Add 0/0 in medicare
				Map<String, Object> programData = new HashMap<>();
				programData.put("Program", lobName);
				programData.put("EarnedPts", "-1.0");
				programData.put("PotentialPts", "-1.0");
				programData.put("EarnAmaount", "-1.0");
				programData.put("PotentialAmaount", "-1.0");
				programData.put("PotentialPayout", "-1.0");
				programDataList.add(programData);
				programDataMap.put(lobName, programDataList);

			}

		}

		comparePrograms(GroupName);

		report.logTestResult(GroupName, "", "", "", "", "");
		for (String[] log : deferredPaymentPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(GroupName, "", "", "", "", "");

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

			if (!"Medicare".equalsIgnoreCase(key)) {

				String programMatch;
				if (referenceSet.isEmpty() && actualProgramSet.size() == 1 && actualProgramSet.contains("")) {
					programMatch = "Pass";
				} else {
					boolean countMatches = referenceSet.size() == actualProgramSet.size();
					boolean namesMatch = referenceSet.equals(actualProgramSet);
					programMatch = (countMatches && namesMatch) ? "Pass" : "Fail";
				}

				report.logTestResult(GroupName, "Program match", programMatch,
						"Programs In Extract: " + referenceSet + " , Programs In UI:" + actualProgramSet, key, "");
			}
			
			
			for (Map<String, Object> program : programList) {
				String programName = program.get("Program").toString().trim();

				String potentialpoints = program.get("EarnedPts") + "/" + program.get("PotentialPts");

				if (!potentialpoints.equals("0.00/0.00")) {
					if (potentialpoints.equals("-1.0/-1.0")) {
						report.logTestResult(GroupName, "Incentive Points !=0", "Pass", "NA", key, programName);

					} else {
						report.logTestResult(GroupName, "Incentive Points !=0", "Pass", potentialpoints, key,
								programName);
					}

				} else {
					report.logTestResult(GroupName, "Incentive Points !=0", "Fail", potentialpoints, key, programName);
				}

			}

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

					boolean actualMatch = ((double) data.get("EarnedPts") == totalMetricActualPay);
					boolean potentialMatch = ((double) data.get("PotentialPts") == totalMetricPotentialPay);

					String ptsMatch = (actualMatch && potentialMatch) ? "Pass" : "Fail";

					deferredPaymentPrintLogs.add(new String[] { GroupName, "Actual & Potential points match", ptsMatch,
							"Registry Actual: " + data.get("EarnedPts") + " , Sum Actual: " + totalMetricActualPay
									+ " | Registry Potential: " + data.get("PotentialPts") + " , Sum Potential: "
									+ totalMetricPotentialPay,
							Lob, Program });

				}
			}

		}

		for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {

			String metricName = entry.getKey();

			int expectedCoinStack = (int) metricDataMap.get(metricName).get("ExpectedCoinStack");
			int actualCoinStack = (int) metricDataMap.get(metricName).get("actualCoinStack");

			String coinStackMatch = (expectedCoinStack == actualCoinStack) ? "Pass" : "Fail";

			deferredPaymentPrintLogs.add(new String[] { GroupName, metricName, coinStackMatch,
					"Expected Coin stack: " + (int) metricDataMap.get(metricName).get("ExpectedCoinStack")
							+ " , Actual Coin stack: " + (int) metricDataMap.get(metricName).get("actualCoinStack"),
					Lob, Program });

		}

	}

}
