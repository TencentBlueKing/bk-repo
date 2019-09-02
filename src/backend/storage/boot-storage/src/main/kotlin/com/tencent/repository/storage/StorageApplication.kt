package com.tencent.repository.storage

import com.tencent.repository.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class StorageApplication

fun main(args: Array<String>) {
    runApplication<StorageApplication>(*args)
}
