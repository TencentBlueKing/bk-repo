package com.tencent.bkrepo.media.job.k8s

import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1ResourceRequirements

fun V1ResourceRequirements.limits(cpu: String, memory: String, ephemeralStorage: String) {
    limits(
        mapOf(
            "cpu" to Quantity(cpu),
            "memory" to Quantity(memory),
            "ephemeral-storage" to Quantity(ephemeralStorage),
        ),
    )
}

fun V1ResourceRequirements.requests(cpu: String, memory: String, ephemeralStorage: String) {
    requests(
        mapOf(
            "cpu" to Quantity(cpu),
            "memory" to Quantity(memory),
            "ephemeral-storage" to Quantity(ephemeralStorage),
        ),
    )
}
