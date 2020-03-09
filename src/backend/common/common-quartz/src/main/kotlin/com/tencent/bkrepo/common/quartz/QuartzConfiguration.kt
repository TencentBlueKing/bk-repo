package com.tencent.bkrepo.common.quartz

import org.quartz.Calendar
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.Trigger
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.quartz.QuartzProperties
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.scheduling.quartz.SpringBeanJobFactory
import java.util.Properties

@Configuration
@ConditionalOnClass(Scheduler::class, SchedulerFactoryBean::class)
@EnableConfigurationProperties(QuartzProperties::class)
@Import(QuartzMongoConfiguration::class)
class QuartzConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun schedulerFactoryBean(
        properties: QuartzProperties,
        customizers: ObjectProvider<SchedulerFactoryBeanCustomizer>,
        jobDetails: ObjectProvider<JobDetail>,
        calendars: ObjectProvider<Map<String, Calendar>>,
        triggers: ObjectProvider<Trigger>,
        applicationContext: ApplicationContext
    ): SchedulerFactoryBean {
        val schedulerFactoryBean = SchedulerFactoryBean()
        val jobFactory = SpringBeanJobFactory()
        jobFactory.setApplicationContext(applicationContext)
        schedulerFactoryBean.setJobFactory(jobFactory)

        schedulerFactoryBean.isAutoStartup = properties.isAutoStartup
        schedulerFactoryBean.setStartupDelay(properties.startupDelay.seconds.toInt())
        schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(properties.isWaitForJobsToCompleteOnShutdown)
        schedulerFactoryBean.setOverwriteExistingJobs(properties.isOverwriteExistingJobs)
        properties.schedulerName?.let { schedulerFactoryBean.setSchedulerName(it) }

        jobDetails.ifAvailable?.let { schedulerFactoryBean.setJobDetails(it) }
        calendars.ifAvailable?.let { schedulerFactoryBean.setCalendars(it) }
        triggers.ifAvailable?.let { schedulerFactoryBean.setTriggers(it) }
        customizers.orderedStream().forEach { customizer -> customizer.customize(schedulerFactoryBean) }
        schedulerFactoryBean.setQuartzProperties(asProperties(properties.properties))
        return schedulerFactoryBean
    }

    private fun asProperties(source: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(source)
        return properties
    }
}
