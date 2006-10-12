package name.kugelman.john.compiler;

import java.util.*;

/**
 * Logs messages, warnings, and errors generated during compilation.
 */
public abstract class Log {
    protected interface Message {
        public String getFormatString();
    }
    
    private Collection<Logger> loggers;
    
    public Log(Logger... loggers) {
        this.loggers = new ArrayList<Logger>(Arrays.asList(loggers));
    }
    
    
    /**
     * Adds a logger to the list to be notified when messages are written to
     * the log.
     * 
     * @param logger  a logger
     */
    public void register(Logger logger) {
        loggers.add(logger);
    }
    
    /**
     * Removes a logger from the list to be notified when messages are written
     * to the log.
     * 
     * @param logger  a logger
     */
    public void removeListener(Logger logger) {
        loggers.remove(logger);
    }
    
    
    /**
     * Writes a message to the log, notifying any loggers that the message has
     * been written.
     * 
     * @param message            a message
     * @param messageParameters  the parameters for the message
     */
    public void write(Message message, Object... messageParameters) {
        String string = String.format(message.getFormatString(), messageParameters);
        
        for (Logger logger: loggers) {
            logger.message(string);
        }
    }
}