package com.tencent.bkrepo.common.artifact.health

import com.tencent.bkrepo.common.storage.core.StorageService
import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.stereotype.Component

@Component("storageHealthIndicator")
class StorageHealthIndicator(
    private val storageService: StorageService
): AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        storageService.checkHealth()
        builder.up()
    }
}