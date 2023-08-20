package com.billy.android.register

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

abstract class RegisterTransformTask: DefaultTask() {

    @get:Input
    abstract val allDirectories: ListProperty<Directory>

    @get:Input
    abstract val allJars: ListProperty<RegularFile>

    @get:OutputFiles
    abstract val output: RegularFileProperty

    @TaskAction
    fun transform() {
    }

}