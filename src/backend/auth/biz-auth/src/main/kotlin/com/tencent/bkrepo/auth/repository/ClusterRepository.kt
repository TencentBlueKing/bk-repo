package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TCluster
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ClusterRepository : MongoRepository<TCluster, String> {
    fun findOneByClusterId(appId: String): TCluster?
    fun findAllBy(): List<TCluster>
}
