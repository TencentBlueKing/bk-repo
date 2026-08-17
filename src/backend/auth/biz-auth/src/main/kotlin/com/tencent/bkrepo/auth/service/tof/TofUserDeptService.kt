package com.tencent.bkrepo.auth.service.tof

import com.tencent.bkrepo.auth.pojo.user.UserOrgMembership
import com.tencent.bkrepo.auth.service.UserDeptService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * [UserDeptService] 的 TOF 实现。
 *
 * 当配置了 `tof.host` 时生效。对接其他组织源时：不要配置该项，并自行提供 [UserDeptService] Bean。
 */
@Service
@ConditionalOnProperty(prefix = "tof", name = ["host"])
class TofUserDeptService(
    private val tofUserDeptClient: TofUserDeptClient,
) : UserDeptService {

    override fun getUserOrgMembership(userId: String): UserOrgMembership {
        val normalized = userId.trim()
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("userId is blank")
        }
        if (!tofUserDeptClient.isConfigured()) {
            logger.warn("tof is not fully configured, cannot resolve org for user [$normalized]")
            throw IllegalStateException("tof is not fully configured")
        }
        return try {
            tofUserDeptClient.getUserOrgMembership(normalized)
        } catch (ex: Exception) {
            logger.warn("Failed to resolve user org via TOF for user [$normalized]: ${ex.message}")
            throw ex
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TofUserDeptService::class.java)
    }
}
