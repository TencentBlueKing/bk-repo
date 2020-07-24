package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.JsonNode
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import java.io.Serializable

/**
 * to deserialize manifest json
 * @author: owenlxu
 * @date: 2020-02-05
 */
class ManifestJson : Serializable {

    var mediaType: String = EMPTY
    private val other: MutableMap<String, Any>? = null
    var size: Int? = null
    var digest: String? = null
    var platform: JsonNode? = null

    @JsonAnySetter
    operator fun set(name: String, value: Any) {
        this.other!![name] = value
    }

    override fun toString(): String {
        return "ManifestJson{mediaType='" + this.mediaType + '\''.toString() + ", size=" + this.size + ", digest='" + this.digest + '\''.toString() + ", platform=" + this.platform + '}'.toString()
    }
}
