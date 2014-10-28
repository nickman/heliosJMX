/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package web;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.heliosapm.SimpleLogger;
import com.heliosapm.SimpleLogger.SLogger;


/**
 * <p>Title: WebTest</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>web.WebTest</code></p>
 */

public class WebTest {
	public static final SLogger LOG = SimpleLogger.logger(WebTest.class);
	private static final String WEBDRIVER_SERVER_URL = "http://localhost:9515/";
	private RemoteWebDriver driver;
	
	/**
	 * Creates a new WebTest
	 * @throws Exception 
	 */
	public WebTest() throws Exception {
		setup();
	}
	
	public void setup() throws Exception {
//	    DesiredCapabilities caps = DesiredCapabilities.chrome();
//	    ChromeOptions options = new ChromeOptions();
//	    options.addArguments("test-type");
//	    caps.setCapability(ChromeOptions.CAPABILITY, options);
//	    
//	    LoggingPreferences logPrefs = new LoggingPreferences();
//	    logPrefs.enable(LogType.BROWSER, Level.ALL);
//	    logPrefs.enable(LogType.PERFORMANCE, Level.INFO);
//	    caps.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
//	    driver = (RemoteWebDriver) 
//	    		new Augmenter().augment(new RemoteWebDriver(new URL(WEBDRIVER_SERVER_URL), caps));
	    DesiredCapabilities caps = DesiredCapabilities.firefox();
	    driver = (RemoteWebDriver) 
	    		new Augmenter().augment(new RemoteWebDriver(new URL(WEBDRIVER_SERVER_URL), caps));
	    
	    
		
	}
  

  public void testTSDB() throws Exception {
    driver.get("http://localhost:4242");
    
    List<WebElement> elements = driver.findElements(By.className("gwt-SuggestBox"));
    LOG.log("SuggestBox Elements: [%s]", elements.size());
    sleep(1000);
    elements = driver.findElements(By.className("gwt-DateBox"));
    LOG.log("DateBox Elements: [%s]", elements.size());
    tearDown();
    
//    element.sendKeys("Selenium Conference 2013");
//    element.submit();
//    driver.findElement(By.linkText("Web")).click();
  }
	
  public static void sleep(final long sleep) {
	  try { Thread.currentThread().join(sleep); } catch (Exception x) {throw new RuntimeException(x);}
  }

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		LOG.log("WebTest");
		WebTest wt = null;
		try {
			wt = new WebTest();
			wt.testTSDB();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			try { wt.driver.quit();} catch (Exception x) {/* No Op */}
		}
	}

	
	
	  public void tearDown() throws Exception {
		    try {
		      Logs logs = driver.manage().logs();
		      System.out.println("Log types: " + logs.getAvailableLogTypes());
		      printLog(LogType.BROWSER);
		      submitPerformanceResult("Test.testGoogleSearch", logs.get(LogType.PERFORMANCE).getAll());
		    } finally {
		      driver.quit();
		    }
		  }

		  void printLog(String type) {
		    List<LogEntry> entries = driver.manage().logs().get(type).getAll();
		    System.out.println(entries.size() + " " + type + " log entries found");
		    for (LogEntry entry : entries) {
		      System.out.println(
		          new Date(entry.getTimestamp()) + " " + entry.getLevel() + " " + entry.getMessage());
		    }
		  }

		  void submitPerformanceResult(String name, List<LogEntry> perfLogEntries)
		      throws IOException, JSONException {
		    JSONArray devToolsLog = new JSONArray();
		    System.out.println(perfLogEntries.size() + " performance log entries found");
		    for (LogEntry entry : perfLogEntries) {
		      JSONObject message = new JSONObject(entry.getMessage());
		      JSONObject devToolsMessage = message.getJSONObject("message");
		      LOG.log("PerfLogEntry:\n%s", devToolsMessage.toString(2));
		      // System.out.println(
		      //     devToolsMessage.getString("method") + " " + message.getString("webview"));
		      //devToolsLog.put(devToolsMessage);
		    }
//		    byte[] screenshot = null;
//		    if (null == androidPackage) {  // Chrome on Android does not yet support screenshots
//		      screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
//		    }
//		    String resultUrl = new WebPageTest(new URL("http://localhost:8888/"), "Test", name)
//		        .submitResult(devToolsLog, screenshot);
//		    System.out.println("Result page: " + resultUrl);
		  }

	
}
