package com.tencent.bkrepo.common.artifact.resolve.file

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.exception.ArtifactResolveException
import javax.servlet.http.HttpServletRequest
import org.apache.commons.fileupload.disk.DiskFileItem
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.multipart.commons.CommonsMultipartFile

/**
 * Multipart表单多文件上传参数解析器
 *
 * @author: carrypan
 * @date: 2019-10-30
 */
class ArtifactFileMapMethodArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return ArtifactFileMap::class.java.isAssignableFrom(parameter.parameterType)
    }

    override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, nativeWebRequest: NativeWebRequest, factory: WebDataBinderFactory?): Any? {
        val request = nativeWebRequest.getNativeRequest(HttpServletRequest::class.java)!!
        val artifactFileMap = ArtifactFileMap()
        if (request is MultipartHttpServletRequest) {
            request.fileMap.forEach { (key, value) -> artifactFileMap[key] = resolveMultipartFile(value) }
        } else throw ArtifactResolveException("Missing multipart file")
        return artifactFileMap
    }

    private fun resolveMultipartFile(multipartFile: MultipartFile): ArtifactFile {
        val commonsMultipartFile = multipartFile as CommonsMultipartFile
        return ArtifactFileFactory.adapt(commonsMultipartFile.fileItem as DiskFileItem)
    }
}
