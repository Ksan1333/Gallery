package com.example.gallery.service

fun selectRepresentativeProgressOperation(operations: List<OperationState>): OperationState? {
    return operations.maxWithOrNull(
        compareBy<OperationState> { it.progress }
            .thenBy { it.title }
            .thenBy { it.id }
    )
}
