package paymentHTML;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.FullPageScreenshotBothAxes;
import paymentHelper.PaymentHelper;
import report.CSVBackup;
import report.ReportGeneratorContextwise;

public class PaymentHTML extends PaymentHelper {

	ReportGeneratorContextwise report;
	CSVBackup csv;
	String customer;
	String method;

	public PaymentHTML(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		report = ReportGeneratorContextwise.getInstance();
		report.setExtraColumns(List.of("Quarter"));
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
	}

	Map<String, List<Map<String, Object>>> fileNamesMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> paymentDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> programDataMap = new LinkedHashMap<>();
	List<List<String>> backupRows = new ArrayList<>();
	List<String[]> deferredPaymentPrintLogs = new ArrayList<>();

	public void validatePaymentHTML(String NPI) {
		List<WebElement> years = new ArrayList<>();

		try {

			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("sidebar"))))
					.click();
			wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("reportInSidebar"))))
					.click();
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("cozevaPaymentReports")))).click();
		} catch (Exception e) {
			report.logTestResult(NPI, "Navigation to Payments Reports Page", "Fail", e.getMessage(), "");
			return;
		}
		switchToNewTab();

		try {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown")))).click();
			years = driver.findElements(By.xpath(properties.getProperty("yearDropdown")));
		} catch (Exception e) {
			report.logTestResult(NPI, "Payments Reports Dropdown", "Fail", e.getMessage(), "");
			return;
		}

		if (years.size() == 0) {
			report.logTestResult(NPI, "Payments Reports Dropdown", "Fail", "Dropdown is blank", " ");
			return;
		} else if (years.size() == 1 && "Choose your option".equals(years.get(0).getText())) {
			report.logTestResult(NPI, "Payments Reports Dropdown", "Pass", "No Data Available", " ");
			List<String> backupRow = Arrays.asList(NPI, "No Quarters", "No Programs", "NA", "NA", "NA", "NA");
			backupRows.add(backupRow);
			takeDataForBackup();
			return;
		} else {
			report.logTestResult(NPI, "Payments Reports Dropdown", "Pass",
					"Year: " + years.stream().map(WebElement::getText).collect(Collectors.joining(", ")), " ");
		}

		wait.until(
				ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown"))))
				.click();

		for (int i = 0; i < years.size(); i++) {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown")))).click();

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			years = driver.findElements(By.xpath(properties.getProperty("yearDropdown")));
			String year = years.get(i).getText();
			years.get(i).click();
			System.out.println(year);

			List<Map<String, Object>> fileNameDataList = new ArrayList<>();
			
			 /* List<WebElement> paymentReport = driver
			  .findElements(By.xpath(properties.getProperty("paymentReportTable")));*/
			List<WebElement> paymentReport = wait.until(
				    ExpectedConditions.visibilityOfAllElementsLocatedBy(
				        By.xpath(properties.getProperty("paymentReportTable"))
				    )
				);
			
			System.out.println("No. of reports: " +paymentReport.size());

			for (int k = 0; k < paymentReport.size(); k++) {

				paymentReport = driver.findElements(By.xpath(properties.getProperty("paymentReportTable")));
				WebElement currentRow = paymentReport.get(k);

				WebElement fileLink = currentRow.findElement(By.xpath(properties.getProperty("filenames")));
				String fileName = fileLink.getText();
				System.out.println(fileName);

				String[] parts = fileName.split(" ");
				String quarterText = String.join(" ", Arrays.copyOfRange(parts, parts.length - 2, parts.length));
				String formattedQuarter = quarterText.replace(" ", "_");

				int downloads = Integer
						.parseInt(currentRow.findElement(By.xpath(properties.getProperty("downloads"))).getText());

				Map<String, Object> fileNameData = new LinkedHashMap<>();
				fileNameData.put("fileName", fileName);
				fileNameData.put("downloads", downloads);
				fileNameDataList.add(fileNameData);
				fileNamesMap.put(year, fileNameDataList);

				wait.until(ExpectedConditions.elementToBeClickable(fileLink));
				fileLink.click();

				switchToNewTab();

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				String provider = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("providerName"), formattedQuarter))))
						.getText();
				System.out.println(provider);
				
				String providerNPI = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("NPI"), formattedQuarter))))
						.getText();
				//System.out.println(providerNPI);
				String taxId = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("taxId"), formattedQuarter))))
						.getText();
				//System.out.println(taxId);
				String rundate = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("rundate"), formattedQuarter))))
						.getText();
				//System.out.println(rundate);
				String rundateValue = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("rundateValue"), formattedQuarter))))
						.getText();
				//System.out.println(rundateValue);

				takeScreenshot(customer);

				Map<String, Object> quarterData = new HashMap<>();
				List<String> programNames = new ArrayList<>();

				// Selected Quarter
				String MY = quarterText.split(" ")[0];
				String q = quarterText.split(" ")[1];

				if (driver
						.findElement(By
								.xpath(String.format(properties.getProperty("programSummaryHeader"), formattedQuarter)))
						.getText().contains(MY)
						&& driver
								.findElement(By.xpath(
										String.format(properties.getProperty("amountPaidInQuarter"), formattedQuarter)))
								.getText().contains(q)
						&& driver.findElement(By.xpath(properties.getProperty("cardHeader"))).getText()
								.equals(quarterText)) {
					quarterData.put("displayedSelectedQuarter", "Pass");
				} else {
					quarterData.put("displayedSelectedQuarter", "Fail");
				}

				// ePaymentEligibility
				String ePaymentEligibility = wait
						.until(ExpectedConditions.visibilityOfElementLocated(
								By.xpath(String.format(properties.getProperty("ePaymentSignedUp"), formattedQuarter))))
						.getText();
				quarterData.put("ePaymentEligibility", ePaymentEligibility);

				// programNames
				List<WebElement> programElements = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
						By.xpath(String.format(properties.getProperty("programsForePayments"), formattedQuarter))));

				for (WebElement el : programElements) {
					String[] words = el.getText().trim().split(" ");
					programNames.add(words[words.length - 1]);
				}
				quarterData.put("programNames", programNames);

				// numberOfIndividualProgramShown
				int numberOfIndividualProgramShown = driver
						.findElements(By.xpath(
								String.format(properties.getProperty("numberOfIndividualProgram"), formattedQuarter)))
						.size();
				quarterData.put("numberOfIndividualProgramShown", numberOfIndividualProgramShown);

				// AmountsInProgramSummary
				List<WebElement> amountsInProgramSummary = wait
						.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(
								String.format(properties.getProperty("amountsInProgramSummary"), formattedQuarter))));

				String[] totalAmountKeys = { "Earned Amount", "Previous ePayments", "Previous manual payments",
						"Amount Paid in Quarter" };

				for (int j = 0; j < amountsInProgramSummary.size(); j++) {
					
					
					WebElement elem = wait.until(
				            ExpectedConditions.visibilityOf(amountsInProgramSummary.get(j))
				    );
					Double value = Double
							.parseDouble(elem.getText().replace("$", "").replace(",", ""));
					quarterData.put(totalAmountKeys[j], value);
				}

				paymentDataMap.put(quarterText, quarterData);
				
				List<String> backupRow = Arrays.asList(NPI, quarterText,
						String.join(";", (List<String>) paymentDataMap.get(quarterText).get("programNames")),
						String.valueOf(paymentDataMap.get(quarterText).get("Earned Amount")),
						String.valueOf(paymentDataMap.get(quarterText).get("Previous ePayments")),
						String.valueOf(paymentDataMap.get(quarterText).get("Previous manual payments")),
						String.valueOf(paymentDataMap.get(quarterText).get("Amount Paid in Quarter")));
				backupRows.add(backupRow);


				List<WebElement> individualProgramHeaderElements = wait
						.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(
								String.format(properties.getProperty("individualProgramHeader"), formattedQuarter))));

				for (WebElement el : individualProgramHeaderElements) {
					Map<String, Object> programData = new HashMap<>();

					WebElement programElement = wait.until(ExpectedConditions.visibilityOf(el));
					String program = programElement.getText();

					if (program != null && program.toLowerCase().contains("program")) {
						program = program.replaceAll("(?i)program", " ").trim();
					}

					WebElement individualProgram = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
							String.format(properties.getProperty("individualProgram"), program, formattedQuarter))));

					// AddMeasureNames
					List<WebElement> measureElements = individualProgram
							.findElements(By.xpath(properties.getProperty("measureName")));
					List<String> measureList = new ArrayList<>();
					for (WebElement measure : measureElements) {
						WebElement elem = wait.until(
					            ExpectedConditions.visibilityOf(measure)
					    );
						measureList.add(elem.getText());
					}
					programData.put("measures", measureList);

					// AddIncentivePerMeasure
					List<WebElement> incentiveElements = individualProgram
							.findElements(By.xpath(properties.getProperty("incentivePermeasure")));
					List<Double> incentiveList = new ArrayList<>();
					for (WebElement incentive : incentiveElements) {
						WebElement elem = wait.until(
					            ExpectedConditions.visibilityOf(incentive)
					    );
						incentiveList.add(Double.parseDouble(elem.getText().replace("$", "").replace(",", "")));
					}
					programData.put("incentives", incentiveList);

					// AddTotalAmountPerIndividualProgram
					List<WebElement> totalAmountsPerMeasure = individualProgram
							.findElements(By.xpath(properties.getProperty("totalAmaountsPerMeasure")));

					for (int j = 0; j < totalAmountsPerMeasure.size(); j++) {
						
						WebElement elem = wait.until(
					            ExpectedConditions.visibilityOf(totalAmountsPerMeasure.get(j))
					    );
						
						Double value = Double
								.parseDouble(elem.getText().replace("$", "").replace(",", ""));
						programData.put(totalAmountKeys[j], value);

					}

					programDataMap.put(program, programData);
				}
				comparePaymentHTML(NPI, quarterText);
				programDataMap.clear();
				driver.close();
				switchToNewTab();

			}
			takeDataForBackup();
		}

		validateFileNames(NPI);
		for (String[] log : deferredPaymentPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4]);
		}

	}

	public void validateFileNames(String NPI) {
		
		for (Map.Entry<String, List<Map<String, Object>>> entry : fileNamesMap.entrySet()) {
			String year = entry.getKey();
			List<Map<String, Object>> fileList = entry.getValue();
			
			String column1 = driver.findElement(By.xpath(properties.getProperty("fileNameColumn"))).getText();
			String column2 = driver.findElement(By.xpath(properties.getProperty("downloadsColumn"))).getText();

			if (column1.equals("Filename")) {
				report.logTestResult(NPI, "FileName column presents: "+year, "Pass", "", " ");
			} else {
				report.logTestResult(NPI, "FileName column presents: "+year, "Fail", "", " ");
			}
			if (column2.equals("Downloads")) {
				report.logTestResult(NPI, "Downloads column presents: "+year, "Pass", "", " ");
			} else {
				report.logTestResult(NPI, "Downloads column presents: "+year, "Fail", "", " ");
			}


			boolean allQuartersPass = true;
			boolean allDownloadsPass = true;
			List<String> mismatchedFiles = new ArrayList<>();
			StringBuilder downloadsDetails = new StringBuilder();

			for (Map<String, Object> fileData : fileList) {
				String fileName = (String) fileData.get("fileName");
				int downloads = (int) fileData.get("downloads");
				downloadsDetails.append(fileName).append(": ").append(downloads).append(", ");

				if (!fileName.contains(year)) {
					allQuartersPass = false;
					mismatchedFiles.add(fileName);
				}
				if (downloads < 0) {
					allDownloadsPass = false;
				}
			}

			if (allQuartersPass) {
				report.logTestResult(NPI, "Quarter Reports displayed in FileName column for: " + year, "Pass",
						"All filenames match the year", " ");
			} else {
				report.logTestResult(NPI, "Quarter Reports displayed in FileName column for: " + year, "Fail",
						"One or more filenames do not match the year: " + String.join(", ", mismatchedFiles), " ");
			}

			if (allDownloadsPass) {
				report.logTestResult(NPI, "Downloads values: " + year, "Pass", downloadsDetails.toString(), " ");
			} else {
				report.logTestResult(NPI, "Downloads values: " + year, "Fail",
						downloadsDetails.toString(), " ");
			}
		}
	}

	public void comparePaymentHTML(String provider, String quarter) {

		double totalEarnedAmaount = 0;
		double totalpreviousEpayment = 0;
		double totalManualpreviousEpayment = 0;
		double totalAmaountPaidInQuarter = 0;

		StringBuilder earnedDetails = new StringBuilder();
		StringBuilder previousEPaymentDetails = new StringBuilder();
		StringBuilder manualPreviousDetails = new StringBuilder();
		StringBuilder paidInQuarterDetails = new StringBuilder();

		List<String> expectedProgramsList = (List<String>) paymentDataMap.get(quarter).get("programNames");
		Set<String> expectedProgramSet = new HashSet<>(expectedProgramsList);
		Set<String> actualProgramSet = new HashSet<>(programDataMap.keySet());

		boolean countMatches = expectedProgramSet.size() == actualProgramSet.size();
		boolean namesMatch = expectedProgramSet.equals(actualProgramSet);

		StringBuilder incentivesDetails = new StringBuilder();
		String isIncentivePositive = "";

		for (Map.Entry<String, Map<String, Object>> entry : programDataMap.entrySet()) {
			String programName = entry.getKey();
			Map<String, Object> data = entry.getValue();

			double earned = (double) data.get("Earned Amount");
			double prevEPayment = (double) data.get("Previous ePayments");
			double manualPayment = (double) data.get("Previous manual payments");
			double paidInQuarter = (double) data.get("Amount Paid in Quarter");

			totalEarnedAmaount = totalEarnedAmaount + earned;
			totalpreviousEpayment = totalpreviousEpayment + prevEPayment;
			totalManualpreviousEpayment = totalManualpreviousEpayment + manualPayment;
			totalAmaountPaidInQuarter = totalAmaountPaidInQuarter + paidInQuarter;

			earnedDetails.append(programName).append(": ").append(earned).append(", ");
			previousEPaymentDetails.append(programName).append(": ").append(prevEPayment).append(", ");
			manualPreviousDetails.append(programName).append(": ").append(manualPayment).append(", ");
			paidInQuarterDetails.append(programName).append(": ").append(paidInQuarter).append(", ");

			List<String> measures = (List<String>) data.get("measures");
			List<Double> incentive = (List<Double>) data.get("incentives");
			incentivesDetails.append(programName).append("- ");
			for (int i = 0; i < incentive.size(); i++) {
				incentivesDetails.append(measures.get(i)).append(": ").append(incentive.get(i)).append(", ");
				if (incentive.get(i) > 0) {
					isIncentivePositive = "Pass";
				} else {
					isIncentivePositive = "Fail";
				}
			}

		}
		String matchePaymentEligibility = "Yes".equalsIgnoreCase(
				String.valueOf(paymentDataMap.get(quarter).get("ePaymentEligibility"))) ? "Pass" : "Fail";
		String programMatch = (countMatches && namesMatch) ? "Pass" : "Fail";
		String earnedAmountMatches = (double) paymentDataMap.get(quarter).get("Earned Amount") == totalEarnedAmaount
				? "Pass"
				: "Fail";

		String previousEpaymentMatches = (double) paymentDataMap.get(quarter)
				.get("Previous ePayments") == totalpreviousEpayment ? "Pass" : "Fail";

		String ManualpreviousEpaymentMatches = (double) paymentDataMap.get(quarter)
				.get("Previous manual payments") == totalManualpreviousEpayment ? "Pass" : "Fail";

		String AmaountPaidInQuarterMatches = (double) paymentDataMap.get(quarter)
				.get("Amount Paid in Quarter") == totalAmaountPaidInQuarter ? "Pass" : "Fail";

		deferredPaymentPrintLogs.add(new String[] { provider, "Displayed Selected Quarter",
				paymentDataMap.get(quarter).get("displayedSelectedQuarter").toString(), "", quarter });

		deferredPaymentPrintLogs.add(new String[] { provider, "E-Payment Eligibility", matchePaymentEligibility,
				"E-Payment Eligibility: " + paymentDataMap.get(quarter).get("ePaymentEligibility"), quarter });

		deferredPaymentPrintLogs.add(new String[] { provider, "Program Match", programMatch,
				"Overall: " + expectedProgramSet + " , Individual: " + actualProgramSet, quarter });

		deferredPaymentPrintLogs.add(new String[] { provider, "Earned Amount Match", earnedAmountMatches,
				"Overall Earned Amount: " + paymentDataMap.get(quarter).get("Earned Amount") + " " + earnedDetails,
				quarter });

		deferredPaymentPrintLogs.add(new String[] {
				provider, "Previous E-payment Match", previousEpaymentMatches, "Overall Previous E-Payments: "
						+ paymentDataMap.get(quarter).get("Previous ePayments") + " " + previousEPaymentDetails,
				quarter });

		deferredPaymentPrintLogs.add(new String[] { provider, "Manual Previous E-payment Match",
				ManualpreviousEpaymentMatches, "Overall Previous manual payments: "
						+ paymentDataMap.get(quarter).get("Previous manual payments") + " " + manualPreviousDetails,
				quarter });

		deferredPaymentPrintLogs.add(new String[] { provider, "Amount Paid In Quarter Match",
				AmaountPaidInQuarterMatches, "Overall Amount Paid in Quarter: "
						+ paymentDataMap.get(quarter).get("Amount Paid in Quarter") + " " + paidInQuarterDetails,
				quarter });

		deferredPaymentPrintLogs.add(
				new String[] { provider, "Incentive>=0", isIncentivePositive, incentivesDetails.toString(), quarter });

	}
	
	public void takeDataForBackup() {
		List<String> headers = Arrays.asList("Provider", "Quarter", "Program Names", "Earned Amount",
				"Previous E-Payments", "Previous manual payments", "Amount Paid in Quarter");
		csv.takeBackup(headers, backupRows);
	}
}
