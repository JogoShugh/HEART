package heart

import kotlinx.serialization.json.JsonObject

class ArtieClient(private val transport: Transport) : HeartRiseClient {
    private var current: HeartRepresentation? = null

    override fun enter(url: String): HeartRepresentation {
        val (body, contentType) = transport.get(url)
        return HeartResponseParser.parse(body, contentType).also { current = it }
    }

    override fun act(rel: String, payload: JsonObject): HeartRepresentation {
        TODO("Implemented in act slice")
    }
}
