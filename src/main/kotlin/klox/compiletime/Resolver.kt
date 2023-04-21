package klox.compiletime

import error
import klox.AST.Expr
import klox.AST.Stmt
import java.util.*


private enum class FunctionType {
    NONE, FUNCTION, INITIALIZER, METHOD
}

private enum class ClassType {
    NONE, CLASS, SUBCLASS
}

private var currentClass = ClassType.NONE
private var currentFunction = FunctionType.NONE

private val scopes: Stack<MutableMap<String, Boolean>> = Stack()

private fun onStmt(stmt: Stmt?): Unit = stmt.run {
    when (this) {
        is Stmt.Block -> scoped { resolve(statements) }
        is Stmt.Var -> {
            declare(name)
            onExpr(initializer)
            define(name)
        }

        is Stmt.Function -> {
            declare(name)
            define(name)
            resolveFunction(this, FunctionType.FUNCTION)
        }

        is Stmt.Expression -> onExpr(expr)
        is Stmt.If -> {
            onExpr(condition)
            onStmt(thenBranch)
            onStmt(elseBranch)
        }

        is Stmt.Print -> onExpr(expr)
        is Stmt.Return -> {
            if (currentFunction == FunctionType.NONE) {
                error(keyword, "Can't return from top-level code.")
            }
            if (value != null) {
                if (currentFunction == FunctionType.INITIALIZER) {
                    error(keyword, "Can't return a value from an initializer.")
                }
                onExpr(value)
            }
        }

        is Stmt.While -> {
            onExpr(condition)
            onStmt(body)
        }

        is Stmt.Class -> {
            val enclosingClass = currentClass
            currentClass = ClassType.CLASS
            declare(name)
            define(name)
            superClass?.let { if (it.name.lexeme == name.lexeme) error(it.name, "A class can't inherit from itself.") }
            superClass?.let {
                currentClass = ClassType.SUBCLASS
                onExpr(it)
            }
            superClass?.let {
                beginScope()
                scopes.peek().put("super", true)
            }
            scoped {
                scopes.peek()["this"] = true
                methods.map {
                    resolveFunction(
                        it, if (it.name.lexeme == "init") FunctionType.INITIALIZER else FunctionType.METHOD
                    )
                }
            }
            superClass?.let { endScope() }
            currentClass = enclosingClass
        }

        else -> {}
    }
}

private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
    val enclosingFunction = currentFunction
    currentFunction = type
    scoped {
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
    }
    currentFunction = enclosingFunction
}

private fun onExpr(expression: Expr): Unit = expression.run {
    when (this) {
        is Expr.Var -> {
            if (!scopes.isEmpty() && scopes.peek()[name.lexeme] == false) {
                error(name, "Can't read local variable in its own initializer.")
            }
            resolveLocal(expression, name)
        }

        is Expr.Assign -> {
            onExpr(value)
            resolveLocal(expression, name)
        }

        is Expr.Binary -> {
            onExpr(left)
            onExpr(right)
        }

        is Expr.Call -> {
            onExpr(callee)
            args.forEach(::onExpr)
        }

        is Expr.Grouping -> onExpr(expr)
        is Expr.Logical -> {
            onExpr(left)
            onExpr(right)
        }

        is Expr.Unary -> onExpr(right)
        is Expr.Get -> onExpr(obj)
        is Expr.Set -> {
            onExpr(value)
            onExpr(obj)
        }

        is Expr.This -> {
            if (currentClass == ClassType.NONE) {
                error(keyword, "Can't use 'this' outside of a class.")
            }
            resolveLocal(this, keyword)
        }

        is Expr.Super -> {
            if (currentClass == ClassType.NONE) {
                error(
                    keyword, "Can't use 'super' outside of a class."
                )
            } else if (currentClass != ClassType.SUBCLASS) {
                error(
                    keyword, "Can't use 'super' in a class with no superclass."
                )
            }
            resolveLocal(this, keyword)
        }

        else -> {}
    }
}

private fun resolveLocal(expr: Expr, name: Token) {
    for (i in scopes.indices.reversed()) {
        if (scopes[i].containsKey(name.lexeme)) {
            klox.runtime.resolve(expr, scopes.size - 1 - i)
            return
        }
    }
}

private inline fun scoped(lambda: () -> Unit) {
    beginScope()
    lambda()
    endScope()
}

fun resolve(stmts: List<Stmt>) = stmts.forEach(::onStmt)

private fun beginScope() {
    scopes.push(mutableMapOf())
}

private fun endScope() {
    scopes.pop()
}

private fun declare(name: Token) {
    if (scopes.isEmpty()) return
    val scope = scopes.peek()
    if (scope.containsKey(name.lexeme)) error(name, "Already a variable with this name in this scope.")
    scope[name.lexeme] = false
}

private fun define(name: Token) {
    if (scopes.isEmpty()) return
    scopes.peek()[name.lexeme] = true
}
