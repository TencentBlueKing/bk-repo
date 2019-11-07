package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.pojo.metadata.MetadataDeleteRequest
import com.tencent.bkrepo.repository.pojo.metadata.MetadataUpsertRequest
import com.tencent.bkrepo.repository.util.NodeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.core.updateFirst
import org.springframework.data.mongodb.core.upsert
import org.springframework.stereotype.Service

/**
 * 元数据服务
 *
 * @author: carrypan
 * @date: 2019-10-14
 */
@Service
class MetadataService @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val nodeDao: NodeDao
) {
    fun query(projectId: String, repoName: String, fullPath: String): Map<String, String> {
        repositoryService.checkRepository(projectId, repoName)
        return nodeDao.findOne(createQuery(projectId, repoName, fullPath))?.metadata ?: emptyMap()
    }

    fun upsert(metadataUpsertRequest: MetadataUpsertRequest) {
        val projectId = metadataUpsertRequest.projectId
        val repoName = metadataUpsertRequest.repoName
        repositoryService.checkRepository(projectId, repoName)

        val query = createQuery(projectId, repoName, metadataUpsertRequest.fullPath)
        val update = Update()
        metadataUpsertRequest.metadata.filterKeys { it.isNotBlank() }.forEach { (key, value) -> update.set("metadata.$key", value) }
        nodeDao.upsert(query, update)
    }

    fun delete(metadataDeleteRequest: MetadataDeleteRequest) {
        val projectId = metadataDeleteRequest.projectId
        val repoName = metadataDeleteRequest.repoName
        repositoryService.checkRepository(projectId, repoName)
        val query = createQuery(projectId, repoName, metadataDeleteRequest.fullPath)

        val update = Update()
        metadataDeleteRequest.keyList.filter { it.isNotBlank() }.forEach {
            update.unset("metadata.$it")
        }
        nodeDao.updateFirst(query, update)
    }

    private fun createQuery(projectId: String, repoName: String, fullPath: String): Query {
        val formattedPath = NodeUtils.formatFullPath(fullPath)
        return Query(Criteria.where("projectId").`is`(projectId)
                        .and("repoName").`is`(repoName)
                        .and("fullPath").`is`(formattedPath)
                        .and("deleted").`is`(null)
        )
    }
}
