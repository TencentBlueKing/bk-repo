package com.tencent.bkrepo.docker.common

import org.apache.commons.lang.StringUtils

/**
 * model to describe docker info
 * @author: owenlxu
 * @date: 2019-11-12
 */
class DockerBlobInfoModel(var id: String, var digest: String, var size: String, var created: String) {
    var shortId: String = ""
    var command: String = ""
    var commandText: String = ""

    init {
        if (StringUtils.isNotBlank(id)) {
            this.shortId = id.substring(0, 12)
        }
    }
}
