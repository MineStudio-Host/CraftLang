package host.minestudio.craftlang

import host.minestudio.craftlang.language.error.RuntimeError
import host.minestudio.craftlang.language.runtime.Interpreter
import host.minestudio.craftlang.language.scanning.Token
import host.minestudio.craftlang.language.scanning.TokenType
import org.slf4j.Logger
import java.nio.file.Path


private lateinit var INSTANCE: CraftLang
private val LOGGER: Logger
    get() = INSTANCE.logger
val interpreter: Interpreter = Interpreter()

class CraftLang private constructor(val path: Path, val logger: Logger) {
    companion object {
        fun init(path: Path, logger: Logger) {
            if (::INSTANCE.isInitialized) {
                throw IllegalStateException("CraftLang is already initialized")
            }

            INSTANCE = CraftLang(path, logger)


            val time = System.nanoTime()
            LOGGER.info("Setting up CraftLang...")

            INSTANCE.registerEvents()

            LOGGER.info("CraftLang set up in ${(System.nanoTime() - time) / 1000000.0}ms")
        }

        val instance: CraftLang
            get() = INSTANCE
        val logger: Logger
            get() = LOGGER
    }

    var hadError: Boolean = false
    var hadRuntimeError: Boolean = false

    fun registerEvents() {
        LOGGER.info("Registering events...")

        // Register events here

        LOGGER.info("Events registered")
    }


    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun error(token: Token, message: String) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message)
        } else {
            report(token.line, " at '${token.lexeme}'", message)
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

    private fun report(line: Int, where: String, message: String) {
        println(
            "[line $line] Error$where: $message"
        )
        hadError = true
    }

}