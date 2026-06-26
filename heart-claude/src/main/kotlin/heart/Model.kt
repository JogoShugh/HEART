package heart

import kotlinx.serialization.json.JsonObject

const val FORMS = "_forms"
const val HREF = "href"
const val HASH = "hash"
const val SCHEMA = "_schema"
const val TYPE = "type"
const val PROPERTIES = "properties"
const val REQUIRED = "required"

data class HeartResponse(
    val state: JsonObject,
    val forms: Map<String, AffordanceForm>
)

data class AffordanceForm(
    val rel: String,
    val href: String,
    val hash: String,
    val schema: JsonObject
)
