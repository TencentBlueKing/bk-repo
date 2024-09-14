dependencies {
    implementation(project(":common:common-service:service-servlet"))
    implementation(project(":common:common-security"))
    implementation(project(":job:api-schedule"))
    implementation("com.tencent.devops:devops-boot-starter-schedule-server")
}
