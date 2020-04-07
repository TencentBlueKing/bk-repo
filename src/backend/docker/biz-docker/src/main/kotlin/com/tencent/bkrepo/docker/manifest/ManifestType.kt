package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.util.JsonUtil
import java.io.IOException
import org.springframework.http.MediaType

enum class ManifestType private constructor(private val mediaType: String) {
    Schema1("application/vnd.docker.distribution.manifest.v1+json"),
    Schema1Signed("application/vnd.docker.distribution.manifest.v1+prettyjws"),
    Schema2("application/vnd.docker.distribution.manifest.v2+json"),
    Schema2List("application/vnd.docker.distribution.manifest.list.v2+json");

    override fun toString(): String {
        return this.mediaType
    }

    companion object {

        fun from(mediaType: MediaType?): ManifestType {
            return from(mediaType.toString())
        }

        fun from(contentType: String): ManifestType {
            val values = values()
            val size = values.size

            for (index in 0 until size) {
                val manifestType = values[index]
                if (manifestType.mediaType == contentType) {
                    return manifestType
                }
            }

            return Schema1Signed
        }

        @Throws(IOException::class)
        fun from(manifestBytes: ByteArray): ManifestType {
            val manifest = JsonUtil.readTree(manifestBytes)
            val schemaVersionNode = manifest.get("schemaVersion")
            if (schemaVersionNode != null) {
                val schemaVersion = schemaVersionNode.intValue()
                if (schemaVersion == 1) {
/*                    val signatures = manifest.get("signatures") ?: return Schema1*/
                    return Schema1Signed
                }
            }

            val mediaType = manifest.get("mediaType")
            var contentType = ""
            if (mediaType != null) {
                contentType = mediaType.textValue()
            }

            return from(contentType)
        }
    }
}
