package com.hungii.prototype

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.LocalDate
import kotlin.math.roundToInt

data class Span(val low: Int, val high: Int) {
    val mid get() = (low + high)/2f
    val label get() = if(low == high) "$low" else "$low–$high"
    operator fun plus(other: Span) = Span(low+other.low,high+other.high)
    fun portion(f: Float) = Span((low*f).toInt(),(high*f).toInt())
    fun json() = JSONArray().put(low).put(high)
    companion object { fun from(a: JSONArray) = Span(a.getInt(0),a.getInt(1)) }
}
data class Nutrition(val calories: Span, val protein: Span, val carbs: Span, val fat: Span) {
    operator fun plus(n: Nutrition) = Nutrition(calories+n.calories,protein+n.protein,carbs+n.carbs,fat+n.fat)
    fun portion(f: Float) = Nutrition(calories.portion(f),protein.portion(f),carbs.portion(f),fat.portion(f))
    fun json() = JSONObject().put("calories",calories.json()).put("protein",protein.json()).put("carbs",carbs.json()).put("fat",fat.json())
    companion object {
        val zero = Nutrition(Span(0,0),Span(0,0),Span(0,0),Span(0,0))
        fun from(o: JSONObject) = Nutrition(Span.from(o.getJSONArray("calories")),Span.from(o.getJSONArray("protein")),Span.from(o.getJSONArray("carbs")),Span.from(o.getJSONArray("fat")))
    }
}
data class Meal(val id: String, val dishId: String, val restaurantId: String, val name: String, val restaurant: String,
    val imageUrl: String?, val veg: Boolean?, val itemPrice: Double?, val etaMinutes: Int?, val distanceKm: Double?,
    val description: String = "", val offerText: String? = null, val nutrition: Nutrition? = null, val estimatedPayable: Double? = null, val photo: Int = 0) {
    val price get() = Span(itemPrice?.roundToInt() ?: 0,itemPrice?.roundToInt() ?: 0)
    val priceLabel get() = itemPrice?.let { "₹" + if(it == it.toInt().toDouble()) it.toInt().toString() else "%.2f".format(java.util.Locale.ROOT,it) } ?: "Price unavailable"
    val etaLabel get() = etaMinutes?.let { "$it min est." } ?: "ETA unavailable"
    val badge get() = if(BuildConfig.LOCAL_DEMO) "SYNTHETIC DEMO" else "Powered by Swiggy"
    val tags get() = setOf("spicy","cheesy","sweet","bland","light","filling").filter { it in (name+" "+description).lowercase() }.toSet()
    val benefit get() = if(description.isNotBlank()) description else "${restaurant}. ${etaLabel}."
    val compromise get() = if(nutrition!=null) "Estimated nutrition, not a lab measurement. Final savings depend on the basket." else "Nutrition is not published here. Fees and coupon eligibility are checked in the cart."
    fun json() = JSONObject().put("id",id).put("dishId",dishId).put("restaurantId",restaurantId).put("name",name).put("restaurant",restaurant)
        .put("imageUrl",imageUrl ?: JSONObject.NULL).put("veg",veg ?: JSONObject.NULL).put("itemPrice",itemPrice ?: JSONObject.NULL)
        .put("etaMinutes",etaMinutes ?: JSONObject.NULL).put("distanceKm",distanceKm ?: JSONObject.NULL).put("description",description).put("offerText",offerText ?: JSONObject.NULL)
    companion object {
        fun from(o: JSONObject) = Meal(o.getString("id"),o.getString("dishId"),o.getString("restaurantId"),o.getString("name"),o.getString("restaurant"),
            o.optString("imageUrl").takeIf { it.startsWith("https://") },if(o.isNull("veg")) null else o.getBoolean("veg"),
            if(o.isNull("itemPrice")) null else o.getDouble("itemPrice"),if(o.isNull("etaMinutes")) null else o.getInt("etaMinutes"),
            if(o.isNull("distanceKm")) null else o.getDouble("distanceKm"),o.optString("description"),o.optString("offerText").takeIf { it.isNotBlank() && it != "null" },o.optJSONObject("nutrition")?.let {Nutrition.from(it)},if(o.isNull("estimatedPayable"))null else o.optDouble("estimatedPayable"),o.optInt("photo"))
    }
}
enum class Screen { Home, Assistant, Discover, Finalists, Draw, Winner, Review, Payment, Order, Saved, Day }
data class ChatMessage(val text:String,val fromUser:Boolean=false,val actions:List<Pair<String,String>> = emptyList())
data class DeliveryAddress(val id: String,val label: String,val addressLine: String)
data class Restaurant(val id: String,val name: String,val etaMinutes: Int?,val distanceKm: Double?)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class HungiiModel(application: Application) : AndroidViewModel(application) {
    private val db = PrivateTrackerStore(application)
    private val api = HungiiApi(SecureSession(application))
    var screen by mutableStateOf(Screen.Home)
    var calorieGoal by mutableStateOf(2200); var proteinGoal by mutableStateOf(140)
    var carbGoal by mutableStateOf(250); var fatGoal by mutableStateOf(70)
    var allowance by mutableStateOf(600); var spent by mutableStateOf(0)
    var opportunities by mutableStateOf(2); var intake by mutableStateOf(Nutrition.zero)
    var taste by mutableStateOf("any"); var highProtein by mutableStateOf(false)
    var vegOnly by mutableStateOf(false); var budgetOnly by mutableStateOf(false); var fast by mutableStateOf(false)
    var query by mutableStateOf("healthy bowls")
    var displayName by mutableStateOf("")
    var offlineMode by mutableStateOf(BuildConfig.LOCAL_DEMO)
    var initializing by mutableStateOf(true)
    var accountLoading by mutableStateOf(api.signedIn && !BuildConfig.LOCAL_DEMO)
    var cloudSetupPending by mutableStateOf(false)
    var cloudSyncEnabled by mutableStateOf(false)
    var cloudSyncMessage by mutableStateOf("")
    var cloudConflict by mutableStateOf(false)
    private var cloudDecisionMade=false
    private var cloudReady=false
    private var cloudRevision:String?=null
    private var lastSyncedState:String?=null
    private var cloudJob:Job?=null
    private var syncEpoch=0
    private lateinit var initializationJob:Job
    val meals = mutableStateListOf<Meal>(); val finalists = mutableStateListOf<Meal>()
    val passed = mutableStateListOf<String>(); val savedMeals = mutableStateListOf<Meal>()
    val saved get() = savedMeals.map { it.id }
    val addresses = mutableStateListOf<DeliveryAddress>(); val restaurants = mutableStateListOf<Restaurant>()
    var addressPage by mutableStateOf(1); var moreAddresses by mutableStateOf(false)
    var winner by mutableStateOf<Meal?>(null); var drawOrder by mutableStateOf<List<Meal>>(emptyList())
    var shuffling by mutableStateOf(false); var canPick by mutableStateOf(false); var pickedIndex by mutableStateOf<Int?>(null)
    var receipt by mutableStateOf(""); var lastPassed by mutableStateOf<Meal?>(null)
    var assistantLoading by mutableStateOf(false)
    var assistantConsent by mutableStateOf(false)
    var assistantConsentPending by mutableStateOf(false)
    var pendingAssistantText by mutableStateOf("")
    val chatMessages=mutableStateListOf<ChatMessage>()
    var assistantAction by mutableStateOf<Pair<String,String>?>(null)
    var paymentOptions by mutableStateOf<JSONObject?>(null)
    var selectedMethodId by mutableStateOf("")
    var checkoutRequestId by mutableStateOf<String?>(null)
    var checkoutCreatedAt by mutableStateOf(0L)
    var orderingEnabled by mutableStateOf(BuildConfig.LOCAL_DEMO)
    var connectionAvailable by mutableStateOf(BuildConfig.LOCAL_DEMO)
    var payment by mutableStateOf<JSONObject?>(null)
    var paymentStage by mutableStateOf("choose")
    var checkoutNote by mutableStateOf("")
    var order by mutableStateOf<JSONObject?>(null)
    var checkoutCart by mutableStateOf<JSONObject?>(null)
    var replaceCartPending by mutableStateOf(false)
    var mcpTrace by mutableStateOf<List<String>>(emptyList())
    private var paymentJob:Job?=null
    private val trackedOrders=mutableSetOf<String>()
    private val eatenOrders=mutableSetOf<String>()
    var loading by mutableStateOf(false); var connectionMessage by mutableStateOf("")
    var cartRefreshRequired by mutableStateOf(false)
    var discoveryAttempted by mutableStateOf(false)
    var discoveryError by mutableStateOf<String?>(null)
    var accountOpen by mutableStateOf(false); var connected by mutableStateOf(false)
    var signedIn by mutableStateOf(api.signedIn); val configured get() = api.configured
    var environment by mutableStateOf(""); var addressId by mutableStateOf<String?>(null)
    var cart by mutableStateOf<JSONObject?>(null); var coupons by mutableStateOf<JSONObject?>(null)
    var savedConsent by mutableStateOf(false); var pendingSave by mutableStateOf<Meal?>(null)
    private var savedConsentAt = 0L
    private var persistenceJob: Job? = null
    var privacyAction by mutableStateOf<String?>(null)
    var privacyVersion by mutableStateOf(if(BuildConfig.LOCAL_DEMO)"2026-10-04.1" else "2026-10-02.3")
    private var owner = api.userId ?: "device"
    private var foodDay = LocalDate.now().toString()
    private var ready by mutableStateOf(false)
    private var undoUpdate by mutableStateOf<(() -> Unit)?>(null)
    val canUndoInput get() = undoUpdate != null
    val reservedSpend get() = if(paymentStage in listOf("placing","pending","unresolved")) (checkoutCart?.optDouble("payable",0.0)?:0.0).roundToInt() else 0
    val moneyLeft get() = allowance-spent-reservedSpend
    val caloriesLeft get() = Span(calorieGoal-intake.calories.high,calorieGoal-intake.calories.low)
    val proteinLeft get() = Span(proteinGoal-intake.protein.high,proteinGoal-intake.protein.low)
    val reservedCalories get() = Span(0,0)
    val mealMoneyGuide get() = moneyLeft/opportunities.coerceAtLeast(1)
    val pool get() = if(opportunities<=0) emptyList() else meals.filter {
        it.id !in passed && finalists.none { m -> m.id == it.id } && (!vegOnly || it.veg == true) &&
            (!budgetOnly || (it.itemPrice != null && it.itemPrice <= 250))
    }.sortedByDescending {
        (if(taste in it.tags) 28.0 else 0.0) + (if(fast && it.etaMinutes != null) 45.0-it.etaMinutes else 0.0) -
            ((it.estimatedPayable ?: it.itemPrice ?: mealMoneyGuide.toDouble())-mealMoneyGuide).coerceAtLeast(0.0)/7 +
            (if(highProtein) (it.nutrition?.protein?.mid?:0f)*1.1 else 0.0) -
            kotlin.math.abs((it.nutrition?.protein?.mid?:proteinLeft.mid/opportunities.coerceAtLeast(1))-proteinLeft.mid.coerceAtLeast(0f)/opportunities.coerceAtLeast(1))/5 -
            ((it.nutrition?.carbs?.high?:0)-(carbGoal-intake.carbs.mid).coerceAtLeast(0f)/opportunities.coerceAtLeast(1)).coerceAtLeast(0f)/6 -
            ((it.nutrition?.fat?.high?:0)-(fatGoal-intake.fat.mid).coerceAtLeast(0f)/opportunities.coerceAtLeast(1)).coerceAtLeast(0f)/3 -
            (it.etaMinutes?:25)/8.0 -
            ((it.nutrition?.calories?.mid?:0f)-caloriesLeft.mid/opportunities.coerceAtLeast(1)).coerceAtLeast(0f)/30
    }.take(8)

    init {
        initializationJob=viewModelScope.launch {
            db.migrate(); loadLocal(); ready=true; initializing=false
            if(signedIn) refresh()
        }
        startPersistence()
    }
    private fun startPersistence() {
        persistenceJob=viewModelScope.launch {
            snapshotFlow { if(ready) TrackerRecord(owner,snapshot().toString()) else null }.filterNotNull().debounce(600).collect { value ->
                db.save(value)
                if(cloudReady && cloudSyncEnabled && signedIn && cloudState().toString()!=lastSyncedState) scheduleCloudSync()
            }
        }
    }
    private fun preferences()=JSONObject().put("displayName",displayName).put("taste",taste).put("query",query)
        .put("highProtein",highProtein).put("vegOnly",vegOnly).put("budgetOnly",budgetOnly).put("fast",fast)
    private fun cloudState()=tracker().put("preferences",preferences())
    private fun snapshot() = cloudState().put("offlineMode",offlineMode).put("cloudSyncEnabled",cloudSyncEnabled).put("cloudDecisionMade",cloudDecisionMade)
        .put("cloudRevision",cloudRevision?:JSONObject.NULL).put("lastSyncedState",lastSyncedState?:JSONObject.NULL)
        .put("checkoutCreatedAt",checkoutCreatedAt).put("checkoutRequestId",checkoutRequestId?:JSONObject.NULL).put("payment",payment?:JSONObject.NULL).put("checkoutCart",checkoutCart?:JSONObject.NULL).put("paymentStage",paymentStage)
        .put("trackedOrders",JSONArray(trackedOrders.toList())).put("eatenOrders",JSONArray(eatenOrders.toList()))
        .put("savedConsent",savedConsent).put("savedConsentAt",savedConsentAt).put("privacyVersion",privacyVersion).put("savedMeals",JSONArray(savedMeals.map { it.copy(imageUrl=null,itemPrice=null,etaMinutes=null,distanceKm=null,description="",offerText=null).json() }))
    private fun tracker() = JSONObject().put("day",foodDay).put("calorieGoal",calorieGoal).put("proteinGoal",proteinGoal)
        .put("carbGoal",carbGoal).put("fatGoal",fatGoal).put("allowance",allowance).put("spent",spent).put("opportunities",opportunities).put("intake",intake.json())
    private suspend fun loadLocal() {
        trackedOrders.clear();eatenOrders.clear();checkoutRequestId=null;payment=null;checkoutCart=null;paymentStage="choose";checkoutCreatedAt=0L
        val stored = try {db.get(owner)?.payload} catch(_:Exception) {receipt="Saved data could not be decrypted. You can erase it from Accounts.";null} ?: return
        try {
            val o=JSONObject(stored);privacyVersion=o.optString("privacyVersion",privacyVersion); applyState(o)
            checkoutCreatedAt=o.optLong("checkoutCreatedAt");checkoutRequestId=o.optString("checkoutRequestId").takeIf {it.isNotBlank()&&it!="null"};payment=o.optJSONObject("payment");checkoutCart=o.optJSONObject("checkoutCart");paymentStage=o.optString("paymentStage","choose")
            o.optJSONArray("trackedOrders")?.let {a->for(i in 0 until a.length())trackedOrders.add(a.getString(i))};o.optJSONArray("eatenOrders")?.let {a->for(i in 0 until a.length())eatenOrders.add(a.getString(i))}
            if(!signedIn)offlineMode=o.optBoolean("offlineMode")
            cloudSyncEnabled=o.optBoolean("cloudSyncEnabled") && o.optString("privacyVersion")==privacyVersion
            cloudDecisionMade=o.optBoolean("cloudDecisionMade") && o.optString("privacyVersion")==privacyVersion
            cloudRevision=o.optString("cloudRevision").takeIf {it.isNotBlank()&&it!="null"}
            lastSyncedState=o.optString("lastSyncedState").takeIf {it.isNotBlank()&&it!="null"}
            savedConsentAt=o.optLong("savedConsentAt")
            savedConsent=o.optBoolean("savedConsent")&&o.optString("privacyVersion")==privacyVersion&&System.currentTimeMillis()-savedConsentAt<30L*86400*1000; savedMeals.clear()
            if(savedConsent) o.optJSONArray("savedMeals")?.let { a -> for(i in 0 until a.length()) savedMeals.add(Meal.from(a.getJSONObject(i))) }
        } catch (_: Exception) { receipt="The saved tracker could not be loaded. Please check your day." }
    }
    private fun applyState(o:JSONObject) {
        calorieGoal=o.getInt("calorieGoal");proteinGoal=o.getInt("proteinGoal");carbGoal=o.getInt("carbGoal");fatGoal=o.getInt("fatGoal");allowance=o.getInt("allowance")
        if(o.getString("day")==LocalDate.now().toString()) {
            spent=o.getInt("spent");opportunities=o.getInt("opportunities");intake=Nutrition.from(o.getJSONObject("intake"))
        } else {spent=0;opportunities=2;intake=Nutrition.zero}
        foodDay=LocalDate.now().toString()
        o.optJSONObject("preferences")?.let {v->
            displayName=v.optString("displayName");taste=v.optString("taste","any");query=v.optString("query","healthy bowls")
            highProtein=v.optBoolean("highProtein");vegOnly=v.optBoolean("vegOnly");budgetOnly=v.optBoolean("budgetOnly");fast=v.optBoolean("fast")
        }
    }
    private fun resetCloud() {
        syncEpoch++;cloudJob?.cancel();cloudJob=null;cloudSyncEnabled=false;cloudReady=false;cloudConflict=false
        cloudRevision=null;lastSyncedState=null;cloudDecisionMade=false;cloudSetupPending=false;cloudSyncMessage=""
    }
    private suspend fun restoreCloud(force:Boolean=false) {
        if(cloudDecisionMade && !cloudSyncEnabled && !force) {
            cloudReady=false;cloudSetupPending=false;return
        }
        val localChanged=cloudSyncEnabled && lastSyncedState!=null && cloudState().toString()!=lastSyncedState
        val response=api.action("state_get")
        val revision=response.optString("updatedAt").takeIf {it.isNotBlank()&&it!="null"}
        val state=response.optJSONObject("state")
        if(!force && localChanged && revision!=cloudRevision) {
            cloudReady=false;cloudConflict=true;cloudSyncMessage="This profile changed on another device. Choose a copy in Accounts.";return
        }
        if(state!=null && (force || !localChanged)) {
            applyState(state);lastSyncedState=cloudState().toString()
            cloudSyncEnabled=cloudSyncEnabled && response.optString("consentVersion")==privacyVersion && state.has("preferences")
            if(cloudSyncEnabled)cloudDecisionMade=true
            cloudSyncMessage="Profile restored from your Hungii account."
        } else if(state==null && cloudRevision!=null) {
            // Another device's deletion or expiry is not permission to recreate a copy.
            cloudSyncEnabled=false;cloudSyncMessage="Cloud copy is absent. Allow sync again to create a new copy."
        }
        cloudRevision=revision;cloudReady=true;cloudConflict=false
        cloudSetupPending=!cloudDecisionMade
        if(cloudSyncEnabled && cloudState().toString()!=lastSyncedState)scheduleCloudSync()
    }
    private fun scheduleCloudSync() {
        cloudJob?.cancel()
        cloudJob=viewModelScope.launch {
            delay(1000)
            while(loading)delay(200)
            if(cloudReady && cloudSyncEnabled && signedIn) syncTracker()
        }
    }
    fun useOffline() {offlineMode=true;cloudDecisionMade=true;cloudSyncEnabled=false;cloudSetupPending=false}
    fun keepDeviceOnly() {cloudDecisionMade=true;cloudSetupPending=false;cloudSyncEnabled=false}
    fun pauseCloudSync() {syncEpoch++;cloudJob?.cancel();cloudSyncEnabled=false;cloudReady=false;cloudDecisionMade=true;cloudSetupPending=false;cloudSyncMessage="Cloud sync is off. Your profile stays on this device."}
    fun restoreProfile()=run {restoreCloud(force=true);cloudDecisionMade=true;cloudSetupPending=false}
    fun useCloudCopy()=run {cloudSyncEnabled=true;cloudDecisionMade=true;restoreCloud(force=true)}
    fun keepLocalCopy()=run {
        val response=api.action("state_get")
        cloudRevision=response.optString("updatedAt").takeIf {it.isNotBlank()&&it!="null"}
        cloudConflict=false;cloudReady=true;pushCloudState()
    }
    private suspend fun pushCloudState() {
        val epoch=syncEpoch
        val current=cloudState().toString()
        val response=api.action("state_save",JSONObject().put("state",JSONObject(current)).put("privacyVersion",privacyVersion).put("expectedUpdatedAt",cloudRevision?:JSONObject.NULL))
        if(epoch!=syncEpoch)return
        cloudRevision=response.getString("updatedAt");lastSyncedState=current;cloudReady=true;cloudConflict=false
        cloudSyncEnabled=true;cloudDecisionMade=true;cloudSetupPending=false;cloudSyncMessage="Profile and preferences synced."
    }
    private fun run(onFailure: ((String) -> Unit)? = null, block: suspend () -> Unit) {
        if(loading) return
        viewModelScope.launch {
            loading=true; connectionMessage=""
            try { block() } catch(e: ApiFailure) {
                connectionMessage=e.message; onFailure?.invoke(e.message)
                if(e.code=="HUNGII_SYNC_CONFLICT") {cloudConflict=true;cloudReady=false;cloudSyncMessage=e.message}
                if(e.code=="HUNGII_LOGIN_REQUIRED") {
                    resetCloud();api.clearSession(); signedIn=false; clearProviderData()
                    persistenceJob?.cancelAndJoin(); ready=false; owner="device"; resetTracker(); loadLocal(); ready=true; startPersistence()
                }
                if(e.code=="HUNGII_RECONNECT") { connected=false; addressId=null; meals.clear(); finalists.clear(); winner=null; restaurants.clear(); cart=null; coupons=null }
            } catch (_: Exception) { connectionMessage="Could not complete this request. Try again."; onFailure?.invoke(connectionMessage) }
            finally { loading=false; accountLoading=false }
        }
    }
    fun signInUrl() = api.signInUrl()
    fun handleCallback(uri: Uri) = run {
        if(uri.host=="auth-return") {
            accountLoading=true
            initializationJob.join()
            api.callback(uri); signedIn=true;offlineMode=false;resetCloud()
            persistenceJob?.cancelAndJoin();ready=false; owner=api.userId ?: "device"; resetTracker(); savedMeals.clear(); savedConsent=false; loadLocal(); ready=true;startPersistence()
        }
        try {if(!BuildConfig.LOCAL_DEMO)restoreCloud();refreshConnection();if(!BuildConfig.LOCAL_DEMO&&(orderingEnabled||checkoutRequestId!=null)){val resumed=api.action("checkout_resume");if(resumed.optJSONObject("payment")!=null) restoreCheckout(resumed,false)}} finally {accountLoading=false}
    }
    private fun resetTracker() {
        foodDay=LocalDate.now().toString(); calorieGoal=2200; proteinGoal=140; carbGoal=250; fatGoal=70
        allowance=600; spent=0; opportunities=2; intake=Nutrition.zero
        displayName="";taste="any";query="healthy bowls";highProtein=false;vegOnly=false;budgetOnly=false;fast=false
    }
    suspend fun refreshConnection() {
        val status=api.action("status")
        val notice=status.optString("privacyVersion",if(BuildConfig.LOCAL_DEMO)"2026-10-04.1" else "2026-10-02.3")
        if(notice!=privacyVersion){cloudSyncEnabled=false;cloudReady=false;cloudDecisionMade=false;cloudJob?.cancel();clearSaved();privacyVersion=notice;cloudSyncMessage="Read the updated privacy notice to enable sync again."}
        connected=status.getBoolean("connected"); addressId=status.optString("addressId").takeIf { it.isNotBlank()&&it!="null" }; environment=status.optString("environment");orderingEnabled=BuildConfig.LOCAL_DEMO||status.optBoolean("orderingEnabled");connectionAvailable=BuildConfig.LOCAL_DEMO||status.optBoolean("connectionAvailable")
        if(!connected) { meals.clear(); finalists.clear(); restaurants.clear(); winner=null; cart=null; coupons=null }
        if(connected&&addressId==null) {
            loadAddresses()
            if(BuildConfig.LOCAL_DEMO&&addresses.isNotEmpty()){addressId=addresses.first().id;api.action("select_address",JSONObject().put("addressId",addressId))}
        }
    }
    fun refresh() = run {
        accountLoading=true
        try {if(!BuildConfig.LOCAL_DEMO)restoreCloud();refreshConnection();if(!BuildConfig.LOCAL_DEMO&&(orderingEnabled||checkoutRequestId!=null)){val resumed=api.action("checkout_resume");if(resumed.optJSONObject("payment")!=null) restoreCheckout(resumed,false)}} finally {accountLoading=false}
    }
    fun connect(onUrl: (String)->Unit) = run {
        val response=api.action("connect",JSONObject().put("consent",true).put("privacyVersion",privacyVersion))
        if(BuildConfig.LOCAL_DEMO) refreshConnection() else onUrl(response.getString("authorizationUrl"))
    }
    private suspend fun loadAddresses(page: Int=1) {
        val result=api.action("addresses",JSONObject().put("page",page)); addresses.clear()
        val a=result.getJSONArray("addresses"); for(i in 0 until a.length()) { val o=a.getJSONObject(i); addresses.add(DeliveryAddress(o.getString("id"),o.getString("label"),o.getString("addressLine"))) }
        addressPage=page; moreAddresses=result.getJSONObject("pagination").getBoolean("hasMore")
    }
    fun addressList(page: Int=1) = run { loadAddresses(page); accountOpen=true }
    fun selectAddress(address: DeliveryAddress) = run {
        api.action("select_address",JSONObject().put("addressId",address.id).put("page",addressPage)); addressId=address.id
        meals.clear(); finalists.clear(); passed.clear(); restaurants.clear(); winner=null; cart=null; coupons=null; accountOpen=false
    }
    fun search() {
        screen=Screen.Discover
        if(!signedIn||!connected||addressId==null) { accountOpen=true; return }
        run(onFailure = { discoveryError = it }) {
            discoveryError=null; discoveryAttempted=true
            finalists.clear(); passed.clear(); lastPassed=null; winner=null; cart=null; coupons=null
            val args=JSONObject().put("query",query)
            if(highProtein) args.put("collection","EATRIGHT") else if(fast) args.put("collection","BOLT")
            val result=api.action("discover",args); meals.clear(); restaurants.clear();if(result.optBoolean("expandedQuery"))connectionMessage="Added nearby alternatives for your shortlist. Craving and macro trade-offs stay visible."
            val a=result.getJSONArray("meals"); for(i in 0 until a.length()) meals.add(Meal.from(a.getJSONObject(i)))
            val rs=result.getJSONArray("restaurants"); for(i in 0 until rs.length()) { val r=rs.getJSONObject(i); restaurants.add(Restaurant(r.getString("id"),r.getString("name"),if(r.isNull("etaMinutes")) null else r.getInt("etaMinutes"),if(r.isNull("distanceKm")) null else r.getDouble("distanceKm"))) }
        }
    }
    fun restaurantMeals(restaurant: Restaurant) = run(onFailure = { discoveryError = it }) {
        discoveryError=null; discoveryAttempted=true
        val result=api.action("menu",JSONObject().put("query",query).put("restaurantId",restaurant.id).put("vegOnly",vegOnly))
        meals.clear(); passed.clear(); lastPassed=null
        val a=result.getJSONArray("meals"); for(i in 0 until a.length()) meals.add(Meal.from(a.getJSONObject(i)).copy(etaMinutes=restaurant.etaMinutes,distanceKm=restaurant.distanceKm))
    }
    fun review(replace:Boolean=false) {
        screen=Screen.Review; coupons=null
        val meal=winner ?: return
        if(!orderingEnabled){cart=null;return}
        run(onFailure = { cartRefreshRequired = true }) {
            try {applyCart(api.action("cart_create",JSONObject().put("dishId",meal.dishId).put("restaurantId",meal.restaurantId).put("query",meal.name).put("replace",replace)))}
            catch(e:ApiFailure){if(e.code=="HUNGII_REPLACE_CART"){replaceCartPending=true;return@run}else throw e}
            coupons=api.action("coupons",JSONObject().put("restaurantId",meal.restaurantId))
        }
    }
    private fun trace(result:JSONObject){result.optJSONArray("mcpTrace")?.let {a->mcpTrace=(0 until a.length()).map {a.getString(it)}}}
    private fun applyCart(result:JSONObject){cartRefreshRequired=false;cart=result.optJSONObject("cart")?:result;trace(result)}
    fun refreshCart()=run(onFailure = { cartRefreshRequired = true }) {applyCart(api.action("cart"))}
    fun changeQuantity(id:String,quantity:Int)=run(onFailure = { cartRefreshRequired = true }) {
        val items=cart?.optJSONArray("items")?:JSONArray();val name=(0 until items.length()).map {items.getJSONObject(it)}.firstOrNull {it.optString("menuItemId")==id}?.optString("name")?:id
        applyCart(api.action("cart_quantity",JSONObject().put("dishId",id).put("query",name).put("quantity",quantity)))
    }
    fun changeVariant(id:String,group:String,variant:String)=run(onFailure = { cartRefreshRequired = true }) {applyCart(api.action("cart_variant",JSONObject().put("dishId",id).put("groupId",group).put("variationId",variant)))}
    fun changeAddons(id:String,addons:JSONArray)=run(onFailure = { cartRefreshRequired = true }) {applyCart(api.action("cart_addons",JSONObject().put("dishId",id).put("addons",addons)))}
    fun addSide(id:String)=run(onFailure = { cartRefreshRequired = true }) {applyCart(api.action("cart_add_side",JSONObject().put("dishId",id)))}
    fun applyCoupon(code:String)=run(onFailure = { cartRefreshRequired = true }) {applyCart(api.action("cart_coupon",JSONObject().put("couponCode",code)))}
    fun choosePayment(){
        if(paymentStage in listOf("pending","unresolved","placing")){screen=Screen.Payment;return}
        paymentOptions=null;payment=null;checkoutRequestId=null;selectedMethodId="";paymentStage="choose";screen=Screen.Payment
        run(onFailure = { cartRefreshRequired = true }) {val result=api.action("payment_options");paymentOptions=result.getJSONObject("paymentOptions");applyCart(result)}
    }
    fun beginPayment()=run(onFailure={if(paymentStage=="placing")paymentStage="unresolved"}) {
        val c=cart?:return@run
        checkoutRequestId=checkoutRequestId?:java.util.UUID.randomUUID().toString();checkoutCreatedAt=System.currentTimeMillis();paymentStage="placing";checkoutCart=c
        // Save before the network call, rather than waiting for debounced tracker persistence.
        db.save(TrackerRecord(owner,snapshot().toString()))
        val args=JSONObject().put("confirm",true).put("requestId",checkoutRequestId).put("revision",c.get("revision")).put("quote",c.optString("quote")).put("payable",c.getDouble("payable")).put("methodId",selectedMethodId).put("note",checkoutNote)
        try {restoreCheckout(api.action("checkout",args),true)} catch(e:ApiFailure){
            if(e.code in listOf("HUNGII_CART_CHANGED","HUNGII_QUOTE_EXPIRED","HUNGII_PAYMENT_CHANGED","HUNGII_CART_INVALID","HUNGII_CONFIRM_REQUIRED","HUNGII_ORDERING_PENDING")){checkoutRequestId=null;paymentStage="choose";cartRefreshRequired=true}
            throw e
        }
    }
    private suspend fun restoreCheckout(result:JSONObject,navigate:Boolean){
        payment=result.optJSONObject("payment")?:return;checkoutCreatedAt=result.optLong("createdAt",checkoutCreatedAt);checkoutCart=result.optJSONObject("checkoutCart")?:checkoutCart;checkoutRequestId=result.optString("requestId").takeIf {it.isNotBlank()}?:checkoutRequestId;trace(result)
        when(payment!!.optString("status")){
            "CONFIRMED"->{paymentStage="confirmed";completeOrder(payment!!.getString("orderId"),navigate)}
            "PENDING_PAYMENT"->{paymentStage="pending";if(navigate)screen=Screen.Payment;startPaymentPolling()}
            "FAILED"->{paymentStage="failed";if(navigate)screen=Screen.Payment}
            else->{paymentStage="unresolved";if(navigate)screen=Screen.Payment}
        }
    }
    private fun startPaymentPolling(){
        paymentJob?.cancel();paymentJob=viewModelScope.launch {
            val window=(payment?.optLong("maxTimeToPollForInMs",60000)?:60000).coerceIn(1000,600000);val until=System.currentTimeMillis()+window
            while(paymentStage=="pending"&&System.currentTimeMillis()<until){
                delay(if(BuildConfig.LOCAL_DEMO)1600 else (payment?.optLong("pollingIntervalInMs",5000)?:5000).coerceAtLeast(1000))
                try{consumePayment(api.action("payment_status",paymentArgs()))}catch(_:Exception){connectionMessage="Payment status is unresolved. Check status before ordering again.";paymentStage="unresolved";break}
            }
            if(paymentStage=="pending"){paymentStage="unresolved";connectionMessage="Payment is taking longer than expected. Check status before ordering again."}
        }
    }
    private fun paymentArgs():JSONObject {
        val args=JSONObject().put("requestId",checkoutRequestId)
        if(BuildConfig.LOCAL_DEMO)payment?.let {p->listOf("paasId","orderId","cartId").forEach {args.put(it,p.optString(it))}}
        return args
    }
    fun simulatePayment(outcome:String)=run {consumePayment(api.action("simulate_payment",paymentArgs().put("outcome",outcome)))}
    fun resumeCheckout()=run {
        screen=Screen.Payment
        if(BuildConfig.LOCAL_DEMO)consumePayment(api.action("payment_status",paymentArgs())) else {val result=api.action("checkout_resume");if(result.optJSONObject("payment")==null){paymentStage="unresolved";connectionMessage="Checkout could not be recovered. Check recent Swiggy orders before trying again."}else restoreCheckout(result,true)}
    }
    fun refreshPayment()=run {
        if(payment==null&&!BuildConfig.LOCAL_DEMO){val result=api.action("checkout_resume");if(result.optJSONObject("payment")!=null)restoreCheckout(result,true)}
        else consumePayment(api.action("payment_status",paymentArgs()))
    }
    private suspend fun consumePayment(result:JSONObject){
        trace(result);val status=result.getJSONObject("paymentStatus")
        if(!BuildConfig.LOCAL_DEMO){result.optJSONObject("payment")?.let {payment=it};result.optJSONObject("checkoutCart")?.let{checkoutCart=it}}
        if(status.optBoolean("confirmed")){paymentStage="confirmed";completeOrder(status.getString("orderId"))}
        else if(status.optBoolean("isTerminalFailure")){paymentStage="failed";connectionMessage="Payment did not complete. Review the latest basket before another attempt."}
        else if(!BuildConfig.LOCAL_DEMO&&result.optJSONObject("payment")?.optString("status")=="UNRESOLVED")paymentStage="unresolved"
        else paymentStage="pending"
    }
    private suspend fun completeOrder(id:String,navigate:Boolean=true){
        // Confirmation is known independently of whether tracking is temporarily unavailable.
        paymentStage="confirmed";order=JSONObject().put("orderId",id)
        if(trackedOrders.add(id)){if(checkoutCreatedAt>0&&java.time.Instant.ofEpochMilli(checkoutCreatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()==foodDay)spent+=(checkoutCart?.optDouble("payable")?:0.0).roundToInt();db.save(TrackerRecord(owner,snapshot().toString()))}
        if(navigate)screen=Screen.Order
        try{order=api.action("order",JSONObject().put("orderId",id));trace(order!!)}catch(_:Exception){connectionMessage="Order confirmed. Tracking is temporarily unavailable; refresh or check Swiggy."}
    }
    fun refreshOrder()=run {val id=payment?.optString("orderId")?:order?.optString("orderId")?:return@run;order=api.action("order",JSONObject().put("orderId",id))}
    fun logOrderAsEaten(){
        if(!BuildConfig.LOCAL_DEMO){screen=Screen.Day;receipt="Add the portion you ate. Restaurant nutrition is unavailable; Hungii has not guessed it.";return}
        val id=payment?.optString("orderId")?:return;if(!eatenOrders.add(id)){receipt="This meal is already in your food log.";return};val items=checkoutCart?.optJSONArray("items")?:return
        for(i in 0 until items.length()){items.getJSONObject(i).optJSONObject("nutrition")?.let {intake+=Nutrition.from(it)}};opportunities=(opportunities-1).coerceAtLeast(0);receipt="Synthetic meal estimate logged. Your next meal budgets are updated.";screen=Screen.Day
    }
    fun retryPayment(){if(paymentStage!="failed")return;paymentJob?.cancel();payment=null;paymentStage="choose";choosePayment()}
    fun sendAssistantMessage(text:String){val message=text.trim().take(1500);if(message.isBlank()||assistantLoading)return;screen=Screen.Assistant;if(!assistantConsent){pendingAssistantText=message;assistantConsentPending=true;return};chatMessages.add(ChatMessage(message,true));assistantLoading=true;viewModelScope.launch {try{val context=JSONObject().put("caloriesLeft",caloriesLeft.label).put("proteinLeft",proteinGoal-intake.protein.mid).put("carbsLeft",carbGoal-intake.carbs.mid).put("fatLeft",fatGoal-intake.fat.mid).put("moneyLeft",moneyLeft).put("allowance",allowance).put("spent",spent).put("goals",JSONObject().put("calories",calorieGoal).put("protein",proteinGoal).put("carbs",carbGoal).put("fat",fatGoal)).put("opportunities",opportunities).put("query",query).put("taste",taste).put("screen",screen.name).put("winner",winner?.name?:"");val result=api.action("agent_chat",JSONObject().put("message",message).put("context",context).put("history",JSONArray().also {a->chatMessages.dropLast(1).takeLast(4).forEach {a.put(JSONObject().put("role",if(it.fromUser)"user" else "assistant").put("text",it.text.take(1500)))}}).put("consent",true));val a=result.optJSONArray("actions");val actions=if(a==null)emptyList() else (0 until a.length()).map {a.getJSONObject(it).let {o->o.getString("action") to o.optString("value")}};chatMessages.add(ChatMessage(result.getString("reply"),actions=actions));trace(result)}catch(e:ApiFailure){chatMessages.add(ChatMessage(e.message))}catch(_:Exception){chatMessages.add(ChatMessage("I couldn’t connect to free inference. Your manual filters and cart still work."))}finally{assistantLoading=false}}}
    fun acceptAssistant(){assistantConsent=true;assistantConsentPending=false;val text=pendingAssistantText;pendingAssistantText="";sendAssistantMessage(text)}
    fun applyAssistantAction(){val (action,value)=assistantAction?:return;assistantAction=null;val n=value.toIntOrNull();when(action){"search"->{query=value.take(120);search()};"set_allowance"->if(n!=null&&n in 0..100000)allowance=n;"set_opportunities"->if(n!=null&&n in 0..8)opportunities=n;"set_calorie_goal"->if(n!=null&&n in 500..10000)calorieGoal=n;"log_calories"->if(n!=null&&n in 0..10000)intake=intake.copy(calories=intake.calories+Span(n,n));"set_protein_goal"->if(n!=null&&n in 1..1000)proteinGoal=n;"set_carb_goal"->if(n!=null&&n in 1..1500)carbGoal=n;"set_fat_goal"->if(n!=null&&n in 1..1000)fatGoal=n;"log_carbs"->if(n!=null&&n in 0..1500)intake=intake.copy(carbs=intake.carbs+Span(n,n));"log_fat"->if(n!=null&&n in 0..1000)intake=intake.copy(fat=intake.fat+Span(n,n));"log_spending"->if(n!=null&&n in 0..100000)spent+=n;"log_protein"->if(n!=null&&n in 0..1000)intake=intake.copy(protein=intake.protein+Span(n,n));"set_taste"->if(value in listOf("any","spicy","cheesy","sweet","bland","light","filling"))taste=value;"navigate_home"->screen=Screen.Home;"navigate_day"->screen=Screen.Day;"navigate_saved"->screen=Screen.Saved;"review_cart"->if(winner!=null)review();"save_winner"->winner?.let {toggleSaved(it)}};receipt="Assistant change confirmed."}
    private fun clearSaved() {savedMeals.clear();savedConsent=false;savedConsentAt=0}
    private fun clearProviderData() {paymentJob?.cancel();payment=null;order=null;checkoutCart=null;checkoutRequestId=null;paymentStage="choose";paymentOptions=null;orderingEnabled=BuildConfig.LOCAL_DEMO;cartRefreshRequired=false;discoveryAttempted=false;discoveryError=null;connected=false;addressId=null;meals.clear();finalists.clear();winner=null;restaurants.clear();cart=null;coupons=null;addresses.clear();drawOrder=emptyList();passed.clear();lastPassed=null;pendingSave=null;clearSaved()}
    fun disconnect() = run {
        val result=api.action("disconnect",JSONObject().put("confirm",true))
        persistenceJob?.cancelAndJoin();ready=false;clearProviderData();db.erase(owner);db.save(TrackerRecord(owner,snapshot().toString()));ready=true;startPersistence()
        connectionMessage=if(result.optBoolean("revocationConfirmed")) "Disconnected and erased saved Swiggy data." else "Disconnected and erased Hungii's copy. Remote revocation could not be confirmed; check Swiggy account access."
    }
    fun signOut(onUrl:(String)->Unit) = run {
        try {api.signOut()?.let(onUrl)} catch (_: Exception) { connectionMessage="Signed out on this device. The remote sign-out could not be confirmed." }
        signedIn=false;resetCloud();clearProviderData()
        persistenceJob?.cancelAndJoin();ready=false; owner=if(BuildConfig.LOCAL_DEMO) "local-demo" else "device"; resetTracker(); savedMeals.clear(); savedConsent=false; loadLocal(); ready=true;startPersistence()
    }
    fun syncTracker() = run {
        if(!cloudReady) {
            val response=api.action("state_get")
            cloudRevision=response.optString("updatedAt").takeIf {it.isNotBlank()&&it!="null"}
            val remote=response.optJSONObject("state")
            if(remote!=null && remote.toString()!=cloudState().toString() && lastSyncedState!=cloudState().toString()) {
                cloudConflict=true;cloudSyncMessage="Choose whether to use the cloud profile or keep this device's profile.";return@run
            }
            cloudReady=true
        }
        if(cloudConflict)return@run
        pushCloudState();connectionMessage="Profile, preferences and tracker synced."
    }
    fun performPrivacyAction() {
        val action=privacyAction?:return;privacyAction=null
        run {
            if(action=="delete_cloud_tracker") {
                cloudJob?.cancel();api.action(action,JSONObject().put("confirm",true));resetCloud();cloudDecisionMade=true
                connectionMessage="Cloud profile, preferences and tracker erased. Device copy remains.";return@run
            }
            if(action=="delete_account") {
                val result=api.action(action,JSONObject().put("confirm",true))
                if(!result.optBoolean("deleted")) {
                    persistenceJob?.cancelAndJoin();ready=false;resetCloud();db.erase(owner);clearProviderData();resetTracker();ready=true;startPersistence()
                    connectionMessage="Hungii's cloud data was erased. Account-provider deletion is pending; retry Delete Hungii account.";return@run
                }
            }
            persistenceJob?.cancelAndJoin();ready=false
            db.erase(owner);clearProviderData();resetTracker()
            if(action=="delete_account") {resetCloud();api.clearSession();signedIn=false;owner="device";accountOpen=false}
            ready=true;startPersistence();connectionMessage="Selected data erased."
        }
    }
    fun like(m: Meal) { if(finalists.size<3&&finalists.none {it.id==m.id}) { finalists.add(m); lastPassed=null; if(finalists.size==3) screen=Screen.Finalists } }
    fun pass(m: Meal) { passed.add(m.id); lastPassed=m }
    fun undoSwipe() { lastPassed?.let {passed.remove(it.id); lastPassed=null} ?: if(finalists.isNotEmpty()) {finalists.removeAt(finalists.lastIndex);screen=Screen.Discover} else Unit }
    fun toggleSaved(m: Meal) { if(m.id in saved) savedMeals.removeAll {it.id==m.id} else if(savedConsent) savedMeals.add(m) else pendingSave=m }
    fun acceptSaving() { savedConsent=true;savedConsentAt=System.currentTimeMillis(); pendingSave?.let {savedMeals.add(it)}; pendingSave=null }
    fun forgetSaved() = run {persistenceJob?.cancelAndJoin();ready=false;clearSaved();db.erase(owner);db.save(TrackerRecord(owner,snapshot().toString()));ready=true;startPersistence()}
    fun remove(m: Meal) {finalists.remove(m);winner=null;screen=Screen.Discover}
    fun showFinalists() {if(finalists.isNotEmpty())screen=Screen.Finalists}
    fun startDraw() {if(finalists.size!=3||shuffling)return;drawOrder=finalists.shuffled(SecureRandom());winner=null;pickedIndex=null;canPick=false;shuffling=true;screen=Screen.Draw}
    fun pick(i: Int) {if(canPick&&!shuffling&&pickedIndex==null&&i in drawOrder.indices){pickedIndex=i;canPick=false;winner=drawOrder[i]}}
    fun goBack() {shuffling=false;screen=when(screen){Screen.Draw,Screen.Winner->Screen.Finalists;Screen.Review->Screen.Winner;Screen.Payment->Screen.Review;Screen.Order->Screen.Home;Screen.Finalists,Screen.Saved->Screen.Discover;else->Screen.Home}}
    fun invalidateCheckInUndo() {undoUpdate=null}
    fun update(input: String) {
        val oldAllowance=allowance;val oldOpportunities=opportunities;val oldTaste=taste;val oldQuery=query
        val raw=input.lowercase();val changes=mutableListOf<String>()
        Regex("(?:₹|rs\\.?\\s*|rupees?\\s*)(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { amount ->
            when {"left" in raw||"remaining" in raw->{allowance=spent+amount;changes.add("₹$amount left today.")};"daily" in raw||"budget" in raw||"allowance" in raw->{allowance=amount;changes.add("Allowance updated.")};else->changes.add("Add ‘left’ or ‘daily budget’ to that amount.")}
        }
        Regex("\\b([0-8])\\s*(?:more\\s+)?meals?\\s*(?:left|remaining)?").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let {opportunities=it;changes.add("$it opportunities left.")}
        listOf("spicy","cheesy","sweet","bland","light","filling").firstOrNull {it in raw}?.let {taste=it;query=it+" food";changes.add("Searching for $it food next.")}
        if(changes.isEmpty()) { query=input.trim().take(150);changes.add("Next search: $query. Log food quantities in My day; this check-in does not guess nutrition.") }
        undoUpdate={allowance=oldAllowance;opportunities=oldOpportunities;taste=oldTaste;query=oldQuery}
        receipt=changes.joinToString(" ")
    }
    fun undoInput() {undoUpdate?.invoke();undoUpdate=null;receipt="Last update undone."}
    override fun onCleared() {paymentJob?.cancel();db.close();super.onCleared()}
}
