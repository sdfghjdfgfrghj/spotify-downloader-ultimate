package org.example.spotifydownloader

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class SpotifyAccount(
    val displayName: String,
    val clientId: String,
    val clientSecret: String,
    val userId: String? = null,
    val isActive: Boolean = false
)

object SpotifyAccountManager {
    private const val PREFS_NAME = "spotify_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE_ACCOUNT = "active_account"
    
    private val gson = Gson()
    
    fun getAccounts(context: Context): List<SpotifyAccount> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, "[]")
        val type = object : TypeToken<List<SpotifyAccount>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
    
    fun saveAccounts(context: Context, accounts: List<SpotifyAccount>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()
    }
    
    fun getActiveAccount(context: Context): SpotifyAccount? {
        val accounts = getAccounts(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeIndex = prefs.getInt(KEY_ACTIVE_ACCOUNT, -1)
        return if (activeIndex >= 0 && activeIndex < accounts.size) {
            accounts[activeIndex]
        } else null
    }
    
    fun setActiveAccount(context: Context, accountIndex: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ACTIVE_ACCOUNT, accountIndex).apply()
        
        // Update spotify config for the active account
        val accounts = getAccounts(context)
        if (accountIndex >= 0 && accountIndex < accounts.size) {
            val account = accounts[accountIndex]
            updateSpotifyConfig(context, account)
            UiBridge.log("🔄 Switched to account: ${account.displayName}\n")
        }
    }
    
    fun addAccount(context: Context, account: SpotifyAccount): Int {
        val accounts = getAccounts(context).toMutableList()
        accounts.add(account)
        saveAccounts(context, accounts)
        
        // Set as active if it's the first account
        if (accounts.size == 1) {
            setActiveAccount(context, 0)
        }
        
        UiBridge.log("✅ Added Spotify account: ${account.displayName}\n")
        return accounts.size - 1
    }
    
    fun removeAccount(context: Context, accountIndex: Int) {
        val accounts = getAccounts(context).toMutableList()
        if (accountIndex >= 0 && accountIndex < accounts.size) {
            val removedAccount = accounts.removeAt(accountIndex)
            saveAccounts(context, accounts)
            
            // Clear cache for removed account
            clearAccountCache(context, removedAccount)
            
            // Update active account if needed
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentActive = prefs.getInt(KEY_ACTIVE_ACCOUNT, -1)
            if (currentActive == accountIndex) {
                // Set first available account as active
                if (accounts.isNotEmpty()) {
                    setActiveAccount(context, 0)
                } else {
                    prefs.edit().putInt(KEY_ACTIVE_ACCOUNT, -1).apply()
                }
            } else if (currentActive > accountIndex) {
                // Adjust active index if needed
                prefs.edit().putInt(KEY_ACTIVE_ACCOUNT, currentActive - 1).apply()
            }
            
            UiBridge.log("🗑️ Removed Spotify account: ${removedAccount.displayName}\n")
        }
    }
    
    private fun updateSpotifyConfig(context: Context, account: SpotifyAccount) {
        try {
            // Update main spotify config
            val configFile = File(context.filesDir, "spotify_config.json")
            val config = mapOf(
                "client_id" to account.clientId,
                "client_secret" to account.clientSecret,
                "redirect_uri" to "http://localhost:8080"
            )
            configFile.writeText(gson.toJson(config))
            
            // Clear old cache and set new cache file
            val oldCache = File(context.filesDir, ".spotify_cache")
            val newCache = File(context.filesDir, ".spotify_cache_${account.displayName.replace(" ", "_")}")
            
            if (oldCache.exists()) oldCache.delete()
            if (newCache.exists()) {
                newCache.copyTo(oldCache, overwrite = true)
            }
            
        } catch (e: Exception) {
            UiBridge.log("⚠️ Error updating Spotify config: ${e.message}\n")
        }
    }
    
    private fun clearAccountCache(context: Context, account: SpotifyAccount) {
        try {
            val cacheFile = File(context.filesDir, ".spotify_cache_${account.displayName.replace(" ", "_")}")
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            UiBridge.log("⚠️ Error clearing cache: ${e.message}\n")
        }
    }
    
    fun hasAccounts(context: Context): Boolean {
        return getAccounts(context).isNotEmpty()
    }
    
    fun getCurrentAccountInfo(context: Context): String {
        val activeAccount = getActiveAccount(context)
        return if (activeAccount != null) {
            "👤 ${activeAccount.displayName}"
        } else {
            "❌ No account selected"
        }
    }
}