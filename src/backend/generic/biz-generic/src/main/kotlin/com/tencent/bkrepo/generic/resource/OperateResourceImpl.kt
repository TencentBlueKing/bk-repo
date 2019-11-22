package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.generic.api.OperateResource
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileRenameRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.generic.service.OperateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

/**
 * 文件操作接口实现类
 *
 * @author: carrypan
 * @date: 2019-10-13
 */
@RestController
class OperateResourceImpl @Autowired constructor(
    private val operateService: OperateService
) : OperateResource {
    override fun listFile(userId: String, artifactInfo: ArtifactInfo, includeFolder: Boolean, deep: Boolean): Response<List<FileInfo>> {
        return artifactInfo.run {
            Response.success(operateService.listFile(userId, projectId, repoName, this.coordinate.fullPath, includeFolder, deep))
        }
    }

    override fun searchFile(userId: String, searchRequest: FileSearchRequest): Response<Page<FileInfo>> {
        return Response.success(operateService.searchFile(userId, searchRequest))
    }

    override fun getFileDetail(userId: String, artifactInfo: ArtifactInfo): Response<FileDetail> {
        return artifactInfo.run {
            Response.success(operateService.getFileDetail(userId, projectId, repoName, this.coordinate.fullPath))
        }
    }

    override fun getFileSize(userId: String, artifactInfo: ArtifactInfo): Response<FileSizeInfo> {
        return artifactInfo.run {
            Response.success(operateService.getFileSize(userId, projectId, repoName, this.coordinate.fullPath))
        }
    }

    override fun mkdir(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
        return artifactInfo.run {
            operateService.mkdir(userId, projectId, repoName, this.coordinate.fullPath)
            Response.success()
        }
    }

    override fun delete(userId: String, artifactInfo: ArtifactInfo): Response<Void> {
        return artifactInfo.run {
            operateService.delete(userId, projectId, repoName, this.coordinate.fullPath)
            Response.success()
        }
    }

    override fun rename(userId: String, renameRequest: FileRenameRequest): Response<Void> {
        operateService.rename(userId, renameRequest)
        return Response.success()
    }

    override fun move(userId: String, moveRequest: FileMoveRequest): Response<Void> {
        operateService.move(userId, moveRequest)
        return Response.success()
    }

    override fun copy(userId: String, copyRequest: FileCopyRequest): Response<Void> {
        operateService.copy(userId, copyRequest)
        return Response.success()
    }
}
