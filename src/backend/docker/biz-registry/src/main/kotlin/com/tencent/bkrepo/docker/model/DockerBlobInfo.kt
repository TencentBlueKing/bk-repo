package com.tencent.bkrepo.docker.model

import org.apache.commons.lang.StringUtils

class DockerBlobInfo(var id: String, var digest: String?, var size: Long, var created: String) {
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

    companion object {
        val MEDIA_TYPE_DIFF_LAYER_TGZ = "application/vnd.docker.image.rootfs.diff.tar.gzip"
        val MEDIA_TYPE_FOREIGN_DIFF_LAYER_TGZ = "application/vnd.docker.image.rootfs.foreign.diff.tar.gzip"
    }
}
