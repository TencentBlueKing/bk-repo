package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.common.api.util.JsonUtils

/**
 * to deserialize manifest schema2 manifest list
 * @author: owenlxu
 * @date: 2020-02-05
 */
class ManifestListSchema2Deserializer {

    companion object {
        fun deserialize(manifestBytes: ByteArray): ManifestListJson? {
            return JsonUtils.objectMapper.readValue(manifestBytes, ManifestListJson::class.java)
        }
    }
}
