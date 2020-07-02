package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.util.JsonUtils

class ManifestListSchema2Deserializer {

    companion object {
        fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
            return JsonUtils.objectMapper.readValue(manifestBytes, ManifestListJson::class.java)
        }
    }
}
