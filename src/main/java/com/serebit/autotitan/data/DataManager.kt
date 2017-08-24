package com.serebit.autotitan.data

import com.google.gson.GsonBuilder
import java.io.File

private val parentFolder = File(DataManager::class.java.protectionDomain.codeSource.location.toURI()).parentFile
private val serializer = GsonBuilder().apply {
    serializeNulls()
}.create()

class DataManager(type: Class<*>) {
    private val dataFolder = File("$parentFolder/data/${type.name.replace(".", "/")}/")

    fun <T> read(fileName: String, objType: Class<T>): T? {
        val file = File("$dataFolder/$fileName")
        return if (file.exists()) serializer.fromJson(file.readText(), objType) else null
    }

    fun write(fileName: String, obj: Any) {
        val file = File("$dataFolder/$fileName").apply { mkdirs() }
        file.writeText(serializer.toJson(obj))
    }
}