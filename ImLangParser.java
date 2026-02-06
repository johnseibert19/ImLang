import java.util.*;

/**
 * ImLang Parser (Table-Driven LL(1)).
 * Validates the syntax of the ImLang Design specification.
 * @author John Seibert, Dylan Kauffman, Jack Norfolk
 */
public class ImLangParser {

    public static final String EPSILON = "EPSILON";

    private static final Map<String, Map<String, String[]>> parsingTable = new HashMap<>();

    private static final String[] EXPR_FIRSTS = {
            "ID", "NUM_LIT", "STR_LIT", "BOOL",
            "LOAD", "SAVE", "CROP", "FILTER", "IMAGE",
            "LBRACKET", "LPAREN", "MINUS", "NOT"
    };

    private static final String[] TYPE_TOKENS = {"IMAGE", "MAT", "INT", "FLOAT", "BOOL", "STRING", "PIXEL", "REGION"};

    private static final String[] STMT_STARTERS = {
            "ID", "IF", "WHILE", "FOR", "LOAD", "SAVE", "CROP", "FILTER",
            "LBRACE", "SEMI", "BREAK", "CONTINUE", "RETURN", "PRINT"
    };

    // Static initialization block to populate the LL(1) Parsing Table.
    static {
        // =========================================================================
        // 1. PROGRAM START & DECLARATION LIST
        // =========================================================================
        addRule("program_start", "EOF", new String[]{"declList"});
        addRules("program_start", TYPE_TOKENS, new String[]{"declList"});
        addRules("program_start", STMT_STARTERS, new String[]{"declList"});

        addRules("declList", TYPE_TOKENS, new String[]{"decl", "declList"});
        addRules("declList", STMT_STARTERS, new String[]{"decl", "declList"});
        addRule("declList", "EOF", new String[]{EPSILON});
        addRule("declList", "RBRACE", new String[]{EPSILON});

        // =========================================================================
        // 2. DECLARATIONS
        // =========================================================================
        // Step 1: Consume Type and ID first.
        addRules("decl", TYPE_TOKENS, new String[]{"type", "ID", "decl_tail"});

        // Allow top-level statements
        addRules("decl", STMT_STARTERS, new String[]{"stmt"});

        // Define tokens that signal the END of a declaration (and start of next)
        List<String> declEndFollow = new ArrayList<>(Arrays.asList("EOF", "RBRACE"));
        declEndFollow.addAll(Arrays.asList(TYPE_TOKENS));
        declEndFollow.addAll(Arrays.asList(STMT_STARTERS));

        // Step 2: decl_tail decides the path based on what follows the ID.

        // Path A: Explicit Variable Tail (Assignment or Semicolon)
        addRule("decl_tail", "ASSIGN", new String[]{"var_tail"});
        addRule("decl_tail", "SEMI", new String[]{"var_tail"});

        // Path B: Function Definition
        addRule("decl_tail", "LPAREN", new String[]{"func_tail"});

        // Path C: C-Style Array Dimensions ([10])
        addRule("decl_tail", "LBRACKET", new String[]{"c_style_dims", "var_tail"});

        // Path D: Non-Strict Semicolon Support (Implicit End)
        // If we see the start of a new stmt/decl, go to var_tail (which will epsilon out)
        addRules("decl_tail", declEndFollow.toArray(new String[0]), new String[]{"var_tail"});

        // =========================================================================
        // 3. VARIABLE, FUNCTION, & C-STYLE ARRAY TAILS
        // =========================================================================
        // Variable Tail: = expr ;  OR  ;
        addRule("var_tail", "ASSIGN", new String[]{"ASSIGN", "expr", "opt_semi"});
        addRule("var_tail", "SEMI", new String[]{"opt_semi"});

        // Fallback for var_tail (Allows missing semicolon if next token is valid start)
        addRules("var_tail", declEndFollow.toArray(new String[0]), new String[]{"opt_semi"});

        // Function Tail: ( params ) { block }
        addRule("func_tail", "LPAREN", new String[]{"LPAREN", "opt_params", "RPAREN", "block"});

        // C-Style Dimensions: [size] [size] ...
        addRule("c_style_dims", "LBRACKET", new String[]{"LBRACKET", "opt_arr_size", "RBRACKET", "c_style_dims"});

        // Base Cases for Dimensions:
        addRule("c_style_dims", "ASSIGN", new String[]{EPSILON});
        addRule("c_style_dims", "SEMI", new String[]{EPSILON});
        // Allow implicit end for C-arrays too
        addRules("c_style_dims", declEndFollow.toArray(new String[0]), new String[]{EPSILON});

        // Function Parameters
        addRules("opt_params", TYPE_TOKENS, new String[]{"param_list"});
        addRule("opt_params", "RPAREN", new String[]{EPSILON});

        addRules("param_list", TYPE_TOKENS, new String[]{"type", "ID", "param_tail"});
        addRule("param_tail", "COMMA", new String[]{"COMMA", "type", "ID", "param_tail"});
        addRule("param_tail", "RPAREN", new String[]{EPSILON});

        // Block Scope
        addRule("block", "LBRACE", new String[]{"LBRACE", "stmtList", "RBRACE"});

        // =========================================================================
        // 4. TYPES (Java-Style Arrays)
        // =========================================================================
        addRules("type", TYPE_TOKENS, new String[]{"basic_type", "type_tail"});

        for (String t : TYPE_TOKENS) addRule("basic_type", t, new String[]{t});

        addRule("type_tail", "LBRACKET", new String[]{"LBRACKET", "opt_arr_size", "RBRACKET", "type_tail"});

        addRule("opt_arr_size", "NUM_LIT", new String[]{"NUM_LIT"});
        addRule("opt_arr_size", "RBRACKET", new String[]{EPSILON});

        addRule("type_tail", "ID", new String[]{EPSILON});

        // =========================================================================
        // 5. STATEMENTS
        // =========================================================================
        addRules("stmtList", TYPE_TOKENS, new String[]{"decl", "stmtList"});
        addRules("stmtList", STMT_STARTERS, new String[]{"stmt", "stmtList"});
        addRule("stmtList", "RBRACE", new String[]{EPSILON});

        addRule("stmt", "ID", new String[]{"ID", "id_tail", "opt_semi"});
        addRule("stmt", "IF", new String[]{"if_stmt"});
        addRule("stmt", "WHILE", new String[]{"while_stmt"});

        // UPDATED: Support for(int i=...) via for_init
        addRule("for_stmt", "FOR", new String[]{"FOR", "LPAREN", "for_init", "ASSIGN", "expr", "RANGE", "expr", "RPAREN", "stmt"});

        // Rules for for_init
        addRule("for_init", "ID", new String[]{"ID"});
        addRules("for_init", TYPE_TOKENS, new String[]{"type", "ID"});

        addRules("stmt", new String[]{"LOAD", "SAVE", "CROP", "FILTER"}, new String[]{"builtin_call", "opt_semi"});

        addRule("stmt", "LBRACE", new String[]{"block"});
        addRule("stmt", "SEMI", new String[]{"SEMI"});

        addRule("stmt", "BREAK", new String[]{"BREAK", "SEMI"});
        addRule("stmt", "CONTINUE", new String[]{"CONTINUE", "SEMI"});
        addRule("stmt", "PRINT", new String[]{"PRINT", "LPAREN", "expr", "RPAREN", "SEMI"});
        addRule("stmt", "RETURN", new String[]{"RETURN", "opt_return_val", "SEMI"});

        addRules("opt_return_val", EXPR_FIRSTS, new String[]{"expr"});
        addRule("opt_return_val", "SEMI", new String[]{EPSILON});

        // OPTIONAL SEMICOLON
        addRule("opt_semi", "SEMI", new String[]{"SEMI"});

        List<String> optSemiFollow = new ArrayList<>(Arrays.asList("EOF", "RBRACE", "ELSE"));
        optSemiFollow.addAll(Arrays.asList(TYPE_TOKENS));
        optSemiFollow.addAll(Arrays.asList(STMT_STARTERS));
        for (String t : optSemiFollow) if (!t.equals("SEMI")) addRule("opt_semi", t, new String[]{EPSILON});

        // =========================================================================
        // 6. ID TAIL
        // =========================================================================
        addRule("id_tail", "ASSIGN", new String[]{"ASSIGN", "expr"});
        addRule("id_tail", "PLUS_ASSIGN", new String[]{"PLUS_ASSIGN", "expr"});
        addRule("id_tail", "MINUS_ASSIGN", new String[]{"MINUS_ASSIGN", "expr"});

        addRule("id_tail", "LPAREN", new String[]{"LPAREN", "arg_list", "RPAREN"});
        addRule("id_tail", "LBRACKET", new String[]{"LBRACKET", "expr", "RBRACKET", "id_tail"});

        addRule("id_tail", "INC", new String[]{"INC"});
        addRule("id_tail", "DEC", new String[]{"DEC"});

        List<String> idTailFollow = new ArrayList<>(Arrays.asList("SEMI", "RBRACE", "EOF", "ELSE"));
        idTailFollow.addAll(Arrays.asList(TYPE_TOKENS));
        idTailFollow.addAll(Arrays.asList(STMT_STARTERS));
        addRules("id_tail", idTailFollow.toArray(new String[0]), new String[]{EPSILON});

        // =========================================================================
        // 7. CONTROL FLOW
        // =========================================================================
        addRule("if_stmt", "IF", new String[]{"IF", "LPAREN", "expr", "RPAREN", "stmt", "else_part"});
        addRule("else_part", "ELSE", new String[]{"ELSE", "stmt"});
        addRule("else_part", "SEMI", new String[]{EPSILON});
        addRule("else_part", "RBRACE", new String[]{EPSILON});
        addRule("else_part", "EOF", new String[]{EPSILON});
        addRules("else_part", STMT_STARTERS, new String[]{EPSILON});
        addRules("else_part", TYPE_TOKENS, new String[]{EPSILON});

        addRule("while_stmt", "WHILE", new String[]{"WHILE", "LPAREN", "expr", "RPAREN", "stmt"});

        // =========================================================================
        // 8. EXPRESSIONS
        // =========================================================================
        addRules("expr", EXPR_FIRSTS, new String[]{"logic_and", "logic_or_prime"});
        addRule("logic_or_prime", "OR", new String[]{"OR", "logic_and", "logic_or_prime"});

        addRules("logic_and", EXPR_FIRSTS, new String[]{"equality", "logic_and_prime"});
        addRule("logic_and_prime", "AND", new String[]{"AND", "equality", "logic_and_prime"});

        addRules("equality", EXPR_FIRSTS, new String[]{"comparison", "equality_prime"});
        addRule("equality_prime", "EQ", new String[]{"EQ", "comparison", "equality_prime"});
        addRule("equality_prime", "NEQ", new String[]{"NEQ", "comparison", "equality_prime"});

        addRules("comparison", EXPR_FIRSTS, new String[]{"additive", "comparison_prime"});
        addRule("comparison_prime", "LT", new String[]{"LT", "additive", "comparison_prime"});
        addRule("comparison_prime", "GT", new String[]{"GT", "additive", "comparison_prime"});
        addRule("comparison_prime", "LTE", new String[]{"LTE", "additive", "comparison_prime"});
        addRule("comparison_prime", "GTE", new String[]{"GTE", "additive", "comparison_prime"});

        addRules("additive", EXPR_FIRSTS, new String[]{"term", "additive_prime"});
        addRule("additive_prime", "PLUS", new String[]{"PLUS", "term", "additive_prime"});
        addRule("additive_prime", "MINUS", new String[]{"MINUS", "term", "additive_prime"});

        addRules("term", EXPR_FIRSTS, new String[]{"factor", "term_prime"});
        addRule("term_prime", "MULT", new String[]{"MULT", "factor", "term_prime"});
        addRule("term_prime", "DIV", new String[]{"DIV", "factor", "term_prime"});
        addRule("term_prime", "MOD", new String[]{"MOD", "factor", "term_prime"});

        // Follow Sets for Expression components
        List<String> followBase = new ArrayList<>(Arrays.asList("SEMI", "RPAREN", "RANGE", "COMMA", "RBRACKET", "RBRACE", "EOF"));
        followBase.addAll(Arrays.asList(TYPE_TOKENS));
        followBase.addAll(Arrays.asList(STMT_STARTERS));
        followBase.add("ELSE");
        followBase.addAll(Arrays.asList(EXPR_FIRSTS));
        followBase.removeAll(Arrays.asList("PLUS", "MINUS", "MULT", "DIV", "MOD"));

        List<String> fOr = new ArrayList<>(followBase);
        List<String> fAnd = new ArrayList<>(fOr); fAnd.add("OR");
        List<String> fEq = new ArrayList<>(fAnd); fEq.add("AND");
        List<String> fCmp = new ArrayList<>(fEq); fCmp.add("EQ"); fCmp.add("NEQ");
        List<String> fAdd = new ArrayList<>(fCmp); fAdd.addAll(Arrays.asList("LT", "GT", "LTE", "GTE"));
        List<String> fTerm = new ArrayList<>(fAdd); fTerm.addAll(Arrays.asList("PLUS", "MINUS"));

        addRules("logic_or_prime", fOr.toArray(new String[0]), new String[]{EPSILON});
        addRules("logic_and_prime", fAnd.toArray(new String[0]), new String[]{EPSILON});
        addRules("equality_prime", fEq.toArray(new String[0]), new String[]{EPSILON});
        addRules("comparison_prime", fCmp.toArray(new String[0]), new String[]{EPSILON});
        addRules("additive_prime", fAdd.toArray(new String[0]), new String[]{EPSILON});
        addRules("term_prime", fTerm.toArray(new String[0]), new String[]{EPSILON});

        // =========================================================================
        // 9. FACTORS
        // =========================================================================
        addRule("factor", "ID", new String[]{"ID", "factor_tail"});

        // RULE: factor_tail must explicitly handle '(' for function calls
        addRule("factor_tail", "LBRACKET", new String[]{"LBRACKET", "expr", "RBRACKET", "factor_tail"});
        addRule("factor_tail", "LPAREN", new String[]{"LPAREN", "arg_list", "RPAREN", "factor_tail"});

        List<String> factorTailFollow = new ArrayList<>();
        // Operators
        factorTailFollow.addAll(Arrays.asList("PLUS", "MINUS", "MULT", "DIV", "MOD", "AND", "OR"));
        factorTailFollow.addAll(Arrays.asList("EQ", "NEQ", "LT", "GT", "LTE", "GTE"));
        // Delimiters
        factorTailFollow.addAll(Arrays.asList("SEMI", "RPAREN", "RBRACKET", "COMMA", "RBRACE", "EOF"));
        factorTailFollow.add("ASSIGN");

        // Allow factor_tail to exit if it sees the start of a new statement
        factorTailFollow.addAll(Arrays.asList(TYPE_TOKENS));
        factorTailFollow.addAll(Arrays.asList(STMT_STARTERS));

        // CRITICAL FIX: Explicitly REMOVE LPAREN and LBRACKET from the follow set.
        // This ensures the parser uses the specific rules defined above (for function calls/arrays)
        // instead of "epsilon-ing" out when it sees a bracket or paren.
        // This solves "SYNTAX ERROR ... found [(]"
        factorTailFollow.remove("LPAREN");
        factorTailFollow.remove("LBRACKET");

        addRules("factor_tail", factorTailFollow.toArray(new String[0]), new String[]{EPSILON});

        // Rest of factors
        addRule("factor", "NUM_LIT", new String[]{"NUM_LIT"});
        addRule("factor", "STR_LIT", new String[]{"STR_LIT"});
        addRule("factor", "BOOL", new String[]{"BOOL"});
        addRule("factor", "LPAREN", new String[]{"LPAREN", "expr", "RPAREN"});

        addRule("factor", "LBRACKET", new String[]{"array_literal"});

        addRule("factor", "MINUS", new String[]{"MINUS", "factor"});
        addRule("factor", "NOT", new String[]{"NOT", "factor"});
        addRules("factor", new String[]{"LOAD", "SAVE", "CROP", "FILTER"}, new String[]{"builtin_call"});

        addRule("factor", "IMAGE", new String[]{"IMAGE", "LPAREN", "expr", "COMMA", "expr", "RPAREN"});

        // =========================================================================
        // 10. ARRAY LITERAL STRUCTURE
        // =========================================================================
        addRule("sep_or_space", "COMMA", new String[]{"COMMA"});
        addRule("sep_or_space", "SEMI", new String[]{"SEMI"});

        addRule("array_literal", "LBRACKET", new String[]{"LBRACKET", "elements", "RBRACKET"});

        addRules("elements", EXPR_FIRSTS, new String[]{"expr_or_range_start", "element_list"});
        addRule("elements", "RANGE", new String[]{"RANGE", "opt_expr", "element_list"});
        addRule("elements", "RBRACKET", new String[]{EPSILON});

        addRule("expr_or_range_start", "RANGE", new String[]{"RANGE", "opt_expr"});
        List<String> fEORS = new ArrayList<>(Arrays.asList("RBRACKET", "COMMA", "SEMI"));
        fEORS.addAll(Arrays.asList(EXPR_FIRSTS));
        addRules("expr_or_range_start", fEORS.toArray(new String[0]), new String[]{EPSILON});

        addRules("element_list", new String[]{"COMMA", "SEMI"}, new String[]{"sep_or_space", "elements_item", "element_list"});
        addRules("element_list", EXPR_FIRSTS, new String[]{"elements_item", "element_list"});
        addRule("element_list", "RBRACKET", new String[]{EPSILON});

        addRules("elements_item", EXPR_FIRSTS, new String[]{"expr"});
        addRule("elements_item", "RANGE", new String[]{"RANGE", "opt_expr"});

        addRules("opt_expr", EXPR_FIRSTS, new String[]{"expr"});
        addRule("opt_expr", "COMMA", new String[]{EPSILON});
        addRule("opt_expr", "SEMI", new String[]{EPSILON});
        addRule("opt_expr", "RBRACKET", new String[]{EPSILON});

        // =========================================================================
        // 11. BUILT-INS
        // =========================================================================
        addRules("arg_list", EXPR_FIRSTS, new String[]{"expr", "arg_tail"});
        addRule("arg_list", "RPAREN", new String[]{EPSILON});
        addRule("arg_tail", "COMMA", new String[]{"COMMA", "expr", "arg_tail"});
        addRule("arg_tail", "RPAREN", new String[]{EPSILON});

        addRule("builtin_call", "LOAD", new String[]{"LOAD", "LPAREN", "arg_list", "RPAREN"});
        addRule("builtin_call", "SAVE", new String[]{"SAVE", "LPAREN", "arg_list", "RPAREN"});
        addRule("builtin_call", "CROP", new String[]{"CROP", "LPAREN", "arg_list", "RPAREN"});
        addRule("builtin_call", "FILTER", new String[]{"FILTER", "LPAREN", "arg_list", "RPAREN"});
    }

    /**
     * Entry point for parsing a list of tokens.
     * Delegates to the PDA runtime logic.
     *
     * @param tokens The list of tokens produced by the lexer.
     * @return {@code true} if the tokens form a valid ImLang program; {@code false} otherwise.
     */
    public static boolean parse(List<Token> tokens) {
        return runPDA(tokens);
    }

    /**
     * Registers a single production rule in the parsing table.
     *
     * @param nt The Non-Terminal symbol (row in the table).
     * @param t  The Terminal symbol (column/lookahead in the table).
     * @param p  The production array (Right-Hand Side symbols).
     */
    private static void addRule(String nt, String t, String[] p) {
        parsingTable.computeIfAbsent(nt, k -> new HashMap<>()).put(t, p);
    }

    /**
     * Registers the same production rule for multiple terminal symbols.
     *
     * @param nt The Non-Terminal symbol.
     * @param ts An array of Terminal symbols that trigger this rule.
     * @param p  The production array (Right-Hand Side symbols).
     */
    private static void addRules(String nt, String[] ts, String[] p) {
        for (String t : ts) addRule(nt, t, p);
    }

    /**
     * Executes the Pushdown Automaton (PDA) logic to valid the token stream.
     *
     * @param tokens The input stream of tokens.
     * @return {@code true} if the stack is successfully emptied upon reaching EOF; {@code false} on error.
     */
    public static boolean runPDA(List<Token> tokens) {
        Stack<String> stack = new Stack<>();
        stack.push("EOF");
        stack.push("program_start");
        int cursor = 0;

        System.out.printf("%-60s | %-15s | %s%n", "STACK", "INPUT", "ACTION");
        System.out.println("----------------------------------------------------------------------------------------------------");

        while (!stack.isEmpty()) {
            String top = stack.peek();
            Token currentToken = cursor < tokens.size() ? tokens.get(cursor) : new Token(TokenType.EOF, "$", null, -1);
            String tokenTypeStr = currentToken.type().name();
            String lexeme = currentToken.lexeme() != null ? currentToken.lexeme() : "$";

            String stackStr = stack.toString();
            if (stackStr.length() > 60) stackStr = "..." + stackStr.substring(stackStr.length() - 57);

            if (top.equals(tokenTypeStr)) {
                System.out.printf("%-60s | %-15s | MATCH %s%n", stackStr, lexeme, top);
                stack.pop();
                cursor++;
                if (top.equals("EOF")) {
                    System.out.println("\n>>> SUCCESS: Parse Complete.");
                    return true;
                }
            } else if (parsingTable.containsKey(top) && parsingTable.get(top).containsKey(tokenTypeStr)) {
                String[] production = parsingTable.get(top).get(tokenTypeStr);
                stack.pop();
                String rhs = production.length == 1 && production[0].equals(EPSILON) ? "ε" : String.join(" ", production);
                System.out.printf("%-60s | %-15s | %s -> %s%n", stackStr, lexeme, top, rhs);
                if (!rhs.equals("ε")) {
                    for (int i = production.length - 1; i >= 0; i--) {
                        stack.push(production[i]);
                    }
                }
            } else {
                String expected = parsingTable.containsKey(top)
                        ? String.join(", ", parsingTable.get(top).keySet())
                        : top;
                System.out.println("\n>>> SYNTAX ERROR at Line " + currentToken.line() +
                        ": Expected [" + expected + "] but found [" + lexeme + "]");
                return false;
            }
        }
        return false;
    }

}
