import klox.compiletime.*
import klox.runtime.RuntimeError
import klox.runtime.interpret
import java.nio.file.Files
import java.nio.file.Path
import java.util.Stack
import kotlin.system.exitProcess

var hadRuntimeError = false
var hadError = false

fun main(args: Array<String>) {
    if (args.size > 1){
        System.err.println("Usage: klox [script]")
        exitProcess(64)
    } else if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

fun runFile(script: String) {
    val content = Files.readString(Path.of(script))
    run(content)
    if (hadError) exitProcess(65)
    if (hadRuntimeError) System.exit(70)
}

private val contextTracker = Stack<Char>()
private val content = StringBuilder()
fun runPrompt() {
    while (true) {
        print("> ")
        val line = readlnOrNull() ?: break
        line.forEach { c->
            when(c) {
                '{', '(' -> contextTracker.add(c)
                ')' -> if (contextTracker.peek() == '(') contextTracker.pop() else error(0, "expected '}' not ')'")
                '}' -> if (contextTracker.peek() == '{') contextTracker.pop() else error(0, "expected ')' not '}'")
            }
        }
        if (hadError) exitProcess(65)
        content.append(line)
        if (contextTracker.size == 0) {
            run(content.toString())
            content.clear()
        }
    }
}

fun run(source: String) {
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    val parser = Parser(tokens)
    val statements = parser.parse()
    resolve(statements)
    
    if (hadError) return
    interpret(statements)
}

fun error(line: Int, message: String) {
    report(line, "", message)
}

fun error(token: Token, message: String) {
    if (token.type === TokenType.EOF) {
        report(token.line, " at end", message)
    } else {
        report(token.line, " at '" + token.lexeme + "'", message)
    }
}

fun runtimeError(error: RuntimeError) {
    System.err.println(
        """
            ${error.message}
            [line ${error.token.line}]
            """.trimIndent()
    )
    hadRuntimeError = true
}

private fun report(
    line: Int, where: String,
    message: String
) {
    System.err.println(
        "[line $line] Error$where: $message"
    )
    hadError = true
}