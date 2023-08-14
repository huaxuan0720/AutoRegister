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
    private var interfaceName : String? = null
    private var superClassNames: ArrayList<String?>? = null
    private var initClassName : String? = null
    private var initMethodName : String? = null
    private var registerClassName: String? = null
    private var registerMethodName : String? = null
    private var include: ArrayList<String?>? = null
    private var exclude: ArrayList<String?>? = null

    // 以下不是可配置参数
    private var includePatterns :ArrayList<Pattern> = arrayListOf()
    private var excludePatterns : ArrayList<Pattern> = arrayListOf()
    // initClassName的class文件或含有initClassName类的jar文件
    private var fileContainsInitClass: File? = null
    private var classList : ArrayList<String> = arrayListOf()

    fun setInterfaceName(interfaceName: String?) {
        this.interfaceName = interfaceName
    }

    fun getInterfaceName(): String? {
        return interfaceName
    }

    fun setSuperClassNames(superClassNames: ArrayList<String?>?) {
        this.superClassNames = superClassNames
    }

    fun getSuperClassNames(): ArrayList<String?>? {
        return superClassNames
    }

    fun setInitClassName(initClassName: String?) {
        this.initClassName = initClassName
    }

    fun getInitClassName(): String? {
        return initClassName
    }

    fun setInitMethodName(initMethodName: String?) {
        this.initMethodName = initMethodName
    }

    fun getInitMethodName(): String? {
        return this.initMethodName
    }

    fun setRegisterClassName(registerClassName: String?) {
        this.registerClassName = registerClassName
    }

    fun getRegisterClassName(): String? {
        return registerClassName
    }

    fun setRegisterMethodName(registerMethodName: String?) {
        this.registerMethodName = registerMethodName
    }

    fun getRegisterMethodName(): String? {
        return registerMethodName
    }

    fun setInclude(include: ArrayList<String?>?) {
        this.include = include
    }

    fun getInclude(): ArrayList<String?>? {
        return include
    }

    fun setExclude(exclude: ArrayList<String?>?) {
        this.exclude = exclude
    }

    fun getExclude(): ArrayList<String?>? {
        return exclude
    }

    fun setIncludePatterns(includePatterns: ArrayList<Pattern>) {
        this.includePatterns = includePatterns
    }

    fun getIncludePatterns(): ArrayList<Pattern> {
        return includePatterns
    }

    fun setExcludePatterns(excludePatterns: ArrayList<Pattern>) {
        this.excludePatterns = excludePatterns
    }

    fun getExcludePatterns(): ArrayList<Pattern> {
        return excludePatterns
    }

    fun setFileContainsInitClass(file: File?) {
        this.fileContainsInitClass = file
    }

    fun getFileContainsInitClass(): File? {
        return fileContainsInitClass
    }

    fun setClassList(classList: ArrayList<String>) {
        this.classList = classList
    }

    fun getClassList(): ArrayList<String> {
        return classList
    }


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
