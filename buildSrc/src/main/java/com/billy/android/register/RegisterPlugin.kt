package com.billy.android.register

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project


class RegisterPlugin: Plugin<Project> {

    companion object {
        const val EXT_NAME = "autoregister"
    }

    override fun apply(project: Project) {
        /**
         * 注册transform接口
         */
        val isApp: Boolean = project.plugins.hasPlugin(AppPlugin::class.java)
        project.extensions.create(
            EXT_NAME,
            AutoRegisterConfig::class.java
        )

        if (isApp) {
            println("project(" + project.name + ") apply auto-register plugin")
            val android = project.extensions.getByType(AppExtension::class.java)
            val transformImpl = RegisterTransform(project)
            android.registerTransform(transformImpl)
            project.afterEvaluate {
                init(project, transformImpl)
            }
        }
    }

    private fun init(project: Project, transform: RegisterTransform) {
        val config = project.extensions.findByName(EXT_NAME) as AutoRegisterConfig
        config.project = project
        config.convertConfig()
        transform.config = config
    }

}