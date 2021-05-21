package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.nuget.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.nuget.pojo.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.nuget.service.NugetPackageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NugetPackageServiceImpl : NugetPackageService, ArtifactService() {

    override fun deletePackage(deleteRequest: PackageDeleteRequest) {
        repository.remove(ArtifactRemoveContext())
    }

    override fun deleteVersion(deleteRequest: PackageVersionDeleteRequest) {
        repository.remove(ArtifactRemoveContext())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetPackageServiceImpl::class.java)
    }
}
