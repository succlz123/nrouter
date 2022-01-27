package org.succlz123.nrouter.plugin

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

object JarZipUtils {

    fun unzipJar(jarPath: String, destDirPath: String): List<String> {
        val list = ArrayList<String>()
        if (jarPath.endsWith(".jar")) {
            val jarFile = JarFile(jarPath)
            val jarEntries = jarFile.entries()
            while (jarEntries.hasMoreElements()) {
                val jarEntry = jarEntries.nextElement()
                if (jarEntry.isDirectory) {
                    continue
                }
                val entryName = jarEntry.name
                if (entryName.endsWith(".class")) {
                    val className = entryName.replace('\\', '.').replace('/', '.')
                    list.add(className)
                }
                val outFileName = "$destDirPath/$entryName"
                val outFile = File(outFileName)
                outFile.parentFile.mkdirs()
                val inputStream = jarFile.getInputStream(jarEntry)
                val fileOutputStream = FileOutputStream(outFile)
                val buf = ByteArray(8192)
                var count: Int
                while (-1 != inputStream.read(buf).also { count = it }) {
                    fileOutputStream.write(buf, 0, count)
                }
                fileOutputStream.flush()
                fileOutputStream.close()
                inputStream.close()
            }
            jarFile.close()
        }
        return list
    }

    fun zipJar(packagePath: String, destPath: String) {
        val file = File(packagePath)
        val outputStream = JarOutputStream(FileOutputStream(destPath))
        eachFileRecurse(file, FileType.ANY) { f ->
            val entryName = f.absolutePath.substring(packagePath.length + 1)
            outputStream.putNextEntry(ZipEntry(entryName))
            if (!f.isDirectory) {
                val inputStream = FileInputStream(f)
                val buf = ByteArray(8192)
                var count: Int
                while (-1 != inputStream.read(buf).also { count = it }) {
                    outputStream.write(buf, 0, count)
                }
                outputStream.flush()
                inputStream.close()
            }
        }
        outputStream.close()
    }

    enum class FileType {
        FILES, DIRECTORIES, ANY
    }

    @Throws(
        FileNotFoundException::class,
        IllegalArgumentException::class
    )
    fun eachFileRecurse(
        self: File,
        fileType: FileType,
        cb: (file: File) -> Unit
    ) {
        checkDir(self)
        val files = self.listFiles()
        if (files != null) {
            val var5 = files.size
            for (var6 in 0 until var5) {
                val file = files[var6]
                if (file.isDirectory) {
                    if (fileType != FileType.FILES) {
                        cb.invoke(file)
                    }
                    eachFileRecurse(file, fileType, cb)
                } else if (fileType != FileType.DIRECTORIES) {
                    cb.invoke(file)
                }
            }
        }
    }

    @Throws(
        FileNotFoundException::class,
        java.lang.IllegalArgumentException::class
    )
    private fun checkDir(dir: File) {
        if (!dir.exists()) {
            throw FileNotFoundException(dir.absolutePath)
        } else require(dir.isDirectory) { "The provided File object is not a directory: " + dir.absolutePath }
    }
}
