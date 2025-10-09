package config;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class DriverSetup {
	private static WebDriver driver;
	
	public static WebDriver getDriver() {
		WebDriverManager.chromedriver().setup();
		ChromeOptions options = ChromeProfileSetup.getChromeOptions();
		driver=new ChromeDriver(options);
		driver.manage().window().maximize();
		return driver;
	}

}
