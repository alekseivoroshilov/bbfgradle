package com.stepanov.bbf.bugfinder.executor

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.stepanov.bbf.bugfinder.BugFinder
import com.stepanov.bbf.bugfinder.executor.CompilerArgs.isABICheckMode
import com.stepanov.bbf.bugfinder.executor.checkers.CompilationChecker
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.checkers.PerformanceChecker
import com.stepanov.bbf.bugfinder.executor.checkers.PerformanceCheckerResult
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.BBFFile
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugManager
import com.stepanov.bbf.bugfinder.manager.BugType
import com.stepanov.bbf.bugfinder.mutator.Mutator
import com.stepanov.bbf.bugfinder.mutator.transformations.Factory
import com.stepanov.bbf.bugfinder.util.StatisticCollector
import com.stepanov.bbf.reduktor.parser.PSICreator
import com.stepanov.bbf.reduktor.util.getAllPSIChildrenOfType
import org.apache.log4j.Logger
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

//Project adaptation
open class Checker(compilers: List<CommonCompiler>, private val withTracesCheck: Boolean = true) :
    CompilationChecker(compilers) {

    constructor(compiler: CommonCompiler) : this(listOf(compiler))

    //Back compatibility
    fun checkTextCompiling(text: String): Boolean = checkCompilingWithBugSaving(Project.createFromCode(text), null)
    fun checkCompilingWithBugSaving(file: PsiFile): Boolean = checkTextCompiling(file.text)


    private fun createPsiAndCheckOnErrors(text: String, language: LANGUAGE): Boolean =
        when (language) {
            LANGUAGE.JAVA -> PSICreator("").getPsiForJava(text, Factory.file.project)
            else -> Factory.psiFactory.createFile(text)
        }.let { tree ->
            tree.getAllPSIChildrenOfType<PsiErrorElement>().isEmpty() && additionalConditions.all { it.invoke(tree) }
        }

    //FALSE IF ERROR
    private fun checkSyntaxCorrectnessAndAddCond(project: Project, curFile: BBFFile?) =
        curFile?.let {
            createPsiAndCheckOnErrors(curFile.text, curFile.getLanguage())
        } ?: project.files.any { createPsiAndCheckOnErrors(it.text, it.getLanguage()) }


    fun checkCompiling(project: Project): Boolean {
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, null)) {
            log.debug("Wrong syntax or breaks conditions")
            checkedConfigurations[allTexts] = false
            return false
        }
        val statuses = compileAndGetStatuses(project)
        return if (statuses.all { it == COMPILE_STATUS.OK }) {
            checkedConfigurations[allTexts] = true
            true
        } else {
            checkedConfigurations[allTexts] = false
            false
        }
    }

    fun checkCompilingWithBugSaving(project: Project, curFile: BBFFile? = null): Boolean {
        log.debug("Compilation checking started")
        val allTexts = project.files.map { it.psiFile.text }.joinToString()
        checkedConfigurations[allTexts]?.let { log.debug("Already checked"); return it }
        //Checking syntax correction
        if (!checkSyntaxCorrectnessAndAddCond(project, curFile)) {
            log.debug("Wrong syntax or breaks conditions")
            StatisticCollector.incField("Incorrect programs")
            checkedConfigurations[allTexts] = false
            return false
        }
        val statuses = compileAndGetStatuses(project)
        when {
            statuses.all { it == COMPILE_STATUS.OK } -> {
                if (isABICheckMode) {
                    checkABI(project)?.let { res ->
                        BugManager.saveBug(
                            Bug(
                                CompilerArgs.getCompilersList(),
                                res.second.readText(),
                                project,
                                BugType.DIFFABI
                            )
                        )
                        return false
                    }
                }
//                if (withTracesCheck && CompilerArgs.isMiscompilationMode) {
//                    val checkRes = checkTraces(project)
//                    checkedConfigurations[allTexts] = checkRes
//                    return checkRes
//                }
                StatisticCollector.incField("Correct programs")
                checkedConfigurations[allTexts] = true
                return true
            }
            statuses.all { it == COMPILE_STATUS.ERROR } -> {
                StatisticCollector.incField("Incorrect programs")
                checkedConfigurations[allTexts] = false
                return false
            }
        }
        checkAndGetCompilerBugs(project).forEach { BugManager.saveBug(it) }
        checkedConfigurations[allTexts] = false
        StatisticCollector.incField("Correct programs")
        return false
    }


    fun compareAfterMutation(
        previousPCR : List<PerformanceCheckerResult>,
        currentPCR : List<PerformanceCheckerResult>,
        compilers: List<CommonCompiler>
    ) : Int {
        for (n in compilers.indices) {
            if (previousPCR[n].compilationTime != currentPCR[n].compilationTime) {
                println("  Comparing ${previousPCR[n].name} and ${currentPCR[n].name}")
                println("   which backends are ${previousPCR[n].compiler} and ${currentPCR[n].compiler} accordingly")
                val b = maxOf(currentPCR[n].compilationTime, previousPCR[n].compilationTime)
                val a = minOf(currentPCR[n].compilationTime, previousPCR[n].compilationTime)
                println("\t Performance difference (COMPILATION):" +
                        "\n \t Previous PCR = ${previousPCR[n].compilationTime}" +
                        "\n \t Current PCR = ${currentPCR[n].compilationTime}" +
                        "\n \t DIFFERENCE AFTER MUTATION (COMPILATION) ${(100 * (b - a) / a).toFloat()} %")

                if (previousPCR[n].executionTime != 0L && currentPCR[n].executionTime != 0L){
                    val exb = maxOf(currentPCR[n].executionTime, previousPCR[n].executionTime)
                    val exa = minOf(currentPCR[n].executionTime, previousPCR[n].executionTime)
                    println("\t Performance difference (EXECUTION):" +
                            "\n \t Previous PCR = ${previousPCR[n].executionTime}" +
                            "\n \t Current PCR = ${currentPCR[n].executionTime}" +
                            "\n \t DIFFERENCE AFTER MUTATION (EXECUTION) ${(100 * (exb - exa) / exa).toFloat()} %")
                }
                else println("Execution time (${previousPCR[n].executionTime} ms) remains UNCHANGED after mutation")
            } else println("Compilation time (${previousPCR[n].compilationTime} ms) remains UNCHANGED after mutation")
        }
        return 0
    }
    fun checkForPerformanceBug (paths: List<String>) {
        val compilers = listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir"))
        val pc = PerformanceChecker(paths, compilers)
        pc.checkPerformance(
            includeExecutionTime = true, enableBugReport = false, printReport = true, saveReport = true)
    }
    fun checkForPerformanceBug (path: String, mutationPhases: Int) : List<Bug>? {
        println("performance bug check")
        println("Checking path: $path")
        var phase = 0
        val bugList = mutableListOf<Bug>()

        val compilers = listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir"))
        val pc = PerformanceChecker(path, compilers)
        var currentPCR = pc.checkPerformance(includeExecutionTime = true, enableBugReport = false, printReport = true)
        if (currentPCR == null) {
            log.debug("got no PerformanceCheckerResult")
            return null
        }
        /*if ( mutationPhases < 1 ) return null

        while (phase < mutationPhases){
            phase++

            val mutationChecker = MutationChecker(compilers, project, project.files.first())
            if (!mutationChecker.checkCompiling()) {
                log.debug("Can't compile")
                exitProcess(0)
            }

            log.debug("Mutated = $project")
            val previousPCR = currentPCR!!
            currentPCR = pc.checkPerformance(includeExecutionTime = true, enableBugReport = false)

            if (currentPCR == null) {
                log.debug("got no PerformanceCheckerResult on phase $phase")
                return null
            }
            compareAfterMutation(previousPCR, currentPCR, compilers)
        }*/
        return bugList
    }
    val additionalConditions: MutableList<(PsiFile) -> Boolean> = mutableListOf()

    private val checkedConfigurations = hashMapOf<String, Boolean>()
    private val log = Logger.getLogger("mutatorLogger")
}