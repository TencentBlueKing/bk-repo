package com.tencent.bkrepo.docker.common

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import org.apache.commons.lang.StringUtils

/**
 * model to describe docker info
 * @author: owenlxu
 * @date: 2019-11-12
 */
class DockerBlobInfoModel(id: String, var digest: String, var size: String, var created: String) {

    var shortId: String = EMPTY
    var command: String = EMPTY
    var commandText: String = EMPTY

    init {
        if (StringUtils.isNotBlank(id)) {
            this.shortId = id.substring(0, 12)
        }
    }
}
