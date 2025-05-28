dependencies {
    api(project(":common:common-artifact:artifact-api"))
    api(project(":common:common-query:query-api"))
    api(project(":common:common-archive:archive-api"))
    compileOnly("org.springframework.cloud:spring-cloud-openfeign-core")
}
