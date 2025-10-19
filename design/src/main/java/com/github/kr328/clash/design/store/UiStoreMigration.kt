package com.github.kr328.clash.design.store

import android.content.Context
import com.tencent.mmkv.MMKV

object UiStoreMigration {
    private const val PREFERENCE_NAME = "ui"
    private const val MIGRATION_FLAG = "ui_migration_v1_done"

    fun migrateIfNeeded(context: Context) {
        val mmkv = MMKV.mmkvWithID(PREFERENCE_NAME)

        if (mmkv.decodeBool(MIGRATION_FLAG, false)) {
            return
        }

        try {
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val allData = sp.all

            allData.forEach { (key, value) ->
                when (value) {
                    is String -> mmkv.encode(key, value)
                    is Int -> mmkv.encode(key, value)
                    is Boolean -> mmkv.encode(key, value)
                    is Long -> mmkv.encode(key, value)
                    is Float -> mmkv.encode(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        mmkv.encode(key, value as? Set<String> ?: emptySet())
                    }

                    else -> {
                        android.util.Log.w("UiStoreMigration", "未知类型: $key = $value")
                    }
                }
            }

            mmkv.encode(MIGRATION_FLAG, true)

            android.util.Log.i("UiStoreMigration", "成功迁移 ${allData.size} 条数据到 MMKV")
        } catch (e: Exception) {
            android.util.Log.e("UiStoreMigration", "迁移失败", e)
        }
    }

    /**
     * 回滚到 SharedPreferences（紧急情况使用）
     *
     * 注意：此方法会将 MMKV 数据写回 SharedPreferences
     *
     * @param context Android Context
     */
    fun rollbackToSharedPreferences(context: Context) {
        try {
            val mmkv = MMKV.mmkvWithID(PREFERENCE_NAME)
            val sp = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            val editor = sp.edit()

            mmkv.allKeys()?.forEach { key ->
                // 跳过迁移标志
                if (key == MIGRATION_FLAG) return@forEach

                // 根据键名猜测类型并写回
                when {
                    key.endsWith("_mode") || key.endsWith("_sort") -> {
                        // 枚举类型（存为 String）
                        val value = mmkv.decodeString(key, "")
                        if (!value.isNullOrEmpty()) {
                            editor.putString(key, value)
                        }
                    }

                    key.startsWith("enable_") || key.startsWith("hide_") ||
                            key.endsWith("_reverse") || key.endsWith("_selectable") ||
                            key.contains("exclude") || key.contains("system") -> {
                        // Boolean 类型
                        val value = mmkv.decodeBool(key, false)
                        editor.putBoolean(key, value)
                    }

                    key.endsWith("_line") || key.endsWith("_type") -> {
                        // Int 类型
                        val value = mmkv.decodeInt(key, 0)
                        editor.putInt(key, value)
                    }

                    else -> {
                        // 默认为 String
                        val value = mmkv.decodeString(key, "")
                        if (!value.isNullOrEmpty()) {
                            editor.putString(key, value)
                        }
                    }
                }
            }

            editor.apply()
            android.util.Log.i("UiStoreMigration", "成功回滚到 SharedPreferences")
        } catch (e: Exception) {
            android.util.Log.e("UiStoreMigration", "回滚失败", e)
        }
    }

    /**
     * 清除迁移标志（测试用）
     *
     * 调用此方法后，下次启动将重新执行迁移
     */
    fun resetMigrationFlag() {
        val mmkv = MMKV.mmkvWithID(PREFERENCE_NAME)
        mmkv.removeValueForKey(MIGRATION_FLAG)
    }
}




