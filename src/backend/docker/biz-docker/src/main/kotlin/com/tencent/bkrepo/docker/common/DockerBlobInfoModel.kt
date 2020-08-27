package com.tencent.bkrepo.docker.common

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY

/**
 * model to describe docker info
 */
class DockerBlobInfoModel(id: String, var digest: String, var size: String, var created: String) {

    var shortId: String = EMPTY
    var command: String = EMPTY
    var commandText: String = EMPTY

    init {
        if (id.isNotBlank()) {
            this.shortId = id.substring(0, 12)
        }
    }
}
