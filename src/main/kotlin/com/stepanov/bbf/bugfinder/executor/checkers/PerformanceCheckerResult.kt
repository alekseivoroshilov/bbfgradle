package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project

data class PerformanceCheckerResult(val project: Project, val compiler: CommonCompiler) {
    var compilationTime = 0L
    var executionTime = 0L
    var name = ""
/*
    val pcrComparator =  Comparator<PerformanceCheckerResult> { a, b ->
        when {
            (a == null && b == null) -> 0
            (a == null) -> -1
            (b == null) -> 1
            (a.compilationTime > b.compilationTime && a.executionTime > b.executionTime) -> 1
            (a.compilationTime < b.compilationTime && a.executionTime < b.executionTime) -> -1
            else -> 0
        }
    }
*/
}
