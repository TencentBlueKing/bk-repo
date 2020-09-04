package com.tencent.bkrepo.common.artifact.resolve.response

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.repository.pojo.node.NodeDetail

class ArtifactResource(
    val inputStream: ArtifactInputStream,
    val artifact: String,
    val node: NodeDetail?,
    val channel: ArtifactChannel
) {
    var characterEncoding: String = StringPool.UTF_8
}
