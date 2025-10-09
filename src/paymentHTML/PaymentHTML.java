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
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	Map<String, Map<String, Object>> paymentDataMap = new HashMap<>();
	Map<String, Map<String, Object>> programDataMap = new HashMap<>();
	List<List<String>> backupRows = new ArrayList<>();

	public void validatePaymentHTML(String NPI) {

		WebElement contextElement = wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
		String context = contextElement.getText();

		List<WebElement> quarters = new ArrayList<>();

		try {

			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("sidebar"))))
					.click();
			wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("reportInSidebar"))))
					.click();
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTML"))))
					.click();
		} catch (Exception e) {
			report.logTestResult(context, "Navigation to Payment HTML Page", "Fail", e.getMessage(), "");
			return;
		}
		switchToNewTab();

		try {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown")))).click();
			quarters = driver.findElements(By.xpath(properties.getProperty("quarters")));
		} catch (Exception e) {
			report.logTestResult(context, "Payment HTML Dropdown", "Fail", e.getMessage(), "");
			return;
		}
		// List<WebElement> quarters =
		// driver.findElements(By.xpath(properties.getProperty("quarters")));

		if (quarters.size() == 0) {
			String noQuarter = driver.findElement(By.xpath(properties.getProperty("noQuarter"))).getText();
			if (noQuarter.equals("Choose your option")) {
				System.out.println("❌ No quarters available for provider: " + NPI);
				report.logTestResult(context, "No Quarter Found", "Fail", "Dropdown only shows 'Choose your option'",
						"NA");

				List<String> backupRow = Arrays.asList(context, "No Quarters", "No Programs", "NA", "NA", "NA", "NA");
				backupRows.add(backupRow);
				takeDataForBackup();
				return;
			}
		}
		wait.until(
				ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown"))))
				.click();

		for (int i = 0; i < quarters.size(); i++) {
			wait.until(ExpectedConditions
					.visibilityOfElementLocated(By.xpath(properties.getProperty("paymentHTMLDropdown")))).click();

			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			quarters = wait.until(
					ExpectedConditions.visibilityOfAllElementsLocatedBy(By.xpath(properties.getProperty("quarters"))));

			WebElement quarter = quarters.get(i);
			String quarterText = quarter.getText();
			String formattedQuarter = quarterText.replace(" ", "_");

			quarter.click();

			String provider = wait
					.until(ExpectedConditions.visibilityOfElementLocated(
							By.xpath(String.format(properties.getProperty("providerName"), formattedQuarter))))
					.getText();

			takeScreenshot(customer);
			
			
			
		

			Map<String, Object> quarterData = new HashMap<>();
			List<String> programNames = new ArrayList<>();

			// Selected Quarter
			String MY = quarterText.split(" ")[0];
			String q = quarterText.split(" ")[1];

			if (driver
					.findElement(
							By.xpath(String.format(properties.getProperty("programSummaryHeader"), formattedQuarter)))
					.getText().contains(MY)
					&& driver
							.findElement(By.xpath(
									String.format(properties.getProperty("amountPaidInQuarter"), formattedQuarter)))
							.getText().contains(q)) {
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
			List<WebElement> amountsInProgramSummary = wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
					By.xpath(String.format(properties.getProperty("amountsInProgramSummary"), formattedQuarter))));

			String[] totalAmountKeys = { "Earned Amount", "Previous ePayments", "Previous manual payments",
					"Amount Paid in Quarter" };

			for (int j = 0; j < amountsInProgramSummary.size(); j++) {
				Double value = Double
						.parseDouble(amountsInProgramSummary.get(j).getText().replace("$", "").replace(",", ""));
				quarterData.put(totalAmountKeys[j], value);
			}
			
			
			
			try {
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("download"))))
						.click();
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("export"))))
						.click();
				Thread.sleep(3000);
				String[] files = validateDownloadedCSVBySize(NPI, formattedQuarter);
				quarterData.put("exportedFileName", files[0]);
				quarterData.put("exportedFileStatus", files[1]);
				quarterData.put("exportedFileDetails", "");
			} catch (Exception e) {
				quarterData.put("exportedFileName", "");
				quarterData.put("exportedFileStatus", "Fail");
				quarterData.put("exportedFileDetails", e.getMessage());
			}

			
		/*	try {
	            FullPageScreenshotBothAxes.waitForPageReady(driver, 30000);
	            String scrollbarid = "payment_html_"+formattedQuarter;

	            FullPageScreenshotBothAxes.takeScrollableElementScreenshot(
	                    driver,
	                    scrollbarid,
	                    "C:\\HNET_Migration_SS\\PaymentHTML\\"+NPI.trim()+"_"+quarterText.trim()+".png",
	                    100,
	                    60,
	                    300
	            );

	            System.out.println("Processing complete.");
	        } catch(Exception e) {
	        	System.out.println(e);
	        }*/

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

				WebElement individualProgram = wait.until(ExpectedConditions.presenceOfElementLocated(By
						.xpath(String.format(properties.getProperty("individualProgram"), program, formattedQuarter))));

				// AddMeasureNames
				List<WebElement> measureElements = individualProgram
						.findElements(By.xpath(properties.getProperty("measureName")));
				List<String> measureList = new ArrayList<>();
				for (WebElement measure : measureElements) {
					measureList.add(measure.getText());
				}
				programData.put("measures", measureList);

				// AddIncentivePerMeasure
				List<WebElement> incentiveElements = individualProgram
						.findElements(By.xpath(properties.getProperty("incentivePermeasure")));
				List<Double> incentiveList = new ArrayList<>();
				for (WebElement incentive : incentiveElements) {
					incentiveList.add(Double.parseDouble(incentive.getText().replace("$", "").replace(",", "")));
				}
				programData.put("incentives", incentiveList);

				// AddTotalAmountPerIndividualProgram
				List<WebElement> totalAmountsPerMeasure = individualProgram
						.findElements(By.xpath(properties.getProperty("totalAmaountsPerMeasure")));

				for (int j = 0; j < totalAmountsPerMeasure.size(); j++) {
					Double value = Double
							.parseDouble(totalAmountsPerMeasure.get(j).getText().replace("$", "").replace(",", ""));
					programData.put(totalAmountKeys[j], value);

				}

				programDataMap.put(program, programData);
			}

			

			comparePaymentHTML(NPI, quarterText);
			programDataMap.clear();
		}
		takeDataForBackup();

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

		report.logTestResult(provider, "Displayed Selected Quarter",
				paymentDataMap.get(quarter).get("displayedSelectedQuarter").toString(), "", quarter);
		report.logTestResult(provider, "E-Payment Eligibility", matchePaymentEligibility,
				"E-Payment Eligibility: " + paymentDataMap.get(quarter).get("ePaymentEligibility"), quarter);
		report.logTestResult(provider, "Program Match", programMatch,
				"Overall: " + expectedProgramSet + " , Individual: " + actualProgramSet, quarter);
		report.logTestResult(provider, "Earned Amount Match", earnedAmountMatches, "Overall Earned Amount: "
				+ paymentDataMap.get(quarter).get("Earned Amount") + " " + earnedDetails.toString(), quarter);
		report.logTestResult(provider, "Previous E-payment Match", previousEpaymentMatches,
				"Overall Previous E-Payments: " + paymentDataMap.get(quarter).get("Previous ePayments") + " "
						+ previousEPaymentDetails.toString(),
				quarter);
		report.logTestResult(provider, "Manual Previous E-payment Match", ManualpreviousEpaymentMatches,
				"Overall Previous manual payments: " + paymentDataMap.get(quarter).get("Previous manual payments") + " "
						+ manualPreviousDetails.toString(),
				quarter);
		report.logTestResult(provider, "Amount Paid In Quarter Match", AmaountPaidInQuarterMatches,
				"Overall Amount Paid in Quarter: " + paymentDataMap.get(quarter).get("Amount Paid in Quarter") + " "
						+ paidInQuarterDetails.toString(),
				quarter);
		report.logTestResult(provider, "Incentive>=0", isIncentivePositive, incentivesDetails.toString(), quarter);
		report.logTestResult(provider, "Export Member-Level Report",
				(String) paymentDataMap.get(quarter).get("exportedFileStatus"),
				(String) paymentDataMap.get(quarter).get("exportedFileName") + " , "
						+ (String) paymentDataMap.get(quarter).get("exportedFileDetails"),
				quarter);
		// report.logTestResult(provider, "Print to
		// PDF",(String)paymentDataMap.get(quarter).get("PrintToPDFClickStatus"),(String)paymentDataMap.get(quarter).get("PrintToPDFClickDetails"),quarter);

	}

	public void takeDataForBackup() {
		List<String> headers = Arrays.asList("Provider", "Quarter", "Program Names", "Earned Amount",
				"Previous E-Payments", "Previous manual payments", "Amount Paid in Quarter");
		csv.takeBackup(headers, backupRows);
	}

	public String[] validateDownloadedCSVBySize(String NPI, String Quarter) {
		String baseFileName = Quarter + "_" + NPI + "_Member_Level_Report";
		String downloadDir = properties.getProperty("downloadDir");
		;
		File dir = new File(downloadDir);

		if (!dir.exists()) {
			dir.mkdirs();
		}

		String todayDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

		File[] matchingFiles = dir.listFiles((d, name) -> {
			if (name.toLowerCase().startsWith(baseFileName.toLowerCase()) && name.toLowerCase().endsWith(".csv")) {
				File file = new File(d, name);
				String fileModDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date(file.lastModified()));
				return todayDate.equals(fileModDate);
			}
			return false;
		});

		if (matchingFiles == null || matchingFiles.length == 0) {
			System.out.println("No report file found.");
			return new String[] { "No report file found", "Fail" };
		}

		Arrays.sort(matchingFiles, Comparator.comparingLong(File::lastModified).reversed());

		File latestFile = matchingFiles[0];

		String fileName = latestFile.getName();
		long fileSize = latestFile.length() / 1024;

		String fileHasContent = "";

		if (fileSize > 0) {
			fileHasContent = "Pass";
		} else {
			fileHasContent = "Fail";
		}

		return new String[] { fileName + " ,Filesize: " + fileSize + " kb", fileHasContent };
	}

}
