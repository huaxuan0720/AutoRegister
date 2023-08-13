package com.billy.android.register

import com.android.builder.model.AndroidProject.FD_INTERMEDIATES
import com.google.gson.Gson
import org.gradle.api.Project
import java.io.File
import java.io.FileNotFoundException
import java.lang.reflect.Type


object AutoRegisterHelper {
    private const val CACHE_INFO_DIR: String = "auto-register"

    /**
     * 缓存自动注册配置的文件
     * @param project
     * @return file
     */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getRegisterInfoCacheFile(project: Project) : File {
        val baseDir = getCacheFileDir(project)
        if (mkdirs(baseDir)) {
            return File(baseDir + "register-info.config")
        } else {
            throw FileNotFoundException("Not found  path:$baseDir")
        }
    }

    /**
     * 缓存扫描到结果的文件
     * @param project
     * @return File
     */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun getRegisterCacheFile(project: Project) : File{
        val baseDir: String = getCacheFileDir(project)
        if (mkdirs(baseDir)) {
            return File(baseDir + "register-cache.json")
        } else {
            throw FileNotFoundException("Not found  path:$baseDir")
        }
    }

    /**
     * 将扫描到的结果缓存起来
     * @param cacheFile
     * @param harvests
     */
    @JvmStatic
    fun cacheRegisterHarvest(cacheFile: File?, harvests: String?) {
        cacheFile?: return
        harvests?: return
        cacheFile.parentFile.mkdirs()
        if (!cacheFile.exists()) {
            cacheFile.createNewFile()
        }
        cacheFile.writeText(harvests)
    }

    private fun getCacheFileDir(project: Project): String {
        return project.buildDir.absolutePath + File.separator + FD_INTERMEDIATES + File.separator + CACHE_INFO_DIR + File.separator
    }

    /**
     * 读取文件内容并创建Map
     * @param file 缓存文件
     * @param type map的类型
     * @return
     */
    @JvmStatic
    fun readToMap(file: File?, type: Type?): Map<Any, Any?> {
        var map: Map<Any, Any?>? = null
        if (file?.exists() == true) {
            if (type != null) {
                val text = file.readText()
                try {
                    map = Gson().fromJson(text, type)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (map == null) {
            map = HashMap()
        }
        return map
    }

    /**
     * 创建文件夹
     * @param dirPath
     * @return boolean, 是否成功
     */
    private fun mkdirs(dirPath: String): Boolean {
        val baseDirFile = File(dirPath)
        var isSuccess : Boolean = true
        if (!baseDirFile.isDirectory) {
            isSuccess = baseDirFile.mkdirs()
        }
        return isSuccess
    }
}
