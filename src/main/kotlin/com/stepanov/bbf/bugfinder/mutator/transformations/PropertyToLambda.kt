package com.stepanov.bbf.bugfinder.mutator.transformations;

import com.intellij.psi.PsiElement
import com.stepanov.bbf.bugfinder.util.getAllPSIChildrenOfType
import com.stepanov.bbf.bugfinder.util.getTrueWithProbability
import com.stepanov.bbf.reduktor.util.getAllChildren
import org.jetbrains.kotlin.psi.KtProperty

class PropertyToLambda : Transformation() {
    private var assembledLambdaName = ""
    override fun transform() {
        val properties = file.getAllPSIChildrenOfType<KtProperty>().filter{ getTrueWithProbability(100)}
        for (property in properties) {
            val argList = property.lastChild.getAllChildren().filter {it.node.elementType.toString() == "IDENTIFIER"}
            val operationString = property.lastChild.text
            val assembledLambdaText = assembleLambda(argList, operationString)
            if (assembledLambdaText.isBlank() || property.identifyingElement == null) continue
            val leftRefString = property.identifyingElement!!.text
            val assembledProp = assemblePropertyWithLambda(leftRefString, argList)

            val lambda = Factory.psiFactory.createProperty(assembledLambdaText)
            val newProperty = Factory.psiFactory.createProperty(assembledProp)

            //val checkCompiling = checker.addNodeIfPossible(property, property, before = true)
            val additionDone = checker.addNodeIfPossible(property, lambda, before = true)
            val replaceDone = checker.replacePSINodeIfPossible(property, newProperty)
            if (replaceDone && additionDone)
                property.delete()
        }
    }
    private fun assembleLambda(argList: List<PsiElement>, operationString: String): String {
        if (operationString.isBlank()) return ""
        //  name assembling
        val randomName = getRandomString(5)
        var lambdaName = randomName
        if (argList.isNotEmpty()) {
            lambdaName += "_with"
            argList.forEach { value -> lambdaName += "_${value.node.firstChildNode.text}" }
        }
        assembledLambdaName = lambdaName
        //  args assembling
        var args = ""

        return if (argList.isNotEmpty()){
            argList.forEach { value ->
                if (value.node.elementType.toString() == "IDENTIFIER")
                    args += "${value.node.firstChildNode.text}:${value.node.elementType}"
                if (value != argList.last())
                    args += ", "
            }
            "val $lambdaName = {$args -> {\n$operationString\n}}"
        } else {
            "val $lambdaName = {\n$operationString\n}"
        }

    }
    private fun assemblePropertyWithLambda(leftRefString : String, argList: List<PsiElement>) : String {
        var result = "val $leftRefString = $assembledLambdaName("
        argList.forEach { arg ->
            result += arg.node.firstChildNode.text
            if (arg != argList.last())
                result += ", "
        }
        return "$result)"
    }
    private fun getRandomString(length: Int) : String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return "lambda_" + (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }
}

