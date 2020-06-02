package com.tencent.bkrepo.common.artifact.resolve.response

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import java.nio.charset.StandardCharsets

class ArtifactResource(
    val inputStream: ArtifactInputStream,
    val artifact: String,
    val nodeInfo: NodeInfo?
) {
    var characterEncoding: String = StandardCharsets.UTF_8.name()
}
