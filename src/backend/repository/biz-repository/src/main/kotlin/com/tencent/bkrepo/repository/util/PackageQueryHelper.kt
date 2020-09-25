package com.tencent.bkrepo.repository.util

import com.tencent.bkrepo.repository.model.TPackage
import com.tencent.bkrepo.repository.model.TPackageVersion
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * 查询条件构造工具
 */
object PackageQueryHelper {

    // package
    fun packageQuery(projectId: String, repoName: String, key: String): Query {
        val criteria = Criteria.where(TPackage::projectId.name).`is`(projectId)
            .and(TPackage::repoName.name).`is`(repoName)
            .and(TPackage::key.name).`is`(key)
        return Query(criteria)
    }

    fun packageListCriteria(projectId: String, repoName: String, packageName: String?): Criteria {
        return Criteria.where(TPackage::projectId.name).`is`(projectId)
            .and(TPackage::repoName.name).`is`(repoName)
            .apply {
                packageName?.let { and(TPackage::name.name).regex("^$packageName") }
            }
    }

    fun packageListQuery(projectId: String, repoName: String, packageName: String?): Query {
        return Query(packageListCriteria(projectId, repoName, packageName))
    }

    // version
    fun versionQuery(packageId: String, name: String? = null): Query {
        val criteria = Criteria.where(TPackageVersion::packageKey.name).`is`(packageId)
            .apply {
                name?.let { and(TPackageVersion::name.name).`is`(name) }
            }
        return Query(criteria)
    }

    fun versionListCriteria(packageId: String): Criteria {
        return Criteria.where(TPackageVersion::packageKey.name).`is`(packageId)
    }

    fun versionListQuery(packageId: String): Query {
        return Query(versionListCriteria(packageId)).with(Sort.by(Sort.Order(Sort.Direction.DESC, TPackageVersion::ordinal.name)))
    }

    fun versionLatestQuery(packageId: String): Query {
        return versionListQuery(packageId).limit(1)
    }
}
