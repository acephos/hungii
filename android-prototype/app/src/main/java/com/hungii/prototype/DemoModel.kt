package com.hungii.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import kotlin.math.abs

// Fictional fixtures, not a nutrition database or Swiggy response.
data class Span(val low: Int, val high: Int) {
    val mid: Float get() = (low + high) / 2f
    val label: String get() = if (low == high) "$low" else "$low–$high"
    operator fun plus(other: Span) = Span(low + other.low, high + other.high)
    fun portion(fraction: Float) = Span((low * fraction).toInt(), (high * fraction).toInt())
}

data class Nutrition(val calories: Span, val protein: Span, val carbs: Span, val fat: Span) {
    operator fun plus(n: Nutrition) = Nutrition(calories + n.calories, protein + n.protein, carbs + n.carbs, fat + n.fat)
    fun portion(f: Float) = Nutrition(calories.portion(f), protein.portion(f), carbs.portion(f), fat.portion(f))
}

data class Meal(
    val id: Int, val name: String, val restaurant: String, val image: Int, val veg: Boolean,
    val price: Span, val minutes: Span, val nutrition: Nutrition, val tags: Set<String>,
    val badge: String, val benefit: String, val compromise: String,
    val menuPrice: Int, val charges: Int, val discount: Int
) {
    val payable get() = menuPrice + charges - discount
    val sideDeal get() = menuPrice + 40 >= 300 && menuPrice + 40 + charges - 100 < payable
    fun quote(side: Boolean) = menuPrice + (if (side) 40 else 0) + charges - (if (side && sideDeal) 100 else discount)
    fun nutrients(side: Boolean) = if (side) nutrition + curdNutrition else nutrition
}

val curdNutrition = Nutrition(Span(80, 110), Span(6, 9), Span(8, 12), Span(2, 4))
val demoMeals = listOf(
    Meal(1,"Pepper salmon power bowl","Grain Theory",R.drawable.meal_3,false,Span(224,254),Span(20,30),Nutrition(Span(510,630),Span(40,48),Span(58,68),Span(12,18)),setOf("spicy","protein","filling"),"PROTEIN PICK","40–48g protein. A strong start to your next meal.","A little above the meal spending guide; sauce adds uncertainty.",239,35,40),
    Meal(2,"Chilli paneer rainbow bowl","Bowl Assembly",R.drawable.meal_1,true,Span(209,239),Span(25,35),Nutrition(Span(520,640),Span(29,37),Span(56,70),Span(19,25)),setOf("spicy","cheesy","filling"),"CRAVING MATCH","Spicy, filling, and a little comfort-food energy.","Higher fat and less protein than the salmon option.",219,35,40),
    Meal(3,"Tofu sesame crunch","Grain Theory",R.drawable.meal_5,true,Span(194,224),Span(20,30),Nutrition(Span(430,540),Span(26,34),Span(37,48),Span(18,23)),setOf("spicy","protein","light"),"BALANCED PICK","A lighter protein option with room left for later.","Less filling; the dressing makes calories less certain.",209,35,35),
    Meal(4,"Garden cheese flatbread","Slice Society",R.drawable.meal_4,true,Span(254,284),Span(30,40),Nutrition(Span(620,780),Span(24,32),Span(70,88),Span(27,34)),setOf("cheesy","filling"),"COMFORT PICK","Hits the cheesy craving. A sample side deal can lower the bill.","More calories and a longer wait than the bowls.",269,35,40),
    Meal(5,"Bean & veggie comfort bowl","Everyday Fuel",R.drawable.meal_6,true,Span(164,194),Span(25,35),Nutrition(Span(520,650),Span(18,24),Span(80,92),Span(13,19)),setOf("spicy","filling"),"BUDGET PICK","Keeps more of your money available for the last meal.","Less protein now; your later meal may need to contribute more.",159,30,15),
    Meal(6,"Chickpea sunshine bowl","Bowl Assembly",R.drawable.meal_2,true,Span(189,219),Span(15,25),Nutrition(Span(400,510),Span(20,27),Span(44,55),Span(15,20)),setOf("bland","light"),"QUICK & LIGHT","A quick, lighter choice under the meal spending guide.","May feel less filling than a grain bowl.",189,35,25)
)

enum class Screen { Home, Discover, Finalists, Draw, Winner, Review, Ordered, Saved, Day }
data class DemoOrder(val meal: Meal, val side: Boolean, val cost: Int, val eaten: Float = 0f)

class DemoModel {
    var screen by mutableStateOf(Screen.Home)
    var calorieGoal by mutableStateOf(2200)
    var proteinGoal by mutableStateOf(140)
    var carbGoal by mutableStateOf(250)
    var fatGoal by mutableStateOf(70)
    var allowance by mutableStateOf(600)
    var spent by mutableStateOf(160)
    var opportunities by mutableStateOf(2)
    var intake by mutableStateOf(Nutrition(Span(760,760),Span(42,42),Span(90,90),Span(25,25)))
    var taste by mutableStateOf("spicy")
    var highProtein by mutableStateOf(true)
    var vegOnly by mutableStateOf(false)
    var budgetOnly by mutableStateOf(false)
    var fast by mutableStateOf(false)
    val finalists = mutableStateListOf<Meal>()
    val passed = mutableStateListOf<Int>()
    val saved = mutableStateListOf(5)
    val orders = mutableStateListOf<DemoOrder>()
    var winner by mutableStateOf<Meal?>(null)
    var drawOrder by mutableStateOf<List<Meal>>(emptyList())
    var shuffling by mutableStateOf(false)
    var canPick by mutableStateOf(false)
    var pickedIndex by mutableStateOf<Int?>(null)
    var side by mutableStateOf(false)
    var receipt by mutableStateOf("")
    var lastPassed by mutableStateOf<Meal?>(null)
    private var undoUpdate by mutableStateOf<(() -> Unit)?>(null)
    val canUndoInput get() = undoUpdate != null
    fun invalidateCheckInUndo() { undoUpdate=null }

    val moneyLeft get() = allowance - spent
    val caloriesLeft get() = Span(calorieGoal - intake.calories.high, calorieGoal - intake.calories.low)
    val proteinLeft get() = Span(proteinGoal - intake.protein.high, proteinGoal - intake.protein.low)
    val carbLeft get() = Span(carbGoal - intake.carbs.high, carbGoal - intake.carbs.low)
    val fatLeft get() = Span(fatGoal - intake.fat.high, fatGoal - intake.fat.low)
    val reservedCalories get() = orders.fold(Span(0,0)) { n,o -> n + o.meal.nutrients(o.side).calories.portion(1f-o.eaten) }
    val mealMoneyGuide get() = moneyLeft / opportunities.coerceAtLeast(1)
    val pool get() = if (opportunities <= 0) emptyList() else demoMeals
        .filter { it.id !in passed && it !in finalists && (!vegOnly || it.veg) && (!budgetOnly || it.price.high <= 250) && it.price.low <= moneyLeft }
        .sortedByDescending {
            (if (taste in it.tags) 28 else 0) + (if (highProtein) it.nutrition.protein.mid else 0f) +
                (if (fast) 45 - it.minutes.mid else 0f) -
                (abs(it.nutrition.calories.mid - (caloriesLeft.mid-reservedCalories.mid)/opportunities.coerceAtLeast(1))/90) -
                ((it.price.mid-mealMoneyGuide).coerceAtLeast(0f)/7)
        }

    fun like(meal: Meal) {
        if (finalists.size >= 3 || meal in finalists) return
        finalists.add(meal)
        lastPassed = null
        if (finalists.size == 3) screen = Screen.Finalists
    }
    fun pass(meal: Meal) { passed.add(meal.id); lastPassed = meal }
    fun undoSwipe() {
        val m = lastPassed
        if (m != null) { passed.remove(m.id); lastPassed = null }
        else if (finalists.isNotEmpty()) { finalists.removeAt(finalists.lastIndex); screen = Screen.Discover }
    }
    fun toggleSaved(meal: Meal) { if (meal.id in saved) saved.remove(meal.id) else saved.add(meal.id) }
    fun remove(meal: Meal) { finalists.remove(meal); winner = null; screen = Screen.Discover }
    fun showFinalists() {
        if (finalists.size == 1) { winner = finalists[0]; screen = Screen.Winner }
        else if (finalists.isNotEmpty()) screen = Screen.Finalists
    }
    fun startDraw() {
        if (finalists.size < 2 || shuffling) return
        val result = finalists.toMutableList()
        val rng = SecureRandom()
        for (i in result.lastIndex downTo 1) { val j = rng.nextInt(i+1); val m=result[i]; result[i]=result[j]; result[j]=m }
        drawOrder = result
        winner = null
        pickedIndex = null
        canPick = false
        shuffling = true
        screen = Screen.Draw
    }
    fun pick(index: Int) {
        if (!canPick || shuffling || pickedIndex != null || index !in drawOrder.indices) return
        pickedIndex = index
        canPick = false
        winner = drawOrder[index]
    }
    fun goBack() {
        shuffling = false
        screen = when (screen) {
            Screen.Draw,Screen.Winner -> Screen.Finalists
            Screen.Review -> Screen.Winner
            Screen.Finalists -> Screen.Discover
            else -> Screen.Home
        }
    }
    fun confirm() {
        val m=winner ?: return
        if (screen != Screen.Review || m.quote(side) > moneyLeft) return
        invalidateCheckInUndo()
        val cost=m.quote(side)
        spent += cost
        orders.add(DemoOrder(m,side,cost))
        finalists.clear(); passed.clear(); winner=null; lastPassed=null; side=false
        screen=Screen.Ordered
    }
    fun eat(fraction: Float) = eatAt(orders.lastIndex,fraction)
    fun eatAt(index: Int,fraction: Float) {
        val order=orders.getOrNull(index) ?: return
        val amount=fraction.coerceAtMost(1f-order.eaten)
        if (amount<=0f) return
        invalidateCheckInUndo()
        intake += order.meal.nutrients(order.side).portion(amount)
        orders[index]=order.copy(eaten=order.eaten+amount)
        opportunities=(opportunities-1).coerceAtLeast(0)
        receipt="Logged ${(amount*100).toInt()}% of the meal. Spending stayed the same."
    }
    fun nextMeal() {
        finalists.clear(); passed.clear(); winner=null; lastPassed=null; side=false
        screen=Screen.Discover
    }
    fun update(text: String) {
        val oldAllowance=allowance; val oldOpportunities=opportunities; val oldIntake=intake; val oldTaste=taste
        val oldVeg=vegOnly; val oldProtein=highProtein; val oldFast=fast
        val raw=text.lowercase(); val changes=mutableListOf<String>()
        val money=Regex("(?:₹|rs\\.?\\s*|rupees?\\s*)(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        if (money!=null) {
            when {
                "left" in raw || "remaining" in raw -> { allowance=spent+money; changes.add("₹$money left today.") }
                "daily" in raw || "budget" in raw || "allowance" in raw -> { allowance=money; changes.add("Daily allowance is ₹$money.") }
                else -> changes.add("Is ₹$money your allowance or money left? Add ‘left’ or ‘daily budget’.")
            }
        }
        Regex("\\b([1-8])\\s*(?:more\\s+)?meals?\\s*(?:left|remaining)?").find(raw)?.groupValues?.get(1)?.toIntOrNull()?.let { opportunities=it; changes.add("$it eating opportunities left.") }
        listOf("spicy","cheesy","sweet","bland","light","filling").firstOrNull { it in raw }?.let { taste=it; changes.add("Feeling $it. Pool updated.") }
        if ("high protein" in raw || "more protein" in raw) { highProtein=true; changes.add("Protein gets more weight.") }
        if ("veg only" in raw || "vegetarian" in raw) { vegOnly=true; changes.add("Vegetarian-only pool.") }
        if ("quick" in raw || "fast" in raw) { fast=true; changes.add("Faster options get more weight.") }
        if (Regex("(?:had|ate) (?:2|two) eggs").containsMatchIn(raw)) {
            intake += Nutrition(Span(130,170),Span(10,14),Span(0,2),Span(8,12))
            changes.add("Two eggs logged using a fictional sample estimate. Opportunities unchanged.")
        }
        if (changes.isEmpty()) { invalidateCheckInUndo(); receipt="Scripted demo: try ‘₹350 left’, ‘2 meals left’, ‘something cheesy’, or ‘I had 2 eggs’."; return }
        undoUpdate={ allowance=oldAllowance; opportunities=oldOpportunities; intake=oldIntake; taste=oldTaste; vegOnly=oldVeg; highProtein=oldProtein; fast=oldFast }
        receipt=changes.joinToString(" ") + if(finalists.isNotEmpty()) " Your finalists are kept." else ""
    }
    fun undoInput() { undoUpdate?.invoke(); undoUpdate=null; receipt="Last check-in update undone." }
}
