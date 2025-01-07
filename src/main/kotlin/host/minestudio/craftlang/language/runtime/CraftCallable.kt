package host.minestudio.craftlang.language.runtime

interface CraftCallable {
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
    fun arity(): Int
}