package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.migration.NpmDataMigrationResponse
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

interface DataMigrationResource {
    @ApiOperation("data migration by file")
    @GetMapping("/{projectId}/{repoName}/dataMigrationByFile")
    fun dataMigrationByFile(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @RequestParam(defaultValue = "false") useErrorData: Boolean
    ): NpmDataMigrationResponse

    @ApiOperation("data migration by url")
    @GetMapping("/{projectId}/{repoName}/dataMigrationByUrl")
    fun dataMigrationByUrl(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @RequestParam(defaultValue = "false") useErrorData: Boolean
    ): NpmDataMigrationResponse

    @ApiOperation("data migration by PkgName")
    @GetMapping("/{projectId}/{repoName}/dataMigrationByPkgName")
    fun dataMigrationByPkgName(
        @ArtifactPathVariable artifactInfo: NpmArtifactInfo,
        @RequestParam(defaultValue = "false") useErrorData: Boolean,
        pkgName: String
    ): NpmDataMigrationResponse
}
