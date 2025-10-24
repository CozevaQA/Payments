package login;

import java.io.IOException;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import paymentHelper.PaymentHelper;


public class Login extends PaymentHelper {

	public Login(WebDriver driver) throws IOException {
		super(driver);
	}


	public void LoginCozeva(String server) {
		driver.get(server);
		
		if(server.equals(properties.getProperty("Prod"))) {
			driver.findElement(By.tagName("body")).click();
			driver.findElement(By.xpath(properties.getProperty("loginFormButton"))).click();
		}
		
		driver.findElement(By.xpath(properties.getProperty("username"))).sendKeys(System.getenv("CSUser"));
		driver.findElement(By.xpath(properties.getProperty("password"))).sendKeys(System.getenv("CS2Password"));
		driver.findElement(By.xpath(properties.getProperty("loginButton"))).click();
		
		List<WebElement> twoStepVerification = driver.findElements(By.xpath(properties.getProperty("twostepVerification")));
		if(twoStepVerification.size() == 1) {
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		driver.findElement(By.xpath(properties.getProperty("reasonForLogin"))).sendKeys("https://redmine2.cozeva.com/issues/39338");

		driver.findElement(By.xpath(properties.getProperty("submitButton"))).click();
	}
	
	public void Masquerade() {
	
		driver.findElement(By.xpath(properties.getProperty("profileIcon"))).click();
		wait.until(ExpectedConditions
				.elementToBeClickable(By.xpath(properties.getProperty("userList")))).click();
		
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(properties.getProperty("ajax_preloader"))));
		
		driver.findElement(By.xpath(properties.getProperty("filterList"))).click();
		
		WebElement searchBox = driver.findElement(By.xpath(properties.getProperty("searchUser")));
        searchBox.sendKeys("Adelines.Nunez@centene.com" + Keys.ENTER);
        
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath(properties.getProperty("ajax_preloader"))));
        driver.findElement(By.xpath(properties.getProperty("selectUser"))).click();
        driver.findElement(By.xpath(properties.getProperty("threedot"))).click();
        driver.findElement(By.xpath(properties.getProperty("masquerade"))).click();
        
        wait.until(ExpectedConditions
				.elementToBeClickable(By.xpath(properties.getProperty("reasonFormasquerade")))).sendKeys("https://redmine2.cozeva.com/issues/39338");
        
		driver.findElement(By.xpath(properties.getProperty("signature"))).sendKeys("Mousumi Dey");
		driver.findElement(By.xpath(properties.getProperty("go"))).click();
		wait.until(ExpectedConditions
				.elementToBeClickable(By.xpath(properties.getProperty("skip")))).click();
	}

	
	

}
