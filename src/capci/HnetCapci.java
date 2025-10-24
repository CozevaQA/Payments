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

public class HnetCapci extends PaymentHelper {

	String customer;
	String method;
	CSVBackup csv;
	ReportGeneratorContextwise report;

	public HnetCapci(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
		report = ReportGeneratorContextwise.getInstance();
	}

	Map<String, List<String[]>> lobPatientsfromCSV = loadDataFromCsv(
			properties.getProperty("patientsMap"));

	Map<String, List<String[]>> ageFactors = loadDataFromCsv(properties.getProperty("agefactor"));

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

			// String patientDashboard = Url + patient[3];
			// ((JavascriptExecutor) driver).executeScript("window.open(arguments[0])",
			// patientDashboard);
			globalSearch(patient[2]);
			switchToNewTab();

			/*WebElement ageElement = wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("patient_age"))));
			int age = Integer
					.parseInt(ageElement.getText().replace("y", "").replace("\u00B7", "").replace("\u00A0", "").trim());*/

			
			  WebElement dobElement = wait.until(
			  ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty
			  ("patient_dob")))); String dobStr = dobElement.getText().trim();
			  
			  //calculate age as of 01-Dec-2024 
			  DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy"); 
			  LocalDate dob =LocalDate.parse(dobStr, formatter); 
			  LocalDate referenceDate =LocalDate.of(2025, 9, 2); 
			  int age = Period.between(dob,referenceDate).getYears();
			 

			WebElement genderElement = wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("patient_gender"))));
			String gender = genderElement.getText().replace("\u00B7", "").replace("\u00A0", "").trim();

			if (gender.equals("Others") || gender.equals("Unknown")) {
				gender = "Others";
			}

			double ageFactor = getAgeFactor(gender, age);

			double incentiveForPatient = getIncentiverForPatients(ageFactor, riskFactor);

			Map<String, Object> patientData = Map.of("age", age, "gender", gender, "ageFactor", ageFactor, "riskFactor",
					riskFactor, "incentiveForPatient", incentiveForPatient);
			patientDataMap.put(patient[2], patientData);

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

	public double getIncentiverForPatients(double ageFactor, double riskFactor) {
		double result = 7 + 7 * (ageFactor - 1) + 7 * (riskFactor - 1);
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
		List<String> headers = Arrays.asList("Patient Id", /* "Gender", "Age", */ "Age Factor", "Risk Factor",
				"Incentive");
		List<List<String>> patientRows = new ArrayList<>();

		for (Map.Entry<String, Map<String, Object>> entry : patientDataMap.entrySet()) {
			Map<String, Object> data = entry.getValue();

			List<String> row = Arrays.asList(entry.getKey(),
					/*
					 * String.valueOf(data.get("gender")), String.valueOf(data.get("age")),
					 */ String.valueOf(data.get("ageFactor")), String.valueOf(data.get("riskFactor")),
					String.valueOf(data.get("incentiveForPatient")));

			patientRows.add(row);
		}

		report.writeTable(practice, headers, patientRows);
	}

	public void takeDataForBackup() {
		List<String> headers = Arrays.asList("Practice", "Earned Pay", "Potential Pay");
		csv.takeBackup(headers, backupRows);
	}

}
