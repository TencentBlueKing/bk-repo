
dependencies {
    implementation(project(":archive:api-archive"))
    api(project(":common:common-storage:storage-service"))
    api(project(":common:common-security"))
    api(project(":common:common-service:service-servlet"))
    api(project(":common:common-mongo"))
    api(project(":common:common-mongo-reactive"))
    api(project(":common:common-metadata:metadata-service"))
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.apache.tika:tika-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
    testImplementation("org.mockito.kotlin:mockito-kotlin")
    testImplementation("io.mockk:mockk")
}
