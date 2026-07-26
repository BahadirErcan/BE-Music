package com.be.music.ui

import android.Manifest

object DownloadPermissionHelper {
    fun requiredPermissions(apiLevel: Int, type: String): Array<String> {
        return if (apiLevel >= 33) {
            buildList {
                add(Manifest.permission.POST_NOTIFICATIONS)
                if (type == "m4a") {
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }.toTypedArray()
        } else {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}
