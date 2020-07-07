package com.tencent.bkrepo.docker.common

import com.google.common.collect.Lists

/**
 * model to describe docker v2 protocol
 * @author: owenlxu
 * @date: 2019-11-12
 */
class DockerV2InfoModel {
    var tagInfo = DockerTagInfoModel()
    var blobsInfo: List<DockerBlobInfoModel> = Lists.newArrayList()
}
