package host.minestudio.craftlang.language.parsing

import host.minestudio.craftlang.CraftLang
import host.minestudio.craftlang.language.expr.Expr
import host.minestudio.craftlang.language.scanning.Token
import host.minestudio.craftlang.language.scanning.TokenType
import host.minestudio.craftlang.language.stmt.Statement


class Parser(private val tokens: List<Token>) {
    private class ParseError(message: String) : RuntimeException(message)

    private var current = 0

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
            if (match(TokenType.COLON))
                Expr.Variable(consume(TokenType.IDENTIFIER, "Expect superclass name."))
            else null

        consume(TokenType.LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<Statement.FunctionStatement>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            consume(TokenType.FUN, "Expect method declaration.")
            methods.add(function("method"))
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after class body.")

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

        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")

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
        if (match(TokenType.LEFT_BRACE)) return Statement.BlockStatement(block())

        return expressionStatement()
    }

    private fun ifStatement(): Statement {
        val condition = expression()

        val thenBranch = statement()
        var elseBranch: Statement? = null
        if (match(TokenType.ELSE)) {
            elseBranch = statement()
        }

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

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration())
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
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




    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }

        return false
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

    private fun peek(): Token {
        return tokens[current]
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