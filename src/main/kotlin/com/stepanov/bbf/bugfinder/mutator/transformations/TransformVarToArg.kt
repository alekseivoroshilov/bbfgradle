package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.util.getAllChildren
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.psi.*

class TransformVarToArg : Transformation() {
    override fun transform() {
        file.getAllPSIChildrenOfType<KtNamedFunction>().forEach { f ->
            getAllInvocations(f).forEach { inv ->
                if (f.valueParameters.size != 0) {
                    val args = inv.valueArgumentList!!.getAllPSIChildrenOfType<KtValueArgument>()
                        .filter { it.node.firstChildNode.elementType == KtNodeTypes.REFERENCE_EXPRESSION }
                    for (arg in args) {
                        val properties = file.getAllPSIDFSChildrenOfType<KtProperty>()//.filter{it.name == arg.node.firstChildNode.text} //find this var
                        for (property in properties) {
                            println(property.text)
                            val refs = property.getAllChildren().filter {it.node.elementType.toString() == "IDENTIFIER"}
                            val varName = refs.first().node.text
                            if (varName != arg.node.text) continue
                                //ensure var value assigned with condition
                                //ensure after the condition a text has a loop
                                /*
                            val conditions = (psiNode.getAllChildren()
                                .filter {it is KtWhenExpression || it is KtIfExpression})
                            for (cond in conditions) {
                                val loops = cond.getAllChildren().filter {
                                    it is KtForExpression || it is KtWhileExpression
                                }
                                if (!loops.isNullOrEmpty()) {
                                    replaceVarToArg(inv, psiNode, elem)
                                    psiNode.delete() //node replaced, to avoid duplication it must be deleted
                                }*/

                                println(inv.text)
                                println(inv.firstChild.text)

                                println(property.lastChild.text)
                                println(arg.text)
                                //val replaceDone = replaceVarToArg(inv, psiNode.lastChild, elem)
                                val newArg = psiFactory.createArgument(property.lastChild.text.toString())
                                println(newArg.text)
                                val replaceDone = checker.replacePSINodeIfPossible(arg, newArg)
                                if (replaceDone)
                                    property.delete()

                        }
                    }
                }
            }
        }
    }

    private fun getAllInvocations(func: KtNamedFunction): List<KtCallExpression> {
        val res = mutableListOf<KtCallExpression>()
        file.getAllPSIChildrenOfType<KtCallExpression>()
            .filter {
                it.firstChild.text.equals(func.name) &&
                        it.valueArguments.size == func.valueParameters.size
            }
            .forEach { res.add(it) }
        return res
    }

    private fun replaceVarToArg(
        invocation: KtCallExpression, replacement: PsiElement, argument: PsiElement
    ): Boolean {
        var argumentString = ""
        val args = invocation.valueArguments
        var oldArg = argument
        args.forEachIndexed { _, arg ->
            if (arg == argument) {
                argumentString += replacement.text
                oldArg = arg
            } else
                argumentString += arg.text
            if (args.last() != arg)
                argumentString += ", "
        }
        //val replace = psiFactory.createFunction("${invocation.node.firstChildNode.text} ( ${argumentString} )")

        /*val replace = psiFactory.createBlock("${replacement.text}")
        replace.lBrace?.delete()
        replace.rBrace?.delete()*/
        val replace = psiFactory.createArgument(replacement.text)

        log.debug(replace.text)
        log.debug(argument.parent.text)
        return checker.replacePSINodeIfPossible(oldArg, replace)
    }

    private val randomConst = 1
}