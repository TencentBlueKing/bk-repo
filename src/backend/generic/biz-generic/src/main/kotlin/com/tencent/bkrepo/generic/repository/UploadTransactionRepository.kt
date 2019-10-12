package com.tencent.bkrepo.generic.repository

import com.tencent.bkrepo.generic.model.TUploadTransaction
import org.springframework.data.mongodb.repository.MongoRepository

/**
 * 上传事物 repository
 *
 * @author: carrypan
 * @date: 2019-10-10
 */
interface UploadTransactionRepository : MongoRepository<TUploadTransaction, String> {
    fun findByProjectIdAndRepoNameAndFullPath(projectId: String, repoName: String, fullPath: String): TUploadTransaction?
}
