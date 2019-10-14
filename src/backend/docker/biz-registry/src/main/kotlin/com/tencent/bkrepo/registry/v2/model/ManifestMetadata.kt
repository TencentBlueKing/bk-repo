package com.tencent.bkrepo.registry.v2.model

import com.google.common.collect.Lists
import com.tencent.bkrepo.registry.repomd.PackageMetadata

class ManifestMetadata : PackageMetadata {
    var tagInfo = DockerTagInfo()
    var blobsInfo: MutableList<DockerBlobInfo> = Lists.newArrayList<DockerBlobInfo>()
}
