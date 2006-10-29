package name.kugelman.john.compiler;

import java.io.*;

import name.kugelman.john.compiler.Grammar.*;
import name.kugelman.john.compiler.Parser.*;

/**
 * Thrown when a shift/reduce conflict can't be resolved during the creation
 * of the parser.
 */
public class ShiftReduceException extends ParserException {
    /** The shift rule. */
    public final Rule shiftRule;
    /** The reduce rule. */
    public final Rule reduceRule;
    /** The parser state where the conflict occurs. */
    public final State state;

    public ShiftReduceException(Rule shiftRule, Rule reduceRule, State state) {
        super("Unable to resolve shift/reduce conflict.");
        
        this.shiftRule  = shiftRule;
        this.reduceRule = reduceRule;
        this.state      = state;
    }

    /** Prints the conflicting rules and the state of the parser. */
    @Override
    public void printDetails(PrintStream stream) {
        stream.println("Unable to resolve shift/reduce conflict.");
        stream.println();
        stream.printf ("Shift rule:  %s%n", shiftRule);
        stream.printf ("Reduce rule: %s%n", reduceRule);
        stream.println();
        stream.println("Parser state:");

        for (ParseItem parseItem: state) {
            stream.printf("    %s%n", parseItem);
        }
    }
}