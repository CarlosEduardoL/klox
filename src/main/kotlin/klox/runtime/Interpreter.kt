package klox.runtime

import klox.AST.Expr
import klox.AST.Stmt
import klox.compiletime.Token
import klox.compiletime.TokenType.*
import klox.types.KLoxCallable
import klox.types.KLoxClass
import klox.types.KLoxFunction
import klox.types.KLoxInstance
import runtimeError


val globals = run {
    val init = Environment()
    init["clock"] = object : KLoxCallable {
        override val arity: Int = 0
        override fun call(args: List<Any?>) = System.currentTimeMillis().toDouble() / 1000.0
        override fun toString(): String = "<native fn>"
    }
    init
}
private val locals: MutableMap<Expr, Int> = mutableMapOf()
private var environment = globals

fun interpret(statements: List<Stmt>) {
    try {
        for (statement in statements) {
            execute(statement)
        }
    } catch (error: RuntimeError) {
        runtimeError(error)
    }
}

private fun execute(stmt: Stmt): Unit = stmt.run {
    when (this) {
        is Stmt.Print -> println(expr.evaluate().stringify())
        is Stmt.Expression -> expr.evaluate()
        is Stmt.Var -> environment[name.lexeme] = initializer.evaluate()
        is Stmt.Block -> executeBlock(statements, Environment(environment))
        is Stmt.If -> if (condition.evaluate().isTruthy()) execute(thenBranch) else if (elseBranch != null) execute(
            elseBranch
        )

        is Stmt.While -> while (condition.evaluate().isTruthy()) execute(body)
        is Stmt.Function -> environment[name.lexeme] = KLoxFunction(this, environment, false)

        is Stmt.Return -> throw Return(value.evaluate())
        is Stmt.Class -> {
            val superclass: KLoxClass? = superClass?.evaluate()?.let {
                when (it) {
                    is KLoxClass -> it
                    else -> throw RuntimeError(superClass.name, "Superclass must be a class.")
                }
            }
            if (superclass != null) {
                environment = Environment(environment)
                environment["super"] = superclass
            }

            val klass = KLoxClass(name.lexeme,
                superclass,
                methods.associate { it.name.lexeme to KLoxFunction(it, environment, it.name.lexeme == "init") }
                    .toMutableMap())

            if (superclass != null) environment = environment.enclosing!!

            environment[name.lexeme] = klass
        }
    }
}

fun resolve(expr: Expr, depth: Int) {
    locals[expr] = depth
}

fun executeBlock(stmts: List<Stmt>, env: Environment) {
    val previous: Environment = environment
    try {
        environment = env
        for (statement in stmts) {
            execute(statement)
        }
    } finally {
        environment = previous
    }
}

private inline fun <R> Pair<Double, Double>.operate(operation: (Double, Double) -> R): R = operation(first, second)

private fun Expr?.evaluate(): Any? = when (this) {
    null -> null
    is Expr.Binary -> run<Any?> {
        val left = left.evaluate()
        val right = right.evaluate()
        when (operator.type) {
            MINUS -> checkNumberOperands(operator, left, right).operate(Double::minus)
            SLASH -> checkNumberOperands(operator, left, right).operate(Double::div)
            STAR -> checkNumberOperands(operator, left, right).operate(Double::times)
            PLUS -> if (left is Double && right is Double) left + right
            else if (left is String && right is String) "$left$right"
            else throw RuntimeError(operator, "Operands must be two numbers or two strings.")

            GREATER -> checkNumberOperands(operator, left, right).operate(Double::compareTo) > 0
            GREATER_EQUAL -> checkNumberOperands(operator, left, right).operate(Double::compareTo) >= 0
            LESS -> checkNumberOperands(operator, left, right).operate(Double::compareTo) < 0
            LESS_EQUAL -> checkNumberOperands(operator, left, right).operate(Double::compareTo) <= 0
            BANG_EQUAL -> !isEqual(left, right)
            EQUAL_EQUAL -> isEqual(left, right)
            else -> throw RuntimeException("Invalid Operator")
        }
    }

    is Expr.Grouping -> expr.evaluate()
    is Expr.Literal -> value
    is Expr.Unary -> when (operator.type) {
        MINUS -> -checkNumberOperand(operator, right.evaluate())
        BANG -> !(right.evaluate().isTruthy())
        else -> throw RuntimeException("There are just two Unary Operators")
    }

    is Expr.Var -> lookUpVariable(name, this)
    is Expr.Assign -> value.evaluate().apply {
        val distance = locals[this@evaluate]
        if (distance != null) {
            environment[distance, name] = value
        } else {
            globals[name] = value
        }
    }

    is Expr.Logical -> left.evaluate().let {
        if (operator.type == OR) {
            if (it.isTruthy()) return@let it
        } else {
            if (!it.isTruthy()) return@let it
        }
        return@let right.evaluate()
    }

    is Expr.Call -> callee.evaluate().let { function ->
        if (function is KLoxCallable) {
            if (args.size != function.arity) throw RuntimeError(
                paren, "Expected ${function.arity} arguments but got ${args.size}."
            )
            function.call(args.map { it.evaluate() })
        } else throw RuntimeError(paren, "Can only call functions and classes.")
    }

    is Expr.Get -> obj.evaluate().let {
        when (it) {
            is KLoxInstance -> it[name]
            else -> RuntimeError(name, "Only instances have properties.");
        }
    }

    is Expr.Set -> obj.evaluate().let {
        if (it !is KLoxInstance) throw RuntimeError(name, "Only instances have fields.")
        val value = value.evaluate()
        it[name] = value
        return@let value
    }

    is Expr.This -> lookUpVariable(keyword, this)
    is Expr.Super -> {
        val distance = locals[this]!!
        val superclass: KLoxClass = environment[distance, "super"] as KLoxClass
        val obj = environment[distance-1, "this"] as KLoxInstance
        superclass.findMethod(method.lexeme)
            ?.bind(obj)
            ?: throw RuntimeError(method, "Undefined property '${method.lexeme}'.")
    }
}

private fun lookUpVariable(name: Token, expr: Expr): Any? {
    val distance = locals[expr]
    return if (distance != null) {
        environment[distance, name.lexeme]
    } else {
        globals[name]
    }
}

private fun Any?.stringify(): String {
    if (this == null) return "nil"
    if (this is Double) {
        var text = this.toString()
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length - 2)
        }
        return text
    }
    return this.toString()
}

private fun checkNumberOperand(operator: Token, operand: Any?): Double {
    if (operand is Double) return operand
    throw RuntimeError(operator, "Operand must be a number.")
}

private fun checkNumberOperands(
    operator: Token, left: Any?, right: Any?
): Pair<Double, Double> {
    if (left is Double && right is Double) return Pair(left, right)
    throw RuntimeError(operator, "Operands must be numbers.")
}

private fun isEqual(a: Any?, b: Any?): Boolean {
    return if (a == null) b == null else a == b
}

private fun Any?.isTruthy(): Boolean {
    if (this == null) return false
    return if (this is Boolean) this else true
}
