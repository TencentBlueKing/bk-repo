package com.tencent.bkrepo.common.api.util

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TraceUtils 测试")
class TraceUtilsTest {

    @Test
    fun `should create span with fixed name and low cardinality attributes when init is true`() {
        val registry = ObservationRegistry.create()
        var capturedName: String? = null
        var capturedLowCardinalityKeyValues: KeyValues? = null
        registry.observationConfig().observationHandler(
            object : ObservationHandler<Observation.Context> {
                override fun supportsContext(context: Observation.Context): Boolean = true

                override fun onStart(context: Observation.Context) {
                    capturedName = context.name
                    capturedLowCardinalityKeyValues = context.lowCardinalityKeyValues
                }
            },
        )

        val result = TraceUtils.newSpan(
            observationRegistry = registry,
            spanName = "batch.job.execute",
            lowCardinalityKeyValues = KeyValues.of(KeyValue.of("job.name", "TestJob")),
            init = true,
        ) { "done" }

        assertEquals("done", result)
        assertEquals("batch.job.execute", capturedName)
        assertEquals("TestJob", capturedLowCardinalityKeyValues?.get("job.name"))
    }

    @Test
    fun `should skip span creation when context is empty and init is false`() {
        val registry = ObservationRegistry.create()
        var spanStarted = false
        registry.observationConfig().observationHandler(
            object : ObservationHandler<Observation.Context> {
                override fun supportsContext(context: Observation.Context): Boolean = true

                override fun onStart(context: Observation.Context) {
                    spanStarted = true
                }
            },
        )

        val result = TraceUtils.newSpan(registry, "batch.job.execute") { "done" }

        assertEquals("done", result)
        assertEquals(false, spanStarted)
    }

    @Test
    fun `should create child span when parent observation exists`() {
        val registry = ObservationRegistry.create()
        var childSpanName: String? = null
        registry.observationConfig().observationHandler(
            object : ObservationHandler<Observation.Context> {
                override fun supportsContext(context: Observation.Context): Boolean = true

                override fun onStart(context: Observation.Context) {
                    if (context.name == "mongodb.batch.collection") {
                        childSpanName = context.name
                    }
                }
            },
        )

        Observation.createNotStarted("parent", registry).observe {
            TraceUtils.newSpan(
                observationRegistry = registry,
                spanName = "mongodb.batch.collection",
                lowCardinalityKeyValues = KeyValues.of(KeyValue.of("mongodb.collection.name", "node")),
            ) { "done" }
        }

        assertEquals("mongodb.batch.collection", childSpanName)
    }
}

private fun KeyValues.get(key: String): String? {
    var value: String? = null
    forEach { keyValue ->
        if (keyValue.key == key) {
            value = keyValue.value
        }
    }
    return value
}
