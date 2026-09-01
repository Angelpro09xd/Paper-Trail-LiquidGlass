package com.example.securevault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.securevault.model.SecureFileItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SecureVaultDao {
  @Query("SELECT * FROM secure_files ORDER BY dateAdded DESC")
  fun getAllFiles(): Flow<List<SecureFileItem>>

  @Query("SELECT * FROM secure_files WHERE id = :id")
  suspend fun getFileById(id: Long): SecureFileItem?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFile(file: SecureFileItem): Long

  @Delete
  suspend fun deleteFile(file: SecureFileItem)

  @Query("DELETE FROM secure_files WHERE id = :id")
  suspend fun deleteFileById(id: Long)
}
