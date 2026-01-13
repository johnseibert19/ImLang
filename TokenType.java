/**
 * Defines the vocabulary for the Scanner and Parser.
 * @author John Seibert
 */
public enum TokenType {
    // literals
    STR_LIT, NUM_LIT,

    // loops
    IF, ELSE, WHILE, FOR,

    // method names
    LOAD, SAVE, CROP, FILTER,

    // type names
    IMAGE, MAT, INT, FLOAT, BOOL, STRING, PIXEL, REGION,

    // operators
    EQ, NEQ, LTE, GTE, AND, OR,
    ASSIGN, PLUS, MINUS, MULT, DIV, LT, GT,
    RANGE, INC, DEC, BREAK, CONTINUE,

    // symbols
    SEMI, COMMA,
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET, NOT, MOD, PLUS_ASSIGN, MINUS_ASSIGN,

    // special chars
    ID,
    EOF,
    ERROR
}