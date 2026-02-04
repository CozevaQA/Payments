package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import capci.HnetCapci;
import capci.BSCCapci;
import config.DriverSetup;
import hnet.HealthnetPayment;
import login.Login;
import molina.MolinaPayment_2024;
import molina.MolinaPayment_2025;
import paymentHTML.PaymentHTML;
import paymentHelper.PaymentHelper;
import report.CSVComparator;
import report.ReportGeneratorContextwise;
import runner.Main;

public class RunTest {

	Properties properties = new Properties();
	public WebDriverWait wait;
	String Url;
	String customer;
	String method;
	String year;

	public RunTest(String env, String custName, String method, String year) throws IOException {
		FileInputStream file = new FileInputStream(Main.configPath);
		properties.load(file);
		// this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		this.wait = new WebDriverWait(driver, 30);
		Url = properties.getProperty(env);
		customer = custName;
		this.method = method;
		this.year = year;
	}

	WebDriver driver = DriverSetup.getDriver();
	PaymentHelper paymentHelper = new PaymentHelper(driver);

	Login login = new Login(driver);
	ReportGeneratorContextwise report = ReportGeneratorContextwise.getInstance();
	CSVComparator csv = new CSVComparator();

	public void runHnetCAPCI() throws IOException {
		String practiceName = null;

		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> practices = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("HnetCapci_Practicemap"));

			for (String[] practice : practices) {

				practiceName = practice[0];
				String registryLink = Url + practice[1];

				HnetCapci capci = new HnetCapci(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				capci.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				capci.validateCAPCI(practiceName, Url);
				login.closeAllOtherTabs();

			}

			String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer;

			csv.compareLastTwoCSVs(customerFolderPath, List.of("Practice"));
		} catch (Exception e) {
			report.logTestResult(practiceName, practiceName, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		} finally {
			report.saveReport(customer, method, year);
			driver.quit();

		}

	}

	public void runBSCCAPCI() throws IOException {
		String practiceName = null;

		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> practices = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("BSCCapci_capciPracticemap"));

			for (String[] practice : practices) {

				practiceName = practice[0];
				String registryLink = Url + practice[1];

				BSCCapci capci = new BSCCapci(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				capci.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				capci.validateCAPCI(practiceName, Url);
				login.closeAllOtherTabs();

			}

			String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer;

			csv.compareLastTwoCSVs(customerFolderPath, List.of("Practice"));
		} catch (Exception e) {
			report.logTestResult(practiceName, practiceName, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		} finally {
			report.saveReport(customer, method, year);
			driver.quit();

		}

	}

	public void runPaymentHTML() throws IOException {
		String providerNPI = null;
		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> providers = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("Hnet_PaymentHTML_providerMap"));

			for (String[] provider : providers) {

				providerNPI = provider[0];
				String registryLink = Url + provider[1];

				PaymentHTML paymentHTML = new PaymentHTML(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				paymentHTML.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				paymentHTML.validatePaymentHTML(providerNPI);
				login.closeAllOtherTabs();

			}

			String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer
					+ File.separator + "PaymentHTML";

			csv.compareLastTwoCSVs(customerFolderPath, List.of("Provider", "Quarter"));
		} catch (Exception e) {
			report.logTestResult(providerNPI, providerNPI, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		}

		finally {

			report.saveReport(customer, method, year);
			driver.quit();

		}
	}

	public void runHnet() throws IOException {
		String providerNPI = null;
		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> providers = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("hnetProviderMap"));

			for (String[] provider : providers) {

				providerNPI = provider[0];
				String registryLink = Url + provider[1];

				HealthnetPayment hnet = new HealthnetPayment(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				hnet.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				paymentHelper.clickIfXpathExists(properties.getProperty("hide_announcement"));

				hnet.validateHnetPayment(providerNPI);
				login.closeAllOtherTabs();

			}

			// String customerFolderPath = properties.getProperty("backupFolderPath") +
			// File.separator + customer + File.separator + "PaymentHTML";

			// csv.compareLastTwoCSVs(customerFolderPath,List.of("Provider", "Quarter"));
		} catch (Exception e) {
			report.logTestResult(providerNPI, providerNPI, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		} finally {
			report.saveReport(customer, method, year);
			driver.quit();

		}
	}

	public void runMolina_2025() throws IOException {
		String groupName = null;

		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> groups = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("molinaGroupMap_2025"));

			for (String[] group : groups) {

				groupName = group[0];
				String registryLink = Url + group[1];

				MolinaPayment_2025 molinaPayment = new MolinaPayment_2025(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				molinaPayment.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				paymentHelper.clickIfXpathExists(properties.getProperty("hide_announcement"));

				molinaPayment.validateMolina(groupName);
				molinaPayment.closeAllOtherTabs();
			}

			String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer;

			csv.compareLastTwoCSVs(customerFolderPath, List.of("GroupName", "LobName", "ProgramName"));

		} catch (Exception e) {
			report.logTestResult(groupName, groupName, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		} finally {
			report.saveReport(customer, method, year);
			driver.quit();

		}
	}

	public void runMolina_2024() throws IOException {
		String groupName = null;

		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			// List<String[]> groups =
			// PaymentHelper.loadRegistryLinksFromCsv("assets/testdata/MolinaDataset/MolinaGroupMap.csv");

			List<String[]> groups = PaymentHelper
					.loadRegistryLinksFromCsv(properties.getProperty("molinaGroupMap_2024"));

			for (String[] group : groups) {

				groupName = group[0];
				String registryLink = Url + group[1];

				MolinaPayment_2024 molinaPayment = new MolinaPayment_2024(driver, customer, method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				molinaPayment.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				molinaPayment.validateMolina(groupName);
				molinaPayment.closeAllOtherTabs();
			}

			String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer;

			csv.compareLastTwoCSVs(customerFolderPath, List.of("GroupName", "LobName", "ProgramName"));

		} catch (Exception e) {
			report.logTestResult(groupName, groupName, "FAIL", e.getMessage(), "", "");
			e.printStackTrace();
		} finally {
			report.saveReport(customer, method, year);
			driver.quit();

		}
	}

}
