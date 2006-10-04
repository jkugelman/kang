package name.kugelman.john.compiler;

import java.util.*;

/**
 * Represents a program that has been parsed via a {@link Grammar} in tree form,
 * with each node being either a terminal (one {@link Token} from the source
 * code) or a variable (a non-terminal like "expression" or "statement").
 * <p>
 * Alternatively, thnk of the parse tree as a sequence of derivations that
 * generate a string in the grammar. The parse tree specifies the proper
 * sequence of rule replacement to generate a program starting from the start
 * symbol.
 */
public class ParseTree {
    /**
     * The base class for the different types of nodes that can appear in the
     * parse tree.
     * 
     * @see Grammar.Item
     */
    public abstract class Node {
        /**
         * Gets the node that has this node as a child.
         * 
         * @return this node's parent, or <code>null</code> if it has none
         */
        public abstract Node getParent();

        /**
         * Gets the child nodes of this node. The default implementation returns
         * an empty list.
         * 
         * @return this node's children
         */
        public List<Node> getChildren() {
            return Collections.emptyList();
        }

        /**
         * Gets the position of the start of this node in its source file.
         * 
         * @return this node's start position
         */
        public abstract Position getStart();

        /**
         * Gets the position of the end of this node in its source file.
         * 
         * @return this node's end position
         */
        public abstract Position getEnd();


        /**
         * Determines if this node was parsed successfully or not. The default
         * implementation returns <code>false</code> only if all of the node's
         * children are error-free.
         * 
         * @return <code>true</code>  if this node contains an error,
         *         <code>false</code> if it and all of its children were parsed successfully
         */
        public boolean hasError() {
            for (Node node: getChildren()) {
                if (node.hasError()) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Represents a terminal, or token, in the parse tree.
     * 
     * @see Token
     * @see Grammar.Terminal
     */
    public class Terminal extends Node {
        private Token token;

        /**
         * Creates a new terminal node.
         * 
         * @param token  the token that was matched with this terminal
         */
        public Terminal(Token token) {
            this.token = token;
        }

        
        /**
         * Gets the token that was matched with this terminal.
         * 
         * @return this terminal's token
         */
        public Token getToken() {
            return token;
        }

        /**
         * Gets the position of the first character of the terminal. This is the
         * same as the start of the token.
         */
        public Position getStart() {
            return token.getStart();
        }

        /**
         * Gets the position of the last character of the terminal. This is the
         * same as the end of the token.
         */
        public Position getEnd() {
            return token.getEnd();
        }
    }


    /**
     * Represents a variable replacement in the parse tree.
     * 
     * @see Grammar.Variable
     */
    public class Variable extends Node
    {
        private Grammar.Rule rule;
        private List<Node>   children;
        private Position     tokenizerPosition;
        
        /**
         * Creates a new variable node.
         * 
         * @param rule
         *            the rule used in the replacement
         * @param children
         *            the nodes this variable was replaced by
         * @param tokenizerPosition
         *            the position of the tokenizer when the variable node was
         *            created
         */
        public Variable(Grammar.Rule rule, List<Node> children, Position tokenizerPosition) {
            this.rule              = rule;
            this.children          = new ArrayList<Node>(children);
            this.tokenizerPosition = tokenizerPosition;
        }

        /**
         * Gets the rule that was used in the replacement.
         * 
         * @return the rule used in the replacement
         */
        public Grammar.Rule getRule() {
            return rule;
        }

        /**
         * Gets the terminals and variables this variable was replaced by.
         * 
         * @return the nodes this variable was replaced by
         */
        public List<Node> getChildren() {
            return Collections.unmodifiableList(children);
        }

        /**
         * Gets the start position of this variable, which is the start position
         * of the first token in its parse tree.
         */
        public Position getStart() {
            if (children.isEmpty()) {
                return tokenizerPosition;
            }
            
            return children.get(0).getStart();
        }
        
        /**
         * Gets the end position of this variable, which is the end position of
         * the last token in its parse tree.
         */
        public Position getEnd() {
            if (children.isEmpty()) {
                return tokenizerPosition;
            }
            
            return children.get(children.size() - 1).getEnd();
        }
    }

    /**
     * Represents an error in the parse tree.
     * 
     * @see Grammar.Rule.ErrorReference
     * @see Grammar#getErrorTerminal()
     */
    public class Error extends Node {
        private Token                        token;
        private Collection<Grammar.Terminal> expectedTerminals;

        /**
         * Creates a new error node.
         * 
         * @param token
         *            the token at which the error was discovered
         * @param expectedTerminals
         *            the terminals that were expected at the point of the error
         */
        public Error(Token token, Collection<Grammar.Terminal> expectedTerminals) {
            this.token             = token;
            this.expectedTerminals = new ArrayList<Grammar.Terminal>(expectedTerminals);
        }

        /**
         * Gets the token at which the error was discovered.
         * 
         * @return the token at which the error was discovered
         */
        public Token getToken() {
            return token;
        }

        /**
         * Gets the terminals that were expected at the point of the error.
         * 
         * @return the expected terminals
         */
        public Collection<Grammar.Terminal> getExpectedTerminals() {
            return Collections.unmodifiableCollection(expectedTerminals);
        }

        /**
         * Gets the position at which the error was discovered.
         * 
         * @return where the error was discovered
         */
        public Position getPosition() {
            return token.getStart();
        }

        
        public boolean hasError() {
            return true;
        }

        public Position getStart() {
            return getPosition();
        }
        
        public Position getEnd() {
            return getPosition();
        }
    }


    private Variable root;

    /**
     * Creates a parse tree with <code>root</code> at the top of the tree,
     * corresponding to the start symbol of the grammar.
     * 
     * @param root
     *            the root of the parse tree
     */
    public ParseTree(Variable root) {
        this.root = root;
    }

    /**
     * Gets the root node of the tree, which corresponds to the start symbol in
     * the language grammar.
     * 
     * @return the root node
     * 
     * @see Grammar#getStartVariable()
     */
    public Variable getRoot() {
        return root;
    }
}
