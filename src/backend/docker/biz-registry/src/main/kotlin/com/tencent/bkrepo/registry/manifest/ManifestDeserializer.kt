package com.tencent.bkrepo.registry.manifest

import com.tencent.bkrepo.registry.DockerWorkContext
import com.tencent.bkrepo.registry.repomd.Repo
import com.tencent.bkrepo.registry.util.DockerSchemaUtils
import com.tencent.bkrepo.registry.v2.model.DockerDigest
import com.tencent.bkrepo.registry.v2.model.ManifestMetadata

class ManifestDeserializer {
    companion object {

        fun deserialize(repo: Repo<DockerWorkContext>, dockerRepo: String, tag: String, manifestType: ManifestType, manifestBytes: ByteArray, digest: DockerDigest): ManifestMetadata {
            var manifestBytes = manifestBytes
            when (manifestType) {
                ManifestType.Schema1 -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema1Signed -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema2 -> {
                    val manifestJsonBytes = DockerSchemaUtils.fetchSchema2ManifestConfig(repo, manifestBytes, dockerRepo, tag)
                    return ManifestSchema2Deserializer.deserialize(manifestBytes, manifestJsonBytes, dockerRepo, tag, digest)
                }
                ManifestType.Schema2List -> {
                    val schema2Path = DockerSchemaUtils.fetchSchema2Path(repo, dockerRepo, manifestBytes, true)
                    manifestBytes = DockerSchemaUtils.fetchSchema2Manifest(repo, schema2Path)
                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                else -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            }
        }
    }
}
