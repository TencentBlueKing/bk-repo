dependencies {
    implementation("com.google.guava:guava")
    implementation("com.squareup.okhttp3:okhttp")
    implementation(project(":common:common-api"))
    implementation("ch.qos.logback:logback-classic")
}
plugins {
    id("me.champeau.gradle.jmh") version "0.5.3"
}
