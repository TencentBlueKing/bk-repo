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
    implementation("com.google.guava:guava")
    testImplementation("org.mockito.kotlin:mockito-kotlin")
}

configurations.all {
    exclude(module = "devops-plugin-core")
    exclude(group = "com.tencent.devops", module = "devops-web")
}
