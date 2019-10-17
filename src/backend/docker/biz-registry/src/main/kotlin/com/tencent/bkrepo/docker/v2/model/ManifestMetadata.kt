package com.tencent.bkrepo.docker.v2.model

import com.google.common.collect.Lists
import com.tencent.bkrepo.docker.repomd.PackageMetadata

class ManifestMetadata : PackageMetadata {
    var tagInfo = DockerTagInfo()
    var blobsInfo: MutableList<DockerBlobInfo> = Lists.newArrayList<DockerBlobInfo>()
}
