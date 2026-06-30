package com.example.gallery.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.gallery.data.repository.GooglePhotoItem
import com.example.gallery.data.repository.GooglePhotosApiClient
import com.example.gallery.ui.state.GalleryState
import com.example.gallery.ui.theme.GalleryThemeTokens
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val GOOGLE_PHOTOS_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GooglePhotosScreen(
    galleryState: GalleryState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val apiClient = remember { GooglePhotosApiClient() }
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val scope = rememberCoroutineScope()
    val colors = GalleryThemeTokens.colors

    var accessToken by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<GooglePhotoItem>>(emptyList()) }
    var nextPageToken by remember { mutableStateOf<String?>(null) }
    var isAuthorizing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    fun loadPhotos(token: String, append: Boolean = false) {
        if (token.isBlank() || isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            runCatching {
                apiClient.listMediaItems(
                    accessToken = token,
                    pageToken = if (append) nextPageToken else null
                )
            }.onSuccess { page ->
                items = if (append) items + page.items else page.items
                nextPageToken = page.nextPageToken
            }.onFailure { error ->
                errorMessage = error.message ?: "Google Photos の取得に失敗しました"
            }
            isLoading = false
        }
    }

    fun handleAuthorizationResult(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            errorMessage = "Google Photos のアクセストークンを取得できませんでした"
            return
        }
        accessToken = token
        loadPhotos(token)
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isAuthorizing = false
        if (result.resultCode != Activity.RESULT_OK) {
            errorMessage = "Google アカウントの承認がキャンセルされました"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                authorizationClient.getAuthorizationResultFromIntent(result.data ?: Intent())
            }.onSuccess(::handleAuthorizationResult)
                .onFailure { error -> errorMessage = error.message ?: "Google アカウントの承認に失敗しました" }
        }
    }

    fun startAuthorization() {
        val currentActivity = activity
        if (currentActivity == null || isAuthorizing) return
        isAuthorizing = true
        errorMessage = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GOOGLE_PHOTOS_SCOPE)))
            .build()
        scope.launch {
            runCatching {
                authorizationClient.authorize(request).await()
            }.onSuccess { result ->
                if (result.hasResolution()) {
                    val requestSender = IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build()
                    authorizationLauncher.launch(requestSender)
                } else {
                    isAuthorizing = false
                    handleAuthorizationResult(result)
                }
            }.onFailure { error ->
                isAuthorizing = false
                errorMessage = error.message ?: "Google アカウントの承認に失敗しました"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Scaffold(
            containerColor = colors.background,
            topBar = {
                TopAppBar(
                    title = { Text("Google Photos", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { accessToken?.let { loadPhotos(it) } },
                            enabled = accessToken != null && !isLoading && !isAuthorizing
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "再読み込み")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = ::startAuthorization,
                        enabled = !isAuthorizing && !isLoading
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Text("Googleでログイン", modifier = Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = {
                        accessToken = null
                        items = emptyList()
                        nextPageToken = null
                        errorMessage = null
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Text("解除", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                Text(
                    text = "Google アカウントで承認すると、Photos Library API から取得できる写真と動画をアプリ内に表示します。",
                    color = colors.secondaryText,
                    modifier = Modifier.padding(top = 8.dp)
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = colors.danger,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        GooglePhotoGridItem(item = item) {
                            selectedIndex = index
                        }
                    }
                    if (nextPageToken != null && accessToken != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(112.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, colors.divider, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isLoading) { loadPhotos(accessToken!!, append = true) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("さらに取得", color = colors.accent, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (isAuthorizing || isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        }

        selectedIndex?.let { initialPage ->
            MediaViewerScreen(
                imageList = items.map { it.mediaData },
                initialPage = initialPage,
                onClickedClose = { selectedIndex = null },
                galleryState = galleryState,
                showDeleteButton = false,
                keepNavigationBarsHidden = true
            )
        }
    }
}

@Composable
private fun GooglePhotoGridItem(
    item: GooglePhotoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .height(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(context)
                    .data(item.thumbnailUrl)
                    .crossfade(false)
                    .build()
            ),
            contentDescription = item.mediaData.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.mediaData.isVideo) {
            Text(
                text = "VIDEO",
                color = Color.White,
                fontSize = com.example.gallery.ui.AppConstants.TinyFontSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
    }
}
