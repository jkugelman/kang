package name.kugelman.john.kang.compiler;

import java.util.*;
import java.util.regex.*;

import name.kugelman.john.compiler.Tokenizer.*;

/**
 * A token from a Kang source file.
 */
public class Token extends name.kugelman.john.compiler.Token {
    /**
     * Describes the token's type, which serves as its {@link #getTokenClass()}.
     */
    public enum Type {
        /** Open block (beginning of indented section). */
        OPEN_BLOCK,
        /** Close block (end of indented section). */
        CLOSE_BLOCK,

        /** End of the current line. */
        END_OF_LINE,
    
        /** Integer literal (e.g., 54). */
        INTEGER_LITERAL,
        /** Real literal (e.g., 3.1415). */
        REAL_LITERAL,
        /** Character literal (e.g., 'x'). */
        CHARACTER_LITERAL,
        /** String literal (e.g., "Hello world!"). */
        STRING_LITERAL,

        /** Identifier. */
        IDENTIFIER,

        /** Keyword. */
        KEYWORD,
        
        /**
         * Applied to tokens that are the only one in their class, and which can
         * be distinguished from all other tokens by their lexeme alone.
         */
        SYMBOL
    }

    /** The data stored for an integer literal. */
    public static class IntegerData
    {
        /** The value of the integer literal. */
        public int value;

        /**
         * Creates the data for an integer literal token.
         * 
         * @param lexeme  the token's lexeme
         */
        public IntegerData(String lexeme) {
            value = Integer.parseInt(lexeme);
        }
    }

    /** The data stored for a real literal. */
    public static class RealData {
        /** The value of the real literal. */
        public double value;

        /**
         * Creates the data for a real literal token.
         * 
         * @param lexeme  the token's lexeme
         */
        public RealData(String lexeme) {
            value = Double.parseDouble(lexeme);
        }
    }

    /** The data stored for a character literal. */
    public static class CharacterData {
        /** The value of the character literal. */
        public char value;

        /**
         * Creates the data for a character literal token.
         * 
         * @param lexeme  the token's lexeme
         */
        public CharacterData(String lexeme) {
            value = lexeme.charAt(0);
        }
    }

    /** The data stored for a string literal. */
    public static class StringData {
        /**
         * The value of the string literal, with end quotes stripped and escape
         * sequences unescaped.
         */
        public String value;
        /** The left-end character that began the string. */
        public char leftDelimiter;
        /** The right-end character that ended the string. */
        public char rightDelimiter;

        /**
         * Creates the data for a string literal token.
         * 
         * @param lexeme  the token's lexeme
         */
        public StringData(String lexeme) {
            value          = lexeme.substring(1, lexeme.length() - 2);
            leftDelimiter  = lexeme.charAt(0);
            rightDelimiter = lexeme.charAt(lexeme.length() - 1);
        }
    }


    private Type     type;
    private String   lexeme;
    private Object   data;
    private Position start;
    private Position end;

    /**
     * Indicates that the lexeme passed to the constructor was invalid. This is
     * most likely caused by an invalid character.
     */
    public static class InvalidLexemeException extends Exception {
        private String lexeme;

        /**
         * Creates a new invalid lexeme exception.
         * 
         * @param lexeme  the invalid lexeme
         */
        public InvalidLexemeException(String lexeme) {
            super("Invalid token: " + lexeme);
            
            this.lexeme = lexeme;
        }

        /**
         * Gets the invalid lexeme.
         * 
         * @return the invalid lexeme.
         */
        public String getLexeme() {
            return lexeme;
        }
    }

    /**
     * Creates a new token with the specified lexeme, determining its type
     * automatically.
     * 
     * @param lexeme  the token's lexeme
     * @param start   the position of the token's first character
     * @param end     the position of the token's last character
     * 
     * @throws InvalidLexemeException
     *     if the lexeme does not represent a valid token.
     */
    public Token(String lexeme, Position start, Position end)
        throws InvalidLexemeException
    {
        this(start, end);
        
        this.type   = determineType(lexeme);
        this.lexeme = lexeme;

        switch (type) {
            case INTEGER_LITERAL:   data = new IntegerData  (lexeme); break;
            case REAL_LITERAL:      data = new RealData     (lexeme); break;
            case CHARACTER_LITERAL: data = new CharacterData(lexeme); break;
            case STRING_LITERAL:    data = new StringData   (lexeme); break;
            
            default:
                break;
        }
    }

    /**
     * Creates a new token with the specified lexeme, determining its type
     * automatically.
     * 
     * @param lexeme    the token's lexeme
     * @param position  the token's position
     *
     * @throws InvalidLexemeException
     *     if the lexeme does not represent a valid token.
     */
    public Token(String lexeme, Position position) throws InvalidLexemeException {
        this(lexeme, position, position);
    }

    /**
     * Creates a new token with the specified type. Use this form of the
     * constructor only for tokens that have no associated lexeme—particularly,
     * {@link Type#OPEN_BLOCK}, {@link Type#CLOSE_BLOCK}, and
     * {@link Type#END_OF_LINE}.
     * 
     *  @param type   the token's type
     *  @param start  the position of the token's first character
     *  @param end    the position of the token's last character
     */
    public Token(Type type, Position start, Position end) {
        this(start, end);
        
        assert type == Type.OPEN_BLOCK
            || type == Type.CLOSE_BLOCK
            || type == Type.END_OF_LINE;

        this.type   = type;
        this.lexeme = null;
    }

    /**
     * Creates a new token with the specified type. Use this form of the
     * constructor only for tokens that have no associated lexeme—particularly,
     * {@link Type#OPEN_BLOCK} and {@link Type#CLOSE_BLOCK}.
     * 
     * @param type      the token's type
     * @param position  the token's position
     */
    public Token(Type type, Position position) {
        this(type, position, position);
    }

    /**
     * Sets the token's start and end positions.
     * 
     * @param start  the position of the token's first character
     * @param end    the position of the token's last character
     */
    private Token(Position start, Position end) {
        this.start = start;
        this.end   = end;
    }
    

    private static final Pattern
        identifierRegex = Pattern.compile("[^\\W\\d_][^\\W_]*"),
        integerRegex    = Pattern.compile("\\d+"),
        realRegex       = Pattern.compile("\\d+\\.\\d+"),
        symbolRegex     = Pattern.compile("[+\\-×÷^()\\[\\]{}=≠<>≤≥.,:→←↑&]");
    /**
     * Returns the token type corresponding to the specified lexeme.
     * 
     * @param lexeme  the lexeme to use
     * 
     * @return the token type for <code>lexeme</code>
     * @throws InvalidLexemeException 
     * 
     * @throws InvalidLexemeException
     *     if <code>lexeme</code> is not a valid token
     */
    private static Type determineType(String lexeme)
        throws InvalidLexemeException
    {
        // Identifier or keyword—a letter followed by any number of letters and digits.
        if      (identifierRegex.matcher(lexeme).matches()) return isKeyword(lexeme) ? Type.KEYWORD : Type.IDENTIFIER;
        else if (integerRegex   .matcher(lexeme).matches()) return Type.INTEGER_LITERAL;
        else if (realRegex      .matcher(lexeme).matches()) return Type.REAL_LITERAL;
        else if (symbolRegex    .matcher(lexeme).matches()) return Type.SYMBOL;
        
        throw new InvalidLexemeException(lexeme);
    }

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(new String[] {
        "abstract",
        "and",
        "assures",
        "at",
        "break",
        "case",
        "catch",
        "class",
        "constant",
        "continue",
        "default",
        "each",
        "else",
        "ensures",
        "exceptions",
        "explicit",
        "finalize",
        "for",
        "function",
        "get",
        "goto",
        "if",
        "implicit",
        "in",
        "initialize",
        "invariants",
        "is",
        "not",
        "of",
        "or",
        "out",
        "parameters",
        "private",
        "property",
        "protected",
        "public",
        "record",
        "repeat",
        "requires",
        "return",
        "returns",
        "self",
        "set",
        "shared",
        "switch",
        "throw",
        "to",
        "until",
        "variables",
        "while",
        "xor",
    }));
    
    /**
     * Determines if the given identifier is a keyword.
     * 
     * @param identifier  the identifier to check
     * 
     * @return <code>true</code>  if <code>identifier</code> is a keyword,
     *         <code>false</code> if it is a regular identifier.
     */
    private static boolean isKeyword(String identifier) {
        return KEYWORDS.contains(identifier);
    }

    @Override
    public String getTokenClass() {
        switch (type) {
            case OPEN_BLOCK:        return "open block";
            case CLOSE_BLOCK:       return "close block";
            case END_OF_LINE:       return "end of line";

            case INTEGER_LITERAL:   return "integer";
            case REAL_LITERAL:      return "real number";
            case CHARACTER_LITERAL: return "character";
            case STRING_LITERAL:    return "string";

            case IDENTIFIER:        return "identifier";

            default:
                return lexeme;
        }
    }

    /**
     * Gets this token's type.
     * 
     * @return this token's type. 
     */
    public Type getType() {
        return type;
    }

    @Override
    public String getLexeme() {
        return lexeme;
    }

    /**
     * Gets the value of this integer literal.
     * 
     * @return the data object for this token.
     */
    public IntegerData integer() {
        assert type == Type.INTEGER_LITERAL;
        return (IntegerData) data;
    }

    /**
     * Gets the value of this real literal.
     * 
     * @return the data object for this token.
     */
    public RealData real() {
        assert type == Type.REAL_LITERAL;
        return (RealData) data;
    }

    /**
     * Gets the value of this character literal.
     * 
     * @return the data object for this token.
     */
    public CharacterData character() {
        assert type == Type.CHARACTER_LITERAL;
        return (CharacterData) data;
    }

    /**
     * Gets the value of a string literal, with its end quotes stripped and
     * escape sequences unescaped. <code>string().</code>{@link StringData#value value}
     * contains the contents of the string without the quotation marks and with
     * any escape sequences unescaped. {@link StringData#leftDelimiter leftDelimiter}
     * and {@link StringData#rightDelimiter rightDelimiter} are the characters
     * that begin and end the string literal.
     * <p>
     * For example, given the literal <code>"The value of x is: `</code>,
     * <code>string().value</code>          would be <code>The value of x is: </code>,
     * <code>string().leftDelimiter</code>  would be <code>"</code>, and
     * <code>string().rightDelimiter</code> would be <code>`</code>.
     * 
     * @return the data object for this token.
     */
    public StringData string() {
        assert type == Type.STRING_LITERAL;
        return (StringData) data;
    }

    @Override
    public Position getStart() {
        return start;
    }

    @Override
    public Position getEnd() {
        return end;
    }
}