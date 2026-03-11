package com.tencent.bkrepo.replication.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.replication.model.TFederationGroup
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class FederationGroupDao : SimpleMongoDao<TFederationGroup>() {

    fun findByName(name: String): TFederationGroup? {
        return findOne(Query(Criteria.where(TFederationGroup::name.name).`is`(name)))
    }

    fun findAutoEnableGroups(projectId: String): List<TFederationGroup> {
        val criteria = Criteria.where(TFederationGroup::autoEnableForNewRepo.name).`is`(true)
            .orOperator(
                Criteria.where(TFederationGroup::projectScope.name).`is`(null),
                Criteria.where(TFederationGroup::projectScope.name).`in`(projectId)
            )
        return find(Query(criteria))
    }

    fun deleteById(id: String) {
        remove(Query(Criteria.where("_id").`is`(id)))
    }
}
