package com.tencent.bkrepo.npm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.migration.NpmPackageDependentMigrationResponse
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping

interface PackageDependentResource {
    @ApiOperation("package dependent migration by url")
    @GetMapping("/{projectId}/{repoName}/dependentMigrationByUrl")
    fun dependentMigrationByUrl(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): NpmPackageDependentMigrationResponse

    @ApiOperation("package dependent migration by file")
    @GetMapping("/{projectId}/{repoName}/dependentMigrationByFile")
    fun dependentMigrationByFile(@ArtifactPathVariable artifactInfo: NpmArtifactInfo): NpmPackageDependentMigrationResponse
}
