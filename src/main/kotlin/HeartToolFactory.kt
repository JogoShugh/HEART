import ai.koog.agents.core.tools.*
import io.github.optimumcode.json.schema.JsonSchema
import kotlinx.serialization.json.*
import java.net.http.HttpClient

/**
 * Factory to produce HeartTools from HAL Schema Forms.
 */
class HeartToolFactory(
    private val httpClient: HttpClient
) {

    fun createTool(name: String, method: String, target: String, schemaJson: JsonObject): HeartTool {

        // 1. Map Schema Properties to ToolParameterDescriptors
        val properties = schemaJson["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val requiredList = schemaJson["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

        val params = properties.entries.map { (key, value) ->
            val type = value.jsonObject["type"]?.jsonPrimitive?.content ?: "string"
            ToolParameterDescriptor(
                name = key,
                description = value.jsonObject["title"]?.jsonPrimitive?.content ?: "No description",
                type = mapHalTypeToKoog(type)
            )
        }

        // 2. Define Descriptor
        val descriptor = ToolDescriptor(
            name = name,
            description = schemaJson["description"]?.jsonPrimitive?.content ?: "Dynamic H.E.A.R.T. action: $name",
            requiredParameters = params.filter { requiredList.contains(it.name) },
            optionalParameters = params.filter { !requiredList.contains(it.name) }
        )

        // 3. Compile Schema for OptimumCode (The JIT validation engine)
        val schema = JsonSchema.fromJsonElement(schemaJson)

        return HeartTool(descriptor, method, target, schema, httpClient)
    }

    private fun mapHalTypeToKoog(type: String): ToolParameterType = when (type) {
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "boolean" -> ToolParameterType.Boolean
        else -> ToolParameterType.String
    }
}