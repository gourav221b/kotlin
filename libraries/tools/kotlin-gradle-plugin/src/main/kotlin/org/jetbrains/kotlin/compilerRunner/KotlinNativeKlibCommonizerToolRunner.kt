/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.compilerRunner

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KLIB_COMMONIZER_CLASSPATH_CONFIGURATION_NAME
import java.io.File

internal class KotlinNativeKlibCommonizerToolRunner(project: Project) : KotlinToolRunner(project) {
    override val displayName get() = "Kotlin/Native KLIB commonizer"

    override val mainClass: String get() = "org.jetbrains.kotlin.descriptors.commonizer.cli.CommonizerCLI"

    override val classpath by lazy {
        try {
            project.configurations.getByName(KLIB_COMMONIZER_CLASSPATH_CONFIGURATION_NAME).resolve() as Set<File>
        } catch (e: Exception) {
            project.logger.error(
                "Could not resolve KLIB commonizer classpath. Check if Kotlin Gradle plugin repository is configured in $project."
            )
            throw e
        }
    }

    override val isolatedClassLoader by lazy { buildIsolatedClassLoader(classpath) }

    override val mustRunViaExec get() = false
}
