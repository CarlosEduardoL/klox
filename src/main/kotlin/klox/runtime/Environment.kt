package klox.runtime

import klox.compiletime.Token

class Environment(
    val enclosing: Environment? = null
) {
    private val values = mutableMapOf<String, Any?>()

    operator fun set(name: String, value: Any?) {
        values.put(name, value)
    }

    operator fun set(name: Token, value: Any?) {
        if (values.containsKey(name.lexeme)) {
            values[name.lexeme] = value
            return
        }
        if (enclosing != null) {
            enclosing[name] = value
            return
        }
        throw RuntimeError(
            name,
            "Undefined variable '${name.lexeme}'."
        )
    }

    operator fun set(distance: Int, name: Token, value: Any?) {
        ancestor(distance)!!.values[name.lexeme] = value
    }

    operator fun get(name: Token): Any? {
        if (values.containsKey(name.lexeme)) {
            return values[name.lexeme]
        }
        if (enclosing != null) return enclosing[name]
        throw RuntimeError(
            name,
            "Undefined variable '${name.lexeme}'."
        )
    }

    operator fun get(distance: Int, name: String?): Any? {
        return ancestor(distance)?.values?.get(name)
    }

    fun ancestor(distance: Int): Environment? {
        var environment: Environment? = this
        for (i in 0 until distance) {
            environment = environment?.enclosing
        }
        return environment
    }
}