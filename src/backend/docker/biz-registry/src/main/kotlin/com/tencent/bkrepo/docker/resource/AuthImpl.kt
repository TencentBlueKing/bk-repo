package com.tencent.bkrepo.docker.resource

import org.springframework.web.bind.annotation.RestController
import com.tencent.bkrepo.docker.api.Auth
import com.tencent.bkrepo.docker.auth.AuthUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@RestController
class AuthImpl @Autowired constructor(val authUtil: AuthUtil) : Auth {
    override  fun auth(
            request: HttpServletRequest,
            response : HttpServletResponse
    ) :ResponseEntity<Any>{
        return authUtil.authUser(request, response)
    }
}