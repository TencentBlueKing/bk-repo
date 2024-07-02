dependencies {
    api(project(":common:common-artifact:artifact-service"))
    implementation("org.bytedeco:javacpp:${Versions.JavaCpp}")
    implementation("org.bytedeco:ffmpeg:${Versions.FFmpegPlatform}")
    implementation("org.bytedeco:javacpp:${Versions.JavaCpp}:windows-x86_64")
    implementation("org.bytedeco:javacpp:${Versions.JavaCpp}:linux-x86_64")
    implementation("org.bytedeco:ffmpeg:${Versions.FFmpegPlatform}:linux-x86_64")
    implementation("org.bytedeco:ffmpeg:${Versions.FFmpegPlatform}:windows-x86_64")
}
