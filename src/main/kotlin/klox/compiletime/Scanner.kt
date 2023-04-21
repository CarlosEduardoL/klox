package klox.compiletime

import error
import klox.compiletime.TokenType.*

private val keywords = mapOf(
    Pair("and", AND),
    Pair("class", CLASS),
    Pair("else", ELSE),
    Pair("false", FALSE),
    Pair("for", FOR),
    Pair("fun", FUN),
    Pair("if", IF),
    Pair("nil", NIL),
    Pair("or", OR),
    Pair("print", PRINT),
    Pair("return", RETURN),
    Pair("super", SUPER),
    Pair("this", THIS),
    Pair("true", TRUE),
    Pair("var", VAR),
    Pair("while", WHILE)
)


class Scanner(private val source: String) {

    private val tokens = mutableListOf<Token>()

    private var start = 0
    private var current = 0
    private var line = 1

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current
            scanToken()
        }

        tokens.add(Token(EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '!' -> addToken(if (match('=')) BANG_EQUAL else BANG)
            '=' -> addToken(if (match('=')) EQUAL_EQUAL else EQUAL)
            '<' -> addToken(if (match('=')) LESS_EQUAL else LESS)
            '>' -> addToken(if (match('=')) GREATER_EQUAL else GREATER)
            '/' -> if (match('/')) {
                while (peek() != '\n' && !isAtEnd()) advance()
            } else {
                addToken(SLASH)
            }
            '"' -> string()
            in '0'..'9' -> number()
            in 'A'..'Z' -> identifier()
            in 'a'..'z' -> identifier()
            ' '->{}
            '\n'-> line+=1
            else -> error(line, "Unexpected character $c.")
        }
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()
        val text = source.substring(start, current)
        var type = keywords[text]
        if (type == null) type = IDENTIFIER
        addToken(type)
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_'
    }

    private fun number() {
        while (peek() in '0'..'9') advance()

        // Look for a fractional part.
        if (peek() == '.' && (peekNext() in '0'..'9')) {
            // Consume the "."
            advance()
            while (peek() in '0'..'9') advance()
        }
        addToken(NUMBER, source.substring(start, current).toDouble())
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }
        if (isAtEnd()) {
            error(line, "Unterminated string.")
            return
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        addToken(STRING, value)
    }

    private fun peek(): Char {
        return if (isAtEnd()) '\u0000' else source[current]
    }

    private fun peekNext(): Char {
        return if (current + 1 >= source.length) '\u0000' else source[current + 1]
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        return true
    }

    private fun advance(): Char {
        return source[current++]
    }

    private fun addToken(type: TokenType) {
        addToken(type, null)
    }

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line))
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }
}