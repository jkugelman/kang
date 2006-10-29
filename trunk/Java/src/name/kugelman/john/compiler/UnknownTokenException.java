package name.kugelman.john.compiler;

/**
 * Thrown by the parser when it encounters a token that was not declared in
 * the grammar.
 */
public class UnknownTokenException extends ParserException {
    private Token token;

    UnknownTokenException(Token token) {
        super("Unknown token " + token + " encountered during parsing.");
        
        this.token = token;
    }

    /**
     * Gets the unknown token.
     * @return the unknown token.
     */
    public Token getToken() {
        return token;
    }
}