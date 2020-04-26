package com.tencent.bkrepo.npm.resource

import com.tencent.bkrepo.npm.api.DataMigrationResource
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.NpmDataMigrationResponse
import com.tencent.bkrepo.npm.service.DataMigrationService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@RestController
class DataMigrationResourceImpl : DataMigrationResource {

    @Autowired
    private lateinit var dataMigrationService: DataMigrationService

    override fun dataMigrationByFile(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        return dataMigrationService.dataMigrationByFile(artifactInfo)
    }

    override fun dataMigrationByUrl(artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String> {
        return dataMigrationService.dataMigrationByUrl(artifactInfo)
    }
}
