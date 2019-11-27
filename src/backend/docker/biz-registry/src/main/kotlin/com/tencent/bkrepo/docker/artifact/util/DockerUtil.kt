package com.tencent.bkrepo.docker.artifact.util

object DockerUtil {

//    fun fromV2Info(info: ManifestMetadata, convertSizeToHumanReadable: Boolean): DockerV2InfoModel {
//        val model = DockerV2InfoModel()
//        model.tagInfo = fromV2TagInfo(info.tagInfo, convertSizeToHumanReadable)
//        model.blobsInfo = info.blobsInfo.stream().map(???({ fromV2BlobInfo(it) })).collect(Collectors.toList<T>()) as List<*>
//        return model
//    }

//    private fun fromV2TagInfo(info: DockerTagInfo, convertSizeToHumanReadable: Boolean): DockerTagInfoModel {
//        val model = DockerTagInfoModel()
//        model.title = info.title
//        model.ports = info.ports
//        model.digest = info.digest.toString()
//        model.volumes = info.volumes
//        if (convertSizeToHumanReadable) {
//            model.totalSize = DockerUnits.humanReadableByteCount(info.totalSize, true)
//        } else {
//            model.totalSizeLong = info.totalSize
//        }
//
//        model.labels = info.labels.entries().stream().map(???({ DockerLabel() })).collect(Collectors.toList<T>()) as List<*>
//        return model
//    }

//    private fun fromV2BlobInfo(info: DockerBlobInfo): DockerBlobInfoModel {
//        val model = DockerBlobInfoModel(info.id, info.digest, DockerUnits.humanReadableByteCount(info.size, true), info.created)
//        model.command = info.command
//        model.commandText = info.commandText
//        model.shortId = info.shortId
//        return model
//    }

//    fun createDockerRepoContext(repoKey: String): DockerArtifactoryService {
//        // val repoPath = RepoPathFactory.create(repoKey)
//        val context = DockerPackageWorkContext()
//        return DockerArtifactoryService(context, repoKey)
//    }
}
