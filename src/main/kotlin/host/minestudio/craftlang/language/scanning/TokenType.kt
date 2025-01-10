package host.minestudio.craftlang.language.scanning

enum class TokenType {
    // Indentation
    SPACE, TAB,

    // Single-character tokens.
    LEFT_PAREN, RIGHT_PAREN, COMMA, DOT,
    MINUS, PLUS, SLASH, STAR,

    // One or two character tokens.
    BANG,
    BANG_EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,
    COLON, DOUBLE_COLON,

    // Literals.
    IDENTIFIER, STRING, NUMBER,

    // Keywords.
    AND, CLASS, ELSE, FALSE, FUNCTION, FOR, IF, NULL, OR,
    PRINT, RETURN, SUPER, THIS, TO, TRUE, SET, WHILE,

    EOF
}