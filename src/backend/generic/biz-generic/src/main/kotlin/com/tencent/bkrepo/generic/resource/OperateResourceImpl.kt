package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactCoordinate
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
    override fun listFile(userId: String, artifactCoordinate: ArtifactCoordinate, includeFolder: Boolean, deep: Boolean): Response<List<FileInfo>> {
        return artifactCoordinate.run {
            Response.success(operateService.listFile(userId, projectId, repoName, artifactPath.fullPath, includeFolder, deep))
        }
    }

    override fun searchFile(userId: String, searchRequest: FileSearchRequest): Response<Page<FileInfo>> {
        return Response.success(operateService.searchFile(userId, searchRequest))
    }

    override fun getFileDetail(userId: String, artifactCoordinate: ArtifactCoordinate): Response<FileDetail> {
        return artifactCoordinate.run {
            Response.success(operateService.getFileDetail(userId, projectId, repoName, artifactPath.fullPath))
        }
    }

    override fun getFileSize(userId: String, artifactCoordinate: ArtifactCoordinate): Response<FileSizeInfo> {
        return artifactCoordinate.run {
            Response.success(operateService.getFileSize(userId, projectId, repoName, artifactPath.fullPath))
        }
    }

    override fun mkdir(userId: String, artifactCoordinate: ArtifactCoordinate): Response<Void> {
        return artifactCoordinate.run {
            operateService.mkdir(userId, projectId, repoName, artifactPath.fullPath)
            Response.success()
        }
    }

    override fun delete(userId: String, artifactCoordinate: ArtifactCoordinate): Response<Void> {
        return artifactCoordinate.run {
            operateService.delete(userId, projectId, repoName, artifactPath.fullPath)
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
