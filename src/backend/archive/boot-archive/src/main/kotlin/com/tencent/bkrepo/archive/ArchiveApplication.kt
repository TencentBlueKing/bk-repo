package com.tencent.bkrepo.archive

import com.tencent.bkrepo.common.service.condition.MicroService
import org.springframework.boot.runApplication

@MicroService
class ArchiveApplication

fun main(args: Array<String>) {
    runApplication<ArchiveApplication>(*args)
}
