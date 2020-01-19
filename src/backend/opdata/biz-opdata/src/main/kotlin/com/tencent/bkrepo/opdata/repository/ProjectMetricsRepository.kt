package com.tencent.bkrepo.opdata.repository

import com.tencent.bkrepo.opdata.model.TProjectMetrics
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectMetricsRepository : MongoRepository<TProjectMetrics, String>
