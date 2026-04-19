package com.meshrelief.features.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshrelief.core.crypto.DeviceIdentity
import com.meshrelief.core.util.Constants
import com.meshrelief.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import java.io.File

data class SetupUiState(
    val name: String = "",
    val phone: String = "",
    val language: String = "EN",
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val nameError: String? = null,
    val phoneError: String? = null
)

data class TileDownloadUiState(
    val isDownloading: Boolean = false,
    val isDone: Boolean        = false,
    val progress: Float        = 0f,       // 0.0 – 1.0
    val error: String?         = null
)

private data class TileBounds(
    val xMin: Int,
    val xMax: Int,
    val yMin: Int,
    val yMax: Int
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val deviceIdentity: DeviceIdentity,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState

    private val _tileDownloadState = MutableStateFlow(TileDownloadUiState())
    val tileDownloadState: StateFlow<TileDownloadUiState> = _tileDownloadState.asStateFlow()

    fun onNameChange(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null
        )
    }

    fun onPhoneChange(phone: String) {
        _uiState.value = _uiState.value.copy(
            phone = phone,
            phoneError = null
        )
    }

    fun onLanguageChange(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun onSubmit() {
        val state = _uiState.value

        val nameError = when {
            state.name.isBlank()      -> "Name is required"
            state.name.length < 2     -> "Name must be at least 2 characters"
            state.name.length > 50    -> "Name must be less than 50 characters"
            else                      -> null
        }
        val phoneError = when {
            state.phone.isBlank()               -> "Phone number is required"
            state.phone.length != 10            -> "Enter a valid 10-digit number"
            !state.phone.all { it.isDigit() }   -> "Only digits allowed"
            else                                -> null
        }

        if (nameError != null || phoneError != null) {
            _uiState.value = _uiState.value.copy(
                nameError = nameError,
                phoneError = phoneError
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val deviceId = deviceIdentity.generateDeviceId(state.phone)

            userPreferences.saveUserName(state.name.trim())
            userPreferences.saveUserPhone(state.phone.trim())
            userPreferences.saveDeviceId(deviceId)
            userPreferences.saveLanguage(state.language)
            userPreferences.setSetupComplete(true)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isComplete = true
            )
        }
    }

    fun startTileDownload() {
        if (_tileDownloadState.value.isDownloading) return

        _tileDownloadState.value = TileDownloadUiState(isDownloading = true)

        viewModelScope.launch {
            try {
                // ── Point OSMDroid at the shared local tile cache ──────────────
                // Must be done before SqlTileWriter is instantiated so it writes
                // to the same path that MapScreen reads from at runtime.
                Configuration.getInstance().apply {
                    userAgentValue       = context.packageName
                    tileFileSystemCacheMaxBytes =
                        Constants.MAP_TILE_CACHE_MB * 1024L * 1024L    // 200 MB
                    osmdroidBasePath     = File(context.filesDir, "osmdroid")
                    osmdroidTileCache    = File(context.filesDir, "osmdroid/tiles")
                }

                val boundingBox = BoundingBox(
                    19.3760,   // latNorth
                    73.0777,   // lonEast
                    18.7760,   // latSouth
                    72.6777    // lonWest
                )
                val zoomMin = 10
                val zoomMax = 16

                val tileSource = TileSourceFactory.MAPNIK
                val tileWriter = SqlTileWriter()

                val totalTiles     = countTiles(boundingBox, zoomMin, zoomMax)
                var downloadedCount = 0

                for (zoom in zoomMin..zoomMax) {
                    val (xMin, xMax, yMin, yMax) = tileBounds(boundingBox, zoom)
                    for (x in xMin..xMax) {
                        for (y in yMin..yMax) {

                            val tileIndex = MapTileIndex.getTileIndex(zoom, x, y)
                            val url       = tileSource.getTileURLString(tileIndex)

                            try {
                                val bytes = java.net.URL(url).readBytes()
                                tileWriter.saveFile(
                                    tileSource,
                                    tileIndex,
                                    bytes.inputStream(),
                                    // 30-day expiry
                                    System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 30
                                )
                            } catch (_: Exception) {
                                // Skip individual tile failures; keep going
                            }

                            downloadedCount++
                            _tileDownloadState.value = _tileDownloadState.value.copy(
                                progress = downloadedCount.toFloat() / totalTiles
                            )
                        }
                    }
                }

                // ── Mark tiles as ready in DataStore ──────────────────────────
                userPreferences.setMapTilesDownloaded(true)

                _tileDownloadState.value = TileDownloadUiState(isDone = true, progress = 1f)

            } catch (e: Exception) {
                _tileDownloadState.value = TileDownloadUiState(
                    error = "Download failed: ${e.localizedMessage ?: "unknown error"}"
                )
            }
        }
    }

    // ── Tile-count helpers ────────────────────────────────────────────────────

    private fun countTiles(box: BoundingBox, zoomMin: Int, zoomMax: Int): Int {
        var count = 0
        for (zoom in zoomMin..zoomMax) {
            val (xMin, xMax, yMin, yMax) = tileBounds(box, zoom)
            count += (xMax - xMin + 1) * (yMax - yMin + 1)
        }
        return count.coerceAtLeast(1)
    }

    private fun tileBounds(box: BoundingBox, zoom: Int): TileBounds {
        fun lonToX(lon: Double) =
            ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()

        fun latToY(lat: Double): Int {
            val rad = Math.toRadians(lat)
            return ((1.0 - Math.log(Math.tan(rad) + 1.0 / Math.cos(rad)) / Math.PI)
                    / 2.0 * (1 shl zoom)).toInt()
        }

        return TileBounds(
            xMin = lonToX(box.lonWest),
            xMax = lonToX(box.lonEast),
            yMin = latToY(box.latNorth),
            yMax = latToY(box.latSouth)
        )
    }
}