package host.minestudio.craftlang.language.runtime

import host.minestudio.craftlang.language.Environment
import host.minestudio.craftlang.language.error.CraftReturn
import host.minestudio.craftlang.language.stmt.Statement

class CraftFunction(
    private val declaration: Statement.FunctionStatement,
    private val closure: Environment,
    private val isInitializer: Boolean = false
) : CraftCallable {
    fun bind(instance: CraftInstance): CraftFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return CraftFunction(declaration, environment, isInitializer)
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in 0 until declaration.parameters.size) {
            environment.define(declaration.parameters[i].lexeme, arguments[i])
        }

        try {
            interpreter.executeBlock(declaration.body, environment)
        } catch (returnValue: CraftReturn) {
            if (isInitializer) return closure.getAt(0, "this")

            return returnValue.value
        }

        if (isInitializer) return closure.getAt(0, "this")
        return null
    }

    override fun arity(): Int {
        return declaration.parameters.size
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme}>"
    }
}