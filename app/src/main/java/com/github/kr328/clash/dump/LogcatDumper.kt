package com.github.kr328.clash.dump

import com.github.kr328.clash.core.utils.Log

object LogcatDumper {
    fun dump(): List<String> {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-s", "-v", "raw", "Go"))

            val result = process.inputStream.bufferedReader().useLines {
                var list = mutableListOf<String>()
                var capture = false

                it.forEach { line ->
                    if ( line.startsWith("panic") ) {
                        capture = true

                        list = mutableListOf()
                    }

                    if ( capture )
                        list.add(line)
                }

                list
            }

            process.waitFor()

            result.forEach {
                Log.d("Catch $it")
            }

            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}