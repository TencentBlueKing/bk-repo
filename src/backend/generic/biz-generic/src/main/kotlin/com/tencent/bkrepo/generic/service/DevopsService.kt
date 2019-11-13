package com.tencent.bkrepo.generic.service

import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.generic.model.TDownloadTokenRecord
import com.tencent.bkrepo.generic.pojo.devops.ExternalUrlRequest
import com.tencent.bkrepo.generic.repository.DownloadTokenRepository
import com.tencent.bkrepo.generic.util.TokenUtils
import com.tencent.bkrepo.repository.api.NodeResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class DevopsService @Autowired constructor(
    private val downloadTokenRepository: DownloadTokenRepository,
    private val nodeResource: NodeResource
) {
    fun createExternalDownloadUrl(userId: String, request: ExternalUrlRequest): String {
        logger.info("createExternalDownloadUrl, userId: $userId, request: $request")
        with(request) {
            nodeResource.queryDetail(projectId, repoName, path).data
                ?: throw ErrorCodeException(CommonMessageCode.ELEMENT_NOT_FOUND, path)

            val now = LocalDateTime.now()
            val expireTime = now.plusSeconds(request.ttl.toLong())
            val token = TokenUtils.createToken()
            downloadTokenRepository.save(
                TDownloadTokenRecord(
                    id = null,
                    token = token,
                    projectId = path,
                    repoName = repoName,
                    path = path,
                    user = userId,
                    downloadUser = downloadUser,
                    isInternal = false,
                    expireTime = expireTime,
                    createTime = now,
                    updateTime = now
                )
            )
            return "https://bkdevops.qq.com/bkrepo/api/external/generic/devops/external/$projectId/$repoName$path?token=$token"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DevopsService::class.java)
    }
}
