package com.billy.android.register

import com.billy.android.register.ScanJarHarvest.Harvest
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Pattern


class CodeScanProcessor(
    private val infoList: ArrayList<RegisterInfo>,
    private val cacheMap: MutableMap<String, ScanJarHarvest>
) {

    companion object {
        @JvmStatic
        private fun shouldProcessThisClassForRegister(info: RegisterInfo?, entryName: String?) : Boolean {
            if (info != null) {
                val list = info.includePatterns
                if (!list.isNullOrEmpty()) {
                    val exlist = info.excludePatterns
                    var pattern: Pattern
                    var p: Pattern
                    for (i in 0 until list.size) {
                        pattern = list[i]
                        if (pattern.matcher(entryName).matches()) {
                            if (!exlist.isNullOrEmpty()) {
                                for (j in 0 until exlist.size) {
                                    p = exlist[j]
                                    if (p.matcher(entryName).matches()) return false
                                }
                            }
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

    private val cachedJarContainsInitClass = mutableSetOf<String>()

    /**
     * 扫描jar包
     * @param jarFile 来源jar包文件
     * @param destFile transform后的目标jar包文件
     */
    fun scanJar(jarFile : File?, destFile: File): Boolean {
        //检查是否存在缓存，有就添加class list 和 设置fileContainsInitClass
        if (jarFile == null || hitCache(jarFile, destFile))
            return false

        val srcFilePath : String = jarFile.absolutePath
        val file : JarFile = JarFile(jarFile)
        val enumeration : Enumeration<JarEntry> = file.entries()

        while (enumeration.hasMoreElements()) {
            val jarEntry: JarEntry = enumeration.nextElement() as JarEntry
            val entryName : String  = jarEntry.name
            //support包不扫描
            if (entryName.startsWith("android/support"))
                break
            checkInitClass(entryName, destFile, srcFilePath)

            //是否要过滤这个类，这个可配置
            if (shouldProcessClass(entryName)) {
                val inputStream: InputStream = file.getInputStream(jarEntry)
                scanClass(inputStream, jarFile.absolutePath)
                inputStream.close()
            }
        }

        file.close()
        //加入缓存
        addToCacheMap(null, null, srcFilePath);
        return true;
    }

    /**
     * 检查此entryName是不是被注入注册代码的类，如果是则记录此文件（class文件或jar文件）用于后续的注册代码注入
     * @param entryName
     * @param destFile
     */
    fun checkInitClass(entryName: String?, destFile: File): Boolean {
        return checkInitClass(entryName, destFile, "")
    }

    fun checkInitClass(entryName: String?, destFile: File, srcFilePath: String): Boolean {
        var entryName = entryName
        if (entryName == null || !entryName.endsWith(".class")) {
            return false
        }
        entryName = entryName.substring(0, entryName.lastIndexOf("."))
        var found : Boolean = false
        infoList.forEach { ext ->
            if (ext.initClassName == entryName) {
                ext.fileContainsInitClass = destFile
                if (destFile.name.endsWith(".jar")) {
                    addToCacheMap(null, entryName, srcFilePath);
                    found = true
                }
            }
        }
        return found
    }

    // file in folder like these
    //com/billy/testplugin/Aop.class
    //com/billy/testplugin/BuildConfig.class
    //com/billy/testplugin/R$attr.class
    //com/billy/testplugin/R.class
    // entry in jar like these
    //android/support/v4/BuildConfig.class
    //com/lib/xiwei/common/util/UiTools.class
    fun shouldProcessClass(entryName: String?): Boolean {
//        println('classes:' + entryName)
        var entryName = entryName
        if (entryName == null || !entryName.endsWith(".class")) {
            return false
        }
        entryName = entryName.substring(0, entryName.lastIndexOf("."))
        val length = infoList.size
        for (i in 0 until length) {
            if (shouldProcessThisClassForRegister(infoList[i], entryName)) {
                return true
            }
        }
        return false
    }



    /**
     * 处理class的注入
     * @param file class文件
     * @return 修改后的字节码文件内容
     */
    fun scanClass(file: File): Boolean {
        val bufferedInputStream = BufferedInputStream(FileInputStream(file))
        return scanClass(bufferedInputStream, file.absolutePath)
    }

    //refer hack class when object init
    fun scanClass(inputStream: InputStream, filePath: String): Boolean {
        val cr = ClassReader(inputStream)
        val cw = ClassWriter(cr, 0)
        val cv: ScanClassVisitor = ScanClassVisitor(Opcodes.ASM6, cw, filePath)
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        inputStream.close()
        return cv.isFound()
    }

    /**
     * 扫描到的类添加到map
     * @param interfaceName
     * @param name
     * @param srcFilePath
     */
    private fun addToCacheMap(interfaceName: String?, name: String?, srcFilePath: String) {
        if (!srcFilePath.endsWith(".jar")) return
        var jarHarvest: ScanJarHarvest? = cacheMap[srcFilePath]
        if (jarHarvest == null) {
            jarHarvest = ScanJarHarvest()
            cacheMap.put(srcFilePath, jarHarvest)
        }
        if (name != null) {
            val classInfo = Harvest()
            classInfo.setIsInitClass(interfaceName == null)
            classInfo.setInterfaceName(interfaceName)
            classInfo.setClassName(name)
            jarHarvest!!.harvestList.add(classInfo)
        }
    }

    fun isCachedJarContainsInitClass(filePath: String?): Boolean {
        return cachedJarContainsInitClass.contains(filePath)
    }

    /**
     * 检查是否存在缓存，有就添加class list 和 设置fileContainsInitClass
     * @param jarFile
     * @param destFile
     * @return 是否存在缓存
     */
    fun hitCache(jarFile: File, destFile: File): Boolean {
        val jarFilePath = jarFile.absolutePath
        val scanJarHarvest: ScanJarHarvest? = cacheMap.get(jarFilePath)
        if (scanJarHarvest != null) {
            infoList.forEach { info ->
                scanJarHarvest.harvestList.forEach { harvest ->
                    //       println("----harvest-------"+harvest.className)
                    if (harvest.getIsInitClass()) {
                        if (info.initClassName === harvest.getClassName()) {
                            info.fileContainsInitClass = destFile
                            cachedJarContainsInitClass.add(jarFilePath);
                        }
                    } else if (info.interfaceName == harvest.getInterfaceName()) {
                        info.classList.add(harvest.getClassName());
                    }

                    val superClassNames = info.superClassNames
                    if (!superClassNames.isNullOrEmpty()) {
                        for (i in 0 until superClassNames.size) {
                            if (superClassNames.get(i) == harvest.getInterfaceName()) {
                                info.classList.add(harvest.getClassName())
                            }
                        }
                    }
                }
            }
            return true
        }
        return false
    }

    private inner class ScanClassVisitor(
        api: Int, cv: ClassVisitor, private val filePath: String
    ) : ClassVisitor(api, cv) {

        private var found: Boolean = false

        fun `is`(access: Int, flag: Int): Boolean {
            return access and flag == flag
        }
        fun isFound(): Boolean {
            return found
        }

        override fun visit(
            version: Int,
            access: Int,
            name: String?,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            super.visit(version, access, name, signature, superName, interfaces)
            //抽象类、接口、非public等类无法调用其无参构造方法
            if (`is`(access, Opcodes.ACC_ABSTRACT)
                || `is`(access, Opcodes.ACC_INTERFACE)
                || !`is`(access, Opcodes.ACC_PUBLIC)
            ) {
                return
            }

            infoList.forEach { ext: RegisterInfo ->
                if (shouldProcessThisClassForRegister(ext, name)) {
                    val superClassNames = ext.superClassNames
                    if (superName !== "java/lang/Object" && !superClassNames.isNullOrEmpty()) {
                        for (i in 0 until superClassNames.size) {
                            if (superClassNames.get(i) == superName && name != null) {
                                ext.classList.add(name)
                                found = true
                                addToCacheMap(superName, name, filePath)
                                return
                            }
                        }
                    }
                    val interfaceName: String? = ext.interfaceName
                    if (!interfaceName.isNullOrEmpty() && interfaces != null) {
                        interfaces.forEach {itName ->
                            if (itName == interfaceName && name != null) {
                                ext.classList.add(name) //需要把对象注入到管理类  就是fileContainsInitClass
                                addToCacheMap(itName, name, filePath)
                                found = true
                            }
                        }
                    }
                }
            }


        }

    }
}