package com.tencent.bkrepo.repository.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.bkrepo.common.api.constant.CommonMessageCode
import com.tencent.bkrepo.common.api.constant.PROJECT_CODE_PREFIX
import com.tencent.bkrepo.common.api.enums.SystemModuleEnum
import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.pojo.MessageCodeDetail
import com.tencent.bkrepo.common.redis.RedisOperation
import com.tencent.bkrepo.repository.model.TMessageCodeDetail
import com.tencent.bkrepo.repository.pojo.MessageCodeCreateRequest
import com.tencent.bkrepo.repository.repository.MessageCodeDetailRepository
import javax.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 消息码service
 *
 * @author: carrypan
 * @date: 2019-10-09
 */
@Service
class MessageCodeDetailService @Autowired constructor(
    private val messageCodeDetailRepository: MessageCodeDetailRepository,
    private val redisOperation: RedisOperation,
    private val objectMapper: ObjectMapper
) {

    @PostConstruct
    fun initMessageCodeDetail() {
        logger.info("Initialize message code detail")
        val messageCodeDetailList = messageCodeDetailRepository.findAll().map { toMessageCodeDetail(it) }
        messageCodeDetailList.filterNotNull().forEach {
            redisOperation.set(
                    key = PROJECT_CODE_PREFIX + it.messageCode,
                    value = objectMapper.writeValueAsString(it),
                    expired = false
            )
        }
    }

    /**
     * 获取code信息详细信息
     */
    fun getMessageCodeDetail(messageCode: String): MessageCodeDetail? {
        return toMessageCodeDetail(messageCodeDetailRepository.findByMessageCode(messageCode))
    }

    /**
     * 添加code信息信息
     */
    fun create(messageCodeCreateRequest: MessageCodeCreateRequest) {
        logger.info("create message code detail: $messageCodeCreateRequest")
        // 判断code信息是否存在，不存在才添加
        messageCodeCreateRequest.takeUnless { getMessageCodeDetail(it.messageCode) != null }?.let {
            val messageCodeDetail = toMessageCodeDetail(
                    messageCodeDetailRepository.save(TMessageCodeDetail(
                            messageCode = it.messageCode,
                            moduleCode = it.systemModule.code,
                            messageDetailZhCn = it.messageDetailZhCn,
                            messageDetailZhTw = it.messageDetailZhTw,
                            messageDetailEn = it.messageDetailEn
                    )))

            redisOperation.set(
                    key = PROJECT_CODE_PREFIX + it.messageCode,
                    value = objectMapper.writeValueAsString(messageCodeDetail),
                    expired = false
            )
        } ?: throw ErrorCodeException(CommonMessageCode.PARAMETER_IS_EXIST)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MessageCodeDetailService::class.java)

        fun toMessageCodeDetail(tMessageCodeDetail: TMessageCodeDetail?): MessageCodeDetail? {
            return tMessageCodeDetail?.let {
                MessageCodeDetail(
                        it.id!!,
                        it.messageCode,
                        SystemModuleEnum.getSystemModule(it.moduleCode).name,
                        it.messageDetailZhCn,
                        it.messageDetailZhTw,
                        it.messageDetailEn
                )
            }
        }
    }
}
