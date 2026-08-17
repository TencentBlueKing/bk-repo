package com.tencent.bkrepo.preview.service.share

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import com.tencent.bkrepo.preview.config.BKUserCustomProperties
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * [OrgScopeIdMappingService] 的腾讯实现：展示侧蓝鲸部门 ID ↔ 库内 TOF 部门 ID。
 *
 * 当配置了 `bk-user-custom.url` 时生效。未配置时使用 [NoopOrgScopeIdMappingService]。
 *
 * 配置项：
 * - `bk-user-custom.url`
 * - `bk-user-custom.app-code`
 * - `bk-user-custom.app-secret`
 */
@Service
@ConditionalOnProperty(prefix = "bk-user-custom", name = ["url"])
class BkUserDeptRelationService(
    private val properties: BKUserCustomProperties,
) : OrgScopeIdMappingService {

    private val objectMapper = jacksonObjectMapper()
    private val okHttpClient = HttpClientBuilderFactory.create()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val tofIdToBkIdCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_MAX_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
        .build<String, String>()

    private val bkIdToTofIdCache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_MAX_SIZE)
        .expireAfterWrite(CACHE_EXPIRE_HOURS, TimeUnit.HOURS)
        .build<String, String>()

    fun isConfigured(): Boolean {
        return properties.url.isNotBlank() && properties.appCode.isNotBlank() && properties.appSecret.isNotBlank()
    }

    override fun toStoredIds(displayIds: Collection<String>): Map<String, String> {
        val validIds = displayIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) {
            return emptyMap()
        }
        val result = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        validIds.forEach { id ->
            val cached = bkIdToTofIdCache.getIfPresent(id)
            if (cached != null) {
                result[id] = cached
            } else {
                missing += id
            }
        }
        if (missing.isEmpty()) {
            return result
        }
        queryTofDepartmentsByBkIds(missing).forEach { (bkId, tofId) ->
            bkIdToTofIdCache.put(bkId, tofId)
            result[bkId] = tofId
        }
        return result
    }

    override fun toDisplayIds(storedIds: Collection<String>): Map<String, String> {
        val validIds = storedIds.filter { it.isNotBlank() }.distinct()
        if (validIds.isEmpty()) {
            return emptyMap()
        }
        val result = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        validIds.forEach { id ->
            val cached = tofIdToBkIdCache.getIfPresent(id)
            if (cached != null) {
                result[id] = cached
            } else {
                missing += id
            }
        }
        if (missing.isEmpty()) {
            return result
        }
        queryBkDepartmentsByTofIds(missing).forEach { (tofId, bkId) ->
            tofIdToBkIdCache.put(tofId, bkId)
            result[tofId] = bkId
        }
        return result
    }

    private fun queryTofDepartmentsByBkIds(bkIds: List<String>): Map<String, String> {
        if (!isConfigured()) {
            logger.warn("bk-user-custom is not configured, skip bkIds->tofIds, bkIds=[$bkIds]")
            return emptyMap()
        }
        val url = "${properties.url.trimEnd('/')}/api/v1/open/tof-bk-relation/tof-departments/" +
            "?ids=${bkIds.joinToString(",")}"
        return try {
            val body = objectMapper.readValue<BkUserRelationResponse<List<BkDepartmentItem>>>(request(url))
            body.data.orEmpty()
                .mapNotNull { item ->
                    val id = item.id
                    val tofId = item.tofId
                    if (id.isNullOrBlank() || tofId.isNullOrBlank()) {
                        null
                    } else {
                        id to tofId
                    }
                }
                .toMap()
        } catch (ex: Exception) {
            logger.error("queryTofDepartmentsByBkIds failed, bkIds=[$bkIds]", ex)
            throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun queryBkDepartmentsByTofIds(tofIds: List<String>): Map<String, String> {
        if (!isConfigured()) {
            logger.warn("bk-user-custom is not configured, skip tofIds->bkIds, tofIds=[$tofIds]")
            return emptyMap()
        }
        val url = "${properties.url.trimEnd('/')}/api/v1/open/tof-bk-relation/bk-departments/" +
            "?tof_ids=${tofIds.joinToString(",")}"
        return try {
            val body = objectMapper.readValue<BkUserRelationResponse<List<BkDepartmentItem>>>(request(url))
            body.data.orEmpty()
                .mapNotNull { item ->
                    val tofId = item.tofId
                    val id = item.id
                    if (tofId.isNullOrBlank() || id.isNullOrBlank() || item.staffCategory != STAFF_CATEGORY_TENCENT) {
                        null
                    } else {
                        tofId to id
                    }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, ids) -> ids.first() }
        } catch (ex: Exception) {
            logger.error("queryBkDepartmentsByTofIds failed, tofIds=[$tofIds]", ex)
            throw ErrorCodeException(CommonMessageCode.SERVICE_CALL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun request(url: String): String {
        val auth = objectMapper.writeValueAsString(
            mapOf(
                "bk_app_code" to properties.appCode,
                "bk_app_secret" to properties.appSecret,
            ),
        )
        val httpRequest = Request.Builder()
            .url(url)
            .header("X-Bkapi-Authorization", auth)
            .header("Content-Type", "application/json")
            .get()
            .build()
        okHttpClient.newCall(httpRequest).execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "request bk-user-custom failed, code=[${response.code}], body=[$content]",
                )
            }
            return content
        }
    }

    private data class BkUserRelationResponse<T>(
        val code: Int? = null,
        val message: String? = null,
        val data: T? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class BkDepartmentItem(
        @JsonProperty("id")
        val id: String? = null,
        @JsonProperty("name")
        val name: String? = null,
        @JsonProperty("tof_id")
        val tofId: String? = null,
        @JsonProperty("staff_category")
        val staffCategory: String? = null,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(BkUserDeptRelationService::class.java)
        private const val CACHE_MAX_SIZE = 20_000L
        private const val CACHE_EXPIRE_HOURS = 24L
        private const val STAFF_CATEGORY_TENCENT = "tencent"
    }
}
