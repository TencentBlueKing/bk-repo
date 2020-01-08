package com.tencent.bkrepo.pypi.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.pypi.api.PypiResource
import com.tencent.bkrepo.pypi.artifact.PackagesArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.service.PypiService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ResponseBody

/**
 * pypi服务接口实现类
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
@Controller
class PypiResourceImpl : PypiResource {

    @Autowired
    private lateinit var pypiService: PypiService

//    @ResponseBody
//    override fun update(action: String, artifactFileMap: ArtifactFileMap) {
//        println(action)
//        println(artifactFileMap.keys)
//    }

    @ResponseBody
    override fun upload(
        pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap

    ) {
        pypiService.upload(pypiArtifactInfo, artifactFileMap)
    }

    @ResponseBody
    override fun simple(artifactInfo: PypiArtifactInfo) {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    @ResponseBody
    override fun packages(artifactInfo: PackagesArtifactInfo) {
        pypiService.packages(artifactInfo)
    }
}
