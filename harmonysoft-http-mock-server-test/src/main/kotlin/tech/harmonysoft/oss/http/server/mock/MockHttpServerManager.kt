package tech.harmonysoft.oss.http.server.mock

import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.slf4j.Logger
import tech.harmonysoft.oss.common.collection.mapFirstNotNull
import tech.harmonysoft.oss.http.server.mock.config.MockHttpServerConfigProvider
import tech.harmonysoft.oss.http.server.mock.fixture.MockHttpServerPathTestFixture
import tech.harmonysoft.oss.http.server.mock.request.condition.DynamicRequestCondition
import tech.harmonysoft.oss.http.server.mock.request.condition.JsonBodyPathToMatcherCondition
import tech.harmonysoft.oss.http.server.mock.request.condition.ParameterName2ValueCondition
import tech.harmonysoft.oss.http.server.mock.response.ConditionalResponseProvider
import tech.harmonysoft.oss.http.server.mock.response.ResponseProvider
import tech.harmonysoft.oss.jackson.JsonHelper
import tech.harmonysoft.oss.json.JsonApi
import tech.harmonysoft.oss.test.binding.DynamicBindingContext
import tech.harmonysoft.oss.test.fixture.CommonTestFixture
import tech.harmonysoft.oss.test.fixture.FixtureDataHelper
import tech.harmonysoft.oss.test.json.CommonJsonUtil
import tech.harmonysoft.oss.test.matcher.Matcher
import tech.harmonysoft.oss.test.util.NetworkUtil
import tech.harmonysoft.oss.test.util.TestUtil

@Named
class MockHttpServerManager(
    private val configProvider: Optional<MockHttpServerConfigProvider>,
    private val jsonHelper: JsonHelper,
    private val fixtureDataHelper: FixtureDataHelper,
    private val jsonApi: JsonApi,
    private val dynamicContext: DynamicBindingContext,
    private val logger: Logger
) {

    private val mockRef = AtomicReference<ClientAndServer>()
    private val expectations = ConcurrentHashMap<HttpRequest, ExpectationInfo>()
    private val receivedRequests = CopyOnWriteArrayList<HttpRequest>()

    private val activeExpectationInfoRef = AtomicReference<ExpectationInfo?>()
    private val activeExpectationInfo: ExpectationInfo
        get() = activeExpectationInfoRef.get() ?: TestUtil.fail("no active mock HTTP request if defined")

    @BeforeEach
    fun startIfNecessary() {
        if (mockRef.get() != null) {
            return
        }
        val port = if (configProvider.isPresent) {
            configProvider.get().data.port
        } else {
            NetworkUtil.freePort
        }
        logger.info("Starting mock http server on port {}", port)
        mockRef.set(ClientAndServer.startClientAndServer(port))
    }

    @AfterEach
    fun cleanExpectations() {
        logger.info("Cleaning all mock HTTP server expectation rules")
        for (info in expectations.values) {
            mockRef.get().clear(info.expectationId)
        }
        expectations.clear()
        logger.info("Finished cleaning all mock HTTP server expectation rules")
        receivedRequests.clear()
        activeExpectationInfoRef.set(null)
    }

    fun targetRequest(request: HttpRequest) {
        expectations[request]?.let {
            activeExpectationInfoRef.set(it)
            return
        }
        val info = ExpectationInfo(request)
        mockRef.get().`when`(request).withId(info.expectationId).respond { req ->
            info.responseProviders.mapFirstNotNull { responseProvider ->
                responseProvider.maybeRespond(req)
            } ?: TestUtil.fail(
                "request $req is not matched by ${info.responseProviders.size} configured expectations:\n" +
                info.responseProviders.joinToString("\n")
            )
        }
        expectations[request] = info
        activeExpectationInfoRef.set(info)
    }

    fun setJsonRequestBodyCondition(path2matcher: Map<String, Matcher>) {
        addCondition(JsonBodyPathToMatcherCondition(path2matcher, jsonHelper))
    }

    fun setRequestParameterCondition(parameterName2value: Map<String, String>) {
        addCondition(ParameterName2ValueCondition(parameterName2value))
    }

    fun addCondition(condition: DynamicRequestCondition) {
        val current = activeExpectationInfo.dynamicRequestConditionRef.get()
        activeExpectationInfo.dynamicRequestConditionRef.set(current?.and(condition) ?: condition)
    }

    fun configureResponseWithCode(code: Int, response: String) {
        val condition = activeExpectationInfo.dynamicRequestConditionRef.getAndSet(null)
                        ?: DynamicRequestCondition.MATCH_ALL
        val newResponseProvider = ConditionalResponseProvider(
            condition = condition,
            response = HttpResponse.response().withStatusCode(code).withBody(response)
        )
        // there is a possible case that we stub some default behavior in Background cucumber section
        // but want to define a specific behavior later on in Scenario. Then we need to replace
        // previous response provider by a new one. This code allows to do that
        activeExpectationInfo.responseProviders.removeIf {
            (it !is ConditionalResponseProvider || it.condition == condition).apply {
                if (this) {
                    logger.info(
                        "Replacing mock HTTP response provider for {}: {} -> {}",
                        activeExpectationInfo.request, it, newResponseProvider
                    )
                }
            }
        }
        // we add new provider as the first one in assumption that use-case for multiple providers is as below:
        //  * common generic stub is defined by default (e.g. in cucumber 'Background' section)
        //  * specific provider is defined in test
        // This way specific provider's condition would be tried first and generic provider would be called
        // only as a fallback
        activeExpectationInfo.responseProviders.add(0, newResponseProvider)
    }

    fun verifyRequestReceived(httpMethod: String, path: String, expectedRawJson: String) {
        val expandedPath = fixtureDataHelper.prepareTestData(MockHttpServerPathTestFixture.TYPE, Unit, path).toString()
        val candidateBodies = mockRef.get().retrieveRecordedRequests(
            HttpRequest.request(expandedPath).withMethod(httpMethod)
        ).map { it.body.value as String }
        val prepared = fixtureDataHelper.prepareTestData(
            type = CommonTestFixture.TYPE,
            context = Any(),
            data = CommonJsonUtil.prepareDynamicMarkers(expectedRawJson)
        ).toString()
        val expected = jsonApi.parseJson(prepared)
        val bodiesWithErrors = candidateBodies.map { candidateBody ->
            val candidate = jsonApi.parseJson(candidateBody)
            val result = CommonJsonUtil.compareAndBind(
                expected = expected,
                actual = candidate,
                strict = false
            )
            if (result.errors.isEmpty()) {
                dynamicContext.storeBindings(result.boundDynamicValues)
                return
            }
            candidateBody to result.errors
        }
        TestUtil.fail(
            "can't find HTTP $httpMethod request to path $expandedPath with at least the following JSON body:" +
            "\n$expectedRawJson" +
            "\n\n${candidateBodies.size} request(s) with the same method and path are found:\n"
            + bodiesWithErrors.joinToString("\n-------------------------------------------------\n") {
                """
                    ${it.first}
                    ${it.second} error(s):
                    * ${it.second.joinToString("\n* ")}
                """.trimIndent()
            }
        )
    }

    fun verifyNoCallIsMade(method: String, path: String) {
        val expandedPath = fixtureDataHelper.prepareTestData(MockHttpServerPathTestFixture.TYPE, Unit, path).toString()
        val requests = mockRef.get().retrieveRecordedRequests(
            HttpRequest.request(expandedPath).withMethod(method)
        )
        if (requests.isNotEmpty()) {
            TestUtil.fail(
                "expected that no HTTP $method requests to $path are done but there were "
                + "${requests.size} request(s): \n"
                + requests.joinToString("\n---------------\n")
            )
        }
    }

    class ExpectationInfo(
        val request: HttpRequest
    ) {

        val expectationId = UUID.randomUUID().toString()
        val responseProviders = CopyOnWriteArrayList<ResponseProvider>()
        val dynamicRequestConditionRef = AtomicReference<DynamicRequestCondition?>()
    }

}