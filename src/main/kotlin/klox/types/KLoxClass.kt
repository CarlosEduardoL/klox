package klox.types


class KLoxClass(val name: String, private val superClass: KLoxClass?, private val methods: Map<String, KLoxFunction>) :
    KLoxCallable {
    override val arity: Int
        get() {
            val initializer = findMethod("init") ?: return 0
            return initializer.arity
        }

    override fun call(args: List<Any?>): Any {
        val instance = KLoxInstance(this)
        findMethod("init")?.bind(instance)?.call(args)
        return instance
    }


    override fun toString(): String {
        return "<class $name>"
    }

    fun findMethod(name: String): KLoxFunction? {
        return methods.getOrElse(name) { superClass?.methods?.getOrElse(name) { null } }
    }
}
