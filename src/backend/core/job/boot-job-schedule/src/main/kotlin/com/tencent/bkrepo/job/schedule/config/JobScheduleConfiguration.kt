package com.tencent.bkrepo.job.schedule.config

import com.tencent.bkrepo.common.security.http.core.HttpAuthSecurity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JobScheduleConfiguration {
    @Bean
    fun httpAuthSecurity(): HttpAuthSecurity {
        return HttpAuthSecurity()
            .withPrefix("/schedule")
    }
}
