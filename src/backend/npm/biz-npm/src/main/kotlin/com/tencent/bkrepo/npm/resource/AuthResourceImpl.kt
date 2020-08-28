package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.AuthResource
import com.tencent.bkrepo.npm.constants.USERNAME
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthResourceImpl : AuthResource {

    override fun logout(userId: String): NpmAuthResponse<Void> {
        // 使token失效
        // 1、将 token 存入 DB（如 Redis）中，失效则删除；但增加了一个每次校验时候都要先从 DB 中查询 token 是否存在的步骤，而且违背了 JWT 的无状态原则（这不就和 session 一样了么？）。
        // 2、维护一个 token 黑名单，失效则加入黑名单中。
        // 3、在 JWT 中增加一个版本号字段，失效则改变该版本号。
        // 4、在服务端设置加密的 key 时，为每个用户生成唯一的 key，失效则改变该 key。
        return NpmAuthResponse.success()
    }

    override fun whoami(userId: String): Map<String, String> {
        return mapOf(Pair(USERNAME, userId))
    }
}
