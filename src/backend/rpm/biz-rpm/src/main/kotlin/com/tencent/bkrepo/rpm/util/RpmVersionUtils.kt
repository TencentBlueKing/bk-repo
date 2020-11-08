package com.tencent.bkrepo.rpm.util

import com.tencent.bkrepo.rpm.exception.RpmArtifactMetadataResolveException
import com.tencent.bkrepo.rpm.exception.RpmRequestParamMissException
import com.tencent.bkrepo.rpm.pojo.RpmVersion

object RpmVersionUtils {

    /**
     * filename解析rpm构件版本信息
     */
    fun resolverRpmVersion(filename: String): RpmVersion {
        try {
            val strList = filename.split("-")
            val suffixFormat = strList.last()
            val suffixList = suffixFormat.split(".")
            val arch = suffixList[1]
            val rel = suffixList[0]
            val ver = strList[strList.size - 2]
            val name = filename.removeSuffix("-$ver-$suffixFormat")
            return RpmVersion(name, arch, "0", ver, rel)
        } catch (indexOutOfBoundsException: IndexOutOfBoundsException) {
            throw RpmRequestParamMissException("$filename format error")
        }
    }

    fun RpmVersion.toMetadata(): MutableMap<String, String> {
        return mutableMapOf(
            "name" to this.name,
            "arch" to this.arch,
            "epoch" to this.epoch,
            "ver" to this.ver,
            "rel" to this.rel
        )
    }

    fun Map<String, String>.toRpmVersion(artifactUri: String): RpmVersion {
        return RpmVersion(
            this["name"] ?: throw RpmArtifactMetadataResolveException("$artifactUri: not found metadata.name value"),
            this["arch"] ?: throw RpmArtifactMetadataResolveException("$artifactUri: not found metadata.arch value"),
            this["epoch"] ?: throw RpmArtifactMetadataResolveException("$artifactUri: not found metadata.epoch value"),
            this["ver"] ?: throw RpmArtifactMetadataResolveException("$artifactUri: not found metadata.ver value"),
            this["rel"] ?: throw RpmArtifactMetadataResolveException("$artifactUri: not found metadata.rel value")
        )
    }
}
