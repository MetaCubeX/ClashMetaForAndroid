package com.github.kr328.clash.design.store

import com.github.kr328.clash.common.store.StoreProvider
import com.tencent.mmkv.MMKV

class MMKVStoreProvider(name: String) : StoreProvider {
    private val mmkv = MMKV.mmkvWithID(name, MMKV.MULTI_PROCESS_MODE)

    override fun getInt(key: String, defaultValue: Int): Int {
        return mmkv.decodeInt(key, defaultValue)
    }

    override fun setInt(key: String, value: Int) {
        mmkv.encode(key, value)
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return mmkv.decodeLong(key, defaultValue)
    }

    override fun setLong(key: String, value: Long) {
        mmkv.encode(key, value)
    }

    override fun getString(key: String, defaultValue: String): String {
        return mmkv.decodeString(key, defaultValue) ?: defaultValue
    }

    override fun setString(key: String, value: String) {
        mmkv.encode(key, value)
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> {
        return mmkv.decodeStringSet(key, defaultValue) ?: defaultValue
    }

    override fun setStringSet(key: String, value: Set<String>) {
        mmkv.encode(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mmkv.decodeBool(key, defaultValue)
    }

    override fun setBoolean(key: String, value: Boolean) {
        mmkv.encode(key, value)
    }
}
