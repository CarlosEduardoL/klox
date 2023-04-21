package klox.types

import klox.compiletime.Token
import klox.runtime.RuntimeError


class KLoxInstance(private val klass: KLoxClass) {

    private val fields: MutableMap<String, Any?> = mutableMapOf()

    operator fun get(name: Token): Any? {
        if (fields.containsKey(name.lexeme)) {
            return fields[name.lexeme]
        }

        val method = klass.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        throw RuntimeError(
            name,
            "Undefined property '" + name.lexeme + "'."
        )
    }

    override fun toString(): String {
        return klass.name + " instance"
    }

    operator fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }
}
