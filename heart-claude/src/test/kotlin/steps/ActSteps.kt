package steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActSteps(private val ctx: ClientContext) {

    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val jsonMapper = ObjectMapper()

    @Given("the service returns the following on enter:")
    fun givenEnterResponse(body: String) {
        ctx.enterBody = jsonMapper.writeValueAsString(yamlMapper.readTree(body))
    }

    @And("acting on {string} returns:")
    fun givenActResponse(@Suppress("UNUSED_PARAMETER") rel: String, body: String) {
        ctx.actBody = jsonMapper.writeValueAsString(yamlMapper.readTree(body))
    }

    @When("the client enters the service")
    fun whenClientEnters() {
        ctx.fakeTransport.enqueue(ctx.enterBody, ctx.contentType)
        ctx.current = ctx.client.enter("https://example.com/")
    }

    @And("the client acts on {string} with:")
    fun whenClientActs(rel: String, body: String) {
        ctx.fakeTransport.enqueue(ctx.actBody, ctx.contentType)
        val payload = Json.parseToJsonElement(
            jsonMapper.writeValueAsString(yamlMapper.readTree(body))
        ).jsonObject
        ctx.current = ctx.client.act(rel, payload)
    }

    @Then("the manifest affordances are:")
    fun thenManifestAffordances(entries: List<ManifestEntry>) {
        entries.forEach { (rel, present) ->
            if (present)
                assertTrue(ctx.current.rels().contains(rel), "Expected '$rel' in manifest")
            else
                assertFalse(ctx.current.rels().contains(rel), "Expected '$rel' absent from manifest")
        }
    }
}
