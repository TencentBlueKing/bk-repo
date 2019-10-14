package com.tencent.bkrepo.registry.manifest

import com.tencent.bkrepo.registry.util.JsonUtil
import java.io.IOException
import javax.ws.rs.core.MediaType

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
            var contentType = ""
            if (mediaType != null) {
                contentType = mediaType.toString()
            }

            return from(contentType)
        }

        fun from(contentType: String): ManifestType {
            val var1 = values()
            val var2 = var1.size

            for (var3 in 0 until var2) {
                val manifestType = var1[var3]
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
                    val signatures = manifest.get("signatures") ?: return Schema1

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
