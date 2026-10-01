package com.hungii.prototype

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Entity(tableName = "tracker_state")
data class TrackerRecord(@PrimaryKey val owner: String, val payload: String)
@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_state WHERE owner = :owner") suspend fun get(owner: String): TrackerRecord?
    @Upsert suspend fun save(record: TrackerRecord)
}
@Database(entities = [TrackerRecord::class], version = 1, exportSchema = false)
abstract class HungiiDatabase : RoomDatabase() {
    abstract fun tracker(): TrackerDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, HungiiDatabase::class.java, "hungii-tracker.db").build()
    }
}

// Only encrypted ciphertext is persisted. Android backup is disabled in the manifest.
class SecureSession(context: Context) {
    private val preferences = context.getSharedPreferences("hungii-session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("hungii-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("hungii-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return try {
            val parts = stored.split('.')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.updateAAD(name.toByteArray())
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
        } catch (_: Exception) { remove(name); null }
    }
    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(name.toByteArray())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        preferences.edit().putString(name, encrypted).commit()
    }
    fun remove(name: String) { preferences.edit().remove(name).commit() }
}
