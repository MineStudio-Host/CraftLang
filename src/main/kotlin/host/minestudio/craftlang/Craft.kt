package host.minestudio.craftlang

import host.minestudio.craftlang.language.parsing.Parser
import host.minestudio.craftlang.language.runtime.Resolver
import host.minestudio.craftlang.language.scanning.Scanner
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Temporary main function, this is used for testing purposes until the project is connected to Minestom.
 */
fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: java -jar CraftLang.jar [path/to/script.craft]")
        exitProcess(64)
    }

    CraftLang.init(Path.of("run/"), LoggerFactory.getLogger(CraftLang::class.java))

    if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}

private fun runFile(path: String) {
    val filePath = Path.of(path)
    if (!Files.exists(filePath)) {
        println("File not found: $filePath")
        exitProcess(66)
    }
    val bytes = Files.readAllBytes(filePath)
    run(String(bytes))
}

private fun runPrompt() {
    val input = System.`in`
    val reader = input.bufferedReader()

    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        runFile(line)

        CraftLang.instance.hadError = false
    }
}

private fun run(source: String) {
    var time = System.nanoTime()
    val scanner = Scanner(source)
    val tokens = scanner.scanTokens()
    println("Scanned in ${(System.nanoTime() - time) / 1000000.0}ms")

    time = System.nanoTime()
    val parser = Parser(tokens)
    val statements = parser.parse()
    println("Parsed in ${(System.nanoTime() - time) / 1000000.0}ms")

    if (CraftLang.instance.hadError) exitProcess(65)

    time = System.nanoTime()
    val resolver = Resolver(interpreter)
    resolver.resolve(statements)
    println("Resolved in ${(System.nanoTime() - time) / 1000000.0}ms")

    if (CraftLang.instance.hadError) exitProcess(65)

    time = System.nanoTime()
    interpreter.interpret(statements)
    println("Interpreted in ${(System.nanoTime() - time) / 1000000.0}ms")

    if (CraftLang.instance.hadRuntimeError) exitProcess(70)
}
