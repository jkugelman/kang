package name.kugelman.john.kang.compiler;

/**
 * Logs messages generated while compiling a Kang source file.
 */
public class Log extends name.kugelman.john.compiler.Log {
    public Log(Listener... listeners) {
        super(listeners);
    }
    
    /**
     * Codes for error messages that can be generated during compilation.
     * <p>
     * The format string for each message is repeated in the Javadoc comment so
     * callers can easily see the value placeholders.
     */
    public enum Error implements Message {
        /** Ellipsis '…' not at the end of the line. */
        ELLIPSIS_NOT_AT_END_OF_LINE("Ellipsis '…' not at the end of the line."),

        /** Invalid character '%c'. */
        INVALID_CHARACTER("Invalid character '%c'."),

        /** %s is not a valid number. */
        INVALID_NUMBER("%s is not a valid number.");

        
        private String formatString;

        Error(String formatString) {
            this.formatString = formatString;
        }
        
        public String getFormatString() {
            return "Error: " + formatString;
        }
    }

    /**
     * Codes for warning messages that can be generated during compilation.
     * <p>
     * The format string for each message is repeated in the Javadoc comment so
     * callers can easily see the value placeholders.
     */
    public enum Warning implements Message {
        ;
        
        private String formatString;

        @SuppressWarnings("unused")
        Warning(String formatString) {
            this.formatString = formatString;
        }
        
        public String getFormatString() {
            return "Warning: " + formatString;
        }
    }
}