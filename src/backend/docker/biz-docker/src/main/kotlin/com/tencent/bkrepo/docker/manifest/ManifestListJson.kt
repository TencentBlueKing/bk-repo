package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import java.io.Serializable

/**
 * to deserialize manifest list json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ManifestListJson : Serializable {

    var schemaVersion: Int = 0
    var mediaType: String = EMPTY
    var manifests: List<ManifestJson> = emptyList()

    override fun toString(): String {
        return "ManifestListJson{schemaVersion=" + this.schemaVersion + ", mediaType='" + this.mediaType + '\''.toString() + ", manifests=" + this.manifests + '}'.toString()
    }
}
