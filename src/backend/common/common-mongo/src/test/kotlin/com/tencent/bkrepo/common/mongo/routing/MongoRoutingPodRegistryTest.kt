package com.tencent.bkrepo.common.mongo.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import java.time.LocalDateTime
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class MongoRoutingPodRegistryTest {

    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var registry: MongoRoutingRegistry
    private lateinit var properties: MongoMultiInstanceProperties
    private lateinit var podRegistry: MongoRoutingPodRegistry

    @BeforeEach
    fun setUp() {
        mongoTemplate = mock()
        registry = mock()
        properties = MongoMultiInstanceProperties().apply {
            podRegistry.enabled = true
            podRegistry.staleSeconds = 120
        }
        podRegistry = MongoRoutingPodRegistry(mongoTemplate, properties, registry, "bkrepo-test")
    }

    @Test
    fun `verifyClusterUpToDate passes when min version is zero`() {
        val result = podRegistry.verifyClusterUpToDate(0)
        assertTrue(result.passed)
    }

    @Test
    fun `verifyClusterUpToDate fails when live pod is behind`() {
        whenever(registry.isConfigUpToDate()).thenReturn(true)
        whenever(registry.getConfigVersion()).thenReturn(5)
        whenever(registry.getMinConfigVersion()).thenReturn(5)
        whenever(
            mongoTemplate.find(any<Query>(), eq(Document::class.java), eq(MongoRoutingPodRegistry.COLLECTION)),
        ).thenReturn(
            listOf(
                Document(mapOf("_id" to "bkrepo@host1", "configVersion" to 3L, "lastSeen" to LocalDateTime.now())),
            ),
        )

        val result = podRegistry.verifyClusterUpToDate(5)
        assertFalse(result.passed)
        assertTrue(result.reason!!.contains("bkrepo@host1"))
    }
}
