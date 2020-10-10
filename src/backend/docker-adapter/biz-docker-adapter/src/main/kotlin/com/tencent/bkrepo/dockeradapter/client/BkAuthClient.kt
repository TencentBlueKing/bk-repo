package com.tencent.bkrepo.dockeradapter.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.dockeradapter.config.SystemConfig
import com.tencent.bkrepo.dockeradapter.util.HttpUtils
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class BkAuthClient(
    private val systemConfig: SystemConfig
) {
    private val accessTokenCache = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(200, TimeUnit.SECONDS)
        .build<String, String>()

    fun getAccessToken(): String {
        val cachedToken = accessTokenCache.getIfPresent(TOKEN_CACHE_KEY)
        if (cachedToken != null) {
            return cachedToken
        }
        val accessToken = createAccessToken()
        accessTokenCache.put(TOKEN_CACHE_KEY, accessToken)
        return accessToken
    }

    fun refreshAccessToken(): String {
        logger.info("refresh access token")
        accessTokenCache.invalidate(TOKEN_CACHE_KEY)
        return getAccessToken()
    }

    fun createAccessToken(): String {
        logger.info("create access token")
        var url = "${systemConfig.bkssmServer}/api/v1/auth/access-tokens"
        val reqData = mapOf(
            "grant_type" to "client_credentials",
            "id_provider" to "client"
        )
        val httpRequest = Request.Builder().url(url)
            .header("X-BK-APP-CODE", systemConfig.appCode!!)
            .header("X-BK-APP-SECRET", systemConfig.appSecret!!)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(reqData)
                )
            )
            .build()
        val apiResponse = HttpUtils.doRequest(httpRequest, 2)
        val projectResponse: PaasResponse<AccessTokenData> = objectMapper.readValue(apiResponse.content)
        if (projectResponse.code != 0) {
            logger.error("get access token failed, code: ${projectResponse.code}, message: ${projectResponse.message}")
            throw RuntimeException("get access token failed")
        }
        return projectResponse.data!!.accessToken
    }

    fun checkProjectPermission(user: String, projectId: String, retry: Boolean = false): Boolean {
        logger.info("checkProjectPermission, user: $user, projectId: $projectId, retry: $retry")
        val accessToken = getAccessToken()
        val url = "${systemConfig.apigwServer}/api/apigw/bcs-app/prod/apis/projects/$projectId/user_perms/"
        val httpRequest = Request.Builder().url(url)
            .header("X-BKAPI-AUTHORIZATION", "{\"access_token\": \"$accessToken\"}")
            .header("X-BKAPI-TOKEN", accessToken)
            .header("X-BKAPI-USERNAME", user)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    "{\"action_ids\":[\"project_view\"]}"
                )
            )
            .build()
        val apiResponse = HttpUtils.doRequest(httpRequest, 2)
        val projectResponse: PaasResponse<ProjectPermission> = objectMapper.readValue(apiResponse.content)
        if (projectResponse.code != 0) {
            if (!retry && projectResponse.code == 1308406 /* access_token invalid */) {
                logger.warn("access token invalid($accessToken), retry after refresh access token")
                refreshAccessToken()
                return checkProjectPermission(user, projectId, true)
            }
            logger.error("check project permission failed, code: ${projectResponse.code}, message: ${projectResponse.message}")
            throw RuntimeException("check project permission failed")
        }
        return projectResponse.data!!.projectView.allowed

    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkAuthClient::class.java)
        private const val TOKEN_CACHE_KEY = "access_token_cache_key"
    }
}