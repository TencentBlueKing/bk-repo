package com.tencent.bkrepo.maven

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class MavenApplication

fun main() {
    runApplication<MavenApplication>()
}
