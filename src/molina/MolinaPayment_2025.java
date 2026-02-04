package molina;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.PaymentHelper;
import report.CSVBackup;
import report.ReportGeneratorContextwise;

public class MolinaPayment_2025 extends PaymentHelper {
	ReportGeneratorContextwise report;
	String customer;
	String method;
	CSVBackup csv;

	public MolinaPayment_2025(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
		report = ReportGeneratorContextwise.getInstance();
		report.setExtraColumns(List.of("Lob", "Program"));
	}

	Map<String, List<String[]>> lobMeasuresfromCSV = loadDataFromCsv(properties.getProperty("molina_Incentive_2025"));
	Map<String, List<String[]>> programDetailsfromCSV = loadDataFromCsv(
			properties.getProperty("molinaProgramDetails_2025"));
	Map<String, List<String[]>> pmpmDetailFromCSV = loadDataFromCsv(properties.getProperty("molina_pmpm_2025"));

	Map<String, List<Map<String, Object>>> programDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> metricDataMap = new LinkedHashMap<>();
	Map<String, Boolean> incentiveProgramDropdownPresent = new LinkedHashMap<>();
	Set<String> programsfromExtract = new LinkedHashSet<>();
	boolean isIncentiveCardPresentInMedicare = false;
	boolean modalOpened = false;

	Map<String, List<Map<String, Object>>> earnedDetailsModalMap = new LinkedHashMap<>();
	List<String[]> deferredPaymentPrintLogs = new ArrayList<>();
	List<String[]> deferredcountyPrintLogs = new ArrayList<>();

	List<List<String>> backupRows = new ArrayList<>();

	public void validateMolina(String GroupName) {
		List<String[]> programDetails = programDetailsfromCSV.get(GroupName.trim());
		for (String[] row : programDetails) {
			programsfromExtract.add(row[0].trim());
		}

		openGreenRibIfClosed();
		int lobCount = driver.findElements(By.xpath(properties.getProperty("lobElements"))).size();
		for (int i = 0; i < lobCount; i++) {
			String lobName = selectLob(i);

			boolean isDropdownPresent = checkIncentiveProgramDropdown(lobName);
			incentiveProgramDropdownPresent.put(lobName, isDropdownPresent);

			int programCount = isDropdownPresent
					? driver.findElements(By.xpath(properties.getProperty("incentiveprograms"))).size()
					: driver.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram"))).size();
			programCount = programCount != 0 ? programCount : 1;

			List<Map<String, Object>> programDataList = new ArrayList<>();

			for (int j = 0; j < programCount; j++) {
				String programName = selectProgram(j, isDropdownPresent, lobName);
				Map<String, Object> programData = extractRegistryIncentive(lobName, programName);
				programDataList.add(programData);
				programDataMap.put(lobName, programDataList);
				

				List<String> backupRow = Arrays.asList(GroupName, lobName, programName,
						String.valueOf(programData.get("EarnedPts")), String.valueOf(programData.get("PotentialPts")),
						String.valueOf(programData.get("EarnAmaount")),
						String.valueOf(programData.get("PotentialAmaount")));
				backupRows.add(backupRow);

				if (!programData.get("PotentialPayout").equals("N/A")) {
					metricDataMap = getMetricIncentiveDetails(customer, programName, lobMeasuresfromCSV);
					compareMolinaPayment(GroupName, lobName, programName);
					metricDataMap.clear();
				}

				if (!lobName.equals("Medicare")) {
					if (!programData.get("PotentialPayout").equals("N/A")) {
					List<Map<String, Object>> countyDataList = extractCountyModalData(programName, lobName);
					earnedDetailsModalMap.put(programName, countyDataList);
					compareEarnedDetails(GroupName, lobName, programName);
					earnedDetailsModalMap.clear();
					}
				}

				

			}

		}
		takeDataForBackup();
		comparePrograms(GroupName);

		report.logTestResult(GroupName, "", "", "", "", "");
		for (String[] log : deferredPaymentPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(GroupName, "", "", "", "", "");
		for (String[] log : deferredcountyPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}

	}

	public String selectLob(int index) {
		openGreenRibIfClosed();
		List<WebElement> lobList = driver.findElements(By.xpath(properties.getProperty("lobElements")));
		WebElement lob = wait.until(ExpectedConditions.visibilityOf(lobList.get(index)));
		lob.click();
		driver.findElement(By.xpath(properties.getProperty("apply"))).click();
		String lobName = lob.getText().trim();
		System.out.println(lobName);
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(properties.getProperty("ajax_preloader"))));

		return lobName;
	}

	public boolean checkIncentiveProgramDropdown(String lobName) {
		try {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("incentiveProgramContainer"))));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public String selectProgram(int index, boolean dropdownPresent, String lobName) {
		String programName = "";
		if (dropdownPresent) {
			driver.findElement(By.xpath(properties.getProperty("incentiveProgramDropdown"))).click();
			List<WebElement> programList = driver.findElements(By.xpath(properties.getProperty("incentiveprograms")));
			WebElement program = wait.until(ExpectedConditions.visibilityOf(programList.get(index)));
			program.click();
			programName = program.getText();
		} else {
			List<WebElement> hiddenProgramList = driver
					.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram")));
			if (lobName.equals("Medicare")) {
				programName = hiddenProgramList.size() >= 1 ? hiddenProgramList.get(index).getAttribute("data-value")
						: lobName;
			} else {
				programName = hiddenProgramList.size() >= 1 ? hiddenProgramList.get(index).getAttribute("data-value")
						: "";
			}
		}
		System.out.println(programName);
		return programName;
	}

	public Map<String, Object> extractRegistryIncentive(String lobName, String programName) {
		Map<String, Object> data = new LinkedHashMap<>();

		try {
			List<WebElement> incentiveCard_hide = driver
					.findElements(By.xpath(properties.getProperty("incentiveCard_hide")));
			if (incentiveCard_hide.size() == 1)
				driver.findElement(By.xpath(properties.getProperty("incentiveCard"))).click();

			if (lobName.equals("Medicare")) {
				List<WebElement> incentiveCardInMedicare = driver
						.findElements(By.xpath(properties.getProperty("incentiveCard")));
				if (incentiveCardInMedicare.size() == 1)
					isIncentiveCardPresentInMedicare = true;
				
				takeScreenshot(customer);
			}

			double earnedPts = Double.parseDouble(wait
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts"))))
					.getText());

			double potentialPts = Double.parseDouble(wait
					.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts"))))
					.getText().replace("/", ""));
			
			takeScreenshot(customer);

			driver.findElement(By.xpath(properties.getProperty("incentiveCard"))).click();
			List<Object> payout = extractPotentialPayout();
			
			takeScreenshot(customer);

			data.put("Program", programName);
			data.put("EarnedPts", earnedPts);
			data.put("PotentialPts", potentialPts);
			data.put("EarnAmaount", payout.get(0));
			data.put("PotentialAmaount", payout.get(1));
			data.put("PotentialPayout", payout.get(2));

		} catch (Exception e) {
			data.put("Program", programName);
			data.put("EarnedPts", -1.0);
			data.put("PotentialPts", -1.0);
			data.put("EarnAmaount", -1.0);
			data.put("PotentialAmaount", -1.0);
			data.put("PotentialPayout", "N/A");
		}

		System.out.println(data.get("EarnedPts") + "/" + data.get("PotentialPts"));
		System.out.println(data.get("PotentialPayout"));

		return data;
	}

	public List<Map<String, Object>> extractCountyModalData(String programName, String lobName) {
		List<Map<String, Object>> countyDataList = new ArrayList<>();

		try {
			driver.findElement(By.xpath(properties.getProperty("chart"))).click();
			
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("modalHeader"))));
			modalOpened = true;
		} catch (TimeoutException e) {
			modalOpened = false;
		}

		if (modalOpened) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}			
			takeScreenshot(customer);

			int countyCount = driver.findElements(By.xpath(properties.getProperty("county"))).size();

			if ("MRPL-IPA-FQHC".equals(programName) && countyCount == 0) {
				countyDataList.add(extractModalData("NA"));
			} else {
				for (int k = 0; k < countyCount; k++) {
					WebElement county = wait.until(ExpectedConditions
							.visibilityOf(driver.findElements(By.xpath(properties.getProperty("county"))).get(k)));
					county.click();
					countyDataList.add(extractModalData(county.getText()));
				}
			}

			driver.findElement(By.xpath(properties.getProperty("cross"))).click();
		}
		return countyDataList;
	}

	public Map<String, Object> extractModalData(String countyName) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("county", countyName);

		data.put("earnedPtsInModal",
				Double.parseDouble(wait
						.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts_inModal"))))
						.getText()));
		data.put("potentialPtsInModal",
				Double.parseDouble(wait
						.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts_inModal"))))
						.getText().replace("/", "")));
		data.put("pmpmUnlocked", Double.parseDouble(wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("pmpm_unlocked"))))
				.getText().replace("$", "")));
		data.put("membership", Double.parseDouble(wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("membership"))))
				.getText().replace(",", "")));
		data.put("currentPMPM", Double.parseDouble(wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("currentPMPM"))))
				.getText().replace("$", "")));
		data.put("potentialPMPM", Double.parseDouble(wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPMPM"))))
				.getText().replace("$", "")));
		data.put("yearlyEarned", Double.parseDouble(wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_earned"))))
				.getText().replace("$", "").replace(",", "")));
		data.put("yearlyPotential",
				Double.parseDouble(wait
						.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_potential"))))
						.getText().replace("$", "").replace(",", "")));

		List<WebElement> graphBarElements = driver.findElements(By.xpath(properties.getProperty("graphBar")));
		if (graphBarElements != null && !graphBarElements.isEmpty()) {
			data.put("graphBarPresent", "Yes");
		} else {
			data.put("graphBarPresent", "No");
		}

		List<Double> pmpm = new ArrayList<>();
		for (WebElement elem : driver.findElements(By.xpath(properties.getProperty("pmpm"))))
			pmpm.add(Double.parseDouble(elem.getText().replace("$", "")));
		data.put("pmpm", pmpm);

		List<Double> pts = new ArrayList<>();
		for (WebElement elem : driver.findElements(By.xpath(properties.getProperty("pts"))))
			pts.add(Double.parseDouble(elem.getText()));
		data.put("pts", pts);

		return data;
	}

	public void comparePrograms(String GroupName) {
		for (Map.Entry<String, List<Map<String, Object>>> entry : programDataMap.entrySet()) {
			String lob = entry.getKey();
			List<Map<String, Object>> programList = entry.getValue();

			Set<String> actualProgramSet = new LinkedHashSet<>();
			for (Map<String, Object> program : programList) {
				String programName = program.get("Program").toString().trim();
				actualProgramSet.add(programName);
			}

			Set<String> referenceSet = new LinkedHashSet<>();
			for (String p : programsfromExtract) {
				if ("ALL".equalsIgnoreCase(lob)) {
					referenceSet.add(p);
				} else if ("Medi-Cal".equalsIgnoreCase(lob) && p.contains("MCD")) {
					referenceSet.add(p);
				} else if ("Marketplace".equalsIgnoreCase(lob) && p.contains("MRPL")) {
					referenceSet.add(p);
				}
			}

			boolean isDropdownPresent = incentiveProgramDropdownPresent.getOrDefault(lob, false);

			if (lob.equals("Medicare") || lob.equals("Marketplace")) {
				if (isDropdownPresent) {
					report.logTestResult(GroupName, "Incentive program drop down present", "Fail",
							"Incentive program drop down should not present", lob, "");
				} else {
					report.logTestResult(GroupName, "Incentive program drop down present", "Pass",
							"Incentive program drop down is correctly not present", lob, "");
				}
			}

			else if (lob.equals("ALL") || lob.equals("Medi-Cal")) {

				if (actualProgramSet.size() == 1) {
					if (isDropdownPresent) {
						report.logTestResult(GroupName, "Incentive program drop down present", "Fail",
								"Incentive program drop down should not present", lob, "");
					} else {
						report.logTestResult(GroupName, "Incentive program drop down present", "Pass",
								"Incentive program drop down is correctly not present", lob, "");
					}
				}

				else if (actualProgramSet.size() > 1) {
					if (isDropdownPresent) {
						report.logTestResult(GroupName, "Incentive program drop down present", "Pass",
								"Incentive program drop down correctly present", lob, "");
					} else {
						report.logTestResult(GroupName, "Incentive program drop down present", "Fail",
								"Incentive program drop down is incorrectly not present", lob, "");
					}
				}
			}

			if (lob.equals("Medicare")) {
				if (isIncentiveCardPresentInMedicare) {
					report.logTestResult(GroupName, "Incentive Card present in Medicare", "Fail",
							"Card should not present", lob, "");
				} else {
					report.logTestResult(GroupName, "Incentive Card present in Medicare", "Pass", "Card not present",
							lob, "");
				}
			}

			if (!"Medicare".equalsIgnoreCase(lob)) {

				String programMatch;
				if (referenceSet.isEmpty() && actualProgramSet.size() == 1 && actualProgramSet.contains("")) {
					programMatch = "Pass";
				} else {
					boolean countMatches = referenceSet.size() == actualProgramSet.size();
					boolean namesMatch = referenceSet.equals(actualProgramSet);
					programMatch = (countMatches && namesMatch) ? "Pass" : "Fail";
				}

				report.logTestResult(GroupName, "Program match", programMatch,
						"Programs In Extract: " + referenceSet + " , Programs In UI:" + actualProgramSet, lob, "");

				for (Map<String, Object> program : programList) {

					String programName = program.get("Program").toString().trim();

					double earnedPts = (double) program.get("EarnedPts");
					double potentialPts = (double) program.get("PotentialPts");

					double earnedAmount = (double) program.get("EarnAmaount");
					double potentialAmount = (double) program.get("PotentialAmaount");

					String potentialPayout = program.get("PotentialPayout").toString();

					// renamed to clearly represent missing data
					boolean isIncentiveValueNotPresent = earnedPts == -1.0 && potentialPts == -1.0
							&& earnedAmount == -1.0 && potentialAmount == -1.0;

					// FAIL if program is expected (in referenceSet) but data missing
					boolean isDataMissingButProgramExpected = isIncentiveValueNotPresent
							&& referenceSet.contains(programName);

					boolean isZeroPts = potentialPts == 0.0;
					boolean isZeroAmount = potentialAmount == 0.0;

					boolean isValidPts = earnedPts <= potentialPts;
					boolean isValidAmount = earnedAmount <= potentialAmount;

					String displayPts = (earnedPts == -1.0 && potentialPts == -1.0) ? "NA"
							: earnedPts + "/" + potentialPts;
					String displayAmount = (earnedAmount == -1.0 && potentialAmount == -1.0) ? "NA"
							: earnedAmount + "/" + potentialAmount;

					if (!isDataMissingButProgramExpected && (!isIncentiveValueNotPresent && !isZeroPts && !isZeroAmount
							&& isValidPts && isValidAmount
							|| (!referenceSet.contains(programName) && isIncentiveValueNotPresent))) {

						report.logTestResult(GroupName,
								"Earned/Potential points & amount != 0/0 and Earned <= Potential", "Pass",
								isIncentiveValueNotPresent ? "NA"
										: "Points: " + displayPts + " | Amount: " + displayAmount,
								lob, programName);

					} else {

						report.logTestResult(GroupName,
								"Earned/Potential points & amount != 0/0 and Earned <= Potential", "Fail",
								"Points: " + displayPts + " | Amount: " + displayAmount, lob, programName);
					}
				}

				/*
				 * for (Map<String, Object> program : programList) { String programName =
				 * program.get("Program").toString().trim();
				 * 
				 * String potentialpoints = program.get("EarnedPts") + "/" +
				 * program.get("PotentialPts"); String potentialPayout = (String)
				 * program.get("PotentialPayout");
				 * 
				 * if (!potentialpoints.equals("0.00/0.00") &&
				 * !potentialPayout.equals("0.00/0.00")) { if
				 * (potentialpoints.equals("-1.0/-1.0")) { report.logTestResult(GroupName,
				 * "Incentive points & dollar amount !=0", "Pass", "NA", lob, programName);
				 * 
				 * } else { report.logTestResult( GroupName,
				 * "Incentive points & dollar amount !=0", "Pass", "Incentive Points: " +
				 * potentialpoints + " | Incentive dollar amount: " + potentialPayout, lob,
				 * programName); }
				 * 
				 * } else { report.logTestResult( GroupName,
				 * "Incentive points & dollar amount !=0", "Fail", "Incentive Points: " +
				 * potentialpoints + " | Incentive dollar amount: " + potentialPayout, lob,
				 * programName); }
				 * 
				 * }
				 */
			}

		}

	}

	public void compareMolinaPayment(String GroupName, String Lob, String Program) {
		double totalMetricActualPay = 0, totalMetricPotentialPay = 0;
		boolean actualMatch = false;
		boolean potentialMatch = false;

		for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {

			Map<String, Object> data = entry.getValue();
			totalMetricActualPay += (double) data.get("MetricActualPay");
			totalMetricPotentialPay += (double) data.get("MetricPotentialPay");

		}

		if (!Lob.equals("Medicare")) {

			for (Map<String, Object> data : programDataMap.get(Lob)) {
				if (data.get("Program").equals(Program)) {

					double earnedPts = (double) data.get("EarnedPts");
					double potentialPts = (double) data.get("PotentialPts");

					actualMatch = (earnedPts == totalMetricActualPay);
					potentialMatch = (potentialPts == totalMetricPotentialPay);
					String ptsMatch = (actualMatch && potentialMatch) ? "Pass" : "Fail";

					deferredPaymentPrintLogs.add(new String[] { GroupName, "Actual & Potential points match", ptsMatch,
							"Registry Earned pts: " + data.get("EarnedPts") + " , Sum of metric earned pts: "
									+ totalMetricActualPay + " | Registry Potential pts: " + data.get("PotentialPts")
									+ " , Sum of metric potential pts: " + totalMetricPotentialPay,
							Lob, Program });

				}
			}

		}
		if (actualMatch && potentialMatch) {

			List<String[]> pmpmDetails = pmpmDetailFromCSV.get(Program.trim());

			for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {

				String metricName = entry.getKey();

				int expectedCoinStack = (int) metricDataMap.get(metricName).get("ExpectedCoinStack");
				int actualCoinStack = (int) metricDataMap.get(metricName).get("actualCoinStack");
				String IsMetricPresentInDataset = (String) metricDataMap.get(metricName)
						.get("IsMetricPresentInDataset");
				double metricPotentialPay = (double) metricDataMap.get(metricName).get("MetricPotentialPay");
				double metricActualPay = (double) metricDataMap.get(metricName).get("MetricActualPay");
				double metricIncentive = (double) metricDataMap.get(metricName).get("MetricIncentive");
				int denom = (int) metricDataMap.get(metricName).get("denominator");
				int num = (int) metricDataMap.get(metricName).get("numerator");

				boolean tooltip;

				if (metricActualPay != metricPotentialPay) {
					tooltip = metricDataMap.get(metricName).get("tooltipPresent").equals("Yes") ? true : false;
				} else {
					tooltip = metricDataMap.get(metricName).get("tooltipPresent").equals("NA") ? true : false;
				}

				boolean earedZeroWhennumZero = (num == 0 && metricActualPay != 0) ? false : true;

				String metricPass = (expectedCoinStack == actualCoinStack) && (IsMetricPresentInDataset.equals("Yes"))
						&& (metricPotentialPay == metricIncentive) && (metricActualPay <= metricPotentialPay)
						&& (denom != 0) && earedZeroWhennumZero && tooltip ? "Pass" : "Fail";

				deferredPaymentPrintLogs.add(new String[] { GroupName, metricName, metricPass,
						"Is Metric Present In Context key: "
								+ (String) metricDataMap.get(metricName).get("IsMetricPresentInDataset")
								+ " | Num/Denom: " + num + "/" + denom + " | Max pts in Context key: " + metricIncentive
								+ ", Potential pts in registry: " + metricPotentialPay
								+ " | Metric Actual Pay<= Metric Potential Pay : "
								+ (metricActualPay <= metricPotentialPay)
								+ " | Earned/Potential: " + metricActualPay + "/" + metricPotentialPay
								+ " | Expected Coin stack: "
								+ (int) metricDataMap.get(metricName).get("ExpectedCoinStack")
								+ " , Actual Coin stack: " + (int) metricDataMap.get(metricName).get("actualCoinStack")
								+ " | Tooltip value:" + (String) metricDataMap.get(metricName).get("pair"),
						Lob, Program });

			}
		}

	}

	public void compareEarnedDetails(String GroupName, String Lob, String Program) {
		if ("Medicare".equalsIgnoreCase(Lob))
			return;

		deferredcountyPrintLogs.add(new String[] { GroupName, "Earned pts Details modal opened",
				modalOpened ? "Pass" : "Fail", modalOpened ? "Modal opened" : "Modal did not open", Lob, Program });

		// List<Map<String, Object>> countyDataList =
		// earnedDetailsModalMap.get(Program);
		List<Map<String, Object>> countyDataList = earnedDetailsModalMap.getOrDefault(Program, new ArrayList<>());

		if (modalOpened) {
			if ("MRPL-IPA-FQHC".equals(Program)) {

				boolean countyPass = countyDataList.size() == 1 && "NA".equals(countyDataList.get(0).get("county"));

				deferredcountyPrintLogs.add(new String[] { GroupName, "County Check", countyPass ? "Pass" : "Fail",
						countyPass ? "County dropdown correctly not present for program"
								: "County dropdown should not be present for program",
						Lob, Program });

			} else {

				deferredcountyPrintLogs
						.add(new String[] { GroupName, "County Check", countyDataList.isEmpty() ? "Fail" : "Pass",
								countyDataList.isEmpty()
										? "County dropdown should be present for program but no counties found"
										: "County dropdown correctly present",
								Lob, Program });
			}

			double registryEarnedAmount = 0;
			double registryPotentialAmount = 0;
			double registryEarnedPts = 0;
			double registryPotentialPts = 0;

			for (Map<String, Object> data : programDataMap.get(Lob)) {
				if (data.get("Program").equals(Program)) {
					registryEarnedAmount = (double) data.get("EarnAmaount");
					registryPotentialAmount = (double) data.get("PotentialAmaount");
					registryEarnedPts = (double) data.get("EarnedPts");
					registryPotentialPts = (double) data.get("PotentialPts");
					break;
				}
			}

			double modalEarnedTotal = 0;
			double modalPotentialTotal = 0;

			List<String[]> pmpmDetails = pmpmDetailFromCSV.get(Program.trim());

			for (Map<String, Object> countyData : countyDataList) {

				String county = (String) countyData.get("county");

				double modalEarnedPts = (double) countyData.get("earnedPtsInModal");
				double modalPotentialPts = (double) countyData.get("potentialPtsInModal");
				double modalYearlyEarned = (double) countyData.get("yearlyEarned");
				double modalYearlyPotential = (double) countyData.get("yearlyPotential");
				double actualPMPM = (double) countyData.get("currentPMPM");
				double potentialPMPM = (double) countyData.get("potentialPMPM");
				double membership = (double) countyData.get("membership");
				double pmpmUnlocked = (double) countyData.get("pmpmUnlocked");
				String graphBarPresent = (String) countyData.get("graphBarPresent");

				modalEarnedTotal += modalYearlyEarned;
				modalPotentialTotal += modalYearlyPotential;

				double bestEarnedPts = 0;
				double bestPotentialPts = 0;
				double expectedActualPMPM = 0;
				double expectedPotentialPMPM = 0;

				for (String[] row : pmpmDetails) {
					if (county.equals(row[0].trim())) {

						double pts = Double.parseDouble(row[1]);
						double pmpm = Double.parseDouble(row[2]);

						if (pts <= modalEarnedPts && pts > bestEarnedPts) {
							bestEarnedPts = pts;
							expectedActualPMPM = pmpm;
						}
						if (pts <= modalPotentialPts && pts > bestPotentialPts) {
							bestPotentialPts = pts;
							expectedPotentialPMPM = pmpm;
						}
					}
				}

				deferredcountyPrintLogs.add(new String[] { GroupName, "Pts Match in modal",
						(modalEarnedPts == registryEarnedPts && modalPotentialPts == registryPotentialPts) ? "Pass"
								: "Fail",
						"Registry: " + registryEarnedPts + "/" + registryPotentialPts + " | Modal: " + modalEarnedPts
								+ "/" + modalPotentialPts,
						Lob, Program + "- " + county });

				deferredcountyPrintLogs
						.add(new String[] { GroupName, "PMPM Match in modal",
								(expectedActualPMPM == actualPMPM && expectedPotentialPMPM == potentialPMPM) ? "Pass"
										: "Fail",
								"Actual PMPM: " + actualPMPM + " | Potential PMPM: " + potentialPMPM, Lob,
								Program + "- " + county });

				deferredcountyPrintLogs.add(new String[] { GroupName, "Graph bar present",
						"Yes".equals(graphBarPresent) ? "Pass" : "Fail", "", Lob, Program + "- " + county });

				deferredcountyPrintLogs.add(
						new String[] { GroupName, "pmpmUnlocked match", actualPMPM == pmpmUnlocked ? "Pass" : "Fail",
								"pmpmUnlocked: " + pmpmUnlocked + " | actualPMPM: " + actualPMPM, Lob,
								Program + "- " + county });

				double expectedEarnedAmount = 12 * membership * expectedActualPMPM;
				double expectedPotentialAmount = 12 * membership * expectedPotentialPMPM;

				deferredcountyPrintLogs.add(new String[] { GroupName, "Dollar Amount Match By Formula",
						(expectedEarnedAmount == modalYearlyEarned && expectedPotentialAmount == modalYearlyPotential)
								? "Pass"
								: "Fail",
						"Formula: " + expectedEarnedAmount + "/" + expectedPotentialAmount + " | Modal: "
								+ modalYearlyEarned + "/" + modalYearlyPotential,
						Lob, Program + "- " + county });
			}

			deferredcountyPrintLogs.add(new String[] { GroupName, "Dollar amount Match in modal",
					(modalEarnedTotal == registryEarnedAmount && modalPotentialTotal == registryPotentialAmount)
							? "Pass"
							: "Fail",
					"Registry: " + registryEarnedAmount + "/" + registryPotentialAmount + " | Modal: "
							+ modalEarnedTotal + "/" + modalPotentialTotal,
					Lob, Program });
		}
	}

	public void takeDataForBackup() {
		List<String> headers = Arrays.asList("GroupName", "LobName", "ProgramName", "Earned Pts", "Potential Pts",
				"Earned Amount", "Potential Amount");
		csv.takeBackup(headers, backupRows);
	}

}
