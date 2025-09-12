package com.tencent.com.bkrepo.generic

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import org.springframework.stereotype.Component

@Component
class NoopObservationRegistry : ObservationRegistry {
    private val noop = ObservationRegistry.NOOP
    override fun getCurrentObservation(): Observation? {
        return noop.currentObservation
    }

    override fun getCurrentObservationScope(): Observation.Scope? {
        return noop.currentObservationScope
    }

    override fun setCurrentObservationScope(current: Observation.Scope?) {
        return noop.setCurrentObservationScope(current)
    }

    override fun observationConfig(): ObservationRegistry.ObservationConfig {
        return noop.observationConfig()
    }

}