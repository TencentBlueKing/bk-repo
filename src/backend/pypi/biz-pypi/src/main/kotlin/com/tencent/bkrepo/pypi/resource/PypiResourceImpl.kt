package com.tencent.bkrepo.pypi.resource

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.pypi.api.PypiResource
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.pojo.PypiMigrateResponse
import com.tencent.bkrepo.pypi.service.PypiService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestBody
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

    @ResponseBody
    override fun upload(
        pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap

    ) {
        pypiService.upload(pypiArtifactInfo, artifactFileMap)
    }

    @ResponseBody
    override fun search(
        pypiArtifactInfo: PypiArtifactInfo,
        @RequestBody xmlString: String
    ) {
        pypiService.search(pypiArtifactInfo, xmlString)
    }

    @ResponseBody
    override fun simple(artifactInfo: PypiArtifactInfo) {
        pypiService.simple(artifactInfo)
    }

    @ResponseBody
    override fun packages(artifactInfo: PypiArtifactInfo) {
        pypiService.packages(artifactInfo)
    }

    @ResponseBody
    override fun migrateByUrl(ArtifactInfo: PypiArtifactInfo): PypiMigrateResponse<String> {
        return pypiService.migrate(ArtifactInfo)
    }

    /**
     * 数据迁移结果查询接口
     */
    @ResponseBody
    override fun migrateResult(ArtifactInfo: PypiArtifactInfo): PypiMigrateResponse<String> {
        return pypiService.migrateResult(ArtifactInfo)
    }
}
