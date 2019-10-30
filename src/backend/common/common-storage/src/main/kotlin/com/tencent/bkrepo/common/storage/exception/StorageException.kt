package com.tencent.bkrepo.common.storage.exception

import java.lang.RuntimeException

/**
 * 存储异常
 *
 * @author: carrypan
 * @date: 2019/10/29
 */
class StorageException(override val message: String) : RuntimeException(message)
