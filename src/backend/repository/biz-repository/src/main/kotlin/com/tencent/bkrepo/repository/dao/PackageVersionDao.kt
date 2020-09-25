package com.tencent.bkrepo.repository.dao

import com.tencent.bkrepo.common.mongo.dao.simple.SimpleMongoDao
import com.tencent.bkrepo.repository.model.TPackageVersion
import com.tencent.bkrepo.repository.util.PackageQueryHelper
import org.springframework.stereotype.Repository

/**
 * 包版本 DAO
 */
@Repository
class PackageVersionDao : SimpleMongoDao<TPackageVersion>() {

    fun listByPackageId(packageId: String): List<TPackageVersion> {
        return this.find(PackageQueryHelper.versionListQuery(packageId))
    }

    fun findByName(packageId: String, name: String): TPackageVersion? {
        return this.findOne(PackageQueryHelper.versionQuery(packageId, name))
    }

    fun deleteByPackageId(packageId: String) {
        this.remove(PackageQueryHelper.versionQuery(packageId))
    }

    fun deleteByName(packageId: String, versionName: String) {
        this.remove(PackageQueryHelper.versionQuery(packageId, versionName))
    }

    fun findLatest(packageId: String): TPackageVersion? {
        val query = PackageQueryHelper.versionLatestQuery(packageId)
        return this.findOne(query)
    }
}
