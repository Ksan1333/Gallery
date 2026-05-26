package com.example.gallery.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class OperationState(
    val id: String,
    val title: String,
    val text: String = "",
    val progress: Float = 0f,
    val tag: String? = null,
    val isCancelRequested: Boolean = false,
    val canCancel: Boolean = true,
    val targetPeriodDays: Int = -1
)

object GlobalOperationService {
    private val _operationsMap = MutableStateFlow<Map<String, OperationState>>(emptyMap())
    val operations = _operationsMap.map { it.values.toList() }

    // 下位互換性のための StateFlow (最初のオペレーションを返す)
    val isProcessing = _operationsMap.map { it.isNotEmpty() }
    val progress = _operationsMap.map { it.values.firstOrNull()?.progress ?: 0f }
    val statusTitle = _operationsMap.map { it.values.firstOrNull()?.title ?: "" }
    val statusText = _operationsMap.map { it.values.firstOrNull()?.text ?: "" }
    val operationTag = _operationsMap.map { it.values.firstOrNull()?.tag }
    val isCancelRequested = _operationsMap.map { it.values.firstOrNull()?.isCancelRequested ?: false }
    val targetPeriodDays = _operationsMap.map { it.values.firstOrNull()?.targetPeriodDays ?: -1 }

    // 現在進行中の「最新」または「唯一」のIDを保持（タグなし呼び出し用）
    private var lastOperationId: String? = null

    fun startOperation(title: String, tag: String? = null, periodDays: Int = -1, canCancel: Boolean = true): String {
        val id = tag ?: UUID.randomUUID().toString()
        val newState = OperationState(
            id = id,
            title = title,
            tag = tag,
            targetPeriodDays = periodDays,
            canCancel = canCancel
        )
        _operationsMap.value = _operationsMap.value + (id to newState)
        lastOperationId = id
        return id
    }

    fun requestCancel(id: String? = null) {
        val targetId = id ?: lastOperationId ?: return
        val current = _operationsMap.value[targetId] ?: return
        _operationsMap.value = _operationsMap.value + (targetId to current.copy(isCancelRequested = true))
    }

    fun updateProgress(current: Float, text: String = "", id: String? = null) {
        val targetId = id ?: lastOperationId ?: return
        val op = _operationsMap.value[targetId] ?: return
        _operationsMap.value = _operationsMap.value + (targetId to op.copy(
            progress = current,
            text = if (text.isNotEmpty()) text else op.text
        ))
    }

    fun finishOperation(id: String? = null) {
        val targetId = id ?: lastOperationId ?: return
        _operationsMap.value = _operationsMap.value - targetId
        if (lastOperationId == targetId) {
            lastOperationId = _operationsMap.value.keys.lastOrNull()
        }
    }

    /**
     * 指定したIDのオペレーションがキャンセルされているかどうかを同期的に確認する
     */
    fun isCanceled(id: String?): Boolean {
        val targetId = id ?: lastOperationId ?: return false
        return _operationsMap.value[targetId]?.isCancelRequested ?: false
    }

    /**
     * 指定したタグのオペレーションが現在進行中かどうかをFlowで取得する
     */
    fun isProcessing(tag: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return _operationsMap.map { it.values.any { op -> op.tag == tag || op.id == tag } }
    }

    /**
     * 指定したタグのオペレーションがキャンセル要請されたかをFlowで取得する
     */
    fun isCancelRequested(tag: String): kotlinx.coroutines.flow.Flow<Boolean> {
        return _operationsMap.map { it.values.find { op -> op.tag == tag || op.id == tag }?.isCancelRequested ?: false }
    }
}
