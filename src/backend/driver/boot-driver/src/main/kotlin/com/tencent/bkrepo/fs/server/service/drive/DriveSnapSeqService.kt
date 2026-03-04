package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.api.exception.NotFoundException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.common.metadata.condition.ReactiveCondition
import com.tencent.bkrepo.common.metadata.dao.node.RDriveSnapSeqDao
import com.tencent.bkrepo.common.metadata.model.TDriveSnapSeq
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.data.mongodb.core.query.where
import org.springframework.stereotype.Service

/**
 * Drive 快照序列号服务
 */
@Service
@Conditional(ReactiveCondition::class)
class DriveSnapSeqService(
    private val driveSnapSeqDao: RDriveSnapSeqDao,
) {
    /**
     * 查询仓库当前快照序列号，不存在时抛出异常。
     */
    suspend fun getLatestSnapSeq(projectId: String, repoName: String): Long {
        validate(projectId, repoName)
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
        return driveSnapSeqDao.findOne(Query(criteria))?.snapSeq
            ?: throw NotFoundException(CommonMessageCode.RESOURCE_NOT_FOUND, "drive snapSeq[$projectId/$repoName]")
    }

    /**
     * 增加仓库快照序列号并返回增加后的值。
     */
    suspend fun incSnapSeq(projectId: String, repoName: String): Long {
        validate(projectId, repoName)
        val criteria = where(TDriveSnapSeq::projectId).isEqualTo(projectId)
            .and(TDriveSnapSeq::repoName.name).isEqualTo(repoName)
        val query = Query(criteria)
        val update = Update()
            .setOnInsert(TDriveSnapSeq::projectId.name, projectId)
            .setOnInsert(TDriveSnapSeq::repoName.name, repoName)
            .inc(TDriveSnapSeq::snapSeq.name, 1L)
        val options = FindAndModifyOptions.options().upsert(true).returnNew(true)
        val latest = driveSnapSeqDao.findAndModify(query, update, options, TDriveSnapSeq::class.java)
            ?: throw IllegalStateException("Failed to increase drive snap sequence for [$projectId/$repoName].")
        logger.info("Increase drive snapSeq to [${latest.snapSeq}] in repo [$projectId/$repoName].")
        return latest.snapSeq
    }

    private fun validate(projectId: String, repoName: String) {
        Preconditions.checkArgument(projectId.isNotBlank(), TDriveSnapSeq::projectId.name)
        Preconditions.checkArgument(repoName.isNotBlank(), TDriveSnapSeq::repoName.name)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DriveSnapSeqService::class.java)
    }
}
