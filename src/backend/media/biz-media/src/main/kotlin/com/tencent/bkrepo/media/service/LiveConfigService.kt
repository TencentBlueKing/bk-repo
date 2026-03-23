package com.tencent.bkrepo.media.service

import com.tencent.bkrepo.media.common.dao.MediaLiveConfigDao
import com.tencent.bkrepo.media.common.model.TMediaLiveConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 直播模式配置服务
 * 管理哪些工作空间可以使用新的直播模式
 * projectId、userId、workspaceId 三选一填入即可
 */
@Service
class LiveConfigService(
    private val mediaLiveConfigDao: MediaLiveConfigDao,
) {

    /**
     * 查询是否开启新的直播模式
     * 只要 projectId、userId、workspaceId 任一命中已启用的配置，即视为开启
     * @param projectId 项目ID
     * @param userId 用户ID
     * @param workspaceId 工作空间ID
     * @return true-已开启新直播模式, false-未开启
     */
    fun isLiveModeEnabled(projectId: String, userId: String, workspaceId: String): Boolean {
        return mediaLiveConfigDao.isLiveModeEnabled(projectId, userId, workspaceId)
    }

    /**
     * 创建直播模式配置
     * projectId、userId、workspaceId 三选一填入，其余传 null
     */
    fun createConfig(
        projectId: String?,
        userId: String?,
        workspaceId: String?,
        enabled: Boolean,
        operator: String,
    ): TMediaLiveConfig {
        require(!projectId.isNullOrBlank() || !userId.isNullOrBlank() || !workspaceId.isNullOrBlank()) {
            "At least one of projectId, userId, workspaceId must be provided"
        }
        val now = LocalDateTime.now()
        val config = TMediaLiveConfig(
            id = null,
            projectId = projectId,
            userId = userId,
            workspaceId = workspaceId,
            enabled = enabled,
            createdBy = operator,
            createdTime = now,
            updatedBy = operator,
            updateTime = now,
        )
        mediaLiveConfigDao.insert(config)
        logger.info("Created live config: projectId=$projectId, userId=$userId, workspaceId=$workspaceId")
        return config
    }

    /**
     * 更新直播模式配置
     */
    fun updateConfig(
        id: String,
        projectId: String?,
        userId: String?,
        workspaceId: String?,
        enabled: Boolean,
        operator: String,
    ) {
        require(!projectId.isNullOrBlank() || !userId.isNullOrBlank() || !workspaceId.isNullOrBlank()) {
            "At least one of projectId, userId, workspaceId must be provided"
        }
        val result = mediaLiveConfigDao.updateConfig(id, projectId, userId, workspaceId, enabled, operator)
        if (result.matchedCount == 0L) {
            throw IllegalArgumentException("Config not found for id=$id")
        }
        logger.info("Updated live config: id=$id, enabled=$enabled")
    }

    /**
     * 删除直播模式配置
     */
    fun deleteConfig(id: String) {
        val result = mediaLiveConfigDao.deleteConfig(id)
        if (result.deletedCount == 0L) {
            throw IllegalArgumentException("Config not found for id=$id")
        }
        logger.info("Deleted live config: id=$id")
    }

    /**
     * 根据 projectId、userId、workspaceId 三选一查询配置
     */
    fun getConfig(projectId: String?, userId: String?, workspaceId: String?): TMediaLiveConfig? {
        require(!projectId.isNullOrBlank() || !userId.isNullOrBlank() || !workspaceId.isNullOrBlank()) {
            "At least one of projectId, userId, workspaceId must be provided"
        }
        return mediaLiveConfigDao.findConfig(projectId, userId, workspaceId)
    }

    /**
     * 查询所有配置
     */
    fun listAll(): List<TMediaLiveConfig> {
        return mediaLiveConfigDao.findAllConfigs()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LiveConfigService::class.java)
    }
}
