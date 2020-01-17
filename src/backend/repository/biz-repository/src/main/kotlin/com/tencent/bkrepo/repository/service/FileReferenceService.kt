package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.model.TFileReference
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.model.TRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

/**
 * 文件摘要引用 Service
 *
 * @author: carrypan
 * @date: 2019/11/12
 */
@Service
class FileReferenceService @Autowired constructor(
    private val fileReferenceDao: FileReferenceDao,
    private val repositoryService: RepositoryService
) {

    fun increment(node: TNode, repository: TRepository? = null): Boolean {
        return if (validateParameter(node)) {
            val repo = repository ?: repositoryService.queryRepository(node.projectId, node.repoName)
            if (repo == null) {
                LoggerHolder.sysErrorLogger.warn("Failed to decrement reference of node [$node], repository not found.")
                return false
            }
            return increment(node.sha256!!, repo.storageCredentials)
        } else false
    }

    fun decrement(node: TNode, repository: TRepository? = null): Boolean {
        return if (validateParameter(node)) {
            val repo = repository ?: repositoryService.queryRepository(node.projectId, node.repoName)
            if (repo == null) {
                LoggerHolder.sysErrorLogger.warn("Failed to decrement reference of node [$node], repository not found.")
                return false
            }
            return decrement(node.sha256!!, repo.storageCredentials)
        } else false
    }

    private fun query(sha256: String, storageCredentials: String?): TFileReference? {
        val query = Query.query(Criteria.where(TFileReference::sha256.name).`is`(sha256)
            .and(TFileReference::storageCredentials.name).`is`(storageCredentials))
        return fileReferenceDao.findOne(query)
    }

    private fun increment(sha256: String, storageCredentials: String?): Boolean {
        query(sha256, storageCredentials)?.run {
            this.count += 1
            fileReferenceDao.save(this)
        } ?: createNewReference(sha256, storageCredentials)
        logger.info("Increment reference of file [$sha256] on storageCredentials [$storageCredentials].")
        return true
    }

    private fun decrement(sha256: String, storageCredentials: String?): Boolean {
        return query(sha256, storageCredentials)?.run {
            if (this.count >= 1) {
                this.count -= 1
                fileReferenceDao.save(this)
                logger.info("Decrement references of file [$sha256] on storageCredentials [$storageCredentials].")
                true
            } else {
                LoggerHolder.sysErrorLogger.warn("Failed to decrement reference of file [$sha256] on storageCredentials [$storageCredentials]: sha256 reference is 0.")
                false
            }
        } ?: run {
            LoggerHolder.sysErrorLogger.warn("Failed to decrement reference of file [$sha256] on storageCredentials [$storageCredentials]: sha256 reference not found.")
            false
        }
    }

    private fun createNewReference(sha256: String, storageCredentials: String?): TFileReference {
        return TFileReference(sha256 = sha256, storageCredentials = storageCredentials, count = 1)
    }

    private fun validateParameter(node: TNode): Boolean {
        if (node.folder) return false
        if (node.sha256.isNullOrBlank()) {
            logger.warn("Failed to change file reference, node[$node] sha256 is null or blank.")
            return false
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileReferenceService::class.java)
    }
}
