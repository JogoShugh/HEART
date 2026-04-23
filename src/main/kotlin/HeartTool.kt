package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.*
import ai.koog.serialization.TypeToken
import io.github.optimumcode.json.schema.ErrorCollector
import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The "Bag of Config" tool for the H.E.A.R.T. protocol.
 * Validates against a schema and fires a request.
 */
class HeartTool(
    descriptor: ToolDescriptor,
    private val method: String,
    private val target: String,
    private val schema: JsonSchema,
    private val httpClient: HttpClient
) : Tool<Map<String, String>, String>(
    argsType = TypeToken.of(Map::class.java),
    resultType = TypeToken.of(String::class.java),
    descriptor = descriptor
) {
    override suspend fun execute(args: Map<String, String>): String {
        // 1. Convert Map to JsonElement natively
        val jsonArgs = Json.parseToJsonElement(Json.encodeToString(args)).jsonObject

        // 2. Gatekeeper: Use ErrorCollector to capture details
        val errors = mutableListOf<ValidationError>()
        val isValid = schema.validate(jsonArgs, errors::add)

        if (!isValid) {
            // Collect the errors from the collector
            val errorMessages = errors.joinToString { it.message }
            throw IllegalArgumentException("H.E.A.R.T. Validation failed: $errorMessages")
        }

        // 3. Passthrough: Execute Remote Action (only if valid)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(target))
            .header("Content-Type", "application/json")
            .method(method.uppercase(), HttpRequest.BodyPublishers.ofString(jsonArgs.toString()))
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
    }
}