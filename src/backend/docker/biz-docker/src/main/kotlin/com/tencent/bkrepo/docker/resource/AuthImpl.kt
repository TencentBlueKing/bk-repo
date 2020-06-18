package com.tencent.bkrepo.docker.resource

import com.tencent.bkrepo.docker.api.Auth
import com.tencent.bkrepo.docker.auth.AuthUtil
import com.tencent.bkrepo.docker.response.DockerResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthImpl @Autowired constructor(val authUtil: AuthUtil) : Auth {
    override fun auth(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): DockerResponse {
        return authUtil.authUser(request, response)
    }
}
