package name.kugelman.john.kang.compiler;

import java.io.*;

import name.kugelman.john.compiler.*;
import name.kugelman.john.util.*;

/**
 * A Kang program. This class parses and creates executable programs from Kang
 * source code files.
 */
public class Program {
    /**
     * Creates a new Kang program.
     * 
     * @param sourceFile  the name of the source file
     * 
     * @throws FileNotFoundException
     *     if the input source file is not found. 
     * @throws IOException 
     *     if there's an error reading from the source file.
     * @throws Parser.Exception
     *     
     */
    public Program(File sourceFile)
        throws FileNotFoundException, Parser.Exception, IOException
    {
        try {
            Log         log       = new Log();
            InputStream stream    = Program.class.getResourceAsStream("grammar.xml");
            Grammar     grammar   = Grammar.fromXML(stream);
            Parser      parser    = new Parser(grammar);
            ParseTree   parseTree = parser.parse(new Tokenizer(new FileReader(sourceFile), log));
    
            parseTree.print(System.out);
        }
        catch (InvalidGrammarException exception) {
            Debug.logError(exception);
            throw new RuntimeException(exception);
        }
    }

    /**
     * Executes the program.
     */
    public void execute() {
    }
}