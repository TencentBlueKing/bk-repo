package com.tencent.bkrepo.docker.model

import com.google.common.collect.Lists

/**
 * docker manifest metadata
 */
class ManifestMetadata : PackageMetadata {
    var tagInfo = DockerTagInfo()
    var blobsInfo: MutableList<DockerBlobInfo> = Lists.newArrayList<DockerBlobInfo>()

    override fun toString(): String {
        return "ManifestMetadata(tagInfo=$tagInfo, blobsInfo=$blobsInfo)"
    }
}
