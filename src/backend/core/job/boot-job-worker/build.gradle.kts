dependencies {
    implementation(project(":common:common-service:service-servlet"))
    implementation(project(":core:job:biz-job"))
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("com.tencent.devops:devops-boot-starter-schedule-worker")
}
