
dependencies {
    implementation(project(":archive:api-archive"))
    api(project(":common:common-storage:storage-service"))
    api(project(":common:common-security"))
    api(project(":common:common-service"))
    api(project(":common:common-mongo"))
    api(project(":common:common-mongo-reactive"))
    implementation("io.micrometer:micrometer-registry-prometheus")
}