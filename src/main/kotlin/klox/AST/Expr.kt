package klox.AST

import klox.compiletime.Token

sealed class Expr {
    data class Logical(val left: Expr, val operator: Token, val right: Expr): Expr()
    data class Assign(val name: Token, val value: Expr): Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    data class Call(val callee: Expr, val paren: Token, val args: List<Expr>): Expr()
    data class Grouping(val expr: Expr) : Expr()
    data class Literal(val value: Any?) : Expr()
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class Var(val name: Token): Expr()
    data class Get(val obj: Expr, val name: Token): Expr()
    data class Set(val obj: Expr, val name: Token, val value: Expr): Expr()
    data class This(val keyword: Token): Expr()
    data class Super(val keyword: Token, val method: Token): Expr()
}