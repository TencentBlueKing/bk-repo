package com.tencent.bkrepo.docker.manifest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.io.Serializable

@JsonIgnoreProperties(ignoreUnknown = true)
class ManifestListJson : Serializable {
    var schemaVersion: Int = 0
    var mediaType: String = ""
    var manifests: List<ManifestJson> = emptyList()

    val manifestType: ManifestType
        get() = ManifestType.from(this.mediaType)

    override fun toString(): String {
        return "ManifestListJson{schemaVersion=" + this.schemaVersion + ", mediaType='" + this.mediaType + '\''.toString() + ", manifests=" + this.manifests + '}'.toString()
    }
}
