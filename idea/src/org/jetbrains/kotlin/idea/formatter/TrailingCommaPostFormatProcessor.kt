/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.formatter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtil.getElementType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.formatter.TrailingCommaPostFormatProcessor.Companion.findInvalidCommas
import org.jetbrains.kotlin.idea.formatter.TrailingCommaPostFormatProcessor.Companion.needComma
import org.jetbrains.kotlin.idea.formatter.TrailingCommaPostFormatProcessor.Companion.trailingCommaAllowedInModule
import org.jetbrains.kotlin.idea.formatter.TrailingCommaPostFormatProcessor.Companion.trailingCommaOrLastElement
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.cast

class TrailingCommaPostFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement =
        TrailingCommaPostFormatVisitor(settings).process(source)

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange =
        TrailingCommaPostFormatVisitor(settings).processText(source, rangeToReformat)

    companion object {
        fun findInvalidCommas(commaOwner: KtElement): List<PsiElement> = commaOwner.firstChild
            ?.siblings(withItself = false)
            ?.filter { it.isComma }
            ?.filter {
                it.prevLeaf(true)?.isLineBreak() == true || it.leafIgnoringWhitespace(false) != it.leafIgnoringWhitespaceAndComments(false)
            }?.toList().orEmpty()

        fun needComma(
            commaOwner: KtElement,
            settings: CodeStyleSettings?,
            checkExistingTrailingComma: Boolean = true,
        ): Boolean = when {
            commaOwner is KtWhenEntry ->
                commaOwner.needTrailingComma(settings, checkExistingTrailingComma)

            commaOwner.parent is KtFunctionLiteral ->
                commaOwner.parent.cast<KtFunctionLiteral>().needTrailingComma(settings, checkExistingTrailingComma)

            commaOwner is KtDestructuringDeclaration ->
                commaOwner.needTrailingComma(settings, checkExistingTrailingComma)

            else -> (checkExistingTrailingComma &&
                    trailingCommaOrLastElement(commaOwner)?.isComma == true ||
                    settings?.kotlinCustomSettings?.addTrailingCommaIsAllowedFor(commaOwner) != false) &&
                    commaOwner.isMultiline()
        }

        fun trailingCommaOrLastElement(commaOwner: KtElement): PsiElement? {
            val lastChild = commaOwner.lastSignificantChild ?: return null
            val withSelf = when (getElementType(lastChild)) {
                KtTokens.COMMA -> return lastChild
                in RIGHT_BARRIERS -> false
                else -> true
            }

            return lastChild.getPrevSiblingIgnoringWhitespaceAndComments(withSelf)?.takeIf {
                getElementType(it) !in LEFT_BARRIERS
            }?.takeIfIsNotError()
        }

        fun trailingCommaAllowedInModule(source: PsiElement): Boolean =
            Registry.`is`("kotlin.formatter.allowTrailingCommaInAnyProject", false) ||
                    source.module?.languageVersionSettings?.supportsFeature(LanguageFeature.TrailingCommas) == true

        fun elementBeforeFirstElement(commaOwner: KtElement): PsiElement? = when (commaOwner) {
            is KtParameterList -> {
                val parent = commaOwner.parent
                if (parent is KtFunctionLiteral) parent.lBrace else commaOwner.leftParenthesis
            }
            is KtWhenEntry -> commaOwner.parent.cast<KtWhenExpression>().openBrace
            is KtDestructuringDeclaration -> commaOwner.lPar
            else -> commaOwner.firstChild?.takeIfIsNotError()
        }

        fun elementAfterLastElement(commaOwner: KtElement): PsiElement? = when (commaOwner) {
            is KtParameterList -> {
                val parent = commaOwner.parent
                if (parent is KtFunctionLiteral) parent.arrow else commaOwner.rightParenthesis
            }
            is KtWhenEntry -> commaOwner.arrow
            is KtDestructuringDeclaration -> commaOwner.rPar
            else -> commaOwner.lastChild?.takeIfIsNotError()
        }
    }
}

private fun PsiElement.takeIfIsNotError(): PsiElement? = takeIf { it !is PsiErrorElement }

private class TrailingCommaPostFormatVisitor(val settings: CodeStyleSettings) : TrailingCommaVisitor() {
    private val myPostProcessor = PostFormatProcessorHelper(settings.kotlinCommonSettings)

    override fun process(commaOwner: KtElement) = processIfInRange(commaOwner) {
        processCommaOwner(commaOwner)
    }

    private fun processIfInRange(element: KtElement, block: () -> Unit = {}) {
        if (myPostProcessor.isElementPartlyInRange(element)) {
            block()
        }
    }

    private fun processCommaOwner(parent: KtElement) {
        val lastElement = trailingCommaOrLastElement(parent) ?: return
        val elementType = getElementType(lastElement)
        when {
            needComma(parent, settings, false) -> {
                // add a missing comma
                if (elementType != KtTokens.COMMA && trailingCommaAllowedInModule(parent)) {
                    lastElement.addCommaAfter(KtPsiFactory(parent))
                }

                correctCommaPosition(parent)
            }
            needComma(parent, settings) -> {
                correctCommaPosition(parent)
            }
            elementType == KtTokens.COMMA -> {
                // remove redundant comma
                lastElement.delete()
            }
        }

        if (postFormatIsEnable(parent)) {
            updatePsi(parent)
        }
    }

    private fun updatePsi(element: KtElement) {
        val oldLength = element.textLength
        PostprocessReformattingAspect.getInstance(element.project).disablePostprocessFormattingInside {
            val result = CodeStyleManager.getInstance(element.project).reformat(element)
            myPostProcessor.updateResultRange(oldLength, result.textLength)
        }
    }

    private fun correctCommaPosition(parent: KtElement) {
        if (!postFormatIsEnable(parent)) return
        for (pointerToComma in findInvalidCommas(parent).map { it.createSmartPointer() }) {
            pointerToComma.element?.let {
                correctComma(it)
            }
        }
    }

    fun process(formatted: PsiElement): PsiElement {
        LOG.assertTrue(formatted.isValid)
        formatted.accept(this)
        return formatted
    }

    fun processText(
        source: PsiFile,
        rangeToReformat: TextRange,
    ): TextRange {
        myPostProcessor.resultTextRange = rangeToReformat
        source.accept(this)
        return myPostProcessor.resultTextRange
    }

    companion object {
        private val LOG = Logger.getInstance(TrailingCommaVisitor::class.java)
    }
}


private fun postFormatIsEnable(source: PsiElement): Boolean = !PostprocessReformattingAspect.getInstance(source.project).isDisabled

private fun PsiElement.addCommaAfter(factory: KtPsiFactory) {
    val comma = factory.createComma()
    parent.addAfter(comma, this)
}

private fun correctComma(comma: PsiElement) {
    val prevWithComment = comma.leafIgnoringWhitespace(false) ?: return
    val prevWithoutComment = comma.leafIgnoringWhitespaceAndComments(false) ?: return
    if (prevWithComment != prevWithoutComment) {
        val check = { element: PsiElement -> element is PsiWhiteSpace || element is PsiComment }
        val firstElement = prevWithComment.siblings(forward = false, withItself = true).takeWhile(check).last()
        val commentOwner = prevWithComment.parent
        comma.parent.addRangeAfter(firstElement, prevWithComment, comma)
        commentOwner.deleteChildRange(firstElement, prevWithComment)
    }
}

private val RIGHT_BARRIERS = TokenSet.create(KtTokens.RBRACKET, KtTokens.RPAR, KtTokens.RBRACE, KtTokens.GT, KtTokens.ARROW)
private val LEFT_BARRIERS = TokenSet.create(KtTokens.LBRACKET, KtTokens.LPAR, KtTokens.LBRACE, KtTokens.LT)
private val PsiElement.lastSignificantChild: PsiElement?
    get() = when (this) {
        is KtWhenEntry -> arrow
        is KtDestructuringDeclaration -> rPar
        else -> lastChild
    }

fun PsiElement.leafIgnoringWhitespace(forward: Boolean = true, skipEmptyElements: Boolean = true) =
    leaf(forward) { (!skipEmptyElements || it.textLength != 0) && it !is PsiWhiteSpace }

fun PsiElement.leafIgnoringWhitespaceAndComments(forward: Boolean = true, skipEmptyElements: Boolean = true) =
    leaf(forward) { (!skipEmptyElements || it.textLength != 0) && it !is PsiWhiteSpace && it !is PsiComment }

fun PsiElement.leaf(forward: Boolean = true, filter: (PsiElement) -> Boolean): PsiElement? =
    if (forward) nextLeaf(filter)
    else prevLeaf(filter)

val PsiElement.isComma: Boolean get() = getElementType(this) == KtTokens.COMMA