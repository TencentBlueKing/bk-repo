package com.tencent.bkrepo.rpm.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo.Companion.RPM
import com.tencent.bkrepo.rpm.artifact.RpmArtifactInfo.Companion.RPM_CONFIGURATION
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * rpm 服务接口
 */
interface RpmResource {

    @PutMapping(RPM, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deploy(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo, artifactFile: ArtifactFile)

    @GetMapping(RPM)
    fun install(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo)

    @DeleteMapping(RPM, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(@ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo)

    @PutMapping(RPM_CONFIGURATION, consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun addGroups(
        @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
        @RequestBody groups: MutableSet<String>
    )

    @DeleteMapping(RPM_CONFIGURATION, consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun deleteGroups(
        @ArtifactPathVariable rpmArtifactInfo: RpmArtifactInfo,
        @RequestBody groups: MutableSet<String>
    )
}
