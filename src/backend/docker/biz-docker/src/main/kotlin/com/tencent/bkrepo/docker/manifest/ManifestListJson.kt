package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import java.io.Serializable

/**
 * to deserialize manifest  list json
 * @author: owenlxu
 * @date: 2020-02-05
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class ManifestListJson : Serializable {

    var schemaVersion: Int = 0
    var mediaType: String = EMPTY
    var manifests: List<ManifestJson> = emptyList()

    val manifestType: ManifestType
        get() = ManifestType.from(this.mediaType)

    override fun toString(): String {
        return "ManifestListJson{schemaVersion=" + this.schemaVersion + ", mediaType='" + this.mediaType + '\''.toString() + ", manifests=" + this.manifests + '}'.toString()
    }
}
