package com.netmaster.pro.presentation.wifiheatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiHeatmapScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خريطة قوة Wi-Fi") },
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
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text  = "تحرك حول المنزل لتسجيل قوة الإشارة وبناء الخريطة الحرارية",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            // Heat-map canvas placeholder
            Card(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Simulated radial gradient heat zones
                        val center1 = Offset(size.width * 0.35f, size.height * 0.4f)
                        val center2 = Offset(size.width * 0.7f, size.height * 0.6f)

                        drawCircle(
                            brush  = Brush.radialGradient(
                                colors = listOf(Color(0x9900C853), Color.Transparent),
                                center = center1,
                                radius = size.minDimension * 0.35f
                            ),
                            center = center1,
                            radius = size.minDimension * 0.35f
                        )
                        drawCircle(
                            brush  = Brush.radialGradient(
                                colors = listOf(Color(0x99FFAB00), Color.Transparent),
                                center = center2,
                                radius = size.minDimension * 0.25f
                            ),
                            center = center2,
                            radius = size.minDimension * 0.25f
                        )
                    }
                    Text(
                        text      = "🗺️\nابدأ المسح للحصول على خريطة حرارية حقيقية",
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Legend
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("مفتاح الألوان", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("🟢 إشارة قوية (> -60 dBm)",  style = MaterialTheme.typography.bodySmall)
                    Text("🟡 إشارة متوسطة (-60 إلى -75 dBm)", style = MaterialTheme.typography.bodySmall)
                    Text("🔴 إشارة ضعيفة (< -75 dBm)",  style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
