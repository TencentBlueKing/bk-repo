dependencies {
    api(project(":common:common-service:service-servlet"))
    api(project(":media:common-media"))
    api(project(":common:common-job"))
    api(project(":common:common-artifact:artifact-service"))
    api("io.kubernetes:client-java")
}
