package host.minestudio.craftlang.language

import host.minestudio.craftlang.language.scanning.Token

class Environment(val enclosing: Environment? = null) {
    private val values = HashMap<String, Any?>(5)

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    private fun ancestor(distance: Int): Environment {
        var environment = this
        for (i in 0 until distance) {
            environment = environment.enclosing!!
        }
        return environment
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    fun get(name: Token): Any? {
        return values[name.lexeme] ?: enclosing?.get(name)
    }
}