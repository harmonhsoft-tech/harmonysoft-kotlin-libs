package tech.harmonysoft.oss.http.server.mock.request.condition

import org.mockserver.model.HttpRequest
import tech.harmonysoft.oss.json.JsonApi
import tech.harmonysoft.oss.test.binding.DynamicBindingContext
import tech.harmonysoft.oss.test.json.CommonJsonUtil

class PartialJsonMatchCondition(
    private val expectedRawJson: String,
    private val expectedParsedJson: Any,
    private val jsonApi: JsonApi,
    private val context: DynamicBindingContext
) : DynamicRequestCondition {

    override fun matches(request: HttpRequest): Boolean {
        val actualParsedJson = jsonApi.parseJson(request.bodyAsJsonOrXmlString)
        val result = CommonJsonUtil.compareAndBind(
            expected = expectedParsedJson,
            actual = actualParsedJson,
            strict = false
        )
        return if (result.errors.isEmpty()) {
            context.storeBindings(result.boundDynamicValues)
            true
        } else {
            false
        }
    }

    override fun toString(): String {
        return expectedRawJson
    }
}