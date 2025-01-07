package host.minestudio.craftlang.language.stmt

import host.minestudio.craftlang.language.expr.Expr
import host.minestudio.craftlang.language.scanning.Token

abstract class Statement {
    class BlockStatement(val statements: List<Statement>) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitBlockStmt(this)
        }
    }

    class ClassStatement(val name: Token, val superclass: Expr.Variable?, val methods: List<FunctionStatement>) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitClassStmt(this)
        }
    }

    class ExpressionStatement(val expression: Expr) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitExpressionStmt(this)
        }
    }

    class FunctionStatement(val name: Token, val parameters: List<Token>, val body: List<Statement>) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitFunctionStmt(this)
        }
    }

    class IfStatement(val condition: Expr, val thenBranch: Statement, val elseBranch: Statement?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitIfStmt(this)
        }
    }

    class PrintStatement(val expression: Expr) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitPrintStmt(this)
        }
    }

    class ReturnStatement(val keyword: Token, val value: Expr?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitReturnStmt(this)
        }
    }

    class VarStatement(val name: Token, val initializer: Expr?) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitVarStmt(this)
        }
    }

    class WhileStatement(val condition: Expr, val body: Statement) : Statement() {
        override fun <R> accept(visitor: Visitor<R>): R {
            return visitor.visitWhileStmt(this)
        }
    }

    abstract fun <R> accept(visitor: Visitor<R>): R?


    interface Visitor<R> {
        fun visitBlockStmt(stmt: BlockStatement): R
        fun visitClassStmt(stmt: ClassStatement): R
        fun visitExpressionStmt(stmt: ExpressionStatement): R
        fun visitFunctionStmt(stmt: FunctionStatement): R
        fun visitIfStmt(stmt: IfStatement): R
        fun visitPrintStmt(stmt: PrintStatement): R
        fun visitReturnStmt(stmt: ReturnStatement): R
        fun visitVarStmt(stmt: VarStatement): R
        fun visitWhileStmt(stmt: WhileStatement): R
    }
}