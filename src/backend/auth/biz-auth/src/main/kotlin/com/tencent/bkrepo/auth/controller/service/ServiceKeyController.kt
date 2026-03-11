package com.tencent.bkrepo.auth.controller.service

import com.tencent.bkrepo.auth.api.ServiceKeyClient
import com.tencent.bkrepo.auth.dao.repository.KeyRepository
import com.tencent.bkrepo.auth.model.TKey
import com.tencent.bkrepo.auth.pojo.key.KeyInfo
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.service.util.ResponseBuilder
import org.springframework.web.bind.annotation.RestController

@RestController
class ServiceKeyController(
    private val keyRepository: KeyRepository
) : ServiceKeyClient {

    override fun listKeyByUserId(userId: String): Response<List<KeyInfo>> {
        val keys = keyRepository.findByUserId(userId).map { tKey ->
            KeyInfo(
                id = tKey.id!!,
                name = tKey.name,
                key = tKey.key,
                fingerprint = tKey.fingerprint,
                userId = tKey.userId,
                createAt = tKey.createAt
            )
        }
        return ResponseBuilder.success(keys)
    }

    override fun createKeyForFederation(request: KeyInfo): Response<Boolean> {
        // 按 fingerprint 幂等：已存在则跳过
        if (keyRepository.findByFingerprint(request.fingerprint) != null) {
            return ResponseBuilder.success(false)
        }
        keyRepository.save(
            TKey(
                id = null,
                name = request.name,
                key = request.key,
                fingerprint = request.fingerprint,
                userId = request.userId,
                createAt = request.createAt
            )
        )
        return ResponseBuilder.success(true)
    }

    override fun deleteKeyForFederation(id: String): Response<Boolean> {
        keyRepository.findById(id).ifPresent { keyRepository.delete(it) }
        return ResponseBuilder.success(true)
    }
}
