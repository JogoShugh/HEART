package heart.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun `main should greet provided name`() {
        val output = captureOutput { main(arrayOf("Kotlin")) }
        assertEquals("Hello, Kotlin!\n", output)
    }

    @Test
    fun `main should default to world when no args provided`() {
        val output = captureOutput { main(emptyArray()) }
        assertEquals("Hello, world!\n", output)
    }

    private fun captureOutput(block: () -> Unit): String {
        val outputStream = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(java.io.PrintStream(outputStream))
        return try {
            block()
            outputStream.toString()
        } finally {
            System.setOut(originalOut)
        }
    }
}
