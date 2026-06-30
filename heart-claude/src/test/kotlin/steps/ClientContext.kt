package steps

import heart.ArtieClient
import heart.HeartRepresentation
import heart.HeartRiseClient

class ClientContext {
    val fakeTransport = FakeTransport()
    val client: HeartRiseClient = ArtieClient(fakeTransport)
    lateinit var current: HeartRepresentation
    var contentType: String = ""
    var enterBody: String = ""
    var actBody: String = ""
}
