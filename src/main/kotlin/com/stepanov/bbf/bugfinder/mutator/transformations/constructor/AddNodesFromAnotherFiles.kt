package com.stepanov.bbf.bugfinder.mutator.transformations.constructor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.checkers.AbstractTreeMutator
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.mutator.transformations.Transformation
import com.stepanov.bbf.bugfinder.util.*
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllChildren
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable
import java.io.File
import kotlin.random.Random
import kotlin.streams.toList
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory.psiFactory as psiFactory

class AddNodesFromAnotherFiles : Transformation() {

    override fun transform() {
        for (i in 0 until randomConst) {
            val ctx = PSICreator.analyze(psi)!!
            usageExamples = collectUsageCases()
            log.debug("Try №$i")
            val line = File("database.txt").bufferedReader().lines().toList().find { it.startsWith("FUN") }!!//.random()
            val randomType = line.takeWhile { it != ' ' }
            val files = line.dropLast(1).takeLastWhile { it != '[' }.split(", ")
            val randomFile = files.random()
            val placeToInsert =
                psi.getAllPSIDFSChildrenOfType<PsiWhiteSpace>().filter { it.text.contains("\n") }.last()//.random()
            val creator2 = PSICreator("")
            val psi2 = creator2.getPSIForText(File("${CompilerArgs.baseDir}/$randomFile").readText())
            if (Project.createFromCode(psi2.text).language != LANGUAGE.KOTLIN) continue
            val anonPsi = psi2.copy() as KtFile
            if (!Anonymizer.anon(anonPsi)) continue
            //val ctx2 = PSICreator.analyze(anonPsi)!!
            val sameTypeNodes = anonPsi.node.getAllChildrenNodes().filter { it.elementType.toString() == randomType }
            val targetNode = sameTypeNodes.random().psi as KtNamedFunction
//            val psiBackup = psi.text
//            val backup = targetNode.text
            //Filter useless nodes
            if (targetNode.getAllPSIChildrenOfType<KtExpression>().isEmpty()) continue
            log.debug("Trying to insert ${targetNode.text}")
            //If node is fun or property then rename
//            if (targetNode.psi is KtProperty || targetNode.psi is KtNamedFunction) {
//                (targetNode.psi as PsiNamedElement).setName(generateRandomName())
//                if (tryToAdd(targetNode.psi, psi, placeToInsert) != null) continue
//            }
            //addPropertiesToFun(targetNode)
            val addedNodes = addAllDataFromAnotherFile(psi, anonPsi)
            if (!mutChecker.checkCompiling(psi)) {
                psi = file.copy() as KtFile
                continue
            }

            val newTargetNode = psi.getAllPSIChildrenOfType<KtNamedFunction>().find { it.name == targetNode.name }
                ?: throw Exception("Cant find node")
            val ctx2 = PSICreator.analyze(psi)!!
            replaceNodesOfFile(addedNodes, ctx2)
            //Add info in psi
//            ctx = PSICreator.analyze(psi)!!
//            log.debug("renamed = ${targetNode.text}")
//            if (targetNode.psi.text == backup || !isRenamingCorrect) continue
//            val res = tryToAdd(targetNode.psi, psi, placeToInsert)
//            creator.updateCtx()
//            ctx = creator.ctx!!
//            if (res != null) {
//                var fl = false
//                val diagnostics = (ctx.diagnostics as MutableDiagnosticsWithSuppression).getOwnDiagnostics()
//                if (diagnostics.any { it.toString().contains("UNREACHABLE_CODE") }) {
//                    log.debug("Expression makes some code unreachable")
//                    fl = true
//                    res.replaceThis(psiFactory.createWhiteSpace("\n"))
//                }
//                if (!fl && res.getAllPSIChildrenOfType<KtNameReferenceExpression>().isEmpty()) {
//                    log.debug("Expression using only constants and useless")
//                    fl = true
//                    res.replaceThis(psiFactory.createWhiteSpace("\n"))
//                }
//                if (fl && !checker.checkCompilingWithBugSaving(psi)) {
//                    log.debug("CANT COMPILE AFTER REMOVING $res")
//                    psi = creator.getPSIForText(psiBackup)
//                    ctx = creator.ctx!!
//                }
//            }
        }
        checker.curFile.changePsiFile(psi.text)
        //file = creator.getPSIForText(psi.text)
    }


    private fun replaceNodesOfFile(
        replaceNodes: List<PsiElement>,
        ctx: BindingContext
    ): Boolean {
//        val table1 = getSlice(placeToInsert)
//            .map { Triple(it, it.text, it.getType(psiCtx)) }
//            .filter { it.third != null }
//        if (table1.isEmpty()) return false
        var nodesForChange = updateReplacement(replaceNodes, ctx).shuffled()
        for (i in 0 until nodesForChange.size) {
            val node = nodesForChange.randomOrNull() ?: continue
            node.first.parents.firstOrNull { it is KtNamedFunction }?.let { addPropertiesToFun(it as KtNamedFunction)  }
            val expressionsWhichWeCanPaste = getInsertableExpressions(node)
            val replacement =
                if (expressionsWhichWeCanPaste.isEmpty()) {
                    //Try to generate value with needed type
                    val expr =
                        node.second?.let {
                            if (it.isNullable() && Random.getTrue(25)) "null"
                            else generateDefValuesAsString(node.second?.makeNotNullable().toString())
                        } ?: ""
                    psiFactory.createExpressionIfPossible(expr)
                } else expressionsWhichWeCanPaste.random().first
            if (replacement == null) {
                log.debug("Cant find and generate replacement for ${node.first.text} type ${node.second}")
                continue
            }
            log.debug("replacement of ${node.first.text} of type ${node.second} is ${replacement.text}")
            mutChecker.replaceNodeIfPossible(
                psi,
                node.first.node,
                replacement.copy().node
            )
            //node.first.replaceThis(replacement.copy())
            nodesForChange = updateReplacement(replaceNodes, ctx)
        }
        return true
//        while (nodesForChange.isNotEmpty()) {
//            val node = nodesForChange.random()
//            val expressionsWhichWeCanPaste = getInsertableExpressions(node)
//            val replacement =
//                if (expressionsWhichWeCanPaste.isEmpty()) {
//                    //Try to generate value with needed type
//                    val expr =
//                        node.second?.let {
//                            if (it.isNullable() && Random.getTrue(25)) "null"
//                            else generateDefValuesAsString(node.second?.makeNotNullable().toString())
//                        } ?: ""
//                    psiFactory.createExpressionIfPossible(expr)
//                } else changeValuesInExpression(expressionsWhichWeCanPaste.random().first, psiCtx)
//            if (replacement == null) {
//                log.debug("Cant find and generate replacement for ${node.first.text} type ${node.second}")
//                return false
//            }
//            log.debug("replacement of ${node.first.text} of type ${node.second} is ${replacement.text}")
//            node.first.replaceThis(replacement)
//            nodesForChange = updateReplacement(replacementNode, replacementCtx)
//        }
        return true
    }

    private fun changeValuesInExpression(
        node: PsiElement,
        ctx: BindingContext
    ): PsiElement {
        return node
//        while (true) {
//            val table = node.getAllPSIChildrenOfType<KtExpression>()
//                .map { it to it.getType(ctx) }
//                .filter { it.second != null && !it.second!!.toString().contains("Nothing") }
//            if (table.isEmpty() || Random.getTrue(20)) break
//            val randomEl = table.randomOrNull() ?: continue
//            val newEl = generateDefValuesAsString(randomEl.second!!.toString())
//            //Get value of same type
//            val newElFromProg = fileTable.filter { it.third == randomEl.second }.randomOrNull()
//            when {
//                newEl.isNotEmpty() && Random.nextBoolean() -> {
//                    try {
//                        psiFactory.createExpressionIfPossible(newEl)?.let { randomEl.first.replaceThis(it) }
//                    } catch (e: Exception) {
//                        log.debug("Cant create expression from $newEl")
//                    }
//                }
//                newElFromProg != null && Random.nextBoolean() -> randomEl.first.replaceThis(newElFromProg.first.copy())
//            }
//        }
//        //Return old node
//        node.replaceThis(nodeCopy)
//        return node.copy() as PsiElement
    }

    private fun addPropertiesToFun(node: KtNamedFunction) {
        val props = usageExamples.filter { it.first is KtProperty }
        val funProps = node.bodyBlockExpression?.allChildren?.filter { it is KtProperty }?.toList() ?: listOf()
        props.reversed().forEach {
            val pr = it.first as KtProperty
            if (funProps.all { it.text != pr.text }) node.bodyBlockExpression?.addProperty(pr)
        }
    }

    private fun collectUsageCases(): List<Triple<KtExpression, String, KotlinType?>> {
        val ctx = PSICreator.analyze(psi)!!
        val boxFun = psi.getBoxFun() ?: return listOf()
        val properties = boxFun.getAllPSIChildrenOfType<KtProperty>()
            .map {
                if (it.typeReference != null) Triple(it, it.text, it.typeReference!!.getAbbreviatedTypeOrType(ctx))
                else Triple(it, it.text, it.initializer?.getType(ctx))
            }
            .filter { it.third != null && !it.third!!.isError } ?: listOf()
        val exprs = boxFun.getAllPSIChildrenOfType<KtExpression> { it !is KtProperty }
            .removeDuplicatesBy { it.text }
            .map { Triple(it, it.text, it.getType(ctx)) }
            .filter {
                it.third != null &&
                        !it.third!!.isError &&
                        it.first !is KtStringTemplateEntry &&
                        it.first !is KtConstantExpression
            }
        val generatedSamples = UsagesSamplesGenerator.generate(psi)
        return (properties + exprs).map { Triple(it.first, it.second, it.third!!) } + generatedSamples
    }


    private fun getInsertableExpressions(
        node: Pair<KtExpression, KotlinType?>
    ): List<Triple<KtExpression, String, KotlinType?>> {
        //Nullable or most common types
        val res = mutableListOf<Triple<KtExpression, String, KotlinType?>>()
        val nodeType = node.second ?: return emptyList()
        for (el in usageExamples.filter { it.first !is KtProperty }) {
            when {
                el.third == nodeType -> res.add(el)
                el.third?.toString() == "$nodeType?" -> res.add(el)
                commonTypesMap[nodeType.toString()]?.contains(el.third?.toString()) ?: false -> res.add(el)
            }
        }
        return res
    }

    private fun addAllDataFromAnotherFile(
        file: KtFile,
        anotherFile: KtFile,
        except: List<PsiElement> = listOf()
    ): List<PsiElement> {
        val topLevelNodes =
            anotherFile.getAllChildren()
                .filter { it.isTopLevelKtOrJavaMember() && it !is KtImportList && it !in except }
        //val block = psiFactory.createBlock("\n\n" + topLevelNodes.joinToString("\n") { it.text })
        //block.lBrace?.delete()
        //block.rBrace?.delete()
        anotherFile.importDirectives.forEach { file.addImport(it) }
        val addedNodes = mutableListOf<PsiElement>()
        topLevelNodes.forEach { node ->
            file.getAllPSIDFSChildrenOfType<PsiElement>().last().parent.let {
                it.add(psiFactory.createWhiteSpace("\n\n"))
                addedNodes.add(it.add(node))
                it.add(psiFactory.createWhiteSpace("\n\n"))
            }
        }
        return addedNodes
    }

    private fun tryToAdd(targetNode: PsiElement, psi: KtFile, placeToInsert: PsiElement): PsiElement? {
        val block = psiFactory.createBlock(targetNode.text)
        block.lBrace?.delete()
        block.rBrace?.delete()
        return AbstractTreeMutator(checker.compilers, checker.project.configuration).addNodeIfPossibleWithNode(
            psi,
            placeToInsert,
            block
        )
    }

    private fun updateReplacement(nodes: List<PsiElement>, ctx: BindingContext) =
        nodes.flatMap { updateReplacement(it, ctx) }

    private fun updateReplacement(node: PsiElement, ctx: BindingContext) =
        node.getAllPSIChildrenOfType<KtExpression>()
            .map { it to it.getType(ctx) }
            .filter {
                it.second != null &&
                        !it.second!!.isError &&
                        it.second.toString() !in blockListOfTypes &&
                        it.second?.toString()?.contains("name provided") == false
            }

    private fun generateRandomName() = java.util.Random().getRandomVariableName(4)

    private val blockListOfTypes = listOf("Unit", "Nothing", "Nothing?")

    private val commonTypesMap = mapOf(
        "Byte" to listOf("Number"),
        "Short" to listOf("Number"),
        "Int" to listOf("Number"),
        "Long" to listOf("Number"),
        "Float" to listOf("Number"),
        "Double" to listOf("Number"),
        "String" to listOf("CharSequence"),
        "Collection" to listOf("Iterable"),
        "MutableCollection" to listOf("Collection", "MutableIterable"),
        "MutableIterable" to listOf("Iterable"),
        "List" to listOf("Collection"),
        "Set" to listOf("Collection"),
        "List" to listOf("Collection"),
        "MutableList" to listOf("List", "MutableCollection"),
        "MutableSet" to listOf("Set", "MutableCollection"),
        "MutableMap" to listOf("Map")
    )

    private fun <T> Collection<T>.randomOrNull(): T? = if (this.isEmpty()) null else this.random()

    private fun Random.getTrue(prob: Int): Boolean =
        Random.nextInt(0, 100) < prob

    private val randomConst = 3//Random.nextInt(700, 1000)
    private var psi = PSICreator("").getPSIForText(file.text)
    lateinit var usageExamples: List<Triple<KtExpression, String, KotlinType?>>
    private val mutChecker = AbstractTreeMutator(checker.compilers, checker.project.configuration)
}