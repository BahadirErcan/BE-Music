package com.be.music.youtube

object DownloadFormatResolver {
    fun resolveVideoFormat(type: String, quality: String): String {
        if (type == "m4a") {
            return "audio"
        }

        val normalized = quality.trim().lowercase().replace(" ", "").replace("_", "")
        val isHighestQuality = normalized == "best" || normalized == "highest" || normalized == "enyüksek" || normalized == "enyüksek"
        if (isHighestQuality) {
            return "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
        }

        val requestedHeight = normalized.removeSuffix("p").toIntOrNull() ?: 720
        return (
            "bestvideo[height>=${requestedHeight}][ext=mp4]+bestaudio[ext=m4a]/" +
                "bestvideo[height<${requestedHeight}][ext=mp4]+bestaudio[ext=m4a]/" +
                "best[ext=mp4]"
        )
    }
}
