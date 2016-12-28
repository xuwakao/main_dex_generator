package com.wakao.maindexkeep.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

public class GenerateMainDexListTask extends DefaultTask {
    GenerateMainDexListTask() {

    }

    @TaskAction
    def generate() {
        println('3**********************************3')
        println('begin execute generate main dex list')
        println('***********************************')
    }
}