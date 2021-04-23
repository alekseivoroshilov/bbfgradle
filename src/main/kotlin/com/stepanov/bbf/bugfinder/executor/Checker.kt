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
import kotlin.math.abs
import kotlin.system.exitProcess

//Project adaptation
open class Checker(compilers: List<CommonCompiler>, private val withTracesCheck: Boolean = true) :
    CompilationChecker(compilers) {

    constructor(compiler: CommonCompiler) : this(listOf(compiler))

    //private val differences = mutableListOf<Pair<Float, Float>>()
    private val differences = mutableListOf<List<Float>>() //for a fitness function
    private var name = ""
    private var mutationsPath = ""

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

    fun checkCompilingWithBugSaving(project: Project, curFile: BBFFile? = null ): Boolean {
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
                //println("Project is ok, mutating")
                //mutateAndCheck(project)
                //return fitnessFunction()
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
    fun setProjectName(name : String){
        this.name = name
    }
    fun mutateAndCheck(project: Project, name: String){
        var isValidMutation = true

        val originalPcrList = checkForPerformance(project) //original PCR for original project
        var currentProject = project.copy()

        val pc = PerformanceChecker(compilers)
        val originalDifference = pc.findBiggestDifference(originalPcrList, executionTime = true)
        println("Project name: $name")
        differences.add(originalDifference)
        for (i in 0..3) {      //number of mutation histories
            createNewMutationHistory(currentProject, i) //new folder, new history
            currentProject =
                Project.createFromCode(readMutationCode(i - 1)) // read pre-last mutant from the previous stage
            isValidMutation = true
            while (isValidMutation) {
                println(mutationsPath)
                val bugFinder = BugFinder(mutationsPath)
                bugFinder.mutate(currentProject, currentProject.files.first(), listOf(/*::noBoxFunModifying*/))

                val afterPcrList = this.checkForPerformance(currentProject)
                differences.add(pc.findBiggestDifference(afterPcrList, executionTime = true))
                println(differences)
                isValidMutation = this.fitnessFunction()

                if (isValidMutation) { //if TRUE - start from latest valid mutation
                    println("CONTINUE")
                    saveProject(currentProject)
                } else {
                    println("OVER")
                    saveProject(currentProject)
                    differences.clear()
                    differences.add(originalDifference)
                }
            }
        }
    }

    private fun compareAfterMutation(
        previousPCR : List<PerformanceCheckerResult>,
        currentPCR : List<PerformanceCheckerResult>,
        report: Boolean = false //to turn off prints
    ) {

        for (n in compilers.indices) {
            val previousC = previousPCR[n].compilationTime
            val previousE = previousPCR[n].executionTime
            val currentC = currentPCR[n].compilationTime
            val currentE = currentPCR[n].executionTime
            if (previousC != currentC) {
                val b = maxOf(currentC, previousC)
                val a = minOf(currentC, currentC)
                if (report) {
                    println("  Comparing ${previousPCR[n].name} and ${currentPCR[n].name}")
                    println("   which backends are ${previousPCR[n].compiler} and ${currentPCR[n].compiler} accordingly")
                    println("\t Performance difference (COMPILATION):" +
                            "\n \t Previous PCR = $previousC" +
                            "\n \t Current PCR = $currentC" +
                            "\n \t DIFFERENCE AFTER MUTATION (COMPILATION) ${(100 * (b - a) / a).toFloat()} %")
                }
                if (previousE != 0L && currentE != 0L){
                    val exb = maxOf(currentE, previousE)
                    val exa = minOf(currentE, previousE)


                    //differences.add(Pair(currentC - previousC, currentE - previousE))
                    if (report)
                        println("\t Performance difference (EXECUTION):" +
                            "\n \t Previous PCR = $previousE" +
                            "\n \t Current PCR = $currentE" +
                            "\n \t DIFFERENCE AFTER MUTATION (EXECUTION) ${(100 * (exb - exa) / exa).toFloat()} %")

                }
                //else differences.add(Pair(currentC - previousC, 0))
            }
        }
    }
    private fun fitnessFunction() : Boolean{
        /* TODO MORE INTELLIGENT STUFF
        var deltaC = 0L
        var bufC = mutableListOf<Long>()
        var bufE = mutableListOf<Long>()
        var deltaE = 0L
        for (difference in differences){
            bufC.add(difference.first)
            bufE.add(difference.second)
        }
        return bufC.average() < differences.last().first || bufE.average() < differences.last().second
        */
        val prevDiff = differences[differences.size - 2]
        val curDiff = differences.last()
        val deltaC = abs(prevDiff.first() - curDiff.first())
        val deltaE = abs(prevDiff.last() - curDiff.last())
        println("Current delta: $deltaC and $deltaE")
        return abs(prevDiff.first() - curDiff.first()) > 10 || abs(prevDiff.last() - curDiff.last()) > 10
    }
    fun checkForPerformance (paths: List<String>) {
        val compilers = listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir"))
        val pc = PerformanceChecker(paths, compilers)
        pc.checkPerformance(
            includeExecutionTime = true, enableBugReport = false, printReport = true,
            saveReport = false, includeMemoryUsage = true)
    }
    fun checkForPerformance (project: Project) : List<PerformanceCheckerResult>{
        val pc = PerformanceChecker(project, compilers)
        return pc.checkPerformance(includeExecutionTime = true, enableBugReport = true, printReport = true)
    }
/*fun checkForPerformanceBug (path: String) : List<Bug>? {
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
    if ( mutationPhases < 1 ) return null

    whi (phase < mutationPhases){
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
    }
    return bugList
}*/

    private fun createNewMutationHistory(project : Project, stage : Int){
        //val name = currentProject.files.first().name
        val name = this.name.replace(".","_")
        val path = System.getProperty("user.dir")
        val file = File("$path/tmp/mutations/${name}/$stage")
        /*var i = 0
        while (file.exists()) {
            i++
            file = File("$path/tmp/mutations/$name/$i")
        }*/
        file.mkdirs()
        println(file.path)
        mutationsPath = file.path
        if (stage == 0) {
            val textFile = File("$mutationsPath/original.kt")
            textFile.createNewFile()
            project.saveInOneFile("$mutationsPath/original.kt")
        } else
            saveProject(project)
    }

    private fun saveProject(project : Project) {
        //if(mutationsPath.split("/").last() == "0"){
        var file = File("${mutationsPath}/modifiedProject0.kt")
        var i = 0
        while (file.exists()) {
            i++
            file = File("${mutationsPath}/modifiedProject$i.kt")

        }
        file.createNewFile()
        project.saveInOneFile("${mutationsPath}/modifiedProject$i.kt")

    }

    //function for the other type of mutation
    private fun readMutationCode(stage : Int) : String{ //try to read the most promising mutant
        val path = System.getProperty("user.dir")
        val name = this.name.replace(".","_")
        val previousMutationPath = "$path/tmp/mutations/${name}/$stage"
        var file = File("$previousMutationPath/modifiedProject0.kt")
        var i = 0
        while (file.exists()) {
            i++
            file = File("$previousMutationPath/modifiedProject$i.kt")
        }

        val startMutationFrom = File("$previousMutationPath/modifiedProject${i-2}.kt")

        return if (startMutationFrom.exists())
            startMutationFrom.readText()
        else
            File("$mutationsPath/original.kt").readText()

    }

val additionalConditions: MutableList<(PsiFile) -> Boolean> = mutableListOf()

private val checkedConfigurations = hashMapOf<String, Boolean>()
private val log = Logger.getLogger("mutatorLogger")
}