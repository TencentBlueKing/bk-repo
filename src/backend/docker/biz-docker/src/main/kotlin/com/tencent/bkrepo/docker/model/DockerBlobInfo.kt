package com.tencent.bkrepo.docker.model

/**
 * docker blob info
 */
data class DockerBlobInfo(var id: String, var digest: String?, var size: Long, var created: String) {

    var shortId: String? = null
    var command: String? = null
    var commandText: String? = null
    var mediaType: String? = null
    var urls: MutableList<String>? = null

    init {
        if (id.isNotBlank()) {
            this.shortId = id.substring(0, 12)
        }
    }
}
