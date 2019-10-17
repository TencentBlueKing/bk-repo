package com.tencent.bkrepo.docker

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class RegistryApplication

fun main(args: Array<String>) {
    runApplication<RegistryApplication>(*args)
}
