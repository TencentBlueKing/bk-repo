package com.tencent.bkrepo.auth

import com.tencent.bkrepo.common.service.MicroService
import org.springframework.boot.runApplication

@MicroService
class AuthApplication

fun main(args: Array<String>) {
    runApplication<AuthApplication>(*args)
}
