package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.repository.pojo.packages.PackageInfo
import com.tencent.bkrepo.repository.service.PackageService
import org.springframework.stereotype.Service

@Service
class PackageServiceImpl : AbstractService(), PackageService {
    override fun findVersion(
        projectId: String,
        repoName: String,
        packageName: String,
        packageVersion: String
    ): PackageInfo {
        TODO("Not yet implemented")
    }

    override fun deletePackage(projectId: String, repoName: String, packageName: String): PackageInfo {
        TODO("Not yet implemented")
    }

    override fun deleteVersion(
        projectId: String,
        repoName: String,
        packageName: String,
        packageVersion: String
    ): PackageInfo {
        TODO("Not yet implemented")
    }

    override fun listPackagePage(
        projectId: String,
        repoName: String,
        packageName: String?,
        pageNumber: Int,
        pageSize: Int
    ): Page<PackageInfo> {
        TODO("Not yet implemented")
    }

    override fun query(queryModel: QueryModel): Page<PackageInfo> {
        TODO("Not yet implemented")
    }
}