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
    val description: String = "", val offerText: String? = null, val nutrition: Nutrition? = null) {
    val price get() = Span(itemPrice?.roundToInt() ?: 0,itemPrice?.roundToInt() ?: 0)
    val priceLabel get() = itemPrice?.let { "₹" + if(it == it.toInt().toDouble()) it.toInt().toString() else "%.2f".format(java.util.Locale.ROOT,it) } ?: "Price unavailable"
    val etaLabel get() = etaMinutes?.let { "$it min est." } ?: "ETA unavailable"
    val badge get() = "SWIGGY MENU"
    val tags get() = setOf("spicy","cheesy","sweet","bland","light","filling").filter { it in (name+" "+description).lowercase() }.toSet()
    val benefit get() = if(description.isNotBlank()) description else "${restaurant}. ${etaLabel}."
    val compromise get() = "Nutrition is not published here. Fees and coupon eligibility are checked in the cart."
    fun json() = JSONObject().put("id",id).put("dishId",dishId).put("restaurantId",restaurantId).put("name",name).put("restaurant",restaurant)
        .put("imageUrl",imageUrl ?: JSONObject.NULL).put("veg",veg ?: JSONObject.NULL).put("itemPrice",itemPrice ?: JSONObject.NULL)
        .put("etaMinutes",etaMinutes ?: JSONObject.NULL).put("distanceKm",distanceKm ?: JSONObject.NULL).put("description",description).put("offerText",offerText ?: JSONObject.NULL)
    companion object {
        fun from(o: JSONObject) = Meal(o.getString("id"),o.getString("dishId"),o.getString("restaurantId"),o.getString("name"),o.getString("restaurant"),
            o.optString("imageUrl").takeIf { it.startsWith("https://") },if(o.isNull("veg")) null else o.getBoolean("veg"),
            if(o.isNull("itemPrice")) null else o.getDouble("itemPrice"),if(o.isNull("etaMinutes")) null else o.getInt("etaMinutes"),
            if(o.isNull("distanceKm")) null else o.getDouble("distanceKm"),o.optString("description"),o.optString("offerText").takeIf { it.isNotBlank() && it != "null" })
    }
}
enum class Screen { Home, Discover, Finalists, Draw, Winner, Review, Saved, Day }
data class DeliveryAddress(val id: String,val label: String,val addressLine: String)
data class Restaurant(val id: String,val name: String,val etaMinutes: Int?,val distanceKm: Double?)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class HungiiModel(application: Application) : AndroidViewModel(application) {
    private val db = HungiiDatabase.open(application)
    private val api = HungiiApi(SecureSession(application))
    var screen by mutableStateOf(Screen.Home)
    var calorieGoal by mutableStateOf(2200); var proteinGoal by mutableStateOf(140)
    var carbGoal by mutableStateOf(250); var fatGoal by mutableStateOf(70)
    var allowance by mutableStateOf(600); var spent by mutableStateOf(0)
    var opportunities by mutableStateOf(2); var intake by mutableStateOf(Nutrition.zero)
    var taste by mutableStateOf("any"); var highProtein by mutableStateOf(false)
    var vegOnly by mutableStateOf(false); var budgetOnly by mutableStateOf(false); var fast by mutableStateOf(false)
    var query by mutableStateOf("healthy bowls")
    val meals = mutableStateListOf<Meal>(); val finalists = mutableStateListOf<Meal>()
    val passed = mutableStateListOf<String>(); val savedMeals = mutableStateListOf<Meal>()
    val saved get() = savedMeals.map { it.id }
    val addresses = mutableStateListOf<DeliveryAddress>(); val restaurants = mutableStateListOf<Restaurant>()
    var addressPage by mutableStateOf(1); var moreAddresses by mutableStateOf(false)
    var winner by mutableStateOf<Meal?>(null); var drawOrder by mutableStateOf<List<Meal>>(emptyList())
    var shuffling by mutableStateOf(false); var canPick by mutableStateOf(false); var pickedIndex by mutableStateOf<Int?>(null)
    var receipt by mutableStateOf(""); var lastPassed by mutableStateOf<Meal?>(null)
    var loading by mutableStateOf(false); var connectionMessage by mutableStateOf("")
    var accountOpen by mutableStateOf(false); var connected by mutableStateOf(false)
    var signedIn by mutableStateOf(api.signedIn); val configured get() = api.configured
    var environment by mutableStateOf(""); var addressId by mutableStateOf<String?>(null)
    var cart by mutableStateOf<JSONObject?>(null); var coupons by mutableStateOf<JSONObject?>(null)
    var savedConsent by mutableStateOf(false); var pendingSave by mutableStateOf<Meal?>(null)
    private var owner = api.userId ?: "device"
    private var foodDay = LocalDate.now().toString()
    private var ready by mutableStateOf(false)
    private var undoUpdate by mutableStateOf<(() -> Unit)?>(null)
    val canUndoInput get() = undoUpdate != null
    val moneyLeft get() = allowance-spent
    val caloriesLeft get() = Span(calorieGoal-intake.calories.high,calorieGoal-intake.calories.low)
    val proteinLeft get() = Span(proteinGoal-intake.protein.high,proteinGoal-intake.protein.low)
    val reservedCalories get() = Span(0,0)
    val mealMoneyGuide get() = moneyLeft/opportunities.coerceAtLeast(1)
    val pool get() = if(opportunities<=0) emptyList() else meals.filter {
        it.id !in passed && finalists.none { m -> m.id == it.id } && (!vegOnly || it.veg == true) &&
            (!budgetOnly || (it.itemPrice != null && it.itemPrice <= 250)) && (it.itemPrice == null || it.itemPrice <= moneyLeft)
    }.sortedByDescending {
        (if(taste in it.tags) 28.0 else 0.0) + (if(fast && it.etaMinutes != null) 45.0-it.etaMinutes else 0.0) -
            ((it.itemPrice ?: mealMoneyGuide.toDouble())-mealMoneyGuide).coerceAtLeast(0.0)/7
    }

    init {
        viewModelScope.launch { loadLocal(); ready=true; if(signedIn) refresh() }
        viewModelScope.launch {
            snapshotFlow { if(ready) TrackerRecord(owner,snapshot().toString()) else null }.filterNotNull().debounce(600).collect { value ->
                db.tracker().save(value)
            }
        }
    }
    private fun snapshot() = tracker().put("savedConsent",savedConsent).put("savedMeals",JSONArray(savedMeals.map { it.json() }))
    private fun tracker() = JSONObject().put("day",foodDay).put("calorieGoal",calorieGoal).put("proteinGoal",proteinGoal)
        .put("carbGoal",carbGoal).put("fatGoal",fatGoal).put("allowance",allowance).put("spent",spent).put("opportunities",opportunities).put("intake",intake.json())
    private suspend fun loadLocal() {
        val stored = db.tracker().get(owner)?.payload ?: return
        try {
            val o=JSONObject(stored); calorieGoal=o.getInt("calorieGoal"); proteinGoal=o.getInt("proteinGoal"); carbGoal=o.getInt("carbGoal"); fatGoal=o.getInt("fatGoal"); allowance=o.getInt("allowance")
            if(o.getString("day")==LocalDate.now().toString()) { spent=o.getInt("spent"); opportunities=o.getInt("opportunities"); intake=Nutrition.from(o.getJSONObject("intake")) }
            savedConsent=o.optBoolean("savedConsent"); savedMeals.clear()
            if(savedConsent) o.optJSONArray("savedMeals")?.let { a -> for(i in 0 until a.length()) savedMeals.add(Meal.from(a.getJSONObject(i))) }
        } catch (_: Exception) { receipt="The saved tracker could not be loaded. Please check your day." }
    }
    private fun run(block: suspend () -> Unit) {
        if(loading) return
        viewModelScope.launch {
            loading=true; connectionMessage=""
            try { block() } catch(e: ApiFailure) {
                connectionMessage=e.message
                if(e.code=="HUNGII_RECONNECT") { connected=false; addressId=null; meals.clear(); finalists.clear(); winner=null; restaurants.clear(); cart=null; coupons=null }
            } catch (_: Exception) { connectionMessage="Could not complete this request. Try again." }
            finally { loading=false }
        }
    }
    fun signInUrl() = api.signInUrl()
    fun handleCallback(uri: Uri) = run {
        if(uri.host=="auth-return") {
            api.callback(uri); signedIn=true
            ready=false; owner=api.userId ?: "device"; resetTracker(); savedMeals.clear(); savedConsent=false; loadLocal(); ready=true
        }
        refreshConnection()
    }
    private fun resetTracker() {
        foodDay=LocalDate.now().toString(); calorieGoal=2200; proteinGoal=140; carbGoal=250; fatGoal=70
        allowance=600; spent=0; opportunities=2; intake=Nutrition.zero
    }
    suspend fun refreshConnection() {
        val status=api.action("status"); connected=status.getBoolean("connected"); addressId=status.optString("addressId").takeIf { it.isNotBlank()&&it!="null" }; environment=status.optString("environment")
        if(!connected) { meals.clear(); finalists.clear(); restaurants.clear(); winner=null; cart=null; coupons=null }
        if(connected&&addressId==null) loadAddresses()
    }
    fun refresh() = run { refreshConnection() }
    fun connect(onUrl: (String)->Unit) = run { onUrl(api.action("connect",JSONObject().put("consent",true)).getString("authorizationUrl")) }
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
        run {
            finalists.clear(); passed.clear(); winner=null; cart=null; coupons=null
            val args=JSONObject().put("query",query)
            if(highProtein) args.put("collection","EATRIGHT") else if(fast) args.put("collection","BOLT")
            val result=api.action("discover",args); meals.clear(); restaurants.clear()
            val a=result.getJSONArray("meals"); for(i in 0 until a.length()) meals.add(Meal.from(a.getJSONObject(i)))
            val rs=result.getJSONArray("restaurants"); for(i in 0 until rs.length()) { val r=rs.getJSONObject(i); restaurants.add(Restaurant(r.getString("id"),r.getString("name"),if(r.isNull("etaMinutes")) null else r.getInt("etaMinutes"),if(r.isNull("distanceKm")) null else r.getDouble("distanceKm"))) }
        }
    }
    fun restaurantMeals(restaurant: Restaurant) = run {
        val result=api.action("menu",JSONObject().put("query",query).put("restaurantId",restaurant.id).put("vegOnly",vegOnly))
        meals.clear(); passed.clear()
        val a=result.getJSONArray("meals"); for(i in 0 until a.length()) meals.add(Meal.from(a.getJSONObject(i)).copy(etaMinutes=restaurant.etaMinutes,distanceKm=restaurant.distanceKm))
    }
    fun review() {
        screen=Screen.Review; cart=null; coupons=null
        val meal=winner ?: return
        run {
            // Selection permits scoped details; still no cart mutation or order.
            api.action("menu",JSONObject().put("query",meal.name).put("restaurantId",meal.restaurantId).put("vegOnly",vegOnly))
            cart=api.action("cart")
            coupons=api.action("coupons",JSONObject().put("restaurantId",meal.restaurantId))
        }
    }
    fun disconnect() = run { api.action("disconnect"); connected=false; addressId=null; meals.clear(); finalists.clear(); winner=null; restaurants.clear(); cart=null; coupons=null; addresses.clear() }
    fun signOut() = run {
        try { api.signOut() } catch (_: Exception) { connectionMessage="Signed out on this device. The remote sign-out could not be confirmed." }
        signedIn=false; connected=false; addressId=null; meals.clear(); finalists.clear(); winner=null; restaurants.clear(); addresses.clear(); cart=null; coupons=null
        ready=false; owner="device"; resetTracker(); savedMeals.clear(); savedConsent=false; loadLocal(); ready=true
    }
    fun syncTracker() = run { api.action("state_save",JSONObject().put("state",tracker())); connectionMessage="Your tracker was synced." }
    fun like(m: Meal) { if(finalists.size<3&&finalists.none {it.id==m.id}) { finalists.add(m); lastPassed=null; if(finalists.size==3) screen=Screen.Finalists } }
    fun pass(m: Meal) { passed.add(m.id); lastPassed=m }
    fun undoSwipe() { lastPassed?.let {passed.remove(it.id); lastPassed=null} ?: if(finalists.isNotEmpty()) {finalists.removeAt(finalists.lastIndex);screen=Screen.Discover} else Unit }
    fun toggleSaved(m: Meal) { if(m.id in saved) savedMeals.removeAll {it.id==m.id} else if(savedConsent) savedMeals.add(m) else pendingSave=m }
    fun acceptSaving() { savedConsent=true; pendingSave?.let {savedMeals.add(it)}; pendingSave=null }
    fun forgetSaved() { savedMeals.clear(); savedConsent=false }
    fun remove(m: Meal) {finalists.remove(m);winner=null;screen=Screen.Discover}
    fun showFinalists() {if(finalists.size==1){winner=finalists[0];screen=Screen.Winner}else if(finalists.isNotEmpty()) screen=Screen.Finalists}
    fun startDraw() {if(finalists.size<2||shuffling)return;drawOrder=finalists.shuffled(SecureRandom());winner=null;pickedIndex=null;canPick=false;shuffling=true;screen=Screen.Draw}
    fun pick(i: Int) {if(canPick&&!shuffling&&pickedIndex==null&&i in drawOrder.indices){pickedIndex=i;canPick=false;winner=drawOrder[i]}}
    fun goBack() {shuffling=false;screen=when(screen){Screen.Draw,Screen.Winner->Screen.Finalists;Screen.Review->Screen.Winner;Screen.Finalists->Screen.Discover;else->Screen.Home}}
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
    override fun onCleared() {db.close();super.onCleared()}
}
