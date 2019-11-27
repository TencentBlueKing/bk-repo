package com.tencent.bkrepo.docker.resource

import org.springframework.web.bind.annotation.RestController
import com.tencent.bkrepo.docker.api.Auth
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity


@RestController
class AuthImpl : Auth {
    override  fun auth(): ResponseEntity<Any> {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("")
    }
}