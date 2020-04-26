package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.NpmDataMigrationResponse
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping

interface DataMigrationResource {
    @ApiOperation("data migration by file")
    @GetMapping("/{projectId}/{repoName}/dataMigrationByFile")
    fun dataMigrationByFile(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String>

    @ApiOperation("data migration by url")
    @GetMapping("/{projectId}/{repoName}/dataMigrationByUrl")
    fun dataMigrationByUrl(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): NpmDataMigrationResponse<String>
}
