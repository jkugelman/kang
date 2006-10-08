package name.kugelman.john.compiler;

/**
 * Exception thrown when there's an error in an XML grammar.
 * 
 * @see Grammar#fromXML(org.jdom.Document)
 */
public class InvalidGrammarException extends Exception {
    public InvalidGrammarException() {
        this("Unspecified error in XML grammar.");
    }

    public InvalidGrammarException(String message) {
        super(message);
    }
    
    public InvalidGrammarException(Throwable cause) {
        this("Invalid XML grammar.", cause);
    }
    
    public InvalidGrammarException(String message, Throwable cause) {
        super(message, cause);
    }
}
