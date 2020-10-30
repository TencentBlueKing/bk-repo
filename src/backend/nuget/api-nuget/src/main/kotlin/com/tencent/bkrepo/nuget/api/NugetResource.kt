package com.tencent.bkrepo.nuget.api

import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo.Companion.NUGET_RESOURCE
import org.springframework.web.bind.annotation.PutMapping

interface NugetResource {

    @PutMapping(NUGET_RESOURCE)
    fun push(@ArtifactPathVariable nugetArtifactInfo: NugetArtifactInfo): String
}
