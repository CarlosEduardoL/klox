private var environment = Environment()

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
    }
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

private fun Expr?.evaluate(): Any? = when (this) {
    null -> null
    is Expr.Binary -> run<Any?> {
        val left = left.evaluate()
        val right = right.evaluate()
        when (operator.type) {
            TokenType.MINUS -> checkNumberOperands(operator, left, right).run {
                first - second
            }

            TokenType.SLASH -> checkNumberOperands(operator, left, right).run {
                first / second
            }

            TokenType.STAR -> checkNumberOperands(operator, left, right).run {
                first * second
            }

            TokenType.PLUS -> if (left is Double && right is Double) left + right
            else if (left is String && right is String) "$left$right"
            else throw RuntimeError(
                operator,
                "Operands must be two numbers or two strings."
            )

            TokenType.GREATER -> checkNumberOperands(operator, left, right).run {
                first > second
            }

            TokenType.GREATER_EQUAL -> checkNumberOperands(operator, left, right).run {
                first >= second
            }

            TokenType.LESS -> checkNumberOperands(operator, left, right).run {
                first < second
            }

            TokenType.LESS_EQUAL -> checkNumberOperands(operator, left, right).run {
                first <= second
            }

            TokenType.BANG_EQUAL -> !isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            else -> throw RuntimeException("Invalid Operator")
        }
    }

    is Expr.Grouping -> expr.evaluate()
    is Expr.Literal -> value
    is Expr.Unary -> when (operator.type) {
        TokenType.MINUS -> -checkNumberOperand(operator, right.evaluate())
        TokenType.BANG -> !(right.evaluate().isTruthy())
        else -> throw RuntimeException("There are just two Unary Operators")
    }

    is Expr.Var -> environment[name]
    is Expr.Assign -> value.evaluate().apply { environment[name] = this }
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
    operator: Token,
    left: Any?, right: Any?
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
