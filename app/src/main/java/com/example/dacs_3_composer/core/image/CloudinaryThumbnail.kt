package com.example.dacs_3_composer.core.image

/** Adds delivery-side resizing only to Cloudinary upload URLs; other providers pass through. */
fun cloudinaryThumbnail(url: String, widthPx: Int): String {
    if (!url.contains("res.cloudinary.com/") || "/image/upload/" !in url) return url
    val marker = "/image/upload/"
    val position = url.indexOf(marker) + marker.length
    val tail = url.substring(position)
    if (tail.substringBefore('/').contains(',')) return url
    return url.substring(0, position) + "f_auto,q_auto,w_${widthPx.coerceIn(96, 1600)},c_limit/" + tail
}
