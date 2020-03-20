package com.tencent.bkrepo.auth.repository

import com.tencent.bkrepo.auth.model.TCluster
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ClusterRepository : MongoRepository<TCluster, String> {
    fun findOneByClusterId(clusterId: String): TCluster?
    fun deleteByClusterId(clusterId: String): Long
    fun findAllBy(): List<TCluster>
}
