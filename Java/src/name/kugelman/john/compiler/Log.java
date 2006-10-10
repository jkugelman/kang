package name.kugelman.john.compiler;

import java.util.*;

/**
 * Logs messages, warnings, and errors generated during compilation.
 */
public abstract class Log {
    protected interface Message {
        public String getFormatString();
    }
    
    /**
     * Interface for classes that want to listen for messages being written to
     * the log.
     * 
     * @see #addListener(Listener)
     * @see #removeListener(Listener)
     */
    public interface Listener {
        /**
         * Called when a message is written to the log.
         * 
         * @param message  the message that was written
         */
        public void message(String message);
    }
    
    private Collection<Listener> listeners;
    
    public Log(Listener... listeners) {
        this.listeners = new ArrayList<Listener>(Arrays.asList(listeners));
    }
    
    
    /**
     * Adds a listener to the list to be notified when messages are written to
     * the log.
     * 
     * @param listener  a listener
     */
    public void addListener(Listener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a listener from the list to be notified when messages are written
     * to the log.
     * 
     * @param listener  a listener
     */
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
    
    
    /**
     * Writes a message to the log, notifying any listeners that the message has
     * been written.
     * 
     * @param message            a message
     * @param messageParameters  the parameters for the message
     */
    public void write(Message message, Object... messageParameters) {
        String string = String.format(message.getFormatString(), messageParameters);
        
        for (Listener listener: listeners) {
            listener.message(string);
        }
    }
}