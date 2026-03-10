package com.unischeduler.presentation.availability

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen() {
    // Sadece UI prototipi olarak hazırlıyorum.
    // İleriki adımda Supabase ile ViewModel bağlamasını yapacağız.
    val days = listOf("Pzt", "Sal", "Çar", "Per", "Cum")
    val hours = (9..17).map { "$it:00" }

    // Gecici mock state (Hangi saat dilimi dolu/boş kontrolü için)
    // Map of "dayIndex_timeIndex" to isAvailable
    var gridState by remember { mutableStateOf(mutableMapOf<String, Boolean>()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Haftalık Müsaitlik Durumum") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Lütfen ders veremeyeceğiniz (müsait olmadığınız) saatleri kırmızı yapın. Diğer saatler yeşil kalabilir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Grid Headers (Günler)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, bottom = 4.dp, top = 8.dp)
            ) {
                days.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Grid Content (Saatler ve Hücreler)
            LazyVerticalGrid(
                columns = GridCells.Fixed(6), // 1 sütün saat, 5 sütun günler
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                hours.forEachIndexed { hourIndex, hour ->
                    // İlk sütun: Saat metni
                    item {
                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hour,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sonraki 5 sütun: Günlerin kutucukları
                    items(5) { dayIndex ->
                        val key = "${dayIndex}_${hourIndex}"
                        // Varsayılan true (müsait) kabul ediyoruz
                        val isAvailable = gridState[key] ?: true

                        Box(
                            modifier = Modifier
                                .height(40.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(
                                    if (isAvailable)
                                        Color(0xFF81C784) // Soft Yeşil
                                    else
                                        Color(0xFFE57373) // Soft Kırmızı
                                )
                                .clickable {
                                    val newMap = gridState.toMutableMap()
                                    newMap[key] = !isAvailable
                                    gridState = newMap
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if(isAvailable) "Uygun" else "Dolu",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
