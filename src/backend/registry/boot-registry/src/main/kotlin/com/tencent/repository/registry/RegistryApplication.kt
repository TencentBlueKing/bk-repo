package com.tencent.bkrepo.registry

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class RegistryApplication

fun main(args: Array<String>) {
    runApplication<RegistryApplication>(*args)
}
