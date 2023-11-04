dependencies {
    implementation(project(":common:common-security")) {
        exclude(module = "common-service")
    }
    implementation(project(":common:common-storage:storage-service"))
    implementation(project(":common:common-mongo-reactive"))
    implementation("io.jsonwebtoken:jjwt-api")
    runtimeOnly("io.jsonwebtoken:jjwt-impl")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson")
    implementation("com.tencent.devops:devops-boot-starter-service")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.playtika.reactivefeign:feign-reactor-spring-cloud-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-influx")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.google.guava:guava")
    implementation("org.springframework.cloud:spring-cloud-starter-sleuth")
    api(project(":common:common-stream"))
    api(project(":fs:api-fs-server"))
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")

    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
}

configurations.all {
    exclude(module = "devops-plugin-core")
    exclude(group = "com.tencent.devops", module = "devops-web")
}
