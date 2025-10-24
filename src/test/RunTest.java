package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import capci.HnetCapci;
import config.DriverSetup;
import hnet.HealthnetPayment;
import login.Login;
import molina.MolinaPayment;
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

	public RunTest(String env, String custName,String method) throws IOException {
		FileInputStream file = new FileInputStream(Main.configPath);
		properties.load(file);
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
		Url = properties.getProperty(env);
		customer = custName;
		this.method = method;
	}

	WebDriver driver = DriverSetup.getDriver();

	Login login = new Login(driver);
	ReportGeneratorContextwise report = ReportGeneratorContextwise.getInstance();
	CSVComparator csv = new CSVComparator();

	public void runHnetCAPCI() throws IOException {

		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
			
			List<String[]> practices = PaymentHelper.loadRegistryLinksFromCsv(properties.getProperty("capciPracticemap"));

			for (String[] practice : practices) {

				String practiceName = practice[0];
				String registryLink = Url + practice[1];

				HnetCapci capci = new HnetCapci(driver,customer,method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				capci.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				capci.validateCAPCI(practiceName, Url);
				login.closeAllOtherTabs();

			}
			
			
	        String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer;
	        
	        //csv.compareLastTwoCSVs(customerFolderPath,List.of("Practice"));
		} finally {
			report.saveReport(customer,method);
			driver.quit();

		}

	}
	
	public void runPaymentHTML() throws IOException {
		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> providers = PaymentHelper.loadRegistryLinksFromCsv(properties.getProperty("Hnet_PaymentHTML_providerMap"));
			

			for (String[] provider : providers) {

				String providerNPI = provider[0];
				String registryLink = Url + provider[1];

				PaymentHTML paymentHTML = new PaymentHTML(driver,customer,method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				paymentHTML.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				paymentHTML.validatePaymentHTML(providerNPI);
				login.closeAllOtherTabs();

			}
			
			
	        String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer + File.separator + "PaymentHTML";
	        
	        csv.compareLastTwoCSVs(customerFolderPath,List.of("Provider", "Quarter"));
		} finally {
			report.saveReport(customer,method);
			driver.quit();

		}
	}
	
	public void runHnet() throws IOException {
		try {

			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			List<String[]> providers = PaymentHelper.loadRegistryLinksFromCsv(properties.getProperty("hnetProviderMap"));

			for (String[] provider : providers) {

				String providerNPI = provider[0];
				String registryLink = Url + provider[1];

				HealthnetPayment hnet = new HealthnetPayment(driver,customer,method);

				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);

				hnet.switchToNewTab();

				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());

				hnet.validateHnetPayment(providerNPI);
				login.closeAllOtherTabs();

			}
			
			
	       // String customerFolderPath = properties.getProperty("backupFolderPath") + File.separator + customer + File.separator + "PaymentHTML";
	        
	      //  csv.compareLastTwoCSVs(customerFolderPath,List.of("Provider", "Quarter"));
		} finally {
			report.saveReport(customer,method);
			driver.quit();

		}
	}
	
	public void runMolina() throws IOException {
		
		try {
			
			login.LoginCozeva(Url);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));

			//List<String[]> groups = PaymentHelper.loadRegistryLinksFromCsv("assets/testdata/MolinaDataset/MolinaGroupMap.csv");
			
			List<String[]> groups = PaymentHelper.loadRegistryLinksFromCsv(properties.getProperty("molinaGroupMap"));
			
			for (String[] group : groups) {
				
				String groupName = group[0];
				String registryLink = Url + group[1];
				
				MolinaPayment molinaPayment = new MolinaPayment(driver,customer,method);
				
				((JavascriptExecutor) driver).executeScript("window.open(arguments[0])", registryLink);
				
				molinaPayment.switchToNewTab();
				
				WebElement context = wait.until(
						ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
				System.out.println(context.getText());
				
				molinaPayment.validateMolina(groupName);
				molinaPayment.closeAllOtherTabs();
			}
			
		}finally {
			report.saveReport(customer,method);
			driver.quit();

		}
	}

}
