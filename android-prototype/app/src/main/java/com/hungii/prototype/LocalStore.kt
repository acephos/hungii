package com.hungii.prototype

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "tracker_state")
data class TrackerRecord(
    @PrimaryKey val owner: String,
    val payload: String,
)

@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_state WHERE owner = :owner")
    suspend fun get(owner: String): TrackerRecord?

    @Upsert
    suspend fun save(record: TrackerRecord)

    @Query("DELETE FROM tracker_state WHERE owner = :owner")
    suspend fun erase(owner: String)

    @Query("SELECT * FROM tracker_state")
    suspend fun all(): List<TrackerRecord>
}

/** Schema history is exported to app/schemas so future versions can ship tested migrations. */
@Database(entities = [TrackerRecord::class], version = 1, exportSchema = true)
abstract class HungiiDatabase : RoomDatabase() {
    abstract fun tracker(): TrackerDao

    companion object {
        fun open(
            context: Context,
            name: String = "hungii-private.db",
        ) = Room.databaseBuilder(context, HungiiDatabase::class.java, name).build()
    }
}

/** Android Keystore AES-GCM. Only ciphertext is persisted; Android backup is disabled in the manifest. */
class SecureSession(
    context: Context,
) {
    private val preferences = context.getSharedPreferences("hungii-session", Context.MODE_PRIVATE)
    private val keyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(alias: String = SESSION_KEY): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    /** Encrypts [value] as "iv.ciphertext", binding it to [aad]. */
    private fun seal(
        alias: String,
        aad: String,
        value: String,
    ): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        cipher.updateAAD(aad.toByteArray())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun unseal(
        alias: String,
        aad: String,
        value: String,
    ): String {
        val parts = value.split('.')
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.updateAAD(aad.toByteArray())
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }

    /** An unreadable entry is removed, so a damaged session signs out instead of failing forever. */
    fun read(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return try {
            unseal(SESSION_KEY, name, stored)
        } catch (_: Exception) {
            remove(name)
            null
        }
    }

    // Writes commit synchronously: the PKCE verifier and refresh token must be on disk
    // before a browser opens or a request continues, or a process death would lose them.
    fun put(
        name: String,
        value: String,
    ) {
        preferences.edit(commit = true) { putString(name, seal(SESSION_KEY, name, value)) }
    }

    fun remove(name: String) {
        preferences.edit(commit = true) { remove(name) }
    }

    fun eraseAuth() {
        preferences.edit(commit = true) { clear() }
        keyStore.deleteEntry(SESSION_KEY)
    }

    fun encryptPayload(
        ownerHash: String,
        value: String,
    ) = seal(payloadKey(ownerHash), ownerHash, value)

    fun decryptPayload(
        ownerHash: String,
        value: String,
    ) = unseal(payloadKey(ownerHash), ownerHash, value)

    /** Deleting the per-owner key makes any leftover ciphertext unreadable. */
    fun erasePayloadKey(ownerHash: String) = keyStore.deleteEntry(payloadKey(ownerHash))

    private fun payloadKey(ownerHash: String) = "hungii-data-$ownerHash"

    private companion object {
        const val SESSION_KEY = "hungii-session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** The encrypted tracker store, keyed by a hash of the Hungii account. */
class PrivateTrackerStore(
    private val context: Context,
) {
    private val db = HungiiDatabase.open(context)
    private val secure = SecureSession(context)

    private fun hash(owner: String) =
        MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Moves records from the original unencrypted database, then deletes it. */
    suspend fun migrate() {
        if (!context.getDatabasePath(LEGACY_DATABASE).exists()) return
        val old = HungiiDatabase.open(context, LEGACY_DATABASE)
        try {
            for (record in old.tracker().all()) {
                if (db.tracker().get(hash(record.owner)) == null) save(record)
            }
        } finally {
            old.close()
        }
        context.deleteDatabase(LEGACY_DATABASE)
    }

    suspend fun get(owner: String): TrackerRecord? {
        val h = hash(owner)
        val record = db.tracker().get(h) ?: return null
        return TrackerRecord(owner, secure.decryptPayload(h, record.payload))
    }

    suspend fun save(record: TrackerRecord) {
        val h = hash(record.owner)
        db.tracker().save(TrackerRecord(h, secure.encryptPayload(h, record.payload)))
    }

    suspend fun erase(owner: String) {
        val h = hash(owner)
        db.tracker().erase(h)
        secure.erasePayloadKey(h)
    }

    fun close() = db.close()

    private companion object {
        const val LEGACY_DATABASE = "hungii-tracker.db"
    }
}
