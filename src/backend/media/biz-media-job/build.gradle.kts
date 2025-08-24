dependencies {
    api(project(":common:common-service:service-servlet"))
    api(project(":media:common-media"))
    api("net.javacrumbs.shedlock:shedlock-spring")
    api("net.javacrumbs.shedlock:shedlock-provider-mongo") {
        exclude(group = "org.mongodb", module = "mongo-java-driver")
    }
    api("io.kubernetes:client-java")
}
