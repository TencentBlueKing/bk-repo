package com.tencent.repository

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@MicroService
@ComponentScan("com.tencent.repository")
class BootApplication

fun main(args: Array<String>) {
    runApplication<BootApplication>(*args)
}
