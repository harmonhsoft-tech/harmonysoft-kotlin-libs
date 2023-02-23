package tech.harmonysoft.oss.test.fixture.meta.value

import tech.harmonysoft.oss.common.ProcessingResult
import tech.harmonysoft.oss.common.ProcessingResult.Companion.failure
import tech.harmonysoft.oss.common.ProcessingResult.Companion.success
import tech.harmonysoft.oss.common.time.clock.ClockProvider
import tech.harmonysoft.oss.test.fixture.CommonTestFixture
import java.util.*
import javax.inject.Named

@Named
class CommonFixtureMetaValueMapper(
    private val clockProvider: ClockProvider
) : FixtureMetaValueMapper<Any> {

    override val type = CommonTestFixture.TYPE

    override fun map(context: Any, metaValue: String): ProcessingResult<Any?, Unit> {
        return when (metaValue) {
            "time-zone" -> success(clockProvider.data.zone.id)
            "uuid" -> success(UUID.randomUUID().toString())
            else -> failure(Unit)
        }
    }
}