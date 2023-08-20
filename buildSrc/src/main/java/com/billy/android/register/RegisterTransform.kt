package com.billy.android.register

import com.android.build.api.transform.Context
import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Status
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.io.FileType
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import java.io.File


class RegisterTransform(val project: Project) : Transform() {

    lateinit var config: AutoRegisterConfig
    override fun getName(): String {
        return "auto-register"
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 是否支持增量编译
     * @return
     */
    override fun isIncremental(): Boolean {
        return true
    }

    override fun transform(
        context: Context?,
        inputs: MutableCollection<TransformInput>?,
        referencedInputs: MutableCollection<TransformInput>?,
        outputProvider: TransformOutputProvider?,
        isIncremental: Boolean
    ) {
        project.logger.warn("start auto-register transform...")
        config.reset()
        project.logger.warn(config.toString())

        val clearCache = !isIncremental
        if (clearCache) {
            outputProvider?.deleteAll()
        }

        val time = System.currentTimeMillis()
        val leftSlash = File.separator === "/"

        val cacheEnabled = config.cacheEnabled
        println("auto-register-----------isIncremental:${isIncremental}--------config.cacheEnabled:${cacheEnabled}--------------------\n")

        val cacheMap: MutableMap<String, ScanJarHarvest> = mutableMapOf()
        var cacheFile: File? = null
        var gson: Gson? = null

        if (cacheEnabled) { //开启了缓存
            gson = Gson()
            cacheFile = AutoRegisterHelper.getRegisterCacheFile(project)
            if (clearCache && cacheFile.exists()) cacheFile.delete()
            (AutoRegisterHelper.readToMap(
                cacheFile,
                object : TypeToken<HashMap<String, ScanJarHarvest>?>() {}.type
            ) as MutableMap<String, ScanJarHarvest>?)?.let {
                cacheMap.putAll(it)
            }
        }

        val scanProcessor = CodeScanProcessor(config.list, cacheMap)
        // 遍历输入文件
        inputs?.forEach { input: TransformInput ->
            // 遍历jar
            input.jarInputs.forEach { jarInput: JarInput ->
                if (jarInput.status != Status.NOTCHANGED) {
                    cacheMap.remove(jarInput.file.absolutePath)
                }
                if (outputProvider != null) {
                    scanJar(jarInput, outputProvider, scanProcessor)
                }
            }

            input.directoryInputs.forEach { directoryInput: DirectoryInput ->
                val dirTime = System.currentTimeMillis()
                // 获得产物的目录
                val dest = outputProvider!!.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes,
                    directoryInput.scopes,
                    Format.DIRECTORY
                )
                var root = directoryInput.file.absolutePath
                if (!root.endsWith(File.separator)) {
                    root += File.separator
                }

                //遍历目录下的每个文件
                eachFileRecurse(directoryInput.file, FileType.ANY) { file: File ->
                    val path: String = file.absolutePath.replace(root, "")
                    if (file.isFile) {
                        var entryName = path
                        if (!leftSlash) {
                            entryName = entryName.replace("\\\\", "/")
                        }
                        scanProcessor.checkInitClass(
                            entryName,
                            File(dest.absolutePath + File.separator + path)
                        )
                        if (scanProcessor.shouldProcessClass(entryName)) {
                            scanProcessor.scanClass(file)
                        }
                    }
                }
                val scanTime = System.currentTimeMillis()
                // 处理完后拷到目标文件
                FileUtils.copyDirectory(directoryInput.file, dest)
                println("auto-register cost time: ${System.currentTimeMillis() - dirTime}, scan time: ${scanTime - dirTime}. path=${root}")
            }

        }
        if (cacheFile != null && gson != null) {
            val json = gson.toJson(cacheMap)
            AutoRegisterHelper.cacheRegisterHarvest(cacheFile, json)
        }

        val scanFinishTime = System.currentTimeMillis()
        project.logger.error("register scan all class cost time: " + (scanFinishTime - time) + " ms")

        config.list.forEach { ext ->
            if (ext.fileContainsInitClass != null) {
                println()
                println("insert register code to file:" + ext.fileContainsInitClass?.absolutePath);
                if (ext.classList.isEmpty()) {
                    project.logger.error("No class implements found for interface:" + ext.interfaceName)
                } else {
                    ext.classList.forEach {
                        println(it)
                    }
                    CodeInsertProcessor.insertInitCodeTo(ext)
                }
            } else {
                project.logger.error("The specified register class not found:" + ext.registerClassName)
            }
        }

        val finishTime = System.currentTimeMillis()
        project.logger.error("register insert code cost time: " + (finishTime - scanFinishTime) + " ms")
        project.logger.error("register cost time: " + (finishTime - time) + " ms")
    }

    fun eachFileRecurse(
        self: File,
        fileType: FileType,
        closure: (File) -> Unit
    ) {
        if (!self.exists() || !self.isDirectory) {
            return
        }
        val files = self.listFiles()
        if (files != null) {
            val var5 = files.size
            for (var6 in 0 until var5) {
                val file = files[var6]
                if (file.isDirectory) {
                    if (fileType != FileType.FILES) {
                        closure(file)
                    }
                    eachFileRecurse(file, fileType, closure)
                } else if (fileType != FileType.DIRECTORIES) {
                    closure(file)
                }
            }
        }
    }

    fun scanJar(
        jarInput: JarInput,
        outputProvider: TransformOutputProvider,
        scanProcessor: CodeScanProcessor
    ) {
        // 获得输入文件
        val src = jarInput.file
        //遍历jar的字节码类文件，找到需要自动注册的类
        val dest = getDestFile(jarInput, outputProvider)
        val time = System.currentTimeMillis()
        if (!scanProcessor.scanJar(src, dest) //直接读取了缓存，没有执行实际的扫描
            //此jar文件中不需要被注入代码
            //为了避免增量编译时代码注入重复，被注入代码的jar包每次都重新复制
            && !scanProcessor.isCachedJarContainsInitClass(src.absolutePath)
        ) {
            //不需要执行文件复制，直接返回
            return
        }
        println("auto-register cost time: " + (System.currentTimeMillis() - time) + " ms to scan jar file:" + dest.absolutePath)
        //复制jar文件到transform目录：build/transforms/auto-register/
        FileUtils.copyFile(src, dest)
    }

    fun getDestFile(jarInput: JarInput, outputProvider: TransformOutputProvider): File {
        var destName: String = jarInput.name
        // 重名名输出文件,因为可能同名,会覆盖
        val hexName: String = DigestUtils.md5Hex(jarInput.file.absolutePath)
        if (destName.endsWith(".jar")) {
            destName = destName.substring(0, destName.length - 4)
        }
        // 获得输出文件
        return outputProvider.getContentLocation(
            destName + "_" + hexName,
            jarInput.contentTypes,
            jarInput.scopes,
            Format.JAR
        )
    }
}