package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.util.JsonUtils
import org.springframework.http.MediaType

/**
 * to deserialize with manifest type
 * @author: owenlxu
 * @date: 2020-02-05
 */
enum class ManifestType(private val mediaType: String) {

    Schema1("application/vnd.docker.distribution.manifest.v1+json"),
    Schema1Signed("application/vnd.docker.distribution.manifest.v1+prettyjws"),
    Schema2("application/vnd.docker.distribution.manifest.v2+json"),
    Schema2List("application/vnd.docker.distribution.manifest.list.v2+json");

    override fun toString(): String {
        return this.mediaType
    }

    companion object {

        // calculate manifest type from media  type
        fun from(mediaType: MediaType?): ManifestType {
            return from(mediaType.toString())
        }

        // calculate manifest type from content type
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

        // calculate manifest type from  manifest byte
        fun from(manifestBytes: ByteArray): ManifestType {
            val manifest = JsonUtils.objectMapper.readTree(manifestBytes)
            val schemaVersionNode = manifest.get("schemaVersion")
            if (schemaVersionNode != null) {
                val schemaVersion = schemaVersionNode.intValue()
                if (schemaVersion == 1) {
                    return Schema1Signed
                }
            }
            val mediaType = manifest.get("mediaType")
            var contentType = EMPTY
            mediaType?.let {
                contentType = mediaType.textValue()
            }
            return from(contentType)
        }
    }
}
