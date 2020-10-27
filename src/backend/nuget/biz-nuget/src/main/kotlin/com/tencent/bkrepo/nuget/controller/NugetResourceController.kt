package com.tencent.bkrepo.nuget.controller

import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.nuget.api.NugetResource
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.service.NugetResourceService
import com.tencent.bkrepo.nuget.util.ArtifactFileUtils.getNupkgFullPath
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.springframework.web.bind.annotation.RestController

@RestController
class NugetResourceController(
    private val nugetResourceService: NugetResourceService
) : NugetResource {

    override fun push(nugetArtifactInfo: NugetArtifactInfo): String {
        val request = HttpContextHolder.getRequest()
        val factory = DiskFileItemFactory()
        val upload = ServletFileUpload(factory)
        upload.headerEncoding = "UTF-8"
        val fileItemIterator = upload.parseRequest(request)
        fileItemIterator.first().inputStream.use {
            val artifactFile = ArtifactFileFactory.build(it)
            val nupkgIdAndVersion = artifactFile.getNupkgFullPath()
            nugetResourceService.push(nugetArtifactInfo, artifactFile)
            artifactFile.delete()
            return "Successfully published NuPkg to: $nupkgIdAndVersion"
        }
    }
}
