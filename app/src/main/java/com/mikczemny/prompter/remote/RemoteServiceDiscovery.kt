package com.mikczemny.prompter.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable

data class DiscoveredCamera(val name: String, val host: String)

class RemoteServiceAdvertiser(context: Context) : Closeable {
    private val manager = context.getSystemService(NsdManager::class.java)
    private var registered = false
    private val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) { registered = true }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) { registered = false }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
    }

    fun start() {
        val info = NsdServiceInfo().apply {
            serviceName = "Prompter-${Build.MODEL}"
            serviceType = SERVICE_TYPE
            port = RemoteSecureServer.DEFAULT_PORT
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun close() {
        if (registered) runCatching { manager.unregisterService(listener) }
        registered = false
    }
}

class RemoteServiceBrowser(
    context: Context,
    private val onCameraFound: (DiscoveredCamera) -> Unit,
) : Closeable {
    private val manager = context.getSystemService(NsdManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val multicastLock = context.getSystemService(WifiManager::class.java)
        .createMulticastLock("prompter-remote-discovery").apply { setReferenceCounted(false) }
    private var discovering = false
    private var resolving = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) { discovering = true }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { discovering = false }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { discovering = false }
        override fun onDiscoveryStopped(serviceType: String) { discovering = false }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            if (serviceInfo.serviceType != SERVICE_TYPE || resolving) return
            resolving = true
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving = false
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    resolving = false
                    val address = if (Build.VERSION.SDK_INT >= 34) {
                        serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                    } else {
                        @Suppress("DEPRECATION")
                        serviceInfo.host?.hostAddress
                    }
                    if (!address.isNullOrBlank()) {
                        mainHandler.post { onCameraFound(DiscoveredCamera(serviceInfo.serviceName, address)) }
                    }
                }
            })
        }
    }

    fun start() {
        if (!multicastLock.isHeld) multicastLock.acquire()
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    override fun close() {
        if (discovering) runCatching { manager.stopServiceDiscovery(discoveryListener) }
        discovering = false
        if (multicastLock.isHeld) multicastLock.release()
    }
}

private const val SERVICE_TYPE = "_promptercam._tcp."
