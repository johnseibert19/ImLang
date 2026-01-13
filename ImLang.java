import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ImLang Scanner (Lexical Analyzer).
 * Converts source code strings into a List of strongly-typed Tokens.
 * Features: Scientific Notation, Underscores in IDs, Line Counting, Unary Minus fix.
 * @author John Seibert
 */
public class ImLang {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("if", TokenType.IF), Map.entry("else", TokenType.ELSE),
            Map.entry("while", TokenType.WHILE), Map.entry("for", TokenType.FOR),
            Map.entry("load", TokenType.LOAD), Map.entry("save", TokenType.SAVE),
            Map.entry("crop", TokenType.CROP), Map.entry("filter", TokenType.FILTER),
            Map.entry("image", TokenType.IMAGE), Map.entry("mat", TokenType.MAT),
            Map.entry("int", TokenType.INT), Map.entry("float", TokenType.FLOAT),
            Map.entry("bool", TokenType.BOOL), Map.entry("string", TokenType.STRING),
            Map.entry("pixel", TokenType.PIXEL), Map.entry("region", TokenType.REGION),
            Map.entry("true", TokenType.BOOL),
            Map.entry("false", TokenType.BOOL),
            Map.entry("and", TokenType.AND), Map.entry("or", TokenType.OR)
    );

    private static String source;
    private static int current;
    private static int start;
    private static int line;
    private static List<Token> tokens;

    private static List<String> errors;

    /**
     * Scans the provided source code string and converts it into a list of Tokens.
     * This is the main entry point for the lexical analysis phase.
     *
     * @param code The source code string to scan.
     * @return A list of {@code Token} objects representing the lexical structure of the code,
     * ending with an EOF token.
     */
    public static List<Token> scan(String code) {
        source = code;
        current = 0;
        line = 1;
        tokens = new ArrayList<>();
        errors = new ArrayList<>();

        while (!isAtEnd()) {
            start = current;
            char c = advance();

            if (isWhitespace(c)) continue;

            if (c == '/') {
                if (match('/')) while (peek() != '\n' && !isAtEnd()) advance();
                else if (match('*')) consumeMultiLineComment();
                else addToken(TokenType.DIV);
            } else if (isStringDelimiter(c)) {
                consumeString();
            } else if (isDigit(c) || (c == '.' && isDigit(peek()))) {
                consumeNumber(c);
            } else if (isAlpha(c) || c == '_') {
                consumeIdentifier();
            } else if (c == '-') {
                // Priority 1: Check for Negative Number Literal (e.g. x = -5)
                // We only do this if it looks like a number, and we are in a unary context
                if (isDigit(peek()) && isUnaryContext()) {
                    consumeNumber('-');
                }
                // Priority 2: Check for Decrement (--)
                else if (match('-')) {
                    addToken(TokenType.DEC);
                }
                // Priority 3: Check for Minus-Assign (-=)
                else if (match('=')) {
                    addToken(TokenType.MINUS_ASSIGN);
                }
                // Priority 4: Standard Subtraction (-)
                else {
                    addToken(TokenType.MINUS);
                }
            } else {
                handleSymbol(c);
            }
        }

        // Report any final errors/summary
        if (!errors.isEmpty()) {
            System.out.println("\n--- Lexing Summary ---");
            System.out.println(errors.size() + " lexical errors reported.");
        }

        tokens.add(new Token(TokenType.EOF, "$", null, line));
        return tokens;
    }

    private static void error(String message) {
        String errorMsg = "Line " + line + ": " + message;
        errors.add(errorMsg);
        System.err.println(errorMsg);
    }

    /**
     * Helper to determine if the current MINUS character ('-') should be treated as
     * a **unary minus** (part of a negative number literal) or a **binary subtraction**
     * operator.
     *
     * @return {@code true} if the current context suggests a unary minus (e.g., after an assignment
     * or beginning of an expression), {@code false} if it suggests binary subtraction
     * (e.g., after a value, identifier, or closing delimiter).
     */
    private static boolean isUnaryContext() {
        if (tokens.isEmpty()) return true;
        Token last = tokens.getLast();

        // If the last token was a Value, Identifier, or Closing Bracket/Paren,
        // then this minus sign MUST be subtraction (Binary Operator).
        return switch (last.type()) {
            case ID, NUM_LIT, RPAREN, RBRACKET, STR_LIT, BOOL ->
                    false; // Context implies subtraction (e.g., "x - 5", "10 - 5")
            default -> true;  // Context implies unary (e.g., "x = -5", "(-5)", ", -5")
        };
    }

    /**
     * Creates and adds a {@code Token} with no literal value to the list of scanned tokens.
     * The token's lexeme is derived from the source code slice between {@code start} and {@code current}.
     *
     * @param type The {@code TokenType} of the token.
     */
    private static void addToken(TokenType type) {
        addToken(type, null);
    }

    /**
     * Creates and adds a {@code Token} with an associated literal value to the list of scanned tokens.
     * The token's lexeme is derived from the source code slice between {@code start} and {@code current}.
     *
     * @param type The {@code TokenType} of the token.
     * @param literal The Java object representing the token's literal value (e.g., the parsed string or number).
     */
    private static void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    /**
     * Consumes characters to scan for a full identifier or keyword.
     * Keywords are checked against the {@code KEYWORDS} map.
     * Identifiers can contain letters, numbers, and underscores.
     */
    private static void consumeIdentifier() {
        while (isAlphaNumeric(peek()) || peek() == '_') advance();
        String text = source.substring(start, current);

        TokenType type = KEYWORDS.getOrDefault(text, TokenType.ID);
        addToken(type);
    }

    /**
     * Consumes characters to scan for a full string literal, including escape sequences.
     * Handles quoted strings and reports an error for unterminated strings.
     * All string literals are tokenized as {@code TokenType.STR_LIT}.
     */
    private static void consumeString() {
        StringBuilder sb = new StringBuilder();

        while (!isAtEnd() && !isStringDelimiter(peek()) && peek() != '\n') {
            char c = peek();

            if (c == '\\') {
                advance();
                if (!isAtEnd()) {
                    char next = advance();
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '"' -> sb.append('"');
                        case '\'' -> sb.append('\'');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(next);
                    }
                }
            } else {
                sb.append(advance());
            }
        }

        if (isAtEnd() || peek() == '\n') {
            error("Unterminated string literal. Expected a closing quote (\").");
            return;
        }

        advance(); // Consume the closing quote
        // Convert all string literals to the generic STR_LIT token per directions
        addToken(TokenType.STR_LIT, sb.toString());
    }

    /**
     * Consumes characters to scan for a full number literal, including decimal points
     * and scientific notation (e.g., 1.2e-3).
     * The token type is always {@code TokenType.NUM_LIT}.
     *
     * @param first The first character of the number, typically a digit
     * or a leading '-' for unary minus.
     */
    private static void consumeNumber(char first) {
        // Append the leading '-' if present
        StringBuilder sb = new StringBuilder();
        if (first == '-') sb.append(first);

        while (isDigit(peek())) advance();

        if (peek() == '.' && isDigit(peekNext())) {
            do advance();
            while (isDigit(peek()));
        }
        // Scientific Notation (1.5e-10)
        if (peek() == 'e' || peek() == 'E') {
            advance();
            if (peek() == '+' || peek() == '-') advance();
            if (!isDigit(peek())) {
                error("Malformed scientific notation. Expected digits after exponent.");
            }
            while (isDigit(peek())) advance();
        }

        String text = source.substring(start, current);
        addToken(TokenType.NUM_LIT, text);
    }

    /**
     * Handles the scanning of single-character and two-character symbols
     * (operators and delimiters). Uses {@code match()} for two-character
     * lookaheads (e.g., '==', '!=', '<=', '>=').
     * Reports an {@code TokenType.ERROR} for unknown or malformed symbols.
     *
     * @param c The character that began the symbol lexeme.
     */
    private static void handleSymbol(char c) {
        switch (c) {
            case '(': addToken(TokenType.LPAREN); break;
            case ')': addToken(TokenType.RPAREN); break;
            case '{': addToken(TokenType.LBRACE); break;
            case '}': addToken(TokenType.RBRACE); break;
            case '[': addToken(TokenType.LBRACKET); break;
            case ']': addToken(TokenType.RBRACKET); break;
            case ',': addToken(TokenType.COMMA); break;
            case ';': addToken(TokenType.SEMI); break;
            case '+':
                if (match('+')) addToken(TokenType.INC); // Assuming you add INC/DEC
                else if (match('=')) addToken(TokenType.PLUS_ASSIGN);
                else addToken(TokenType.PLUS);
                break;
            case '-':
                if (match('-')) addToken(TokenType.DEC);
                else if (match('=')) addToken(TokenType.MINUS_ASSIGN);
                else addToken(TokenType.MINUS);
                break;
            case '*': addToken(TokenType.MULT); break;
            case '=': addToken(match('=') ? TokenType.EQ : TokenType.ASSIGN); break;
            case '<': addToken(match('=') ? TokenType.LTE : TokenType.LT); break;
            case '>': addToken(match('=') ? TokenType.GTE : TokenType.GT); break;
            case '&':
                if (match('&')) addToken(TokenType.AND);
                else error("Single '&' is not a valid operator. Did you mean '&&' (And)?");
                break;
            case '|':
                if (match('|')) addToken(TokenType.OR);
                else error("Single '|' is not a valid operator. Did you mean '||' (Or)?");
                break;
            case '.':
                if (match('.')) addToken(TokenType.RANGE);
                else error("Single '.' is not a valid symbol. Did you mean '..' (Range)?");
                break;
            case '!':
                addToken(match('=') ? TokenType.NEQ : TokenType.NOT); // Support NOT
                break;
            case '%':
                addToken(TokenType.MOD); // Support MOD
                break;
            default:
                error("Unexpected character: '" + c + "'. This character " +
                        "is not recognized by the language.");
                break;
        }
    }

    /**
     * Consumes characters to skip over a block-style (multi-line) comment
     * that starts with '/*'. Increments the line count when newlines are encountered.
     * Stops after consuming the closing character set.
     */
    private static void consumeMultiLineComment() {
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance(); advance(); return;
            }
            if (peek() == '\n') line++;
            advance();
        }
        error("Unterminated multi-line comment. Expected '*/'.");
    }


    /**
     * Checks if the scanner has reached or exceeded the end of the source code.
     *
     * @return {@code true} if there are no more characters to consume;
     * {@code false} otherwise.
     */
    private static boolean isAtEnd() {
        return current >= source.length();
    }

    /**
     * Consumes the character at the current position and advances the scanner pointer.
     * If the consumed character is a newline ('\n'), the line number counter is incremented.
     *
     * @return The character that was just consumed.
     */
    private static char advance() {
        char c = source.charAt(current++);
        if (c == '\n') line++;
        return c;
    }

    /**
     * Returns the character at the current position without consuming it.
     *
     * @return The current character, or the null character {@code '\0'} if the scanner
     * is at the end of the source.
     */
    private static char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    /**
     * Returns the character one position ahead of the current position without consuming it.
     * This is used for lookahead operations (e.g., distinguishing between '>' and '>=').
     *
     * @return The next character, or the null character {@code '\0'} if the next position
     * is at the end of the source.
     */
    private static char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    /**
     * Checks if the current character matches the expected character.
     * If the characters match, the scanner advances (consumes the character).
     * If they do not match, or if the scanner is at the end, the state is unchanged.
     *
     * @param expected The character to check against.
     * @return {@code true} if the current character matches the
     * expected character; {@code false} otherwise.
     */
    private static boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    /**
     * Checks if the provided character is a numeric digit (0-9).
     *
     * @param c The character to test.
     * @return {@code true} if the character is a digit; {@code false} otherwise.
     */
    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Checks if the provided character is an alphabetic letter (a-z or A-Z).
     * Note: This does not handle underscores ('_').
     *
     * @param c The character to test.
     * @return {@code true} if the character is a letter; {@code false} otherwise.
     */
    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Checks if the provided character is alphanumeric.
     *
     * @param c The character to test.
     * @return {@code true} if the character is a letter or a digit; {@code false} otherwise.
     */
    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    /**
     * Checks if the character is a valid string delimiter.
     * Supports standard double quotes (") as well as opening and
     * closing smart quotes (“ and ”).
     *
     * @param c The character to test.
     * @return {@code true} if the character is a quote mark; {@code false} otherwise.
     */
    private static boolean isStringDelimiter(char c) {
        return c == '"' || c == '“' || c == '”';
    }

    /**
     * Checks if the provided character is a whitespace character.
     * Treats space, carriage return, tab, and newline as whitespace.
     *
     * @param c The character to test.
     * @return {@code true} if the character is whitespace; {@code false} otherwise.
     */
    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\r' || c == '\t' || c == '\n';
    }
}