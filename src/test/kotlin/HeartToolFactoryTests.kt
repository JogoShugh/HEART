import org.junit.jupiter.api.Test
import kotlinx.serialization.json.*
import java.net.http.HttpClient
import kotlin.test.assertEquals

class HeartToolFactoryTests {
    private val factory = HeartToolFactory(HttpClient.newHttpClient())

    @Test
    fun `factory creates sowSeed tool descriptor with correct parameters`() {
        // Setup schema for sowSeed
        val schema = buildJsonObject {
            put("description", "Sows seeds for a specific cultivar")
            putJsonObject("properties") {
                putJsonObject("plantCultivar") { put("type", "string") }
                putJsonObject("count") { put("type", "integer") }
            }
            putJsonArray("required") {
                add("plantCultivar")
                add("count")
            }
        }

        val tool = factory.createTool("sowSeed", "POST", "http://api/sowSeed", schema)

        // Verify Descriptor
        assertEquals("sowSeed", tool.descriptor.name)
        assertEquals(2, tool.descriptor.requiredParameters.size)
        assertEquals("plantCultivar", tool.descriptor.requiredParameters[0].name)
        assertEquals("count", tool.descriptor.requiredParameters[1].name)
        assertEquals("string", tool.descriptor.requiredParameters[0].type.name.lowercase())
    }
}