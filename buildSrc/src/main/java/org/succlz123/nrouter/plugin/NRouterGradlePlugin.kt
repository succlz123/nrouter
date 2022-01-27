package org.succlz123.nrouter.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.EXPAND_FRAMES
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile

class NRouterGradlePlugin : Transform(), Plugin<Project> {

    companion object {
        const val TAG = "NRouterGradlePlugin"

        val SUPPORTED_TYPES = setOf(
            "anim", "array", "attr", "bool", "color", "dimen",
            "drawable", "id", "integer", "layout", "menu", "plurals", "string", "style", "styleable"
        )

        var supportRClass = ArrayList<String>()

        init {
            supportRClass.add("R.class")
            for (supportedType in SUPPORTED_TYPES) {
                supportRClass.add("R\$${supportedType}.class")
            }
        }
    }


    override fun apply(project: Project) {
        val android = project.extensions.getByType(AppExtension::class.java)
        android.registerTransform(this)
    }

    override fun getName(): String {
        return TAG
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return TransformManager.CONTENT_CLASS
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(transformInvocation: TransformInvocation?) {
        println("--- $name ---")
        println("--- transform start ---")
        val outputProvider =
            transformInvocation?.outputProvider ?: return super.transform(transformInvocation)
        val inputs = transformInvocation.inputs
        val startTime = System.currentTimeMillis()
        outputProvider.deleteAll()


        val jarList = ArrayList<File>()

        println("--- transform start connect ---")
        inputs?.forEach { input ->
            input.directoryInputs.forEach { directoryInput ->
                if (directoryInput.file.isDirectory) {
                    directoryInput.file.walk().forEach { file ->
                        if (file.isFile) {
                            RClassUtil.collectRInfo(file)
                        }
                    }
                } else {
                    RClassUtil.collectRInfo(directoryInput.file)
                }
            }
            input.jarInputs.forEach { jarInput ->
                var jarName = jarInput.name
                val md5Name = DigestUtils.md5Hex(jarInput.file.absolutePath)
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length - 4)
                }
                val dest = outputProvider.getContentLocation(
                    jarName + md5Name,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                val src = jarInput.file
                FileUtils.copyFile(src, dest)
                if (src.path.contains("com.squareup")) {
                    //TODO 有很多jar其实是不需要处理的
                } else if (src.path.contains("nrouter")) {
                    println("--- transform start connect ---  " +src.path)
                    val jarFile = JarFile(jarInput.file)
                    val enumeration = jarFile.entries()
                    while (enumeration.hasMoreElements()) {
                        val jarEntry = enumeration.nextElement() as JarEntry
                        val entryName = jarEntry.name
                        if (isRClass(entryName)) {
                            println("--- RRRRR class from 1111class " + entryName + " ---")
                        }
                    }
                } else {
                    jarList.add(dest)
                }
            }
        }

        println("--- ----R类信息收集完毕---- ---")

        inputs?.forEach { input ->
            input.directoryInputs.forEach { directoryInput ->
                if (directoryInput.file.isDirectory) {
                    directoryInput.file.walk().forEach { file ->
                        if (file.isFile) {
                            RClassUtil.replaceAndDeleteRInfo(file, null)
                        }
                    }
                } else {
                    RClassUtil.replaceAndDeleteRInfo(directoryInput.file, null)
                }
                val dest = outputProvider.getContentLocation(
                    directoryInput.name,
                    directoryInput.contentTypes,
                    directoryInput.scopes,
                    Format.DIRECTORY
                )
                FileUtils.copyDirectory(directoryInput.file, dest)
            }
        }
//
//        for (file in jarList) {
//            println("处理Jar包里的R信息：${file.getAbsolutePath()}")
//            RClassUtil.replaceAndDeleteRInfoFromJar(jarFile, extension)
//        }

        val cost = (System.currentTimeMillis() - startTime) / 1000
        println("--- transform end - cost " + cost + "s ---")
    }

    private fun getProcessClassFromDirectoryInput(
        dirInput: DirectoryInput,
        processClasses: ArrayList<ClassParameter>,
        outputProvider: TransformOutputProvider
    ) {
        if (dirInput.file.isDirectory) {
            dirInput.file.walk().forEach { file ->
                if (file.isFile) {
                    val fileName = file.name
                    if (isRClass(fileName)) {
                        println("--- RRRRR class from class " + fileName + " ---")
                    }
                    if (isProcessClass(fileName) && isMapperClass(fileName)) {
                        val parameter = getClassParameter(file.readBytes())
                        println("--- mapper class from class " + parameter.className + " ---")
                        parameter.parentFilePath = dirInput.file.absolutePath
                        processClasses.add(parameter)
                    }
                }
            }
        }
        val dest = outputProvider.getContentLocation(
            dirInput.name,
            dirInput.contentTypes,
            dirInput.scopes,
            Format.DIRECTORY
        )
        FileUtils.copyDirectory(dirInput.file, dest)
    }

    private fun getProcessClassFromJarInputs(
        jarInput: JarInput,
        processClasses: ArrayList<ClassParameter>,
        outputProvider: TransformOutputProvider
    ) {
        var jarName = jarInput.name
        val md5Name = DigestUtils.md5Hex(jarInput.file.absolutePath)
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length - 4)
        }
        if (jarInput.file.absolutePath.endsWith(".jar")) {
            val dest = outputProvider.getContentLocation(
                jarName + md5Name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
            )


//            val jarFile = JarFile(jarInput.file)
//            val enumeration = jarFile.entries()
//            while (enumeration.hasMoreElements()) {
//                val jarEntry = enumeration.nextElement() as JarEntry
//                val entryName = jarEntry.name
//                if (isRClass(entryName)) {
//                    println("--- RRRRR class from 1111class " + entryName + " ---")
//                }
//                if (isProcessClass(entryName)) {
//                    if (isManagerClass(entryName)) {
//                        println("--- manager class from jar $entryName ---")
//                        managerJarInput = jarInput
//                        isManagerJar = true
//                    } else if (isMapperClass(entryName)) {
//                        val inputStream = jarFile.getInputStream(jarEntry)
//                        val parameter = getClassParameter(inputStream.readBytes())
//                        println("--- mapper class from jar ${parameter.className} ---")
//                        parameter.jarFilePath = jarFile.name
//                        processClasses.add(parameter)
//                    }
//                }
//            }
        }
    }

    private fun handleManagerJarInputs(
        jarInput: JarInput,
        processClasses: List<ClassParameter>,
        outputProvider: TransformOutputProvider
    ) {
        val path = jarInput.file.absolutePath
        val jarFile = File(path)
        val jarZipDir = jarFile.parent + "/" + jarFile.name.replace(".jar", "")
        // xxx.xxx.xxx.class xxx.xxx.yyy.class
        val classNameList = JarZipUtils.unzipJar(path, jarZipDir)
        // delete the original jar package.
        jarFile.delete()

        val pool = ClassPool.getDefault()
        pool.appendClassPath(jarZipDir)
        val user = System.getProperties().getProperty("user.home")
        // add you android sdk directory here
        pool.appendClassPath("$user/Library/Android/sdk/platforms/android-27/android.jar")
        for (classParameter in processClasses) {
            val parentPath = classParameter.parentFilePath
            val jarFilePath = classParameter.jarFilePath
            if (parentPath != null) {
                pool.appendClassPath(parentPath)
            } else if (jarFilePath != null) {
                val processJarFile = File(jarFilePath)
                if (processJarFile.exists()) {
                    val processJarDir = processJarFile.parent + "/" + processJarFile.name
                        .replace(".jar", "")
                    JarZipUtils.unzipJar(jarFilePath, processJarDir)
                    pool.appendClassPath(processJarDir)
                }
            }
        }
        println("--- process manager class - init ---")

        for (cn in classNameList) {
            if (cn == ("org.succlz123.nrouter.NRouter.class")) {
                val className = cn.substring(0, cn.length - 6)
                val c = pool.getCtClass(className)
                if (c.isFrozen) {
                    c.defrost()
                }
                val method = c.getDeclaredMethod("register")
                val sb = StringBuilder()

                processClasses.forEachIndexed { _, classParameter ->
                    val taskClassName = classParameter.className
                    if (taskClassName.isNullOrEmpty()) {
                        return@forEachIndexed
                    }
                    println("--- class - " + classParameter.className + " ---")
                    val instanceName = taskClassName.split(".").lastOrNull()
                    if (instanceName.isNullOrEmpty()) {
                        return@forEachIndexed
                    }
                    sb.append("$taskClassName $instanceName = new $taskClassName();")
                    sb.append("\n")
                    sb.append("allMapper.add($instanceName);")
                    sb.append("\n")
                }
                method.insertAfter(sb.toString())
                c.writeFile(jarZipDir)
                c.detach()
            }
        }
        JarZipUtils.zipJar(jarZipDir, path)
        //        FileUtils.deleteDirectory(new File(jarZipDir)) 如果不删除，可以 debug 看生成的 classes
        val dest = outputProvider.getContentLocation(
            jarInput.name,
            jarInput.contentTypes,
            jarInput.scopes,
            Format.JAR
        )
        FileUtils.copyFile(jarInput.file, dest)
    }

    private fun getClassParameter(bytes: ByteArray): ClassParameter {
        val classReader = ClassReader(bytes)
        val cn = ClassNode()
        classReader.accept(cn, EXPAND_FRAMES)
        val parameter = ClassParameter()
        parameter.className = covertName(cn.name)
        return parameter
    }

    // xxx/yyy/zzz -> xxx.yyy.zzz
    private fun covertName(name: String): String {
        return name.replace('\\', '.').replace('/', '.')
    }

    private fun getClassNameFromTypeName(typeName: String): String {
        val name = covertName(typeName)
        return covertName(typeName).substring(1, name.length - 1)
    }

    private fun isRClass(name: String): Boolean {
        return name.endsWith(".class") && supportRClass.any { name.endsWith(it) }
    }

    private fun isProcessClass(name: String): Boolean {
        return name.endsWith(".class") && !name.startsWith("R\$") && "R.class" != name && "BuildConfig.class" != name
    }

    private fun isMapperClass(name: String): Boolean {
        return name.contains("NRouterMapper")
    }

    private fun isManagerClass(name: String): Boolean {
        return name.contains("NRouter.class")
    }
}
