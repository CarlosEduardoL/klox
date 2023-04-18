sealed class Stmt {
    data class Block(val statements: List<Stmt>): Stmt()
    data class Expression(val expr: Expr): Stmt()
    data class Print(val expr: Expr): Stmt()
    data class Var(val name: Token, val initializer: Expr): Stmt()
}