package heart

import kotlinx.serialization.json.JsonObject

class HeartRepresentation(
    val state: JsonObject,
    private val forms: Map<String, AffordanceForm>
) {
    fun rels(): Set<String> = forms.keys
    fun affordance(rel: String): AffordanceForm? = forms[rel]
}
