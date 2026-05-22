package com.example.gallery.ui.component

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.gallery.data.repository.BookData
import com.example.gallery.data.repository.BookRepository
import com.example.gallery.data.repository.BookType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookViewer(
    book: BookData,
    repository: BookRepository,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var screenOrientation by rememberSaveable { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) }
    val isLandscape = screenOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    val isTwoPageMode = isLandscape // 横向きなら2枚表示

    val spreadCount = if (isTwoPageMode) (book.pageCount + 1) / 2 else book.pageCount
    var currentAbsolutePage by rememberSaveable { mutableIntStateOf(0) }
    
    val pagerState = rememberPagerState(
        initialPage = if (isTwoPageMode) currentAbsolutePage / 2 else currentAbsolutePage,
        pageCount = { spreadCount }
    )
    
    LaunchedEffect(isTwoPageMode) {
        val targetSpread = if (isTwoPageMode) currentAbsolutePage / 2 else currentAbsolutePage
        if (pagerState.currentPage != targetSpread) {
            pagerState.scrollToPage(targetSpread)
        }
    }

    LaunchedEffect(pagerState.currentPage, isTwoPageMode) {
        currentAbsolutePage = if (isTwoPageMode) pagerState.currentPage * 2 else pagerState.currentPage
    }
    
    var isUiVisible by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    val window = (context as? Activity)?.window
    val insetsController = remember(window) {
        window?.let { WindowCompat.getInsetsController(it, it.decorView) }
    }

    LaunchedEffect(isUiVisible, insetsController, screenOrientation) {
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!isUiVisible) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    SideEffect {
        if (screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            (context as? Activity)?.requestedOrientation = screenOrientation
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler { onClose() }

    val pageCache = remember { mutableStateMapOf<Int, Bitmap?>() }

    LaunchedEffect(currentAbsolutePage) {
        if (!isSeeking) {
            seekValue = (book.pageCount - 1 - currentAbsolutePage).toFloat()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onGloballyPositioned { viewSize = it.size }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            beyondViewportPageCount = 1,
            reverseLayout = true,
            userScrollEnabled = scale <= 1.01f
        ) { spreadIndex ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (isTwoPageMode) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val rightIdx = spreadIndex * 2
                        val leftIdx = rightIdx + 1

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val bmp = pageCache[rightIdx]
                            LaunchedEffect(rightIdx) {
                                if (pageCache[rightIdx] == null) {
                                    Log.d("BookViewer", "Loading right page: $rightIdx")
                                    val loaded = if (book.type == BookType.ZIP) 
                                        repository.getZipPage(book.path, rightIdx) else repository.getPdfPage(book.path, rightIdx)
                                    pageCache[rightIdx] = loaded
                                }
                            }
                            if (bmp != null) {
                                Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
                            } else {
                                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                            }
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (leftIdx < book.pageCount) {
                                val bmp = pageCache[leftIdx]
                                LaunchedEffect(leftIdx) {
                                    if (pageCache[leftIdx] == null) {
                                        Log.d("BookViewer", "Loading left page: $leftIdx")
                                        val loaded = if (book.type == BookType.ZIP) 
                                            repository.getZipPage(book.path, leftIdx) else repository.getPdfPage(book.path, leftIdx)
                                        pageCache[leftIdx] = loaded
                                    }
                                }
                                if (bmp != null) {
                                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, alignment = Alignment.CenterEnd)
                                } else {
                                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    val bmp = pageCache[spreadIndex]
                    LaunchedEffect(spreadIndex) {
                        if (pageCache[spreadIndex] == null) {
                            Log.d("BookViewer", "Loading single page: $spreadIndex")
                            val loaded = if (book.type == BookType.ZIP) 
                                repository.getZipPage(book.path, spreadIndex) else repository.getPdfPage(book.path, spreadIndex)
                            pageCache[spreadIndex] = loaded
                        }
                    }
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    }
                }

                // Tap Areas (Inside Page to not block swiping)
                if (scale <= 1.01f) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxHeight().weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Color.White.copy(alpha = 0.2f))) {
                            if (pagerState.currentPage < pagerState.pageCount - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        })
                        Box(Modifier.fillMaxHeight().weight(1f).pointerInput(Unit) { detectTapGestures { isUiVisible = !isUiVisible } })
                        Box(Modifier.fillMaxHeight().weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = Color.White.copy(alpha = 0.2f))) {
                            if (pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        })
                    }
                }
            }
        }

        if (isUiVisible) {
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).height(60.dp).padding(horizontal = 8.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Default.Close, null, tint = Color.White) }
                    Text(book.title, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(horizontal = 80.dp), maxLines = 1, fontSize = 14.sp)
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = {
                            val bitmap = captureViewToBitmap(context, isTwoPageMode, book.pageCount, pagerState.currentPage, pageCache, viewSize)
                            if (bitmap != null) saveBitmapToGallery(context, bitmap) else Toast.makeText(context, "保存失敗", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.Screenshot, null, tint = Color.White) }
                        IconButton(onClick = { screenOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) { Icon(Icons.Default.ScreenRotation, null, tint = Color.White) }
                    }
                }
            }
            Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentAbsolutePage + 1} / ${book.pageCount}", color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = seekValue,
                            onValueChange = { 
                                seekValue = it
                                isSeeking = true
                                val p = (book.pageCount - 1) - it.toInt()
                                scope.launch { previewBitmap = if (book.type == BookType.ZIP) repository.getZipPage(book.path, p) else repository.getPdfPage(book.path, p) }
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                val target = (book.pageCount - 1) - seekValue.toInt()
                                scope.launch { pagerState.scrollToPage(if (isTwoPageMode) target / 2 else target) }
                            },
                            valueRange = 0f..(book.pageCount - 1).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(activeTrackColor = Color.White.copy(alpha = 0.3f), inactiveTrackColor = Color.White.copy(alpha = 0.3f), thumbColor = Color.White)
                        )
                    }
                }
            }
            if (isSeeking && previewBitmap != null) {
                Card(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp).size(160.dp, 240.dp), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    Image(previewBitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

private fun captureViewToBitmap(context: Context, isTwoPageMode: Boolean, pageCount: Int, spreadIdx: Int, cache: Map<Int, Bitmap?>, viewSize: IntSize): Bitmap? {
    if (viewSize.width <= 0 || viewSize.height <= 0) return null
    val rects = mutableListOf<RectF>()
    if (isTwoPageMode) {
        val rIdx = spreadIdx * 2; val lIdx = rIdx + 1; val halfW = viewSize.width / 2f
        cache[rIdx]?.let { rects.add(getFitRect(it, halfW, viewSize.height.toFloat(), halfW, 0f)) }
        if (lIdx < pageCount) cache[lIdx]?.let { rects.add(getFitRect(it, halfW, viewSize.height.toFloat(), 0f, 0f)) }
    } else { cache[spreadIdx]?.let { rects.add(getFitRect(it, viewSize.width.toFloat(), viewSize.height.toFloat(), 0f, 0f)) } }
    if (rects.isEmpty()) return null
    val union = RectF(rects[0]); rects.forEach { union.union(it) }
    val result = Bitmap.createBitmap(union.width().toInt().coerceAtLeast(1), union.height().toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result); canvas.drawColor(android.graphics.Color.BLACK)
    canvas.translate(-union.left, -union.top)
    if (isTwoPageMode) {
        val rIdx = spreadIdx * 2; val lIdx = rIdx + 1; val halfW = viewSize.width / 2f
        cache[rIdx]?.let { drawFit(canvas, it, halfW, viewSize.height.toFloat(), halfW, 0f) }
        if (lIdx < pageCount) cache[lIdx]?.let { drawFit(canvas, it, halfW, viewSize.height.toFloat(), 0f, 0f) }
    } else { cache[spreadIdx]?.let { drawFit(canvas, it, viewSize.width.toFloat(), viewSize.height.toFloat(), 0f, 0f) } }
    return result
}

private fun getFitRect(bmp: Bitmap, tw: Float, th: Float, x: Float, y: Float): RectF {
    val s = Math.min(tw / bmp.width, th / bmp.height); val dw = bmp.width * s; val dh = bmp.height * s
    val dx = x + (tw - dw) / 2f; val dy = y + (th - dh) / 2f
    return RectF(dx, dy, dx + dw, dy + dh)
}

private fun drawFit(canvas: Canvas, bmp: Bitmap, tw: Float, th: Float, x: Float, y: Float) {
    val s = Math.min(tw / bmp.width, th / bmp.height); val dw = bmp.width * s; val dh = bmp.height * s
    val dx = x + (tw - dw) / 2f; val dy = y + (th - dh) / 2f
    canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "BookScreenshot_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    try {
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear(); contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) { Toast.makeText(context, "失敗", Toast.LENGTH_SHORT).show() }
}
