package capci;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.PaymentHelper;
import report.CSVBackup;
import report.ReportGeneratorContextwise;

public class BSCCapci extends PaymentHelper {

	String customer;
	String method;
	CSVBackup csv;
	ReportGeneratorContextwise report;

	public BSCCapci(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
		report = ReportGeneratorContextwise.getInstance();
	}

	Map<String, List<String[]>> lobPatientsfromCSV = loadDataFromCsv(properties.getProperty("BSCCapci_patientsMap"));

	Map<String, List<String[]>> ageFactors = loadDataFromCsv(properties.getProperty("BSCCapci_agefactor"));

	Map<String, Map<String, Object>> practiceDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> patientDataMap = new LinkedHashMap<>();
	List<List<String>> backupRows = new ArrayList<>();

	public void validateCAPCI(String practice, String Url) {
		List<Object> payoutData = extractPotentialPayout();

		Map<String, Object> paymentData = new HashMap<>();
		paymentData.put("EarnPay", payoutData.get(0));
		paymentData.put("PotentialPay", payoutData.get(1));
		paymentData.put("PotentialPayout", payoutData.get(2));

		practiceDataMap.put(practice, paymentData);

		List<String> backupRow = Arrays.asList(practice, String.valueOf(paymentData.get("EarnPay")),
				String.valueOf(paymentData.get("PotentialPay")));
		backupRows.add(backupRow);

		takeScreenshot(customer);

		List<String[]> patients = lobPatientsfromCSV.get(practice.trim());

		for (String[] patient : patients) {

			double riskFactor = Double.parseDouble(patient[1]);
			double basePayment = Double.parseDouble(patient[3]);
			System.out.println("Cozeva Id: " + patient[2]);
			System.out.println("Risk Factor: " + patient[1]);
			System.out.println("Base Payment: " + patient[3]);

			globalSearch(patient[2]);
			switchToNewTab();

			WebElement dobElement = wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("patient_dob"))));
			String dobStr = dobElement.getText().trim();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
			LocalDate dob = LocalDate.parse(dobStr, formatter);
			System.out.println(
					"Month: " + dob.getMonthValue() + ", day: " + dob.getDayOfMonth() + ", year: " + dob.getYear());

			WebElement genderElement = wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("patient_gender"))));
			String gender = genderElement.getText().replace("\u00B7", "").replace("\u00A0", "").trim();

			if (gender.equals("Others") || gender.equals("Unknown")) {
				gender = "Others";
			}

			double totalIncentive = 0.0;

			List<Double> ageFactors = new ArrayList<>();
			List<String> months = new ArrayList<>();
			List<Double> monthlyIncentives = new ArrayList<>();

			String refDatesStr = properties.getProperty("BSCCapci_referenceDate");
			String[] refDates = refDatesStr.split(",");

			for (String refDate : refDates) {
				LocalDate referenceDate = LocalDate.parse(refDate.trim(), formatter);
				int age = Period.between(dob, referenceDate).getYears();
				double ageFactor = getAgeFactor(gender, age);
				double incentive = getIncentiverForPatients(ageFactor, riskFactor, basePayment);

				System.out.println("Ref Month: " + referenceDate.getMonth() + ", Age: " + age + ", AgeFactor: "
						+ ageFactor + ", Incentive: " + incentive);
				ageFactors.add(ageFactor);
				months.add(referenceDate.getMonth().toString());
				monthlyIncentives.add(incentive);

				totalIncentive += incentive;

			}

			double incentiveForPatient = Math.round(totalIncentive * 100.0) / 100.0;

			Map<String, Object> patientData = new HashMap<>();
			patientData.put("riskFactor", riskFactor);
			patientData.put("ageFactors", ageFactors);
			patientData.put("months", months);
			patientData.put("monthlyIncentives", monthlyIncentives);
			patientData.put("incentiveForPatient", incentiveForPatient);

			patientDataMap.put(patient[2], patientData);

			driver.close();

			switchToNewTab();

		}

		comapareIncentive(practice);
		writePatientTableToReport(practice);
		takeDataForBackup();
	}

	public double getAgeFactor(String gender, int age) {
		List<String[]> ranges = ageFactors.get(gender);
		if (ranges == null)
			return -1.0;

		for (String[] row : ranges) {
			int lower = Integer.parseInt(row[0]);
			int upper = Integer.parseInt(row[1]);
			double factor = Double.parseDouble(row[2]);

			if (age >= lower && age < upper) {
				return factor;
			}
		}
		return -1.0;
	}

	public double getTotalAgeFactor(String gender, LocalDate dob) {

		String refDatesStr = properties.getProperty("referenceDate");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

		String[] refDates = refDatesStr.split(",");

		double totalAgeFactor = 0.0;

		for (String refDate : refDates) {
			LocalDate referenceDate = LocalDate.parse(refDate.trim(), formatter);
			int age = Period.between(dob, referenceDate).getYears();
			double ageFactor = getAgeFactor(gender, age);

			System.out
					.println("Ref Month: " + referenceDate.getMonth() + ", Age: " + age + ", AgeFactor: " + ageFactor);

			totalAgeFactor += ageFactor;
		}

		return Math.round(totalAgeFactor * 100.0) / 100.0;
	}

	public double getIncentiverForPatients(double ageFactor, double riskFactor, double basePayment) {
		double result = basePayment + basePayment * (ageFactor - 1) + basePayment * (riskFactor - 1);
		return Math.round(result * 100.0) / 100.0;
	}

	public void comapareIncentive(String practice) {
		double totalIncentive = 0;
		for (Map.Entry<String, Map<String, Object>> entry : patientDataMap.entrySet()) {
			Map<String, Object> patientInfo = entry.getValue();

			totalIncentive = totalIncentive + (double) patientInfo.get("incentiveForPatient");
			System.out.println(patientInfo.get("incentiveForPatient"));

		}

		double roundedIncentive = Math.round(totalIncentive * 100.0) / 100.0;
		// double roundedIncentive = Math.round(totalIncentive * 10.0) / 10.0;

		String earnedAndPotentialMatch = (double) practiceDataMap.get(practice)
				.get("EarnPay") == (double) practiceDataMap.get(practice).get("PotentialPay") ? "Pass" : "Fail";
		String patientLevelPayMatch = (double) practiceDataMap.get(practice).get("EarnPay") == roundedIncentive ? "Pass"
				: "Fail";

		report.logTestResult(practice, "Earned = Potential in registry", earnedAndPotentialMatch,
				"Earn Pay:" + (double) practiceDataMap.get(practice).get("EarnPay") + " Potential Pay: "
						+ (double) practiceDataMap.get(practice).get("PotentialPay"));
		report.logTestResult(practice, "Sum of patient level incentive = Pay in registry", patientLevelPayMatch,
				"Pay in registry:" + (double) practiceDataMap.get(practice).get("PotentialPay")
						+ " Total sum for patient level: " + roundedIncentive);

	}

	public void writePatientTableToReport(String practice) {

	    List<String> headers = Arrays.asList(
	            "Cozeva Id",
	            "Month",
	            "Age Factor",
	            "Risk Factor",
	            "Incentive");

	    List<List<String>> patientRows = new ArrayList<>();

	    for (Map.Entry<String, Map<String, Object>> entry : patientDataMap.entrySet()) {

	        String cozevaId = entry.getKey();
	        Map<String, Object> data = entry.getValue();

	        List<Double> ageFactors = (List<Double>) data.get("ageFactors");
	        List<String> months = (List<String>) data.get("months");
	        List<Double> monthlyIncentives = (List<Double>) data.get("monthlyIncentives");

	        String risk = String.valueOf(data.get("riskFactor"));

	        // 👉 one row per reference month
	        for (int i = 0; i < ageFactors.size(); i++) {

	            List<String> row = new ArrayList<>();

	            row.add(cozevaId);
	            row.add(months.get(i));
	            row.add(String.valueOf(ageFactors.get(i)));
	            row.add(risk);
	            row.add(String.valueOf(monthlyIncentives.get(i))); // month-only incentive

	            patientRows.add(row);
	        }
	    }

	    report.writeTable(practice, headers, patientRows);
	}


	public void takeDataForBackup() {
		List<String> headers = Arrays.asList("Practice", "Earned Pay", "Potential Pay");
		csv.takeBackup(headers, backupRows);
	}

}
