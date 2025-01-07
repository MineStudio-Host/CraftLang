package host.minestudio.craftlang.language.runtime

class CraftClass(
    val name: String,
    private val superClass: CraftClass?,
    private val methods: Map<String, CraftFunction>) : CraftCallable {
    fun findMethod(name: String): CraftFunction? {
        return methods[name] ?: superClass?.findMethod(name)
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
        val instance = CraftInstance(this)
        val initializer = findMethod("init")
        initializer?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    override fun arity(): Int {
        val initializer = findMethod("init")
        return initializer?.arity() ?: 0
    }

    override fun toString(): String {
        return name
    }
}