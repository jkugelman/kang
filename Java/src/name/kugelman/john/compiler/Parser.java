package name.kugelman.john.compiler;

import java.io.*;
import java.util.*;

import name.kugelman.john.compiler.Grammar.*;
import name.kugelman.john.compiler.Grammar.Rule.*;
import name.kugelman.john.util.*;

/**
 * Builds an LR(1) parser from a context-free grammar. The parser generates a
 * syntax tree for valid strings and a list of error messages for invalid ones.
 * <p>
 * The generation of the parsing tables and the implementation of the parsing
 * algorithm are described in detail by most any compiler text. Traditional
 * parser generates like Yacc and Bison generate LALR parsing tables, whereas
 * this parser generates canonical LR parsing tables. LALR parsers may encounter
 * reduce/reduce conflicts that canonical parsers won't, and may also perform
 * uncalled-for reductions when an error is encountered. Canonical parsers, on
 * the other hand, usually have much larger parsing tables, but this is not as
 * important a consideration as it was twenty years ago.
 */
public class Parser {
    /**
     * Represents one action in the parser's action table, which is either to
     * shift a token, reduce by the specified rule, or accept the string and
     * return a syntax tree.
     */
    private static class Action
    {
        /**
         * The action type.
         */
        public enum Type
        {
            /** Shift the next token from the source program. */
            SHIFT,
            /** Reduce by the specified rule number. */
            REDUCE,
            /** Accept the source program. */
            ACCEPT
        }

        /** The action type. */
        public Type type;
        
        /**
         * For a shift action, the new state to push onto the stack. For a
         * reduce action, the rule to reduce by.
         */
        public Object parameter;

        private Action(Type type, Object parameter) {
            this.type      = type;
            this.parameter = parameter;
        }
        
        /**
         * Returns a new action indicating a shift into the specified state.
         * 
         * @param newState
         *            the state to push onto the stack after shifting.
         * 
         * @return a new shift action.
         */
        public static Action shift(int newState) {
            return new Action(Type.SHIFT, newState);
        }
        
        /**
         * Returns a new action indicating a reduction by the specified rule.
         * 
         * @param rule
         *            the rule to reduce by
         * 
         * @return a new reduce action.
         */
        public static Action Reduce(Rule rule) {
            return new Action(Type.REDUCE, rule);
        }
        
        /**
         * Returns a new accept action.
         * 
         * @return a new accept action.
         */
        public static Action Accept() {
            return new Action(Type.ACCEPT, null);
        }


        @Override
        public String toString() {
            String string = type.toString();

            if (parameter != null) {
                string += "(" + parameter + ")";
            }

            return string;
        }
    }
    

    /**
     * An LR(1) item, needed to determine the parser states and construct the
     * parse tables. A parse item is of the form [A → α·β, a], where A → αβ is
     * a rule in the grammar and a is a lookahead terminal.
     */
    public static class ParseItem {
        /** The rule A → αβ. */
        public final Rule rule;
        /** The position of the dot. */
        public final int position;
        /** The lookahead terminal. */
        public final Terminal lookahead;

        public ParseItem(Rule rule, int position, Terminal lookahead) {
            this.rule      = rule;
            this.position  = position;
            this.lookahead = lookahead;
        }


        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            
            builder.append(String.format("[%s →", rule.getVariable()));

            for (int i = 0; i < position; ++i) {
                builder.append(" ");
                builder.append(rule.items().get(i).getItem());
            }

            builder.append(" ·");

            for (int i = position; i < rule.items().size(); ++i) {
                builder.append(" ");
                builder.append(rule.items().get(i).getItem());
            }

            builder.append(String.format(", %s]", lookahead));

            return builder.toString();
        }

        public boolean equals(Object object) {
            if (!(object instanceof ParseItem)) {
                return false;
            }

            ParseItem item = (ParseItem) object;

            return this.rule     .equals(item.rule)
                && this.position == item.position
                && this.lookahead.equals(item.lookahead);
        }

        public int getHashCode() {
            return rule.hashCode() ^ position ^ lookahead.hashCode();
        }
    }
    
    public static class State extends HashSet<ParseItem> {
        public State() {
            super();
        }
        
        public State(Collection<ParseItem> items) {
            super(items);
        }
    }


    /** Base class for exceptions thrown by the parser. */
    public abstract class Exception extends java.lang.Exception {
        public Exception(String message) {
            super(message);
        }
        
        public void printDetails(PrintStream stream) {
            stream.println(getMessage());
        }
    }

    /**
     * Thrown when a shift/reduce conflict can't be resolved during the creation
     * of the parser.
     */
    public class ShiftReduceException extends Exception {
        /// <summary>The shift rule.</summary>
        public final Rule shiftRule;
        /// <summary>The reduce rule.</summary>
        public final Rule reduceRule;
        /// <summary>The parser state where the conflict occurs.</summary>
        public final State state;

        ShiftReduceException(Rule shiftRule, Rule reduceRule, State state) {
            super("Shift/reduce conflict encountered.");
            
            this.shiftRule  = shiftRule;
            this.reduceRule = reduceRule;
            this.state      = state;
        }

        /** Prints the conflicting rules and the state of the parser. */
        @Override
        public void printDetails(PrintStream stream) {
            stream.println("Shift/reduce conflict encountered.");
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

    /**
     * Thrown when a reduce/reduce conflict can't be resolved during the
     * creation of the parser.
     */
    public class ReduceReduceException extends Exception {
        /** The new rule. */
        public final Rule newRule;
        /** The old rule already in the parsing table. */
        public final Rule oldRule;
        /** The parser state where the conflict occurs. */
        public final State state;

        public ReduceReduceException(Rule newRule, Rule oldRule, State state) {
            super("Reduce/reduce conflict encountered.");
            
            this.newRule = newRule;
            this.oldRule = oldRule;
            this.state   = state;
        }


        /** Prints the conflicting rules and the state of the parser. */
        @Override
        public void printDetails(PrintStream stream) {
            stream.println("Reduce/reduce conflict encountered.");
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

    /**
     * When constructed, produces the action and goto tables for a grammar.
     */
    private class Tables {
        private Grammar grammar;

        /** Indexed by variables, indicates if a variable can derive ε. */
        private Map<Variable, Boolean> isNullable;
        /** Indexed by grammar items, gives the set of initial terminals. */
        private Map<Variable, Set<Terminal>> firstSets;
        /** Indexed by grammar items, gives the set of following terminals. */
        private Map<Variable, Set<Terminal>> followSets;
        /** Contains sets of parse items corresponding to parser states. */
        private Set<State> states;

        private boolean isNullable(Item item) {
            if (item instanceof Terminal) {
                return false;
            }
            
            return isNullable.get(item);
        }
        
        private Set<Terminal> firstSet(Item item) {
            if (item instanceof Terminal) {
                return Collections.singleton((Terminal) item);
            }
            
            return firstSets.get(item);
        }
        
        /**
         * The possible states the parser can be in.
         */
        public List<State> stateTable;
        
        /**
         * The action table for the parser; undefined entries indicate an error.
         * Indexed by state number and token class.
         */
        public List<Map<Terminal, Action>> actionTable;

        /**
         * The goto table for the parser; undefined entries indicate an error.
         * Indexed by state number and variable name.
         */
        public List<Map<Variable, Integer>> gotoTable;

        /**
         * Parallel to <code>actionTable</code>, gives the {@link Rule} that
         * produced each action, so that we know which two rules are in conflict
         * when we encounter any parsing conflicts.
         */
        private List<Map<Terminal, Rule>> reasonTable;
                
        
        /**
         * Constructs the parsing tables needed to parse the language specified
         * by the given LR(1) grammar.
         * 
         * @param grammar  a grammar to parse
         * 
         * @throws ShiftReduceException
         *                if there is a shift/reduce conflict the parser can't
         *                resolve.
         * @throws ReduceReduceException
         *                if there is a reduce/reduce conflict the parser can't
         *                resolve.
         */
        public Tables(Grammar grammar) throws ShiftReduceException, ReduceReduceException {
            this.grammar = grammar;

            augmentGrammar        ();
            calculateIsNullable   ();
            calculateFirstSets    ();
            calculateFollowSets   ();
            generateStates        ();
            constructParsingTables();
            unaugmentGrammar      ();
        }

        /**
         * Augments the grammar with a new start symbol with the single rule
         * "start → S", where S is the original start symbol, and with a new
         * distinguished terminal "end" that appears at the end of all programs.
         */
        @SuppressWarnings("unused")
        private void augmentGrammar() {
            Variable startSymbol = grammar.addVariable("@start");
            Terminal endTerminal = grammar.addTerminal("@end");
            
            startSymbol.addRule()
                .addVariable(grammar.getStartVariable());

            grammar.setStartVariable(startSymbol);
        }

        /** 
         * Removes the extra terminal and variable added by {@link
         * #augmentGrammar()}.
         */
        private void unaugmentGrammar() {
            grammar.variables().remove("@start");
            grammar.terminals().remove("@end");

            grammar.setStartVariable((Variable) grammar.getStartVariable().rules().get(0).items().get(0).getItem());
        }


        /**
         * Calculates whether each variable is nullable (can derive ε).
         */
        private void calculateIsNullable() {
            isNullable = new HashMap<Variable, Boolean>(grammar.variables().size());

            // Assume initially that no variables are nullable.
            for (Variable variable: grammar.variables().values()) {
                isNullable.put(variable, false);
            }

            // Keep repeating the following loop until no more variables are marked nullable.
            for (boolean changes = true; changes;) {
                changes = false;

                // Determine if each variable is nullable; a variable is nullable if any of its
                // rules consists entirely of other nullable variables.
                for (Variable variable: grammar.variables().values())
                for (Rule     rule:     variable.rules()) {
                    if (isNullable.get(variable)) {
                        continue;
                    }
                    
                    boolean allNullable = true;

                    for (Object item: rule.items()) {
                        // If the rule contains a terminal or a non-nullable variable then
                        // it's not nullable, so we're done.
                        if (item instanceof Terminal || isNullable.get(item)) {
                            allNullable = false;
                            break;
                        }
                    }
                    
                    if (allNullable) {
                        changes = true;
                        isNullable.put(variable, true);
                    }
                }
            }
        }

        /**
         * For each variable in the grammar, calculates which terminals could
         * appear at the beginning of a string derived from the variable.
         */
        private void calculateFirstSets() {
            firstSets = new HashMap<Variable, Set<Terminal>>(grammar.variables().size());

            for (Variable variable: grammar.variables().values()) {
                firstSets.put(variable, new HashSet<Terminal>());
            }

            // Keep repeating the following loop as long as something changes.
            for (boolean changes = true; changes;) {
                changes = false;

                for (Variable       variable: grammar.variables().values())
                for (Rule           rule:     variable.rules())
                for (Rule.Reference item:     rule.items()) {
                    Set<Terminal> variableFirstSet = firstSets.get(variable);
                    Set<Terminal> itemFirstSet     = firstSet(item.getItem());
                    
                    if (variableFirstSet.addAll(itemFirstSet)) {
                        changes = true;
                    }

                    if (!isNullable(item.getItem())) {
                        break;
                    }
                }
            }
        }

        /**
         * For each variable, calculates which terminals could follow the
         * variable in some valid derivation.
         */
        private void calculateFollowSets() {
            followSets = new HashMap<Variable, Set<Terminal>>(grammar.variables().size());

            for (Variable variable: grammar.variables().values()) {
                followSets.put(variable, new HashSet<Terminal>());
            }

            // Keep repeating the following loop as long as something changes.
            for (boolean changes = true; changes;)
            {
                changes = false;

                for (Variable variable: grammar.variables().values())
                for (Rule     rule:     variable.rules()) {
                    // Determine the possible terminals that can follow variable.
                    for (int i = 0; i < rule.items().size(); ++i) {
                        // The last nullable variable starting from the right side of the rule.
                        int lastNullable;

                        for (lastNullable = rule.items().size() - 1; lastNullable >= i; --lastNullable)
                        {
                            if (!isNullable(rule.items().get(lastNullable).getItem())) {
                                ++lastNullable;
                                break;
                            }
                        }

                        // Add the follow set of variable to the follow set of all of the nullable
                        // variables on the right side of the current rule.
                        for (int j = Math.max(lastNullable - 1, 0); j < rule.items().size(); ++j)
                        {
                            if (rule.items().get(j) instanceof TerminalReference) {
                                break;
                            }

                            if (followSets.get(rule.items().get(j).getItem()).addAll(followSets.get(variable))) {
                                changes = true;
                            }
                        }


                        if (rule.items().get(i) instanceof VariableReference) {
                            // If Y follows X and every variable between X and Y is nullable, add FIRST(Y)
                            // to FOLLOW(X).
                            for (int j = i + 1; j < rule.items().size(); ++j)
                            {
                                if (followSets.get(rule.items().get(i).getItem()).addAll(firstSet(rule.items().get(j).getItem()))) {
                                    changes = true;
                                }

                                if (!isNullable(rule.items().get(j).getItem()))
                                    break;
                            }
                        }
                    }
                }
            }
        }

        /**
         * Generates the set of sets of parse items that correspond to states in
         * the parsing tables.
         */
        private void generateStates() {
            Variable  startSymbol = grammar.getStartVariable();
            Terminal  endSymbol   = grammar.terminals().get("@end");
            ParseItem initialItem = new ParseItem(startSymbol.rules().get(0), 0, endSymbol);
            
            states = new HashSet<State>();

            // Start with one initial state: closure({start → ·S, end})
            states.add(closure(Collections.singleton(initialItem)));

            for (int i = 0; ; ++i) {
                Set<State> newStates = new HashSet<State>();

                for (State state: states) {
                    for (Variable symbol: grammar.variables().values()) {
                        State items = nextState(state, symbol);

                        if (!items.isEmpty()) {
                            newStates.add(items);
                        }
                    }

                    for (Terminal symbol: grammar.terminals().values()) {
                        State items = nextState(state, symbol);

                        if (!items.isEmpty()) {
                            newStates.add(items);
                        }
                    }
                }

                // If we didn't generate any new states then we're done.
                if (!states.addAll(newStates)) {
                    break;
                }
            }
        }

        /**
         * Determines the closure for the given set of parse items, which is the
         * set of possible items to be parsed. So if [A → α·Bβ, a] is an item
         * in <tt>items</tt> then [B → ·γ, a] is in the closure set for all
         * rules B → γ.
         * 
         * @param items  the initial set of parse items
         * 
         * @return the closure of <tt>items</tt>.
         */
        private State closure(Set<ParseItem> items) {
            State closureSet = new State(items);

            for (;;) {
                State newItems = new State();

                for (ParseItem item: closureSet) {
                    if (item.position >= item.rule.items().size() || !(item.rule.items().get(item.position) instanceof VariableReference)) {
                        continue;
                    }

                    Variable variable = (Variable) item.rule.items().get(item.position).getItem();

                    for (Rule     rule:     variable.rules())
                    for (Terminal terminal: first(item)) {
                        newItems.add(new ParseItem(rule, 0, terminal));
                    }
                }

                // If we didn't discover any new items then we're done.
                if (!closureSet.addAll(newItems))
                    break;
            }

            return closureSet;
        }

        /**
         * Determines the state reached after having parsed <tt>item</tt> from
         * the parse items in <tt>state</tt>.
         * 
         * @param state  a set of parse items corresponding to a parser state
         * @param item   a terminal or variable
         * 
         * @return a new set of items.
         */
        private State nextState(State state, Item item) {
            State next = new State();

            for (ParseItem parseItem: state) {
                if (parseItem.position >= parseItem.rule.items().size()) {
                    continue;
                }

                if (parseItem.rule.items().get(parseItem.position).getItem() == item) {
                    next.add(new ParseItem(
                        parseItem.rule,
                        parseItem.position + 1,
                        parseItem.lookahead
                    ));
                }
            }

            return closure(next);
        }

        /**
         * Constructs the action and goto tables to be used by the parser.
         */
        private void constructParsingTables()
            throws ShiftReduceException, ReduceReduceException
        {
            stateTable  = new ArrayList<State>                 (states);
            actionTable = new ArrayList<Map<Terminal, Action>> (states.size());
            gotoTable   = new ArrayList<Map<Variable, Integer>>(states.size());
            reasonTable = new ArrayList<Map<Terminal, Rule>>   (states.size());

            for (int i = 0; i < stateTable.size(); ++i) {
                actionTable.add(new HashMap<Terminal, Action>());
                gotoTable  .add(new HashMap<Variable, Integer>());
                reasonTable.add(new HashMap<Terminal, Rule>  ());

                State state = stateTable.get(i);

                for (ParseItem parseItem: state) {
                    if (parseItem.position < parseItem.rule.items().size()) {
                        if (parseItem.rule.items().get(parseItem.position) instanceof VariableReference) {
                            continue;
                        }

                        Terminal terminal    = (Terminal) parseItem.rule.items().get(parseItem.position).getItem();
                        Set      gotoState   = nextState(state, terminal);
                        int      stateNumber = stateTable.indexOf(gotoState);

                        if (stateNumber >= 0) {
                            addShiftAction(parseItem.rule, i, terminal, stateNumber);
                        }
                    }
                    else {
                        if (parseItem.rule.getVariable() == grammar.getStartVariable()) {
                            actionTable.get(i).put(grammar.terminals().get("@end"), Action.Accept());
                        }
                        else {
                            addReduceAction(i, parseItem.lookahead, parseItem.rule);
                        }
                    }

                    for (Variable variable: grammar.variables().values()) {
                        State gotoState   = nextState(state, variable);
                        int   stateNumber = stateTable.indexOf(gotoState);
                        
                        if (stateNumber >= 0) {
                            gotoTable.get(i).put(variable, stateNumber);
                        }
                    }
                }
            }
        }

        /**
         * Adds a shift action to the action table.
         * 
         * @param reason    the rule calling for the shift
         * @param state     the current state of the parser
         * @param terminal  the current lookahead token
         * @param newState  the new state to go to after shifting
         * 
         * @throws ShiftReduceException
         *     if the action table entry already contains a reduce action and
         *     the parser is unable to resolve the conflict.
         */
        private void addShiftAction(Rule reason, int state, Terminal terminal, int newState)
            throws ShiftReduceException
        {
            if (actionTable.get(state).containsKey(terminal)) {
                // Table already contains SHIFT.
                if (actionTable.get(state).get(terminal).type == Action.Type.SHIFT) {
                    return;
                }

                // Table already contains REDUCE. If we are to resolve in favor
                // of REDUCE then do nothing.
                if (resolveShiftReduceConflict(reason, reasonTable.get(state).get(terminal), state) == Action.Type.REDUCE) {
                    return;
                }
            }

            actionTable.get(state).put(terminal, Action.shift(newState));
            reasonTable.get(state).put(terminal, reason);
        }

        /**
         * Adds a reduce action to the action table.
         * 
         * @param state     the current state of the parser
         * @param terminal  the current lookahead token
         * @param rule      the rule to reduce by
         * 
         * @throws ShiftReduceException
         *     if the action table entry already contains a shift action and the
         *     parser is unable to resolve the conflict.
         * @throws ReduceReduceException
         *     if the action table entry already contains a different reduce
         *     action.
         */
        private void addReduceAction(int state, Terminal terminal, Rule rule)
            throws ShiftReduceException, ReduceReduceException
        {
            Action currentAction = actionTable.get(state).get(terminal);
            
            if (currentAction != null) {
                switch (currentAction.type) {
                    case SHIFT:
                        // Table already contains SHIFT. If we are to resolve in favor of SHIFT
                        // then do nothing.
                        if (resolveShiftReduceConflict(reasonTable.get(state).get(terminal), rule, state) == Action.Type.SHIFT) {
                            return;
                        }

                        break;

                    case REDUCE:
                        // Table already contains an identical REDUCE action.
                        if (currentAction.parameter == rule) {
                            break;
                        }

                        // Table already contains another REDUCE action. If we are to resolve in
                        // favor of the existing action then do nothing.
                        if (!resolveReduceReduceConflict(rule, reasonTable.get(state).get(terminal), state)) {
                            return;
                        }

                        break;
                        
                    case ACCEPT:
                        Debug.logError("Unexpected ACCEPT action in action table (state %d, terminal %s).", state, terminal);
                        break;
                }
            }

            actionTable.get(state).put(terminal, Action.Reduce(rule));
            reasonTable.get(state).put(terminal, rule);
        }

        /**
         * Tries to resolve a shift/reduce conflict by deciding whether to shift
         * or reduce. The two rules' precedence and associativity attributes are
         * used to resolve the conflict. The rule with the higher precedence is
         * favored; otherwise, when the rules have the same precedence and
         * associativity, reduction is favored if they are left-associative and
         * shift if they are right-associative.
         * 
         * @param shiftRule   the rule calling for a shift
         * @param reduceRule  the rule calling for a reduction
         * @param state       the state in which the conflict is occurring
         * 
         * @return {@link Action.Type.SHIFT}  if shifting is favored,
         *         {@link Action.Type.REDUCE} if reduction is favored.
         *         
         * @throws ShiftReduceException 
         *     if the conflict could not be resolved.
         */
        private Action.Type resolveShiftReduceConflict(Rule shiftRule, Rule reduceRule, int state)
            throws ShiftReduceException
        {
            if (shiftRule.getPrecedenceSet() != null && shiftRule.getPrecedenceSet() == reduceRule.getPrecedenceSet()) {
                if (reduceRule.getPrecedenceLevel() < shiftRule.getPrecedenceLevel()) return Action.Type.REDUCE;
                if (reduceRule.getPrecedenceLevel() > shiftRule.getPrecedenceLevel()) return Action.Type.SHIFT;

                switch (shiftRule.getAssociativity()) {
                    case LEFT:  return Action.Type.REDUCE;
                    case RIGHT: return Action.Type.SHIFT;
                    
                    case NONE:
                        break;
                }
            }
            
            throw new ShiftReduceException(shiftRule, reduceRule, stateTable.get(state));
        }

        /**
         * Tries to resolve a reduce/reduce conflict by deciding which rule to
         * reduce by.
         * 
         * @param newRule  the new rule
         * @param oldRule  the existing rule
         * @param state    the state in which the conflict is occurring
         * 
         * @return <code>true</code>  if the new rule should replace the old rule,
         *         <code>false</code> if the existing rule should stay
         * @throws ReduceReduceException 
         *         
         * @throws ReduceReduceException
         *     if the conflict could not be resolved.
         */
        private boolean resolveReduceReduceConflict(Rule newRule, Rule oldRule, int state)
            throws ReduceReduceException
        {
            throw new ReduceReduceException(newRule, oldRule, stateTable.get(state));
        }

        /**
         * For any item [A → α·Bβ, a], determines the possible first terminals
         * of the string βa.
         * 
         * @param parseItem  a parse item
         * 
         * @return the set of terminals that could appear at the front of βa.
         */
        private Set<Terminal> first(ParseItem parseItem) {
            Set<Terminal> firstSet = new HashSet<Terminal>();

            for (int i = parseItem.position + 1; i < parseItem.rule.items().size(); ++i) {
                Item item = parseItem.rule.items().get(i).getItem();

                firstSet.addAll(firstSet(item));

                if (isNullable(item)) {
                    return firstSet;
                }
            }

            firstSet.add(parseItem.lookahead);

            return firstSet;
        }
    }

    
    /**
     * The grammar defining the language accepted by the parser.
     */
    private Grammar grammar;
    
    /**
     * The action table for the parser; undefined entries indicate an error.
     * Indexed by state number and token class.
     */
    private List<Map<Terminal, Action>> actionTable;
    
    /**
     * The goto table for the parser; undefined entries indicate an error.
     * Indexed by state number and variable name.
     */
    private List<Map<Variable, Integer>> gotoTable;

    /**
     * Constructs a parser that can parse the language specified by the given
     * LR(1) grammar.
     *
     * @param grammar  a grammar to parse
     * 
     * @throws ShiftReduceException
     *     if the grammar contains a shift/reduce conflict the parser can't
     *     resolve.
     * @throws ReduceReduceException
     *     if the grammar contains a reduce/reduce conflict the parser can't
     *     resolve.
     */
    public Parser(Grammar grammar) throws ShiftReduceException, ReduceReduceException {
        Tables tables = new Tables(grammar);

        this.grammar     = grammar;
        this.actionTable = tables.actionTable;
        this.gotoTable   = tables.gotoTable;
    }


    /**
     * Thrown by the parser when it encounters a token that was not declared in
     * the grammar.
     */
    public class UnknownTokenException extends Exception {
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
    
    /**
     * Parses the file represented by the specified tokenizer and returns a
     * parse tree.
     * 
     * @param tokenizer
     *            a tokenizer reading from the source code to parse
     * 
     * @return a parse tree, or <code>null</code> if the parser encounters an
     *         error from which it cannot recover.
     *         
     * @throws UnknownTokenException
     *     if the parser encounters a token that was not declared in the
     *     grammar.
     */
    public ParseTree parse(Tokenizer tokenizer) throws UnknownTokenException {
        Stack<Integer>       stateStack  = new Stack<Integer>();                // The states reached by the parser.
        Token                token       = tokenizer.getToken();                // The current lookahead token.
        List<ParseTree.Node> parseNodes  = new ArrayList<ParseTree.Node>();     // An array of parse tree nodes.
        boolean              errorMode   = false;                               // In error recovery mode.

        // Start in state 0.
        stateStack.push(0);
        
        for (;;) {
            Terminal terminal = (token != null)
                ? grammar.terminals().get(token.getTokenClass())
                : grammar.terminals().get("@end");
            
            if (terminal == null) {
                throw new UnknownTokenException(token);
            }

            int state = stateStack.peek();

            // If we're in error mode but haven't yet shifted the @error token, pretend that
            // @error is the current token.
            if (errorMode && !tokenizer.isTransactionInProgress()) {
                terminal = grammar.getErrorTerminal();
            }
            
            // The current token is invalid in the current state; begin panic-mode error
            // recovery.
            if (!actionTable.get(state).containsKey(terminal)) {
                // When in error recovery mode, the parser attempts to perform a reduction by a
                // rule including the error token. If no reduction can be performed, it cancels
                // the attempted reduction, discards a token, and then tries again.
                if (errorMode) {
                    // Remove the states and parse nodes for the attempted reduction that just
                    // failed.
                    while (!(parseNodes.get(parseNodes.size() - 1) instanceof ParseTree.Error)) {
                        stateStack.pop();
                        parseNodes.remove(parseNodes.size() - 1);
                    }

                    tokenizer.rollbackTransaction();

                    // Now discard the current token and try again.
                    token = tokenizer.getToken();
                    tokenizer.beginTransaction();

                    // If the tokenizer ran out of tokens, and end is an invalid token, then there
                    // are no more tokens to discard and no more hope of recovering from this error.
                    // We must admit defeat.
                    if (token == null && !actionTable.get(stateStack.peek()).containsKey(grammar.terminals().get("@end"))) {
                        return null;
                    }
                }
                else
                {
                    // Find the topmost state on the stack that will accept an error token.
                    while (!actionTable.get(state).containsKey(grammar.getErrorTerminal()))
                    {
                        // Couldn't find a state on the stack with an error production. We encountered
                        // an error we couldn't recover from, so we can only return null indicating
                        // complete failure.
                        if (stateStack.size() <= 1) {
                            return null;
                        }

                        stateStack.pop();
                        parseNodes.remove(parseNodes.size() - 1);

                        state = stateStack.peek();
                    }

                    errorMode = true;
                }

                continue;
            }
            
            Action action = actionTable.get(state).get(terminal);
            
            switch (action.type) {
                case SHIFT:
                    ParseTree.Node node;

                    if (terminal == grammar.getErrorTerminal()) {
                        // The terminals that would have been valid at this point, minus one because the
                        // @error token will be left out.
                        Collection<Terminal> expectedTerminals = new ArrayList<Terminal>();

                        expectedTerminals.addAll(actionTable.get(state).keySet());
                        expectedTerminals.remove(grammar.getErrorTerminal());
                        
                        node = new ParseTree.Error(token, expectedTerminals);
                    }
                    else {
                        node = new ParseTree.Terminal(token);
                    }

                    stateStack.push((Integer) action.parameter);    // Find the new state.
                    parseNodes.add (node);                          // Add a new node to the derivation tree.

                    if (node instanceof ParseTree.Terminal) {
                        token = tokenizer.getToken();
                    }
                    else {
                        tokenizer.beginTransaction();
                    }

                    break;
                
                case REDUCE:
                    Rule rule = (Rule) action.parameter;

                    // End error recovery mode.
                    if (rule.isErrorRule()) {
                        errorMode = false;
                        tokenizer.commitTransaction();
                    }

                    for (int i = 0; i < rule.items().size(); ++i)
                        stateStack.pop();

                    // Reduce the nodes at the end of the program to a single variable node.                        
                    int                  start       = parseNodes.size() - rule.items().size();
                    List<ParseTree.Node> nodes       = collapse(rule, parseNodes.subList(start, start + rule.items().size()));
                    ParseTree.Variable   replacement = new ParseTree.Variable(rule, nodes, tokenizer.getPosition());
                    
                    for (int i = 0; i < rule.items().size(); ++i) {
                        parseNodes.remove(start);
                    }
                    
                    parseNodes.add(replacement);
                    
                    // Find the new state and push it onto the stack.
                    stateStack.push(gotoTable.get(stateStack.peek()).get(rule.getVariable()));

                    break;

                case ACCEPT:
                    // Verify that there's only one node in parseNodes, the start symbol.
                    assert parseNodes.size() == 1;
                    assert ((ParseTree.Variable) parseNodes.get(0)).getRule().getVariable() == grammar.getStartVariable();

                    return new ParseTree((ParseTree.Variable) parseNodes.get(0));
            }
        }
    }

    /**
     * Replaces any collapsible {@link ParseTree.Variable}s in
     * <code>nodes</code> with their child nodes and removes any discardable
     * {@link ParseTree.Terminal}s.
     * 
     * @param rule   the rule being reduced by
     * @param nodes  a list of replacement nodes to collapse
     * 
     * @return a new list of nodes with the collapsible nodes collapsed.
     */
    private List<ParseTree.Node> collapse(Rule rule, List<ParseTree.Node> nodes) {
        nodes = new ArrayList<ParseTree.Node>(nodes);

        for (int i = nodes.size() - 1; i >= 0; --i) {
            if (nodes.get(i) instanceof ParseTree.Terminal) {
                if (!((TerminalReference) rule.items().get(i)).isPreserved())
                    nodes.remove(i);
            }
            else if (nodes.get(i) instanceof ParseTree.Variable) {
                ParseTree.Variable variableNode = (ParseTree.Variable) nodes.get(i);

                if (((VariableReference) rule.items().get(i)).getVariable().isCollapsible()) {
                    nodes.addAll(i + 1, variableNode.getChildren());
                    nodes.remove(i);
                }
            }
        }

        return nodes;
    }
}