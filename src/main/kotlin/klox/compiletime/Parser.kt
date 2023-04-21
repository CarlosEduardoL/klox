package klox.compiletime

import error
import klox.AST.Expr
import klox.AST.Stmt
import klox.AST.Expr.Assign
import klox.AST.Expr.Logical
import klox.compiletime.TokenType.*


class Parser(private val tokens: List<Token>) {

    private class ParseError : RuntimeException()

    private var current: Int = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }
        return statements
    }

    private fun declaration(): Stmt? {
        return try {
            if (match(CLASS)) classDeclaration()
            else if (match(FUN)) function("function")
            else if (match(VAR)) varDeclaration()
            else statement()
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect class name.")

        var sup: Expr.Var? = null
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            sup = Expr.Var(previous())
        }

        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods: MutableList<Stmt.Function> = mutableListOf()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")

        return Stmt.Class(name, sup, methods)
    }

    private fun function(kind: String): Stmt.Function {
        val name = consume(IDENTIFIER, "Expect $kind name.")
        consume(LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters: MutableList<Token> = mutableListOf()
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }
                parameters.add(
                    consume(IDENTIFIER, "Expect parameter name.")
                )
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.")
        consume(LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
    }

    private fun statement(): Stmt {
        return if (match(FOR)) forStatement()
        else if (match(IF)) ifStatement()
        else if (match(PRINT)) printStatement()
        else if (match(RETURN)) returnStatement()
        else if (match(WHILE)) whileStatement()
        else if (match(LEFT_BRACE)) Stmt.Block(block())
        else expressionStatement()
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        var value: Expr? = null
        if (!check(SEMICOLON)) {
            value = expression()
        }

        consume(SEMICOLON, "Expect ';' after return value.")
        return Stmt.Return(keyword, value)
    }

    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'for'")
        val initializer = if (match(SEMICOLON)) null else if (match(VAR)) varDeclaration() else expressionStatement()
        val condition = if (check(SEMICOLON)) Expr.Literal(true) else expression()
        consume(SEMICOLON, "Expect ';' after loop condition.")
        val increment = if (check(RIGHT_PAREN)) null else expression()
        consume(RIGHT_PAREN, "Expect ')' after loop condition.")
        var body = statement()
        if (increment != null) {
            body = Stmt.Block(listOf(body, Stmt.Expression(increment)))
        }
        body = Stmt.While(condition, body)
        if (initializer != null) {
            body = Stmt.Block(listOf(initializer, body))
        }
        return body
    }

    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after 'while'.")
        val body = statement()
        return Stmt.While(condition, body)
    }

    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()
        val elseBranch = if (match(ELSE)) statement() else null

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            declaration()?.let(statements::add)
        }
        consume(RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value.")
        return Stmt.Print(value)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name.")
        var initializer: Expr = Expr.Literal(null)
        if (match(EQUAL)) {
            initializer = expression()
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.")
        return Stmt.Var(name, initializer)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression.")
        return Stmt.Expression(expr)
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = or()
        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()
            if (expr is Expr.Var) {
                val name: Token = expr.name
                return Assign(name, value)
            } else if (expr is Expr.Get) {
                return Expr.Set(expr.obj, expr.name, value)
            }
            error(equals, "Invalid assignment target.")
        }
        return expr
    }

    private inline fun logical(next: () -> Expr, op: TokenType): Expr {
        var expr: Expr = next()
        while (match(op)) {
            val operator = previous()
            val right: Expr = next()
            expr = Logical(expr, operator, right)
        }
        return expr
    }

    private fun or(): Expr = logical(::and, OR)

    private fun and(): Expr = logical(::equality, AND)

    private inline fun binaryExpr(next: () -> Expr, vararg operations: TokenType): Expr {
        var expr: Expr = next()
        while (match(*operations)) {
            val operator: Token = previous()
            val right: Expr = next()
            expr = Expr.Binary(expr, operator, right)
        }
        return expr
    }

    private fun equality(): Expr = binaryExpr(::comparison, BANG_EQUAL, EQUAL_EQUAL)

    private fun comparison(): Expr = binaryExpr(::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)

    private fun term(): Expr = binaryExpr(::factor, MINUS, PLUS)

    private fun factor(): Expr = binaryExpr(::unary, SLASH, STAR)

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }
        return call()
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            expr = if (match(LEFT_PAREN)) {
                finishCall(expr)
            } else if (match(DOT)) {
                val name = consume(
                    IDENTIFIER,
                    "Expect property name after '.'."
                )
                Expr.Get(expr, name)
            } else break
        }
        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments: MutableList<Expr> = mutableListOf()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression())
            } while (match(COMMA))
        }
        val paren = consume(
            RIGHT_PAREN,
            "Expect ')' after arguments."
        )
        return Expr.Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        return if (match(FALSE)) Expr.Literal(false)
        else if (match(TRUE)) Expr.Literal(true)
        else if (match(NIL)) Expr.Literal(null)
        else if (match(NUMBER, STRING)) Expr.Literal(previous().literal)
        else if (match(SUPER)) {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'")
            val method = consume(IDENTIFIER, "Expect superclass method name.")
            Expr.Super(keyword, method)
        }
        else if (match(THIS)) Expr.This(previous())
        else if (match(IDENTIFIER)) Expr.Var(previous())
        else if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            Expr.Grouping(expr)
        } else
            throw parseError(peek(), "Expect expression.")
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw parseError(peek(), message)
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type === SEMICOLON) return
            when (peek().type) {
                CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> return
                else -> {}
            }
            advance()
        }
    }

    private fun parseError(token: Token, message: String): ParseError {
        error(token, message)
        return ParseError()
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        return if (isAtEnd()) false else peek().type === type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type === EOF
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }
}