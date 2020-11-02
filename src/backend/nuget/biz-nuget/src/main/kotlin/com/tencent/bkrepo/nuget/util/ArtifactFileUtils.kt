package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.nuget.pojo.NupkgVersion
import com.tencent.bkrepo.nuget.util.DecompressUtil.resolverNuspec

object ArtifactFileUtils {
    fun ArtifactFile.getNupkgFullPath(): NupkgVersion {
        this.getInputStream().use {
            val nuspecPackage = it.resolverNuspec()
            val nupkgId = nuspecPackage.metadata.id
            val nupkgVersion = nuspecPackage.metadata.version
            return NupkgVersion(nupkgId, nupkgVersion)
        }
    }
}
