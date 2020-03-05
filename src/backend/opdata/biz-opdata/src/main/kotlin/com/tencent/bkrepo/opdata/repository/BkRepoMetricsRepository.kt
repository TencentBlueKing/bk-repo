package com.tencent.bkrepo.opdata.repository

import com.tencent.bkrepo.opdata.model.TBkRepoMetrics
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface BkRepoMetricsRepository : MongoRepository<TBkRepoMetrics, String>
