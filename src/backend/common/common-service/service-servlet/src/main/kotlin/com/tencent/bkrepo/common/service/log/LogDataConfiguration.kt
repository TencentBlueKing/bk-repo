package com.tencent.bkrepo.common.service.log

import com.tencent.bkrepo.common.service.log.impl.LogDataServiceImpl
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    LogDataController::class,
    LogDataServiceImpl::class
)
class LogDataConfiguration