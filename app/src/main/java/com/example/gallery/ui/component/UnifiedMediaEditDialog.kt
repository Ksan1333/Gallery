package com.example.gallery.ui.component

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMediaEditDialog(
    uris: List<String>,
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onSelectFolder: (() -> Unit)? = null, // 追加: フォルダ選択画面を開く
    initialFolderName: String = "" // 追加: 選択されたフォルダ名を受け取る
) {
    var targetFolderName by remember(initialFolderName) { mutableStateOf(initialFolderName) }
    var selectedAgeRating by remember { mutableStateOf<String?>(null) }
    val tagCounts by repository.getAllTagsWithCounts().collectAsState(initial = emptyList())
    val allTags = remember(tagCounts) { tagCounts.map { it.tag } }
    
    // 既存の全フォルダ名を取得（MediaDataのフォルダ名とメタデータのフォルダ名の両方を考慮）
    val allMetadata by repository.getAllMetadataFlow().collectAsState(initial = emptyList())
    // フォルダ移動のUIを簡略化したため existingFolders は不要になったが、他の用途があるかもしれないので一旦残すか、警告に従って消す
    // var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    
    /* 
    LaunchedEffect(allMetadata) {
        withContext(Dispatchers.IO) {
            val allMedia = repository.getAllMedia()
            val foldersFromMedia = allMedia.map { it.folderName }
            val foldersFromMeta = allMetadata.map { it.folderName }
            val uniqueFolders = (foldersFromMedia + foldersFromMeta)
                .filter { it.isNotBlank() && it != "Unknown" }
                .distinct()
                .sorted()
            withContext(Dispatchers.Main) {
                existingFolders = uniqueFolders
            }
        }
    }
    */

    val selectedTags = remember { mutableStateListOf<String>() }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 進捗管理用の状態
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // ダイアログ表示中もシステムバーを隠す設定を維持する
    LaunchedEffect(Unit) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isImmersive = !WindowInsetsCompat.toWindowInsetsCompat(window.decorView.rootWindowInsets).isVisible(WindowInsetsCompat.Type.statusBars())
        
        if (uris.size == 1 || isImmersive) {
             delay(50)
             insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 初期値のロード
    LaunchedEffect(uris) {
        if (uris.size == 1) {
            val meta = repository.getMetadata(uris[0])
            selectedAgeRating = meta?.ageRating ?: "SFW"
        } else {
            // 複数選択時は「変更なし」をデフォルトにするため null
            selectedAgeRating = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("タグ編集 (${uris.size} 件)")
            }
        },
        text = {
            Column {
                if (isProcessing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${(progress * 100).toInt()}% 更新中...", fontSize = 14.sp)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text("対象年齢", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(null to "変更なし", "SFW" to "健全", "R15" to "R-15", "R18" to "R-18").forEach { (code, label) ->
                                if (uris.size > 1 || code != null) {
                                    FilterChip(
                                        selected = selectedAgeRating == code,
                                        onClick = { if (!isProcessing) selectedAgeRating = code },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("フォルダ移動", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("移動先フォルダを選択してください", fontSize = 12.sp, color = Color.Gray)
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedCard(
                                onClick = { if (!isProcessing) onSelectFolder?.invoke() },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = Color.DarkGray.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.LightGray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (targetFolderName.isBlank()) "フォルダを選択..." else targetFolderName,
                                        color = if (targetFolderName.isBlank()) Color.Gray else Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (targetFolderName.isNotBlank()) {
                                        isProcessing = true
                                        scope.launch {
                                            uris.forEachIndexed { index, uri ->
                                                val current = repository.getMetadata(uri)
                                                repository.mediaDao.insertMetadata(
                                                    com.example.gallery.data.local.entity.MediaMetadataEntity(
                                                        uri = uri,
                                                        isFavorite = current?.isFavorite ?: false,
                                                        colorComposition = current?.colorComposition,
                                                        ageRating = current?.ageRating ?: "SFW",
                                                        isAiAnalyzed = current?.isAiAnalyzed ?: false,
                                                        folderName = targetFolderName
                                                    )
                                                )
                                                progress = (index + 1).toFloat() / uris.size
                                            }
                                            isProcessing = false
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = !isProcessing && targetFolderName.isNotBlank(),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("移動")
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("タグ設定", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newTagName,
                                onValueChange = { newTagName = it },
                                placeholder = { Text("新しいタグ") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = !isProcessing
                            )
                            IconButton(
                                onClick = {
                                    if (newTagName.isNotBlank()) {
                                        if (!selectedTags.contains(newTagName)) selectedTags.add(newTagName)
                                        newTagName = ""
                                    }
                                },
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "追加")
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
                                        onClick = { if (!isProcessing) selectedTags.remove(tag) },
                                        label = { Text(tag) },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        enabled = !isProcessing
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
                                            if (!isProcessing) {
                                                if (selectedTags.contains(tag)) selectedTags.remove(tag)
                                                else selectedTags.add(tag)
                                            }
                                        },
                                        label = { Text(tag) },
                                        enabled = !isProcessing
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    scope.launch {
                        val total = uris.size
                        // 1件ずつ更新して進捗を出す
                        uris.forEachIndexed { index, uri ->
                            repository.bulkUpdateAgeRating(listOf(uri), selectedAgeRating)
                            if (selectedTags.isNotEmpty()) {
                                repository.bulkAddTags(listOf(uri), selectedTags.toList())
                            }
                            progress = (index + 1).toFloat() / total
                            // 非常に速い場合にUIを更新させるためのわずかな遅延
                            if (total > 50) delay(1)
                        }
                        onDismiss()
                    }
                },
                enabled = !isProcessing
            ) {
                Text("適用")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("キャンセル")
            }
        }
    )
}
