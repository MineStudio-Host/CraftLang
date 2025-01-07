package host.minestudio.craftlang.language.runtime

import host.minestudio.craftlang.CraftLang
import host.minestudio.craftlang.language.Environment
import host.minestudio.craftlang.language.error.CraftReturn
import host.minestudio.craftlang.language.error.RuntimeError
import host.minestudio.craftlang.language.expr.Expr
import host.minestudio.craftlang.language.scanning.Token
import host.minestudio.craftlang.language.scanning.TokenType
import host.minestudio.craftlang.language.stmt.Statement

private val numberOperators = setOf(TokenType.MINUS, TokenType.SLASH, TokenType.STAR,
    TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)

class Interpreter : Expr.Visitor<Any>, Statement.Visitor<Unit> {
    private val globals = Environment()
    private var environment = globals
    private val locals = mutableMapOf<Expr, Int>()

    fun interpret(statements: List<Statement>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RuntimeError) {
            CraftLang.instance.runtimeError(error)
        }
    }

    private fun execute(statement: Statement) {
        statement.accept(this)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) {
            environment.getAt(distance, name.lexeme)
        } else {
            globals.get(name)
        }
    }


    private fun stringify(obj: Any?): String {
        if (obj == null) return "null"

        if (obj is Double) {
            var text = obj.toString()
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length - 2)
            }
            return text
        }

        return obj.toString()
    }

    private fun evaluate(expr: Expr): Any? {
        return expr.accept(this)
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        if (expr.operator.type in numberOperators) {
            checkNumberOperands(expr.operator, left, right)
        }

        return when (expr.operator.type) {
            TokenType.MINUS -> left as Double - right as Double
            TokenType.PLUS -> {
                return if (left is Double && right is Double) {
                    left + right
                } else if (left is String || right is String) {
                    stringify(left) + stringify(right)
                } else throw RuntimeError(expr.operator, "Can't add a ${left?.javaClass?.simpleName} to a ${right?.javaClass?.simpleName}")
            }
            TokenType.SLASH -> {
                left as Double / right as Double
            }
            TokenType.STAR -> left as Double * right as Double
            TokenType.GREATER -> left as Double > right as Double
            TokenType.GREATER_EQUAL -> left as Double >= right as Double
            TokenType.LESS -> (left as Double) < (right as Double)
            TokenType.LESS_EQUAL -> left as Double <= right as Double
            TokenType.BANG_EQUAL -> !isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            else -> null
        }
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)

        if (callee !is CraftCallable) {
            throw RuntimeError(expr.paren, "Can only call functions and classes.")
        }
        
        if (expr.arguments.size != callee.arity()) {
            throw RuntimeError(expr.paren, "Expected ${callee.arity()} arguments but got ${expr.arguments.size}.")
        }

        return callee.call(this, expr.arguments.map { evaluate(it) })
    }

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val obj = evaluate(expr.obj)
        if (obj is CraftInstance) {
            return obj.get(expr.name)
        }
        throw RuntimeError(expr.name, "Only instances have properties.")
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
        return evaluate(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): Any? {
        return expr.value
    }

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left
        } else {
            if (!isTruthy(left)) return left
        }

        return evaluate(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val obj = evaluate(expr.obj) as? CraftInstance
            ?: throw RuntimeError(expr.name, "Only instances have fields.")
        val value = evaluate(expr.value)
        obj.set(expr.name, value)
        return value
    }

    override fun visitSuperExpr(expr: Expr.Super): Any {
        val distance = locals[expr]
        val superclass = environment.getAt(distance!!, "super") as CraftClass
        val obj = environment.getAt(distance - 1, "this") as CraftInstance
        val method = superclass.findMethod(expr.method.lexeme)
            ?: throw RuntimeError(expr.method, "Undefined property '${expr.method.lexeme}'.")
        return method.bind(obj)
    }

    override fun visitThisExpr(expr: Expr.This): Any? {
        return lookUpVariable(expr.keyword, expr)
    }

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.MINUS -> {
                checkNumberOperand(expr.operator, right)
                -(right as Double)
            }
            TokenType.BANG -> {
                !isTruthy(right)
            }
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? {
        return lookUpVariable(expr.name, expr)
    }


    override fun visitBlockStmt(stmt: Statement.BlockStatement) {
        val blockEnvironment = Environment(environment)
        executeBlock(stmt.statements, blockEnvironment)
    }

    override fun visitClassStmt(stmt: Statement.ClassStatement) {
        val superclass = if (stmt.superclass != null) {
            val superclass = evaluate(stmt.superclass)
            if (superclass !is CraftClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
            }
            superclass
        } else null

        environment.define(stmt.name.lexeme, null)

        if (stmt.superclass != null) {
            environment = Environment(environment)
            environment.define("super", superclass)
        }

        val methods = mutableMapOf<String, CraftFunction>()
        for (method in stmt.methods) {
            val function = CraftFunction(method, environment, method.name.lexeme == "init")
            methods[method.name.lexeme] = function
        }

        val klass = CraftClass(stmt.name.lexeme, superclass, methods)

        if (superclass != null) {
            environment = environment.enclosing!!
        }

        environment.define(stmt.name.lexeme, klass)

    }

    override fun visitExpressionStmt(stmt: Statement.ExpressionStatement) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Statement.FunctionStatement) {
        val function = CraftFunction(stmt, environment)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visitIfStmt(stmt: Statement.IfStatement) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        } else {
            stmt.elseBranch?.let { execute(it) }
        }
    }

    override fun visitPrintStmt(stmt: Statement.PrintStatement) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitReturnStmt(stmt: Statement.ReturnStatement) {
        throw CraftReturn(if (stmt.value == null) null else evaluate(stmt.value))
    }

    override fun visitVarStmt(stmt: Statement.VarStatement) {
        var value: Any? = null
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer)
        }

        environment.define(stmt.name.lexeme, value)
    }

    override fun visitWhileStmt(stmt: Statement.WhileStatement) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }


    private fun isTruthy(obj: Any?): Boolean {
        return when (obj) {
            is Boolean -> obj
            null -> false
            else -> true
        }
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false

        return a == b
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(operator, "Operand must be a number.")
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left is Double && right is Double) return
        throw RuntimeError(operator, "Operands must be numbers.")
    }

    fun executeBlock(statements: List<Statement>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            this.environment = previous
        }
    }
}