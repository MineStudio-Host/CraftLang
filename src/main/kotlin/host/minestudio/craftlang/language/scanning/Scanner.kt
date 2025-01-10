package host.minestudio.craftlang.language.scanning

import host.minestudio.craftlang.CraftLang

class Scanner(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var keywords = mapOf(
        "and" to TokenType.AND,
        "class" to TokenType.CLASS,
        "else" to TokenType.ELSE,
        "false" to TokenType.FALSE,
        "for" to TokenType.FOR,
        "function" to TokenType.FUNCTION,
        "if" to TokenType.IF,
        "null" to TokenType.NULL,
        "or" to TokenType.OR,
        "print" to TokenType.PRINT,
        "return" to TokenType.RETURN,
        "super" to TokenType.SUPER,
        "this" to TokenType.THIS,
        "to" to TokenType.TO,
        "true" to TokenType.TRUE,
        "set" to TokenType.SET,
        "while" to TokenType.WHILE
    )

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line))
        return tokens
    }

    private fun isAtEnd(skip: Int = 0): Boolean {
        return (current + skip) >= source.length
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            ',' -> addToken(TokenType.COMMA)
            '.' -> addToken(TokenType.DOT)
            '-' -> addToken(TokenType.MINUS)
            '+' -> addToken(TokenType.PLUS)
            '*' -> addToken(TokenType.STAR)
            '!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
            '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQUAL_EQUAL)
                } else {
                    CraftLang.instance.error(line, "Unexpected character '='. For comparison use '==' instead.")
                }
            }
            '<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
            '>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else {
                    addToken(TokenType.SLASH)
                }
            }
            ':' -> addToken(if (match(':')) TokenType.DOUBLE_COLON else TokenType.COLON)
            ' ', '\t', '\r' -> {} // Ignore whitespace (besides indentation, handled in '\n')
            '\n' -> {
                line++

                // At this point, we can't tell how many spaces are in each indentation tier,
                // so we just parse as single spaces, and determine whether it's single, double, or quad in the parser
                while (peek() == ' ' || peek() == '\t') {
                    addToken(if (advance() == ' ') TokenType.SPACE else TokenType.TAB)
                }
            }
            '"' -> string()
            else -> {
                if (isDigit(c)) {
                    number()
                } else if (isAlpha(c)) {
                    identifier()
                } else {
                    if (c == '\u0000') {
                        return
                    }

                    CraftLang.instance.error(line, "Unexpected character '$c'.")
                }

            }
        }

    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun peek(skip: Int = 0): Char {
        if (isAtEnd(skip)) return '\u0000'
        return source[current + skip]
    }

    private fun advance(): Char {
        current++
        return source[current - 1]
    }

    private fun isDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isAlpha(c: Char): Boolean {
        return (c in 'a'..'z') || (c in 'A'..'Z') || c == '_'
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isDigit(c)
    }


    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            if (peek() == '\\' && peek(1) == '"') advance()
            advance()
        }

        if (isAtEnd()) {
            CraftLang.instance.error(line, "Missing '\"'.")
            return
        }

        advance()

        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun number() {
        while (isDigit(peek())) advance()

        if (peek() == '.' && peek(1).isDigit()) {
            advance()

            while (peek().isDigit()) advance()
        }

        addToken(TokenType.NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        addToken(type)
    }



    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }
}