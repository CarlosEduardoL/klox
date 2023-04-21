package klox.types

interface KLoxCallable {
    val arity: Int
    fun call(args: List<Any?>): Any?
}