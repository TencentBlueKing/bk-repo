package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.DockerWorkContext
import com.tencent.bkrepo.docker.repomd.Repo
import com.tencent.bkrepo.docker.util.DockerSchemaUtils
import com.tencent.bkrepo.docker.v2.model.DockerDigest
import com.tencent.bkrepo.docker.v2.model.ManifestMetadata

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
