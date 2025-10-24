package paymentHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.opencsv.CSVReader;
import runner.Main;

public class PaymentHelper {
	public WebDriver driver;
	public WebDriverWait wait;
	public Properties properties = new Properties();

	Map<String, Map<String, Object>> metricDataMap = new LinkedHashMap<>();

	public PaymentHelper(WebDriver driver) throws IOException {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		FileInputStream file = new FileInputStream(Main.configPath);
		properties.load(file);
	}

	public void switchToNewTab() {
		Set<String> windowHandles = driver.getWindowHandles();
		List<String> handlesList = new ArrayList<>(windowHandles);
		driver.switchTo().window(handlesList.get(handlesList.size() - 1));
	}

	public void closeCurrentTab() {
		driver.close();
	}

	public void closeAllOtherTabs() {
		Set<String> windowHandles = driver.getWindowHandles();
		List<String> handlesList = new ArrayList<>(windowHandles);

		String mainTab = handlesList.get(0);

		for (int i = 1; i < handlesList.size(); i++) {
			driver.switchTo().window(handlesList.get(i));
			driver.close();
		}
		driver.switchTo().window(mainTab);
	}

	public void takeScreenshot(String customerName) {
		String basePath = properties.getProperty("baseFolderPath");
		String todayDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
		String fileName = "screenshot_" + System.currentTimeMillis() + ".png";

		File destDir = new File(
				basePath + File.separator + todayDate + File.separator + customerName + File.separator + "snapshot");

		if (!destDir.exists()) {
			destDir.mkdirs();
		}

		File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		File destFile = new File(destDir, fileName);

		try {
			FileUtils.copyFile(srcFile, destFile);
			System.out.println("Screenshot saved to: " + destFile.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static List<String[]> loadRegistryLinksFromCsv(String filePath) {
		List<String[]> registrieLinks = new ArrayList<>();
		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			String[] nextLine;
			reader.readNext();
			while ((nextLine = reader.readNext()) != null) {
				if (nextLine.length >= 2) {
					registrieLinks.add(new String[] { nextLine[0], nextLine[1] });
				}
			}
		} catch (Exception e) {
			System.err.println("Error reading CSV file.");
			e.printStackTrace();
		}
		return registrieLinks;
	}

	public Map<String, List<String[]>> loadDataFromCsv(String filePath) {
		// Map<String, List<String[]>> dataMap = new HashMap<>();
		Map<String, List<String[]>> dataMap = new LinkedHashMap<>();

		try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
			String[] nextLine;
			reader.readNext();

			while ((nextLine = reader.readNext()) != null) {
				if (nextLine.length >= 1) {
					String key = nextLine[0].trim();
					String[] values = Arrays.copyOfRange(nextLine, 1, nextLine.length);

					dataMap.computeIfAbsent(key, k -> new ArrayList<>()).add(values);
				}
			}
		} catch (Exception e) {
			System.err.println("Error reading CSV file.");
			e.printStackTrace();
		}

		return dataMap;
	}

	public List<Object> extractPotentialPayout() {
		List<Object> payoutData = new ArrayList<>();
		double earnPay, potentialPay;
		String potentialPayout;

		try {
			WebElement earnElement = wait
					.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("earnPay"))));
			earnPay = Double.parseDouble(earnElement.getText().replace("$", "").replace(",", ""));

			WebElement potentialElement = wait.until(
					ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPay"))));
			potentialPay = Double
					.parseDouble(potentialElement.getText().replace("/", "").replace("$", "").replace(",", ""));

			potentialPayout = earnElement.getText() + potentialElement.getText();

			payoutData.add(earnPay);
			payoutData.add(potentialPay);
			payoutData.add(potentialPayout);
		} catch (Exception e) {
			payoutData.add(-1.0);
			payoutData.add(-1.0);
			payoutData.add("N/A");
		}
		return payoutData;
	}

	public void openGreenRibIfClosed() {
		WebElement greenRibToggle = wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("greenrib"))));

		WebElement regFilters = driver.findElement(By.id("reg-filters"));
		String style = regFilters.getAttribute("style");

		if (style == null || style.trim().isEmpty()) {
			greenRibToggle.click();
		}
	}

	public int calculateCoinStack(double actualPay, double potentialPay) {
		return (int) (Math.ceil((actualPay / potentialPay) * 6));
	}

	public void globalSearch(String cozevaID) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("searchbar"))))
				.sendKeys(cozevaID);
		wait.until(ExpectedConditions
				.visibilityOfElementLocated(By.xpath(properties.getProperty("searched_patientResult")))).click();
	}

	public Map<String, Map<String, Object>> getMetricIncentiveDetails(String customerName) {

		int coinCount = driver.findElements(By.xpath(properties.getProperty("coinContainer"))).size();
		
		for (int i = 0; i < coinCount; i++) {
			List<WebElement> coinContainer = driver.findElements(By.xpath(properties.getProperty("coinContainer")));
			WebElement coin = coinContainer.get(i);
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", coin);

			WebElement metricElement = coin.findElement(By.xpath(properties.getProperty("metric")));
			String metricName = metricElement.getText();
			
			String metricAbbr = coin.findElement(By.xpath(properties.getProperty("metric_abbr"))).getText()
					.trim().replace("\u00B7", "");
			
			int patientDenom = Integer
					.parseInt(coin.findElement(By.xpath(properties.getProperty("patientCount"))).getText()
							.split("/")[1].replace(",", "").trim());
			
			
			Double metricActual = Double
					.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricActualPay")))
							.getText().replace("$", " ").replace(",", "").trim());

			Double metricPotential = Double
					.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricPotentialPay")))
							.getText().replace("$", " ").replace(",", "").trim());

	        

			List<WebElement> ele = coin.findElements(By.xpath(properties.getProperty("coinStack")));

			int expectedCoinStack = calculateCoinStack(metricActual, metricPotential);
			int actualCoinStack = ele.size();

	        
	        Map<String, Object> metricData = new HashMap<>();
	        metricData.put("MetricName", metricName);
	        metricData.put("MetricAbbr", metricAbbr);
	        metricData.put("MetricActualPay", metricActual);
	        metricData.put("MetricPotentialPay", metricPotential);
	        metricData.put("ExpectedCoinStack", expectedCoinStack);
	        metricData.put("actualCoinStack", actualCoinStack);
	        metricData.put("denominator", patientDenom);

	       
	        if ("Molina".equals(customerName)) {
	            
	        }

	        metricDataMap.put(metricName, metricData);
	    }

	    return metricDataMap;
	}


}
