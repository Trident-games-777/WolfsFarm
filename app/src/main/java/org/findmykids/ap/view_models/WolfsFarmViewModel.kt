package org.findmykids.ap.view_models

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.facebook.applinks.AppLinkData
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.onesignal.OneSignal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.findmykids.ap.R
import org.findmykids.ap.dataStore
import org.findmykids.ap.other.Keys
import org.findmykids.ap.other.UrlStatus
import java.io.File
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WolfsFarmViewModel(
    private val app: Application
) : AndroidViewModel(app) {
    val urlLiveData: MutableLiveData<String> = MutableLiveData()

    init {
        OneSignal.initWithContext(app.applicationContext)
        OneSignal.setAppId(app.resources.getString(R.string.one_signal_id))
        initializeUrl()
    }

    private fun initializeUrl() {
        viewModelScope.launch {
            val afDeferred: Deferred<MutableMap<String, Any>?> = async { fetchAF() }
            val fbDeferred: Deferred<String> = async { fetchFB() }
            sendOneSignalTag(fbDeferred.await(), afDeferred.await())
            withContext(Dispatchers.Default) {
                val url = createUrl(fbDeferred.await(), afDeferred.await())
                updateUrl(url)
                urlLiveData.postValue(url)
            }
        }
    }

    private suspend fun fetchAF(): MutableMap<String, Any>? = suspendCoroutine { cont ->
        AppsFlyerLib.getInstance().init(
            app.resources.getString(R.string.apps_dev_key),
            object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(p0: MutableMap<String, Any>?) {
                    cont.resume(p0)
                }

                override fun onConversionDataFail(p0: String?) {}
                override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {}
                override fun onAttributionFailure(p0: String?) {}
            },
            app.applicationContext
        )
        AppsFlyerLib.getInstance().start(app.applicationContext)
    }

    private suspend fun fetchFB(): String = suspendCoroutine { cont ->
        AppLinkData.fetchDeferredAppLinkData(getApplication()) {
            cont.resume(it?.targetUri.toString())
        }
    }

    private fun sendOneSignalTag(fb: String, af: MutableMap<String, Any>?) {
        val campaign = af?.get("campaign").toString()

        if (campaign == "null" && fb == "null") {
            OneSignal.sendTag("key2", "organic")
        } else if (fb != "null") {
            OneSignal.sendTag("key2", fb.replace("myapp://", "").substringBefore("/"))
        } else if (campaign != "null") {
            OneSignal.sendTag("key2", campaign.substringBefore("_"))
        }
    }

    private fun createUrl(fb: String, af: MutableMap<String, Any>?): String {
        val gAdId = AdvertisingIdClient.getAdvertisingIdInfo(app.applicationContext).id.toString()
        val res = app.resources
        val baseUrl = res.getString(R.string.base_url)
        val url = baseUrl.toUri().buildUpon().apply {
            appendQueryParameter(
                res.getString(R.string.secure_get_parametr),
                res.getString(R.string.secure_key)
            )
            appendQueryParameter(
                res.getString(R.string.dev_tmz_key),
                TimeZone.getDefault().id
            )
            appendQueryParameter(res.getString(R.string.gadid_key), gAdId)
            appendQueryParameter(res.getString(R.string.deeplink_key), fb)
            appendQueryParameter(
                res.getString(R.string.source_key),
                af?.get("media_source").toString()
            )
            appendQueryParameter(
                res.getString(R.string.af_id_key),
                AppsFlyerLib.getInstance().getAppsFlyerUID(app.applicationContext)
            )
            appendQueryParameter(
                res.getString(R.string.adset_id_key),
                af?.get("adset_id").toString()
            )
            appendQueryParameter(
                res.getString(R.string.campaign_id_key),
                af?.get("campaign_id").toString()
            )
            appendQueryParameter(
                res.getString(R.string.app_campaign_key),
                af?.get("campaign").toString()
            )
            appendQueryParameter(res.getString(R.string.adset_key), af?.get("adset").toString())
            appendQueryParameter(res.getString(R.string.adgroup_key), af?.get("adgroup").toString())
            appendQueryParameter(
                res.getString(R.string.orig_cost_key),
                af?.get("orig_cost").toString()
            )
            appendQueryParameter(
                res.getString(R.string.af_siteid_key),
                af?.get("af_siteid").toString()
            )
        }.toString()
        return url
    }

    suspend fun updateUrl(url: String) {
        val dataStoreUrlKey: Preferences.Key<String> = stringPreferencesKey(Keys.URL_KEY)
        val dataStoreUrlStatusKey: Preferences.Key<Int> = intPreferencesKey(Keys.URL_STATUS_KEY)
        val preferences: Preferences = app.dataStore.data.first()
        when (preferences[dataStoreUrlStatusKey]) {
            null, UrlStatus.NONE -> {
                app.dataStore.edit { prefs -> prefs[dataStoreUrlStatusKey] = UrlStatus.FIRST }
                app.dataStore.edit { prefs -> prefs[dataStoreUrlKey] = url }
            }
            UrlStatus.FIRST -> {
                app.dataStore.edit { prefs -> prefs[dataStoreUrlStatusKey] = UrlStatus.FINAL }
                app.dataStore.edit { prefs -> prefs[dataStoreUrlKey] = url }
            }
            UrlStatus.FINAL -> {}
        }
    }

    suspend fun fetchUrl(): String {
        val dataStoreKey: Preferences.Key<String> = stringPreferencesKey(Keys.URL_KEY)
        val preferences: Preferences = app.dataStore.data.first()
        return preferences[dataStoreKey] ?: "No url"
    }

    suspend fun fetchIsFirstLaunch(): Boolean {
        val dataStoreKey: Preferences.Key<Boolean> = booleanPreferencesKey(Keys.IS_FIRST_LAUNCH_KEY)
        val preferences: Preferences = app.dataStore.data.first()
        return if (preferences[dataStoreKey] == null) {
            app.dataStore.edit { prefs ->
                prefs[dataStoreKey] = false
            }
            true
        } else false
    }

    fun isRootsAndAdbEnabled(): Boolean {
        return isRootsEnabled() || getAdbEnabled() == "1"
    }

    private fun isRootsEnabled(): Boolean {
        val dirsArray: Array<String> = app.resources.getStringArray(R.array.dirs_array)
        try {
            for (dir in dirsArray) {
                if (File(dir + "su").exists()) return true
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        return false
    }

    private fun getAdbEnabled(): String {
        return Settings.Global.getString(app.contentResolver, Settings.Global.ADB_ENABLED)
            ?: "null"
    }
}