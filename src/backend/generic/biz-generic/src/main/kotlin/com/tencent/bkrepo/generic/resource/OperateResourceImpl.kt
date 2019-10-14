package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.generic.api.OperateResource
import com.tencent.bkrepo.generic.pojo.FileDetail
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.operate.FileCopyRequest
import com.tencent.bkrepo.generic.pojo.operate.FileMoveRequest
import com.tencent.bkrepo.generic.pojo.operate.FileSearchRequest
import com.tencent.bkrepo.generic.service.OperateService
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
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
        return Response.success(operateService.listFile(userId, projectId, repoName, path, includeFolder, deep))
    }

    override fun searchFile(userId: String, projectId: String, repoName: String, searchRequest: FileSearchRequest): Response<List<FileInfo>> {
        return Response.success(operateService.searchFile(userId, projectId, repoName, searchRequest.pathPattern, searchRequest.metadataCondition))
    }

    override fun getFileDetail(userId: String, projectId: String, repoName: String, fullPath: String): Response<FileDetail> {
        return Response.success(operateService.getFileDetail(userId, projectId, repoName, fullPath))
    }

    override fun getFileSize(userId: String, projectId: String, repoName: String, fullPath: String): Response<Long> {
        return Response.success(operateService.getFileSize(userId, projectId, repoName, fullPath))
    }

    override fun mkdir(userId: String, projectId: String, repoName: String, fullPath: String): Response<Void> {
        operateService.mkdir(userId, projectId, repoName, fullPath)
        return Response.success()
    }

    override fun delete(userId: String, projectId: String, repoName: String, fullPath: String): Response<Void> {
        operateService.mkdir(userId, projectId, repoName, fullPath)
        return Response.success()
    }

    override fun move(userId: String, projectId: String, repoName: String, fullPath: String, moveRequest: FileMoveRequest): Response<Void> {
        operateService.move(userId, projectId, repoName, fullPath, moveRequest.toPath)
        return Response.success()
    }

    override fun copy(userId: String, projectId: String, repoName: String, fullPath: String, copyRequest: FileCopyRequest): Response<Void> {
        operateService.copy(userId, projectId, repoName, fullPath, copyRequest.toProjectId, copyRequest.toRepoName, copyRequest.toPath)
        return Response.success()
    }

    @ApiOperation("head请求")
    @RequestMapping(method = [RequestMethod.HEAD])
    fun head(@RequestParam name: String): Response<String> {
        println("head request: $name")
        return Response.success("head response")
    }
}
