package com.tencent.bkrepo.registry.manifest

import  com.tencent.bkrepo.registry.JsonUtil

// deserialize manifest data
class ManifestDeserializer {
    companion object {
        fun deserialize(dockerRepo: String, tag: String, manifestType: String, manifestBytes: ByteArray, digest: String): Descriptor {
            val  des: Descriptor
            when (manifestType) {
                ManifestType.valueOf("Schema1").getType() -> {
                    //                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                ManifestType.valueOf("Schema1Signed").getType() -> {
                    //                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                ManifestType.valueOf("Schema2").getType() -> {
                    //                    val manifestJsonBytes = DockerSchemaUtils.fetchSchema2ManifestConfig(repo, manifestBytes, true)
            //                    return ManifestSchema2Deserializer.deserialize(manifestBytes, manifestJsonBytes, dockerRepo, tag, digest)
                }
                ManifestType.valueOf("Schema2List").getType() -> {
                    JsonUtil.getObjectMapper().readValue(buildInfoStr)
            //                    val schema2Path = DockerSchemaUtils.fetchSchema2Path(repo, dockerRepo, manifestBytes, true)
            //                    manifestBytes = DockerSchemaUtils.fetchSchema2Manifest(repo, schema2Path)
            //                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                else -> {
            //                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
            }
            return des
            }
    }
}
