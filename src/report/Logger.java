package report;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private PrintStream logStream;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public Logger(String logFilePath) throws IOException {
        File logFile = new File(logFilePath);
        logFile.getParentFile().mkdirs(); // ensure folder exists

        // Redirect System.out and System.err to the log file
        logStream = new PrintStream(new FileOutputStream(logFile, true), true);
        System.setOut(logStream);
        System.setErr(logStream);

        log("===== Log started at " + dateFormat.format(new Date()) + " =====");
    }

    public void log(String message) {
        logStream.println("[" + dateFormat.format(new Date()) + "] " + message);
    }

    public void close() {
        log("===== Log ended at " + dateFormat.format(new Date()) + " =====");
        if (logStream != null) {
            logStream.close();
        }
    }
}
