package com.mfp.filemanager.data

data class CategoryItem(
    val id: String,
    val label: String,
    val storageUsed: Long,
    val progress: Float,
    val storageUsedReadable : String
)