package com.example.gallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class TutorialPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val pages = listOf(
        TutorialPage(
            "ようこそ！",
            "このアプリでは、大量の画像や動画を快適に管理できます。\nまずは基本的な使い方を覚えましょう。",
            Icons.Default.Home,
            Color(0xFF4CAF50)
        ),
        TutorialPage(
            "一括操作",
            "アイテムを長押しすると選択モードになります。\n複数のアイテムを選択して、一括でタグ付けや移動が可能です。",
            Icons.Default.CheckCircle,
            Color(0xFF2196F3)
        ),
        TutorialPage(
            "タグとフィルタ",
            "アイテムにタグを付けることで、後から簡単に検索できます。\n上部のフィルタバーで特定の条件のアイテムだけを表示できます。",
            Icons.AutoMirrored.Filled.Label,
            Color(0xFFFF9800)
        ),
        TutorialPage(
            "プライバシー管理",
            "対象年齢（SFW, R15, R18）を設定して、表示を切り替えることができます。\n大事なデータはしっかり管理しましょう。",
            Icons.Default.Security,
            Color(0xFFE91E63)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(500.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(page.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = page.color
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = page.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = page.description,
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // インジケーター
                Row(
                    Modifier
                        .height(8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pages.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.White else Color.DarkGray
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("スキップ", color = Color.Gray)
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = pages[pagerState.currentPage].color)
                    ) {
                        Text(if (pagerState.currentPage == pages.size - 1) "はじめる" else "次へ")
                    }
                }
            }
        }
    }
}
