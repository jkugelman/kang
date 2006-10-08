package name.kugelman.john.compiler;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.xml.*;
import javax.xml.parsers.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;

import name.kugelman.john.util.*;

import org.jaxen.*;
import org.jaxen.jdom.*;
import org.jdom.*;
import org.jdom.input.*;
import org.xml.sax.*;

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
    public abstract class Item {
    }

    /**
     * Represents a terminal symbol, which corresponds to a token returned by
     * the lexer. Terminals are the atomic units of text that make up a source
     * program.
     */
    public class Terminal extends Item {
        private Object  tokenClass;
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
        Terminal(Object tokenClass, boolean isDiscardable) {
            this.tokenClass    = tokenClass;
            this.isDiscardable = isDiscardable;
        }
        
        /**
         * Gets the token class associated with this terminal.
         * 
         * @return the token class associated with this terminal
         */
        public Object getTokenClass() {
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
            String tokenClass = this.tokenClass.toString();
            
            return Pattern.matches("^[\\w\\s]+$", tokenClass)
                 ?       tokenClass
                 : "'" + tokenClass + "'";
        }
    }
    
    /**
     * Represents a variable (or non-terminal), which has associated rules (or
     * productions) that determine the possible derivations in the grammar.
     */
    public class Variable extends Item {
        private String     name;
        private List<Rule> rules;
        private Rule       parentRule;
        
        Variable(String name, Rule parentRule) {
            this.name       = name;
            this.rules      = new ArrayList<Rule>();
            this.parentRule = parentRule;
        }
        
        /**
         * Gets this variable's name.
         * 
         * @return this variable's name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Gets the possible replacement rules for this variable.
         * 
         * @return this variable's rules
         */
        public List<Rule> rules() {
            return Collections.unmodifiableList(rules);
        }
        
        /**
         * Adds a new replacement rule to this variable's list of possible
         * rules. New rules by default have no items on the right-side and will
         * look like "<i>variable</i> → ε".
         * 
         * @return the new {@link Rule}
         */
        public Rule addRule() {
            Rule rule = new Rule(this);
            
            rules.add(rule);
            
            return rule;
        }

        /**
         * Gets the variable's parent rule. For the auxiliary variables created
         * by extended grammar items like <tt>&lt;optional&gt;</tt> and
         * <tt>&lt;repeat&gt;</tt>, this is the containing rule.
         * 
         * @return the variable's parent rule, if it is an auxiliary variable
         *         created internally to implemented an extended grammar
         *         construct
         */
        public Rule getParentRule() {
            return parentRule;
        }

        /**
         * If <code>true</code>, the variable is collapsed after being parsed,
         * which means the replacement items are inserted in the parse tree
         * where the variable normally would be.
         * 
         * @return a boolean indicating if the variable is collapsible
         */
        public boolean isCollapsible() {
            return parentRule != null;
        }
        
        
        /**
         * Returns this variable's name.
         */
        public String toString() {
            return name;
        }
    }
    
    /**
     * This is the object that will be returned by {@link
     * Rule.ErrorReference#getItem()}.
     */
    public class Error extends Item {
        Error() {
        }
        
        public String toString() {
            return "<error>";
        }
    }
    
    /**
     * The associativity of a {@link Rule}, which allows the parser to resolve
     * certain ambiguities in a grammar.
     * <p>
     * Java does not allow enums to be defined inside of a non-static inner
     * class, so this enum is here instead of within <code>Rule</code>. 
     */
    public enum Associativity {
        /** Non-associative rule. */
        NONE,
        /** Left-associative rule. */
        LEFT,
        /** Right-associative rule. */
        RIGHT
    }

    /**
     * A single production "variable → variables and terminals".
     */
    public class Rule {
        /**
         * Reference to a terminal or variable in the grammar. A separate class
         * from {@link Grammar.Item} is needed to attach attributes to each
         * reference.
         */
        public abstract class Reference {
            /**
             * Gets the terminal or variable being referenced.
             *
             * @return the referenced item
             */
            public abstract Item getItem();
        }

        /**
         * Reference to a terminal in a production rule.
         */
        public class TerminalReference extends Reference {
            private Terminal terminal;
            private boolean  isPreserved;

            TerminalReference(Terminal terminal, boolean isPreserved) {
                this.terminal    = terminal;
                this.isPreserved = isPreserved;

            }


            /**
             * Gets the terminal referred to as part of a production rule.
             * 
             * @return the referenced terminal
             */
            public Terminal getTerminal() {
                return terminal;
            }

            public Item getItem() {
                return terminal;
            }

            /**
             * If <code>false</code>, the terminal is discarded after being
             * parsed and is not added to the parse tree.
             * 
             * @return a boolean indicated if the terminal is to be preserved in
             *         the parse tree
             */
            public boolean isPreserved() {
                return isPreserved;
            }
        }



        /**
         * Reference to a variable in a production rule.
         */
        public class VariableReference extends Reference {
            private Variable variable;

            /**
             * Creates a new variable reference as part of a production rule.
             * 
             * @param variable  the variable in the rule
             */
            VariableReference(Variable variable) {
                this.variable = variable;
            }


            /**
             * Gets the variable referred to as part of a production rule.
             * 
             * @return the referenced variable
             */
            public Variable getVariable() {
                return variable;
            }

            public Item getItem() {
                return variable;
            }
        }



        /**
         * Reference to an <tt>&lt;error/&gt;</tt> production in a rule.
         */
        public class ErrorReference extends Reference {
            /**
             * Creates a new error reference.
             */
            public ErrorReference() {
            }

            public Item getItem() {
                return errorInstance;
            }

        }

        
        private Variable        variable;
        private List<Reference> items;

        private Integer         precedenceSet;
        private Integer         precedenceLevel;
        private Associativity   associativity;

        Rule(Variable variable) {
            this.variable          = variable;
            this.items             = new ArrayList<Reference>();

            this.precedenceSet     = null;
            this.precedenceLevel   = null;
            this.associativity     = Associativity.NONE;
        }


        /**
         * Gets the original variable on the left side of the rule.
         * 
         * @return the original variable
         */
        public Variable getVariable() {
            return variable;
        }
        
        /**
         * Gets the replacement variables and terminals on the right side of the
         * rule.
         * 
         * @return the items on the right side of the rule
         */
        public List<Reference> items() {
            return Collections.unmodifiableList(items);
        }
        
        /**
         * Adds a terminal to the end of the rule.
         * 
         * @param terminal  the terminal in the rule
         * 
         * @return a new {@link TerminalReference}
         */
        public TerminalReference addTerminal(Terminal terminal) {
            return addTerminal(terminal, true);
        }
        
        /**
         * Adds a terminal to the end of the rule. The terminal will be
         * discarded during parsing if <code>isPreserved</code> is
         * <code>false</code>.
         * 
         * @param terminal     the terminal in the rule
         * @param isPreserved  <code>true</code>  to add the terminal to the parse tree,
         *                     <code>false</code> to discard it
         * 
         * @return a new {@link TerminalReference}
         */
        public TerminalReference addTerminal(Terminal terminal, boolean isPreserved) {
            TerminalReference reference = new TerminalReference(terminal, isPreserved);
            
            items.add(reference);
            return reference;
        }

        /**
         * Adds a variable to the end of the rule.
         *  
         * @param variable  the variable in the rule
         * 
         * @return a new {@link VariableReference}
         */
        public VariableReference addVariable(Variable variable) {
            VariableReference reference = new VariableReference(variable);
            
            items.add(reference);
            return reference;
        }
        
        /**
         * Adds a special error marker to the end of the rule. When the parser
         * encounters a parsing error it will add an error marker to the token
         * stream and then look for rules with error markers in them so it can
         * recover from the error and continue parsing.
         * 
         * @return a new {@link ErrorReference}
         */
        public ErrorReference addError() {
            ErrorReference reference = new ErrorReference();
            
            items.add(reference);
            return reference;
        }
        
        
        /**
         * Gets this rule's precedence set. Only rules belonging to the same
         * precedence set can be compared.
         * 
         * @return this rule's precedence set, or <code>null</code>
         */
        public Integer getPrecedenceSet() {
            // Use the precedence of the parent rule.
            if (variable.getParentRule() != null) {
                return variable.getParentRule().getPrecedenceSet();
            }

            return precedenceSet;
        }

        /**
         * Sets this rule's precedence set.
         * 
         * @param precedenceSet  this rule's precedence set, or
         *                       <code>null</code>
         */
        public void setPrecedenceSet(Integer precedenceSet) {
            this.precedenceSet = precedenceSet;
        }

        /**
         * Gets this rule's precedence level.
         * 
         * @return this rule's precedence level, or <code>null</code>
         */
        public Integer getPrecedenceLevel() {
            // Use the precedence of the parent rule.
            if (variable.getParentRule() != null) {
                return variable.getParentRule().getPrecedenceLevel();
            }

            return precedenceLevel;
        }

        /**
         * Sets this rule's precedence level.
         * 
         * @param precedenceLevel  this rule's precedence level, or
         *                         <code>null</code>
         */
        public void setPrecedenceLevel(Integer precedenceLevel) {
            this.precedenceLevel = precedenceLevel;
        }

        /**
         * Gets this rule's associativity.
         * 
         * @return this rule's associativity
         */
        public Associativity getAssociativity() {
            // Use the associativity of the parent rule.
            if (variable.getParentRule() != null) {
                return variable.getParentRule().getAssociativity();
            }

            return associativity;
        }

        /**
         * Sets this rule's associativity.
         * 
         * @param associativity  this rule's associativity
         */
        public void setAssociativity(Associativity associativity) {
            this.associativity = associativity;
        }


        /**
         * Does this rule contain an {@link ErrorReference}?
         * 
         * @return a boolean indicating if this is an error production
         */
        public boolean isErrorRule() {
            for (Reference reference: items) {
                if (reference instanceof ErrorReference) {
                    return true;
                }
            }

            return false;
        }


        /**
         * Returns this rule as a string like "statement → expression ';'".
         * 
         * @return this rule as a string
         */
        public String toString() {
            StringBuilder builder = new StringBuilder();
            
            builder.append(variable);
            builder.append(" →");

            if (items.size() == 0) {
                builder.append(" ε");
            }
            else {
                for (Reference reference: items) {
                    builder.append(" ");
                    builder.append(reference.getItem());
                }
            }

            return builder.toString();                              
        }
    }
    
    
    private Map<String, Terminal> terminals;
    private Map<String, Variable> variables;
    private Variable              startVariable;
            Error                 errorInstance;
    
    public Grammar() {
        this.terminals     = new TreeMap<String, Terminal>();
        this.variables     = new TreeMap<String, Variable>();
        this.startVariable = null;
        this.errorInstance = new Error();
    }
    
    
    public Map<String, Terminal> terminals() {
        return terminals;
    }
    
    public Map<String, Variable> variables() {
        return variables;
    }
    
    /**
     * Adds a new terminal to this grammar.
     * 
     * @param tokenClass
     *            the token class associated with the terminal
     *            
     * @return the new terminal
     */
    public Terminal addTerminal(String tokenClass) {
        return addTerminal(tokenClass, false);
    }
    
    /**
     * Adds a new, possibly discardable terminal to this grammar.
     * 
     * @param tokenClass
     *            the token class associated with the terminal
     * @param isDiscardable
     *            if <code>true</code>, all instances of the terminal are
     *            discarded after being parsed and are not added to the parse
     *            tree
     *            
     * @return the new terminal
     */
    public Terminal addTerminal(String tokenClass, boolean isDiscardable) {
        Terminal terminal = new Terminal(tokenClass, isDiscardable);
        
        terminals.put(tokenClass, terminal);
        
        return terminal;
    }
    
    /**
     * Adds a new variable to this grammar.
     * 
     * @param name  the variable's name
     * 
     * @return the new variable
     */
    public Variable addVariable(String name) {
        Variable variable = new Variable(name, null);
        
        variables.put(name, variable);
        
        return variable;
    }
    
    /**
     * Adds a new auxiliary variable to this grammar.
     * 
     * @param parentRule
     *            the rule for which the auxiliary variable is being created
     *
     * @return the new variable
     */
    public Variable newAuxiliaryVariable(Rule parentRule) {
        return newAuxiliaryVariable(parentRule, 0);
    }
    
    /**
     * Adds a new auxiliary variable to this grammar. For convenience the
     * specified number of blank rules are added.
     * 
     * @param parentRule
     *            the rule for which the auxiliary variable is being created
     * @param numRules
     *            the number of rules to create for the auxiliary variable
     * 
     * @return the new variable
     */
    public Variable newAuxiliaryVariable(Rule parentRule, int numRules) {
        String   name     = parentRule.getVariable().getName() + "@" + variables.size();
        Variable variable = new Variable(name, parentRule);
        
        for (int i = 0; i < numRules; ++i) {
            variable.addRule();
        }
        
        variables.put(name, variable);
        
        return variable;
    }
    

    /**
     * Gets the start symbol. This is the root variable in the grammar. If it
     * has not been explicitly set then the first variable in the grammar is the
     * start symbol.
     * 
     * @return the start symbol
     */
    public Variable getStartVariable() {
        if (startVariable != null) {
            return startVariable;
        }
        else if (!variables.isEmpty()) {
            return variables.get(0);
        }
        else {
            return null;
        }
    }
    
    /**
     * Sets the start symbol.
     * 
     * @param startVariable  the start symbol
     */
    public void setStartVariable(Variable startVariable) {
        this.startVariable = startVariable;
    }
    
    
    /**
     * Reads a grammar from the XML in an input stream. The XML format is
     * described in <tt>grammar.xsd</tt>.
     * 
     * @param inputStream
     *            an input stream to read the XML from
     * 
     * @return a new grammar
     * 
     * @throws SAXException  if there's an error in the XML
     * @throws IOException   if there's an error reading from the input stream
     */
    public static Grammar fromXML(InputStream inputStream) throws SAXException, IOException {
        // Loads the XML via DOM so we can check it against the schema, then
        // converts the DOM to JDOM.
        org.w3c.dom.Document domDocument  = getDocumentBuilder().parse(inputStream);
        DOMBuilder           domBuilder   = new DOMBuilder(); 
        Document             jdomDocument = domBuilder.build(domDocument);
        
        return fromValidatedXML(jdomDocument);
    }

    
    private static DocumentBuilder documentBuilder;
    private static DocumentBuilder getDocumentBuilder() {
        if (documentBuilder == null) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        
            factory.setCoalescing    (true);
            factory.setNamespaceAware(true);
//            factory.setSchema        (getSchema());
            
            try {
                documentBuilder = factory.newDocumentBuilder();
            }
            catch (ParserConfigurationException exception) {
                Debug.logError(exception);
                throw new RuntimeException(exception);
            }
        }
        
        return documentBuilder;
    }

    private static Schema schema;
    private static Schema getSchema() {
        if (schema == null) {
            SchemaFactory factory      = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream   schemaStream = Grammar.class.getResourceAsStream("grammar.xsd"); 
            
            try {
                schema = factory.newSchema(new StreamSource(schemaStream));
            }
            catch (SAXException exception) {
                Debug.logError(exception, "Error reading grammar.xsd.");
                throw new RuntimeException(exception);
            }
        }
        
        return schema;
    }
    
    
    @SuppressWarnings("unchecked")
    private static Grammar fromValidatedXML(Document xmlGrammar) throws SAXException {
        try {
            Grammar grammar = new Grammar();
            
            // Verify that for each <repeat> element, if a maximum is specified that it is
            // greater than the minimum.
            for (Element xmlVariable: (List<Element>) new JDOMXPath("//repeat[@maximum]").selectNodes(xmlGrammar)) {
                int minimum = Integer.parseInt(xmlVariable.getAttributeValue("minimum"));
                int maximum = Integer.parseInt(xmlVariable.getAttributeValue("maximum"));
    
                if (minimum > maximum) {
                    throw new SAXException(String.format(
                        "Variable \"%s\": <repeat> minimum (%d) greater than maximum (%d).",
                                
                        xmlVariable.getAttributeValue("name"),
                        minimum,
                        maximum
                    ));
                }
            }
            
            // Get the terminal and variable nodes from the XML file.
            List<Element> xmlTerminals = new JDOMXPath("/grammar/terminal").selectNodes(xmlGrammar);
            List<Element> xmlVariables = new JDOMXPath("/grammar/variable").selectNodes(xmlGrammar);
    
            // Read the terminals.
            for (Element xmlTerminal: xmlTerminals) {
                String  name    = xmlTerminal.getAttributeValue("name");
                boolean discard = "yes".equals(xmlTerminal.getAttributeValue("discard"));
    
                grammar.addTerminal(name, discard);
            }
    
            // Create all the variables before reading any rules.
            for (Element xmlVariable: xmlVariables) {
                grammar.addVariable(xmlVariable.getAttributeValue("name"));
            }
    
            // The ID number for the current precedence set; this number is incremented
            // after each <orderedByPrecedence> element.
            int precedenceSet = 0;
    
            // Read the rules for all of the variables.
            for (Element xmlVariable: xmlVariables) {
                String   name       = xmlVariable.getAttributeValue("name");
                Variable variable   = grammar.variables().get(name);
    
                for (Element xmlRule: (List<Element>) xmlVariable.getChildren("rule")) {
                    addItems(grammar, variable.addRule(), xmlRule.getChildren());
                }
                
                for (Element xmlGroups: (List<Element>) xmlVariable.getChildren("orderedByPrecedence")) {
                    int precedenceLevel = 0;
    
                    for (Element xmlGroup: (List<Element>) xmlGroups.getChildren("group")) {
                        // Determine the group's associativity.
                        String        xmlAssociativity = xmlGroup.getAttributeValue("associativity");
                        Associativity associativity    = Associativity.NONE;
    
                        if (xmlAssociativity != null) {
                            associativity = Associativity.valueOf(xmlAssociativity.toUpperCase());
                        }
    
                        // Assign each rule in the group the same precedence and associativity.
                        for (Element xmlRule: (List<Element>) xmlGroup.getChildren("rule")) {
                            Rule rule = variable.addRule();
    
                            rule.setPrecedenceSet  (precedenceSet);
                            rule.setPrecedenceLevel(precedenceLevel);
                            rule.setAssociativity  (associativity);
    
                            addItems(grammar, rule, xmlRule.getChildren());
                        }
    
                        ++precedenceLevel;
                    }
    
                    // Assign the next precedence set a different ID number.
                    ++precedenceSet;
                }
            }
            
            return grammar;
        }
        catch (JaxenException exception) {
            Debug.logError(exception);
            
            // We shouldn't have any Jaxen problems, but since we did, convert
            // the exception into a SAXException so we can get out of here
            // without adding JaxenException to the @throws list.
            throw new SAXException(exception);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static void addItems(Grammar grammar, Rule rule, Element xmlRule) {
        addItems(grammar, rule, xmlRule.getChildren());
    }
    
    @SuppressWarnings("unchecked")
    private static void addItems(Grammar grammar, Rule rule, List<Element> xmlItems) {
        for (Element xmlItem: xmlItems) {
            if ("terminal".equals(xmlItem.getName())) {
                Terminal terminal = grammar.terminals().get(xmlItem.getTextTrim());
                boolean  discard  = terminal.isDiscardable();

                if (xmlItem.getAttribute("discard") != null) {
                    discard = xmlItem.getAttributeValue("discard").equals("yes");
                }

                rule.addTerminal(terminal, discard);
            }
            else if ("variable".equals(xmlItem.getName())) {
                rule.addVariable(grammar.variables().get(xmlItem.getTextTrim()));
            }
            else if ("group".equals(xmlItem.getName())) {
                // Let groupVariable → xmlItem.
                Variable newVariable = grammar.newAuxiliaryVariable(rule, 1);
                Rule     newRule     = newVariable.rules().get(0);
                
                addItems(grammar, newRule, xmlItem);

                rule.addVariable(newVariable);
            }
            else if ("optional".equals(xmlItem.getName())) {
                // Let optionalVariable → xmlItem | ε.
                Variable newVariable = grammar.newAuxiliaryVariable(rule, 2);

                addItems(grammar, newVariable.rules().get(0), xmlItem);
            
                rule.addVariable(newVariable);
            }
            else if ("repeat".equals(xmlItem.getName())) {
                int minimum = Integer.parseInt(xmlItem.getAttributeValue("minimum"));

                // If maximum is unbounded.
                if (xmlItem.getAttribute("maximum") == null) {
                    Variable newVariable = grammar.newAuxiliaryVariable(rule, 2);

                    // Create the rule "repeatVariable → repeatVariable itemsToRepeat".
                    newVariable.rules().get(0).addVariable(newVariable);
                    addItems(grammar, newVariable.rules().get(0), xmlItem);

                    // Create the rule "repeatVariable → itemsToRepeat^minimum" (itemsToRepeat
                    // repeated minimum times).
                    for (int i = 0; i < minimum; ++i) {
                        addItems(grammar, newVariable.rules().get(1), xmlItem);
                    }
                
                    rule.addVariable(newVariable);
                }
                else {
                    int maximum = Integer.parseInt(xmlItem.getAttributeValue("maximum"));

                    // Create the rule "repeatVariable → itemsToRepeat^i" for each i between minimum
                    // and maximum.
                    Variable newVariable = grammar.newAuxiliaryVariable(rule, 0);

                    for (int i = minimum; i <= maximum; ++i) {
                        Rule newRule = newVariable.addRule();
                        
                        for (int j = 0; j < i; ++j) {
                            addItems(grammar, newRule, xmlItem);
                        }
                    }

                    rule.addVariable(newVariable);
                }
            }
            else if ("choice".equals(xmlItem.getName())) {
                // Create a separate rule for each choice.
                Variable newVariable = grammar.newAuxiliaryVariable(rule, 0);
                
                for (Element xmlChoice: (List<Element>) xmlItem.getChildren()) {
                    addItems(grammar, newVariable.addRule(), Collections.singletonList(xmlChoice));
                }
                
                rule.addVariable(newVariable);
            }
            else if ("error".equals(xmlItem.getName())) {
                rule.addError();
            }
            else {
                Debug.logError("Unknown rule item <" + xmlItem.getName() + ">.");
            }
        }
    }
    
    
    public void print(PrintStream out) {
        out.print("Terminals:");
        
        for (Terminal terminal: terminals.values()) {
            out.printf(" %s", terminal);
        }
        
        out.println();
        out.println();
        
        for (Variable variable: variables.values()) {
            out.printf("%s:%n", variable.getName());
            
            for (Rule rule: variable.rules()) {
                out.printf("    %s", rule);
                
                if (rule.getPrecedenceSet() != null) {
                    out.printf(" (group = %d, precedence = %d, associativity = %s)",
                        rule.getPrecedenceSet  (),
                        rule.getPrecedenceLevel(),
                        rule.getAssociativity  ()
                    );
                }
                
                out.println();
            }
            
            out.println();
        }
    }
    
    public static void main(String[] arguments) {
        String grammarXml =
            "<grammar>"
          + "  <terminal name='+'/>"                    + "\n"
          + "  <terminal name='-'/>"                    + "\n"
          + "  <terminal name='*'/>"                    + "\n"
          + "  <terminal name='/'/>"                    + "\n"
          + "  <terminal name='identifier'/>"           + "\n"
          + "  "                                        + "\n"
          + "  <variable name='expression'>"            + "\n"
          + "    <orderedByPrecedence>"                 + "\n"
          + "      <group associativity='left'>"        + "\n"
          + "        <rule>"                            + "\n"
          + "          <variable>expression</variable>" + "\n"
          + "          <choice>"                        + "\n"
          + "            <terminal>+</terminal>"        + "\n"
          + "            <terminal>-</terminal>"        + "\n"
          + "          </choice>"                       + "\n"
          + "          <variable>expression</variable>" + "\n"
          + "        </rule>"                           + "\n"
          + "      </group>"                            + "\n"
          + "      "                                    + "\n"
          + "      <group associativity='left'>"        + "\n"
          + "        <rule>"                            + "\n"
          + "          <variable>expression</variable>" + "\n"
          + "          <choice>"                        + "\n"
          + "            <terminal>*</terminal>"        + "\n"
          + "            <terminal>/</terminal>"        + "\n"
          + "          </choice>"                       + "\n"
          + "          <variable>expression</variable>" + "\n"
          + "        </rule>"                           + "\n"
          + "      </group>"                            + "\n"
          + "    </orderedByPrecedence>"                + "\n"
          + "    "                                      + "\n"
          + "    <rule>"                                + "\n"
          + "      <terminal>identifier</terminal>"     + "\n"
          + "    </rule>"                               + "\n"
          + "  </variable>"                             + "\n"
          + "</grammar>";
        
        try {
            Grammar.fromXML(new ByteArrayInputStream(grammarXml.getBytes())).print(System.out);
        }
        catch (SAXException exception) {
            Debug.logError(exception);
        }
        catch (IOException exception) {
            Debug.logError(exception);
        }
    }
}
