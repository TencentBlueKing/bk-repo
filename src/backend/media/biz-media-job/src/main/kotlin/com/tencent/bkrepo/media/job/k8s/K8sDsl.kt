package com.tencent.bkrepo.media.job.k8s

import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1ResourceRequirements

fun V1ResourceRequirements.limits(cpu: Double, memory: Long, ephemeralStorage: Long) {
    limits(
        mapOf(
            "cpu" to Quantity("$cpu"),
            "memory" to Quantity("$memory"),
            "ephemeral-storage" to Quantity("$ephemeralStorage"),
        ),
    )
}

fun V1ResourceRequirements.requests(cpu: Double, memory: Long, ephemeralStorage: Long) {
    requests(
        mapOf(
            "cpu" to Quantity("$cpu"),
            "memory" to Quantity("$memory"),
            "ephemeral-storage" to Quantity("$ephemeralStorage"),
        ),
    )
}
