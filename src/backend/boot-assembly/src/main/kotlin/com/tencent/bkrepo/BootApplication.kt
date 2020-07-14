package com.tencent.bkrepo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.tencent.bkrepo"])
class BootApplication

fun main() {
    runApplication<BootApplication>()
}
