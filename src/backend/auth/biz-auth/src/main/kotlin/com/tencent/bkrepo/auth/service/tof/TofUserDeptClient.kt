package com.tencent.bkrepo.auth.service.tof

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.CacheBuilder
import com.tencent.bkrepo.auth.config.TofProperties
import com.tencent.bkrepo.auth.pojo.token.OrgScope
import com.tencent.bkrepo.auth.pojo.user.UserOrgMembership
import com.tencent.bkrepo.auth.util.HttpUtils
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.api.util.okhttp.HttpClientBuilderFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * TOF 组织查询客户端：员工信息 + 祖先部门 → 约定 type 的 [OrgScope] 列表。
 */
@Component
@ConditionalOnProperty(prefix = "tof", name = ["host"])
class TofUserDeptClient(
    private val properties: TofProperties,
) {

    private val okHttpClient = HttpClientBuilderFactory.create()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val userDeptCache = CacheBuilder.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build<String, UserOrgMembership>()

    private val deptInfoCache = CacheBuilder.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(24, TimeUnit.HOURS)
        .build<Int, TofDeptInfo>()

    fun isConfigured(): Boolean {
        return properties.host.isNotBlank() && properties.appCode.isNotBlank() && properties.appSecret.isNotBlank()
    }

    fun getUserOrgMembership(userId: String): UserOrgMembership {
        require(isConfigured()) { "tof.host/app-code/app-secret is not configured" }
        val normalized = userId.trim()
        require(normalized.isNotEmpty()) { "userId is blank" }
        userDeptCache.getIfPresent(normalized)?.let { return it }
        val staff = getStaffInfo(normalized)
        if (isLeftCompany(staff)) {
            throw IllegalStateException("user [$normalized] left company or has no organization")
        }
        val membership = generateUserOrgMembership(normalized, staff.groupId.toIntOrNull(), staff.chineseName)
        userDeptCache.put(normalized, membership)
        return membership
    }

    private fun getStaffInfo(userId: String): TofStaffInfo {
        val body = mapOf(
            "app_code" to properties.appCode,
            "app_secret" to properties.appSecret,
            "operator" to null,
            "login_name" to userId,
            "bk_ticket" to "",
        )
        val content = request("get_staff_info_by_login_name", body)
        val response = objectMapper.readValue<TofResponse<TofStaffInfo>>(content)
        return response.data
            ?: throw IllegalStateException("TOF staff info empty for user [$userId]")
    }

    private fun getParentDeptInfo(groupId: String, level: Int): List<TofDeptInfo> {
        val body = mapOf(
            "app_code" to properties.appCode,
            "app_secret" to properties.appSecret,
            "dept_id" to groupId,
            "level" to level,
        )
        val content = request("get_parent_dept_infos", body)
        val response = objectMapper.readValue<TofResponse<List<TofDeptInfo>>>(content)
        return response.data.orEmpty()
    }

    private fun getDeptInfo(id: Int): TofDeptInfo {
        deptInfoCache.getIfPresent(id)?.let { return it }
        val body = mapOf(
            "app_code" to properties.appCode,
            "app_secret" to properties.appSecret,
            "dept_id" to id.toString(),
        )
        val content = request("get_dept_info", body)
        val response = objectMapper.readValue<TofResponse<TofDeptInfoResponse>>(content)
        val raw = response.data
            ?: throw IllegalStateException("TOF dept info empty for id [$id]")
        val deptInfo = TofDeptInfo(
            typeId = raw.typeId,
            leaderId = raw.leaderId,
            name = raw.name,
            level = raw.level,
            enabled = raw.enabled,
            parentId = raw.parentId,
            id = raw.id,
        )
        deptInfoCache.put(id, deptInfo)
        return deptInfo
    }

    private fun generateUserOrgMembership(
        userId: String,
        userGroupId: Int?,
        userChineseName: String,
    ): UserOrgMembership {
        val deptInfos = if (userGroupId != null) {
            getParentDeptInfo(userGroupId.toString(), 10) + getDeptInfo(userGroupId)
        } else {
            emptyList()
        }
        val scopes = mutableListOf<OrgScope>()
        deptInfos.forEach { deptInfo ->
            val typeId = deptInfo.typeId.toIntOrNull() ?: return@forEach
            val id = deptInfo.id.trim()
            if (id.isEmpty()) {
                return@forEach
            }
            scopes.add(
                OrgScope(typeId.toString(), id, deptInfo.name.takeIf { it.isNotBlank() })
            )
        }
        return UserOrgMembership(userId = userId, scopes = scopes.distinctBy { it.scopeType to it.scopeValue })
            .also {
                logger.debug(
                    "Resolved TOF org scopes for user [$userId] name [$userChineseName]: scopes=${it.scopes}",
                )
            }
    }

    private fun isLeftCompany(staff: TofStaffInfo): Boolean {
        return staff.groupId.isBlank() || staff.groupId == "0"
    }

    private fun request(path: String, body: Any): String {
        val url = "http://${properties.host.trimEnd('/')}/component/compapi/tof/$path"
        val requestContent = objectMapper.writeValueAsString(body)
        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            requestContent,
        )
        val request = Request.Builder().url(url).post(requestBody).build()
        val response = HttpUtils.doRequest(okHttpClient, request, 1)
        return response.content
    }

    private data class TofResponse<T>(
        val code: String? = null,
        val message: String? = null,
        val data: T? = null,
    )

    private data class TofStaffInfo(
        @JsonProperty("LoginName")
        val loginName: String = "",
        @JsonProperty("ChineseName")
        val chineseName: String = "",
        @JsonProperty("GroupId")
        val groupId: String = "",
        @JsonProperty("StatusId")
        val statusId: String = "",
    )

    private data class TofDeptInfo(
        @JsonProperty("TypeId")
        val typeId: String = "",
        @JsonProperty("LeaderId")
        val leaderId: String = "",
        @JsonProperty("Name")
        val name: String = "",
        @JsonProperty("Level")
        val level: String = "",
        @JsonProperty("Enabled")
        val enabled: String = "",
        @JsonProperty("ParentId")
        val parentId: String = "",
        @JsonProperty("ID")
        val id: String = "",
    )

    private data class TofDeptInfoResponse(
        @JsonProperty("TypeId")
        val typeId: String = "",
        @JsonProperty("LeaderId")
        val leaderId: String = "",
        @JsonProperty("Name")
        val name: String = "",
        @JsonProperty("Level")
        val level: String = "",
        @JsonProperty("Enabled")
        val enabled: String = "",
        @JsonProperty("ParentId")
        val parentId: String = "",
        @JsonProperty("ID")
        val id: String = "",
    )

    companion object {
        private val logger = LoggerFactory.getLogger(TofUserDeptClient::class.java)
    }
}
