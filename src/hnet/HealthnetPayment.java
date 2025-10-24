package hnet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.PaymentHelper;
import paymentHelper.*;
import report.CSVBackup;
import report.ReportGeneratorContextwise;

public class HealthnetPayment extends PaymentHelper {

	ReportGeneratorContextwise report;
	CSVBackup csv;
	String customer;
	String method;

	public HealthnetPayment(WebDriver driver, String custName, String method) throws IOException {
		super(driver);
		report = ReportGeneratorContextwise.getInstance();
		report.setExtraColumns(List.of("Lob", "Payer"));
		this.customer = custName;
		this.method = method;
		this.csv = new CSVBackup(customer, method);
	}

	Map<String, List<Map<String, Object>>> lobDataMap = new LinkedHashMap<>();
	Map<String, Map<String, Object>> metricDataMap = new HashMap<>();
	Map<String, Map<String, Object>> patientDataMap = new HashMap<>();

	List<String[]> deferredMetricPrintLogs = new ArrayList<>();
	List<String[]> deferredMetricLogs = new ArrayList<>();
	List<String[]> deferredPatientLogs = new ArrayList<>();
	List<String[]> deferredPatientHeaderLogs = new ArrayList<>();

	Map<String, List<String[]>> lobMeasuresfromCSV = loadDataFromCsv(properties.getProperty("hnet_Incentive"));

	public void validateHnetPayment(String providerNPI) throws IOException {

		WebElement contextElement = wait
				.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("context"))));
		String context = contextElement.getText();

		openGreenRibIfClosed();
		int lobCount = driver.findElements(By.xpath(properties.getProperty("lobElements"))).size();

		for (int i = 0; i < lobCount; i++) {
			openGreenRibIfClosed();
			List<WebElement> lobList = driver.findElements(By.xpath(properties.getProperty("lobElements")));
			WebElement lob = lobList.get(i);
			lob = wait.until(ExpectedConditions.visibilityOf(lob));
			String lobName = lob.getText();
			System.out.println(lobName);
			lob.click();

			List<Map<String, Object>> payerDataList = new ArrayList<>();
			int payerCount = driver.findElements(By.xpath(properties.getProperty("payer"))).size();

			for (int j = 0; j < payerCount; j++) {
				openGreenRibIfClosed();

				List<WebElement> payerList = driver.findElements(By.xpath(properties.getProperty("payer")));
				WebElement payer = payerList.get(j);
				payer = wait.until(ExpectedConditions.visibilityOf(payer));
				String payerName = payer.getText();
				payer.click();
				System.out.println(payerName);

				driver.findElement(By.xpath(properties.getProperty("apply"))).click();
				
				try {
					Thread.sleep(7000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				double earnPay, potentialPay;
				String potentialPayout;

				try {
					WebElement earnElement = wait.until(
							ExpectedConditions.visibilityOfElementLocated(By.xpath(properties.getProperty("earnPay"))));
					earnPay = Double.parseDouble(earnElement.getText().replace("$", "").replace(",", ""));

					WebElement potentialElement = wait.until(ExpectedConditions
							.visibilityOfElementLocated(By.xpath(properties.getProperty("potentialPay"))));
					potentialPay = Double
							.parseDouble(potentialElement.getText().replace("/", "").replace("$", "").replace(",", ""));

					potentialPayout = earnElement.getText() + potentialElement.getText();
				} catch (Exception e) {
					potentialPayout = "N/A";
					earnPay = -1.0;
					potentialPay = -1.0;
				}

				System.out.println(potentialPayout);

				Map<String, Object> payerData = new HashMap<>();
				payerData.put("Payer", payerName);
				payerData.put("PotentialPayout", potentialPayout);
				payerData.put("ActualPay", earnPay);
				payerData.put("PotentialPay", potentialPay);

				takeScreenshot(customer);

				/*
				 * try { String SCROLLBAR_CLASS =
				 * "card-content dt_scroll_elem quality_profile row no_top_padding";
				 * 
				 * WebElement container =
				 * FullPageScreenshotBothAxes.findScrollableContainerByScrollbarClass(driver,
				 * SCROLLBAR_CLASS); if (container == null) { throw new
				 * IllegalStateException("Could not locate scrollable container from scrollbar class: "
				 * + SCROLLBAR_CLASS); }
				 * 
				 * FullPageScreenshotBothAxes.takeScrollableElementScreenshot(driver, container,
				 * "C:\\HNET_Migration_SS\\Registry\\"+providerNPI.trim()
				 * +"_"+lobName.trim()+"_"+payerName.trim()+".png", 100, // vertical overlap in
				 * CSS px 60, //horizontal overlap in CSS px 300, // wait ms after each scroll
				 * 30000 //initial max wait for page ready ); } catch(Exception e) {
				 * System.out.println(e); }
				 */

				payerDataList.add(payerData);
				lobDataMap.put(lobName, payerDataList);

				if (!potentialPayout.equals("N/A")) {
					int coinCount = driver.findElements(By.xpath(properties.getProperty("coinContainer"))).size();
					for (int k = 0; k < coinCount; k++) {
						List<WebElement> coinContainer = driver.findElements(By.xpath(properties.getProperty("coinContainer")));
						// System.out.println(coinContainer.size());
						WebElement coin = coinContainer.get(k);
						((JavascriptExecutor) driver).executeScript(
								"arguments[0].scrollIntoView({behavior: 'instant', block: 'center'});", coin);

						WebElement metricElement = coin.findElement(By.xpath(properties.getProperty("metric")));

						String metricName = metricElement.getText();

						String metricAbbr = coin.findElement(By.xpath(properties.getProperty("metric_abbr"))).getText()
								.trim().replace("\u00B7", "");

						// System.out.println(metricAbbr);

						Double metricActual = Double
								.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricActualPay")))
										.getText().replace("$", " ").replace(",", "").trim());

						// System.out.println(metricActual);
						Double metricPotential = Double
								.parseDouble(coin.findElement(By.xpath(properties.getProperty("metricPotentialPay")))
										.getText().replace("$", " ").replace(",", "").trim());

						// System.out.println(metricPotential);
						int patientdenom = Integer
								.parseInt(coin.findElement(By.xpath(properties.getProperty("patientCount"))).getText()
										.split("/")[1].replace(",", "").trim());

						List<WebElement> ele = coin.findElements(By.xpath(properties.getProperty("coinStack")));

						int expectedCoinStack = calculateCoinStack(metricActual, metricPotential);
						int actualCoinStack = ele.size();

						List<String[]> measuresIncentive = lobMeasuresfromCSV.get(lobName);
						String ifMeasureNamePresentInDataset = "No";
						double incentiveFrmDataset = 0;

						/*
						 * if (lobName.equals("All Patients")) { ifMeasureNamePresentInDataset = "-1";
						 * incentiveFrmDataset = -1.0; }
						 */
						if (lobName.equals("ALL")) {
							ifMeasureNamePresentInDataset = "-1";
							incentiveFrmDataset = -1.0;
						} else {
							for (String[] measure : measuresIncentive) {
								if (metricAbbr.trim().equalsIgnoreCase(measure[0].trim())) {
									ifMeasureNamePresentInDataset = "Yes";
									incentiveFrmDataset = Double.parseDouble(measure[1]);
									break;
								}

							}
						}

						Map<String, Object> metricData = Map.of("MetricName", metricName, "IsMetricPresentInDataset",
								ifMeasureNamePresentInDataset, "MetricIncentive", incentiveFrmDataset, "ActualPay",
								metricActual, "PotentialPay", metricPotential, "ExpectedCoinStack", expectedCoinStack,
								"actualCoinStack", actualCoinStack, "denominator", patientdenom);

						metricDataMap.put(metricName, metricData);

						if (!lobName.equals("ALL") && !payerName.equals("ALL")) {
							if (patientdenom <= 10) {
								if (metricDataMap.get(metricName).get("IsMetricPresentInDataset").equals("Yes")) {
									((JavascriptExecutor) driver).executeScript(
											"arguments[0].scrollIntoView(true); arguments[0].click();", metricElement);
									switchToNewTab();
									wait.until(ExpectedConditions.invisibilityOfElementLocated(
											By.xpath(properties.getProperty("ajax_preloader"))));
									wait.until(ExpectedConditions
											.elementToBeClickable(By.xpath(properties.getProperty("closeFilter"))))
											.click();

									try {
										Thread.sleep(5000);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}

									List<WebElement> patientlist = wait
											.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(
													By.xpath(properties.getProperty("msplPatient"))));

									for (WebElement patient : patientlist) {
										wait.until(ExpectedConditions.invisibilityOfElementLocated(
												By.xpath(properties.getProperty("ajax_preloader"))));

										patient.click();
										switchToNewTab();

										List<WebElement> incentives = new ArrayList<>();
										incentives.clear();
										try {

											WebElement metricInPatientDashboard = wait
													.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(String
															.format(properties.getProperty("metric_PatientDashboard1"),
																	metricName))));

											// System.out.println(metricInPatientDashboard.getText());

											((JavascriptExecutor) driver).executeScript(
													"arguments[0].scrollIntoView(true);", metricInPatientDashboard);

											incentives = metricInPatientDashboard.findElements(
													(By.xpath(properties.getProperty("incentive_PatientDashboard"))));

										} catch (Exception e) {
											incentives = new ArrayList<>();
										}
										if (incentives.size() > 0) {
											String patientCozevaID = driver
													.findElement(By.xpath(properties.getProperty("cozevaID")))
													.getText();
											WebElement incentiveElement = incentives.get(0);
											String dataAjaxData = incentiveElement.getDomAttribute("data-ajax-data")
													.replace("&quot;", "\"");
											JSONObject jsonData = new JSONObject(dataAjaxData);
											double actualPayInPatientDashboard = Double.parseDouble(jsonData
													.getString("pay_amount").replace("$", "").replace(",", "").trim());
											double potentialPayInPatientDashboard = Double
													.parseDouble(jsonData.getString("potential_pay").replace("$", "")
															.replace(",", "").trim());
											// System.out.println(actualPayInPatientDashboard);
											// System.out.println(potentialPayInPatientDashboard);

											double totalActualPay = 0.0;
											double totalPotentialPay = 0.0;

											double earnedIncentiveInPatientHeader = Double.parseDouble(driver
													.findElement(By.xpath(
															properties.getProperty("earnedIncentive_patientHeader")))
													.getText());
											double potentialIncentiveInPatientHeader = Double.parseDouble(driver
													.findElement(By.xpath(
															properties.getProperty("potentialIncentive_patientHeader")))
													.getText().replace("$", "").replace("/", ""));
											List<WebElement> allIncentives = driver.findElements(
													By.xpath("//span[contains(@class, 'incentive pts')]"));

											for (WebElement incentive : allIncentives) {
												String dataAjax = incentive.getDomAttribute("data-ajax-data");

												if (dataAjax != null && !dataAjax.isEmpty()) {

													dataAjax = dataAjax.replace("&quot;", "\"");

													JSONObject json = new JSONObject(dataAjax);

													double allactualPay = Double
															.parseDouble(json.getString("pay_amount").replace("$", "")
																	.replace(",", "").trim());
													double allpotentialPay = Double
															.parseDouble(json.getString("potential_pay")
																	.replace("$", "").replace(",", "").trim());

													totalActualPay += allactualPay;
													totalPotentialPay += allpotentialPay;

												}
											}

											Map<String, Object> patientData = Map.of("MetricName", patientCozevaID,
													"ActualPayInPatientDashboard", actualPayInPatientDashboard,
													"PotentialPayInPatientDashboard", potentialPayInPatientDashboard,
													"TotalActualPay", totalActualPay, "TotalPotentialPay",
													totalPotentialPay, "EarnedIncentiveInPatientHeader",
													earnedIncentiveInPatientHeader, "PotentialIncentiveInPatientHeader",
													potentialIncentiveInPatientHeader);

											patientDataMap.put(patientCozevaID, patientData);

											matchPaymentWithPatientHeader(context, patientCozevaID, lobName, payerName);
										}
										driver.close();

										switchToNewTab();
									}

									matchPaymentsPatientWise(context, lobName, payerName, metricName);
									patientDataMap.clear();
									driver.findElement(By.xpath(properties.getProperty("performanceMeasures"))).click();
								}
							}
						}
					}
					matchPaymentsMetricWise(context, lobName, payerName);
					showPaymentsMetricWise(context, lobName, payerName);

					metricDataMap.clear();
				}
			}
		}
		matchPaymentsLOBWise(context);
		report.logTestResult(context, "", "", "", "", "");
		matchPaymentsPayerWise(context);
		report.logTestResult(context, "", "", "", "", "");
		for (String[] log : deferredMetricPrintLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(context, "", "", "", "", "");
		for (String[] log : deferredMetricLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(context, "", "", "", "", "");

		for (String[] log : deferredPatientLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(context, "", "", "", "", "");
		for (String[] log : deferredPatientHeaderLogs) {
			report.logTestResult(log[0], log[1], log[2], log[3], log[4], log[5]);
		}
		report.logTestResult(context, "", "", "", "", "");

	}

	public void matchPaymentsLOBWise(String provider) throws IOException {
		double otherLobTotalActualPay = 0, otherLobTotalPotentialPay = 0;

		for (Map.Entry<String, List<Map<String, Object>>> entry : lobDataMap.entrySet()) {
			String lobName = entry.getKey();
			List<Map<String, Object>> data = entry.getValue();

			if (!lobName.equals("ALL")) {
				if ((double) data.get(0).get("ActualPay") != -1) {
					otherLobTotalActualPay += (double) data.get(0).get("ActualPay");
				}
				if ((double) data.get(0).get("PotentialPay") != -1) {
					otherLobTotalPotentialPay += (double) data.get(0).get("PotentialPay");
				}
			}
		}

		String actualMatch = ((double) lobDataMap.get("ALL").get(0).get("ActualPay") == otherLobTotalActualPay) ? "Pass"
				: "Fail";
		String potentialMatch = ((double) lobDataMap.get("ALL").get(0).get("PotentialPay") == otherLobTotalPotentialPay)
				? "Pass"
				: "Fail";

		report.logTestResult(
				provider, "Lob Comparison - Earned Pay", actualMatch, "ALL: "
						+ (lobDataMap.containsKey("ALL")
								? (((double) lobDataMap.get("ALL").get(0).get("ActualPay")) == -1.0 ? "NA"
										: lobDataMap.get("ALL").get(0).get("ActualPay"))
								: "")
						+ " HEDIS Commercial: "
						+ (lobDataMap.containsKey("HEDIS Commercial")
								? (((double) lobDataMap.get("HEDIS Commercial").get(0).get("ActualPay")) == -1.0 ? "NA"
										: lobDataMap.get("HEDIS Commercial").get(0).get("ActualPay"))
								: "")
						+ " Medi-Cal: "
						+ (lobDataMap.containsKey("Medi-Cal")
								? (((double) lobDataMap.get("Medi-Cal").get(0).get("ActualPay")) == -1.0 ? "NA"
										: lobDataMap.get("Medi-Cal").get(0).get("ActualPay"))
								: "")
						+ " Medicare: "
						+ (lobDataMap.containsKey("Medicare")
								? (((double) lobDataMap.get("Medicare").get(0).get("ActualPay")) == -1.0 ? "NA"
										: lobDataMap.get("Medicare").get(0).get("ActualPay"))
								: ""),
				"", "");

		report.logTestResult(provider, "Lob Comparison - Potential Pay", potentialMatch, "ALL: "
				+ (lobDataMap.containsKey("ALL")
						? (((double) lobDataMap.get("ALL").get(0).get("PotentialPay")) == -1.0 ? "NA"
								: lobDataMap.get("ALL").get(0).get("PotentialPay"))
						: "")
				+ " HEDIS Commercial: "
				+ (lobDataMap.containsKey("HEDIS Commercial")
						? (((double) lobDataMap.get("HEDIS Commercial").get(0).get("PotentialPay")) == -1.0 ? "NA"
								: lobDataMap.get("HEDIS Commercial").get(0).get("PotentialPay"))
						: "")
				+ " Medi-Cal: "
				+ (lobDataMap.containsKey("Medi-Cal")
						? (((double) lobDataMap.get("Medi-Cal").get(0).get("PotentialPay")) == -1.0 ? "NA"
								: lobDataMap.get("Medi-Cal").get(0).get("PotentialPay"))
						: "")
				+ " Medicare: "
				+ (lobDataMap.containsKey("Medicare")
						? (((double) lobDataMap.get("Medicare").get(0).get("PotentialPay")) == -1.0 ? "NA"
								: lobDataMap.get("Medicare").get(0).get("PotentialPay"))
						: ""),
				"", "");

	/*	report.logTestResult(provider, "Lob Comparison - Earned Pay", actualMatch,
				"ALL: " + (lobDataMap.containsKey("ALL") ? lobDataMap.get("ALL").get(0).get("ActualPay") : "")
						+ " HEDIS Commercial: "
						+ (lobDataMap.containsKey("HEDIS Commercial")
								? lobDataMap.get("HEDIS Commercial").get(0).get("ActualPay")
								: "")
						+ " Medi-Cal: "
						+ (lobDataMap.containsKey("Medi-Cal") ? lobDataMap.get("Medi-Cal").get(0).get("ActualPay") : "")
						+ " Medicare: "
						+ (lobDataMap.containsKey("Medicare") ? lobDataMap.get("Medicare").get(0).get("ActualPay")
								: ""),
				"", "");

		report.logTestResult(provider, "Lob Comparison - Potential Pay", potentialMatch,
				"ALL: " + (lobDataMap.containsKey("ALL") ? lobDataMap.get("ALL").get(0).get("PotentialPay") : "") + ", "
						+ " HEDIS Commercial: "
						+ (lobDataMap.containsKey("HEDIS Commercial")
								? lobDataMap.get("HEDIS Commercial").get(0).get("PotentialPay")
								: "")
						+ ", " + " Medi-Cal: "
						+ (lobDataMap.containsKey("Medi-Cal")
								? lobDataMap.get("Medi-Cal").get(0).get("PotentialPay")
								: "")
						+ ", " + " Medicare: "
						+ (lobDataMap.containsKey("Medicare") ? lobDataMap.get("Medicare").get(0).get("PotentialPay")
								: ""),
				"", "");*/

	}

	public void matchPaymentsPayerWise(String provider) throws IOException {

		StringBuilder earnedDetails = new StringBuilder();
		StringBuilder potentialDetails = new StringBuilder();
		StringBuilder zeroPotentialPayoutDetails = new StringBuilder();

		for (Map.Entry<String, List<Map<String, Object>>> entry : lobDataMap.entrySet()) {
			double otherPayerTotalActualPay = 0, otherPayerTotalPotentialPay = 0;
			String lobName = entry.getKey();

			List<Map<String, Object>> data = entry.getValue();

			for (int i = 0; i < data.size(); i++) {
				if (((String) data.get(i).get("PotentialPayout")).equals("$0.00/$0.00")) {
					zeroPotentialPayoutDetails.append(data.get(i).get("Payer")).append(": ")
							.append(data.get(i).get("PotentialPay")).append(", ");
				}
			}

			for (int i = 1; i < data.size(); i++) {
				Map<String, Object> payerdata = data.get(i);
				String payerName = (String) payerdata.get("Payer");

				if ((double) data.get(i).get("ActualPay") != -1) {
					otherPayerTotalActualPay += (double) data.get(i).get("ActualPay");
				}
				if ((double) data.get(i).get("PotentialPay") != -1) {
					otherPayerTotalPotentialPay += (double) data.get(i).get("PotentialPay");
				}

				earnedDetails.append(data.get(i).get("Payer")).append(": ")
						.append((double) data.get(i).get("ActualPay") == -1 ? "NA" : data.get(i).get("ActualPay"))
						.append(", ");
				potentialDetails.append(data.get(i).get("Payer")).append(": ")
						.append((double) data.get(i).get("PotentialPay") == -1 ? "NA" : data.get(i).get("PotentialPay"))
						.append(", ");

			}

			String actualMatch = ((double) lobDataMap.get(lobName).get(0).get("ActualPay") == otherPayerTotalActualPay)
					? "Pass"
					: "Fail";
			String potentialMatch = ((double) lobDataMap.get(lobName).get(0)
					.get("PotentialPay") == otherPayerTotalPotentialPay) ? "Pass" : "Fail";

			if (lobDataMap.get(lobName).get(0).get("PotentialPayout").equals("N/A") && otherPayerTotalActualPay == 0
					&& otherPayerTotalPotentialPay == 0) {
				actualMatch = "Pass";
				potentialMatch = "Pass";
			}
			report.logTestResult(provider, "Payer Comparison - Earned Pay", actualMatch,
					lobDataMap.get(lobName).get(0).get("Payer") + " : "
							+ ((double) data.get(0).get("ActualPay") == -1 ? "NA" : data.get(0).get("ActualPay")) + ","
							+ earnedDetails.toString(),
					lobName, "");

			report.logTestResult(provider, "Payer Comparison - Potential Pay", potentialMatch,
					lobDataMap.get(lobName).get(0).get("Payer") + " : "
							+ ((double) data.get(0).get("PotentialPay") == -1 ? "NA" : data.get(0).get("PotentialPay"))
							+ "," + potentialDetails.toString(),
					lobName, "");

			if (zeroPotentialPayoutDetails.isEmpty()) {
				report.logTestResult(provider, "Incentive!= 0/0", "Pass", "", lobName, "");
			} else {
				report.logTestResult(provider, "Incentive!= 0/0", "Fail", zeroPotentialPayoutDetails.toString(),
						lobName, "");
			}

			earnedDetails.setLength(0);
			potentialDetails.setLength(0);

		}

	}

	public void showPaymentsMetricWise(String provider, String lob, String payer) throws IOException {
		for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {
			String metricName = entry.getKey();
			Map<String, Object> data = entry.getValue();

			double actualPay = (double) data.get("ActualPay");
			double potentialPay = (double) data.get("PotentialPay");

			deferredMetricPrintLogs.add(new String[] { provider,

					metricName, "", "  Actual Pay: " + actualPay + " | Potential Pay: " + potentialPay, lob, payer });
		}
	}

	public void matchPaymentsMetricWise(String provider, String lob, String payer) throws IOException {
		double totalMetricActualPay = 0, totalMetricPotentialPay = 0;
		String actualMatch = "";
		String potentialMatch = "";
		List<String> metricNames = new ArrayList<>();
		List<String> failedDenomMetrics = new ArrayList<>();
		List<String> failedNotPresentInDataset = new ArrayList<>();

		for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet()) {
			Map<String, Object> data = entry.getValue();
			totalMetricActualPay += (double) data.get("ActualPay");
			totalMetricPotentialPay += (double) data.get("PotentialPay");
			metricNames.add(entry.getKey());
			String isPresent = (String) data.get("IsMetricPresentInDataset");
			if (!"ALL".equalsIgnoreCase(lob)) {
				if (!"Yes".equalsIgnoreCase(isPresent)) {
					failedNotPresentInDataset.add(entry.getKey());
				}
			}
			if ((int) data.get("denominator") == 0) {
				failedDenomMetrics.add(entry.getKey());
			}
		}

		for (Map<String, Object> data : lobDataMap.get(lob)) {
			if (data.get("Payer").equals(payer)) {
				actualMatch = ((double) data.get("ActualPay") == totalMetricActualPay) ? "Pass" : "Fail";
				potentialMatch = ((double) data.get("PotentialPay") == totalMetricPotentialPay) ? "Pass" : "Fail";

				deferredMetricLogs.add(new String[] { provider, "Metric Comparison - Earned Pay", actualMatch,
						payer + ": " + data.get("ActualPay") + " ,Sum of Metrics: " + totalMetricActualPay
								+ " , Metric Names: " + String.join(", ", metricNames),
						lob, payer });

				deferredMetricLogs.add(new String[] { provider, "Metric Comparison - Potential Pay", potentialMatch,
						payer + ": " + data.get("PotentialPay") + " ,Sum of Metrics: " + totalMetricPotentialPay
								+ " , Metric Names: " + String.join(", ", metricNames),
						lob, payer });

				if (!"ALL".equalsIgnoreCase(lob)) {
					deferredMetricLogs.add(new String[] { provider, "Metric present in Dataset",
							failedNotPresentInDataset.isEmpty() ? "Pass" : "Fail",
							failedNotPresentInDataset.isEmpty() ? "All metrics present in dataset"
									: String.join(", ", failedNotPresentInDataset),
							lob, payer });
				}

				deferredMetricLogs.add(new String[] { provider, "Metric Denominator != 0",
						failedDenomMetrics.isEmpty() ? "Pass" : "Fail",
						failedDenomMetrics.isEmpty() ? "No metrics with zero denominator"
								: String.join(", ", failedDenomMetrics),
						lob, payer });

				break;
			}

		}

	}

	public void matchPaymentWithPatientHeader(String provider, String cozevaID, String lob, String payer) {
		String potentialPayoutMatch = patientDataMap.get(cozevaID).get("EarnedIncentiveInPatientHeader")
				.equals(patientDataMap.get(cozevaID).get("TotalActualPay"))
				&& patientDataMap.get(cozevaID).get("PotentialIncentiveInPatientHeader")
						.equals(patientDataMap.get(cozevaID).get("TotalPotentialPay")) ? "Pass" : "Fail";

		deferredPatientHeaderLogs.add(new String[] { provider, cozevaID, potentialPayoutMatch,
				"Actual Earned Pay in Patient Header: "
						+ patientDataMap.get(cozevaID).get("EarnedIncentiveInPatientHeader")
						+ " , Sum of Earned Pay in Patient Dashboard: "
						+ patientDataMap.get(cozevaID).get("TotalActualPay")
						+ " | Actual Potential Pay in Patient Header: "
						+ patientDataMap.get(cozevaID).get("PotentialIncentiveInPatientHeader")
						+ " , Sum of Potential Pay in Patient Dashboard: "
						+ patientDataMap.get(cozevaID).get("TotalPotentialPay"),
				lob, payer

		});

	}

	public void matchPaymentsPatientWise(String provider, String lob, String payer, String metricName)
			throws IOException {
		double totalActualPayInPatient = 0, totalPotentialPayInPatient = 0;
		List<String> complientpatients = new ArrayList<>();
		List<String> incentiveMismatchedpatients = new ArrayList<>();
		String incentiveMatchesWithDataSet = " ";

		if (!patientDataMap.isEmpty()) {
			incentiveMatchesWithDataSet = "Yes";

			for (Map.Entry<String, Map<String, Object>> entry : patientDataMap.entrySet()) {
				Map<String, Object> data = entry.getValue();

				double actualPay = (double) data.get("ActualPayInPatientDashboard");
				double potentialPay = (double) data.get("PotentialPayInPatientDashboard");

				totalActualPayInPatient += actualPay;
				totalPotentialPayInPatient += potentialPay;

				if (actualPay == (double) metricDataMap.get(metricName).get("MetricIncentive")) {
					complientpatients.add(entry.getKey());
				}

				if (potentialPay != (double) metricDataMap.get(metricName).get("MetricIncentive")) {
					incentiveMatchesWithDataSet = "No";
					incentiveMismatchedpatients.add(entry.getKey());
				}
			}
		}

		String potentialPayoutMatch = ((double) metricDataMap.get(metricName)
				.get("ActualPay") == totalActualPayInPatient)
				&& ((double) metricDataMap.get(metricName).get("PotentialPay") == totalPotentialPayInPatient)
				&& (incentiveMatchesWithDataSet.equals("Yes")) ? "Pass" : "Fail";

		int expectedCoinStack = (int) metricDataMap.get(metricName).get("ExpectedCoinStack");
		int actualCoinStack = (int) metricDataMap.get(metricName).get("actualCoinStack");

		String coinStackMatch = (expectedCoinStack == actualCoinStack) ? "Pass" : "Fail";

		deferredPatientLogs.add(new String[] { provider, metricName, potentialPayoutMatch,
				"Metric Earned pay in Registry: " + (double) metricDataMap.get(metricName).get("ActualPay")
						+ " , Sum of Earned Pay from Patients: " + totalActualPayInPatient
						+ " | Metric Potential pay in Registry: "
						+ (double) metricDataMap.get(metricName).get("PotentialPay")
						+ " , Sum of Potential Pay from Patients: " + totalPotentialPayInPatient
						+ " | Incentive Match with dataset: " + incentiveMatchesWithDataSet + " , Metric Incentive: "
						+ (double) metricDataMap.get(metricName).get("MetricIncentive"),
				lob, payer

		});

		deferredPatientLogs.add(new String[] { provider, metricName, coinStackMatch,
				"Expected Coin stack: " + (int) metricDataMap.get(metricName).get("ExpectedCoinStack")
						+ " , Actual Coin stack: " + (int) metricDataMap.get(metricName).get("actualCoinStack"),
				lob, payer

		});

	}

	/*
	 * public void matchPaymentsLOBWise(String provider) throws IOException { double
	 * otherLobTotalActualPay = 0, otherLobTotalPotentialPay = 0;
	 * 
	 * for (Map.Entry<String, List<Map<String, Object>>> entry :
	 * lobDataMap.entrySet()) { String lobName = entry.getKey(); List<Map<String,
	 * Object>> data = entry.getValue();
	 * 
	 * if (!lobName.equals("All Patients")) { if ((double)
	 * data.get(0).get("ActualPay") != -1) { otherLobTotalActualPay += (double)
	 * data.get(0).get("ActualPay"); } if ((double) data.get(0).get("PotentialPay")
	 * != -1) { otherLobTotalPotentialPay += (double)
	 * data.get(0).get("PotentialPay"); } } }
	 * 
	 * String actualMatch = ((double)
	 * lobDataMap.get("All Patients").get(0).get("ActualPay") ==
	 * otherLobTotalActualPay) ? "Pass" : "Fail"; String potentialMatch = ((double)
	 * lobDataMap.get("All Patients").get(0) .get("PotentialPay") ==
	 * otherLobTotalPotentialPay) ? "Pass" : "Fail";
	 * 
	 * report.logTestResult(provider, "Lob Comparison - Earned Pay", actualMatch,
	 * "All Patients: " + (lobDataMap.containsKey("All Patients") ? (((double)
	 * lobDataMap.get("All Patients").get(0).get("ActualPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("All Patients").get(0).get("ActualPay")) : "") +
	 * " Commercial: " + (lobDataMap.containsKey("Commercial") ? (((double)
	 * lobDataMap.get("Commercial").get(0).get("ActualPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Commercial").get(0).get("ActualPay")) : "") + " Medi-Cal: " +
	 * (lobDataMap.containsKey("Medi-Cal") ? (((double)
	 * lobDataMap.get("Medi-Cal").get(0).get("ActualPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Medi-Cal").get(0).get("ActualPay")) : "") + " Medicare: " +
	 * (lobDataMap.containsKey("Medicare") ? (((double)
	 * lobDataMap.get("Medicare").get(0).get("ActualPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Medicare").get(0).get("ActualPay")) : ""), "", "");
	 * 
	 * report.logTestResult(provider, "Lob Comparison - Potential Pay",
	 * potentialMatch, "All Patients: " + (lobDataMap.containsKey("All Patients") ?
	 * (((double) lobDataMap.get("All Patients").get(0).get("PotentialPay")) == -1.0
	 * ? "NA" : lobDataMap.get("All Patients").get(0).get("PotentialPay")) : "") +
	 * " Commercial: " + (lobDataMap.containsKey("Commercial") ? (((double)
	 * lobDataMap.get("Commercial").get(0).get("PotentialPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Commercial").get(0).get("PotentialPay")) : "") +
	 * " Medi-Cal: " + (lobDataMap.containsKey("Medi-Cal") ? (((double)
	 * lobDataMap.get("Medi-Cal").get(0).get("PotentialPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Medi-Cal").get(0).get("PotentialPay")) : "") + " Medicare: "
	 * + (lobDataMap.containsKey("Medicare") ? (((double)
	 * lobDataMap.get("Medicare").get(0).get("PotentialPay")) == -1.0 ? "NA" :
	 * lobDataMap.get("Medicare").get(0).get("PotentialPay")) : ""), "", "");
	 * 
	 * report.logTestResult(provider, "Lob Comparison - Earned Pay", actualMatch,
	 * "All Patients: " + (lobDataMap.containsKey("All Patients") ?
	 * lobDataMap.get("All Patients").get(0).get("ActualPay") : "") +
	 * " Commercial: " + (lobDataMap.containsKey("Commercial") ?
	 * lobDataMap.get("Commercial").get(0).get("ActualPay") : "") + " Medi-Cal: " +
	 * (lobDataMap.containsKey("Medi-Cal") ?
	 * lobDataMap.get("Medi-Cal").get(0).get("ActualPay") : "") + " Medicare: " +
	 * (lobDataMap.containsKey("Medicare") ?
	 * lobDataMap.get("Medicare").get(0).get("ActualPay") : ""), "", "");
	 * 
	 * report.logTestResult(provider, "Lob Comparison - Potential Pay",
	 * potentialMatch, "All Patients: " + (lobDataMap.containsKey("All Patients") ?
	 * lobDataMap.get("All Patients").get(0).get("PotentialPay") : "") + ", " +
	 * " Commercial: " + (lobDataMap.containsKey("Commercial") ?
	 * lobDataMap.get("Commercial").get(0).get("PotentialPay") : "") + ", " +
	 * " Medi-Cal: " + (lobDataMap.containsKey("Medi-Cal") ?
	 * lobDataMap.get("Medi-Cal").get(0).get("PotentialPay") : "") + ", " +
	 * " Medicare: " + (lobDataMap.containsKey("Medicare") ?
	 * lobDataMap.get("Medicare").get(0).get("PotentialPay") : ""), "", "");
	 * 
	 * }
	 * 
	 * public void matchPaymentsPayerWise(String provider) throws IOException {
	 * 
	 * StringBuilder earnedDetails = new StringBuilder(); StringBuilder
	 * potentialDetails = new StringBuilder(); StringBuilder
	 * zeroPotentialPayoutDetails = new StringBuilder();
	 * 
	 * for (Map.Entry<String, List<Map<String, Object>>> entry :
	 * lobDataMap.entrySet()) { double otherPayerTotalActualPay = 0,
	 * otherPayerTotalPotentialPay = 0; String lobName = entry.getKey();
	 * 
	 * List<Map<String, Object>> data = entry.getValue();
	 * 
	 * for (int i = 0; i < data.size(); i++) { if (((String)
	 * data.get(i).get("PotentialPayout")).equals("$0.00/$0.00")) {
	 * zeroPotentialPayoutDetails.append(data.get(i).get("Payer")).append(": ")
	 * .append(data.get(i).get("PotentialPay")).append(", "); } }
	 * 
	 * for (int i = 1; i < data.size(); i++) { Map<String, Object> payerdata =
	 * data.get(i); String payerName = (String) payerdata.get("Payer");
	 * 
	 * if ((double) data.get(i).get("ActualPay") != -1) { otherPayerTotalActualPay
	 * += (double) data.get(i).get("ActualPay"); } if ((double)
	 * data.get(i).get("PotentialPay") != -1) { otherPayerTotalPotentialPay +=
	 * (double) data.get(i).get("PotentialPay"); }
	 * 
	 * earnedDetails.append(data.get(i).get("Payer")).append(": ").append(data.get(i
	 * ).get("ActualPay")) .append(", ");
	 * potentialDetails.append(data.get(i).get("Payer")).append(": ").append(data.
	 * get(i).get("PotentialPay")) .append(", ");
	 * earnedDetails.append(data.get(i).get("Payer")).append(": ") .append((double)
	 * data.get(i).get("ActualPay") == -1 ? "NA" : data.get(i).get("ActualPay"))
	 * .append(", "); potentialDetails.append(data.get(i).get("Payer")).append(": ")
	 * .append((double) data.get(i).get("PotentialPay") == -1 ? "NA" :
	 * data.get(i).get("PotentialPay")) .append(", ");
	 * 
	 * }
	 * 
	 * String actualMatch = ((double)
	 * lobDataMap.get(lobName).get(0).get("ActualPay") == otherPayerTotalActualPay)
	 * ? "Pass" : "Fail"; String potentialMatch = ((double)
	 * lobDataMap.get(lobName).get(0) .get("PotentialPay") ==
	 * otherPayerTotalPotentialPay) ? "Pass" : "Fail";
	 * 
	 * if (lobDataMap.get(lobName).get(0).get("PotentialPayout").equals("N/A") &&
	 * otherPayerTotalActualPay == 0 && otherPayerTotalPotentialPay == 0) {
	 * actualMatch = "Pass"; potentialMatch = "Pass"; }
	 * report.logTestResult(provider, "Payer Comparison - Earned Pay", actualMatch,
	 * lobDataMap.get(lobName).get(0).get("Payer") + " : " + ((double)
	 * data.get(0).get("ActualPay") == -1 ? "NA" : data.get(0).get("ActualPay")) +
	 * "," + earnedDetails.toString(), lobName, "");
	 * 
	 * report.logTestResult(provider, "Payer Comparison - Potential Pay",
	 * potentialMatch, lobDataMap.get(lobName).get(0).get("Payer") + " : " +
	 * ((double) data.get(0).get("PotentialPay") == -1 ? "NA" :
	 * data.get(0).get("PotentialPay")) + "," + potentialDetails.toString(),
	 * lobName, "");
	 * 
	 * if (zeroPotentialPayoutDetails.isEmpty()) { report.logTestResult(provider,
	 * "Incentive!= 0/0", "Pass", "", lobName, ""); } else {
	 * report.logTestResult(provider, "Incentive!= 0/0", "Fail",
	 * zeroPotentialPayoutDetails.toString(), lobName, ""); }
	 * 
	 * earnedDetails.setLength(0); potentialDetails.setLength(0);
	 * 
	 * }
	 * 
	 * }
	 * 
	 * public void showPaymentsMetricWise(String provider, String lob, String payer)
	 * throws IOException { for (Map.Entry<String, Map<String, Object>> entry :
	 * metricDataMap.entrySet()) { String metricName = entry.getKey(); Map<String,
	 * Object> data = entry.getValue();
	 * 
	 * double actualPay = (double) data.get("ActualPay"); double potentialPay =
	 * (double) data.get("PotentialPay");
	 * 
	 * deferredMetricPrintLogs.add(new String[] { provider,
	 * 
	 * metricName, "", "  Actual Pay: " + actualPay + " | Potential Pay: " +
	 * potentialPay, lob, payer }); } }
	 * 
	 * public void matchPaymentsMetricWise(String provider, String lob, String
	 * payer) throws IOException { double totalMetricActualPay = 0,
	 * totalMetricPotentialPay = 0; String actualMatch = ""; String potentialMatch =
	 * ""; List<String> metricNames = new ArrayList<>(); List<String>
	 * failedDenomMetrics = new ArrayList<>(); List<String>
	 * failedNotPresentInDataset = new ArrayList<>();
	 * 
	 * for (Map.Entry<String, Map<String, Object>> entry : metricDataMap.entrySet())
	 * { Map<String, Object> data = entry.getValue(); totalMetricActualPay +=
	 * (double) data.get("ActualPay"); totalMetricPotentialPay += (double)
	 * data.get("PotentialPay"); metricNames.add(entry.getKey()); String isPresent =
	 * (String) data.get("IsMetricPresentInDataset"); if
	 * (!"All Patients".equalsIgnoreCase(lob)) { if
	 * (!"Yes".equalsIgnoreCase(isPresent)) {
	 * failedNotPresentInDataset.add(entry.getKey()); } } if ((int)
	 * data.get("denominator") == 0) { failedDenomMetrics.add(entry.getKey()); } }
	 * 
	 * for (Map<String, Object> data : lobDataMap.get(lob)) { if
	 * (data.get("Payer").equals(payer)) { actualMatch = ((double)
	 * data.get("ActualPay") == totalMetricActualPay) ? "Pass" : "Fail";
	 * potentialMatch = ((double) data.get("PotentialPay") ==
	 * totalMetricPotentialPay) ? "Pass" : "Fail";
	 * 
	 * deferredMetricLogs.add(new String[] { provider,
	 * "Metric Comparison - Earned Pay", actualMatch, payer + ": " +
	 * data.get("ActualPay") + " ,Sum of Metrics: " + totalMetricActualPay +
	 * " , Metric Names: " + String.join(", ", metricNames), lob, payer });
	 * 
	 * deferredMetricLogs.add(new String[] { provider,
	 * "Metric Comparison - Potential Pay", potentialMatch, payer + ": " +
	 * data.get("PotentialPay") + " ,Sum of Metrics: " + totalMetricPotentialPay +
	 * " , Metric Names: " + String.join(", ", metricNames), lob, payer });
	 * 
	 * if (!"All Patients".equalsIgnoreCase(lob)) { deferredMetricLogs.add(new
	 * String[] { provider, "Metric present in Dataset",
	 * failedNotPresentInDataset.isEmpty() ? "Pass" : "Fail",
	 * failedNotPresentInDataset.isEmpty() ? "All metrics present in dataset" :
	 * String.join(", ", failedNotPresentInDataset), lob, payer }); }
	 * 
	 * deferredMetricLogs.add(new String[] { provider, "Metric Denominator != 0",
	 * failedDenomMetrics.isEmpty() ? "Pass" : "Fail", failedDenomMetrics.isEmpty()
	 * ? "No metrics with zero denominator" : String.join(", ", failedDenomMetrics),
	 * lob, payer });
	 * 
	 * break; }
	 * 
	 * }
	 * 
	 * }
	 * 
	 * public void matchPaymentWithPatientHeader(String provider,String
	 * cozevaID,String lob,String payer) { // String actualMatch
	 * =patientDataMap.get(cozevaID).get("EarnedIncentiveInPatientHeader").equals(
	 * patientDataMap.get(cozevaID).get("TotalActualPay")) ? "Pass" : "Fail";
	 * //String potentialMatch
	 * =patientDataMap.get(cozevaID).get("PotentialIncentiveInPatientHeader").equals
	 * (patientDataMap.get(cozevaID).get("TotalPotentialPay")) ? "Pass" : "Fail";
	 * String potentialPayoutMatch =
	 * patientDataMap.get(cozevaID).get("EarnedIncentiveInPatientHeader").equals(
	 * patientDataMap.get(cozevaID).get("TotalActualPay")) &&
	 * patientDataMap.get(cozevaID).get("PotentialIncentiveInPatientHeader").equals(
	 * patientDataMap.get(cozevaID).get("TotalPotentialPay")) ? "Pass" : "Fail";
	 * 
	 * 
	 * deferredPatientHeaderLogs.add(new String[] { provider, cozevaID,
	 * potentialPayoutMatch, "Actual Earned Pay in Patient Header: " +
	 * patientDataMap.get(cozevaID).get("EarnedIncentiveInPatientHeader") +
	 * " , Sum of Earned Pay in Patient Dashboard: " +
	 * patientDataMap.get(cozevaID).get("TotalActualPay") +
	 * " | Actual Potential Pay in Patient Header: " +
	 * patientDataMap.get(cozevaID).get("PotentialIncentiveInPatientHeader") +
	 * " , Sum of Potential Pay in Patient Dashboard: " +
	 * patientDataMap.get(cozevaID).get("TotalPotentialPay"), lob,payer
	 * 
	 * });
	 * 
	 * }
	 * 
	 * public void matchPaymentsPatientWise(String provider, String lob, String
	 * payer, String metricName) throws IOException { double totalActualPayInPatient
	 * = 0, totalPotentialPayInPatient = 0; List<String> complientpatients = new
	 * ArrayList<>(); List<String> incentiveMismatchedpatients = new ArrayList<>();
	 * String incentiveMatchesWithDataSet = " ";
	 * 
	 * if (!patientDataMap.isEmpty()) { incentiveMatchesWithDataSet = "Yes";
	 * 
	 * for (Map.Entry<String, Map<String, Object>> entry :
	 * patientDataMap.entrySet()) { Map<String, Object> data = entry.getValue();
	 * 
	 * double actualPay = (double) data.get("ActualPayInPatientDashboard"); double
	 * potentialPay = (double) data.get("PotentialPayInPatientDashboard");
	 * 
	 * totalActualPayInPatient += actualPay; totalPotentialPayInPatient +=
	 * potentialPay;
	 * 
	 * if (actualPay == (double)
	 * metricDataMap.get(metricName).get("MetricIncentive")) {
	 * complientpatients.add(entry.getKey()); }
	 * 
	 * if (potentialPay != (double)
	 * metricDataMap.get(metricName).get("MetricIncentive")) {
	 * incentiveMatchesWithDataSet = "No";
	 * incentiveMismatchedpatients.add(entry.getKey()); } } }
	 * 
	 * String potentialPayoutMatch = ((double) metricDataMap.get(metricName)
	 * .get("ActualPay") == totalActualPayInPatient) && ((double)
	 * metricDataMap.get(metricName).get("PotentialPay") ==
	 * totalPotentialPayInPatient) && (incentiveMatchesWithDataSet.equals("Yes")) ?
	 * "Pass" : "Fail";
	 * 
	 * int expectedCoinStack = (int)
	 * metricDataMap.get(metricName).get("ExpectedCoinStack"); int actualCoinStack =
	 * (int) metricDataMap.get(metricName).get("actualCoinStack");
	 * 
	 * String coinStackMatch = (expectedCoinStack == actualCoinStack) ? "Pass" :
	 * "Fail";
	 * 
	 * deferredPatientLogs.add(new String[] { provider, metricName,
	 * potentialPayoutMatch, "Metric Earned pay in Registry: " + (double)
	 * metricDataMap.get(metricName).get("ActualPay") +
	 * " , Sum of Earned Pay from Patients: " + totalActualPayInPatient +
	 * " | Metric Potential pay in Registry: " + (double)
	 * metricDataMap.get(metricName).get("PotentialPay") +
	 * " , Sum of Potential Pay from Patients: " + totalPotentialPayInPatient +
	 * " | Incentive Match with dataset: " + incentiveMatchesWithDataSet +
	 * " , Metric Incentive: " + (double)
	 * metricDataMap.get(metricName).get("MetricIncentive"), lob, payer
	 * 
	 * });
	 * 
	 * deferredPatientLogs.add(new String[] { provider, metricName, coinStackMatch,
	 * "Expected Coin stack: " + (int)
	 * metricDataMap.get(metricName).get("ExpectedCoinStack") +
	 * " , Actual Coin stack: " + (int)
	 * metricDataMap.get(metricName).get("actualCoinStack"), lob, payer
	 * 
	 * });
	 * 
	 * }
	 */

}
