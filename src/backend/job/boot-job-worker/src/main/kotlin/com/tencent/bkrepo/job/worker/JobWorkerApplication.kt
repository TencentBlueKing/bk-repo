package com.tencent.bkrepo.job.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JobWorkerApplication

fun main(args: Array<String>) {
    runApplication<JobWorkerApplication>(*args)
}
