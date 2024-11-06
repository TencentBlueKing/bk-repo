package com.tencent.bkrepo.job.repository

import com.tencent.bkrepo.job.pojo.TJobFailover
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface JobSnapshotRepository : MongoRepository<TJobFailover, String> {
    fun findFirstByNameOrderByIdDesc(name: String): TJobFailover?
    fun deleteByName(name: String)
}
