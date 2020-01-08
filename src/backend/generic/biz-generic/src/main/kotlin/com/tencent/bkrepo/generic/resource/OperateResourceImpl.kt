package com.tencent.bkrepo.generic.resource

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.api.OperateResource
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.FileSearchRequest
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
            ResponseBuilder.success(operateService.listFile(userId, projectId, repoName, this.artifactUri, includeFolder, deep))
        }
    }

    override fun searchFile(userId: String, searchRequest: FileSearchRequest): Response<Page<FileInfo>> {
        return ResponseBuilder.success(operateService.searchFile(userId, searchRequest))
    }
}
