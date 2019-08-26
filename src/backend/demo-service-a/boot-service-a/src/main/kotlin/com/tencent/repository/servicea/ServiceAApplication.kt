package com.tencent.repository.servicea

import com.tencent.repository.common.service.MicroService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.runApplication
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component


@MicroService
class ServiceAApplication

fun main(args: Array<String>) {
    runApplication<ServiceAApplication>(*args)
}