package com.tencent.bkrepo.media.job.service

import com.tencent.bkrepo.media.common.dao.TranscodeJobConfigDao
import com.tencent.bkrepo.media.common.model.TMediaTranscodeJobConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 转码任务配置服务
 */
@Service
class TranscodeJobConfigService(
    private val transcodeJobConfigDao: TranscodeJobConfigDao,
) {

    /**
     * 创建转码任务配置
     */
    fun createConfig(
        projectId: String?,
        maxJobCount: Int?,
        image: String,
        resource: String?,
        cosConfigMapName: String?,
    ): TMediaTranscodeJobConfig {
        val config = TMediaTranscodeJobConfig(
            id = null,
            projectId = projectId,
            maxJobCount = maxJobCount,
            image = image,
            resource = resource,
            cosConfigMapName = cosConfigMapName,
        )
        transcodeJobConfigDao.insert(config)
        logger.info("Created transcode job config: projectId=$projectId, image=$image")
        return config
    }

    /**
     * 更新转码任务配置
     */
    fun updateConfig(
        id: String,
        projectId: String?,
        maxJobCount: Int?,
        image: String?,
        resource: String?,
        cosConfigMapName: String?,
    ) {
        val result = transcodeJobConfigDao.updateConfig(
            id, projectId, maxJobCount, image, resource, cosConfigMapName,
        )
        if (result.matchedCount == 0L) {
            throw IllegalArgumentException("Config not found for id=$id")
        }
        logger.info("Updated transcode job config: id=$id")
    }

    /**
     * 删除转码任务配置
     */
    fun deleteConfig(id: String) {
        val result = transcodeJobConfigDao.deleteConfig(id)
        if (result.deletedCount == 0L) {
            throw IllegalArgumentException("Config not found for id=$id")
        }
        logger.info("Deleted transcode job config: id=$id")
    }

    /**
     * 根据项目ID查询配置
     */
    fun getConfig(projectId: String?): TMediaTranscodeJobConfig? {
        return transcodeJobConfigDao.findByProjectId(projectId)
    }

    /**
     * 查询所有配置
     */
    fun listAll(): List<TMediaTranscodeJobConfig> {
        return transcodeJobConfigDao.findAllConfigs()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TranscodeJobConfigService::class.java)
    }
}
