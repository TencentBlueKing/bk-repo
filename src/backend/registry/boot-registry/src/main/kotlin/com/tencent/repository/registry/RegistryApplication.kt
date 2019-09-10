package com.tencent.repository.registry

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class RegistryApplication

fun main(args: Array<String>) {
    runApplication<RegistryApplication>(*args)
}
