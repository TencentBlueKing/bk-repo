dependencies {
    api(project(":migrate:api-migrate"))
    api(project(":common:common-job"))
    api(project(":common:common-artifact:artifact-service"))
//    testImplementation("io.cucumber:cucumber-java8:6.8.1")
    testImplementation("io.cucumber:cucumber-java8:2.3.1")
    testImplementation("io.cucumber:cucumber-junit:2.3.1")
}