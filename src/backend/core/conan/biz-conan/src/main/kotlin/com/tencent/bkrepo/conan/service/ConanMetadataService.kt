package com.tencent.bkrepo.conan.service

import com.tencent.bkrepo.common.service.cluster.condition.DefaultCondition
import com.tencent.bkrepo.conan.dao.ConanMetadataDao
import com.tencent.bkrepo.conan.model.TConanMetadataRecord
import com.tencent.bkrepo.conan.pojo.metadata.ConanMetadataRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Conditional
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
@Conditional(DefaultCondition::class)
class ConanMetadataService(
    private val conanMetadataDao: ConanMetadataDao
) {

    fun search(projectId: String, repoName: String, pattern: String?, ignoreCase: Boolean): List<String> {
        val criteria = Criteria.where(TConanMetadataRecord::projectId.name).`is`(projectId)
            .and(TConanMetadataRecord::repoName.name).`is`(repoName)
        pattern?.let {
            if (ignoreCase) {
                criteria.and(TConanMetadataRecord::recipe.name).regex("$pattern", "i")
            } else {
                criteria.and(TConanMetadataRecord::recipe.name).regex("$pattern")
            }
        }
        val query = Query(criteria)
        query.fields().include(TConanMetadataRecord::recipe.name)
        val records = conanMetadataDao.find(query, MutableMap::class.java)
        return records.map { it[TConanMetadataRecord::recipe.name] as String }
    }

    fun storeMetadata(request: ConanMetadataRequest) {
        logger.info("store conan metadata: [$request]")
        conanMetadataDao.findAndModify(request)
    }

    fun delete(projectId: String, repoName: String, recipe: String) {
        logger.info("delete conan metadata for $recipe in repo $projectId|$repoName")
        conanMetadataDao.delete(projectId, repoName, recipe)
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(ConanMetadataService::class.java)
    }
}
