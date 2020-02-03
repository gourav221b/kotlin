/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

enum class JvmDefaultMode(val description: String) {
    DISABLE("disable"),
    ENABLE("enable"),
    ENABLE_WITH_DEFAULT_IMPLS("compatibility"),
    DEFAULT_FOR_ALL("all"),
    DEFAULT_FOR_ALL_NO_DEFAULT_IMPL("all-no-default");

    val isEnabled
        get() = this != DISABLE

    val isCompatibility
        get() = this == ENABLE_WITH_DEFAULT_IMPLS || this == DEFAULT_FOR_ALL

    val forAllMehtodsWithBody
        get() = this == DEFAULT_FOR_ALL || this == DEFAULT_FOR_ALL_NO_DEFAULT_IMPL

    companion object {
        @JvmField
        val DEFAULT = DISABLE

        @JvmStatic
        fun fromStringOrNull(string: String?) = values().find { it.description == string }
    }
}