package com.billy.android.register

import org.gradle.api.Project
import java.io.File
import java.util.Objects

/**
 * Created by Hua Xuan on 2023/08/08.
 * Converted this file from Groovy to Kotlin.
 */
open class AutoRegisterConfig {

    var registerInfo = arrayListOf<MutableMap<String, Any>>()

    var list: ArrayList<RegisterInfo> = arrayListOf()

    lateinit var project: Project

    var cacheEnabled: Boolean = true

    fun convertConfig() {
        registerInfo.forEach { map ->
            val info = RegisterInfo()

            // 读取需要扫描的接口和父类，可以使用数据指定多个需要扫描父类
            info.setInterfaceName(map.get("scanInterface") as String)

            val superClassList = arrayListOf<String>()
            val superClasses : Any? = map.get("scanSuperClasses")
            if (superClasses == null) {
                superClassList.clear()
            } else if (superClasses is String) {
                superClassList.add(superClasses)
            } else if (superClasses is ArrayList<*>) {
                superClassList.addAll(superClasses as ArrayList<String>)
            }

            info.setSuperClassNames(superClassNames = superClassList as ArrayList<String?>)
            // 代码注入的类
            info.setInitClassName(initClassName = map.get("codeInsertToClassName") as String?)
            // 代码注入的方法（默认为static块）,static代码块可以在程序启动的时候就初始化并完成代码注入。
            info.setInitMethodName(initMethodName = map.get("codeInsertToMethodName") as String?)
            // 生成的代码所调用的方法，一般推荐使用静态方法
            info.setRegisterMethodName(registerMethodName = map.get("registerMethodName") as String?)
            // 注册方法所在的类
            info.setRegisterClassName(registerClassName = map.get("registerClassName") as String?)
            info.setInclude(include = map.get("include") as ArrayList<String?>?)
            info.setExclude(exclude = map.get("exclude") as ArrayList<String?>?)
            info.init()
            if (info.validate())
                list.add(info)
            else {
                project.logger.error("auto register config error: scanInterface, codeInsertToClassName and registerMethodName should not be null\n$info")
            }

        }

        if (cacheEnabled) {
            checkRegisterInfo()
        } else {
            deleteFile(AutoRegisterHelper.getRegisterInfoCacheFile(project))
            deleteFile(AutoRegisterHelper.getRegisterCacheFile(project))
        }
    }

    /**
     * 检查配置信息是否有改动，如果有改动，就删除缓存文件，并把新的配置信息写入缓存文件。
     * 同时删除已经扫描到的jar包的缓存信息文件。
     */
    private fun checkRegisterInfo() {
        val registerInfo = AutoRegisterHelper.getRegisterInfoCacheFile(project)
        val listInfo = list.toString()
        var sameInfo = false

        if (!registerInfo.exists()) {
            registerInfo.createNewFile()
        } else if (registerInfo.canRead()) {
            val info = registerInfo.readText()
            sameInfo = info == listInfo
            if (!sameInfo) {
                project.logger.error("auto-register registerInfo has been changed since project(':$project.name') last build")
            }
        } else {
            project.logger.error("auto-register read registerInfo error--------")
        }
        if (!sameInfo) {
            deleteFile(AutoRegisterHelper.getRegisterCacheFile(project))
        }
        if (registerInfo.canWrite()) {
            registerInfo.writeText(listInfo)
        } else {
            project.logger.error("auto-register write registerInfo error--------")
        }
    }


    private fun deleteFile(file: File) {
        if (file.exists()) {
            //registerInfo 配置有改动就删除緩存文件
            file.delete()
        }
    }

    fun reset() {
        list.forEach { info ->
            info.reset()
        }
    }

    override fun toString(): String {
        val sb = StringBuilder(Constants.EXT_NAME).append(" = {")
            .append("\n  cacheEnabled = ").append(cacheEnabled)
            .append("\n  registerInfo = [\n")

        val size = list.size
        list.forEachIndexed { index, registerInfo ->
            sb.append('\t' + registerInfo.toString().replace("\n", "\n\t"))
            if (index < size - 1)
                sb.append(",\n")
        }
        sb.append("\n  ]\n}")
        return sb.toString()
    }

}