package com.stepanov.bbf.bugfinder.executor.checkers;

import com.stepanov.bbf.bugfinder.Reducer
import com.stepanov.bbf.bugfinder.executor.Checker
import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.CompilingResult
import com.stepanov.bbf.bugfinder.executor.project.LANGUAGE
import com.stepanov.bbf.bugfinder.manager.Bug
import com.stepanov.bbf.bugfinder.manager.BugManager
import com.stepanov.bbf.bugfinder.manager.BugType
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis


open class PerformanceChecker(compilers: List<CommonCompiler>) : Checker(compilers) {
    var pcrList = listOf<PerformanceCheckerResult>() // "as an object of result" See PerformanceCheckerResult class

    constructor(path: String, _compilers: List<CommonCompiler>) : this(_compilers) {
        val pcrList = mutableListOf<PerformanceCheckerResult>()
        if (!File(path).exists()) throw FileNotFoundException()
        val files = when {
            path.endsWith(".kt") -> listOf(File(path))
            path.endsWith(".java") -> listOf(File(path))
            else -> File(path).listFiles()!!.filter { it.absolutePath.endsWith(".kt") }
        }
        for (file in files.take(100)) {
            val project = Project.createFromCode(file.readText())
            //projects.add(project)
            for (compiler in compilers) {
                val performanceCheckerResult = PerformanceCheckerResult(project, compiler).apply {
                    name = file.name
                }
                pcrList.add(performanceCheckerResult)
            }
            //println(file.name)
        }
        this.pcrList = pcrList
        //this.projects = projects
    }
    constructor(paths : List<String>, _compilers: List<CommonCompiler>): this(_compilers) {
        val pcrList = mutableListOf<PerformanceCheckerResult>()
        val files = mutableListOf<File>()
        for (path in paths){
            if (!File(path).exists()) throw FileNotFoundException()
            when {
                path.endsWith(".kt") -> files.add(File(path))
                path.endsWith(".java") -> files.add(File(path))
            }
        }

        for (file in files) {
            val project = Project.createFromCode(file.readText())
            for (compiler in compilers) {
                val performanceCheckerResult = PerformanceCheckerResult(project, compiler).apply {
                    name = file.name
                }
                pcrList.add(performanceCheckerResult)
            }
        }
        this.pcrList = pcrList
    }
    //TODO
    constructor(project: Project, compiler: CommonCompiler) : this(listOf(compiler)) {
        val pcr = PerformanceCheckerResult(project, compiler)
        this.pcrList = listOf(pcr)
    }
    constructor(project: Project, compilers: List<CommonCompiler>) : this(compilers) {
        val pcr = mutableListOf<PerformanceCheckerResult>()
        for (compiler in compilers){
            pcr.add(PerformanceCheckerResult(project, compiler))
        }
        this.pcrList = pcr
    }
    fun checkPerformance(
        includeExecutionTime: Boolean = false,
        enableBugReport: Boolean = false,
        printReport: Boolean = false,
        saveReport: Boolean = false
    ): List<PerformanceCheckerResult> {
        val listOfDifferences = mutableMapOf<String, List<Float>>() // for findBiggestDifference()

        for (pcr in pcrList) {
            val compiler = pcr.compiler
            val project = pcr.project
            var executableFile : CompilingResult
            val compilationTime = measureTimeMillis {
                executableFile = compiler.compile(project, false) //
            }
            pcr.compilationTime = compilationTime
            if (includeExecutionTime) {
                try {
                    val executionTime = measureTimeMillis {
                        compiler.exec(executableFile.pathToCompiled)
                    }
                    pcr.executionTime = executionTime
                } catch (e: Exception) {
                    println("Can't execute <${pcr.name}> ($compiler, ${project.configuration})")
                    pcr.executionTime = 0
                }
            }
        }


        val compilationTimeDifferences = mutableListOf<Float>()
        val executionTimeDifferences = mutableListOf<Float>()
        val resultBuf = mutableListOf<PerformanceCheckerResult>()
        var name = pcrList.first().name

        for (pcr in pcrList) {
            if (name != pcr.name || pcrList.last() == pcr) {
                if (pcrList.last() == pcr) resultBuf.add(pcr)
                val commonLocalCompilation = findBiggestDifference(resultBuf, executionTime = includeExecutionTime)
                listOfDifferences[name] = commonLocalCompilation

                if (enableBugReport)
                    checkPerformanceBug(
                            commonLocalCompilation,
                            resultBuf,
                            resultBuf.minOf { it.compilationTime }
                    )

                //println("$name compilation time difference: ${commonLocalCompilation[0]} %")
                compilationTimeDifferences.add(commonLocalCompilation[0])

                //println("$name execution time difference: ${commonLocalCompilation[1]} %")
                executionTimeDifferences.add(commonLocalCompilation[1])
                resultBuf.clear()
            }
            name = pcr.name
            resultBuf.add(pcr) //buffer holds <PerformanceCheckResult> value for findBiggestDifference()
        }

        println("Average compilation difference: " + "${compilationTimeDifferences.average()}" + " %")
        println("Average execution difference: " + "${executionTimeDifferences.average()}" + " %")

        if (listOfDifferences.isNotEmpty() && printReport)
            printReport(
                    pcrList,
                    listOfDifferences,
                    executionTime = includeExecutionTime,
                    saveReport = saveReport
            )
        return pcrList
    }

    fun findBiggestDifference(
        list: List<PerformanceCheckerResult>,
        executionTime: Boolean = false
    ): List<Float> {
        val bufList = mutableListOf<PerformanceCheckerResult>()
        bufList.addAll(list)
        bufList.sortByDescending { it.compilationTime }
        val b = bufList.first().compilationTime
        val a = bufList.last().compilationTime
        if (b == 0L || a == 0L) return listOf(0.0F, 0.0F)
        //println("$b max CompilationTime    ")
        //println("$a min CompilationTime")

        val compTime = (100 * (b - a) / a).toFloat()

        if (executionTime) {
            bufList.sortByDescending { it.executionTime }
            val d = bufList.first().executionTime
            val c = bufList.last().executionTime
            //println("$d max ExecutionTime    ")
            //println("$c min ExecutionTime")
            if (d.toInt() == 0 || c.toInt() == 0) {
                return listOf(compTime, 0.0F)
            }
            return listOf(compTime, (100 * (d - c) / c).toFloat()) //mode == true -> return (compTime, ExecTime)
        }

        return listOf(compTime, 0.0F) //mode == false
    }

    private fun checkPerformanceBug( //TODO
        differences : List<Float>,
        pcrList: List<PerformanceCheckerResult>,
        minCompilationTime : Long
    ) : Boolean {
        if ((differences[0] > 500 || differences[1] > 500) && minCompilationTime > 5000L) {
            val pcr = pcrList.maxByOrNull { it.compilationTime }
            if (pcr != null) {
                val reducedProject = Reducer.reduce(
                    Bug(compilers, "", pcr.project, BugType.PERFORMANCE))
                println("FOUND A BUG:")
                println(reducedProject.files.first().name)
                val bug = Bug(
                    compilers,
                    "Compilation time: ${pcr.compilationTime}(${differences[0]})\n " +
                            "Execution time: ${pcr.executionTime}(${differences[1]})\n " +
                            "Compiler info: ${pcr.compiler}",
                    reducedProject, BugType.PERFORMANCE
                )
                if(!BugManager.haveDuplicates(bug))
                    BugManager.saveBug(bug)
                return true
            }// bugmanager -> savebug
        }
        return false
    }

    private fun printReport(
        pcrList: List<PerformanceCheckerResult>,
        pcrCompilationDifference: Map<String, List<Float>>,
        executionTime: Boolean = false,
        saveReport: Boolean = false

    ) {
        if (pcrCompilationDifference.isEmpty()) return

        val reportFile = File("CheckPerformanceReport.txt")

        var reportFileExist = true
        println("report file exists ${reportFile.exists()}")
        if (!reportFile.exists()) {
            reportFile.createNewFile()
            reportFileExist = false
        }

        val sortedMap: MutableMap<String, List<Float>> = LinkedHashMap()
        pcrCompilationDifference.entries.sortedByDescending { it.value[0] }.forEach { sortedMap[it.key] = it.value }

        println( //TODO
                "\n SORT BY COMPILATION PERFORMANCE:\n" +
                "<Project> \tCompilation Difference \tExecution Difference \n" +
                "[Details]:\n" +
                "[Compiler]: Compilation (Execution) ms\n" +
                "\n ---------------------------------------------------------"
        )
        if (saveReport && !reportFileExist) {
            reportFile.appendText(
                    "<Project> \tCompilation Difference \tExecution Difference \n" +
                    "[Details]:\n" +
                    "[Compiler]: Compilation (Execution) ms" +
                    "\n ---------------------------------------------------------\n\n\n\n")
        }
        if (saveReport) {
            reportFile.appendText("\n\n\n\n Another report list" +
                    "\nSORT BY COMPILATION PERFORMANCE:" +
                    "\n ---------------------------------------------------------")
        }
        var i = 0
        for (entry in sortedMap.entries) {
            if (pcrCompilationDifference.entries.iterator().hasNext()) {
                val key = entry.key
                val pcrDifference = entry.value
                val selectedPcr = pcrList.filter { it.name == key }
                val interestingTest = if (pcrDifference[0] > 300) "(bug)" else ""
                val string = "${i + 1}. <$key>$interestingTest : \t Compilation: ${pcrDifference[0]}%" +
                        "\t Execution: ${pcrDifference[1]}%" + "\n[Details]:"
                println(string)
                if (saveReport) reportFile.appendText("\n$string")
                for (pcr in selectedPcr){
                    val string2 = "\t\t${pcr.compiler}: ${pcr.compilationTime} (${pcr.executionTime}) ms"
                    println(string2)
                    if (saveReport) reportFile.appendText("\n$string2")
                }

                if (saveReport) reportFile.appendText("\n------------------\n")
                println("------------------\n")
                i++
                //if (i >=  5) break
            }

        }
        val sortedMapExecution: MutableMap<String, List<Float>> = LinkedHashMap()
        pcrCompilationDifference.entries.sortedByDescending { it.value[1] }
            .forEach { sortedMapExecution[it.key] = it.value }
        //entry = sortedMap.entries.iterator().next()

        if (executionTime) {
            println(
                "\nSORT BY EXECUTION PERFORMANCE:" +
                "\n ---------------------------------------------------------"
            )
            if (saveReport) {
                reportFile.appendText("\nSORT BY EXECUTION PERFORMANCE:" +
                        "\n ---------------------------------------------------------")
            }
            i = 0
            for (entry in sortedMapExecution.entries) {
                if (pcrCompilationDifference.entries.iterator().hasNext()) {
                    val key = entry.key
                    val pcrDifference = entry.value
                    val selectedPcr = pcrList.filter { it.name == key }
                    val interestingTest = if (pcrDifference[1] > 300) "(bug)" else ""
                    val string = "${i + 1}. <$key>$interestingTest : \tExecution: ${pcrDifference[1]}%" +
                            "\tCompilation: ${pcrDifference[0]}%\n" +
                            "[Details]:"
                    println(string)
                    if (saveReport) reportFile.appendText("\n$string")
                    for (pcr in selectedPcr) {
                        val string2 = "\t\t${pcr.compiler}: ${pcr.compilationTime} (${pcr.executionTime}) ms"
                        println(string2)
                        if (saveReport) reportFile.appendText("\n$string2")
                    }
                    if (saveReport) reportFile.appendText("\n------------------")
                    println("------------------")
                }
                i++
                //if (i >=  5) break
            }
        }
        val notCompiledList = pcrList.filter { it.compilationTime.toFloat() == 0.0F }
        if (notCompiledList.isNotEmpty()) {
            if (saveReport) reportFile.appendText("\nPROJECTS FAILED TO COMPILE")
            println("PROJECTS FAILED TO COMPILE")
            notCompiledList.map { (k, _) -> {
                println(k)
                if (saveReport) reportFile.appendText("\n$k")
            } }

        }
    }
}

