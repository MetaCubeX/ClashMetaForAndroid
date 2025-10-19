package com.github.kr328.clash.util

import android.content.Context
import com.tencent.mmkv.MMKV

object MMKVHelper {
    private lateinit var mmkv: MMKV

    fun initialize(context: Context) {
        val rootDir = MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
        android.util.Log.d("MMKVHelper", "MMKV 初始化完成，根目录: $rootDir")
    }

    fun getMMKV(mmapID: String = "default"): MMKV {
        return if (mmapID == "default") {
            mmkv
        } else {
            MMKV.mmkvWithID(mmapID)
        }
    }


    fun putString(key: String, value: String, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getString(key: String, defaultValue: String = "", mmapID: String = "default"): String {
        return getMMKV(mmapID).decodeString(key, defaultValue) ?: defaultValue
    }

    fun putInt(key: String, value: Int, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getInt(key: String, defaultValue: Int = 0, mmapID: String = "default"): Int {
        return getMMKV(mmapID).decodeInt(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false, mmapID: String = "default"): Boolean {
        return getMMKV(mmapID).decodeBool(key, defaultValue)
    }

    fun putLong(key: String, value: Long, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getLong(key: String, defaultValue: Long = 0L, mmapID: String = "default"): Long {
        return getMMKV(mmapID).decodeLong(key, defaultValue)
    }

    fun putFloat(key: String, value: Float, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getFloat(key: String, defaultValue: Float = 0f, mmapID: String = "default"): Float {
        return getMMKV(mmapID).decodeFloat(key, defaultValue)
    }


    fun putStringSet(key: String, value: Set<String>, mmapID: String = "default") {
        getMMKV(mmapID).encode(key, value)
    }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet(), mmapID: String = "default"): Set<String> {
        return getMMKV(mmapID).decodeStringSet(key, defaultValue) ?: defaultValue
    }

    fun putStringMap(map: Map<String, String>, prefix: String = "kv_", mmapID: String = "default") {
        val kv = getMMKV(mmapID)

        clearMapByPrefix(prefix, mmapID)

        map.forEach { (key, value) ->
            kv.encode("$prefix$key", value)
        }

        kv.encode("${prefix}_keys", map.keys.toSet())
    }

    fun getStringMap(prefix: String = "kv_", mmapID: String = "default"): Map<String, String> {
        val kv = getMMKV(mmapID)
        val keys = kv.decodeStringSet("${prefix}_keys", emptySet()) ?: emptySet()
        return keys.associateWith { key ->
            kv.decodeString("$prefix$key", "") ?: ""
        }
    }

    fun putStringMapEntry(key: String, value: String, prefix: String = "kv_", mmapID: String = "default") {
        val kv = getMMKV(mmapID)
        kv.encode("$prefix$key", value)


        val keys = kv.decodeStringSet("${prefix}_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        keys.add(key)
        kv.encode("${prefix}_keys", keys)
    }

    fun removeStringMapEntry(key: String, prefix: String = "kv_", mmapID: String = "default") {
        val kv = getMMKV(mmapID)
        kv.removeValueForKey("$prefix$key")


        val keys = kv.decodeStringSet("${prefix}_keys", emptySet())?.toMutableSet() ?: mutableSetOf()
        keys.remove(key)
        kv.encode("${prefix}_keys", keys)
    }

    fun clearMapByPrefix(prefix: String = "kv_", mmapID: String = "default") {
        val kv = getMMKV(mmapID)
        val keys = kv.decodeStringSet("${prefix}_keys", emptySet()) ?: emptySet()
        keys.forEach { key ->
            kv.removeValueForKey("$prefix$key")
        }
        kv.removeValueForKey("${prefix}_keys")
    }


    fun contains(key: String, mmapID: String = "default"): Boolean {
        return getMMKV(mmapID).containsKey(key)
    }

    fun remove(key: String, mmapID: String = "default") {
        getMMKV(mmapID).removeValueForKey(key)
    }

    fun clearAll(mmapID: String = "default") {
        getMMKV(mmapID).clearAll()
    }

    fun getAllKeys(mmapID: String = "default"): Array<String>? {
        return getMMKV(mmapID).allKeys()
    }

    fun getTotalSize(mmapID: String = "default"): Long {
        return getMMKV(mmapID).totalSize()
    }

    fun sync(mmapID: String = "default") {
        getMMKV(mmapID).sync()
    }


    fun getMultiProcessMMKV(mmapID: String): MMKV {
        return MMKV.mmkvWithID(mmapID, MMKV.MULTI_PROCESS_MODE)
    }


    fun getEncryptedMMKV(mmapID: String, cryptKey: String): MMKV {
        return MMKV.mmkvWithID(mmapID, MMKV.SINGLE_PROCESS_MODE, cryptKey)
    }
}

