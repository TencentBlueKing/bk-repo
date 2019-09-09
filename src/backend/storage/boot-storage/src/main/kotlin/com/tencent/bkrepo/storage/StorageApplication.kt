package com.tencent.bkrepo.storage

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class StorageApplication

fun main(args: Array<String>) {
    runApplication<StorageApplication>(*args)
}
