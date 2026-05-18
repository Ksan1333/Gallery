package com.example.gallery.ui.component

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import com.example.gallery.ui.component.TooltipWrapper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.gallery.data.repository.MediaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMediaEditDialog(
    uris: List<String>,
    repository: MediaRepository,
    onDismiss: () -> Unit
) {
    var selectedAgeRating by remember { mutableStateOf("SFW") }
    val tagCounts by repository.getAllTagsWithCounts().collectAsState(initial = emptyList())
    val allTags = remember(tagCounts) { tagCounts.map { it.tag } }
    val selectedTags = remember { mutableStateListOf<String>() }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // ダイアログ表示中もシステムバーを隠す設定を維持する（ビュワーからの呼び出し時のみ）
    LaunchedEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 現在没入モード中かどうかを判定
        val isImmersive = !WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.statusBars())
        
        // ビュワー（単一選択）または、すでに没入モード中の場合のみ、ダイアログ表示時に再度隠す
        if (uris.size == 1 || isImmersive) {
             delay(50)
             insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 初期値のロード
    LaunchedEffect(uris) {
        if (uris.size == 1) {
            val meta = repository.getMetadata(uris[0])
            meta?.ageRating?.let { selectedAgeRating = it }
        } else {
            selectedAgeRating = "SFW" // 複数選択時は基本「健全」に合わせる
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("タグ編集 (${uris.size} 件)")
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                item {
                    Text("対象年齢", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SFW" to "健全", "R15" to "R-15", "R18" to "R-18").forEach { (code, label) ->
                            FilterChip(
                                selected = selectedAgeRating == code,
                                onClick = { selectedAgeRating = code },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("タグ設定", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    
                    // 新規タグ入力
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            placeholder = { Text("新しいタグ") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        TooltipWrapper("新規タグをリストに追加") {
                            IconButton(onClick = {
                                if (newTagName.isNotBlank()) {
                                    if (!selectedTags.contains(newTagName)) selectedTags.add(newTagName)
                                    newTagName = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "追加")
                            }
                        }
                    }
                    
                    if (selectedTags.isNotEmpty()) {
                        Text("追加するタグ:", fontSize = 12.sp, color = Color.Gray)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { selectedTags.remove(tag) },
                                    label = { Text(tag) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                    
                    val existingTags = allTags.filter { !it.endsWith("系") }
                    if (existingTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("既存のタグから選択:", fontSize = 12.sp, color = Color.Gray)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            existingTags.forEach { tag ->
                                FilterChip(
                                    selected = selectedTags.contains(tag),
                                    onClick = {
                                        if (selectedTags.contains(tag)) selectedTags.remove(tag)
                                        else selectedTags.add(tag)
                                    },
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    repository.bulkUpdateAgeRating(uris, selectedAgeRating)
                    if (selectedTags.isNotEmpty()) {
                        repository.bulkAddTags(uris, selectedTags.toList())
                    }
                    onDismiss()
                }
            }) {
                Text("適用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
