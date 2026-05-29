package dev.auriya.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.topjohnwu.superuser.Shell

class AuriyaApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        // Configure the global libsu Shell BEFORE super.onCreate so any
        // early IO that touches /data/adb/* uses the cached root shell.
        // FLAG_MOUNT_MASTER ensures we see the real filesystem (not the
        // module's overlay) when reading config/log paths.
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
        super.onCreate()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
    }
}
