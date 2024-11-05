package com.tencent.bkrepo.job.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories

@SpringBootApplication(scanBasePackages = ["com.tencent.bkrepo.job"])
@EnableMongoRepositories(basePackages = ["com.tencent.bkrepo.job"])
class JobWorkerApplication

fun main(args: Array<String>) {
    runApplication<JobWorkerApplication>(*args)
}
