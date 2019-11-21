package com.tencent.bkrepo.common.artifact.resolve

import com.tencent.bkrepo.common.artifact.api.ArtifactData
import com.tencent.bkrepo.common.artifact.api.ArtifactFileItem
import javax.servlet.http.HttpServletRequest
import org.apache.commons.fileupload.servlet.FileCleanerCleanup.FILE_CLEANING_TRACKER_ATTRIBUTE
import org.apache.commons.fileupload.util.Streams
import org.apache.commons.io.FileCleaningTracker
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * application/octet-stream流文件参数解析器
 *
 * @author: carrypan
 * @date: 2019-10-30
 */
class ArtifactDataMethodArgumentResolver : HandlerMethodArgumentResolver {
    override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, nativeWebRequest: NativeWebRequest, factory: WebDataBinderFactory?): Any? {
        val request = nativeWebRequest.getNativeRequest(HttpServletRequest::class.java)
        return request?.let {
            var fileCleaningTracker = it.servletContext.getAttribute(FILE_CLEANING_TRACKER_ATTRIBUTE) as? FileCleaningTracker
            if (fileCleaningTracker == null) {
                fileCleaningTracker = FileCleaningTracker()
                it.servletContext.setAttribute(FILE_CLEANING_TRACKER_ATTRIBUTE, fileCleaningTracker)
            }
            val fileItem = ArtifactFileItemFactory(tracker = fileCleaningTracker).build()
            try {
                Streams.copy(it.inputStream, fileItem.outputStream, true)
                fileItem
            } catch (exception: Exception) {
                try {
                    fileItem.delete()
                } catch (ignored: Exception) {
                    // ignored
                }
                throw RuntimeException("Processing octet-stream request failed.", exception)
            }
        }
    }

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.parameterType.isAssignableFrom(ArtifactFileItem::class.java) && parameter.hasParameterAnnotation(ArtifactData::class.java)
    }
}
