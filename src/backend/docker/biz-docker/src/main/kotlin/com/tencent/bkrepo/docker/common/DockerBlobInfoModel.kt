package com.tencent.bkrepo.docker.common

import com.tencent.bkrepo.docker.constant.EMPTYSTR
import org.apache.commons.lang.StringUtils

/**
 * model to describe docker info
 * @author: owenlxu
 * @date: 2019-11-12
 */
class DockerBlobInfoModel(var id: String, var digest: String, var size: String, var created: String) {
    var shortId: String = EMPTYSTR
    var command: String = EMPTYSTR
    var commandText: String = EMPTYSTR

    init {
        if (StringUtils.isNotBlank(id)) {
            this.shortId = id.substring(0, 12)
        }
    }
}
