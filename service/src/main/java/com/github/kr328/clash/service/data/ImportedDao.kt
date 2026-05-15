package com.github.kr328.clash.service.data

import androidx.room.*
import java.util.*

@Dao
@TypeConverters(Converters::class)
interface ImportedDao {
    @Query("SELECT * FROM imported WHERE uuid = :uuid")
    suspend fun queryByUUID(uuid: UUID): Imported?

    @Query("SELECT uuid FROM imported ORDER BY profileOrder, createdAt")
    suspend fun queryAllUUIDs(): List<UUID>

    @Query("SELECT uuid, profileOrder FROM imported UNION SELECT uuid, profileOrder FROM pending ORDER BY profileOrder, uuid")
    suspend fun queryAllOrderedUUIDs(): List<ProfileOrderEntry>

    @Query("SELECT MAX(profileOrder) FROM imported")
    suspend fun queryMaxProfileOrder(): Long?

    @Query("UPDATE imported SET profileOrder = :profileOrder WHERE uuid = :uuid")
    suspend fun updateProfileOrder(uuid: UUID, profileOrder: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(imported: Imported): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(imported: Imported)

    @Query("DELETE FROM imported WHERE uuid = :uuid")
    suspend fun remove(uuid: UUID)

    @Query("SELECT EXISTS(SELECT 1 FROM imported WHERE uuid = :uuid)")
    suspend fun exists(uuid: UUID): Boolean
}

data class ProfileOrderEntry(
    @ColumnInfo(name = "uuid") val uuid: UUID,
    @ColumnInfo(name = "profileOrder") val profileOrder: Long,
)
