package com.tencent.bkrepo.common.storage.event

import com.tencent.bkrepo.common.service.log.LoggerHolder
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.listener.RetryListenerSupport

/**
 * 文件存储重试监听器
 * @author: carrypan
 * @date: 2020/1/8
 */
class FileStoreRetryListener : RetryListenerSupport() {
    override fun <T : Any, E : Throwable> onError(context: RetryContext, callback: RetryCallback<T, E>, throwable: Throwable) {
        logger.warn("Retryable method [${context.getAttribute(RetryContext.NAME)}] threw [${context.retryCount}]th exception {$throwable}")
    }

    companion object {
        private val logger = LoggerHolder.SYSTEM_ERROR
    }
}
