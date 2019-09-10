package com.tencent.bkrepo

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@MicroService
@ComponentScan("com.tencent.bkrepo")
class BootApplication

fun main(args: Array<String>) {
    runApplication<BootApplication>(*args)
}
