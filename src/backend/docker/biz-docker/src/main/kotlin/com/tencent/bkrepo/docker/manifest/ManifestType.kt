package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.constant.StringPool.EMPTY
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.docker.constant.DOCKER_MEDIA_TYPE
import com.tencent.bkrepo.docker.constant.DOCKER_SCHEMA_VERSION
import org.springframework.http.MediaType

/**
 * enum type of manifest
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

        /**
         * get ManifestType from http media type
         * @param mediaType http MediaType
         * @return ManifestType manifest type result
         */
        fun from(mediaType: MediaType?): ManifestType {
            return from(mediaType.toString())
        }

        /**
         * get ManifestType from content type
         * @param contentType
         * @return ManifestType manifest type result
         */
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

        /**
         * get ManifestType from manifest byte data
         * @param manifestBytes manifest byte data
         * @return ManifestType manifest type result
         */
        fun from(manifestBytes: ByteArray): ManifestType {
            val manifest = JsonUtils.objectMapper.readTree(manifestBytes)
            val schemaVersionNode = manifest.get(DOCKER_SCHEMA_VERSION)
            schemaVersionNode?.let {
                val schemaVersion = schemaVersionNode.intValue()
                if (schemaVersion == 1) {
                    return Schema1Signed
                }
            }
            val mediaType = manifest.get(DOCKER_MEDIA_TYPE)
            var contentType = EMPTY
            mediaType?.let {
                contentType = mediaType.textValue()
            }
            return from(contentType)
        }
    }
}
