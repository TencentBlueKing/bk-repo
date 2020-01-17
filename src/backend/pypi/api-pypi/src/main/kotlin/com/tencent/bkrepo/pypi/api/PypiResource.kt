package com.tencent.bkrepo.pypi.api

import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.api.ArtifactPathVariable
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_PACKAGES_MAPPING_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_ROOT_POST_URI
import com.tencent.bkrepo.pypi.artifact.PypiArtifactInfo.Companion.PYPI_SIMPLE_MAPPING_INSTALL_URI
import org.springframework.http.MediaType
import org.springframework.ui.ModelMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 下载接口
 *
 * @author: carrypan
 * @date: 2019/12/4
 */
interface PypiResource {

    /**
     * pypi upload 接口
     */
    @PostMapping(PYPI_ROOT_POST_URI)
    fun upload(
        @ArtifactPathVariable
        pypiArtifactInfo: PypiArtifactInfo,
        artifactFileMap: ArtifactFileMap
    )

    /**
     * pypi search 接口
     */
    @PostMapping(PYPI_ROOT_POST_URI,
        consumes = [MediaType.TEXT_XML_VALUE],
        produces = [MediaType.TEXT_XML_VALUE]
    )
    fun search(
        @ArtifactPathVariable
        pypiArtifactInfo: PypiArtifactInfo,
        @RequestBody xmlString: String
    )

    @GetMapping(PYPI_ROOT_POST_URI)
    fun root(map: ModelMap): String

    /**
     * pypi simple/{package} 接口
     */
    @GetMapping(PYPI_SIMPLE_MAPPING_INSTALL_URI)
    fun simple(@ArtifactPathVariable artifactInfo: PypiArtifactInfo)

    /**
     * pypi install 接口
     * packages/{package}/{version}/{filename}#md5={md5}
     */
    @GetMapping(PYPI_PACKAGES_MAPPING_URI)
    fun packages(@ArtifactPathVariable artifactInfo: PypiArtifactInfo)
}
