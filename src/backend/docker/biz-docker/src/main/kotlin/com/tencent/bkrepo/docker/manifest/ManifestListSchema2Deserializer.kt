package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.util.JsonUtil

class ManifestListSchema2Deserializer {

    companion object {
        fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
            return JsonUtil.readValue(manifestBytes, ManifestListJson::class.java)
        }
    }
}
