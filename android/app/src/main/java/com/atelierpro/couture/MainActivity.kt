package com.atelierpro.couture

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.fragment.app.FragmentActivity
import com.atelierpro.couture.ui.AtelierApp
import com.atelierpro.couture.ui.AtelierViewModel
import com.atelierpro.couture.ui.theme.AtelierProTheme

class MainActivity : FragmentActivity() {
    private val viewModel: AtelierViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            AtelierProTheme {
                AtelierApp(
                    viewModel = viewModel,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }
    }
}
