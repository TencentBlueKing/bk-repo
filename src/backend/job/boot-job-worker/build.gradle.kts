dependencies {
    implementation(project(":common:common-service"))
    implementation(project(":job:biz-job"))
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("com.tencent.devops:devops-boot-starter-schedule-worker")
}