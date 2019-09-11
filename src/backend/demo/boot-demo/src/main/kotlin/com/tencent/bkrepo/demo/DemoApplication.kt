package com.tencent.bkrepo.demo

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
