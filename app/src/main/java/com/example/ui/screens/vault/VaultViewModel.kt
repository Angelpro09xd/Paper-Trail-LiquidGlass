package com.example.ui.screens.vault

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.PaperTrailApp
import com.example.data.model.SubscriptionCycle
import com.example.data.model.VaultItem
import com.example.data.notifications.ReminderScheduler
import com.example.ui.components.ChartSlice
import com.example.ui.components.DefaultChartPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class VaultTab(val title: String) {
  ALL("All Ledger"),
  RECEIPTS("Receipts"),
  WARRANTIES("Warranties"),
  SUBSCRIPTIONS("Subscriptions")
}

enum class SortOption(val label: String) {
  DATE_DESC("Date (Newest)"),
  DATE_ASC("Date (Oldest)"),
  AMOUNT_DESC("Amount (Highest)"),
  AMOUNT_ASC("Amount (Lowest)"),
  NAME_ASC("Store (A-Z)")
}

data class DashboardStats(
  val monthlySubscriptionCost: Double = 0.0,
  val activeSubscriptionCount: Int = 0,
  val activeWarrantyCount: Int = 0,
  val expiringWarrantyCount: Int = 0,
  val expiredWarrantyCount: Int = 0,
  val totalVaultSpending: Double = 0.0,
  val upcomingRenewals: List<VaultItem> = emptyList(),
  val expiringWarranties: List<VaultItem> = emptyList(),
  val subscriptionCategorySlices: List<ChartSlice> = emptyList()
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = (application as PaperTrailApp).repository
  val authManager = (application as PaperTrailApp).authManager

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedTab = MutableStateFlow(VaultTab.ALL)
  val selectedTab: StateFlow<VaultTab> = _selectedTab.asStateFlow()

  private val _selectedCategory = MutableStateFlow<String?>(null)
  val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

  private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
  val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

  val allItems: StateFlow<List<VaultItem>> = repository.allItems.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  // Filtered and Sorted items for the Vault list
  val filteredItems: StateFlow<List<VaultItem>> = combine(
    allItems,
    _searchQuery,
    _selectedTab,
    _selectedCategory,
    _sortOption
  ) { items, query, tab, category, sort ->
    val now = System.currentTimeMillis()
    var filtered = items

    // 1. Tab filter
    filtered = when (tab) {
      VaultTab.ALL -> filtered
      VaultTab.RECEIPTS -> filtered.filter { !it.isSubscription }
      VaultTab.WARRANTIES -> filtered.filter { it.isWarranty }
      VaultTab.SUBSCRIPTIONS -> filtered.filter { it.isSubscription }
    }

    // 2. Category filter
    if (!category.isNullOrBlank() && category != "All") {
      filtered = filtered.filter { it.category.equals(category, ignoreCase = true) }
    }

    // 3. Search query filter
    if (query.isNotBlank()) {
      val q = query.trim().lowercase()
      filtered = filtered.filter { item ->
        item.storeName.lowercase().contains(q) ||
          item.category.lowercase().contains(q) ||
          (item.notes?.lowercase()?.contains(q) == true) ||
          (item.ocrRawText?.lowercase()?.contains(q) == true)
      }
    }

    // 4. Sort
    when (sort) {
      SortOption.DATE_DESC -> filtered.sortedByDescending { it.purchaseDate }
      SortOption.DATE_ASC -> filtered.sortedBy { it.purchaseDate }
      SortOption.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
      SortOption.AMOUNT_ASC -> filtered.sortedBy { it.amount }
      SortOption.NAME_ASC -> filtered.sortedBy { it.storeName.lowercase() }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  // Dashboard calculations
  val dashboardStats: StateFlow<DashboardStats> = allItems.combine(_selectedTab) { items, _ ->
    val now = System.currentTimeMillis()
    val fourteenDays = now + (14L * 24 * 60 * 60 * 1000)

    val subscriptions = items.filter { it.isSubscription && it.subscriptionActive }
    val monthlySubCost = subscriptions.sumOf { it.monthlyEquivalentCost }

    val warranties = items.filter { it.isWarranty && it.warrantyExpirationDate != null }
    val activeWarranties = warranties.filter { (it.warrantyExpirationDate ?: 0) > now }
    val expiringWarranties = warranties.filter {
      val exp = it.warrantyExpirationDate ?: 0
      exp in now..fourteenDays
    }.sortedBy { it.warrantyExpirationDate }
    val expiredWarranties = warranties.filter { (it.warrantyExpirationDate ?: 0) < now }

    val upcomingRenewals = subscriptions.filter {
      val renew = it.subscriptionNextRenewalDate ?: 0
      renew in now..fourteenDays
    }.sortedBy { it.subscriptionNextRenewalDate }

    val totalSpending = items.sumOf { it.amount }

    // Slices for Category Donut Chart
    val categoryTotals = subscriptions.groupBy { it.category }
      .mapValues { entry -> entry.value.sumOf { it.monthlyEquivalentCost } }
      .toList()
      .sortedByDescending { it.second }

    val slices = categoryTotals.mapIndexed { idx, (cat, amount) ->
      ChartSlice(
        label = cat,
        value = amount,
        color = DefaultChartPalette.getOrElse(idx % DefaultChartPalette.size) { DefaultChartPalette[0] }
      )
    }

    DashboardStats(
      monthlySubscriptionCost = monthlySubCost,
      activeSubscriptionCount = subscriptions.size,
      activeWarrantyCount = activeWarranties.size,
      expiringWarrantyCount = expiringWarranties.size,
      expiredWarrantyCount = expiredWarranties.size,
      totalVaultSpending = totalSpending,
      upcomingRenewals = upcomingRenewals,
      expiringWarranties = expiringWarranties,
      subscriptionCategorySlices = slices
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = DashboardStats()
  )

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setTab(tab: VaultTab) {
    _selectedTab.value = tab
  }

  fun setCategory(category: String?) {
    _selectedCategory.value = category
  }

  fun setSortOption(option: SortOption) {
    _sortOption.value = option
  }

  fun saveCapturedReceipt(
    bitmap: Bitmap?,
    storeName: String,
    amount: Double,
    currency: String = "$",
    category: String,
    purchaseDate: Long,
    rawText: String?,
    notes: String?,
    isWarranty: Boolean,
    warrantyExpirationDate: Long?,
    isSubscription: Boolean,
    subscriptionCycle: SubscriptionCycle?,
    subscriptionNextRenewalDate: Long?,
    reminderDays: Int,
    onSuccess: (Long) -> Unit
  ) {
    viewModelScope.launch {
      val id = repository.saveCapturedReceipt(
        bitmap = bitmap,
        storeName = storeName,
        amount = amount,
        currency = currency,
        category = category,
        purchaseDate = purchaseDate,
        rawText = rawText,
        notes = notes,
        isWarranty = isWarranty,
        warrantyExpirationDate = warrantyExpirationDate,
        isSubscription = isSubscription,
        subscriptionCycle = subscriptionCycle,
        subscriptionNextRenewalDate = subscriptionNextRenewalDate,
        reminderDays = reminderDays
      )
      onSuccess(id)
    }
  }

  fun updateItem(item: VaultItem) {
    viewModelScope.launch {
      repository.updateItem(item)
    }
  }

  fun deleteItem(item: VaultItem) {
    viewModelScope.launch {
      repository.deleteItem(item)
    }
  }

  fun toggleSubscriptionActive(item: VaultItem) {
    viewModelScope.launch {
      repository.updateItem(item.copy(subscriptionActive = !item.subscriptionActive))
    }
  }

  fun triggerReminderCheck() {
    ReminderScheduler.triggerImmediateCheck(getApplication())
  }
}
