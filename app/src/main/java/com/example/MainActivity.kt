package com.example

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import com.example.ui.navigation.PaperTrailAppContent
import com.example.ui.screens.vault.VaultViewModel
import com.example.ui.theme.PaperTrailTheme

class MainActivity : FragmentActivity() {
  private val viewModel: VaultViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      PaperTrailTheme {
        PaperTrailAppContent(viewModel = viewModel)
      }
    }
  }
}
