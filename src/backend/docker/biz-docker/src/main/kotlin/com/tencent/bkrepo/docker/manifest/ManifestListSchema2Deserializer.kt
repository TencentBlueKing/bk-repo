package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.util.JsonUtil
import org.slf4j.LoggerFactory

class ManifestListSchema2Deserializer {
    companion object {
        private val logger = LoggerFactory.getLogger(ManifestListSchema2Deserializer::class.java)

        fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
            return JsonUtil.readValue(manifestBytes, ManifestListJson::class.java)
        }
    }
}
