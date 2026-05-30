package heart.cli

fun main(args: Array<String>) {
    when {
        args.contains("--help") -> printHelp()
        args.contains("--version") -> println("heart-cli 0.1.0")
        else -> greet(args)
    }
}

private fun printHelp() {
    println("heart-cli: A small Kotlin command-line helper")
    println("Usage: java -jar heart-cli.jar [name] [--help] [--version]")
}

private fun greet(args: Array<String>) {
    val name = args.firstOrNull { it.isNotBlank() && !it.startsWith("--") } ?: "world"
    println("Hello, $name!")
}
