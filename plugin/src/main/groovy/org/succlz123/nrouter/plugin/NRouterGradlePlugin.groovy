package org.succlz123.nrouter.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode

import java.util.jar.JarEntry
import java.util.jar.JarFile

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES

class NRouterGradlePlugin extends Transform implements Plugin<Project> {
    JarInput managerJarInput = null

    @Override
    void apply(Project project) {
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(this)
    }

    @Override
    String getName() {
        return "NRouterGradlePlugin"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        println '--- ' + getName() + ' transform start ---'
        def startTime = System.currentTimeMillis()
        if (outputProvider != null) {
            outputProvider.deleteAll()
        }
        List<ClassParameter> processClasses = new ArrayList<>()
        inputs.each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput directoryInput ->
                getProcessClassFromDirectoryInput(directoryInput, processClasses, outputProvider)
            }
            input.jarInputs.each { JarInput jarInput ->
                getProcessClassFromJarInputs(jarInput, processClasses, outputProvider)
            }
        }
        if (managerJarInput != null && processClasses.size() > 0) {
            handleManagerJarInputs(managerJarInput, processClasses, outputProvider)
        } else if (managerJarInput != null) {
            // If you only apply the plug-in and don't use the library, we should handle the manager JAR to avoid the NoClassDefFoundError.
            def dest = outputProvider.getContentLocation(managerJarInput.name, managerJarInput.contentTypes, managerJarInput.scopes, Format.JAR)
            FileUtils.copyFile(managerJarInput.file, dest)
        }
        def cost = (System.currentTimeMillis() - startTime) / 1000d
        println '--- ' + getName() + ' transform end - cost ' + cost + 's ---'
    }

    void getProcessClassFromDirectoryInput(DirectoryInput dirInput, List<ClassParameter> processClasses, TransformOutputProvider outputProvider) {
        if (dirInput.file.isDirectory()) {
            dirInput.file.eachFileRecurse { File file ->
                if (file.isFile()) {
                    def name = file.name
                    if (isProcessClass(name) && isMapperClass(name)) {
                        ClassParameter parameter = getClassParameter(file.bytes)
                        println '--- mapper class from "class" ' + parameter.className + ' ---'
                        parameter.parentFilePath = dirInput.getFile().getAbsolutePath()
                        processClasses.add(parameter)
                    }
                }
            }
        }
        def dest = outputProvider.getContentLocation(dirInput.name, dirInput.contentTypes,
                dirInput.scopes, Format.DIRECTORY)
        FileUtils.copyDirectory(dirInput.file, dest)
    }

    void getProcessClassFromJarInputs(JarInput jarInput, List<ClassParameter> processClasses, TransformOutputProvider outputProvider) {
        boolean isManagerJar = false
        def jarName = jarInput.name
        def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
        if (jarName.endsWith(".jar")) {
            jarName = jarName.substring(0, jarName.length() - 4)
        }
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                if (isProcessClass(entryName)) {
                    if (isManagerClass(entryName)) {
                        println '--- manager class from "jar" ' + entryName + ' ---'
                        managerJarInput = jarInput
                        isManagerJar = true
                    } else if (isMapperClass(entryName)) {
                        InputStream inputStream = jarFile.getInputStream(jarEntry)
                        ClassParameter parameter = getClassParameter(inputStream.getBytes())
                        println '--- mapper class from "jar" ' + parameter.className + ' ---'
                        parameter.jarFilePath = jarFile.name
                        processClasses.add(parameter)
                    }
                }
            }
        }
        if (!isManagerJar) {
            def dest = outputProvider.getContentLocation(jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(jarInput.file, dest)
        }
    }

    static void handleManagerJarInputs(JarInput jarInput, List<ClassParameter> processClasses, TransformOutputProvider outputProvider) {
        String path = jarInput.file.absolutePath
        File jarFile = new File(path)
        String jarZipDir = jarFile.getParent() + "/" + jarFile.getName().replace('.jar', '')
        // xxx.xxx.xxx.class xxx.xxx.yyy.class
        List classNameList = JarZipUtil.unzipJar(path, jarZipDir)
        // delete the original jar package.
        jarFile.delete()

        ClassPool pool = ClassPool.getDefault()
        pool.appendClassPath(jarZipDir)
        String user = System.getProperties().getProperty("user.home")
        // add you android sdk directory here
        pool.appendClassPath(user + "/Library/Android/sdk/platforms/android-27/android.jar")
        for (ClassParameter classParameter : processClasses) {
            if (classParameter.parentFilePath != null) {
                pool.appendClassPath(classParameter.parentFilePath)
            } else if (classParameter.jarFilePath != null) {
                File processJarFile = new File(classParameter.jarFilePath)
                if (processJarFile.exists()) {
                    String processJarDir = processJarFile.getParent() + "/" + processJarFile.getName().replace('.jar', '')
                    JarZipUtil.unzipJar(classParameter.jarFilePath, processJarDir)
                    pool.appendClassPath(processJarDir)
                }
            }
        }
        println '--- process manager class - init ---'
        for (String className : classNameList) {
            if (className == ("org.succlz123.nrouter.NRouter.class")) {
                className = className.substring(0, className.length() - 6)
                CtClass c = pool.getCtClass(className)
                if (c.isFrozen()) {
                    c.defrost()
                }
                CtMethod method = c.getDeclaredMethod("register")
                StringBuilder sb = new StringBuilder()
                for (int i = 0; i < processClasses.size(); i++) {
                    ClassParameter classParameter = processClasses.get(i)
                    String taskClassName = classParameter.className
                    println '--- class - ' + classParameter.className + ' ---'
                    String[] instanceNames = taskClassName.split("\\.")
                    String instanceName = instanceNames[instanceNames.length - 1]
                    sb.append(taskClassName + " " + instanceName + " = new " + taskClassName + "();")
                    sb.append("\n")
                    sb.append("allMapper.add(" + instanceName + ");")
                    sb.append("\n")
                }
                method.insertAfter(sb.toString())
                c.writeFile(jarZipDir)
                c.detach()
            }
        }
        JarZipUtil.zipJar(jarZipDir, path)
        //        FileUtils.deleteDirectory(new File(jarZipDir)) 如果不删除，可以 debug 看生成的 classes
        def dest = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
        FileUtils.copyFile(jarInput.file, dest)
    }

    private static ClassParameter getClassParameter(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes)
        ClassNode cn = new ClassNode()
        classReader.accept(cn, EXPAND_FRAMES)
        ClassParameter parameter = new ClassParameter()
        parameter.className = covertName(cn.name)
        return parameter
    }

    // xxx/yyy/zzz -> xxx.yyy.zzz
    private static String covertName(String name) {
        return name.replace('\\', '.').replace('/', '.')
    }

    private static String getClassNameFromTypeName(String typeName) {
        String name = covertName(typeName)
        return covertName(typeName).substring(1, name.length() - 1)
    }

    private static boolean isProcessClass(String name) {
        return name.endsWith(".class") && !name.startsWith("R\$") && "R.class" != name && "BuildConfig.class" != name
    }

    private static boolean isMapperClass(String name) {
        return name.contains("NRouterMapper")
    }

    private static boolean isManagerClass(String name) {
        return name.contains("NRouter.class")
    }
}
