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
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
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
		// this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		this.wait = new WebDriverWait(driver, 30);
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

	public boolean isElementPresent(By locator) {
		return !driver.findElements(locator).isEmpty();
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
			// System.out.println("Screenshot saved to: " + destFile.getAbsolutePath());
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

	public Map<String, Map<String, Object>> getMetricIncentiveDetails(String customerName, String context,
			Map<String, List<String[]>> lobMeasuresfromCSV) {

		int coinCount = driver.findElements(By.xpath(properties.getProperty("coinContainer"))).size();

		for (int i = 0; i < coinCount; i++) {
			List<WebElement> coinContainer = driver.findElements(By.xpath(properties.getProperty("coinContainer")));
			WebElement coin = coinContainer.get(i);

			((JavascriptExecutor) driver)
					.executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", coin);

			WebElement metricElement = coin.findElement(By.xpath(properties.getProperty("metric")));
			String metricName = metricElement.getText();

			String metricAbbr = coin.findElement(By.xpath(properties.getProperty("metric_abbr"))).getText().trim()
					.replace("\u00B7", "");
			System.out.println(metricAbbr);
			int patientDenom = Integer
					.parseInt(coin.findElement(By.xpath(properties.getProperty("patientCount"))).getText().split("/")[1]
							.replace(",", "").trim());
			int patientNum = Integer
					.parseInt(coin.findElement(By.xpath(properties.getProperty("patientCount"))).getText().split("/")[0]
							.replace(",", "").trim());

			Double metricActual = Double
					.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricActualPay"))).getText()
							.replace("$", " ").replace(",", "").trim());

			Double metricPotential = Double
					.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricPotentialPay"))).getText()
							.replace("$", " ").replace(",", "").trim());

			List<WebElement> ele = coin.findElements(By.xpath(properties.getProperty("coinStack")));

			int expectedCoinStack = calculateCoinStack(metricActual, metricPotential);
			int actualCoinStack = ele.size();

			List<String[]> measuresIncentive = lobMeasuresfromCSV.get(context);
			String ifMeasureNamePresentInDataset = "No";
			double incentiveFrmDataset = 0;
			List<Double[]> performancePercentage = new ArrayList<>();

			if (measuresIncentive != null) {
				for (String[] measure : measuresIncentive) {
					if (metricAbbr.trim().equalsIgnoreCase(measure[0].trim())) {
						ifMeasureNamePresentInDataset = "Yes";
						// incentiveFrmDataset = Double.parseDouble(measure[1]);
						// break;
						double currentIncentivePoint = Double.parseDouble(measure[1]);
						double performance = Double.parseDouble(measure[2]);

						if (currentIncentivePoint > incentiveFrmDataset) {
							incentiveFrmDataset = currentIncentivePoint;
						}

						performancePercentage.add(new Double[] { currentIncentivePoint, performance });

					}

				}
			}

			WebElement coinStackBar = coin.findElement(By.xpath(properties.getProperty("fullCoinStackBar")));

			String tooltipPresent = "Yes";
			StringBuilder pair = new StringBuilder();

			if (actualCoinStack < 6 && (metricActual != metricPotential)) {
				Actions actions = new Actions(driver);
				actions.moveToElement(coinStackBar).perform();

				for (Double[] data : performancePercentage) {
					System.out.println(metricActual);
					System.out.println(data[0]);

					if (!metricActual.equals(data[0])) {
						By tooltip = By
								.xpath(String.format(properties.getProperty("tooltip"), data[0].intValue(), data[1]));
						pair.append(data[0]).append(":").append(data[1]).append(", ");

						System.out.println(pair);

						if (!isElementPresent(tooltip)) {
							System.out.println("Missing element for data: " + data[0] + ", " + data[1]);
							tooltipPresent = "No";
						}

					}
				}

			} else {
				tooltipPresent = "NA";
			}

			Map<String, Object> metricData = new HashMap<>();
			metricData.put("MetricName", metricName);
			metricData.put("MetricAbbr", metricAbbr);
			metricData.put("MetricActualPay", metricActual);
			metricData.put("MetricPotentialPay", metricPotential);
			metricData.put("ExpectedCoinStack", expectedCoinStack);
			metricData.put("actualCoinStack", actualCoinStack);
			metricData.put("denominator", patientDenom);
			metricData.put("numerator", patientNum);
			metricData.put("IsMetricPresentInDataset", ifMeasureNamePresentInDataset);
			metricData.put("MetricIncentive", incentiveFrmDataset);
			metricData.put("tooltipPresent", tooltipPresent);
			metricData.put("pair", pair.toString());

			if ("Molina".equals(customerName)) {

			}

			metricDataMap.put(metricAbbr, metricData);
		}

		return metricDataMap;
	}

	/*
	 * public Map<String, Map<String, Object>> getMetricIncentiveDetails( String
	 * customerName, String context, Map<String, List<String[]>> lobMeasuresfromCSV)
	 * {
	 * 
	 * int coinCount =
	 * driver.findElements(By.xpath(properties.getProperty("coinContainer"))).size()
	 * ;
	 * 
	 * for (int i = 0; i < coinCount; i++) { List<WebElement> coinContainer =
	 * driver.findElements(By.xpath(properties.getProperty("coinContainer")));
	 * WebElement coin = coinContainer.get(i);
	 * 
	 * ((JavascriptExecutor) driver)
	 * .executeScript("arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});"
	 * , coin);
	 * 
	 * String metricName =
	 * coin.findElement(By.xpath(properties.getProperty("metric"))).getText();
	 * String metricAbbr =
	 * coin.findElement(By.xpath(properties.getProperty("metric_abbr")))
	 * .getText().trim().replace("\u00B7", "");
	 * 
	 * int patientDenom = Integer.parseInt(
	 * coin.findElement(By.xpath(properties.getProperty("patientCount")))
	 * .getText().split("/")[1].replace(",", "").trim() );
	 * 
	 * Double metricActual = Double.parseDouble(
	 * coin.findElement(By.xpath(properties.getProperty("metricActualPay")))
	 * .getText().replace("$", " ").replace(",", "").trim() );
	 * 
	 * Double metricPotential = Double.parseDouble(
	 * coin.findElement(By.xpath(properties.getProperty("metricPotentialPay")))
	 * .getText().replace("$", " ").replace(",", "").trim() );
	 * 
	 * List<WebElement> ele =
	 * coin.findElements(By.xpath(properties.getProperty("coinStack"))); int
	 * expectedCoinStack = calculateCoinStack(metricActual, metricPotential); int
	 * actualCoinStack = ele.size();
	 * 
	 * // ─── Detect hover popup ──────────────────────────────── String
	 * hoverPopupState = "No hover popup";
	 * 
	 * if (actualCoinStack < 6 && !ele.isEmpty()) { try { Actions actions = new
	 * Actions(driver); actions.moveToElement(ele.get(ele.size() - 1)).perform();
	 * 
	 * // Wait for UI animation/tooltip render Thread.sleep(500);
	 * 
	 * hoverPopupState = "Hover popup appeared"; } catch (Exception e) {
	 * hoverPopupState = "Hover action failed"; } System.out.println("Metric: " +
	 * metricAbbr + " → " + hoverPopupState);
	 * 
	 * } // ─────────────────────────────────────────────────────────
	 * 
	 * List<String[]> measuresIncentive = lobMeasuresfromCSV.get(context); String
	 * ifMeasurePresent = "No"; double incentiveFrmDataset = 0;
	 * 
	 * if (measuresIncentive != null) { for (String[] measure : measuresIncentive) {
	 * if (metricAbbr.trim().equalsIgnoreCase(measure[0].trim())) { ifMeasurePresent
	 * = "Yes"; incentiveFrmDataset = Double.parseDouble(measure[1]); break; } } }
	 * 
	 * Map<String, Object> metricData = new HashMap<>();
	 * metricData.put("MetricName", metricName); metricData.put("MetricAbbr",
	 * metricAbbr); metricData.put("MetricActualPay", metricActual);
	 * metricData.put("MetricPotentialPay", metricPotential);
	 * metricData.put("ExpectedCoinStack", expectedCoinStack);
	 * metricData.put("actualCoinStack", actualCoinStack);
	 * metricData.put("denominator", patientDenom);
	 * metricData.put("IsMetricPresentInDataset", ifMeasurePresent);
	 * metricData.put("MetricIncentive", incentiveFrmDataset);
	 * metricData.put("HoverPopup", hoverPopupState); // store hover check
	 * 
	 * if ("Molina".equals(customerName)) { // customer-specific logic }
	 * 
	 * metricDataMap.put(metricAbbr, metricData); }
	 * 
	 * return metricDataMap; }
	 */

	/*
	 * public void measureIncentiveInDataset(Map<String, List<String[]>>
	 * lobMeasuresfromCSV, String context) { List<String[]> measuresIncentive =
	 * lobMeasuresfromCSV.get(context); String ifMeasureNamePresentInDataset = "No";
	 * double incentiveFrmDataset = 0;
	 * 
	 * for (String[] measure : measuresIncentive) { if
	 * (metricAbbr.trim().equalsIgnoreCase(measure[0].trim())) {
	 * ifMeasureNamePresentInDataset = "Yes"; incentiveFrmDataset =
	 * Double.parseDouble(measure[1]); break; }
	 * 
	 * } }
	 */

}
