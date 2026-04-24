package com.meshrelief.mesh.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val BT_NAME = "MeshRelief"

@Singleton
class BluetoothMeshManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val TAG = "BluetoothMeshManager"

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _peerList = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val peerList: StateFlow<List<BluetoothDevice>> = _peerList.asStateFlow()

    private val _connectedPeerIPs = MutableStateFlow<List<String>>(emptyList())
    val connectedPeerIPs: StateFlow<List<String>> = _connectedPeerIPs.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var isInitialized = false
    private var isReceiverRegistered = false

    // ── Permission helpers ────────────────────────────────────────────────

    private fun hasScanPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    // ── BLE discovery receiver ────────────────────────────────────────────

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    val current = _peerList.value.toMutableList()
                    if (current.none { d -> d.address == it.address }) {
                        current.add(it)
                        _peerList.value = current
                        Log.d(TAG, "BT peer found: ${it.address}")
                    }
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    fun initialize() {
        if (isInitialized) return
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Device has no Bluetooth adapter.")
            return
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            Log.w(TAG, "Bluetooth permissions not granted — skipping BT initialization.")
            return
        }

        if (!isReceiverRegistered) {
            context.registerReceiver(
                discoveryReceiver,
                IntentFilter(BluetoothDevice.ACTION_FOUND)
            )
            isReceiverRegistered = true
        }

        scope.launch { startAcceptLoop() }

        isInitialized = true
        Log.d(TAG, "BluetoothMeshManager initialized.")
        discoverPeers()
    }

    fun shutdown() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver not registered: ${e.message}")
            }
            isReceiverRegistered = false
        }
        if (hasScanPermission()) {
            bluetoothAdapter?.cancelDiscovery()
        }
        serverSocket?.closeQuietly()
        serverSocket = null
        isInitialized = false
        _peerList.value = emptyList()
        _connectedPeerIPs.value = emptyList()
        Log.d(TAG, "BluetoothMeshManager shut down.")
    }

    // ── Discovery ─────────────────────────────────────────────────────────

    fun discoverPeers() {
        val adapter = bluetoothAdapter ?: return
        if (!hasScanPermission()) {
            Log.w(TAG, "BLUETOOTH_SCAN not granted — skipping discoverPeers().")
            return
        }
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        val started = adapter.startDiscovery()
        Log.d(TAG, "BT discovery started: $started")
    }

    // ── Connect ───────────────────────────────────────────────────────────

    fun connectToPeer(device: BluetoothDevice) {
        if (!hasScanPermission() || !hasConnectPermission()) {
            Log.w(TAG, "Bluetooth permissions not granted — cannot connect to peer.")
            return
        }
        scope.launch {
            bluetoothAdapter?.cancelDiscovery()
            try {
                val socket = device.createRfcommSocketToServiceRecord(BT_UUID)
                socket.connect()
                val ip = "bt:${device.address}"
                _connectedPeerIPs.value = (_connectedPeerIPs.value + ip).distinct()
                Log.d(TAG, "BT connected to ${device.address}")
            } catch (e: IOException) {
                Log.e(TAG, "BT connect failed to ${device.address}: ${e.message}")
            }
        }
    }

    // ── Accept loop ───────────────────────────────────────────────────────

    private fun startAcceptLoop() {
        if (!hasConnectPermission()) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted — cannot start accept loop.")
            return
        }
        try {
            serverSocket = bluetoothAdapter
                ?.listenUsingRfcommWithServiceRecord(BT_NAME, BT_UUID)

            Log.d(TAG, "BT server socket listening.")

            while (true) {
                val socket: BluetoothSocket = serverSocket?.accept() ?: break
                val ip = "bt:${socket.remoteDevice.address}"
                _connectedPeerIPs.value = (_connectedPeerIPs.value + ip).distinct()
                Log.d(TAG, "BT inbound connection from ${socket.remoteDevice.address}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "BT server socket closed: ${e.message}")
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────

    private fun BluetoothServerSocket.closeQuietly() {
        try { close() } catch (_: IOException) {}
    }
}