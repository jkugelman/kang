package name.kugelman.john.compiler.gui;

import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import name.kugelman.john.compiler.*;
import name.kugelman.john.compiler.ParseTree.*;
import name.kugelman.john.compiler.Tokenizer.*;

/**
 * An implementation of {@link TreeModel} for {@link ParseTree}s.
 */
public class ParseTreeModel implements TreeModel {
    private ParseTree                     parseTree;
    private Collection<TreeModelListener> listeners;
    
    /**
     * Creates a new model that reflects the specified parse tree.
     * 
     * @param parseTree
     *            a parse tree to display in a {@link JTree}
     */
    public ParseTreeModel(ParseTree parseTree) {
        this.parseTree = parseTree;
        this.listeners = new ArrayList<TreeModelListener>();
    }
    
    public void addTreeModelListener(TreeModelListener listener) {
        listeners.add(listener);
    }

    public void removeTreeModelListener(TreeModelListener listener) {
        listeners.remove(listener);
    }

    public Object getChild(Object parent, int index) {
        return ((Node) parent).getChildren().get(index);
    }

    public int getChildCount(Object parent) {
        return ((Node) parent).getChildren().size();
    }

    public int getIndexOfChild(Object parent, Object child) {
        return ((Node) parent).getChildren().indexOf(child);
    }

    public Object getRoot() {
        return parseTree.getRoot();
    }

    public boolean isLeaf(Object node) {
        return !(node instanceof Variable);
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        throw new UnsupportedOperationException("ParseTree is read-only");
    }
    
    
    public static void main(String[] arguments) {
        JFrame frame = new JFrame("Parse Tree");
        JTree  tree  = new JTree (new ParseTreeModel(createParseTree()));
        
        frame.add(tree);
        frame.pack();
        frame.setVisible(true);
    }
    
    private static ParseTree createParseTree() {
        final Grammar          grammar   = new Grammar();
        final Grammar.Terminal terminal  = grammar.addTerminal("identifier");
        final Grammar.Variable variable  = grammar.addVariable("X");
        final File             file      = new File("source.kang");
        final Position         position  = new FilePosition(file, 0, 0);
        final ParseTree        parseTree = new ParseTree();
        
        final Token token = new Token() {
            public Object   getTokenClass() { return "identifier"; }
            public String   getLexeme    () { return "foo";        }
            public Position getStart     () { return position;     }
            public Position getEnd       () { return position;     }
        };
            
        variable.addRule();
        variable.addRule();

        variable.rules().get(1).addTerminal(terminal);
        
        parseTree.setRoot(parseTree.new Variable(variable.rules().get(1), Arrays.asList(new Node[] {parseTree.new Terminal(token)}), position));
        
        return parseTree;
    }
}
