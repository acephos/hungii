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
import java.security.MessageDigest

@Entity(tableName = "tracker_state")
data class TrackerRecord(@PrimaryKey val owner: String, val payload: String)
@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_state WHERE owner = :owner") suspend fun get(owner: String): TrackerRecord?
    @Upsert suspend fun save(record: TrackerRecord)
    @Query("DELETE FROM tracker_state WHERE owner = :owner") suspend fun erase(owner: String)
    @Query("SELECT * FROM tracker_state") suspend fun all(): List<TrackerRecord>
}
@Database(entities = [TrackerRecord::class], version = 1, exportSchema = false)
abstract class HungiiDatabase : RoomDatabase() {
    abstract fun tracker(): TrackerDao
    companion object {
        fun open(context: Context, name: String = "hungii-private.db") = Room.databaseBuilder(context, HungiiDatabase::class.java, name).build()
    }
}

// Only encrypted ciphertext is persisted. Android backup is disabled in the manifest.
class SecureSession(context: Context) {
    private val preferences = context.getSharedPreferences("hungii-session", Context.MODE_PRIVATE)
    private fun key(alias: String = "hungii-session"): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256)
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
    fun eraseAuth() {preferences.edit().clear().commit();KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry("hungii-session")}
    fun encryptPayload(ownerHash: String, value: String): String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key("hungii-data-$ownerHash"));cipher.updateAAD(ownerHash.toByteArray())
        return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(value.toByteArray()),Base64.NO_WRAP)
    }
    fun decryptPayload(ownerHash: String, value: String): String {
        val parts=value.split('.');val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key("hungii-data-$ownerHash"),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(ownerHash.toByteArray())
        return String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)))
    }
    fun erasePayloadKey(ownerHash: String) {KeyStore.getInstance("AndroidKeyStore").apply{load(null)}.deleteEntry("hungii-data-$ownerHash")}
}

class PrivateTrackerStore(private val context: Context) {
    private val db=HungiiDatabase.open(context)
    private val secure=SecureSession(context)
    private fun hash(owner:String)=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString(""){"%02x".format(it)}
    suspend fun migrate() {
        if(!context.getDatabasePath("hungii-tracker.db").exists())return
        val old=HungiiDatabase.open(context,"hungii-tracker.db")
        try {for(record in old.tracker().all()) {if(db.tracker().get(hash(record.owner))==null)save(record)} } finally {old.close()}
        context.deleteDatabase("hungii-tracker.db")
    }
    suspend fun get(owner:String):TrackerRecord? {
        val h=hash(owner);val record=db.tracker().get(h)?:return null
        return TrackerRecord(owner,secure.decryptPayload(h,record.payload))
    }
    suspend fun save(record:TrackerRecord) {val h=hash(record.owner);db.tracker().save(TrackerRecord(h,secure.encryptPayload(h,record.payload)))}
    suspend fun erase(owner:String) {val h=hash(owner);db.tracker().erase(h);secure.erasePayloadKey(h)}
    fun close()=db.close()
}
