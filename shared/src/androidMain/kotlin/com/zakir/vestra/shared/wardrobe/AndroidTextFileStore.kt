package com.zakir.vestra.shared.wardrobe

import java.io.File

class AndroidTextFileStore(private val directory: File) : TextFileStore {

    init {
        directory.mkdirs()
    }

    override fun read(name: String): String? {
        val file = File(directory, name)
        return if (file.exists()) file.readText() else null
    }

    override fun write(name: String, content: String) {
        val tmp = File(directory, "$name.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(File(directory, name))) {
            File(directory, name).writeText(content)
            tmp.delete()
        }
    }
}
