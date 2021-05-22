package com.stepanov.bbf.bugfinder.mutator.transformations

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.util.getTrueWithProbability
import com.stepanov.bbf.reduktor.util.getAllChildren
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.asJava.elements.KtLightIdentifier
import org.jetbrains.kotlin.psi.*

class ExpressionToInlineFunction: Transformation() {
    private val randomName = getRandomString(11)
    override fun transform() {
        val properties = file.getAllPSIChildrenOfType<KtProperty>().filter{ getTrueWithProbability(100)}
        val grantedProperties = properties.filter { prop ->
            val propChildren = prop.getAllChildren().filter {
                if (it.node.firstChildNode != null) {
                    it.node.firstChildNode.text == " " ||
                            it.node.firstChildNode.elementType == KtNodeTypes.INTEGER_CONSTANT ||
                            it.node.firstChildNode.elementType == KtNodeTypes.OPERATION_REFERENCE
               }
                else
                    false
            }
            propChildren.isNotEmpty()
            }
        for (property in grantedProperties){
            println(property.text)
            var type = -1
            val refs = property.getAllChildren().filter {it.node.elementType.toString() == "IDENTIFIER"}
            val varName = refs.first().node.text
            val binExpr = property.getAllPSIChildrenOfType<KtBinaryExpression>()
            if (binExpr.isEmpty()) continue

            val firstPart = binExpr.first().node.firstChildNode.text
            val anchor = file.getAllPSIChildrenOfType<KtNamedFunction>().last().lastChild
            val operators = property.getAllChildren().filter { it.node.text == "*" }
            when{
                operators.isEmpty() -> type = -1
                operators.first().node.text == "*" -> type = 0
                //TODO add more
            }
            val createdInline = createInlineFun(anchor, property, type)
            val createdInlineCall = createdInlineCallFun(varName, firstPart)
            if (createdInline && createdInlineCall.text != "")
                checker.replacePSINodeIfPossible(property, createdInlineCall)
        }
    }

    private fun createdInlineCallFun(variableName:String, firstPart: String): PsiElement {
        return Factory.psiFactory.createProperty("var $variableName = " +
                "$firstPart.$randomName()")
    }

    private fun createInlineFun(
        anchor: PsiElement, inlineFunExpr: KtExpression, type: Int
    ) : Boolean{
        //TODO: here i assemble it
        var inlineFun : KtExpression
        var inlineFunExprText = inlineFunExpr.text
        when(type){
            0 -> {
                val restOfExprIndex = inlineFunExprText.indexOf("*")
                inlineFunExprText = inlineFunExprText.substring(restOfExprIndex + 1)
                val inlineFunCreated = Factory.psiFactory.createFunction("private inline fun Int." +
                        "$randomName() = \n\tthis *$inlineFunExprText;\n")//вечно грузит
                //val inlineFunCreated = Factory.psiFactory.createBlock("private static final int " +
                //        "$randomName(int \$receiver) {\n\treturn \$receiver *$inlineFunExprText;\n}")
                //inlineFunCreated.lBrace?.delete()
                //inlineFunCreated.rBrace?.delete()
                inlineFun = inlineFunCreated
                println(inlineFunCreated.text)
            }
            else -> {return false}
        }
        return checker.addNodeIfPossible(anchor, inlineFun, before = false)
    }
    private fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}