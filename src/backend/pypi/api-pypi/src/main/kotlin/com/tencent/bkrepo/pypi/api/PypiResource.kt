package com.tencent.bkrepo.pypi.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.pypi.artifact.PackagesArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PackagesArtifactInfo.Companion.PACKAGES_MAPPING_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_PACKAGES_MAPPING_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_ROOT_MAPPING_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_SIMPLE_MAPPING_URI
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
interface PypiResource {

    @PostMapping(PYPI_ROOT_MAPPING_URI)
    fun update(@RequestParam(":action") action: String, artifactFileMap: ArtifactFileMap)

    @GetMapping(PYPI_ROOT_MAPPING_URI)
    fun root(map: ModelMap): String

    @GetMapping(PYPI_SIMPLE_MAPPING_URI)
    fun simple(@ArtifactPathVariable artifactInfo: PypiArtifactInfo)

    @GetMapping(PACKAGES_MAPPING_URI)
    fun packages(@ArtifactPathVariable artifactInfo: PackagesArtifactInfo)
}
