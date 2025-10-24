package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.openqa.selenium.chrome.ChromeOptions;

import runner.Main;

public class ChromeProfileSetup {
	public static ChromeOptions getChromeOptions() {
		
		Properties properties = new Properties();
		try (FileInputStream fis = new FileInputStream(Main.configPath)) {
			properties.load(fis);
		} catch (IOException e) {
			System.out.println("Could not load config file: " + e.getMessage());
		}
		
        ChromeOptions options = new ChromeOptions();
        options.addArguments(properties.getProperty("testProfile"));     
       // String downloadPath = "C:\\Downloads_PaymentHTML";
        String downloadPath = properties.getProperty("downloadDir");
        
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadPath);
        prefs.put("download.prompt_for_download", false);
        prefs.put("safebrowsing.enabled", true);

        options.setExperimentalOption("prefs", prefs);
        
        return options;
    }

}
