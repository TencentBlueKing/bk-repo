package com.tencent.bkrepo.job.repository

import com.tencent.bkrepo.job.pojo.TJobFailover
import org.springframework.data.mongodb.repository.MongoRepository

interface JobSnapshotRepository : MongoRepository<TJobFailover, String> {
    fun findFirstByNameOrderByIdDesc(name: String): TJobFailover?
    fun deleteByName(name: String)
}
