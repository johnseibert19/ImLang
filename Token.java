/**
 * A single unit of meaning in the ImLang source code.
 * Carries the type, actual text, and location (line number).
 *
 * @param lexeme  The actual text found (e.g., "myVar", "123")
 * @param literal The parsed value (e.g., 123, "hello")
 * @param line    The line number where this token appears
 * @author John Seibert
 */
public record Token(TokenType type, String lexeme, Object literal, int line) {

    @Override
    public String toString() {
        return String.format("%-15s | %-15s | Line %d", type, lexeme, line);
    }
}