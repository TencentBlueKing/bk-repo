package com.tencent.bkrepo.repository.dao.repository

import com.tencent.bkrepo.repository.model.TDownloadStatistics
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface DownloadStatisticsRepository : MongoRepository<TDownloadStatistics, String>
