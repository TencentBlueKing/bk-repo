package com.tencent.bkrepo.composer

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class ComposerApplication

fun main(args: Array<String>) {
    runApplication<ComposerApplication>(*args)
}
