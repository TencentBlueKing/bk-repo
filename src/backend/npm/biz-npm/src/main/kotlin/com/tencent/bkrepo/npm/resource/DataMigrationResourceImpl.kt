package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.DataMigrationResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.migration.NpmDataMigrationResponse
import com.tencent.bkrepo.npm.service.DataMigrationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class DataMigrationResourceImpl : DataMigrationResource {

    @Autowired
    private lateinit var dataMigrationService: DataMigrationService

    override fun dataMigrationByFile(
        artifactInfo: NpmArtifactInfo,
        useErrorData: Boolean
    ): NpmDataMigrationResponse {
        return dataMigrationService.dataMigrationByFile(artifactInfo, useErrorData)
    }

    override fun dataMigrationByUrl(
        artifactInfo: NpmArtifactInfo,
        useErrorData: Boolean
    ): NpmDataMigrationResponse {
        return dataMigrationService.dataMigrationByUrl(artifactInfo, useErrorData)
    }

    override fun dataMigrationByPkgName(
        artifactInfo: NpmArtifactInfo,
        useErrorData: Boolean,
        pkgName: String
    ): NpmDataMigrationResponse {
        return dataMigrationService.dataMigrationByPkgName(artifactInfo, useErrorData, pkgName)
    }
}
