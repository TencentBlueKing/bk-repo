package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.common.api.constant.ANONYMOUS_USER
import com.tencent.bkrepo.common.security.exception.AuthenticationException
import com.tencent.bkrepo.docker.api.Base
import com.tencent.bkrepo.docker.constant.DOCKER_API_VERSION
import com.tencent.bkrepo.docker.constant.DOCKER_HEADER_API_VERSION
import com.tencent.bkrepo.docker.response.DockerResponse
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class BaseImpl : Base {
    override fun ping(userId: String): DockerResponse {
        if (userId == ANONYMOUS_USER) {
            throw AuthenticationException()
        }
        return ResponseEntity.ok().header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).header(DOCKER_HEADER_API_VERSION, DOCKER_API_VERSION).body("{}")
    }
}
