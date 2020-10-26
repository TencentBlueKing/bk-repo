package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.nuget.util.ArtifactFileUtils.getNupkgFullPath
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.api.StageClient
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class NugetLocalRepository : LocalRepository() {
    @Autowired
    lateinit var packageClient: PackageClient

    @Autowired
    lateinit var stageClient: StageClient

    override fun buildNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val nupkgVersion = context.getArtifactFile().getNupkgFullPath()
        return super.buildNodeCreateRequest(context).copy(
            fullPath = "/$nupkgVersion",
            overwrite = true
        )
    }

    override fun onUpload(context: ArtifactUploadContext) {
        super.onUpload(context)
//        packageClient.createVersion(PackageVersionCreateRequest())
    }
}
