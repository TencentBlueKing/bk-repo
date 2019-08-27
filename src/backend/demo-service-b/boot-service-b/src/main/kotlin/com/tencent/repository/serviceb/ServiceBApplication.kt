package com.tencent.repository.serviceb

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class ServiceBApplication

fun main(args: Array<String>) {
    runApplication<ServiceBApplication>(*args)
}
