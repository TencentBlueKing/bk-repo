package com.tencent.bkrepo.conan.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.conan.model.TConanMetadataRecord
import com.tencent.bkrepo.conan.pojo.metadata.ConanMetadataRequest
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class ConanMetadataDao : SimpleMongoDao<TConanMetadataRecord>() {

    fun findAndModify(request: ConanMetadataRequest) {
        with(request) {
            val query = Query(buildCriteria(projectId, repoName, recipe))
            val update = Update().set(TConanMetadataRecord::name.name, name)
                .set(TConanMetadataRecord::user.name, user)
                .set(TConanMetadataRecord::version.name, version)
                .set(TConanMetadataRecord::channel.name, channel)
            upsert(query, update)
        }
    }

    fun delete(projectId: String, repoName: String, recipe: String) {
        remove(Query(buildCriteria(projectId, repoName, recipe)))
    }

    private fun buildCriteria(projectId: String, repoName: String, recipe: String): Criteria {
        return Criteria
            .where(TConanMetadataRecord::projectId.name).`is`(projectId)
            .and(TConanMetadataRecord::repoName.name).`is`(repoName)
            .and(TConanMetadataRecord::recipe.name).`is`(recipe)
    }
}
