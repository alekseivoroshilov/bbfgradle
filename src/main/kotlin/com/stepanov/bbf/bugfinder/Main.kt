package com.stepanov.bbf.bugfinder

import com.stepanov.bbf.bugfinder.executor.CompilerArgs


import com.stepanov.bbf.bugfinder.executor.compilers.JVMCompiler



import com.stepanov.bbf.bugfinder.executor.project.Project
import com.stepanov.bbf.bugfinder.util.FalsePositivesDeleter
import com.stepanov.bbf.bugfinder.util.NodeCollector
import net.sourceforge.argparse4j.ArgumentParsers
import net.sourceforge.argparse4j.impl.Arguments
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PropertyConfigurator
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    //Init log4j
    PropertyConfigurator.configure("src/main/resources/bbfLog4j.properties")
    if (!CompilerArgs.getPropAsBoolean("LOG")) {
        Logger.getRootLogger().level = Level.OFF
        Logger.getLogger("bugFinderLogger").level = Level.OFF
        Logger.getLogger("mutatorLogger").level = Level.OFF
        Logger.getLogger("reducerLogger").level = Level.OFF
        Logger.getLogger("transformationManagerLog").level = Level.OFF
    }

    val parser = ArgumentParsers.newFor("bbf").build()
    parser.addArgument("-r", "--reduce")
        .required(false)
        .help("Reduce mode")
    parser.addArgument("-f", "--fuzz")
        .required(false)
        .help("Fuzzing mode")
    parser.addArgument("-c", "--clean")
        .required(false)
        .action(Arguments.storeTrue())
        .help("Clean directories with bugs from bugs that are not reproduced")
    parser.addArgument("-d", "--database")
        .required(false)
        .action(Arguments.storeTrue())
        .help("Database updating")
    val arguments = parser.parseArgs(args)
    arguments.getString("reduce")?.let {
        //TODO
        exitProcess(0)
    }
    arguments.getString("fuzz")?.let {
        require(File(it).isDirectory) { "Specify directory to take files for mutation" }
        val file = File(it).listFiles()?.random() ?: throw IllegalArgumentException("Wrong directory")
        SingleFileBugFinder(file.absolutePath).findBugsInFile()
        exitProcess(0)
    }
    if (arguments.getString("database") == "true") {
        NodeCollector(CompilerArgs.baseDir).collect()
        exitProcess(0)
    }
    if (arguments.getString("clean") == "true") {
        FalsePositivesDeleter().cleanDirs()
        exitProcess(0)
    }
    val file = File(CompilerArgs.baseDir).listFiles()?.random() ?: exitProcess(0)



    //Full report analysis:

    /*val files = File("tmp/arrays").listFiles()!!.filter {
        it.absolutePath.endsWith(".kt") || it.absolutePath.endsWith(".java")
    }.map { it.absolutePath }
    val reportFile = File("CheckPerformanceFullReport.txt")
    if(reportFile.exists()) reportFile.delete()

    val count = files.size / 99 //0..99 , 100..199, 200..299..........
    println("Number of files: ${files.size} Count: $count")
    for (i in 0 until count) {
        val filesList = try{
            files.subList(i * 100, (i + 1) * 100 - 1) //0..99 , 100..199, 200..299..........
        }
        catch (e : IndexOutOfBoundsException) {
            files.subList(i * 100, files.size - 1)
        }
        if (filesList.isEmpty()) break
        SingleFileBugFinder("tmp/arrays/").testPerformanceBugCheck(filesList)
    }*/

    //Full report analysis block over


    //SingleFileBugFinder(file.absolutePath).findPerformanceBugs() //begin work

    SingleFileBugFinder(file.absolutePath).findPerformance()

    //SingleFileBugFinder(file.absolutePath).findBugsInFile() //begin work
    exitProcess(0)
}