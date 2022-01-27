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
    }

    private var managerJarInput: JarInput? = null

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
        println("--- $name transform start ---")
        val outputProvider =
            transformInvocation?.outputProvider ?: return super.transform(transformInvocation)
        val inputs = transformInvocation.inputs
        val startTime = System.currentTimeMillis()
        outputProvider.deleteAll()
        val processClasses = ArrayList<ClassParameter>()
        inputs?.forEach { input ->
            input.directoryInputs.forEach { directoryInput ->
                getProcessClassFromDirectoryInput(directoryInput, processClasses, outputProvider)
            }
            input.jarInputs.forEach { jarInput ->
                getProcessClassFromJarInputs(jarInput, processClasses, outputProvider)
            }
        }
        managerJarInput?.let {
            if (processClasses.isEmpty()) {
                // If you only apply the plug-in and don't use the library, we should handle the manager JAR to avoid the NoClassDefFoundError.
                val dest = outputProvider.getContentLocation(
                    it.name,
                    it.contentTypes,
                    it.scopes,
                    Format.JAR
                )
                FileUtils.copyFile(it.file, dest)
            } else {
                handleManagerJarInputs(it, processClasses, outputProvider)
            }
        }
        val cost = (System.currentTimeMillis() - startTime) / 1000
        println("--- " + name + " transform end - cost " + cost + "s ---")
    }

    private fun getProcessClassFromDirectoryInput(
        dirInput: DirectoryInput,
        processClasses: ArrayList<ClassParameter>,
        outputProvider: TransformOutputProvider
    ) {
        if (dirInput.file.isDirectory) {
            dirInput.file.walk().forEach { file ->
                if (file.isFile) {
                    val name = file.name
                    if (isRClass(name)) {
                        println("--- RRRRR class from class " + name + " ---")
                    }
                    if (isProcessClass(name) && isMapperClass(name)) {
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
        var isManagerJar: Boolean = false
        var jarName = jarInput.name
        val md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length - 4)
        }
        if (jarInput.file.absolutePath.endsWith(".jar")) {
            val jarFile = JarFile(jarInput.file)
            val enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                val jarEntry = enumeration.nextElement() as JarEntry
                val entryName = jarEntry.name
                if (isRClass(name)) {
                    println("--- RRRRR class from class " + name + " ---")
                }
                if (isProcessClass(entryName)) {
                    if (isManagerClass(entryName)) {
                        println("--- manager class from jar $entryName ---")
                        managerJarInput = jarInput
                        isManagerJar = true
                    } else if (isMapperClass(entryName)) {
                        val inputStream = jarFile.getInputStream(jarEntry)
                        val parameter = getClassParameter(inputStream.readBytes())
                        println("--- mapper class from jar ${parameter.className} ---")
                        parameter.jarFilePath = jarFile.name
                        processClasses.add(parameter)
                    }
                }
            }
        }
        if (!isManagerJar) {
            val dest = outputProvider.getContentLocation(
                jarName + md5Name,
                jarInput.contentTypes,
                jarInput.scopes,
                Format.JAR
            )
            FileUtils.copyFile(jarInput.file, dest)
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
                    if (instanceName.isNullOrEmpty()){
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
        return name.startsWith("R\$") || "R.class" == name
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
