package com.tencent.bkrepo.docker.common

import com.google.common.collect.Lists

class DockerV2InfoModel {
    var tagInfo = DockerTagInfoModel()
    var blobsInfo: List<DockerBlobInfoModel> = Lists.newArrayList()
}
