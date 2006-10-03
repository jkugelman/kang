package name.kugelman.john.compiler;

import java.util.regex.*;

/**
 * Reads an XML file containing the specification for a context-free grammar and
 * stores the tokens, variables, and their associated rules. If this grammar
 * specifies an unambiguous LR(1) language it is then possible to build a parser
 * for the language described by the grammar.
 */
public class Grammar {
    /**
     * Base class for the {@link Terminal} and {@link Variable} classes.
     */
    public class Item {
    }

    /**
     * Represents a terminal symbol, which corresponds to a token returned by
     * the lexer. Terminals are the atomic units of text that make up a source
     * program.
     */
    public class Terminal extends Item {
        private String  tokenClass;
        private boolean isDiscardable;

        /**
         * Creates a new terminal.
         * 
         * @param tokenClass
         *            the token class associated with this terminal
         * @param isDiscardable
         *            if <code>true</code>, all instances of this terminal
         *            are discarded after being parsed and are not added to the
         *            parse tree
         */
        public Terminal(String tokenClass, boolean isDiscardable) {
            this.tokenClass    = tokenClass;
            this.isDiscardable = isDiscardable;
        }
        
        /**
         * Gets the token class associated with this terminal.
         * 
         * @return the token class associated with this terminal
         */
        public String getTokenClass() {
            return tokenClass;
        }
        
        /**
         * If <code>true</code>, all instances of this terminal are discarded
         * after being parsed and are not added to the parse tree. This property
         * can be overridden by individual {@link Rule.TerminalReference}s.
         * 
         * @return whether the token is discardable or not
         */
        public boolean isDiscardable() {
            return this.isDiscardable;
        }
        
        
        /**
         * Returns the terminal as a string, adding single quotes if the
         * terminal is a symbol.
         */
        public String toString() {
            return Pattern.matches("^[\\w\\s]+$", tokenClass)
                 ? tokenClass
                 : "'" + tokenClass + "'";
        }
    }
}
