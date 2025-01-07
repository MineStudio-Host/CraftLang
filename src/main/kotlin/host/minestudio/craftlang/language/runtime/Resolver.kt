package host.minestudio.craftlang.language.runtime

import host.minestudio.craftlang.CraftLang
import host.minestudio.craftlang.language.expr.Expr
import host.minestudio.craftlang.language.scanning.Token
import host.minestudio.craftlang.language.stmt.Statement
import java.util.*

class Resolver(private val interpreter: Interpreter) : Expr.Visitor<Unit>, Statement.Visitor<Unit> {
    private enum class FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        INITIALIZER
    }

    private enum class ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    private fun beginScope() {
        scopes.push(HashMap())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return

        val scope = scopes.peek()
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return

        val scope = scopes.peek()
        scope[name.lexeme] = true
    }


    fun resolve(statements: List<Statement>) {
        for (statement in statements) {
            resolve(statement)
        }
    }

    private fun resolve(statement: Statement) {
        statement.accept(this)
    }

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (name.lexeme in scopes[i]) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }

    private fun resolveFunction(stmt: Statement.FunctionStatement, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        for (param in stmt.parameters) {
            declare(param)
            define(param)
        }
        resolve(stmt.body)
        endScope()

        currentFunction = enclosingFunction
    }


    override fun visitBlockStmt(stmt: Statement.BlockStatement) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visitClassStmt(stmt: Statement.ClassStatement) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)
        define(stmt.name)

        if (stmt.superclass != null) {
            if (stmt.name.lexeme == stmt.superclass.name.lexeme) {
                CraftLang.instance.error(stmt.superclass.name, "A class cannot inherit from itself.")
            }

            currentClass = ClassType.SUBCLASS
            resolve(stmt.superclass)

            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        for (method in stmt.methods) {
            val declaration = if (method.name.lexeme == "init") FunctionType.INITIALIZER else FunctionType.METHOD
            resolveFunction(method, declaration)
        }

        endScope()

        if (stmt.superclass != null) endScope()

        currentClass = enclosingClass
    }

    override fun visitFunctionStmt(stmt: Statement.FunctionStatement) {
        declare(stmt.name)
        define(stmt.name)

        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    override fun visitExpressionStmt(stmt: Statement.ExpressionStatement) {
        resolve(stmt.expression)
    }

    override fun visitIfStmt(stmt: Statement.IfStatement) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        stmt.elseBranch?.let { resolve(it) }
    }

    override fun visitPrintStmt(stmt: Statement.PrintStatement) {
        resolve(stmt.expression)
    }

    override fun visitReturnStmt(stmt: Statement.ReturnStatement) {
        if (currentFunction == FunctionType.NONE) {
            CraftLang.instance.error(stmt.keyword, "Cannot return from top-level code.")
        }

        if (stmt.value != null && currentFunction == FunctionType.INITIALIZER) {
            CraftLang.instance.error(stmt.keyword, "Cannot return a value from an initializer.")
        }

        stmt.value?.let { resolve(it) }
    }

    override fun visitVarStmt(stmt: Statement.VarStatement) {
        declare(stmt.name)
        stmt.initializer?.let { resolve(it) }
        define(stmt.name)
    }

    override fun visitWhileStmt(stmt: Statement.WhileStatement) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }


    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        expr.arguments.forEach { resolve(it) }
    }

    override fun visitGetExpr(expr: Expr.Get) {
        resolve(expr.obj)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {
        // Do nothing
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.obj)
    }

    override fun visitSuperExpr(expr: Expr.Super) {
        if (currentClass == ClassType.NONE) {
            CraftLang.instance.error(expr.keyword, "Cannot use 'super' outside of a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
            CraftLang.instance.error(expr.keyword, "Cannot use 'super' in a class with no superclass.")
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitThisExpr(expr: Expr.This) {
        if (currentClass == ClassType.NONE) {
            CraftLang.instance.error(expr.keyword, "Cannot use 'this' outside of a class.")
            return
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if (!scopes.isEmpty() && scopes.peek()[expr.name.lexeme] == false) {
            CraftLang.instance.error(expr.name, "Cannot read local variable in its own initializer.")
        }

        resolveLocal(expr, expr.name)
    }
}