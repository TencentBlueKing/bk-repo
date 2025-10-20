dependencies {
    api(project(":common:common-api"))
    implementation("org.mongodb:bson")
    implementation("org.springframework.data:spring-data-mongodb")
    implementation("org.mongodb:mongodb-driver-core")
    implementation("org.apache.commons:commons-lang3")
}
