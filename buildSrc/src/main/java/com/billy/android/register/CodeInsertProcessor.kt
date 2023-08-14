package com.billy.android.register

import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Enumeration
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


class CodeInsertProcessor(private val extension: RegisterInfo) {

    companion object {
        @JvmStatic
        fun insertInitCodeTo(extension: RegisterInfo?) {
            if (extension != null && extension.getClassList().isNotEmpty()) {
                val processor : CodeInsertProcessor = CodeInsertProcessor(extension)
                val file: File? = extension.getFileContainsInitClass()
                if (file != null) {
                    if (file.name.endsWith(".jar")) {
                        processor.generateCodeIntoJarFile(file)
                    } else {
                        processor.generateCodeIntoClassFile(file)
                    }
                }

            }
        }
    }



    private fun generateCodeIntoJarFile(jarFile: File?) {
        if (jarFile!= null) {
            val optJar = File(jarFile.parent, jarFile.name + ".opt")
            if (optJar.exists()) optJar.delete()
            val file = JarFile(jarFile)
            val enumeration: Enumeration<*> = file.entries()
            val jarOutputStream = JarOutputStream(FileOutputStream(optJar))

            while (enumeration.hasMoreElements()) {
                val jarEntry: JarEntry = enumeration.nextElement() as JarEntry
                val entryName: String = jarEntry.getName()
                val zipEntry = ZipEntry(entryName)
                val inputStream: InputStream = file.getInputStream(jarEntry)
                jarOutputStream.putNextEntry(zipEntry)
                if (isInitClass(entryName)) {
                    println("generate code into:$entryName")
                    val bytes = doGenerateCode(inputStream)
                    jarOutputStream.write(bytes)
                } else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                inputStream.close()
                jarOutputStream.closeEntry()
            }
            jarOutputStream.close()
            file.close()

            if (jarFile.exists()) {
                jarFile.delete()
            }
            optJar.renameTo(jarFile)
        }
    }

    private fun generateCodeIntoClassFile(file: File): ByteArray? {
        val optClass = File(file.parent, file.name + ".opt")

        val inputStream = FileInputStream(file)
        val outputStream = FileOutputStream(optClass)

        val bytes = doGenerateCode(inputStream)
        outputStream.write(bytes)
        inputStream.close()
        outputStream.close()
        if (file.exists()) {
            file.delete()
        }
        optClass.renameTo(file)
        return bytes
    }

    private fun doGenerateCode(inputStream: InputStream): ByteArray? {
        val cr = ClassReader(inputStream)
        val cw = ClassWriter(cr, 0)
        val cv: ClassVisitor = MyClassVisitor(Opcodes.ASM6, cw)
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray()
    }

    private fun isInitClass(entryName: String?): Boolean {
        var entryName = entryName
        if (entryName == null || !entryName.endsWith(".class")) return false
        if (!extension.getInitClassName().isNullOrEmpty()) {
            entryName = entryName.substring(0, entryName.lastIndexOf('.'))
            return extension.getInitClassName() === entryName
        }
        return false
    }

    private inner class MyClassVisitor(api: Int, cv: ClassVisitor) : ClassVisitor(api, cv) {

        override fun visitMethod(
            access: Int,
            name: String?,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            var mv : MethodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
            if (name == extension.getInitClassName()) {
                val _static: Boolean = (access and Opcodes.ACC_STATIC) > 0
                mv = MyMethodVisitor(Opcodes.ASM6, mv, _static)
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }
    }
    
    private inner class MyMethodVisitor(api: Int, mv: MethodVisitor, private val _static : Boolean): MethodVisitor(api, mv) {
        override fun visitInsn(opcode: Int) {
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                extension.getClassList().forEach { name -> 
                    if (!_static) {
                        // 加载This
                        mv.visitVarInsn(Opcodes.ALOAD, 0)
                    }

                    //用无参构造方法创建一个组件实例
                    mv.visitTypeInsn(Opcodes.NEW, name)
                    mv.visitInsn(Opcodes.DUP)
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false)
                    //调用注册方法将组件实例注册到组件库中
                    if (_static) {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC
                            , extension.getRegisterClassName()
                            , extension.getRegisterMethodName()
                            , "(L${extension.getInterfaceName()})V"
                            , false)
                    } else {
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL
                            , extension.getRegisterClassName()
                            , extension.getRegisterMethodName()
                            , "(L${extension.getInterfaceName()})V"
                            , false)
                    }
                }
            }
            super.visitInsn(opcode)
        }

        override fun visitMaxs(maxStack: Int, maxLocals: Int) {
            super.visitMaxs(maxStack + 4, maxLocals)
        }
    }
}