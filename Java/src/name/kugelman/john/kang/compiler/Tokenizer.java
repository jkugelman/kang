package name.kugelman.john.kang.compiler;

import java.io.*;
import java.util.*;

import name.kugelman.john.kang.compiler.Log.Error;

/**
 * Extracts tokens from a Kang source file.
 */
public class Tokenizer extends name.kugelman.john.compiler.Tokenizer {
    private Log log;
    
    /** The source file to read from, or null when end-of-file is reached. */
    private PushbackReader sourceFile;
    /** The current character. */
    private char ch;
    /** The tokens that have been extracted but not yet returned. */
    private Queue<Token> tokens;

    /** The current line number. */
    private int line;
    /** The current column. */
    private int column;
    
    /** Is this the first token on the line? */
    private boolean isFirstToken;
    /** Did we just see an ellipsis at the end of this line? */
    private boolean justSawEllipsis;
    
    /** Indentation level for each nested block. */
    private Stack<Integer> blockLevels;


    /**
     * Creates a tokenizer that extracts tokens from a source file.
     * 
     * @param sourceFile  a source file
     * @param log         the log to write messages to
     * 
     * @throws IOException
     *     if there's an error reading from the source file
     */
    public Tokenizer(Reader sourceFile, Log log) throws IOException {
        this.log = log;
        
        // "Prime" the source file by reading the first character.
        int ch = sourceFile.read();
    
        if (ch >= 0) {
            this.sourceFile = new PushbackReader(sourceFile);
            this.ch         = (char) ch;

            this.line       = 0;
            this.column     = 0;
        }
        else {
            this.sourceFile = null;
        }
            
        tokens       = new LinkedList<Token>();
        isFirstToken = true;

        blockLevels  = new Stack<Integer>();
        blockLevels.push(column);
    }
    

    @Override
    public Position getPosition() {
        return new FilePosition(null, line, column);
    }


    /**
     * Extracts a token from the Kang source file.
     * 
     * @return the next token from the file, or <code>null</code> if there are
     *         no tokens left.
     *         
     * @throws IOException
     *     if there's an error reading from the source file 
     */
    @Override
    protected Token extractToken() throws IOException {
        // Loop until we extract a token or hit end-of-input.
        for (;;) {
            if (!endOfInput()) {
                skipWhiteSpace  ();
                updateBlockLevel();
            }

            if (!endOfInput()) {            
                // Try to extract a token from the source file.
                if      (Character.isLetter(ch)) readIdentifierOrKeyword();
                else if (Character.isDigit (ch)) readNumber();
                else                             readSymbol();
            }
        
            // When out of tokens, end the line and close any open blocks.
            if (endOfInput()) {
                if (!isFirstToken) {
                    tokens.add(new Token(Token.Type.END_OF_LINE, getPosition()));
                    isFirstToken = true;
                }

                // Close any open blocks.
                while (blockLevels.size() > 1) {
                    blockLevels.pop();
                    tokens.add(new Token(Token.Type.CLOSE_BLOCK, getPosition()));
                }
            }
            
            if (!tokens.isEmpty()) {
                return tokens.remove();
            }
            
            if (endOfInput()) {
                return null;
            }
        }
    }
    

    /**
     * Gets a single character from the input file and stores it in {@link ch},
     * updating the cursor position in the process.
     * 
     * @return the previous character stored in <code>ch</code>.
     * 
     * @throws IOException
     *     if there's an error reading from the source file
     */
    private char getChar() throws IOException {
        return getChar(false);
    }

    /**
     * Gets a single character from the input file and stores it in {@link ch},
     * updating the cursor position in the process.
     * 
     * @param multiLineToken
     *     <code>true</code> if the lexer is currently parsing a multi-line
     *     token, like a string literal, in which case it will not change
     *     {@link isFirstToken}
     *     
     * @return the previous character stored in <code>ch</code>.
     * 
     * @throws IOException
     *     if there's an error reading from the source file
     */
    private char getChar(boolean multiLineToken) throws IOException {
        assert !endOfInput();

        int  tabSize = 8;
        
        char oldCh   = this.ch;
        int  newCh   = sourceFile.read();
        
        switch (oldCh) {
            case '\n':
                if (!multiLineToken && !justSawEllipsis && !isFirstToken) {
                    tokens.add(new Token(Token.Type.END_OF_LINE, getPosition()));
                    isFirstToken = true;
                }
                           
                justSawEllipsis = false;

                line  += 1;
                column = 0;
                
                break;

            case '\t':
                column += tabSize; 
                column -= column % tabSize;

                break;

            default:
                column += 1;
                break;
        }

        switch (newCh) {
            // End-of-input.
            case -1:
                sourceFile = null;
                this.ch    = '\u0000';
                
                break;
                
            default:
                this.ch = (char) newCh;
                break;
        }
        
        return oldCh;
    }
    
    
    private boolean endOfInput() {
        return sourceFile == null;
    }
    
    
    /**
     * Skips white space and comments between tokens.
     */
    private void skipWhiteSpace() throws IOException {
        for (;;) {
            while (!endOfInput() && (Character.isWhitespace(ch) || ch == '…')) {
                if (ch == '…') {
                    if (justSawEllipsis) {
                        log.write(Error.ELLIPSIS_NOT_AT_END_OF_LINE);
                    }

                    justSawEllipsis = true;
                }

                getChar();
            }

            // Exit the loop if this character is not a comment.
            int     nextCh    = sourceFile.read();
            boolean isComment = (ch == '-' && nextCh == '-');
            
            sourceFile.unread(nextCh);
            
            if (!isComment) {
                break;
            }
            
            // Skip over the comment.
            do {
                getChar();
            }
            while (!endOfInput() && ch != '\n');
        }

        if (!endOfInput() && justSawEllipsis) {
            log.write(Error.ELLIPSIS_NOT_AT_END_OF_LINE);
            justSawEllipsis = false;
        }
    }
    
    /**
     * If the indentation level has changed, inserts open or close block tokens
     * into the token queue.
     */
    private void updateBlockLevel() {
        if (!isFirstToken) {
            return;
        }
    
        if (column > blockLevels.peek()) {
            // Open a new block.
            blockLevels.push(column);
            tokens.add(new Token(Token.Type.OPEN_BLOCK, getPosition()));
        }
        else {
            // Close blocks until reaching the current indentation level.
            while (column < blockLevels.peek()) {
                blockLevels.pop();
                tokens.add(new Token(Token.Type.CLOSE_BLOCK, getPosition()));
            }
        }
    }
    
    
    private void readIdentifierOrKeyword() throws IOException {
        Position start  = getPosition();
        Position end;
        String   lexeme = "";
    
        // Read in the entire name.
        do {
            end     = getPosition();
            lexeme += getChar();
        }
        while (!endOfInput() && Character.isLetterOrDigit(ch));
            
        tokens.add(new Token(lexeme, start, end));
        isFirstToken = false;
    }

    private void readNumber() throws IOException {
        Position start  = getPosition();
        Position end;
        String   lexeme = "";
        
        do {
            end     = getPosition();
            lexeme += getChar();
        }
        while (!endOfInput() && Character.isDigit(ch));
        
        // Decimal point followed by at least one digit.
        int     nextCh    = sourceFile.read();
        boolean isDecimal = (ch == '.' && nextCh != -1 && Character.isDigit((char) nextCh));
        
        sourceFile.unread(nextCh);
        
        if (isDecimal) {
            lexeme += getChar();
            
            do {
                end     = getPosition();
                lexeme += getChar();
            }
            while (!endOfInput() && Character.isDigit(ch));
        }
        
        // Invalid number.
        if (Character.isLetter(ch) || ch == '.') {
            String invalidPart = "";

            // Read in all of the invalid characters.
            do {
                end          = getPosition();
                invalidPart += getChar();
            }
            while (Character.isLetterOrDigit(ch) || ch == '.');
        
            log.write(Error.INVALID_NUMBER, lexeme + invalidPart);
        }
        
        tokens.add(new Token(lexeme, start, end));
        isFirstToken = false;
    }
    
    private void readSymbol() throws IOException {
        try {
            // Try to create new token with this character, which will throw an exception if
            // the character is bad.
            tokens.add(new Token(Character.toString(ch), getPosition()));
        }
        catch (Token.InvalidLexemeException exception) {
            log.write(Error.INVALID_CHARACTER, ch);
        }

        getChar();
        isFirstToken = false;
    }
}