package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.AuthResource
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
import com.tencent.bkrepo.npm.service.NpmAuthService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthResourceImpl : AuthResource {

    @Autowired
    private lateinit var npmAuthService: NpmAuthService

    override fun addUser(body: String): NpmAuthResponse<String> {
        return npmAuthService.addUser(body)
    }

    override fun logout(): NpmAuthResponse<Void> {
        return npmAuthService.logout()
    }

    override fun whoami(): Map<String, String> {
        return npmAuthService.whoami()
    }
}
