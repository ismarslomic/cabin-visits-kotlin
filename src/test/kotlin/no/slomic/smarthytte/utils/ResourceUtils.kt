package no.slomic.smarthytte.utils

fun getResourceFilePath(fileName: String): String =
    Thread.currentThread().contextClassLoader.getResource(fileName)!!.toURI().path
