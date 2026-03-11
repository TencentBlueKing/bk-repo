package com.tencent.bkrepo.fs.server.service.drive

import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.pojo.RegionResource
import com.tencent.bkrepo.fs.server.context.ReactiveArtifactContextHolder
import com.tencent.bkrepo.fs.server.model.drive.TDriveBlockNode
import com.tencent.bkrepo.fs.server.request.drive.DriveBlockRequest
import com.tencent.bkrepo.fs.server.response.drive.DriveNode
import com.tencent.bkrepo.fs.server.storage.CoArtifactFile
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DriveFileOperationService(
    private val driveStorageManager: CoDriveStorageManager,
    private val driveBlockNodeService: DriveBlockNodeService,
    private val driveSnapSeqService: DriveSnapSeqService,
) {
    suspend fun read(node: DriveNode, range: Range): ArtifactInputStream? {
        with(node) {
            val repo = ReactiveArtifactContextHolder.getRepoDetail()
            val blocks = driveBlockNodeService.listBlocks(range, projectId, repoName, ino).map { it.toRegionResource() }
            return driveStorageManager.loadArtifactInputStream(blocks, range, repo.storageCredentials)
        }
    }

    suspend fun write(artifactFile: CoArtifactFile, request: DriveBlockRequest, user: String): TDriveBlockNode {
        with(request) {
            val blockNode = TDriveBlockNode(
                createdBy = user,
                createdDate = LocalDateTime.now(),
                projectId = projectId,
                repoName = repoName,
                ino = ino,
                startPos = offset,
                sha256 = artifactFile.getFileSha256(),
                crc64ecma = artifactFile.getFileCrc64ecma(),
                size = artifactFile.getSize(),
                snapSeq = driveSnapSeqService.getLatestSnapSeq(projectId, repoName),
            )
            driveStorageManager.storeBlock(artifactFile, blockNode)
            return blockNode
        }
    }

    private fun TDriveBlockNode.toRegionResource(): RegionResource {
        return RegionResource(
            digest = sha256,
            pos = startPos,
            size = size,
            off = 0,
            len = size,
        )
    }
}
