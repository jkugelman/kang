package name.kugelman.john.compiler;

/**
 * Interface for classes that want to listen for messages being written to
 * the log.
 * 
 * @see Log#register(Logger)
 * @see Log#unregister(Logger)
 */
public interface Logger {
    /**
     * Called when a message is written to the log.
     * 
     * @param message  the message that was written
     */
    public void message(String message);
}