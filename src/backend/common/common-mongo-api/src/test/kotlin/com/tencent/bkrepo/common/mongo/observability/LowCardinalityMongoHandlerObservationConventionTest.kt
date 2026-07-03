package com.tencent.bkrepo.common.mongo.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.mongodb.observability.MongoHandlerContext

@DisplayName("LowCardinalityMongoHandlerObservationConvention 测试")
class LowCardinalityMongoHandlerObservationConventionTest {

    private val convention = LowCardinalityMongoHandlerObservationConvention()

    @Test
    fun `getContextualName should return fixed span name regardless of context`() {
        val context = mock(MongoHandlerContext::class.java)

        assertEquals(LowCardinalityMongoHandlerObservationConvention.SPAN_NAME, convention.getContextualName(context))
    }
}
