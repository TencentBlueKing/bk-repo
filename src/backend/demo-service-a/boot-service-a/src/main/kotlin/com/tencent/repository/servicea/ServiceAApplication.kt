package com.tencent.repository.servicea

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class ServiceAApplication

fun main(args: Array<String>) {
    runApplication<ServiceAApplication>(*args)
}
