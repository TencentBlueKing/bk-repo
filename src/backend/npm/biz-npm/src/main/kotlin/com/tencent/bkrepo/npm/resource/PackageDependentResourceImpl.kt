package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.PackageDependentResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.migration.NpmPackageDependentMigrationResponse
import com.tencent.bkrepo.npm.service.PackageDependentService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class PackageDependentResourceImpl : PackageDependentResource {

    @Autowired
    private lateinit var pkgDependentService: PackageDependentService

    override fun dependentMigrationByUrl(artifactInfo: NpmArtifactInfo): NpmPackageDependentMigrationResponse {
        return pkgDependentService.dependentMigrationByUrl(artifactInfo)
    }

    override fun dependentMigrationByFile(artifactInfo: NpmArtifactInfo): NpmPackageDependentMigrationResponse {
        return pkgDependentService.dependentMigrationByFile(artifactInfo)
    }
}
