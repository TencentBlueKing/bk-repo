package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.ContentUtil

class ManifestDeserializer {
    companion object {

        fun deserialize(
            repo: DockerArtifactRepo,
            context: RequestContext,
            tag: String,
            manifestType: ManifestType,
            bytes: ByteArray,
            digest: DockerDigest
        ): ManifestMetadata {
            var manifestBytes = bytes
            when (manifestType) {
                ManifestType.Schema1 -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema1Signed -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                ManifestType.Schema2 -> {
                    val manifestJsonBytes =
                        ContentUtil.getSchema2ManifestConfigContent(repo, context, manifestBytes, tag)
                    return ManifestSchema2Deserializer.deserialize(manifestBytes, manifestJsonBytes, context.artifactName, tag, digest)
                }
                ManifestType.Schema2List -> {
                    val schema2Path = ContentUtil.getSchema2Path(repo, context, manifestBytes)
                    manifestBytes = ContentUtil.getSchema2ManifestContent(repo, context, schema2Path)
                    return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
                }
                else -> return ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            }
        }
    }
}
