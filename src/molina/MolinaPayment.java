package molina;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

	Map<String, List<String[]>> lobMeasuresfromCSV = loadDataFromCsv(properties.getProperty("molina_Incentive"));
	Map<String, List<String[]>> programDetailsfromCSV = loadDataFromCsv(properties.getProperty("molinaProgramDetails"));
	Map<String, List<String[]>> pmpmDetailFromCSV = loadDataFromCsv(properties.getProperty("molina_pmpm"));

	Map<String, List<Map<String, Object>>> programDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> metricDataMap = new LinkedHashMap<>();
	Map<String, Boolean> incentiveProgramDropdownPresent = new LinkedHashMap<>();
	Set<String> programsfromExtract = new LinkedHashSet<>();
	boolean isIncentiveCardPresentInMedicare = false;

	Map<String, List<Map<String, Object>>> earnedDetailsModalMap = new LinkedHashMap<>();
	// Map<String, Map<String, Object>> earnedDetailsModalMap = new
	// LinkedHashMap<>();

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

			// if (!lobName.equals("Medicare")) {

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

				programCount = driver.findElements(By.xpath(properties.getProperty("hidden_incentiveProgram"))).size();
			}
			incentiveProgramDropdownPresent.put(lobName, isIncentiveProgramDropdownpresent);

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

					/*
					 * programName = hiddenProgramList.size() >= 1 ?
					 * hiddenProgramList.get(j).getAttribute("data-value") : "";
					 */

					if (lobName.equals("Medicare")) {
						programName = hiddenProgramList.size() >= 1
								? hiddenProgramList.get(j).getAttribute("data-value")
								: lobName;
					} else if (lobName.equals("Marketplace")) {
						programName = hiddenProgramList.size() >= 1
								? hiddenProgramList.get(j).getAttribute("data-value")
								: "";
					}

				}

				List<Object> incentiveDollarAmaount = Arrays.asList(-1.0, -1.0, "N/A");
				try {
					List<WebElement> incentiveCard_hide = driver
							.findElements(By.xpath(properties.getProperty("incentiveCard_hide")));

					if (incentiveCard_hide.size() == 1) {
						driver.findElement(By.xpath(properties.getProperty("incentiveCard"))).click();
					}

					// Add for medicare if incentive card present then fail add a variable to check
					// it

					if (lobName.equals("Medicare")) {
						List<WebElement> incentiveCardInMedicare = driver
								.findElements(By.xpath(properties.getProperty("incentiveCard")));
						if (incentiveCardInMedicare.size() == 1) {
							isIncentiveCardPresentInMedicare = true;
						}

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
					metricDataMap = getMetricIncentiveDetails(customer, programName, lobMeasuresfromCSV);
					compareMolinaPayment(GroupName, lobName, programName);
					metricDataMap.clear();
				}

				if (!lobName.equals("Medicare")) {
					driver.findElement(By.xpath(properties.getProperty("chart"))).click();
					List<Map<String, Object>> earnedDetailslistcountyWise = new ArrayList<>();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					int countyCount = driver.findElements(By.xpath(properties.getProperty("county"))).size();
					System.out.println("county size: " + countyCount);

					if (programName.equals("MRPL-IPA-FQHC") && countyCount == 0) {
						String header = wait
								.until(ExpectedConditions
										.visibilityOfElementLocated(By.xpath(properties.getProperty("modalHeader"))))
								.getText();

						WebElement earnPtsElementInModal = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts_inModal"))));
						double earnedPtsInModal = Double.parseDouble(earnPtsElementInModal.getText());

						WebElement potentialPtsElementInModal = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPts_inModal"))));
						double potentialPtsInModal = Double
								.parseDouble(potentialPtsElementInModal.getText().replace("/", ""));

						WebElement pmpmUnlockedElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("pmpm_unlocked"))));
						double pmpmUnlocked = Double.parseDouble(pmpmUnlockedElement.getText().replace("$", ""));

						WebElement membershipElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("membership"))));
						double membership = Double.parseDouble(membershipElement.getText());

						WebElement currentPMPMElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("currentPMPM"))));
						double currentPMPM = Double.parseDouble(currentPMPMElement.getText().replace("$", ""));

						WebElement potentialPMPMElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPMPM"))));
						double potentialPMPM = Double.parseDouble(potentialPMPMElement.getText().replace("$", ""));

						WebElement yearlyEarnedElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_earned"))));
						double yearlyEarned = Double
								.parseDouble(yearlyEarnedElement.getText().replace("$", "").replace(",", ""));

						WebElement yearlyPotentialElement = wait.until(ExpectedConditions
								.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_potential"))));
						double yearlyPotential = Double
								.parseDouble(yearlyPotentialElement.getText().replace("$", "").replace(",", ""));

						List<WebElement> pmpmelement = driver.findElements(By.xpath(properties.getProperty("pmpm")));
						List<Double> pmpm = new ArrayList<Double>();
						for (WebElement elem : pmpmelement) {
							pmpm.add(Double.parseDouble(elem.getText().replace("$", "")));
						}

						List<WebElement> ptselement = driver.findElements(By.xpath(properties.getProperty("pts")));
						List<Double> pts = new ArrayList<Double>();
						for (WebElement elem : ptselement) {
							pts.add(Double.parseDouble(elem.getText()));
						}

						Map<String, Object> earneddata = new HashMap<>();
						earneddata.put("county", "NA");
						earneddata.put("header", header);
						earneddata.put("earnedPtsInModal", earnedPtsInModal);
						earneddata.put("potentialPtsInModal", potentialPtsInModal);
						earneddata.put("pmpmUnlocked", pmpmUnlocked);
						earneddata.put("membership", membership);
						earneddata.put("currentPMPM", currentPMPM);
						earneddata.put("potentialPMPM", potentialPMPM);
						earneddata.put("yearlyEarned", yearlyEarned);
						earneddata.put("yearlyPotential", yearlyPotential);
						earneddata.put("pmpm", pmpm);
						earneddata.put("pts", pts);

						earnedDetailslistcountyWise.add(earneddata);

						earnedDetailsModalMap.put(programName, earnedDetailslistcountyWise);

						driver.findElement(By.xpath(properties.getProperty("cross"))).click();
					} else {

						for (int k = 0; k < countyCount; k++) {
							List<WebElement> countyList = driver
									.findElements(By.xpath(properties.getProperty("county")));
							WebElement county = countyList.get(k);
							county = wait.until(ExpectedConditions.visibilityOf(county));
							county.click();

							String header = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("modalHeader"))))
									.getText();

							WebElement earnPtsElementInModal = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("earnedPts_inModal"))));
							double earnedPtsInModal = Double.parseDouble(earnPtsElementInModal.getText());

							WebElement potentialPtsElementInModal = wait
									.until(ExpectedConditions.visibilityOfElementLocated(
											By.xpath(properties.getProperty("potentialPts_inModal"))));
							double potentialPtsInModal = Double
									.parseDouble(potentialPtsElementInModal.getText().replace("/", ""));

							WebElement pmpmUnlockedElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("pmpm_unlocked"))));
							double pmpmUnlocked = Double.parseDouble(pmpmUnlockedElement.getText().replace("$", ""));

							WebElement membershipElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("membership"))));
							double membership = Double.parseDouble(membershipElement.getText().replace(",", ""));

							WebElement currentPMPMElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("currentPMPM"))));
							double currentPMPM = Double.parseDouble(currentPMPMElement.getText().replace("$", ""));

							WebElement potentialPMPMElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPMPM"))));
							double potentialPMPM = Double.parseDouble(potentialPMPMElement.getText().replace("$", ""));

							WebElement yearlyEarnedElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_earned"))));
							double yearlyEarned = Double
									.parseDouble(yearlyEarnedElement.getText().replace("$", "").replace(",", ""));

							WebElement yearlyPotentialElement = wait.until(ExpectedConditions
									.visibilityOfElementLocated(By.xpath(properties.getProperty("yearly_potential"))));
							double yearlyPotential = Double
									.parseDouble(yearlyPotentialElement.getText().replace("$", "").replace(",", ""));

							List<WebElement> pmpmelement = driver
									.findElements(By.xpath(properties.getProperty("pmpm")));
							List<Double> pmpm = new ArrayList<Double>();
							for (WebElement elem : pmpmelement) {
								pmpm.add(Double.parseDouble(elem.getText().replace("$", "")));
							}

							List<WebElement> ptselement = driver.findElements(By.xpath(properties.getProperty("pts")));
							List<Double> pts = new ArrayList<Double>();
							for (WebElement elem : ptselement) {
								pts.add(Double.parseDouble(elem.getText()));
							}

							Map<String, Object> earneddata = new HashMap<>();
							earneddata.put("county", county.getText());
							earneddata.put("header", header);
							earneddata.put("earnedPtsInModal", earnedPtsInModal);
							earneddata.put("potentialPtsInModal", potentialPtsInModal);
							earneddata.put("pmpmUnlocked", pmpmUnlocked);
							earneddata.put("membership", membership);
							earneddata.put("currentPMPM", currentPMPM);
							earneddata.put("potentialPMPM", potentialPMPM);
							earneddata.put("yearlyEarned", yearlyEarned);
							earneddata.put("yearlyPotential", yearlyPotential);
							earneddata.put("pmpm", pmpm);
							earneddata.put("pts", pts);

							earnedDetailslistcountyWise.add(earneddata);

							earnedDetailsModalMap.put(programName, earnedDetailslistcountyWise);

						}
						driver.findElement(By.xpath(properties.getProperty("cross"))).click();
					}
				}

				for (String program : earnedDetailsModalMap.keySet()) {
					System.out.println("Program: " + program);

					List<Map<String, Object>> countyDataList = earnedDetailsModalMap.get(program);
					for (Map<String, Object> data : countyDataList) {
						System.out.println(data);
					}

					System.out.println("----------------------------");
				}

				 compareEarnedDetails(GroupName, lobName, programName);
				earnedDetailsModalMap.clear();

			}

			// }

			/*
			 * else { // Add 0/0 in medicare //add incentive program dropdown not present
			 * Map<String, Object> programData = new HashMap<>(); programData.put("Program",
			 * lobName); programData.put("EarnedPts", "-1.0");
			 * programData.put("PotentialPts", "-1.0"); programData.put("EarnAmaount",
			 * "-1.0"); programData.put("PotentialAmaount", "-1.0");
			 * programData.put("PotentialPayout", "-1.0"); programDataList.add(programData);
			 * programDataMap.put(lobName, programDataList);
			 * 
			 * }
			 */

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

			boolean flag = incentiveProgramDropdownPresent.getOrDefault(key, false);
			String result = null;

			if (key.equals("Medicare") || key.equals("Marketplace")) {
				result = flag ? "Fail" : "Pass";
			}

			else if (key.equals("ALL") || key.equals("Medi-Cal")) {

				if (actualProgramSet.size() == 1) {
					result = flag ? "Fail" : "Pass";
				}

				else if (actualProgramSet.size() > 1) {
					result = flag ? "Pass" : "Fail";
				}
			}

			report.logTestResult(GroupName, "Incentive program dropdown present", result, "", key, "");

			if (key.equals("Medicare")) {
				if (isIncentiveCardPresentInMedicare) {
					report.logTestResult(GroupName, "Incentive Card present in Medicare", "Fail",
							"Card should not present", key, "");
				} else {
					report.logTestResult(GroupName, "Incentive Card present in Medicare", "Pass", "Card not present",
							key, "");
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
						report.logTestResult(GroupName, "Incentive Points !=0", "Fail", potentialpoints, key,
								programName);
					}

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
			String IsMetricPresentInDataset = (String) metricDataMap.get(metricName).get("IsMetricPresentInDataset");
			double metricPotentialPay = (double) metricDataMap.get(metricName).get("MetricPotentialPay");
			double metricIncentive = (double) metricDataMap.get(metricName).get("MetricIncentive");

			// String coinStackMatch = (expectedCoinStack == actualCoinStack) ? "Pass" :
			// "Fail";
			String metricPass = (expectedCoinStack == actualCoinStack) && (IsMetricPresentInDataset.equals("Yes"))
					&& (metricPotentialPay == metricIncentive)
					&& ((int) metricDataMap.get(metricName).get("denominator") != 0) ? "Pass" : "Fail";

			deferredPaymentPrintLogs.add(new String[] { GroupName, metricName, metricPass,
					"Expected Coin stack: " + (int) metricDataMap.get(metricName).get("ExpectedCoinStack")
							+ " , Actual Coin stack: " + (int) metricDataMap.get(metricName).get("actualCoinStack")
							+ "|" + "IsMetricPresentInDataset: "
							+ (String) metricDataMap.get(metricName).get("IsMetricPresentInDataset") + "|"
							+ "MetricIncentive: " + metricIncentive + "|" + "MetricPotentialPay: " + metricPotentialPay
							+ "Denom :" + (int) metricDataMap.get(metricName).get("denominator"),
					Lob, Program });

		}

	}

	public void compareEarnedDetails(String GroupName, String Lob, String Program) {
	    if (Lob.equals("Medicare")) return;

	    List<Map<String, Object>> countyDataList = earnedDetailsModalMap.getOrDefault(Program, new ArrayList<>());

	    if ("MRPL-IPA-FQHC".equals(Program)) {
	        if (countyDataList.size() == 1 && "NA".equals(countyDataList.get(0).get("county"))) {
	            report.logTestResult(GroupName, "County Check", "Pass",
	                    "County dropdown correctly not present for program", Lob, Program);
	        } else {
	            // Build a simple comma-separated list of counties
	            StringBuilder counties = new StringBuilder();
	            for (Map<String, Object> c : countyDataList) {
	                if (counties.length() > 0) counties.append(", ");
	                counties.append(c.get("county").toString());
	            }
	            String countyStr = counties.length() > 0 ? counties.toString() : "None";

	            report.logTestResult(GroupName, "County Check", "Fail",
	                    "County dropdown should not be present for program, but found: " + countyStr,
	                    Lob, Program);
	        }
	    } else {
	        if (!countyDataList.isEmpty()) {
	            StringBuilder counties = new StringBuilder();
	            for (Map<String, Object> c : countyDataList) {
	                if (counties.length() > 0) counties.append(", ");
	                counties.append(c.get("county").toString());
	            }
	            String countyStr = counties.toString();

	            report.logTestResult(GroupName, "County Check", "Pass",
	                    "County dropdown correctly present: " + countyStr, Lob, Program);
	        } else {
	            report.logTestResult(GroupName, "County Check", "Fail",
	                    "County dropdown should be present for program but no counties found", Lob, Program);
	        }
	    }
	}


	/*
	 * public void compareEarnedDetails(String GroupName, String Lob, String
	 * Program) {
	 * 
	 * if (!Lob.equals("Medicare")) {
	 * 
	 * List<String[]> pmpmDetails = pmpmDetailFromCSV.get(Program.trim());
	 * 
	 * Map<String, Object> modalData = earnedDetailsModalMap.get(Program); if
	 * (modalData == null) { report.logTestResult(GroupName, "Earned Details Modal",
	 * "Fail", "Modal data not found for program", Lob, Program); return; }
	 * 
	 * // Modal values double modalEarnedPts = (double)
	 * modalData.get("earnedPtsInModal"); double modalPotentialPts = (double)
	 * modalData.get("potentialPtsInModal"); double modalYearlyEarned = (double)
	 * modalData.get("yearlyEarned"); double modalYearlyPotential = (double)
	 * modalData.get("yearlyPotential"); double potentialPMPM = (double)
	 * modalData.get("potentialPMPM"); List<String> countyList = (List<String>)
	 * modalData.get("county");
	 * 
	 * // Registry values from programDataMap List<Map<String, Object>> programList
	 * = programDataMap.get(Lob); double registryEarnAmount = 0; double
	 * registryPotentialPts = 0; double registryYearlyEarned = 0; double
	 * registryYearlyPotential = 0; boolean found = false;
	 * 
	 * String countyNames = String.join(", ", countyList);
	 * 
	 * // County validation if (Program.equals("MRPL-IPA-FQHC")) { if
	 * (countyList.size() == 0) { report.logTestResult(GroupName, "County Check",
	 * "Pass", "County dropdown correctly not present for program", Lob, Program); }
	 * else { report.logTestResult(GroupName, "County Check", "Fail",
	 * "County should not be present but found (" + countyList.size() + "): " +
	 * countyNames, Lob, Program); } } else { if (countyList.size() > 0) {
	 * report.logTestResult(GroupName, "County Check", "Pass",
	 * "County dropdown present (" + countyList.size() + "): " + countyNames, Lob,
	 * Program); } else { report.logTestResult(GroupName, "County Check", "Fail",
	 * "County list is empty but should contain values", Lob, Program); } }
	 * 
	 * for (Map<String, Object> prog : programList) { if
	 * (prog.get("Program").equals(Program)) { registryEarnAmount = (double)
	 * prog.get("EarnedPts"); registryPotentialPts = (double)
	 * prog.get("PotentialPts"); registryYearlyEarned = (double)
	 * prog.get("EarnAmaount"); registryYearlyPotential = (double)
	 * prog.get("PotentialAmaount"); found = true; break; } }
	 * 
	 * if (!found) { report.logTestResult(GroupName, "Earned Details Modal", "Fail",
	 * "Registry entry not found for program", Lob, Program); return; } String
	 * potentialPMPMmatch="Fail"; double ptsfromContext = 0; double
	 * potentialPMPMfromContext = 0 ;
	 * 
	 * for (String[] pts : pmpmDetails) { ptsfromContext
	 * =Double.parseDouble(pts[1]); potentialPMPMfromContext
	 * =Double.parseDouble(pts[2]); if(modalEarnedPts<=ptsfromContext) {
	 * if(potentialPMPMfromContext==potentialPMPM) { potentialPMPMmatch="Pass"; } }
	 * 
	 * }
	 * 
	 * // ==== Comparisons & Report Log ====
	 * 
	 * // Earned Points String earnedPtsResult = (registryEarnAmount ==
	 * modalEarnedPts) ? "Pass" : "Fail"; report.logTestResult(GroupName,
	 * "EarnedPts Match", earnedPtsResult, "Registry: " + registryEarnAmount +
	 * " | Modal: " + modalEarnedPts, Lob, Program);
	 * 
	 * // Potential Points String potentialPtsResult = (registryPotentialPts ==
	 * modalPotentialPts) ? "Pass" : "Fail"; report.logTestResult(GroupName,
	 * "PotentialPts Match", potentialPtsResult, "Registry: " + registryPotentialPts
	 * + " | Modal: " + modalPotentialPts, Lob, Program);
	 * 
	 * // Yearly Earned String yearlyEarnedResult = (registryYearlyEarned ==
	 * modalYearlyEarned) ? "Pass" : "Fail"; report.logTestResult(GroupName,
	 * "YearlyEarned Match", yearlyEarnedResult, "Registry: " + registryYearlyEarned
	 * + " | Modal: " + modalYearlyEarned, Lob, Program);
	 * 
	 * // Yearly Potential String yearlyPotentialResult = (registryYearlyPotential
	 * == modalYearlyPotential) ? "Pass" : "Fail"; report.logTestResult(GroupName,
	 * "YearlyPotential Match", yearlyPotentialResult, "Registry: " +
	 * registryYearlyPotential + " | Modal: " + modalYearlyPotential, Lob, Program);
	 * 
	 * report.logTestResult(GroupName, "Potential PMPM Match", potentialPMPMmatch,
	 * "Expected potential pmpm: " + potentialPMPMfromContext +
	 * " | Actual potential pmpm:: " + potentialPMPM, Lob, Program); } }
	 */

}
