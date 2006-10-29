package name.kugelman.john.compiler;

import java.io.*;

import name.kugelman.john.compiler.Grammar.*;
import name.kugelman.john.compiler.Parser.*;

/**
 * Thrown when a reduce/reduce conflict can't be resolved during the
 * creation of the parser.
 */
public class ReduceReduceException extends ParserException {
    /** The new rule. */
    public final Rule newRule;
    /** The old rule already in the parsing table. */
    public final Rule oldRule;
    /** The parser state where the conflict occurs. */
    public final State state;

    public ReduceReduceException(Rule newRule, Rule oldRule, State state) {
        super("Unable to resolve reduce/reduce conflict.");
        
        this.newRule = newRule;
        this.oldRule = oldRule;
        this.state   = state;
    }


    /** Prints the conflicting rules and the state of the parser. */
    @Override
    public void printDetails(PrintStream stream) {
        stream.println("Unable to resolve reduce/reduce conflict.");
        stream.println();
        stream.printf ("Rule #1: %s%n", oldRule);
        stream.printf ("Rule #2: %s%n", newRule);
        stream.println();
        stream.println("Parser state:");

        for (ParseItem parseItem: state) {
            stream.printf("    %s%n", parseItem);
        }
    }
}