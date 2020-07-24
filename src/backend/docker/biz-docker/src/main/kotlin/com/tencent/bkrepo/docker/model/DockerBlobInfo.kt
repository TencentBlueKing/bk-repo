package com.tencent.bkrepo.docker.model

import org.apache.commons.lang.StringUtils

/**
 * docker blob info
 * @author: owenlxu
 * @date: 2019-10-15
 */
data class DockerBlobInfo(var id: String, var digest: String?, var size: Long, var created: String) {

    var shortId: String? = null
    var command: String? = null
    var commandText: String? = null
    var mediaType: String? = null
    var urls: MutableList<String>? = null

    init {
        if (StringUtils.isNotBlank(id)) {
            this.shortId = id.substring(0, 12)
        }
    }
}
