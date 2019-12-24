package com.stepanov.bbf.bugfinder.mutator.transformations

import org.jetbrains.kotlin.psi.KtExpression
import com.stepanov.bbf.bugfinder.executor.MutationChecker
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getRandomBoolean

class AddNotNullAssertions : Transformation() {


    override fun transform() {
        file.getAllPSIChildrenOfType<KtExpression>()
            .filter { getRandomBoolean(3) }
            .map { tryToAddNotNullAssertion(it) }
    }

    private fun tryToAddNotNullAssertion(exp: KtExpression) {
        try {
            val newExp = psiFactory.createExpressionIfPossible("${exp.text}!!") ?: return
            MutationChecker.replacePSINodeIfPossible(file, exp, newExp)
        } catch (e: Exception) {
            return
        }
    }
}
