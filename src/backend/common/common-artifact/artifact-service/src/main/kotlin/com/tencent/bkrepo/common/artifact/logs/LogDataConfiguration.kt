package com.tencent.bkrepo.common.artifact.logs

import com.tencent.bkrepo.common.artifact.logs.impl.LogDataServiceImpl
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import(
    LogDataController::class,
    LogDataServiceImpl::class
)
class LogDataConfiguration