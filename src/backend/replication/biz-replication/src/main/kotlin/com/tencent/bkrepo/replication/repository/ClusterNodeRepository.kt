package com.tencent.bkrepo.replication.repository

import com.tencent.bkrepo.replication.model.TClusterNode
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ClusterNodeRepository : MongoRepository<TClusterNode, String>
