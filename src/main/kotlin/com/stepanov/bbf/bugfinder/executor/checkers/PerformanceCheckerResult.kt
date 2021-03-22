package com.stepanov.bbf.bugfinder.executor.checkers

import com.stepanov.bbf.bugfinder.executor.CommonCompiler
import com.stepanov.bbf.bugfinder.executor.project.Project

data class PerformanceCheckerResult(val project: Project, val compiler: CommonCompiler) {
    var compilationTime = 0L
    var executionTime = 0L
    var name = ""
}