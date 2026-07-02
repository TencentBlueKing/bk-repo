package com.tencent.bkrepo.common.mongo.routing

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

/**
 * @DataMongoTest 所需的 Spring Boot 测试配置。
 * common-mongo 是库模块，没有 @SpringBootApplication，需要此配置类来引导测试上下文。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class MongoTestConfiguration
