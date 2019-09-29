package com.tencent.bkrepo.registry.manifest

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import java.io.Serializable

@JsonIgnoreProperties(ignoreUnknown = true)
class ManifestListJson : Serializable {
    var schemaVersion: Int = 0
    var mediaType: String = ""
    var manifests: List<ManifestListJson.ManifestJson> = emptyList()

    val manifestType: ManifestType
        get() = ManifestType.from(this.mediaType)

    override fun toString(): String {
        return "ManifestListJson{schemaVersion=" + this.schemaVersion + ", mediaType='" + this.mediaType + '\''.toString() + ", manifests=" + this.manifests + '}'.toString()
    }

    class ManifestJson : Serializable {
        var mediaType: String = ""
        private val other: MutableMap<String, Any>? = null
        var size: Int = 0
        var digest: String? = null
        var platform: JsonNode? = null

        val manifestType: ManifestType
            get() = ManifestType.from(this.mediaType)

        @JsonAnyGetter
        fun any(): Map<String, Any>? {
            return this.other
        }

        @JsonAnySetter
        operator fun set(name: String, value: Any) {
            this.other!![name] = value
        }

        override fun toString(): String {
            return "ManifestJson{mediaType='" + this.mediaType + '\''.toString() + ", size=" + this.size + ", digest='" + this.digest + '\''.toString() + ", platform=" + this.platform + '}'.toString()
        }
    }
}
