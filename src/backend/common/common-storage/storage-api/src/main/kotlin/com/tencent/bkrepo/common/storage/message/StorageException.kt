package com.tencent.bkrepo.common.storage.message

import com.tencent.bkrepo.common.api.exception.SystemException
import com.tencent.bkrepo.common.api.message.MessageCode

/**
 * 存储异常
 */
class StorageException(messageCode: MessageCode, vararg params: String) : SystemException(messageCode, *params)
