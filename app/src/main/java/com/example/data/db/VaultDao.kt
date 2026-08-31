package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
  @Query("SELECT * FROM vault_items ORDER BY purchaseDate DESC")
  fun getAllItems(): Flow<List<VaultItem>>

  @Query("SELECT * FROM vault_items WHERE id = :id")
  fun getItemById(id: Long): Flow<VaultItem?>

  @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
  suspend fun getItemByIdSync(id: Long): VaultItem?

  @Query("""
    SELECT * FROM vault_items 
    WHERE storeName LIKE '%' || :query || '%' 
       OR category LIKE '%' || :query || '%' 
       OR notes LIKE '%' || :query || '%' 
       OR ocrRawText LIKE '%' || :query || '%'
    ORDER BY purchaseDate DESC
  """)
  fun searchItems(query: String): Flow<List<VaultItem>>

  @Query("SELECT * FROM vault_items WHERE isWarranty = 1 ORDER BY warrantyExpirationDate ASC")
  fun getWarrantyItems(): Flow<List<VaultItem>>

  @Query("SELECT * FROM vault_items WHERE isSubscription = 1 ORDER BY subscriptionNextRenewalDate ASC")
  fun getSubscriptionItems(): Flow<List<VaultItem>>

  @Query("SELECT * FROM vault_items WHERE isWarranty = 1 AND warrantyExpirationDate IS NOT NULL AND warrantyExpirationDate <= :maxTimestamp AND warrantyExpirationDate >= :minTimestamp")
  suspend fun getWarrantiesExpiringBetween(minTimestamp: Long, maxTimestamp: Long): List<VaultItem>

  @Query("SELECT * FROM vault_items WHERE isSubscription = 1 AND subscriptionActive = 1 AND subscriptionNextRenewalDate IS NOT NULL AND subscriptionNextRenewalDate <= :maxTimestamp AND subscriptionNextRenewalDate >= :minTimestamp")
  suspend fun getSubscriptionsRenewingBetween(minTimestamp: Long, maxTimestamp: Long): List<VaultItem>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertItem(item: VaultItem): Long

  @Update
  suspend fun updateItem(item: VaultItem)

  @Delete
  suspend fun deleteItem(item: VaultItem)

  @Query("DELETE FROM vault_items WHERE id = :id")
  suspend fun deleteItemById(id: Long)
}
