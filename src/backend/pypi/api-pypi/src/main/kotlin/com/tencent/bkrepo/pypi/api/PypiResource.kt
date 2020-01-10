package com.tencent.bkrepo.pypi.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_PACKAGES_MAPPING_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_ROOT_POST_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_SIMPLE_MAPPING_INSTALL_URI
import com.tencent.bkrepo.pypi.pojo.xml.XmlMethodCallRootElement
import org.springframework.http.MediaType
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
interface PypiResource {

//    @PostMapping(PYPI_ROOT_MAPPING_URI)
//    fun update(@RequestParam(":action") action: String, artifactFileMap: ArtifactFileMap)

    @PostMapping(PYPI_ROOT_POST_URI)
    fun upload(
        @ArtifactPathVariable
        pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap
    )

    @PostMapping(PYPI_ROOT_POST_URI,
        consumes = [MediaType.TEXT_XML_VALUE],
        produces = [MediaType.TEXT_XML_VALUE]
    )
    fun search(
        @ArtifactPathVariable
        pypiArtifactInfo: PypiArtifactInfo,
        xmlMethodCallRootElement: XmlMethodCallRootElement
    )

    @GetMapping(PYPI_ROOT_POST_URI)
    fun root(map: ModelMap): String

    @GetMapping(PYPI_SIMPLE_MAPPING_INSTALL_URI)
    fun simple(@ArtifactPathVariable artifactInfo: PypiArtifactInfo)

    @GetMapping(PYPI_PACKAGES_MAPPING_URI)
    fun packages(@ArtifactPathVariable artifactInfo: PypiArtifactInfo)
}
