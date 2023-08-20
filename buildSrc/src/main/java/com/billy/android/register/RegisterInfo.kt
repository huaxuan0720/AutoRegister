package com.billy.android.register

import android.databinding.tool.ext.S
import com.android.build.gradle.internal.cxx.caching.snakeCase
import java.io.File
import java.lang.StringBuilder
import java.util.regex.Pattern

class RegisterInfo {

    companion object {

        @JvmField
        val DEFAULT_EXCLUDE = arrayListOf(
            ".*/R(\$[^/]*)?", ".*/BuildConfig\$"
        )
    }

    // 以下是可配置参数
    var interfaceName : String? = null
    var superClassNames: ArrayList<String?>? = null
    var initClassName : String? = null
    var initMethodName : String? = null
    var registerClassName: String? = null
    var registerMethodName : String? = null
    var include: ArrayList<String?>? = null
    var exclude: ArrayList<String?>? = null

    // 以下不是可配置参数
    var includePatterns :ArrayList<Pattern> = arrayListOf()
    var excludePatterns : ArrayList<Pattern> = arrayListOf()
    // initClassName的class文件或含有initClassName类的jar文件
    var fileContainsInitClass: File? = null
    var classList : ArrayList<String> = arrayListOf()

    fun reset() {
        fileContainsInitClass = null
        classList.clear()
    }

    fun validate() : Boolean {
        return !interfaceName.isNullOrEmpty() && !registerClassName.isNullOrEmpty() && !registerMethodName.isNullOrEmpty()
    }

    override fun toString(): String {
        val sb : StringBuilder = StringBuilder("{")
        // format interfaceName
        sb.append("\n\t").append("scanInterface").append("\t\t\t=\t").append(interfaceName)
        // format superClassNames
        sb.append("\n\t").append("scanSuperClasses").append("\t\t=\t[")
        superClassNames?.forEachIndexed { index, s ->
            if (index > 0) {
                sb.append(",")
            }
            sb.append("\'").append(s).append("\'")
        }
        sb.append("]")
        //
        sb.append("\n\t").append("codeInsertToClassName").append("\t=\t").append(initClassName)
        sb.append("\n\t").append("codeInsertToMethodName").append("\t=\t").append(initMethodName)
        sb.append("\n\t").append("registerMethodName")
            .append("\t\t=\tpublic static void ")
            .append(registerClassName)
            .append(".")
            .append(registerMethodName)
        // format include
        sb.append("\n\t").append("include").append(" = [")
        include?.forEach {
            sb.append("\n\t\t\'").append(it).append("\'")
        }
        sb.append("\n\t]")
        // format exclude
        sb.append("\n\t").append("exclude").append(" = [")
        exclude?.forEach {
            sb.append("\n\t\t\'").append(it).append("\'")
        }
        sb.append("\n\t]")

        sb.append("\n}")
        return sb.toString()
    }

    fun init() {
        if (include == null) {
            include = arrayListOf()
        }
        // 如果没有设置则默认为include所有
        if (include!!.isEmpty()) {
            include!!.add(".*")
        }
        if (exclude == null) {
            exclude = arrayListOf()
        }

        if (registerClassName.isNullOrEmpty()) {
            registerClassName = initClassName
        }

        // 将interfaceName中的'.'转换为'/'
        if (!interfaceName.isNullOrEmpty()) {
            interfaceName = convertDotToSlash(interfaceName)
        }
        // 将superClassName中的'.'转换为'/'
        if (superClassNames == null) {
            superClassNames = arrayListOf()
        }
        superClassNames?.forEachIndexed { index, s ->
            val superClass = convertDotToSlash(s)
            superClassNames?.set(index, superClass)
        }
        // 注册和初始化的方法所在的类默认为同一个类
        initClassName = convertDotToSlash(initClassName)
        // 默认插入到static块中
        if (initMethodName.isNullOrEmpty()) {
            initMethodName = "<clinit>"
        }
        registerClassName = convertDotToSlash(registerClassName)
        // 添加默认的排除项
        DEFAULT_EXCLUDE.forEach { e ->
            exclude?.let {
                if (!it.contains(e)) {
                    it.add(e)
                }
            }
        }
        initPattern(include, includePatterns)
        initPattern(exclude, excludePatterns)
    }

    private fun convertDotToSlash(str: String?) : String? {
        if (str.isNullOrEmpty()) {
            return str
        }
        return str.replace(".", "/").intern()
    }

    private fun initPattern(list: ArrayList<String?>?, patterns: ArrayList<Pattern>) {
        list?.forEach {
            patterns.add(Pattern.compile(it))
        }
    }
}
