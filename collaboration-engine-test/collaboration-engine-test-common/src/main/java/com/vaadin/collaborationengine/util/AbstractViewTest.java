/*
 * Copyright 2000-2024 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.collaborationengine.util;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.vaadin.flow.theme.AbstractTheme;
import com.vaadin.testbench.IPAddress;
import com.vaadin.testbench.Parameters;
import com.vaadin.testbench.ScreenshotOnFailureRule;
import com.vaadin.testbench.TestBench;
import com.vaadin.testbench.TestBenchDriverProxy;
import com.vaadin.testbench.TestBenchTestCase;
import com.vaadin.testbench.parallel.SauceLabsIntegration;
import com.vaadin.testbench.parallel.setup.SetupDriver;

/**
 * Base class for TestBench IntegrationTests on chrome.
 * <p>
 * The tests use Chrome driver (see pom.xml for integration-tests profile) to
 * run integration tests on a headless Chrome. If a property {@code test.use
 * .hub} is set to true, {@code AbstractViewTest} will assume that the TestBench
 * test is running in a CI environment. In order to keep the this class light,
 * it makes certain assumptions about the CI environment (such as available
 * environment variables). It is not advisable to use this class as a base class
 * for you own TestBench tests.
 * <p>
 * To learn more about TestBench, visit <a href=
 * "https://vaadin.com/docs/v10/testbench/testbench-overview.html">Vaadin
 * TestBench</a>.
 */
public abstract class AbstractViewTest extends TestBenchTestCase {
    private static final int SERVER_PORT = 8080;

    private final String deploymentHostname;

    private final By rootSelector;
    private static final Logger logger = Logger
            .getLogger(AbstractViewTest.class.getName());
    protected final boolean isSauce;
    protected final boolean isHub;
    protected final boolean isLocal;
    protected final boolean isHeadless;

    @Rule
    public ScreenshotOnFailureRule rule = new ScreenshotOnFailureRule(this,
            false);

    public AbstractViewTest() {
        this(By.tagName("body"));
    }

    protected AbstractViewTest(By rootSelector) {
        boolean forceLocal = Parameters.isLocalWebDriverUsed();
        isHub = !forceLocal && Parameters.getHubHostname() != null;
        isSauce = !forceLocal && !isHub
                && SauceLabsIntegration.isConfiguredForSauceLabs();
        isLocal = !isSauce && !isHub;
        isHeadless = isLocal && Boolean.getBoolean("headless");
        deploymentHostname = isHub ? IPAddress.findSiteLocalAddress()
                : "localhost";
        this.rootSelector = rootSelector;
    }

    @Before
    public void setup() throws Exception {
        setDriver(createDriver());
        getDriver().get(getURL());
    }

    @After
    public void close() {
        if (driver != null) {
            driver.close();
        }
    }

    protected void refresh() {
        driver.navigate().refresh();
    }

    protected WebDriver createDriver() throws Exception {
        if (isSauce) {
            return createSauceDriver();
        }
        if (isHub) {
            return createHubDriver();
        }
        return createChromeDriver();
    }

    private WebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox", "--disable-dev-shm-usage");
        // Allows using a specific Chrome installation instead of the one
        // ChromeDriver finds by default, e.g. to match the ChromeDriver version
        String chromeBinary = System.getProperty("chrome.binary");
        if (chromeBinary != null && !chromeBinary.isBlank()) {
            options.setBinary(chromeBinary);
        }
        if (isHeadless) {
            // Headless Chrome defaults to a small 800x600 window, where
            // clicking buttons scrolls fields out of view, which affects
            // features that depend on visibility, such as user tags
            options.addArguments("--headless=new", "--window-size=1920,1080");
        }
        logger.info("Using Local Chrome with Capabilities: " + options.asMap());
        TestBenchDriverProxy driver = TestBench
                .createDriver(new ChromeDriver(options));
        return driver;
    }

    private WebDriver createHubDriver() throws Exception {
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability("browserName", "chrome");
        SetupDriver driverConfiguration = new SetupDriver();
        driverConfiguration.setDesiredCapabilities(desiredCapabilities);
        logger.info("Using Selenium Hub with " + desiredCapabilities);
        return driverConfiguration.setupRemoteDriver(
                "http://" + Parameters.getHubHostname() + ":4444/wd/hub");
    }

    private WebDriver createSauceDriver() throws Exception {
        SetupDriver driverConfiguration = new SetupDriver();
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setCapability("browserName", "chrome");
        SauceLabsIntegration.setDesiredCapabilities(desiredCapabilities);
        driverConfiguration.setDesiredCapabilities(desiredCapabilities);
        return driverConfiguration
                .setupRemoteDriver(SauceLabsIntegration.getHubUrl());
    }

    /**
     * Convenience method for getting the root element of the view based on the
     * selector passed to the constructor.
     *
     * @return the root element
     */
    protected WebElement getRootElement() {
        return findElement(rootSelector);
    }

    /**
     * Asserts that the given {@code element} is rendered using a theme
     * identified by {@code themeClass}. If the the is not found, JUnit assert
     * will fail the test case.
     *
     * @param element
     *            web element to check for the theme
     * @param themeClass
     *            theme class (such as {@code Lumo.class}
     */
    protected void assertThemePresentOnElement(WebElement element,
            Class<? extends AbstractTheme> themeClass) {
        String themeName = themeClass.getSimpleName().toLowerCase();
        Boolean hasStyle = (Boolean) executeScript(
                "" + "var styles = Array.from(arguments[0]._template.content"
                        + ".querySelectorAll('style'))"
                        + ".filter(style => style.textContent.indexOf('"
                        + themeName + "') > -1);" + "return styles.length > 0;",
                element);

        Assert.assertTrue(
                "Element '" + element.getTagName() + "' should have"
                        + " had theme '" + themeClass.getSimpleName() + "'.",
                hasStyle);
    }

    /**
     * Returns deployment host name concatenated with route.
     *
     * @return URL to route
     */
    protected String getURL() {
        String path = getRoute();
        return getURL(path);
    }

    /**
     * Returns deployment host name concatenated with route.
     *
     * @param path
     *            route path
     * @return URL to route
     */
    protected String getURL(String path) {
        return String.format("http://%s:%d/%s", deploymentHostname, SERVER_PORT,
                path);
    }

    /**
     * Get the route of the view to navigate to. In most cases it should be the
     * same as the value in the {@code @Route} annotation of the view.
     */
    public abstract String getRoute();
}
