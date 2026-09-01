package com.example.securevault.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secure_files")
data class SecureFileItem(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val originalFileName: String,
  val mimeType: String,
  val fileSizeBytes: Long,
  val encryptedBlobPath: String,
  val dateAdded: Long = System.currentTimeMillis(),
  val iv: String,
  val wrappedDek: String = "",
  val dekIv: String = ""
)
