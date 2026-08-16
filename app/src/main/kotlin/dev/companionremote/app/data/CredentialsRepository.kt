package dev.companionremote.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.companionremote.app.discovery.TvPlatform
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "companion_remote")

/** A saved direct endpoint used when mDNS cannot cross routed VLANs. */
data class SavedDeviceEndpoint(
    val platform: TvPlatform,
    val host: String,
    val port: Int,
    val pairingPort: Int? = null,
)

/**
 * Stores pyatv-format credential strings per device name, wrapped with the
 * Android Keystore (see [KeystoreCrypto]).
 */
class CredentialsRepository(context: Context) {

    private val appContext = context.applicationContext

    private fun credentialsKey(deviceName: String) = stringPreferencesKey("creds_$deviceName")
    private fun endpointKey(deviceName: String) = stringPreferencesKey("endpoint_$deviceName")

    suspend fun load(deviceName: String): String? {
        val preferences = appContext.dataStore.data.first()
        val wrapped = preferences[credentialsKey(deviceName)] ?: return null
        return KeystoreCrypto.decrypt(wrapped)
    }

    suspend fun save(deviceName: String, credentials: String) {
        val wrapped = KeystoreCrypto.encrypt(credentials)
        appContext.dataStore.edit { it[credentialsKey(deviceName)] = wrapped }
    }

    suspend fun delete(deviceName: String) {
        appContext.dataStore.edit {
            it.remove(credentialsKey(deviceName))
            it.remove(endpointKey(deviceName))
        }
    }

    suspend fun loadEndpoint(deviceName: String): SavedDeviceEndpoint? {
        val preferences = appContext.dataStore.data.first()
        val wrapped = preferences[endpointKey(deviceName)] ?: return null
        val raw = KeystoreCrypto.decrypt(wrapped) ?: return null
        val fields = raw.split("\n", limit = 4)
        if (fields.size != 4 || fields[1].isBlank()) return null
        val platform = runCatching { TvPlatform.valueOf(fields[0]) }.getOrNull() ?: return null
        val port = fields[2].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
        val pairingPort = fields[3].toIntOrNull()?.takeIf { it in 1..65_535 }
        return SavedDeviceEndpoint(platform, fields[1], port, pairingPort)
    }

    suspend fun saveEndpoint(deviceName: String, endpoint: SavedDeviceEndpoint) {
        val raw = listOf(
            endpoint.platform.name,
            endpoint.host,
            endpoint.port.toString(),
            endpoint.pairingPort?.toString().orEmpty(),
        ).joinToString("\n")
        val wrapped = KeystoreCrypto.encrypt(raw)
        appContext.dataStore.edit { it[endpointKey(deviceName)] = wrapped }
    }

    /** Atomically-ish move a saved device's credentials and endpoint to a new name. */
    suspend fun rename(oldName: String, newName: String): Boolean {
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) return false
        val existingCredentials = load(newName)
        val existingEndpoint = loadEndpoint(newName)
        if (existingCredentials != null || existingEndpoint != null) return false

        val credentials = load(oldName)
        val endpoint = loadEndpoint(oldName)
        if (credentials == null && endpoint == null) return false

        if (credentials != null) save(newName, credentials)
        if (endpoint != null) saveEndpoint(newName, endpoint)
        delete(oldName)
        return true
    }

    suspend fun endpointDeviceNames(): List<String> {
        val preferences = appContext.dataStore.data.first()
        return preferences.asMap().keys
            .map { it.name }
            .filter { it.startsWith("endpoint_") }
            .map { it.removePrefix("endpoint_") }
    }

    suspend fun pairedDeviceNames(): List<String> {
        val preferences = appContext.dataStore.data.first()
        return preferences.asMap().keys
            .map { it.name }
            .filter { it.startsWith("creds_") }
            .map { it.removePrefix("creds_") }
    }
}
