package com.tencent.bkrepo.repository.service.experience

import com.fasterxml.jackson.module.kotlin.convertValue
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.repository.config.CIExperienceProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CIExperienceService(
    private val properties: CIExperienceProperties
) {
    private val okHttpClient = HttpClientBuilderFactory.create().build()

    /**
     * 构建通用请求头
     */
    private fun Request.Builder.withHeaders(user: String, headers: AppExperienceRequest): Request.Builder {
        this.addHeader(DEVOPS_UID, user)
            .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
        headers.platform?.let { addHeader(DEVOPS_PLATFORM, it) }
        headers.organization?.let { addHeader(DEVOPS_ORGANIZATION, it) }
        headers.version?.let { addHeader(DEVOPS_VERSION, it) }
        return this
    }

    /**
     * 执行请求的通用方法
     */
    private fun executeGetRequest(
        url: String,
        user: String,
        headers: AppExperienceRequest,
        operationName: String
    ): String {
        try {
            val request = Request.Builder()
                .url(url)
                .withHeaders(user, headers)
                .get()
                .build()

            logger.info("$operationName, requestUrl: [$url]")

            return HttpUtils.doRequest(
                okHttpClient = okHttpClient,
                request = request,
                retry = 3,
                acceptCode = allowHttpStatusSet,
                retryDelayMs = 500
            )
        } catch (exception: Exception) {
            logger.error("$operationName error: ", exception)
            throw ErrorCodeException(RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR)
        }
    }

    fun getAppExperiences(user: String, request: AppExperienceRequest): String {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/v3/list"
        return executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperiences"
        )
    }

    fun getAppExperienceDetail(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): String {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/${experienceHashId}/detail"
        return executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperienceDetail"
        )
    }

    fun getAppExperienceChangeLog(
        user: String,
        experienceHashId: String,
        request: AppExperienceChangeLogRequest
    ): String {
        // 拼接params
        val baseUrl = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/$experienceHashId/changeLog"
        val httpUrl = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $baseUrl")

        objectMapper.convertValue<Map<String, Any?>>(request).forEach { (key, value) ->
            value?.let { httpUrl.addQueryParameter(key, it.toString()) }
        }

        val url = httpUrl.build().toString()
        val headers = AppExperienceRequest(
            organization = request.organizationName,
            platform = null,
            version = null
        )
        return executeGetRequest(
            url = url,
            user = user,
            headers = headers,
            operationName = "getAppExperienceChangeLog"
        )
    }

    fun getAppExperienceInstallPackages(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): String {
        val url = properties.ciExperienceServer +
            "/ms/artifactory/api/open/experiences/$experienceHashId/installPackages"
        return executeGetRequest(
            url = url,
            user = user,
            headers = request,
            operationName = "getAppExperienceInstallPackages"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CIExperienceService::class.java)
        const val DEVOPS_BK_TOKEN = "X-DEVOPS-BK-TOKEN"
        const val DEVOPS_UID = "X-DEVOPS-UID"
        const val DEVOPS_PLATFORM = "X-DEVOPS-PLATFORM"
        const val DEVOPS_VERSION = "X-DEVOPS-APP-VERSION"
        const val DEVOPS_ORGANIZATION = "X-DEVOPS-ORGANIZATION-NAME"
        private val allowHttpStatusSet =
            setOf(HttpStatus.FORBIDDEN.value, HttpStatus.BAD_REQUEST.value, HttpStatus.NOT_FOUND.value)
    }
}
