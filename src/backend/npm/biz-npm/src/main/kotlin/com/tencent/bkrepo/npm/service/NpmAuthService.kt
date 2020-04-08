package com.tencent.bkrepo.npm.service

import com.google.gson.JsonParser
import com.tencent.bkrepo.auth.api.ServiceUserResource
import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.npm.auth.BEARER_AUTH_HEADER
import com.tencent.bkrepo.npm.auth.BEARER_AUTH_HEADER_PREFIX
import com.tencent.bkrepo.npm.constants.ID
import com.tencent.bkrepo.npm.constants.NAME
import com.tencent.bkrepo.npm.constants.PASSWORD
import com.tencent.bkrepo.npm.exception.NpmClientAuthException
import com.tencent.bkrepo.npm.exception.NpmLoginFailException
import com.tencent.bkrepo.npm.jwt.JwtProperties
import com.tencent.bkrepo.npm.jwt.JwtUtils
import com.tencent.bkrepo.npm.pojo.auth.NpmAuthResponse
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class NpmAuthService {

    @Autowired
    private lateinit var serviceUserResource: ServiceUserResource

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    fun addUser(body: String): NpmAuthResponse<String> {
        body.takeIf { StringUtils.isNotBlank(it) }
            ?: throw ArtifactNotFoundException("userInfo request body not found!")
        val userInfo = JsonParser().parse(body).asJsonObject
        val id = userInfo[ID].asString
        val username = userInfo[NAME].asString
        val password = userInfo[PASSWORD].asString
        val response = serviceUserResource.checkUserToken(username, password)
        return if (response.data == true) {
            val token = JwtUtils.generateToken(username, jwtProperties)
            NpmAuthResponse.success(id, token)
        } else {
            logger.error("login failed,username or password error!")
            throw NpmLoginFailException("org.couchdb.user:$username")
        }
    }

    fun logout(): NpmAuthResponse<Void> {
        // 是token失效
        // 1、将 token 存入 DB（如 Redis）中，失效则删除；但增加了一个每次校验时候都要先从 DB 中查询 token 是否存在的步骤，而且违背了 JWT 的无状态原则（这不就和 session 一样了么？）。
        // 2、维护一个 token 黑名单，失效则加入黑名单中。
        // 3、在 JWT 中增加一个版本号字段，失效则改变该版本号。
        // 4、在服务端设置加密的 key 时，为每个用户生成唯一的 key，失效则改变该 key。
        val request = HttpContextHolder.getRequest()
        val bearerAuthHeader = request.getHeader(BEARER_AUTH_HEADER)
        if (!bearerAuthHeader.startsWith(BEARER_AUTH_HEADER_PREFIX)) throw NpmClientAuthException("Authorization value [$bearerAuthHeader] is not a valid scheme")
        val token = bearerAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX)
        //获取不到说明token异常，直接报错
        JwtUtils.getUserName(token)
        return NpmAuthResponse.success()
    }

    fun whoami(): Map<String, String> {
        val bearerAuthHeader = HttpContextHolder.getRequest().getHeader(BEARER_AUTH_HEADER)
        val token = bearerAuthHeader.removePrefix(BEARER_AUTH_HEADER_PREFIX)
        val username = JwtUtils.getUserName(token)
        return mapOf(Pair("username", username))
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(NpmAuthService::class.java)
    }
}
