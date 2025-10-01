package com.example.groviq.backEnd.dataStructures

data class ViewFolder(
    val uri: String,
    val displayName: String,
    val progressOfLoading : Float = 0f
)