package paymentHelper;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class FullPageScreenshotBothAxes {

    public static void takeScrollableElementScreenshot(WebDriver driver,
                                                       String containerId, // changed: accept ID instead of WebElement
                                                       String filename,
                                                       int verticalOverlapCss,
                                                       int horizontalOverlapCss,
                                                       int scrollWaitMs) throws Exception {

    	System.out.println("1");
        // changed: resolve the element by ID
        WebElement container = driver.findElement(By.id(containerId));
        System.out.println("2");
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Reset container scroll to top-left
        js.executeScript("arguments[0].scrollTo(0, 0); arguments[0].scrollTop = 0; arguments[0].scrollLeft = 0;", container);
        Thread.sleep(scrollWaitMs);
        System.out.println("3");

        // Container metrics in CSS pixels
        long scrollWidthCss  = toLong(js.executeScript("return arguments[0].scrollWidth;",  container));
        long scrollHeightCss = toLong(js.executeScript("return arguments[0].scrollHeight;", container));
        long clientWidthCss  = toLong(js.executeScript("return arguments[0].clientWidth;",  container));
        long clientHeightCss = toLong(js.executeScript("return arguments[0].clientHeight;", container));

        System.out.println("4");
        if (clientWidthCss <= 0 || clientHeightCss <= 0) {
            throw new IllegalStateException("Container client size not detected.");
        }
        System.out.println("5");

        // Probe scale from a real screenshot
        long vpW = toLong(js.executeScript("return window.innerWidth;"));
        long vpH = toLong(js.executeScript("return window.innerHeight;"));
        BufferedImage probe = ImageIO.read(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE));
        if (probe == null) throw new IllegalStateException("Probe screenshot failed.");
        double scaleX = (double) probe.getWidth()  / Math.max(1.0, (double) vpW);
        double scaleY = (double) probe.getHeight() / Math.max(1.0, (double) vpH);

        System.out.println("6");
        
        // Steps with overlap
        int stepXCss = (int) Math.max(1, clientWidthCss  - horizontalOverlapCss);
        int stepYCss = (int) Math.max(1, clientHeightCss - verticalOverlapCss);

        int cols = (int) Math.ceil((double) scrollWidthCss  / stepXCss);
        int rows = (int) Math.ceil((double) scrollHeightCss / stepYCss);

        // Final stitched image size in pixels
        int finalWidthPx  = (int) Math.round(scrollWidthCss  * scaleX);
        int finalHeightPx = (int) Math.round(scrollHeightCss * scaleY);
        BufferedImage stitched = new BufferedImage(finalWidthPx, finalHeightPx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = stitched.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        System.out.println("7");
        try {
            for (int r = 0; r < rows; r++) {
                long sY = r * stepYCss;
                if (sY + clientHeightCss > scrollHeightCss) sY = Math.max(0, scrollHeightCss - clientHeightCss);

                for (int c = 0; c < cols; c++) {
                    long sX = c * stepXCss;
                    if (sX + clientWidthCss > scrollWidthCss) sX = Math.max(0, scrollWidthCss - clientWidthCss);

                    // Scroll the container to the tile origin
                    js.executeScript("arguments[0].scrollTo(arguments[1], arguments[2]);", container, sX, sY);
                    waitForContainerScroll(driver, container, sX, sY, scrollWaitMs);

                    // Recompute the container's bounding rect per tile
                    Number rectLeftCss = (Number) js.executeScript("return arguments[0].getBoundingClientRect().left;", container);
                    Number rectTopCss  = (Number) js.executeScript("return arguments[0].getBoundingClientRect().top;",  container);

                    // Capture the viewport screenshot
                    BufferedImage shot = ImageIO.read(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE));
                    if (shot == null) throw new IllegalStateException("Tile screenshot failed.");

                    // Visible size for this tile in CSS pixels
                    int needWidthCss  = (int) Math.min(clientWidthCss,  scrollWidthCss  - sX);
                    int needHeightCss = (int) Math.min(clientHeightCss, scrollHeightCss - sY);

                    // Convert container rect to screenshot pixel coords
                    int contLeftPx   = (int) Math.round(rectLeftCss.doubleValue() * scaleX);
                    int contTopPx    = (int) Math.round(rectTopCss.doubleValue()  * scaleY);
                    int contWidthPx  = (int) Math.round(needWidthCss  * scaleX);
                    int contHeightPx = (int) Math.round(needHeightCss * scaleY);

                    // Clamp the container crop to the screenshot bounds
                    contLeftPx   = clamp(contLeftPx, 0, shot.getWidth()  - 1);
                    contTopPx    = clamp(contTopPx,  0, shot.getHeight() - 1);
                    contWidthPx  = clamp(contWidthPx,  1, shot.getWidth()  - contLeftPx);
                    contHeightPx = clamp(contHeightPx, 1, shot.getHeight() - contTopPx);

                    BufferedImage containerCrop = safeSubimage(shot, contLeftPx, contTopPx, contWidthPx, contHeightPx);

                    // Overlap crop except for first row and first column
                    int cropLeftCss = (c == 0) ? 0 : Math.min(horizontalOverlapCss, needWidthCss - 1);
                    int cropTopCss  = (r == 0) ? 0 : Math.min(verticalOverlapCss,   needHeightCss - 1);

                    int cropLeftPx  = (int) Math.round(cropLeftCss * scaleX);
                    int cropTopPx   = (int) Math.round(cropTopCss  * scaleY);
                    int cropWidthPx = containerCrop.getWidth()  - cropLeftPx;
                    int cropHeightPx= containerCrop.getHeight() - cropTopPx;

                    // Clamp overlap crop within the container crop
                    cropLeftPx   = clamp(cropLeftPx,   0, containerCrop.getWidth()  - 1);
                    cropTopPx    = clamp(cropTopPx,    0, containerCrop.getHeight() - 1);
                    cropWidthPx  = clamp(cropWidthPx,  1, containerCrop.getWidth()  - cropLeftPx);
                    cropHeightPx = clamp(cropHeightPx, 1, containerCrop.getHeight() - cropTopPx);

                    BufferedImage overlappedCrop = safeSubimage(containerCrop, cropLeftPx, cropTopPx, cropWidthPx, cropHeightPx);

                    // Destination in final image
                    long destXCss = sX + cropLeftCss;
                    long destYCss = sY + cropTopCss;
                    int destXPx   = (int) Math.round(destXCss * scaleX);
                    int destYPx   = (int) Math.round(destYCss * scaleY);

                    // Clamp destination
                    destXPx = clamp(destXPx, 0, finalWidthPx  - 1);
                    destYPx = clamp(destYPx, 0, finalHeightPx - 1);

                    g.drawImage(overlappedCrop, destXPx, destYPx, null);
                }
            }
        } finally {
        	System.out.println("8");
            g.dispose();
        }

        File out = new File(filename);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new RuntimeException("Unable to create directory " + parent.getAbsolutePath());
            }
        }
        ImageIO.write(stitched, "PNG", out);
    }

    public static void waitForPageReady(WebDriver driver, int maxWaitMs) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long end = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < end) {
            try {
                String state = (String) js.executeScript("return document.readyState");
                if ("complete".equalsIgnoreCase(state)) return;
            } catch (Exception ignored) {}
            Thread.sleep(250);
        }
    }

    private static void waitForContainerScroll(WebDriver driver, WebElement elem, long xCss, long yCss, int settleMs) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            try {
                Number sx = (Number) js.executeScript("return arguments[0].scrollLeft;", elem);
                Number sy = (Number) js.executeScript("return arguments[0].scrollTop;",  elem);
                if (sx != null && sy != null && sx.longValue() == xCss && sy.longValue() == yCss) {
                    break;
                }
            } catch (Exception ignored) {}
            Thread.sleep(50);
        }
        Thread.sleep(settleMs);
    }

    private static BufferedImage safeSubimage(BufferedImage img, int x, int y, int w, int h) {
        int maxW = img.getWidth();
        int maxH = img.getHeight();

        if (x < 0) x = 0;
        if (y < 0) y = 0;

        if (w < 1) w = 1;
        if (h < 1) h = 1;

        if (x >= maxW) x = maxW - 1;
        if (y >= maxH) y = maxH - 1;

        if (x + w > maxW) w = maxW - x;
        if (y + h > maxH) h = maxH - y;

        return img.getSubimage(x, y, w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static long toLong(Object n) {
        if (n instanceof Number) return ((Number) n).longValue();
        throw new IllegalStateException("Script did not return a number: " + n);
    }
}

/*
 * package paymentHelper;
 * 
 * import org.openqa.selenium.*; import org.openqa.selenium.chrome.ChromeDriver;
 * import org.openqa.selenium.chrome.ChromeOptions;
 * 
 * import javax.imageio.ImageIO; import java.awt.*; import
 * java.awt.image.BufferedImage; import java.io.File;
 * 
 * public class FullPageScreenshotBothAxes {
 * 
 * 
 * // Finds the scrolling element by starting from the scrollbar element (by
 * class), // then walking up the DOM until it finds an ancestor that actually
 * scrolls in Y. // Works for common custom scrollbars where the thumb/track is
 * separate from the scroller. public static WebElement
 * findScrollableContainerByScrollbarClass(WebDriver driver, String
 * scrollbarClass) { JavascriptExecutor js = (JavascriptExecutor) driver;
 * 
 * // Try to resolve the scrollbar element from the class. Object result =
 * js.executeScript( "var cls = arguments[0];" +
 * "var nodes = document.getElementsByClassName(cls);" +
 * "if (!nodes || nodes.length === 0) return null;" + "var sb = nodes[0];" +
 * "function isScrollableY(el) {" + "  if (!el) return false;" +
 * "  var style = window.getComputedStyle(el);" + "  var oy = style.overflowY;"
 * +
 * "  if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay') && el.scrollHeight > el.clientHeight) return true;"
 * +
 * "  if (el.scrollHeight > el.clientHeight && oy !== 'hidden' && oy !== 'visible') return true;"
 * + "  return false;" + "}" + "var e = sb;" + "while (e) {" +
 * "  if (isScrollableY(e)) return e;" + "  e = e.parentElement;" + "}" +
 * "return null;", scrollbarClass);
 * 
 * if (result instanceof WebElement) { return (WebElement) result; } return
 * null; }
 * 
 * // Stitches a full screenshot of a scrollable container element. // If it
 * only scrolls vertically, cols will be 1 and it will just tile down. public
 * static void takeScrollableElementScreenshot(WebDriver driver, WebElement
 * container, String filename, int verticalOverlapCss, int horizontalOverlapCss,
 * int scrollWaitMs, int maxPageReadyMs) throws Exception {
 * System.out.println("Screenshot processing..."); JavascriptExecutor js =
 * (JavascriptExecutor) driver;
 * 
 * // Ensure container is at its top-left scroll js.
 * executeScript("arguments[0].scrollTo(0, 0); arguments[0].scrollTop = 0; arguments[0].scrollLeft = 0;"
 * , container); waitForPageReadyWithSettle(driver, scrollWaitMs,
 * maxPageReadyMs);
 * 
 * // Obtain container sizes in CSS pixels long scrollWidthCss =
 * toLong(js.executeScript("return arguments[0].scrollWidth;", container)); long
 * scrollHeightCss =
 * toLong(js.executeScript("return arguments[0].scrollHeight;", container));
 * long clientWidthCss =
 * toLong(js.executeScript("return arguments[0].clientWidth;", container)); long
 * clientHeightCss =
 * toLong(js.executeScript("return arguments[0].clientHeight;", container));
 * 
 * if (clientWidthCss <= 0 || clientHeightCss <= 0) { throw new
 * IllegalStateException("Container client size not detected."); }
 * 
 * // Viewport size for scale estimation long vpW =
 * toLong(js.executeScript("return window.innerWidth;")); long vpH =
 * toLong(js.executeScript("return window.innerHeight;")); BufferedImage probe =
 * ImageIO.read(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE)); if
 * (probe == null) throw new
 * IllegalStateException("Could not capture probe screenshot."); double scaleX =
 * (double) probe.getWidth() / (double) vpW; double scaleY = (double)
 * probe.getHeight() / (double) vpH;
 * 
 * // Steps with overlap int stepXCss = (int) Math.max(1, clientWidthCss -
 * horizontalOverlapCss); int stepYCss = (int) Math.max(1, clientHeightCss -
 * verticalOverlapCss);
 * 
 * int cols = (int) Math.ceil((double) scrollWidthCss / stepXCss); int rows =
 * (int) Math.ceil((double) scrollHeightCss / stepYCss);
 * 
 * int finalWidthPx = (int) Math.round(scrollWidthCss * scaleX); int
 * finalHeightPx = (int) Math.round(scrollHeightCss * scaleY); BufferedImage
 * stitched = new BufferedImage(finalWidthPx, finalHeightPx,
 * BufferedImage.TYPE_INT_ARGB); Graphics2D g = stitched.createGraphics();
 * g.setComposite(AlphaComposite.Src);
 * g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
 * RenderingHints.VALUE_INTERPOLATION_BILINEAR);
 * 
 * try { for (int r = 0; r < rows; r++) { long sY = r * stepYCss; if (sY +
 * clientHeightCss > scrollHeightCss) sY = Math.max(0, scrollHeightCss -
 * clientHeightCss);
 * 
 * for (int c = 0; c < cols; c++) { long sX = c * stepXCss; if (sX +
 * clientWidthCss > scrollWidthCss) sX = Math.max(0, scrollWidthCss -
 * clientWidthCss);
 * 
 * js.executeScript("arguments[0].scrollTo(arguments[1], arguments[2]);",
 * container, sX, sY); waitForScrollSettle(driver, container, sX, sY,
 * scrollWaitMs);
 * 
 * Number rectLeftCss = (Number)
 * js.executeScript("return arguments[0].getBoundingClientRect().left;",
 * container); Number rectTopCss = (Number)
 * js.executeScript("return arguments[0].getBoundingClientRect().top;",
 * container);
 * 
 * BufferedImage shot = ImageIO.read(((TakesScreenshot)
 * driver).getScreenshotAs(OutputType.FILE)); if (shot == null) throw new
 * IllegalStateException("Failed to capture tile screenshot.");
 * 
 * int needWidthCss = (int) Math.min(clientWidthCss, scrollWidthCss - sX); int
 * needHeightCss = (int) Math.min(clientHeightCss, scrollHeightCss - sY);
 * 
 * int contLeftPx = (int) Math.round(rectLeftCss.doubleValue() * scaleX); int
 * contTopPx = (int) Math.round(rectTopCss.doubleValue() * scaleY); int
 * contWidthPx = clamp((int) Math.round(needWidthCss * scaleX), 1,
 * shot.getWidth() - contLeftPx); int contHeightPx = clamp((int)
 * Math.round(needHeightCss * scaleY), 1, shot.getHeight() - contTopPx);
 * 
 * BufferedImage containerCrop = shot.getSubimage( clamp(contLeftPx, 0,
 * shot.getWidth() - 1), clamp(contTopPx, 0, shot.getHeight() - 1),
 * clamp(contWidthPx, 1, shot.getWidth() - contLeftPx), clamp(contHeightPx, 1,
 * shot.getHeight() - contTopPx) );
 * 
 * int cropLeftCss = (c == 0) ? 0 : horizontalOverlapCss; int cropTopCss = (r ==
 * 0) ? 0 : verticalOverlapCss;
 * 
 * cropLeftCss = Math.min(cropLeftCss, needWidthCss - 1); cropTopCss =
 * Math.min(cropTopCss, needHeightCss - 1);
 * 
 * int cropLeftPx = (int) Math.round(cropLeftCss * scaleX); int cropTopPx =
 * (int) Math.round(cropTopCss * scaleY);
 * 
 * int cropWidthPx = containerCrop.getWidth() - cropLeftPx; int cropHeightPx =
 * containerCrop.getHeight() - cropTopPx;
 * 
 * cropWidthPx = clamp(cropWidthPx, 1, containerCrop.getWidth() - cropLeftPx);
 * cropHeightPx = clamp(cropHeightPx, 1, containerCrop.getHeight() - cropTopPx);
 * 
 * BufferedImage overlappedCrop = containerCrop.getSubimage( cropLeftPx,
 * cropTopPx, cropWidthPx, cropHeightPx );
 * 
 * long destXCss = sX + cropLeftCss; long destYCss = sY + cropTopCss;
 * 
 * int destXPx = (int) Math.round(destXCss * scaleX); int destYPx = (int)
 * Math.round(destYCss * scaleY);
 * 
 * g.drawImage(overlappedCrop, destXPx, destYPx, null); } } } finally {
 * g.dispose(); }
 * 
 * File out = new File(filename); File parent = out.getParentFile(); if (parent
 * != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) { throw
 * new RuntimeException("Unable to create directory " +
 * parent.getAbsolutePath()); } ImageIO.write(stitched, "PNG", out); }
 * 
 * private static void waitForPageReady(WebDriver driver) throws
 * InterruptedException { JavascriptExecutor js = (JavascriptExecutor) driver;
 * long end = System.currentTimeMillis() + 30000; while
 * (System.currentTimeMillis() < end) { try { String state = (String)
 * js.executeScript("return document.readyState"); if
 * ("complete".equalsIgnoreCase(state)) return; } catch (Exception ignored) {}
 * Thread.sleep(250); } }
 * 
 * private static void waitForPageReadyWithSettle(WebDriver driver, int
 * settleMs, int maxWaitMs) throws InterruptedException {
 * waitForPageReady(driver); Thread.sleep(settleMs); }
 * 
 * private static void waitForScrollSettle(WebDriver driver, WebElement elem,
 * long xCss, long yCss, int settleMs) throws InterruptedException {
 * JavascriptExecutor js = (JavascriptExecutor) driver; long end =
 * System.currentTimeMillis() + 5000; while (System.currentTimeMillis() < end) {
 * try { Number sx = (Number)
 * js.executeScript("return arguments[0].scrollLeft;", elem); Number sy =
 * (Number) js.executeScript("return arguments[0].scrollTop;", elem); if (sx !=
 * null && sy != null && sx.longValue() == xCss && sy.longValue() == yCss) {
 * break; } } catch (Exception ignored) {} Thread.sleep(50); }
 * Thread.sleep(settleMs); }
 * 
 * private static long toLong(Object n) { if (n instanceof Number) return
 * ((Number) n).longValue(); throw new
 * IllegalStateException("Script did not return a number: " + n); }
 * 
 * private static int clamp(int v, int lo, int hi) { return Math.max(lo,
 * Math.min(hi, v)); } }
 */