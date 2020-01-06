package com.tencent.bkrepo.opdata

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class OpDataApplication

fun main(args: Array<String>) {
    runApplication<OpDataApplication>(*args)
}
