package host.minestudio.craftlang.language.parsing

import host.minestudio.craftlang.CraftLang
import host.minestudio.craftlang.language.expr.Expr
import host.minestudio.craftlang.language.scanning.Token
import host.minestudio.craftlang.language.scanning.TokenType
import host.minestudio.craftlang.language.stmt.Statement

class Parser(private val tokens: List<Token>) {
    private class ParseError(message: String) : RuntimeException(message)
    private enum class Indentation(val type: TokenType? = null, val amount: Int = 1) {
        TAB(TokenType.TAB),
        FOUR_SPACES(TokenType.SPACE, 4),
        TWO_SPACES(TokenType.SPACE, 2),
        ONE_SPACE(TokenType.SPACE),
        NONE(null, 0)
    }

    private var current = 0
    private var indentation = Indentation.NONE
    private var depth = 0

    fun parse(): List<Statement> {
        val statements = mutableListOf<Statement>()

        while (!isAtEnd()) {
            statements.add(declaration())
        }

        return statements
    }

    private fun declaration(): Statement {
        try {
            if (match(TokenType.CLASS)) return classDeclaration()
            if (match(TokenType.FUN)) return function("function")
            if (match(TokenType.SET)) return setter()

            return statement()
        } catch (error: ParseError) {
            synchronize()
            return Statement.ExpressionStatement(Expr.Literal(null))
        }
    }

    private fun classDeclaration(): Statement {
        val name = consume(TokenType.IDENTIFIER, "Expect class name.")

        val superclass =
            if (match(TokenType.DOUBLE_COLON))
                Expr.Variable(consume(TokenType.IDENTIFIER, "Expect superclass name."))
            else null

        consume(TokenType.COLON, "Expect ':' before class body.")
        depth++
        indentation = indentationType()

        val methods = mutableListOf<Statement.FunctionStatement>()
        while (isIndentation()) {
            consumeIndentation()
            consume(TokenType.FUN, "Expect method declaration.")
            methods.add(function("method"))
        }

        depth--

        return Statement.ClassStatement(name, superclass, methods)
    }

    private fun function(kind: String): Statement.FunctionStatement {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()

        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }

                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name."))
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        if (match(TokenType.DOUBLE_COLON)) {
            advance()
            // TODO: Implement return type
            consume(TokenType.IDENTIFIER, "Expect return type.")
        }

        consume(TokenType.COLON, "Expect ':' before $kind body.")

        val body = block()
        return Statement.FunctionStatement(name, parameters, body)
    }

    private fun setter(): Statement {
        val expr = equality()

        if (match(TokenType.TO)) {
            val to = previous()
            val value = expression()

            if (expr is Expr.Variable) {
                return Statement.VarStatement(expr.name, value)
            } else if (expr is Expr.Get) {
                return Statement.ExpressionStatement(Expr.Set(expr.obj, expr.name, value))
            }

            error(to, "Invalid assignment target.")
        }

        return Statement.ExpressionStatement(expr)
    }

    private fun statement(): Statement {
        if (match(TokenType.IF)) return ifStatement()
        if (match(TokenType.PRINT)) return printStatement()
        if (match(TokenType.RETURN)) return returnStatement()
        if (match(TokenType.WHILE)) return whileStatement()
        if (match(TokenType.COLON)) return Statement.BlockStatement(block())

        return expressionStatement()
    }

    private fun ifStatement(): Statement {
        val condition = expression()

        val thenBranch = statement()
        // Next token is an ELSE and indentation is the same
        val elseBranch = if (consumeAfterIndentation(TokenType.ELSE)) statement() else null

        return Statement.IfStatement(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Statement {
        val value = expression()
        return Statement.PrintStatement(value)
    }

    private fun returnStatement(): Statement {
        val keyword = previous()
        val value = if (peek().line == keyword.line) expression() else null
        return Statement.ReturnStatement(keyword, value)
    }

    private fun whileStatement(): Statement {
        val condition = expression()
        val body = statement()

        return Statement.WhileStatement(condition, body)
    }

    private fun expressionStatement(): Statement {
        val expr = expression()
        return Statement.ExpressionStatement(expr)
    }

    private fun block(): List<Statement> {
        val statements = mutableListOf<Statement>()

        val enclosing = indentation
        indentation = indentationType()
        depth++

        while (!isAtEnd() && peek().type == indentation.type) {
            if (!isIndentation()) break
            consumeIndentation()

            statements.add(declaration())
        }

        depth--
        indentation = enclosing

        return statements
    }

    private fun expression(): Expr {
        return or()
    }

    private fun or(): Expr {
        var expr = and()

        while (match(TokenType.OR)) {
            val operator = previous()
            val right = and()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(TokenType.AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = term()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = term()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun term(): Expr {
        var expr = factor()

        while (match(TokenType.MINUS, TokenType.PLUS)) {
            val operator = previous()
            val right = factor()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun factor(): Expr {
        var expr = unary()

        while (match(TokenType.SLASH, TokenType.STAR)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.")
                expr = Expr.Get(expr, name)
            } else {
                break
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()

        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val paren = consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.")

        return Expr.Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(TokenType.FALSE)) return Expr.Literal(false)
        if (match(TokenType.TRUE)) return Expr.Literal(true)
        if (match(TokenType.NULL)) return Expr.Literal(null)

        if (match(TokenType.NUMBER, TokenType.STRING)) return Expr.Literal(previous().literal)

        if (match(TokenType.SUPER)) {
            val keyword = previous()
            consume(TokenType.DOT, "Expect '.' after 'super'.")
            val method = consume(TokenType.IDENTIFIER, "Expect superclass method name.")
            return Expr.Super(keyword, method)
        }

        if (match(TokenType.THIS)) return Expr.This(previous())
        if (match(TokenType.IDENTIFIER)) return Expr.Variable(previous())

        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        throw error(peek(), "Expect expression.")
    }


    /**
     * Gets the amount of indentation elements (spaces/tabs)
     */
    private fun getIndentation(): Int {
        return checkAmount(indentation.type!!)
    }

    /**
     * Validates the indentation for the current line.
     * @return `true` if the indentation is correct, `false` if it's a de-indent.
     * @throws ParseError if the indentation is invalid. (e.g. too many spaces, 3 spaces, etc.)
     */
    private fun isIndentation(): Boolean {
        val amount = getIndentation()
        if (amount != indentation.amount * (depth)) {  // Non-matching indentation
            if (amount == indentation.amount * (depth - 1)) return false // De-indented line, return false

            val type = if (indentation.type == TokenType.SPACE) "spaces" else "tabs"
            throw error(peek(), "Invalid indentation. " +
                    "Expected ${indentation.amount} $type, found $amount.")
        }

        return true // Indentation is correct
    }

    /**
     * Consumes the indentation for the current line.
     * @return `true` if indentation is the same, `false` if it's a de-indent.
     */
    private fun consumeIndentation(): Boolean {
        val startLine = peek().line
        matchAmount(indentation.type!!) // Consume the indentation after it's validated

        if (peek().line != startLine) {
            // Line is empty, consume the indentation for the next line
            return if (isIndentation()) consumeIndentation() else false
        }

        return true
    }

    /**
     * Consumes the token (and indentation) if it's the same as the given type after the indentation.
     * @return `true` if the token was consumed, `false` if it wasn't.
     */
    private fun consumeAfterIndentation(type: TokenType): Boolean {
        if (isIndentation() && (peek(getIndentation()).type == type)) { // Indentation matches AND next token matches?
            consumeIndentation()
            consume(type, "CRITICAL ERROR: Found `${type.name}` then lost it? " +
                    "MAKE AN ISSUE ON https://github.com/MineStudio-Host/CraftLang WITH YOUR CODE.")
            return true
        }

        return false
    }

    /**
     * Gets the type of indentation based on the current line.
     * (or, if the indentation for this block is already set, returns that)
     */
    private fun indentationType(): Indentation {
        return if (indentation != Indentation.NONE) indentation else when (peek().type) {
            TokenType.TAB -> Indentation.TAB
            TokenType.SPACE -> {
                when (val amount = checkAmount(TokenType.SPACE)) {
                    4 -> Indentation.FOUR_SPACES
                    2 -> Indentation.TWO_SPACES
                    1 -> Indentation.ONE_SPACE
                    else -> throw error(peek(), "Invalid indentation. $amount spaces found. Allowed: 1, 2, 4.")
                }
            }
            else -> Indentation.NONE
        }
    }

    /**
     * Consumes all tokens of the given type in a row. (on the same line)
     * @return the amount of tokens consumed.
     */
    private fun matchAmount(type: TokenType): Int {
        var amount = 0
        val line = peek().line
        while (check(type)) {
            if (peek().line != line) break // Stop if the line changes
            advance()
            amount++
        }

        return amount
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }

        return false
    }

    /**
     * Consumes all tokens of the given type in a row. (on the same line)
     * @return the amount of tokens consumed.
     */
    private fun checkAmount(type: TokenType): Int {
        var count = 0
        val line = peek().line
        while (peek(count).type == type) {
            if (peek(count).line != line) break // Stop if the line changes
            count++
        }

        return count
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type == TokenType.EOF
    }

    /**
     * Peeks the token at the given amount of tokens ahead.
     * @param skip the amount of tokens to skip. 0 would be the next token.
     * @return the token at the given amount of tokens ahead.
     */
    private fun peek(skip: Int = 0): Token {
        return tokens[current + skip]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): ParseError {
        CraftLang.instance.error(token, message)
        return ParseError(message)
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {

            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.SET, TokenType.FOR, TokenType.IF, TokenType.WHILE, TokenType.PRINT, TokenType.RETURN -> return
                else -> advance()
            }
        }
    }
}