package com.tencent.bkrepo.docker.manifest

import com.tencent.bkrepo.docker.artifact.DockerArtifactRepo
import com.tencent.bkrepo.docker.context.RequestContext
import com.tencent.bkrepo.docker.model.DockerDigest
import com.tencent.bkrepo.docker.model.ManifestMetadata
import com.tencent.bkrepo.docker.util.ContentUtil

/**
 * the entrance for deserialize manifest
 * @author: owenlxu
 * @date: 2020-02-05
 */
object ManifestDeserializer {

    // deserialize manifest file and get manifest metadata
    fun deserialize(repo: DockerArtifactRepo, context: RequestContext, tag: String, manifestType: ManifestType, bytes: ByteArray, digest: DockerDigest): ManifestMetadata {
        var manifestBytes = bytes
        val contentUtil = ContentUtil(repo)
        return when (manifestType) {
            ManifestType.Schema1 -> ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            ManifestType.Schema1Signed -> ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            ManifestType.Schema2 -> {
                val manifestJsonBytes = contentUtil.getSchema2ManifestConfigContent(context, manifestBytes, tag)
                ManifestSchema2Deserializer.deserialize(
                    manifestBytes,
                    manifestJsonBytes,
                    context.artifactName,
                    tag,
                    digest
                )
            }
            ManifestType.Schema2List -> {
                val schema2Path = contentUtil.getSchema2Path(context, manifestBytes)
                manifestBytes = contentUtil.getSchema2ManifestContent(context, schema2Path)
                ManifestSchema1Deserializer.deserialize(manifestBytes, digest)
            }
        }
    }
}
