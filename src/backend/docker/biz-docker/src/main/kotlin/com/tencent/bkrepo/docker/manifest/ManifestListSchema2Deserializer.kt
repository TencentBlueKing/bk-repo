package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.util.JsonUtil
import java.io.IOException
import org.slf4j.LoggerFactory

class ManifestListSchema2Deserializer {
    companion object {
        private val log = LoggerFactory.getLogger(ManifestListSchema2Deserializer::class.java)

        @Throws(IOException::class)
        fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
            val result = JsonUtil.readValue(manifestBytes, ManifestListJson::class.java)
            return result
        }
    }
}
