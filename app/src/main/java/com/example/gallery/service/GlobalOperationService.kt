package com.example.gallery.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object GlobalOperationService {
    private val _progress = MutableStateFlow(0f)
    val progress = _progress.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _statusTitle = MutableStateFlow("")
    val statusTitle = _statusTitle.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText = _statusText.asStateFlow()

    private val _operationTag = MutableStateFlow<String?>(null)
    val operationTag = _operationTag.asStateFlow()

    fun startOperation(title: String, tag: String? = null) {
        _statusTitle.value = title
        _statusText.value = ""
        _progress.value = 0f
        _operationTag.value = tag
        _isProcessing.value = true
    }

    fun updateProgress(current: Float, text: String = "") {
        _progress.value = current
        if (text.isNotEmpty()) {
            _statusText.value = text
        }
    }

    fun finishOperation() {
        _isProcessing.value = false
        _progress.value = 1.0f
        _operationTag.value = null
    }
}
