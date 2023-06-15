/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2023 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import org.gradle.internal.jvm.Jvm

plugins {
    `cpp-library`
    `java-library`
    publish
}

dependencies {
    compileOnly("org.slf4j:slf4j-api:2.0.7")
    testImplementation("junit:junit:4.13.1")
    testImplementation("ch.qos.logback:logback-classic:1.3.0")
    testImplementation("com.yammer.metrics:metrics-core:2.2.0")
    testImplementation("org.apache.commons:commons-io:1.3.2")
}

// 生成jni headers
val jniHeaderDirectory = layout.buildDirectory.dir("jniHeaders")
val jniHeadersPath: String = jniHeaderDirectory.get().asFile.canonicalPath
tasks.compileJava {
    options.encoding = "utf8"
    outputs.dir(jniHeaderDirectory)
    options.compilerArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-h",
                jniHeadersPath,
            )
        },
    )
}

// native library
val hostOs: String = System.getProperty("os.name")
library {
    linkage.set(listOf(Linkage.SHARED))
    baseName.set("bkrepo-udt")
    val os = when {
        hostOs == "Mac OS X" -> "MACOSX"
        hostOs == "Linux" -> "LINUX"
        hostOs.startsWith("Windows") -> "WINDOWS"
        else -> throw GradleException("Host OS is not supported in KUDT.")
    }
    source.from(
        file("src/main/cpp/jni"),
        file("src/main/cpp/udt4/src"),
    )
    publicHeaders.from(file("src/main/cpp/udt4/src"))
    binaries.configureEach(CppSharedLibrary::class.java) {
        // jni
        val compileTask = compileTask.get()
        compileTask.dependsOn(tasks.compileJava)
        compileTask.compilerArgs.addAll(listOf("-I", jniHeadersPath))
        // jdk
        compileTask.compilerArgs.addAll(
            compileTask.targetPlatform.map {
                listOf("-I", "${Jvm.current().javaHome.canonicalPath}/include") + when {
                    it.operatingSystem.isMacOsX -> listOf(
                        "-I",
                        "${Jvm.current().javaHome.canonicalPath}/include/darwin",
                    )

                    it.operatingSystem.isLinux -> listOf(
                        "-I",
                        "${Jvm.current().javaHome.canonicalPath}/include/linux",
                    )

                    it.operatingSystem.isWindows -> listOf(
                        "-I",
                        "${Jvm.current().javaHome.canonicalPath}/include/win32",
                    )

                    else -> emptyList()
                }
            },
        )
        // udt编译参数
        when (toolChain) {
            is Gcc, is Clang -> {
                compileTask.compilerArgs.addAll(
                    listOf(
                        "-fPIC",
                        "-Wall",
                        "-Wextra",
                        "-D$os",
                        "-DAMD64",
                        "-finline-functions",
                        "-O3",
                        "-fno-strict-aliasing",
                        "-fvisibility=hidden",
                    ),

                )
            }

            else -> emptyList<String>()
        }
        if (os == "WINDOWS") {
            linkTask.get().linkerArgs.addAll(
                "-l",
                "kernel32",
                "-l",
                "user32",
                "-l",
                "ws2_32",
            )
        }
    }
}

tasks.javadoc {
    isFailOnError = false
    options.encoding = "utf8"
    options.quiet()
}

tasks.compileTestJava {
    options.encoding = "utf8"
}

tasks.test {
    val sharedLib = library.developmentBinary.get() as CppSharedLibrary
    dependsOn(sharedLib.linkTask)
    systemProperty("java.library.path", sharedLib.linkFile.get().asFile.parentFile)
}
val arch: String = System.getProperty("os.arch")
val jarClassifier = when {
    hostOs == "Mac OS X" -> "osx-$arch"
    hostOs == "Linux" -> "linux-$arch"
    hostOs.startsWith("Windows") -> "windows-$arch"
    else -> throw GradleException("Host OS is not supported in bkrepo-udt.")
}
tasks.jar {
    into("lib") {
        from(library.developmentBinary.flatMap { (it as CppSharedLibrary).linkFile })
    }
    archiveClassifier.set(jarClassifier)
}
