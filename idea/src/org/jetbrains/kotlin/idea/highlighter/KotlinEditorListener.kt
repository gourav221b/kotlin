/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.KotlinLanguage

class KotlinEditorListener(val project: Project) : EditorFactoryListener {
    init {
        val editorFactory = EditorFactory.getInstance()
        editorFactory.addEditorFactoryListener(this, project)
    }

    override fun editorReleased(event: EditorFactoryEvent) {

    }

    override fun editorCreated(event: EditorFactoryEvent) {
        val document = event.editor.document
        val project = event.editor.project ?: return
        val language = PsiDocumentManager.getInstance(project).getPsiFile(document)?.language
        if (language?.isKindOf(KotlinLanguage.INSTANCE) == true) {
            Registry.get("makeRunInspectionsAfterCompletionOfGeneralHighlightPassForKotlin").setValue(true)
        }
    }
}