package com.tencent.bkrepo.helm

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class HelmApplication

fun main(args: Array<String>) {
    runApplication<HelmApplication>(*args)
}
