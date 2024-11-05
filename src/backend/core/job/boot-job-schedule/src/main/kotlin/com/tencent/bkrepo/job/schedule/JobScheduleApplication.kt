package com.tencent.bkrepo.job.schedule

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JobScheduleApplication

fun main(args: Array<String>) {
    runApplication<JobScheduleApplication>(*args)
}
