package com.github.kr328.clash.service.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverters
import com.github.kr328.clash.service.model.Profile
import java.util.*

@Entity(tableName = "imported", primaryKeys = ["uuid"])
@TypeConverters(Converters::class)
data class Imported(
    @ColumnInfo(name = "uuid") val uuid: UUID,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: Profile.Type,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "interval") val interval: Long,
    @ColumnInfo(name = "upload") val upload: Long,
    @ColumnInfo(name = "download") val download: Long,
    @ColumnInfo(name = "total") val total: Long,
    @ColumnInfo(name = "expire") val expire: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "profileOrder") val profileOrder: Long = createdAt,
    /** age identity (AGE-SECRET-KEY-…) used to decrypt an age-encrypted subscription; null = plain. */
    @ColumnInfo(name = "ageSecretKey") val ageSecretKey: String? = null,
)
