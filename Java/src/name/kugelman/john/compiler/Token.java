/**
 * Minimal interface for the tokens produced by a lexer. Tokens are indivisible
 * atomic pieces of text from a program, such as numbers, symbols, and
 * identifiers. It is the lexer's job to analyze a source code file and break it
 * up into tokens to be used by the parser.
 */
public abstract class Token {
    /**
     * Gets this token's class. Tokens belonging to the same class are treated
     * as equivalent during parsing. Depending on the granularity of the
     * divisions between types, some classes might only have one associated
     * token (like "plus sign" or "left parenthesis"), whereas others might have
     * many (like "identifier" or "keyword").
     * <p>
     * Token classes correspond to "terminals" in a context-free grammar.
     * 
     * @return this token's class
     * 
     * @see Grammar.Terminal
     */
    public abstract Enum<?> getTokenClass();
    
    /**
     * Gets this token's lexeme. A lexeme is a token's exact lexical
     * representation in the source code—that is, the characters that it was
     * composed of in the source. For example, a plus sign token's lexeme would
     * be "+", and an identifier token's lexeme would be the identifier itself.
     * 
     * @return this token's lexeme
     */
    public abstract String getLexeme();

    /**
     * Gets the position of the first character of the token.
     * 
     * @return this token's start position
     */
    public abstract Tokenizer.Position getStart();

    /**
     * Gets the position of the last character of the token.
     * 
     * @return this token's end position
     */
    public abstract Tokenizer.Position getEnd();
    
    
    public String toString() {
        StringBuilder builder = new StringBuilder();

        if (!getTokenClass().toString().equals(getLexeme())) {
            builder.append(getTokenClass());

            if (getLexeme() != null) {
                builder.append(": ");
            }
        }

        if (getLexeme() != null) {
            builder.append("'");
            builder.append(this.getLexeme());
            builder.append("'");
        }

        return builder.toString();
    }

}
