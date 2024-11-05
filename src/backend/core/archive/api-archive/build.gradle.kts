dependencies {
    api(project(":common:common-artifact:artifact-api"))
    api(project(":common:common-query:query-api"))
    api(project(":repository:api-repository"))
    compileOnly("org.springframework.cloud:spring-cloud-openfeign-core")
}
