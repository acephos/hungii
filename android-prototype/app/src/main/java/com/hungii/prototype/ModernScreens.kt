package com.hungii.prototype

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sin

@Composable internal fun FilledInput(value:String,onValueChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier) {
    OutlinedTextField(value,onValueChange,modifier=modifier,singleLine=true,shape=RoundedCornerShape(24.dp),placeholder={Text(placeholder,fontSize=14.sp)},textStyle=LocalTextStyle.current.copy(fontSize=15.sp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Raised,unfocusedContainerColor=Raised,focusedBorderColor=Lime.copy(alpha=.5f),unfocusedBorderColor=Color.Transparent))
}
@Composable private fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable internal fun ModernTabs(model:HungiiModel) {
    Row(Modifier.padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Surface).height(70.dp),verticalAlignment=Alignment.CenterVertically) {
        listOf(Triple(Screen.Home,"Home",Icons.Outlined.Home),Triple(Screen.Discover,"Meals",Icons.Outlined.Style),Triple(Screen.Assistant,"Assistant",Icons.Outlined.AutoAwesome),Triple(Screen.Saved,"Saved",Icons.Outlined.FavoriteBorder),Triple(Screen.Day,"My day",Icons.Outlined.BarChart)).forEach { (screen,label,icon) ->
            val active=model.screen==screen
            Column(Modifier.weight(1f).fillMaxHeight().clickable {if(screen==Screen.Discover&&model.meals.isEmpty())model.search() else model.screen=if(screen==Screen.Discover&&model.finalists.size>=3)Screen.Finalists else screen},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterVertically)) {
                Box(Modifier.size(if(screen==Screen.Assistant)36.dp else 28.dp).background(if(screen==Screen.Assistant)Lime else Color.Transparent,CircleShape),contentAlignment=Alignment.Center){Icon(icon,label,tint=if(screen==Screen.Assistant)Charcoal else if(active)Lime else Muted,modifier=Modifier.size(23.dp))}
                Text(label,color=if(active)Lime else Muted,fontSize=10.sp)
            }
        }
    }
}
@Composable internal fun ModernHomeScreen(model:HungiiModel,onEdit:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("hungii",color=Lime,fontSize=29.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-1).sp);Text("Eat well. Spend less. Overthink less.",color=Muted,fontSize=12.sp)};RoundAction(Icons.Outlined.Person,"Accounts",Raised,White,{model.accountOpen=true},44)}
        Panel {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Your food wallet",color=Muted,fontSize=14.sp);Icon(Icons.Outlined.AccountBalanceWallet,null,tint=Lime,modifier=Modifier.size(20.dp))}
            Row(verticalAlignment=Alignment.Bottom){DisplayText("₹${model.moneyLeft}",42);Text(" left today",color=Muted,fontSize=14.sp,modifier=Modifier.padding(bottom=8.dp))}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("${model.opportunities} meals left",color=White,fontSize=14.sp);Text("Around ₹${model.mealMoneyGuide} each",color=Muted,fontSize=12.sp)};TextButton(onClick=onEdit){Text("Edit my day",color=Lime)}}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)){Text("Calories left",color=Muted,fontSize=12.sp);DisplayText(model.caloriesLeft.label,24);Text("of ${model.calorieGoal} kcal",color=Muted,fontSize=12.sp)}
            Panel(Modifier.weight(1f)){Text("Protein left",color=Muted,fontSize=12.sp);DisplayText("${model.proteinLeft.mid.toInt()}g",30,Lime);Text("of ${model.proteinGoal}g",color=Muted,fontSize=12.sp)}
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            DisplayText("What sounds good?",26)
            FilledInput(model.query,{model.query=it},"Bowls, wraps, something spicy…",Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("any" to "Anything","spicy" to "Spicy","light" to "Light","cheesy" to "Cheesy","sweet" to "Sweet").forEach{(value,label)->SmallChip(label,model.taste==value){model.taste=value}}}
            LimeButton(if(model.loading)"Finding your meals…" else "Find my next meal",Icons.Outlined.ArrowForward,enabled=!model.loading&&model.opportunities>0){model.search()}
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Raised,Color(0xFF252A22)))).clickable{model.screen=Screen.Assistant}.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            GlassOrb(Modifier.size(54.dp),VoiceState(),false,true)
            Column(Modifier.weight(1f)){Text("Talk it out with Hungii",color=White,fontSize=15.sp,fontWeight=FontWeight.SemiBold);Text("Cravings, budgets, macros. Just ask.",color=Muted,fontSize=12.sp)}
            Icon(Icons.Outlined.ArrowOutward,null,tint=Lime,modifier=Modifier.size(19.dp))
        }
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=Coral,fontSize=13.sp)
        Text("Swipe to keep three. Shuffle, pick, eat.",color=Muted,fontSize=13.sp)
    }
}
@Composable internal fun GlassOrb(modifier:Modifier,voice:VoiceState,thinking:Boolean,reduceMotion:Boolean) {
    val transition=rememberInfiniteTransition(label="orb")
    val animatedPhase by transition.animateFloat(0f,6.283f,infiniteRepeatable(tween(if(thinking)4200 else 12000,easing=LinearEasing)),label="orb phase")
    val phase=if(reduceMotion)0f else animatedPhase
    val amplitude=if(voice.listening)voice.level else if(thinking).45f else .12f
    Canvas(modifier.semantics{contentDescription=if(voice.listening)"Listening orb" else if(thinking)"Thinking orb" else "Hungii assistant orb"}) {
        val c=center;val r=size.minDimension*.34f
        drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha=.15f+amplitude*.08f),Color(0xFFBEA9FF).copy(alpha=.06f),Color.Transparent),c,r*1.48f),r*1.48f,c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF344544),Color(0xFF182426),Color(0xFF0C1116)),Offset(c.x-r*.25f,c.y-r*.3f),r*1.5f),r,c)
        drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha=.22f),Color.Transparent),Offset(c.x-r*.35f,c.y-r*.45f),r*.7f),r*.7f,Offset(c.x-r*.35f,c.y-r*.45f))
        for(i in 0..2){
            val wave=sin(phase+i*1.8f)*r*.18f
            val ribbon=Path().apply{moveTo(c.x-r*.86f,c.y+wave);cubicTo(c.x-r*.3f,c.y-r*.85f,c.x+r*.35f,c.y+r*.72f,c.x+r*.86f,c.y-wave);cubicTo(c.x+r*.36f,c.y+r*.95f,c.x-r*.34f,c.y-r*.5f,c.x-r*.86f,c.y+wave)}
            drawPath(ribbon,Brush.linearGradient(listOf(Cyan.copy(alpha=.06f),Color(0xFFB8B3FF).copy(alpha=.28f),White.copy(alpha=.25f),Lime.copy(alpha=.04f)),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r)))
        }
        for(i in 0..4){val a=phase+i*.67f;val path=Path();val y=c.y+sin(a)*r*.2f;path.moveTo(c.x-r*.88f,y);path.cubicTo(c.x-r*.38f,c.y-r*(.9f+amplitude*.3f),c.x+r*.36f,c.y+r*.8f,c.x+r*.88f,y);drawPath(path,Brush.linearGradient(listOf(Cyan.copy(alpha=.1f),Color(0xFFC8B4FF).copy(alpha=.7f),White.copy(alpha=.65f),Lime.copy(alpha=.25f)),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r)),style=Stroke(r*(.012f+i*.003f)))}
        drawCircle(Brush.sweepGradient(listOf(Cyan.copy(alpha=.25f),Color(0xFFC2ABFF),White.copy(alpha=.85f),Cyan.copy(alpha=.16f),Lime.copy(alpha=.4f),Cyan.copy(alpha=.25f)),c),r,c,style=Stroke(r*.025f))
        drawArc(White.copy(alpha=.6f),215f,65f,false,Offset(c.x-r*.92f,c.y-r*.92f),Size(r*1.84f,r*1.84f),style=Stroke(r*.018f))
        drawCircle(White.copy(alpha=.55f),r*.035f,Offset(c.x-r*.54f,c.y-r*.66f))
    }
}
@Composable internal fun ThinkingDots() {
    val context=LocalContext.current;val reduced=remember{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
    val transition=rememberInfiniteTransition(label="loading");val phase by transition.animateFloat(0f,6.283f,infiniteRepeatable(tween(1500,easing=LinearEasing)),label="dots")
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){repeat(3){i->Box(Modifier.size(7.dp).graphicsLayer{alpha=if(reduced).65f else .35f+.65f*((sin(phase-i)+1)/2);translationY=if(reduced)0f else -3f*sin(phase-i)}.background(Lime,CircleShape))};Spacer(Modifier.width(8.dp));Text("Thinking…",color=Muted,fontSize=13.sp)}
}
@Composable internal fun AssistantScreen(model:HungiiModel,voice:VoiceState,onVoice:()->Unit,reduceMotion:Boolean) {
    var input by remember{mutableStateOf("")};val list=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(model.chatMessages.size,model.assistantLoading){if(model.chatMessages.isNotEmpty())list.animateScrollToItem(model.chatMessages.size)}
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){DisplayText("A little less thinking.",25);Text("Your whole day, in one conversation.",color=Muted,fontSize=12.sp)};Icon(Icons.Outlined.AutoAwesome,null,tint=Lime,modifier=Modifier.size(22.dp))}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=12.dp)) {
            item{Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){GlassOrb(Modifier.size(if(model.chatMessages.isEmpty())220.dp else 126.dp),voice,model.assistantLoading,reduceMotion);Text(if(voice.listening)"Listening to you" else if(model.assistantLoading)"Connecting the dots" else "Hey. What do you need?",color=White,fontSize=17.sp,fontWeight=FontWeight.Medium);Text("₹${model.moneyLeft} · ${model.caloriesLeft.label} kcal · ${model.opportunities} meals left",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))}}
            if(model.chatMessages.isEmpty())item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){listOf("Find a spicy, protein-rich meal","Set my food allowance to ₹350","Show my daily tracker").forEach{prompt->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).clickable{model.sendAssistantMessage(prompt)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(prompt,color=White,fontSize=14.sp,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ArrowOutward,null,tint=Muted,modifier=Modifier.size(16.dp))}}}}
            items(model.chatMessages){message->Column(Modifier.fillMaxWidth(),horizontalAlignment=if(message.fromUser)Alignment.End else Alignment.Start){Column(Modifier.widthIn(max=320.dp).clip(RoundedCornerShape(22.dp)).background(if(message.fromUser)Raised else Surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text(message.text,color=White,fontSize=15.sp,lineHeight=23.sp);message.actions.forEach{action->OutlinedButton(onClick={model.assistantAction=action},shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Lime.copy(alpha=.4f))){Text("Review: "+action.first.replace('_',' '),color=Lime,fontSize=12.sp)}}}}}
            if(model.assistantLoading)item{ThinkingDots()}
        }
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledInput(input,{input=it},"Ask Hungii anything…",Modifier.weight(1f))
            RoundAction(if(input.isBlank())Icons.Outlined.Mic else Icons.Outlined.ArrowUpward,if(input.isBlank())"Speak to Hungii" else "Send message",if(voice.listening)Coral else Lime,Charcoal,{if(!model.assistantLoading){if(input.isBlank())onVoice()else{model.sendAssistantMessage(input);input=""}}},52)
        }
        Text(if(BuildConfig.LOCAL_DEMO)"Groq Free · Google ADK · synthetic MCP" else "Cloud assistant available in Simulator",color=Muted,fontSize=10.sp,modifier=Modifier.fillMaxWidth().padding(bottom=6.dp),textAlign=TextAlign.Center)
    }
}
@Composable private fun McpActivity(model:HungiiModel) {
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp)){
        Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Terminal,null,tint=Muted,modifier=Modifier.size(18.dp));Text("MCP activity",color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f).padding(start=10.dp));Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Toggle MCP activity",tint=Muted)}
        if(expanded)model.mcpTrace.takeLast(12).forEach{Text(it,color=Cyan,fontSize=11.sp,modifier=Modifier.padding(top=6.dp))}
    }
}
private fun openSwiggy(context:android.content.Context) {
    val launch=context.packageManager.getLaunchIntentForPackage("in.swiggy.android")
    try{context.startActivity(launch?:Intent(Intent.ACTION_VIEW,Uri.parse("https://www.swiggy.com/")))}catch(_:Exception){android.widget.Toast.makeText(context,"Install Swiggy or a browser to continue manually.",android.widget.Toast.LENGTH_LONG).show()}
}
@Composable internal fun CheckoutScreen(model:HungiiModel) {
    val context=LocalContext.current;val cart=model.cart
    Column(Modifier.fillMaxSize()){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            FocusHeader("Your basket"){model.goBack()};DisplayText("Good food.\nBetter value.",32)
            if(model.loading)ThinkingDots()
            if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=Coral,fontSize=13.sp)
            if(cart!=null){
                Panel{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.LocationOn,null,tint=Lime);Column{Text("Deliver to",color=Muted,fontSize=12.sp);Text(cart.optString("address"),color=White,fontSize=14.sp,lineHeight=20.sp)}}}
                val items=cart.optJSONArray("items")?:JSONArray()
                for(i in 0 until items.length())CartItem(model,items.getJSONObject(i),cart.optString("restaurant"))
                cart.optJSONObject("nudge")?.let{n->Panel{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.LocalOffer,null,tint=Lime);Text("Add a little. Pay less.",color=Lime,fontSize=16.sp,fontWeight=FontWeight.Bold)};Text("${n.getString("name")} costs ${rupees(n.getDouble("price"))} and unlocks ${n.getString("coupon")}. Your final bill drops by ${rupees(n.getDouble("saving"))}.",color=White,fontSize=14.sp,lineHeight=21.sp);val cal=n.getJSONObject("nutrition").getJSONArray("calories");Text("Trade-off: ${cal.getInt(0)}–${cal.getInt(1)} extra kcal, estimated.",color=Coral,fontSize=12.sp);OutlineButton("Add side · new total ${rupees(n.getDouble("newPayable"))}"){if(!model.loading)model.addSide(n.getString("dishId"))}}}
                if(items.length()>0)Panel{
                    Text("Bill details",color=White,fontSize=17.sp,fontWeight=FontWeight.SemiBold);BillLine("Food",rupees(cart.optDouble("itemTotal")));BillLine("Delivery",rupees(cart.optDouble("deliveryCharge")))
                    val fees=cart.optJSONObject("fees")?:JSONObject();BillLine("Packaging",rupees(fees.optDouble("packaging")));BillLine("Platform",rupees(fees.optDouble("platform")));BillLine("Tax",rupees(fees.optDouble("tax")))
                    if(cart.optDouble("couponDiscount")>0)BillLine(cart.optString("appliedCoupon"),"−"+rupees(cart.optDouble("couponDiscount")),Lime)
                    HorizontalDivider(color=Line);BillLine("To pay",rupees(cart.optDouble("payable")),Lime);Text("All amounts are synthetic INR. No real charge.",color=Muted,fontSize=11.sp)
                }
                if(items.length()==0)Text("Your basket is empty. Pick a meal to start again.",color=Muted)
            }
            McpActivity(model);OutlineButton("Open Swiggy · build cart manually"){openSwiggy(context)}
            Text("These restaurants are invented for the simulator. In Swiggy, search for a similar meal and create your basket yourself.",color=Muted,fontSize=11.sp,lineHeight=17.sp);Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(22.dp)){LimeButton("Choose payment · "+rupees(cart?.optDouble("payable")?:0.0),Icons.Outlined.ArrowForward,enabled=!model.loading&&(cart?.optJSONArray("items")?.length()?:0)>0){model.choosePayment()}}
    }
}
@Composable private fun CartItem(model:HungiiModel,item:JSONObject,restaurant:String) {
    val id=item.getString("menuItemId");val quantity=item.optInt("quantity")
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val m=model.meals.firstOrNull{it.dishId==id}?:model.winner?.copy(name=item.optString("name"),photo=item.optInt("photo"))
            if(m!=null)MealImage(m,Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f)){Text(item.optString("name"),color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(restaurant,color=Muted,fontSize=12.sp)}
        }
        Row(verticalAlignment=Alignment.CenterVertically){Text("Quantity",color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f));IconButton(onClick={model.changeQuantity(id,quantity-1)},enabled=!model.loading){Icon(Icons.Outlined.Remove,"Remove one",tint=Muted)};Text("$quantity",color=White,fontSize=17.sp);IconButton(onClick={model.changeQuantity(id,quantity+1)},enabled=!model.loading&&quantity<10){Icon(Icons.Outlined.Add,"Add one",tint=Lime)}}
        val custom=item.optJSONObject("customizations");val variants=custom?.optJSONArray("variants")?:JSONArray()
        for(g in 0 until variants.length()){val group=variants.getJSONObject(g);val choices=group.getJSONArray("variations");Text(group.optString("name","Portion"),color=Muted,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){for(v in 0 until choices.length()){val choice=choices.getJSONObject(v);val selected=item.optJSONArray("variants")?.let{a->(0 until a.length()).any{a.getJSONObject(it).optString("variation_id")==choice.getString("id")}}?:false;SmallChip(choice.getString("name"),selected){if(!model.loading)model.changeVariant(id,group.getString("groupId"),choice.getString("id"))}}}}
        val addons=custom?.optJSONArray("addons")?:JSONArray()
        for(g in 0 until addons.length()){val group=addons.getJSONObject(g);val choices=group.getJSONArray("choices");for(v in 0 until choices.length()){val choice=choices.getJSONObject(v);val chosen=item.optJSONArray("addons")?:JSONArray();val checked=(0 until chosen.length()).any{chosen.getJSONObject(it).optString("addon_id")==choice.getString("id")};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked,onCheckedChange={on->if(!model.loading){val updated=JSONArray();for(k in 0 until chosen.length()){val old=chosen.getJSONObject(k);if(old.optString("addon_id")!=choice.getString("id"))updated.put(old)};if(on)updated.put(JSONObject().put("group_id",group.getString("groupId")).put("addon_id",choice.getString("id")));model.changeAddons(id,updated)}});Text(choice.getString("name")+" · "+rupees(choice.optDouble("price")),color=White,fontSize=13.sp)}}}
        item.optJSONObject("nutrition")?.let{n->MacroStats(Nutrition.from(n));Text("Estimated ranges · includes quantity and extras",color=Muted,fontSize=10.sp)}
    }
}
@Composable internal fun PaymentScreen(model:HungiiModel) {
    var confirm by remember{mutableStateOf(false)};val c=model.cart;val pending=model.paymentStage in listOf("pending","unresolved")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        FocusHeader("Mock payment"){if(!pending)model.goBack()};Text("${c?.optString("restaurant")?:"Your basket"}",color=Muted,fontSize=14.sp);DisplayText(rupees(c?.optDouble("payable")?:0.0),48);Text("Simulator only. No money leaves your account.",color=Lime,fontSize=12.sp)
        if(model.loading)ThinkingDots()
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=Coral,fontSize=13.sp)
        if(model.paymentStage=="choose"){
            Panel{
                Text("Pay using",color=White,fontSize=17.sp,fontWeight=FontWeight.SemiBold);val methods=model.paymentOptions?.optJSONArray("allMethods")?:JSONArray()
                for(i in 0 until methods.length()){val m=methods.getJSONObject(i);Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Raised).clickable{model.selectedMethodId=m.getString("id")}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(m.optString("groupName")=="UPI")Icons.Outlined.QrCode2 else Icons.Outlined.Payments,null,tint=White,modifier=Modifier.size(24.dp));Text(m.getString("displayName"),color=White,fontSize=14.sp,modifier=Modifier.weight(1f).padding(horizontal=12.dp));RadioButton(model.selectedMethodId==m.getString("id"),onClick={model.selectedMethodId=m.getString("id")})}}
            }
            FilledInput(model.checkoutNote,{model.checkoutNote=it.take(200)},"Note to kitchen (optional)",Modifier.fillMaxWidth());LimeButton("Review & confirm mock order",Icons.Outlined.Lock,enabled=!model.loading&&model.paymentOptions!=null){confirm=true}
        }else if(pending){
            Panel{
                Icon(Icons.Outlined.HourglassTop,null,tint=Lime,modifier=Modifier.size(32.dp));DisplayText("Payment pending",26);Text("Your order has not been placed. Choose a mock outcome below; Hungii checks status through MCP and confirms only after success.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
                if(model.selectedMethodId=="mock-qr")Box(Modifier.fillMaxWidth().height(120.dp).background(Raised,RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.QrCode2,"Decorative mock QR, not scannable",tint=White,modifier=Modifier.size(64.dp));Text("Mock QR · do not scan",color=Muted,fontSize=11.sp)}}
                LimeButton("Simulate success",Icons.Outlined.Check,enabled=!model.loading){model.simulatePayment("SUCCESS")};OutlineButton("Simulate failure"){if(!model.loading)model.simulatePayment("FAILED")};TextButton(onClick={if(!model.loading)model.simulatePayment("CANCELLED")}){Text("Cancel mock payment",color=Coral)};TextButton(onClick=model::refreshPayment){Text("Refresh payment status",color=Lime)}
            }
        }else if(model.paymentStage=="failed")Panel{DisplayText("Let’s try again.",27);Text("Your cart is saved. No order was placed.",color=Muted,fontSize=14.sp);LimeButton("Choose payment again",Icons.Outlined.Refresh,enabled=!model.loading){model.retryPayment()}}
        McpActivity(model);Spacer(Modifier.height(20.dp))
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},containerColor=Surface,title={Text("Confirm the mock order")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text("${c?.optString("restaurant")}\n${c?.optString("address")}");val items=c?.optJSONArray("items")?:JSONArray();for(i in 0 until items.length()){val item=items.getJSONObject(i);Text("${item.optInt("quantity")} × ${item.optString("name")}")};Text("${rupees(c?.optDouble("payable")?:0.0)} · ${model.selectedMethodId}",color=Lime);Text("This creates a synthetic payment or COD order. No real money or order.",color=Muted,fontSize=12.sp)}},confirmButton={TextButton(onClick={confirm=false;model.beginPayment()}){Text("Confirm")}},dismissButton={TextButton(onClick={confirm=false}){Text("Back to review")}})
}
@Composable internal fun OrderScreen(model:HungiiModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){
        Box(Modifier.size(90.dp).background(Lime,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Check,"Mock order confirmed",tint=Charcoal,modifier=Modifier.size(42.dp))};DisplayText("Food is on its way.*",30);Text("*In our little simulator world.",color=Muted,fontSize=13.sp)
        Panel{Text("Mock order confirmed",color=Lime,fontSize=17.sp,fontWeight=FontWeight.Bold);val details=model.order?.optJSONObject("details")?.optJSONObject("order");Text("Order #${details?.optString("order_id")?:model.payment?.optString("orderId")}",color=Muted,fontSize=12.sp);Text(model.checkoutCart?.optString("restaurant")?:"",color=White,fontSize=19.sp,fontWeight=FontWeight.Bold);BillLine("Total",rupees(model.checkoutCart?.optDouble("payable")?:0.0));HorizontalDivider(color=Line);listOf("✓ Order received","● Kitchen is preparing","○ Rider pickup","○ Delivered").forEach{Text(it,color=if(it.startsWith("●"))Lime else Muted,fontSize=14.sp)};Text("Synthetic delivery estimate: 20–25 min",color=Muted,fontSize=12.sp)}
        Text("Ordering isn’t eating. Log the meal when you eat it to update your remaining macros.",color=Muted,fontSize=14.sp,lineHeight=21.sp);LimeButton("Log as eaten · estimated macros",Icons.Outlined.Restaurant){model.logOrderAsEaten()};OutlineButton("Back to my day"){model.screen=Screen.Home};McpActivity(model)
    }
}
