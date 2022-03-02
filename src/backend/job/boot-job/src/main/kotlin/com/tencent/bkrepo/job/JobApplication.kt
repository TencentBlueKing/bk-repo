package com.tencent.bkrepo.job

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class JobApplication

fun main(args: Array<String>) {
    runApplication<JobApplication>(*args)
}
