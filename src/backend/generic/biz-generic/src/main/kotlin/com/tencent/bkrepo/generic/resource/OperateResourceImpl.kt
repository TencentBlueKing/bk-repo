package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.OperateResource
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSizeInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileRenameRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.generic.service.OperateService
import com.tencent.bkrepo.generic.util.PathUtils
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

    override fun listFile(userId: String, projectId: String, repoName: String, path: String, includeFolder: Boolean, deep: Boolean): Response<List<FileInfo>> {
        return Response.success(operateService.listFile(userId, projectId, repoName, PathUtils.toFullPath(path), includeFolder, deep))
    }

    override fun searchFile(userId: String, searchRequest: FileSearchRequest): Response<Page<FileInfo>> {
        return Response.success(operateService.searchFile(userId, searchRequest))
    }

    override fun getFileDetail(userId: String, projectId: String, repoName: String, fullPath: String): Response<FileDetail> {
        return Response.success(operateService.getFileDetail(userId, projectId, repoName, PathUtils.toFullPath(fullPath)))
    }

    override fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): Response<FileSizeInfo> {
        return Response.success(operateService.getFileSize(userId, projectId, repoName, PathUtils.toFullPath(fullPath)))
    }

    override fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String): Response<Void> {
        operateService.mkdir(userId, projectId, repoName, PathUtils.toFullPath(fullPath))
        return Response.success()
    }

    override fun delete(userId: String, projectId: String, repoName: String, fullPath: String): Response<Void> {
        operateService.delete(userId, projectId, repoName, PathUtils.toFullPath(fullPath))
        return Response.success()
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
