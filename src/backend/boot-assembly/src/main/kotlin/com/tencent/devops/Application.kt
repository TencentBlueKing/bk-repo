package com.tencent.devops

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@MicroService
@ComponentScan("com.tencent.devops")
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}