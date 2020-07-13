package com.tencent.bkrepo.generic.artifact

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_MD5
import com.tencent.bkrepo.common.artifact.config.ATTRIBUTE_OCTET_STREAM_SHA256
import com.tencent.bkrepo.common.artifact.exception.ArtifactValidateException
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.service.util.HeaderUtils
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import com.tencent.bkrepo.generic.constant.BKREPO_META_PREFIX
import com.tencent.bkrepo.generic.constant.GenericMessageCode
import com.tencent.bkrepo.generic.constant.HEADER_EXPIRES
import com.tencent.bkrepo.generic.constant.HEADER_MD5
import com.tencent.bkrepo.generic.constant.HEADER_OVERWRITE
import com.tencent.bkrepo.generic.constant.HEADER_SEQUENCE
import com.tencent.bkrepo.generic.constant.HEADER_SHA256
import com.tencent.bkrepo.generic.constant.HEADER_UPLOAD_ID
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

/**
 *
 * @author: carrypan
 * @date: 2019/11/28
 */
@Component
class GenericLocalRepository : LocalRepository() {

    override fun onUploadValidate(context: ArtifactUploadContext) {
        super.onUploadValidate(context)
        // 校验sha256
        val calculatedSha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val uploadSha256 = HeaderUtils.getHeader(HEADER_SHA256)
        if (uploadSha256 != null && !calculatedSha256.equals(uploadSha256, true)) {
            throw ArtifactValidateException("File sha256 validate failed.")
        }
        // 校验md5
        val calculatedMd5 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_MD5] as String
        val uploadMd5 = HeaderUtils.getHeader(HEADER_MD5)
        if (uploadMd5 != null && !calculatedMd5.equals(calculatedMd5, true)) {
            throw ArtifactValidateException("File md5 validate failed.")
        }
    }

    override fun onUpload(context: ArtifactUploadContext) {
        val uploadId = context.request.getHeader(HEADER_UPLOAD_ID)
        val sequence = context.request.getHeader(HEADER_SEQUENCE)?.toInt()
        if (isBlockUpload(uploadId, sequence)) {
            this.blockUpload(uploadId, sequence!!, context)
            context.response.contentType = StringPool.MEDIA_TYPE_JSON
            context.response.writer.println(ResponseBuilder.success().toJsonString())
        } else {
            val nodeCreateRequest = getNodeCreateRequest(context)
            storageService.store(nodeCreateRequest.sha256!!, context.getArtifactFile(), context.storageCredentials)
            val createResult = nodeResource.create(nodeCreateRequest)
            context.response.contentType = StringPool.MEDIA_TYPE_JSON
            context.response.writer.println(createResult.toJsonString())
        }
    }

    /**
     * 判断是否为分块上传
     */
    private fun isBlockUpload(uploadId: String?, sequence: Int?): Boolean {
        return !uploadId.isNullOrBlank() && sequence != null
    }

    private fun blockUpload(uploadId: String, sequence: Int, context: ArtifactUploadContext) {
        if (!storageService.checkBlockId(uploadId, context.storageCredentials)) {
            throw ErrorCodeException(GenericMessageCode.UPLOAD_ID_NOT_FOUND, uploadId)
        }
        val calculatedSha256 = context.contextAttributes[ATTRIBUTE_OCTET_STREAM_SHA256] as String
        val overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE)
        storageService.storeBlock(uploadId, sequence, calculatedSha256, context.getArtifactFile(), overwrite, context.storageCredentials)
    }

    override fun getNodeCreateRequest(context: ArtifactUploadContext): NodeCreateRequest {
        val request = super.getNodeCreateRequest(context)
        return request.copy(
            expires = HeaderUtils.getLongHeader(HEADER_EXPIRES),
            overwrite = HeaderUtils.getBooleanHeader(HEADER_OVERWRITE),
            metadata = parseMetadata(context.request)
        )
    }

    /**
     * 从header中提取metadata
     */
    private fun parseMetadata(request: HttpServletRequest): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        val headerNames = request.headerNames
        for (headerName in headerNames) {
            if (headerName.startsWith(BKREPO_META_PREFIX, true)) {
                val key = headerName.substring(BKREPO_META_PREFIX.length).trim()
                if (key.isNotEmpty()) {
                    metadata[key] = HeaderUtils.getUrlDecodedHeader(headerName)!!
                }
            }
        }
        return metadata
    }
}
