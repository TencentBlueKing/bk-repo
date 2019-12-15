package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.dao.FileReferenceDao
import com.tencent.bkrepo.repository.model.TFileReference
import com.tencent.bkrepo.repository.model.TNode
import java.lang.IllegalArgumentException
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
    private val fileReferenceDao: FileReferenceDao
) {
    fun increment(sha256: String, count: Int = 1) {
        validateParameter(sha256, count)

        val fileReference = queryBySha256(sha256) ?: createNewReference(sha256)
        fileReference.count += count
        fileReferenceDao.save(fileReference)
        logger.info("Increment [$count] references of file [$sha256]")
    }

    fun increment(sha256List: List<String>) {
        sha256List.filter { it.isNotBlank() }.forEach {
            increment(it, 1)
        }
    }

    fun increment(node: TNode) {
        if (node.folder) return
        node.blockList?.map { it.sha256 }?.forEach { increment(it, 1) } ?: increment(node.sha256!!)
    }

    fun decrement(sha256: String, count: Int = 1) {
        validateParameter(sha256, count)
        val fileReference = queryBySha256(sha256) ?: return
        fileReference.count = if (fileReference.count >= count) fileReference.count - count else 0
        fileReferenceDao.save(fileReference)
        logger.info("Decrement [$count] references of file [$sha256]")
    }

    fun queryBySha256(sha256: String): TFileReference? {
        val query = Query.query(Criteria.where(TFileReference::sha256.name).`is`(sha256))
        return fileReferenceDao.findOne(query)
    }

    private fun createNewReference(sha256: String): TFileReference {
        return TFileReference(sha256 = sha256, count = 0)
    }

    private fun validateParameter(sha256: String, count: Int) {
        require(sha256.isNotBlank()) { "The sha256 of reference file can not be blank" }
        require(count >= 0) { "Reference increment should be greater than 0" }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FileReferenceService::class.java)
    }
}
