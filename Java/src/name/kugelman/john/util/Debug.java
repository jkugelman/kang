package name.kugelman.john.util;

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Global logging class. Displays debugging messages to <code>System.out</code>
 * and <code>System.err</code>, along with associated line numbers, file names,
 * and timestamps.
 * <p>
 * There are five types of messages: ERROR, WARNING, MAJOR, MINOR, and VERBOSE.
 * You can specify which type of messages to display for each package and class.
 * This will show all error and warning messages from any class under
 * <code>name.kugelman.john</code>:
 * <p>
 * Example: <kbd>-Dlog.level=name.kugelman.john:WARNING</kbd>
 * <p>
 * You can separate multiple packages with semi-colons. If you were working on
 * this class, you might set the log level to this:
 * <p>
 * Example: <kbd>-Dlog.level=name.kugelman.john:WARNING;name.kugelman.john.util.Debug:VERBOSE</kbd>
 */
public abstract class Debug {
    public static enum Level {
        DEBUG  ("[DEBUG]", false),
        ERROR  ("[ERROR]", true),
        WARNING("[WARN]",  true),
        MAJOR  ("[MAJOR]", false),
        MINOR  ("[-----]", false),
        VERBOSE("[---]",   false);
        
        private final String  string;
        private final boolean isError;
        
        private Level(String string, boolean isError) {
            this.string  = string;
            this.isError = isError;
        }
        
        @Override
        public String toString() {
            return this.string;
        }
        
        public boolean isError() {
            return this.isError;
        }
        
        public static final int LONGEST_STRING_LENGTH = longestStringLength();
        
        private static int longestStringLength() {
            int length = 0;
            
            for (Level level: values()) {
                length = Math.max(length, level.toString().length());
            }
            
            return length;
        }
    }
    
    private static final Map<String, Level> packageLevels;
    private static       Level              locationLevel;
    
    static {
        packageLevels = new HashMap<String, Level>();
        
        // Always show warnings and errors from this class.
        packageLevels.put(Debug.class.getCanonicalName(), Level.WARNING);

        if (System.getProperty("log.level") == null) {
            packageLevels.put("", Level.WARNING);
        }
        else {
            // Break up the string into package.name=L1,L2 strings.
            StringTokenizer tokenizer = new StringTokenizer(System.getProperty("log.level"), ";", false);
            
            while (tokenizer.hasMoreTokens()) {
                String packageAndLevels = tokenizer.nextToken();
                
                try {
                    String packageName = packageAndLevels.split(":")[0];
                    String levelName   = packageAndLevels.split(":")[1].toUpperCase();
                    
                    try {
                        packageLevels.put(packageName, Level.valueOf(levelName));
                    }
                    catch (IllegalArgumentException exception) {
                        Debug.logWarning("Invalid log.level \"%s\".", levelName);
                    }
                    
                }
                catch (ArrayIndexOutOfBoundsException exception) {
                    Debug.logWarning("Invalid package log.level \"%s\".", packageAndLevels);
                }
            }
        }
        
        String location = System.getProperty("log.location");
       
        if (location == null) {
            locationLevel = Level.VERBOSE;
        }
        else {
            try {
                locationLevel = Level.valueOf(location);
            }
            catch (IllegalArgumentException exception) {
                Debug.logWarning("Invalid log.location \"%s\".", location);
            }
        }
    }
    
    public static void logDebug  (                     String format, Object... arguments) { log(Level.DEBUG,              format, arguments); }
    public static void logError  (                     String format, Object... arguments) { log(Level.ERROR,              format, arguments); }
    public static void logWarning(                     String format, Object... arguments) { log(Level.WARNING,            format, arguments); }
    public static void logMajor  (                     String format, Object... arguments) { log(Level.MAJOR,              format, arguments); }
    public static void logMinor  (                     String format, Object... arguments) { log(Level.MINOR,              format, arguments); }
    public static void logVerbose(                     String format, Object... arguments) { log(Level.VERBOSE,            format, arguments); }

    public static void logDebug  (Throwable throwable, String format, Object... arguments) { log(Level.DEBUG,   throwable, format, arguments); }
    public static void logError  (Throwable throwable, String format, Object... arguments) { log(Level.ERROR,   throwable, format, arguments); }
    public static void logWarning(Throwable throwable, String format, Object... arguments) { log(Level.WARNING, throwable, format, arguments); }
    public static void logMajor  (Throwable throwable, String format, Object... arguments) { log(Level.MAJOR,   throwable, format, arguments); }
    public static void logMinor  (Throwable throwable, String format, Object... arguments) { log(Level.MINOR,   throwable, format, arguments); }
    public static void logVerbose(Throwable throwable, String format, Object... arguments) { log(Level.VERBOSE, throwable, format, arguments); }
    
    public static void logDebug  (Throwable throwable                                    ) { log(Level.DEBUG,   throwable);                    }
    public static void logError  (Throwable throwable                                    ) { log(Level.ERROR,   throwable);                    }
    public static void logWarning(Throwable throwable                                    ) { log(Level.WARNING, throwable);                    }
    public static void logMajor  (Throwable throwable                                    ) { log(Level.MAJOR,   throwable);                    }
    public static void logMinor  (Throwable throwable                                    ) { log(Level.MINOR,   throwable);                    }
    public static void logVerbose(Throwable throwable                                    ) { log(Level.VERBOSE, throwable);                    }

    public static void log(Level level, String format, Object... arguments) {
        StackTraceElement caller = determineCaller();
        
        if (showingMessagesFrom(caller, level)) {
            writeMessage(caller, level, String.format(format, arguments), null);
        }
    }
    
    public static void log(Level level, Throwable throwable, String format, Object... arguments) {
        StackTraceElement caller = determineCaller();
        
        if (showingMessagesFrom(caller, level)) {
            String       message       = null;
            StringWriter detailsWriter = new StringWriter();
            
            if (format != null) {
                message = String.format(format, arguments);
            }
            
            throwable.printStackTrace(new PrintWriter(detailsWriter));
            
            writeMessage(caller, level, message, detailsWriter.toString());
        }
    }
    
    public static void log(Level level, Throwable throwable) {
        log(level, throwable, null);
    }

    
    private static void writeMessage(StackTraceElement caller, Level level, String message, String details) {
        String      output = buildOutput(caller, level, message, details);
        PrintStream stream = level.isError() ? System.err : System.out;
        
        synchronized (Debug.class) {
            stream.print(output);
        }
    }

    private static StackTraceElement determineCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        for (int i = 0; i + 1 < stackTrace.length; ++i) {
            if (stackTrace[i].getClassName () == Debug.class.getCanonicalName() && 
                stackTrace[i].getMethodName().matches("log.+"))
            {
                return stackTrace[i + 1];
            }
        }
        
        throw new IllegalStateException("Unable to determine caller for logging message.");
    }
    
    private static boolean showingMessagesFrom(StackTraceElement caller, Level level) {
        for (String packageName: packageLevels.keySet()) {
            if (!caller.getClassName().startsWith(packageName)) {
                continue;
            }
            
            if (level.compareTo(packageLevels.get(packageName)) <= 0) {
                return true;
            }
        }
        
        return false;
    }
    
    private static String buildOutput(StackTraceElement caller, Level level, String message, String details) {
        StringBuilder outputBuilder = new StringBuilder();
        
        outputBuilder.append(buildPrefix(level, caller));
        
        if (message != null) {
            outputBuilder.append(": ");
            outputBuilder.append(message);
        }
        
        outputBuilder.append("\n");
        
        if (details != null) {
            outputBuilder.append("\n");
            
            for (String line: details.split("\n")) {
                for (int i = 0; i < Level.LONGEST_STRING_LENGTH + 1; ++i) {
                    outputBuilder.append(' ');
                }
                
                outputBuilder.append(line);
                outputBuilder.append("\n");
            }

            outputBuilder.append("\n");
        }
        
        return outputBuilder.toString();
    }
    
    private static String buildPrefix(Level level, StackTraceElement callerElement) {
        String timestamp = DateFormat.getDateTimeInstance().format(new Date());
        int    padding   = Level.LONGEST_STRING_LENGTH - level.toString().length();
        String width     = (padding > 0) ? "" + padding : "";
        String caller    = "";
        
        // Show the file name and line number for warnings and errors.
        if (locationLevel != null && level.compareTo(locationLevel) <= 0 && callerElement != null) {
            caller = String.format(" (%s:%s)", callerElement.getFileName(), callerElement.getLineNumber());
        }
        
        return String.format("%s%" + width + "s %s%s", level, "", timestamp, caller);
    }
}