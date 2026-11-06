package com.gamebox.common.multiplayer

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

class NearbySession(
    context: Context,
    private val serviceId: String,
    private val listener: Listener
) {
    interface Listener {
        fun onConnectionInitiated(endpointId: String, endpointName: String, authenticationDigits: String)
        fun onConnected(endpointId: String, endpointName: String)
        fun onDisconnected(endpointId: String)
        fun onEndpointFound(endpointId: String, endpointName: String)
        fun onEndpointLost(endpointId: String)
        fun onPayload(endpointId: String, bytes: ByteArray)
        fun onError(error: Exception)
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val endpointNames = mutableMapOf<String, String>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { listener.onPayload(endpointId, it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNames[endpointId] = info.endpointName
            listener.onConnectionInitiated(
                endpointId,
                info.endpointName,
                info.authenticationDigits
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                listener.onConnected(endpointId, endpointNames[endpointId] ?: "שחקן")
            } else {
                endpointNames.remove(endpointId)
                listener.onError(
                    IllegalStateException("Nearby connection failed: ${result.status.statusCode}")
                )
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpointNames.remove(endpointId)
            listener.onDisconnected(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            listener.onEndpointFound(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            endpointNames.remove(endpointId)
            listener.onEndpointLost(endpointId)
        }
    }

    fun startAdvertising(displayName: String) {
        client.startAdvertising(
            displayName,
            serviceId,
            lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener(listener::onError)
    }

    fun startDiscovery() {
        client.startDiscovery(
            serviceId,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnFailureListener(listener::onError)
    }

    fun requestConnection(displayName: String, endpointId: String) {
        client.requestConnection(displayName, endpointId, lifecycleCallback)
            .addOnFailureListener(listener::onError)
    }

    fun acceptConnection(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener(listener::onError)
    }

    fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId).addOnFailureListener(listener::onError)
    }

    fun send(endpointId: String, bytes: ByteArray) {
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener(listener::onError)
    }

    fun stopAdvertising() = client.stopAdvertising()

    fun stopDiscovery() = client.stopDiscovery()

    fun disconnect(endpointId: String) = client.disconnectFromEndpoint(endpointId)

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        endpointNames.clear()
    }
}
