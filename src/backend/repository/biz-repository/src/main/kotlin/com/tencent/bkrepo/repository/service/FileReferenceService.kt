package com.tencent.bkrepo.repository.service

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
class FileReferenceService {

    @Autowired
    private lateinit var fileReferenceDao: FileReferenceDao

    @Autowired
    private lateinit var repositoryService: RepositoryService

    fun increment(node: TNode, repository: TRepository? = null): Boolean {
        return if (validateParameter(node)) {
            val repo = repository ?: repositoryService.queryRepository(node.projectId, node.repoName)
            if (repo == null) {
                logger.error("Failed to decrement reference of node [$node], repository not found.")
                return false
            }
            return increment(node.sha256!!, repo.credentialsKey)
        } else false
    }

    fun decrement(node: TNode, repository: TRepository? = null): Boolean {
        return if (validateParameter(node)) {
            val repo = repository ?: repositoryService.queryRepository(node.projectId, node.repoName)
            if (repo == null) {
                logger.error("Failed to decrement reference of node [$node], repository not found.")
                return false
            }
            return decrement(node.sha256!!, repo.credentialsKey)
        } else false
    }

    private fun query(sha256: String, credentialsKey: String?): TFileReference? {
        val query = Query.query(Criteria.where(TFileReference::sha256.name).`is`(sha256)
            .and(TFileReference::credentialsKey.name).`is`(credentialsKey))
        return fileReferenceDao.findOne(query)
    }

    private fun increment(sha256: String, credentialsKey: String?): Boolean {
        val fileReference = query(sha256, credentialsKey)?.run {
            this.count += 1
            this
        } ?: createNewReference(sha256, credentialsKey)
        fileReferenceDao.save(fileReference)
        logger.info("Increment reference of file [$sha256] on credentialsKey [$credentialsKey].")
        return true
    }

    private fun decrement(sha256: String, credentialsKey: String?): Boolean {
        val fileReference = query(sha256, credentialsKey) ?: run {
            logger.error("Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]: sha256 reference not found, create new one.")
            createNewReference(sha256, credentialsKey)
        }
        return if (fileReference.count >= 1) {
            fileReference.count -= 1
            fileReferenceDao.save(fileReference)
            logger.info("Decrement references of file [$sha256] on credentialsKey [$credentialsKey].")
            true
        } else {
            logger.error("Failed to decrement reference of file [$sha256] on credentialsKey [$credentialsKey]: sha256 reference is 0.")
            false
        }
    }

    private fun createNewReference(sha256: String, credentialsKey: String?): TFileReference {
        return TFileReference(sha256 = sha256, credentialsKey = credentialsKey, count = 1)
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
