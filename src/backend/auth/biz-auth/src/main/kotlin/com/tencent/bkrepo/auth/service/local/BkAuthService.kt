package com.tencent.bkrepo.auth.service.local

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.auth.config.BkAuthConfig
import com.tencent.bkrepo.auth.pojo.BkAuthUserResponse
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJson
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class BkAuthService @Autowired constructor(private val bkAuthConfig: BkAuthConfig) {


    private val okHttpClient = okhttp3.OkHttpClient.Builder().connectTimeout(3L, TimeUnit.SECONDS)
        .readTimeout(5L, TimeUnit.SECONDS)
        .writeTimeout(5L, TimeUnit.SECONDS).build()

    fun checkBkUserExist(userId: String, tenantId: String): Boolean {
        if (!bkAuthConfig.enableBkUser) return false
        if (tenantId == null) return false
        val url =
            "http://${bkAuthConfig.bkAuthServer}/api/bk-user/prod/api/v3/open/tenant/users/-/display_info/?bk_usernames=$userId"
        val authHeader = toJson(mapOf(
            "bk_app_code" to bkAuthConfig.bkAppCode,
            "bk_app_secret" to bkAuthConfig.bkAppSecret
        ))
        try {
            val request = Request.Builder().url(url).header("X-Bkapi-Authorization", authHeader)
                .header("X-Bk-Tenant-Id", tenantId).get().build()
            logger.debug("checkBkUserExist, requestUrl: [$url, $authHeader , $tenantId]")
            val apiResponse = HttpUtils.doRequest(okHttpClient, request, 2)
            logger.debug("checkBkUserExist, requestUrl: [$url], result : [${apiResponse.content}]")
            if (apiResponse.code == HttpStatus.OK.value) {
                val responseObject = JsonUtils.objectMapper.readValue<BkAuthUserResponse>(apiResponse.content)
                if (responseObject.data.isNotEmpty() && responseObject.data[0]["bk_username"] == userId) {
                    return true
                }
            }
        } catch (exception: Exception) {
            logger.error("checkBkUserExist error : ", url, authHeader, tenantId, exception)
        }
        return false
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkAuthService::class.java)
    }
}
