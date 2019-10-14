package com.tencent.bkrepo.registry.common

import com.google.common.collect.Lists

class DockerV2InfoModel {
    var tagInfo = DockerTagInfoModel()
    var blobsInfo: List<DockerBlobInfoModel> = Lists.newArrayList()
}
