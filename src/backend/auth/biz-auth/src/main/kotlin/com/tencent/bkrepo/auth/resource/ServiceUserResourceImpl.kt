package com.tencent.bkrepo.auth.resource

import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.auth.pojo.CreateUserRequest
import com.tencent.bkrepo.auth.service.UserService
import com.tencent.bkrepo.common.api.pojo.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceUserResourceImpl @Autowired constructor(
    private val userService: UserService
) : ServiceUserResource {
    override fun createUser(request: CreateUserRequest): Response<Boolean> {
        userService.createUser(request)
        return Response(true)
    }

    override fun deleteByName(name: String): Response<Boolean> {
        userService.deleteByName(name)
        return Response(true)
    }
}
