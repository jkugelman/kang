package name.kugelman.john.compiler;

import java.io.*;

/** Base class for exceptions thrown by the parser. */
public abstract class ParserException extends RuntimeException {
    public ParserException(String message) {
        super(message);
    }
    
    public void printDetails(PrintStream stream) {
        stream.println(getMessage());
    }
}