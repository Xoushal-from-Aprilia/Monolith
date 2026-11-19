package com.monolith.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache

class MonolithApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(64L * 1024 * 1024).build() }
        .build()
}
