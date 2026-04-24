package com.meshrelief.features.map

import android.content.Context
import com.meshrelief.core.util.Constants
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val MIN_ZOOM = 10
        private const val MAX_ZOOM = 17

        // Bounding box delta around user location (roughly ~30 km box)
        private const val BBOX_DEGREES = 0.27
    }

    /**
     * Pre-fetches OSMDroid tiles for zoom levels [MIN_ZOOM.MAX_ZOOM] in a bounding box
     * centred on [centre]. Emits progress as 0–100 Int.
     * On completion calls userPreferences.setMapTilesDownloaded(true).
     */
    fun downloadTiles(centre: GeoPoint): Flow<Int> = flow {
        val tileSource = TileSourceFactory.MAPNIK
        val cacheDir = File(context.filesDir, "osmdroid/tiles/${tileSource.name()}")
        cacheDir.mkdirs()

        val bbox = BoundingBox(
            centre.latitude  + BBOX_DEGREES,
            centre.longitude + BBOX_DEGREES,
            centre.latitude  - BBOX_DEGREES,
            centre.longitude - BBOX_DEGREES
        )

        // Collect all tile coordinates we need to fetch
        data class TileCoord(val zoom: Int, val x: Int, val y: Int)

        val allTiles = mutableListOf<TileCoord>()
        for (zoom in MIN_ZOOM..MAX_ZOOM) {
            val minX = lonToTileX(bbox.lonWest,  zoom)
            val maxX = lonToTileX(bbox.lonEast,  zoom)
            val minY = latToTileY(bbox.latNorth, zoom)
            val maxY = latToTileY(bbox.latSouth, zoom)
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    allTiles += TileCoord(zoom, x, y)
                }
            }
        }

        val total = allTiles.size.coerceAtLeast(1)
        var done  = 0

        emit(0)

        for (tile in allTiles) {
            val tileFile = File(cacheDir, "${tile.zoom}/${tile.x}/${tile.y}.png")
            if (!tileFile.exists()) {
                tileFile.parentFile?.mkdirs()
                try {
                    val url  = tileSource.getTileURLString(
                        MapTileIndex.getTileIndex(tile.zoom, tile.x, tile.y)
                    )
                    withContext(Dispatchers.IO) {
                        URL(url).openStream().use { input ->
                            tileFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Skip unavailable tiles; don't abort the whole download
                }
            }

            done++
            val progress = ((done.toFloat() / total) * 100).toInt().coerceIn(0, 100)
            emit(progress)
        }

        userPreferences.setMapTilesDownloaded(true)
    }.flowOn(Dispatchers.IO)

    // ── Slippy-map tile maths ─────────────────────────────────────────────────

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = Math.pow(2.0, zoom.toDouble())
        return ((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, (n - 1).toInt())
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val n    = Math.pow(2.0, zoom.toDouble())
        val latR = Math.toRadians(lat)
        return ((1.0 - Math.log(Math.tan(latR) + 1.0 / Math.cos(latR)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, (n - 1).toInt())
    }
}