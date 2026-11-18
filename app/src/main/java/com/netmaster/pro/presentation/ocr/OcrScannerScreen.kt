package com.netmaster.pro.presentation.ocr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ماسح الكاميرا OCR") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier         = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📸", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = "وجّه الكاميرا نحو ملصق الراوتر\nلاستخراج معلومات الاتصال تلقائياً\nالميزة قيد التطوير",
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
