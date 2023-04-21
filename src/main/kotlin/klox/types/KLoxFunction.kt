package klox.types

import klox.AST.Stmt
import klox.runtime.Environment
import klox.runtime.Return
import klox.runtime.executeBlock

class KLoxFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
    private val isInitializer: Boolean
): KLoxCallable {

    override val arity: Int = declaration.params.size

    override fun call(args: List<Any?>): Any? {
        val environment = Environment(closure)
        for (i in declaration.params.indices) {
            environment[declaration.params[i].lexeme] = args[i]
        }
        try {
            executeBlock(declaration.body, environment)
        } catch (r: Return) {
            if (isInitializer) return closure[0, "this"]
            return r.value
        }
        if (isInitializer) return closure[0, "this"]
        return null
    }

    override fun toString(): String {
        return "<fn ${declaration.name.lexeme}>"
    }

    fun bind(instance: KLoxInstance): KLoxFunction {
        val env = Environment(closure)
        env["this"] = instance
        return KLoxFunction(declaration, env, isInitializer)
    }
}