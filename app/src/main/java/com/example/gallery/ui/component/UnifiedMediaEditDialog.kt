package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.gallery.data.repository.MediaRepository
import com.example.gallery.ui.AppConstants
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
    var selectedAgeRating by rememberSaveable { mutableStateOf<String?>(null) }
    val tagCounts by repository.getAllTagsWithCounts().collectAsState(initial = emptyList())
    val allTags = remember(tagCounts) { tagCounts.map { it.tag } }

    // 既存の全フォルダ名を取得（MediaDataのフォルダ名とメタデータのフォルダ名の両方を考慮）
    val allMetadata by repository.getAllMetadataFlow().collectAsState(initial = emptyList())
    // フォルダ移動のUIを簡略化したため existingFolders は不要になった

    val selectedTags = remember { mutableStateListOf<String>() }
    var newTagName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 進捗管理用の状態
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // 初期値のロード
    LaunchedEffect(uris) {
        if (uris.size == 1) {
            val meta = repository.getMetadata(uris[0])
            selectedAgeRating = meta?.ageRating ?: "SFW"
        } else {
            // 複数選択時は「変更なし」をデフォルトにするため null
            if (selectedAgeRating == null) selectedAgeRating = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("一括編集 (${uris.size} 件)", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !isProcessing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val total = uris.size

                                // フォルダ移動の処理 (フォルダが指定されている場合)
                                if (targetFolderName.isNotBlank()) {
                                    repository.moveMediaToFolder(uris, targetFolderName)
                                }

                                uris.forEachIndexed { index, uri ->
                                    repository.bulkUpdateAgeRating(listOf(uri), selectedAgeRating)
                                    if (selectedTags.isNotEmpty()) {
                                        repository.bulkAddTags(listOf(uri), selectedTags.toList())
                                    }
                                    progress = (index + 1).toFloat() / total
                                    if (total > 50) delay(1)
                                }
                                onDismiss()
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("保存", color = if (!isProcessing) Color.White else Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = AppConstants.BackgroundColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 選択中の画像プレビュー (縦2列の横並び: LazyVerticalGridを使用して高さ固定)
            if (!isProcessing && uris.isNotEmpty()) {
                Text(
                    text = "選択中のアイテム",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 2つずつペアにして表示することで縦2段を実現
                    val pairs = uris.chunked(2)
                    items(pairs) { pair ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            pair.forEach { uri ->
                                AsyncImage(
                                    model = remember(uri) {
                                        ImageRequest.Builder(context)
                                            .data(uri)
                                            .videoFrameMillis(1000)
                                            .crossfade(true)
                                            .build()
                                    },
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(88.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // 奇数個の場合に高さを合わせるための空き
                            if (pair.size < 2) {
                                Spacer(modifier = Modifier.size(88.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                    Text("${(progress * 100).toInt()}% 更新中...", fontSize = 14.sp, color = Color.White)
                }
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                item {
                    Text("対象年齢", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
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
                                        selectedLabelColor = Color.White,
                                        labelColor = Color.Gray
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("フォルダ移動", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
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
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("タグ設定", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)

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
                                    keyboardController?.hide()
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "追加", tint = Color.White)
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
                                        enabled = !isProcessing,
                                        colors = FilterChipDefaults.filterChipColors(
                                            labelColor = Color.White,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("その他", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White)
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                repository.moveToTrash(uris)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("ゴミ箱へ移動")
                    }
                }
            }
        }
    }
}
