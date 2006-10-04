package name.kugelman.john.compiler;

import java.io.*;
import java.util.*;

/**
 * Base class for an object that extracts and returns tokens from some token
 * source. A token source is usually a source code file, but as far as the
 * tokenizer is concerned it is just an abstract entity from which tokens are
 * extracted.
 * <p>
 * The tokenizer supports transactions, whereby the {@link Parser} can back up
 * and restart from some prior point in the token stream if it needs to.
 */
public abstract class Tokenizer {
    /** Tokens extracted during a transaction. */
    private List<Token> extractedTokens;
    
    /** The index into {@link extractedTokens} of the current token. */
    private int tokenPosition;

    /**
     * For each transaction in progress, the position in {@link extractedTokens}
     * of the initial token.
     */
    private Stack<Integer> transactionMarkers;

    /**
     * Initializes the tokenizer.
     */
    protected Tokenizer() {
        extractedTokens    = new ArrayList<Token>();
        tokenPosition      = 0;
        transactionMarkers = new Stack<Integer>();
    }

    /**
     * Extracts and returns a token from the token source.
     * 
     * @return a token from the token source, or <code>null</code> if there
     *         are no tokens left.
     */
    protected abstract Token extractToken();

    /**
     * Gets a token from the token source, calling {@link #extractToken()} as
     * needed. Not every call to <code>getToken()</code> will necessarily
     * result in a call to <code>extractToken()</code> if there have been
     * rolled-back transactions.
     * 
     * @return a token from the token source, or <code>null</code> if there
     *         are no tokens left
     */
    public Token getToken() {
        if (tokenPosition < extractedTokens.size()) {
            return extractedTokens.get(tokenPosition++);
        }
        else {
            Token token = extractToken();

            // If there's a transaction in progress, store the token in extractedTokens.
            if (isTransactionInProgress()) {
                extractedTokens.add(token);
                tokenPosition += 1;
            }

            return token;
        }
    }

    /**
     * Represents a position in the token source. 
     */
    public abstract class Position {
    }
    
    /**
     * Represents a line number and column in a particular source file. Lines
     * and columns are numbered starting from 0, not 1.
     */
    public class FilePosition extends Position {
        private File file;
        private int  line, column;

        /**
         * Creates a reference to a position in a source file.
         * 
         * @param file    the source file
         * @param line    a line number in the source file
         * @param column  a column number in the source file
         */
        public FilePosition(File file, int line, int column) {
            this.file   = file;
            this.line   = line;
            this.column = column;
        }

        /**
         * Gets the source file.
         * 
         * @return the source file
         */
        public File getFile() {
            return file;
        }

        /**
         * Gets the line number in the source file. The first line in the file
         * is 0, not 1.
         * 
         * @return the line number
         */
        public int getLine() {
            return line;
        }
        
        /**
         * Gets the column number in the source file. The first column in the
         * file is 0, not 1.
         * 
         * @return the column number
         */
        public int getColumn() {
            return column;
        }
    }

    /**
     * Gets the current position of the tokenizer.
     * 
     * @return the tokenizer's position
     */
    protected abstract Position getPosition();

    
    /**
     * Begins a new transaction. When a transaction is initiated, the tokenizer
     * remembers what state it is in. If the transaction is later rolled back,
     * the tokenizer restores that state, acting as if the tokens returned since
     * the start of the transaction had never been extracted. If the transaction
     * is committed, the tokenizer forgets about its past state.
     * <p>
     * Transactions can be nested by calling <code>beginTransaction()</code>
     * multiple times.
     */
    public void beginTransaction() {
        transactionMarkers.push(tokenPosition);
    }

    /**
     * Commits the current transaction. The transaction is considered successful
     * and the option to rollback to the start is lost.
     */
    public void commitTransaction() {
        transactionMarkers.pop();

        // Clear the tokens from extractedTokens if this was the last transaction.
        if (!isTransactionInProgress()) {
            extractedTokens.clear();
            tokenPosition = 0;
        }
    }

    /**
     * Rolls back the current transaction, restoring the tokenizer to the state
     * it was in when the transaction was begun. Any tokens returned by the
     * tokenizer after the start of the transaction will be returned again in
     * subsequent calls to {@link #getToken()}.
     */
    public void rollbackTransaction() {
        tokenPosition = transactionMarkers.pop();
    }

    /**
     * Gets the number of nested transactions in progress. This is equal to the
     * number of calls to {@link #beginTransaction()} minus the number of calls
     * to {@link #commitTransaction()} and {@link #rollbackTransaction()}.
     * 
     * @return the number of transactions in progress
     */
    public int getTransactionsInProgress() {
        return transactionMarkers.size();
    }

    /**
     * Checks if there is a transaction in progress.
     * 
     * @return a boolean indicating if there are one or more transactions in
     *         progress
     */
    public boolean isTransactionInProgress() {
        return !transactionMarkers.isEmpty();
    }

}

