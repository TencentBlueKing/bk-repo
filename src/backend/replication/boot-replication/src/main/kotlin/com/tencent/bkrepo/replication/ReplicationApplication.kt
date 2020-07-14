package com.tencent.bkrepo.replication

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class ReplicationApplication

fun main() {
    runApplication<ReplicationApplication>()
}
