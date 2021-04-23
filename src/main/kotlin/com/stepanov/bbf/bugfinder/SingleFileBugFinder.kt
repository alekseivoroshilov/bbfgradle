package com.stepanov.bbf.bugfinder

import com.stepanov.bbf.bugfinder.executor.Checker
import com.stepanov.bbf.bugfinder.executor.CompilerArgs
import com.stepanov.bbf.bugfinder.executor.checkers.MutationChecker
import com.stepanov.bbf.bugfinder.executor.checkers.TracesChecker
import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.tracer.Tracer
import com.stepanov.bbf.bugfinder.util.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.io.File
import kotlin.system.exitProcess

class SingleFileBugFinder(dir: String) : BugFinder(dir) {

    fun testPerformanceBugCheck(files : List<String>) {
        println("Let's go")

        val checker = Checker(listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir")))
        checker.checkForPerformance(files)
    }
    fun findPerformanceBugs(){
        println("Find performance bug")
        val project = Project.createFromCode(File(dir).readText())
        val checker = MutationChecker(listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir")), project, project.files.first())
        val name = dir.split("/").last()
        println(name)
        checker.setProjectName(name)
        //checker.checkCompilingWithBugSaving(project)
        checker.mutateAndCheck(project, name)
    }
    fun findPerformance(){
        println("Check memory usage")
        val checker = Checker(listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir")))
        val files = listOf(dir)
        checker.checkForPerformance(files)
    }
    fun findBugsInFile() {
        try {

            ++counter
            log.debug("Name = $dir")
            val project = Project.createFromCode(File(dir).readText())
            if (project.language != LANGUAGE.KOTLIN) return
            if (project.files.isEmpty() || project.files.size != 1) {
                log.debug("Cant create project")
                return
            }
            if (project.files.first().psiFile.getAllPSIChildrenOfType<KtNamedFunction> { it.isTopLevel }.size < 2) {
                if (project.files.first().psiFile.getAllPSIChildrenOfTwoTypes<KtClassOrObject, KtEnumEntry>().isEmpty()) {
                    log.debug("Uninteresting test")
                    return
                }
            }
            val compilersConf = BBFProperties.getStringGroupWithoutQuotes("BACKENDS")
            val filterBackends = compilersConf.map { it.key }
            if (filterBackends.any { project.isBackendIgnores(it) }) {
                //TODO disable?
                log.debug("Ignore some of backends")
            }
            if (compilers.any { !it.checkCompiling(project) }) {
                log.debug("Can not compile $dir")
                return
            }

/*
            for (f in File("tmp/arrays").listFiles().filter { it.absolutePath.endsWith(".kt") }) {
                val project = Project.createFromCode(f.readText())
                if (project.language != LANGUAGE.KOTLIN) continue
                val checker = MutationChecker(listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir")), project)
                //Проверка компиляции
                val check = checker.checkCompiling()
                println(f)
                println("checkCompiling done = $check")
                //Запуск программы
                checker.checkTraces(project)
                println("checkTraces done")
            }
            exitProcess(0)
*/

            log.debug("Start to mutate")
            log.debug("BEFORE = ${project.files.first().text}")
            //ProjectPreprocessor.preprocess(project, null)
            val checker = MutationChecker(listOf(JVMCompiler(""), JVMCompiler("-Xuse-ir")), project, project.files.first())
            if (!checker.checkCompiling()) {
                log.debug("=(")
                exitProcess(0)
            }
            mutate(project, project.files.first(), listOf(/*::noBoxFunModifying*/)) //MUTATOR
//            //Save mutated file
//            if (CompilerArgs.shouldSaveMutatedFiles) {
//                val pathToNewTests = CompilerArgs.dirForNewTests
//                File(pathToNewTests).mkdirs()
//                val pathToSave = "$pathToNewTests/${Random().getRandomVariableName(10)}.kt"
//                File(pathToSave).writeText(resultingMutant.text)
//            }
            log.debug("Mutated = $project")
            if (!CompilerArgs.isMiscompilationMode) {
                Tracer(compilers.first(), project).trace()
                log.debug("Traced = $project")
                TracesChecker(compilers).checkBehavior(project)
            }
            return
        } catch (e: Error) {
            log.debug("ERROR: ${e.localizedMessage}\n${e.stackTrace.map { it.toString() + "\n" }}")
            return
            //System.exit(0)
        }
    }

    var counter = 0
}