package com.tencent.bkrepo.repository.service.experience

import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.repository.config.CIExperienceProperties
import com.tencent.bkrepo.repository.message.RepositoryMessageCode
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceList
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceRequest
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceDetail
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceInstallPackage
import com.tencent.bkrepo.repository.pojo.experience.AppExperienceChangeLogRequest
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceChangeLog
import com.tencent.bkrepo.repository.pojo.experience.PaginationExperienceInstallPackages
import com.tencent.bkrepo.repository.pojo.experience.DevopsResponse
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.util.UriComponentsBuilder

@Service
class CIExperienceService(
    private val properties: CIExperienceProperties
) {
    private val okHttpClient = HttpClientBuilderFactory.create().build()

    /**
     * 构建通用请求头
     */
    private fun Request.Builder.withCommonHeaders(
        user: String,
        platform: String? = null,
        organization: String? = null,
        version: String? = null
    ): Request.Builder {
        this.addHeader(DEVOPS_UID, "kurtduan")
            .addHeader(DEVOPS_BK_TOKEN, properties.ciToken)
        platform?.let { addHeader(DEVOPS_PLATFORM, it) }
        organization?.let { addHeader(DEVOPS_ORGANIZATION, it) }
        version?.let { addHeader(DEVOPS_VERSION, it) }
        return this
    }

    fun getAppExperiences(user: String, request: AppExperienceRequest): AppExperienceList {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/v3/list"
        try {
            val httpRequest = Request.Builder()
                .url(url)
                .withCommonHeaders(
                    user = user,
                    platform = request.platform,
                    organization = request.organization,
                    version = request.version
                )
                .get()
                .build()
            logger.info("getAppExperiences, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<AppExperienceList>>(body)
            return AppExperienceList(
                privateExperiences = resp.data?.privateExperiences ?: emptyList(),
                publicExperiences = resp.data?.publicExperiences ?: emptyList(),
                redPointCount = resp.data?.redPointCount ?: 0
            )
        } catch (exception: Exception) {
            logger.error("getAppExperiences error: ", exception)
            throw ErrorCodeException(RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR)
        }
    }

    fun getAppExperienceDetail(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): AppExperienceDetail? {
        val url = "${properties.ciExperienceServer}/ms/artifactory/api/open/experiences/${experienceHashId}/detail"
        return try {
            val httpRequest = Request.Builder()
                .url(url)
                .withCommonHeaders(
                    user = user,
                    platform = request.platform,
                    organization = request.organization,
                    version = request.version
                )
                .get()
                .build()
            logger.info("getAppExperienceDetail, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<AppExperienceDetail>>(body)
            if (resp.status == 0) resp.data else null
        } catch (exception: Exception) {
            logger.error("getAppExperienceDetail error: ", exception)
            throw ErrorCodeException(RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR)
        }
    }

    fun getAppExperienceChangeLog(
        user: String,
        experienceHashId: String,
        request: AppExperienceChangeLogRequest
    ): PaginationExperienceChangeLog {
        // 拼接params
        val url = UriComponentsBuilder
            .fromUriString(
                properties.ciExperienceServer +
                    "/ms/artifactory/api/open/experiences/$experienceHashId/changeLog"
            )
            .queryParams(LinkedMultiValueMap<String, String>().apply {
                objectMapper.convertValue<Map<String, Any?>>(request).forEach { (k, v) ->
                    v?.let { add(k, it.toString()) }
                }
            })
            .toUriString()

        try {
            val httpRequest = Request.Builder()
                .url(url)
                .withCommonHeaders(
                    user = user,
                    organization = request.organizationName
                )
                .get()
                .build()
            logger.info("getAppExperienceChangeLog, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<PaginationExperienceChangeLog>>(body)
            return if (resp.status == 0) {
                resp.data ?: PaginationExperienceChangeLog(0, false, emptyList())
            } else {
                PaginationExperienceChangeLog(0, false, emptyList())
            }
        } catch (exception: Exception) {
            logger.error("getAppExperienceChangeLog error: ", exception)
            throw ErrorCodeException(RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR)
        }
    }

    fun getAppExperienceInstallPackages(
        user: String,
        experienceHashId: String,
        request: AppExperienceRequest
    ): List<AppExperienceInstallPackage> {
        val url = properties.ciExperienceServer +
            "/ms/artifactory/api/open/experiences/$experienceHashId/installPackages"
        return try {
            val httpRequest = Request.Builder()
                .url(url)
                .withCommonHeaders(
                    user = user,
                    platform = request.platform,
                    version = request.version
                )
                .get()
                .build()
            logger.info("getAppExperienceInstallPackages, requestUrl: [$url]")
            val body = HttpUtils.doRequest(okHttpClient, httpRequest, 2, allowHttpStatusSet)
            val resp = objectMapper.readValue<DevopsResponse<PaginationExperienceInstallPackages>>(body)
            if (resp.status == 0) resp.data?.records ?: emptyList() else throw Exception(resp.message)
        } catch (exception: Exception) {
            logger.error("getAppExperienceInstallPackages error: ", exception)
            throw ErrorCodeException(RepositoryMessageCode.APP_EXPERIENCE_REQUEST_ERROR)
        }
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