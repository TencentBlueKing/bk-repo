package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.util.PackageQueryHelper
import org.springframework.stereotype.Repository

/**
 * 包数据访问层
 */
@Repository
class PackageDao : SimpleMongoDao<TPackage>() {

    fun findByKey(projectId: String, repoName: String, key: String): TPackage? {
        return this.findOne(PackageQueryHelper.packageQuery(projectId, repoName, key))
    }

    fun deleteByKey(projectId: String, repoName: String, key: String) {
        this.remove(PackageQueryHelper.packageQuery(projectId, repoName, key))
    }
}
