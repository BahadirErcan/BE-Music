package com.be.music.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    val app_name: String,
    val package_name: String,
    val latest_version_name: String,
    val latest_version_code: Int,
    val min_supported_version_code: Int,
    val download_url: String,
    val changelog: String,
    val file_size: String,
    val release_date: String,
    val mandatory: Boolean,
    val sha256: String
)
