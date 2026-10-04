package com.hungii.prototype

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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

@Composable internal fun FilledInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val edge by animateColorAsState(if (focused) Accent else Line, tween(220), label = "field edge")
    val tint by animateColorAsState(if (focused) AccentWash else Raised, tween(220), label = "field surface")
    val caption by animateColorAsState(if (focused) Rose else Muted, tween(220), label = "field caption")
    TextField(
        value, onValueChange,
        modifier = modifier.heightIn(min = 60.dp).border(1.dp, edge, RoundedCornerShape(20.dp)),
        singleLine = true, shape = RoundedCornerShape(20.dp),
        label = if (showLabel || label != null) {{ Text(label ?: placeholder, fontSize = 12.sp, color = caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }} else null,
        placeholder = { Text(placeholder, fontSize = 16.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = White),
        interactionSource = interactions,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = tint, unfocusedContainerColor = tint,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent, cursorColor = Accent,
        ),
    )
}

@Composable private fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Raised.copy(alpha=.65f),Surface))).border(1.dp,Line.copy(alpha=.55f),RoundedCornerShape(24.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable internal fun ModernTabs(model: HungiiModel) {
    NavigationBar(containerColor = Surface, contentColor = Muted, tonalElevation = 0.dp) {
        listOf(
            Triple(Screen.Home, "Home", Icons.Outlined.Home),
            Triple(Screen.Discover, "Meals", Icons.Outlined.Restaurant),
            Triple(Screen.Assistant, "Assistant", Icons.Outlined.AutoAwesome),
            Triple(Screen.Day, "My day", Icons.Outlined.BarChart),
        ).forEach { (screen, label, icon) ->
            val selected = model.screen == screen || (screen == Screen.Discover && model.screen == Screen.Saved)
            val indicator by animateColorAsState(if (selected) AccentWash else Color.Transparent, tween(260), label = "tab surface")
            val foreground by animateColorAsState(if (selected) Rose else Muted, tween(260), label = "tab ink")
            NavigationBarItem(
                selected = selected,
                onClick = { model.screen = screen },
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { TabLabel(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = foreground, selectedTextColor = foreground, indicatorColor = indicator,
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                ),
            )
        }
    }
}
@Composable private fun TabLabel(label: String) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val width=measurer.measure(label,style=TextStyle(fontSize=12.sp,fontWeight=FontWeight.Medium),softWrap=false).size.width
        val fit=(with(density){maxWidth.toPx()}/width.coerceAtLeast(1)).coerceAtMost(1f)
        Text(label,fontSize=(12*fit).sp,lineHeight=(16*fit).sp,fontWeight=FontWeight.Medium,maxLines=1,softWrap=false)
    }
}
@Composable internal fun ModernHomeScreen(model: HungiiModel, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("hungii", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text(if(model.displayName.isBlank()) "Your next meal, made easy." else "Hey, ${model.displayName}. What sounds good?", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            RoundAction(Icons.Outlined.Person, "Accounts", Raised, White, { model.accountOpen = true }, 48)
        }
        Panel {
            Text("Food allowance", color = Muted, fontSize = 14.sp)
            if(model.reservedSpend>0)Text("₹${model.reservedSpend} reserved for unresolved checkout",color=Muted,fontSize=12.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DisplayText("₹${model.moneyLeft}", 44)
                    Text("left today · ${model.opportunities} meals left", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
                TextButton(onClick = onEdit) { Text("Edit day", color = Accent, fontSize = 14.sp) }
            }
        }
        ConnectionStrip(model)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisplayText("What sounds good?", 28)
            FilledInput(
                model.query, { model.query = it }, "Bowls, wraps, something spicy…",
                Modifier.fillMaxWidth(), label = "Your craving",
                onSubmit = { if (!model.loading && model.opportunities > 0) model.search() },
            )
            PrimaryButton(if (model.loading) "Finding your meals…" else "Find my next meal", Icons.AutoMirrored.Outlined.ArrowForward,
                enabled = !model.loading && model.opportunities > 0) { model.search() }
            if (model.opportunities == 0) Text("No meals left today. Edit your day to plan another.", color = Muted, fontSize = 14.sp)
            if (model.connectionMessage.isNotBlank()) Text(model.connectionMessage, color = SoftCrimson, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text("Calories left", color = Muted, fontSize = 14.sp)
                DisplayText(model.caloriesLeft.label, 28)
                Text("of ${model.calorieGoal} kcal", color = Muted, fontSize = 12.sp)
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text("Protein left", color = Muted, fontSize = 14.sp)
                DisplayText("${model.proteinLeft.mid.toInt()}g", 28)
                Text("of ${model.proteinGoal}g", color = Muted, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface)
                .clickable { model.screen = Screen.Assistant }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassOrb(Modifier.size(48.dp), VoiceState(), false, true)
            Column(Modifier.weight(1f)) {
                Text("Need a little help?", color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Talk it through with Hungii.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Muted, modifier = Modifier.size(20.dp))
        }
    }
}
@Composable internal fun GlassOrb(modifier:Modifier,voice:VoiceState,thinking:Boolean,reduceMotion:Boolean) {
    val transition=rememberInfiniteTransition(label="orb")
    val animatedPhase by transition.animateFloat(0f,6.283f,infiniteRepeatable(tween(if(thinking)4200 else 12000,easing=LinearEasing)),label="orb phase")
    val phase=if(reduceMotion)0f else animatedPhase
    val amplitude=if(voice.listening)voice.level else if(thinking).45f else .12f
    Canvas(modifier.semantics{contentDescription=if(voice.listening)"Listening orb" else if(thinking)"Thinking orb" else "Hungii assistant orb"}) {
        val c=center;val r=size.minDimension*.34f
        drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha=.15f+amplitude*.08f),SoftCrimson.copy(alpha=.06f),Color.Transparent),c,r*1.48f),r*1.48f,c)
        drawCircle(Brush.radialGradient(listOf(Raised,AccentWash,Charcoal),Offset(c.x-r*.25f,c.y-r*.3f),r*1.5f),r,c)
        drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha=.22f),Color.Transparent),Offset(c.x-r*.35f,c.y-r*.45f),r*.7f),r*.7f,Offset(c.x-r*.35f,c.y-r*.45f))
        for(i in 0..2){
            val wave=sin(phase+i*1.8f)*r*.18f
            val ribbon=Path().apply{moveTo(c.x-r*.86f,c.y+wave);cubicTo(c.x-r*.3f,c.y-r*.85f,c.x+r*.35f,c.y+r*.72f,c.x+r*.86f,c.y-wave);cubicTo(c.x+r*.36f,c.y+r*.95f,c.x-r*.34f,c.y-r*.5f,c.x-r*.86f,c.y+wave)}
            drawPath(ribbon,Brush.linearGradient(listOf(Rose.copy(alpha=.06f),Accent.copy(alpha=.28f),White.copy(alpha=.25f),Accent.copy(alpha=.04f)),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r)))
        }
        for(i in 0..4){val a=phase+i*.67f;val path=Path();val y=c.y+sin(a)*r*.2f;path.moveTo(c.x-r*.88f,y);path.cubicTo(c.x-r*.38f,c.y-r*(.9f+amplitude*.3f),c.x+r*.36f,c.y+r*.8f,c.x+r*.88f,y);drawPath(path,Brush.linearGradient(listOf(Rose.copy(alpha=.1f),Rose.copy(alpha=.7f),White.copy(alpha=.65f),Accent.copy(alpha=.25f)),Offset(c.x-r,c.y-r),Offset(c.x+r,c.y+r)),style=Stroke(r*(.012f+i*.003f)))}
        drawCircle(Brush.sweepGradient(listOf(Rose.copy(alpha=.25f),SoftCrimson,White.copy(alpha=.85f),Rose.copy(alpha=.16f),Accent.copy(alpha=.4f),Rose.copy(alpha=.25f)),c),r,c,style=Stroke(r*.025f))
        drawArc(White.copy(alpha=.6f),215f,65f,false,Offset(c.x-r*.92f,c.y-r*.92f),Size(r*1.84f,r*1.84f),style=Stroke(r*.018f))
        drawCircle(White.copy(alpha=.55f),r*.035f,Offset(c.x-r*.54f,c.y-r*.66f))
    }
}
@Composable internal fun ThinkingDots() {
    val context=LocalContext.current;val reduced=remember{Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
    val transition=rememberInfiniteTransition(label="loading");val phase by transition.animateFloat(0f,6.283f,infiniteRepeatable(tween(1500,easing=LinearEasing)),label="dots")
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){repeat(3){i->Box(Modifier.size(7.dp).graphicsLayer{alpha=if(reduced).65f else .35f+.65f*((sin(phase-i)+1)/2);translationY=if(reduced)0f else -3f*sin(phase-i)}.background(Accent,CircleShape))};Spacer(Modifier.width(8.dp));Text("Thinking…",color=Muted,fontSize=13.sp)}
}
@Composable internal fun AssistantScreen(model:HungiiModel,voice:VoiceState,onVoice:()->Unit,reduceMotion:Boolean) {
    if(!BuildConfig.LOCAL_DEMO){
        var checkIn by remember {mutableStateOf("")}
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
            DisplayText("A quick check-in.",28)
            GlassOrb(Modifier.size(180.dp),VoiceState(),false,reduceMotion)
            Text("Update your meal plans in your own words.",color=Muted,fontSize=14.sp)
            FilledInput(checkIn,{checkIn=it.take(150)},"e.g. ₹300 left, 2 meals, spicy",Modifier.fillMaxWidth())
            PrimaryButton("Update my plan",Icons.Outlined.Check,enabled=checkIn.isNotBlank()){model.update(checkIn);checkIn=""}
            if(model.receipt.isNotBlank())Text(model.receipt,color=White,fontSize=14.sp,lineHeight=21.sp)
            if(model.canUndoInput)OutlineButton("Undo update"){model.undoInput()}
            OutlineButton("Find my next meal"){model.search()}
            Text("This check-in updates your plan on this device. It does not place orders or guess nutrition.",color=Muted,fontSize=12.sp,lineHeight=18.sp)
        }
        return
    }
    var input by remember{mutableStateOf("")};val list=androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(model.chatMessages.size,model.assistantLoading){if(model.chatMessages.isNotEmpty())list.animateScrollToItem(model.chatMessages.size)}
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=18.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){DisplayText("A little less thinking.",25);Text("Your whole day, in one conversation.",color=Muted,fontSize=12.sp)};Icon(Icons.Outlined.AutoAwesome,null,tint=Accent,modifier=Modifier.size(22.dp))}
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),state=list,verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(vertical=12.dp)) {
            item{Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){GlassOrb(Modifier.size(if(model.chatMessages.isEmpty())220.dp else 126.dp),voice,model.assistantLoading,reduceMotion);Text(if(voice.listening)"Listening to you" else if(model.assistantLoading)"Connecting the dots" else "Hey. What do you need?",color=White,fontSize=17.sp,fontWeight=FontWeight.Medium);Text("₹${model.moneyLeft} · ${model.caloriesLeft.label} kcal · ${model.opportunities} meals left",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))}}
            if(model.chatMessages.isEmpty())item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){listOf("Find a spicy, protein-rich meal","Set my food allowance to ₹350","Show my daily tracker").forEach{prompt->Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).clickable{model.sendAssistantMessage(prompt)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(prompt,color=White,fontSize=14.sp,modifier=Modifier.weight(1f));Icon(Icons.Outlined.ArrowOutward,null,tint=Muted,modifier=Modifier.size(16.dp))}}}}
            items(model.chatMessages){message->Column(Modifier.fillMaxWidth(),horizontalAlignment=if(message.fromUser)Alignment.End else Alignment.Start){Column(Modifier.widthIn(max=320.dp).clip(RoundedCornerShape(22.dp)).background(if(message.fromUser)Raised else Surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Text(message.text,color=White,fontSize=15.sp,lineHeight=23.sp);message.actions.forEach{action->OutlinedButton(onClick={model.assistantAction=action},shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Accent.copy(alpha=.4f))){Text("Review: "+action.first.replace('_',' '),color=Accent,fontSize=12.sp)}}}}}
            if(model.assistantLoading)item{ThinkingDots()}
        }
        Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledInput(input,{input=it},"Ask Hungii anything…",Modifier.weight(1f))
            RoundAction(if(input.isBlank())Icons.Outlined.Mic else Icons.Outlined.ArrowUpward,if(input.isBlank())"Speak to Hungii" else "Send message",if(voice.listening)SoftCrimson else Accent,Charcoal,{if(!model.assistantLoading){if(input.isBlank())onVoice()else{model.sendAssistantMessage(input);input=""}}},52)
        }
        Text(if(BuildConfig.LOCAL_DEMO)if(model.assistantConsent) "Cloud assistant enabled" else "Cloud assistant · optional" else "Cloud assistant available in Simulator",color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(bottom=6.dp),textAlign=TextAlign.Center)
    }
}
@Composable private fun McpActivity(model:HungiiModel) {
    if(!BuildConfig.LOCAL_DEMO)return
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(16.dp)){
        Row(Modifier.fillMaxWidth().clickable{expanded=!expanded},verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Terminal,null,tint=Muted,modifier=Modifier.size(18.dp));Text("MCP activity",color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f).padding(start=10.dp));Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Toggle MCP activity",tint=Muted)}
        if(expanded)model.mcpTrace.takeLast(12).forEach{Text(it,color=Rose,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))}
    }
}
private fun openSwiggy(context:android.content.Context) {
    val launch=context.packageManager.getLaunchIntentForPackage("in.swiggy.android")
    try{context.startActivity(launch?:Intent(Intent.ACTION_VIEW,Uri.parse("https://www.swiggy.com/")))}catch(_:Exception){android.widget.Toast.makeText(context,"Install Swiggy or a browser to continue manually.",android.widget.Toast.LENGTH_LONG).show()}
}
private fun moneyLabel(data:JSONObject,key:String)=data.optDouble(key,Double.NaN).takeIf {it.isFinite()&&it>=0}?.let(::rupees)?:"Unavailable"
@Composable internal fun CheckoutScreen(model:HungiiModel) {
    val context=LocalContext.current;val cart=model.cart
    var coupon by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize()){
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            FocusHeader("Your basket"){model.goBack()};DisplayText("Good food.\nBetter value.",32)
            if(model.loading)ThinkingDots()
            if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp)
            if(!model.orderingEnabled)Panel{
                Text("Swiggy ordering · setup pending",color=Accent,fontWeight=FontWeight.SemiBold)
                Text("Ordering will open after Swiggy grants access and we verify checkout. Your meal choice has not changed a cart or placed an order.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
                model.winner?.let {Text(it.name,color=White,fontSize=18.sp)}
                OutlineButton("Back to my day"){model.screen=Screen.Day}
            }
            if(cart!=null){
                Panel{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.LocationOn,null,tint=Accent);Column{Text("Deliver to",color=Muted,fontSize=12.sp);Text(cart.optString("address"),color=White,fontSize=14.sp,lineHeight=20.sp)}}}
                val items=cart.optJSONArray("items")?:JSONArray()
                for(i in 0 until items.length())CartItem(model,items.getJSONObject(i),cart.optString("restaurant"))
                cart.optJSONObject("nudge")?.let{n->Panel{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.LocalOffer,null,tint=Accent);Text("Add a little. Pay less.",color=Accent,fontSize=16.sp,fontWeight=FontWeight.Bold)};Text("${n.getString("name")} costs ${rupees(n.getDouble("price"))} and unlocks ${n.getString("coupon")}. Your final bill drops by ${rupees(n.getDouble("saving"))}.",color=White,fontSize=14.sp,lineHeight=21.sp);val cal=n.getJSONObject("nutrition").getJSONArray("calories");Text("Trade-off: ${cal.getInt(0)}–${cal.getInt(1)} extra kcal, estimated.",color=SoftCrimson,fontSize=12.sp);OutlineButton("Add side · new total ${rupees(n.getDouble("newPayable"))}"){if(!model.loading)model.addSide(n.getString("dishId"))}}}
                if(items.length()>0)Panel{
                    Text("Bill details",color=White,fontSize=17.sp,fontWeight=FontWeight.SemiBold);BillLine("Food",moneyLabel(cart,"itemTotal"));BillLine("Delivery",moneyLabel(cart,"deliveryCharge"))
                    val fees=cart.optJSONObject("fees")?:JSONObject();if(BuildConfig.LOCAL_DEMO){BillLine("Packaging",rupees(fees.optDouble("packaging")));BillLine("Platform",rupees(fees.optDouble("platform")))};BillLine(if(BuildConfig.LOCAL_DEMO)"Tax" else "Taxes & other charges",moneyLabel(fees,"tax"))
                    if(cart.optDouble("couponDiscount")>0)BillLine(cart.optString("appliedCoupon"),"−"+rupees(cart.optDouble("couponDiscount")),Accent)
                    HorizontalDivider(color=Line);BillLine("To pay",rupees(cart.optDouble("payable")),Accent);Text(if(BuildConfig.LOCAL_DEMO) "All amounts are synthetic INR. No real charge." else "Live Swiggy total in INR. We’ll check it again before ordering.",color=Muted,fontSize=12.sp)
                }
                if(items.length()==0)Text("Your basket is empty. Pick a meal to start again.",color=Muted)
            }
            if(cart!=null&&!BuildConfig.LOCAL_DEMO){FilledInput(coupon,{coupon=it.take(80)},"Coupon code",Modifier.fillMaxWidth());OutlineButton("Apply coupon"){if(!model.loading&&coupon.isNotBlank())model.applyCoupon(coupon)}}
            McpActivity(model)
            if(BuildConfig.LOCAL_DEMO){OutlineButton("Open Swiggy · build cart manually"){openSwiggy(context)};Text("These restaurants are invented for the simulator. No real order will be placed.",color=Muted,fontSize=12.sp,lineHeight=17.sp)}
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val total=cart?.optDouble("payable",Double.NaN)?.takeIf {it.isFinite() && it>=0}
            val count=cart?.optJSONArray("items")?.let {items->(0 until items.length()).sumOf {items.getJSONObject(it).optInt("quantity")}} ?: 0
            if(total!=null && !model.loading && !model.cartRefreshRequired) Text("$count item${if(count==1) "" else "s"} · ${if(total<=model.moneyLeft) "${rupees(model.moneyLeft-total)} allowance left" else "${rupees(total-model.moneyLeft)} over allowance"}",color=Muted,fontSize=14.sp)
            if(model.cartRefreshRequired) Text("Refresh to confirm your current basket and total.",color=Muted,fontSize=14.sp)
            PrimaryButton(if(model.loading) "Updating your basket…" else if(model.cartRefreshRequired) "Refresh basket" else total?.let {"Choose payment · ${rupees(it)}"} ?: "Choose payment",Icons.AutoMirrored.Outlined.ArrowForward,enabled=model.orderingEnabled&&!model.loading&&(model.cartRefreshRequired || (count>0&&total!=null))){if(model.cartRefreshRequired)model.refreshCart() else model.choosePayment()}
        }
    }
}
@Composable private fun CartItem(model:HungiiModel,item:JSONObject,restaurant:String) {
    val id=item.getString("menuItemId");val quantity=item.optInt("quantity")
    Panel{
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            val m=model.meals.firstOrNull{it.dishId==id}?:model.winner?.copy(name=item.optString("name"),photo=item.optInt("photo"))
            if(m!=null)MealImage(m,Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(item.optString("name"),color=White,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold)
                Text(restaurant,color=Muted,fontSize=14.sp)
                item.optDouble("total",Double.NaN).takeIf {it.isFinite() && it>=0}?.let {Text(rupees(it)+" · $quantity item${if(quantity==1) "" else "s"}",color=White,fontSize=16.sp)}
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically){Text("Quantity",color=Muted,fontSize=13.sp,modifier=Modifier.weight(1f));IconButton(onClick={model.changeQuantity(id,quantity-1)},enabled=!model.loading){Icon(Icons.Outlined.Remove,"Remove one",tint=Muted)};Text("$quantity",color=White,fontSize=17.sp);IconButton(onClick={model.changeQuantity(id,quantity+1)},enabled=!model.loading&&quantity<10){Icon(Icons.Outlined.Add,"Add one",tint=Accent)}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(1,2,3).forEach {count->SmallChip("${count}×",quantity==count,enabled=!model.loading){model.changeQuantity(id,count)}}
        }
        val custom=item.optJSONObject("customizations");val variants=custom?.optJSONArray("variants")?:JSONArray()
        for(g in 0 until variants.length()){val group=variants.getJSONObject(g);val choices=group.getJSONArray("variations");Text(group.optString("name","Portion"),color=Muted,fontSize=12.sp);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){for(v in 0 until choices.length()){val choice=choices.getJSONObject(v);val selected=item.optJSONArray("variants")?.let{a->(0 until a.length()).any{a.getJSONObject(it).optString("variation_id")==choice.getString("id")}}?:false;SmallChip(choice.getString("name"),selected){if(!model.loading)model.changeVariant(id,group.getString("groupId"),choice.getString("id"))}}}}
        val addons=custom?.optJSONArray("addons")?:JSONArray()
        for(g in 0 until addons.length()){val group=addons.getJSONObject(g);val choices=group.getJSONArray("choices");for(v in 0 until choices.length()){val choice=choices.getJSONObject(v);val chosen=item.optJSONArray("addons")?:JSONArray();val checked=(0 until chosen.length()).any{chosen.getJSONObject(it).optString("addon_id")==choice.getString("id")};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked,onCheckedChange={on->if(!model.loading){val updated=JSONArray();for(k in 0 until chosen.length()){val old=chosen.getJSONObject(k);if(old.optString("addon_id")!=choice.getString("id"))updated.put(old)};if(on)updated.put(JSONObject().put("group_id",group.getString("groupId")).put("addon_id",choice.getString("id")));model.changeAddons(id,updated)}});Text(choice.getString("name")+" · "+rupees(choice.optDouble("price")),color=White,fontSize=13.sp)}}}
        item.optJSONObject("nutrition")?.let{n->MacroStats(Nutrition.from(n));Text("Estimated ranges · includes quantity and extras",color=Muted,fontSize=12.sp)}
    }
}
@Composable internal fun PaymentScreen(model:HungiiModel) {
    var confirm by remember{mutableStateOf(false)}
    val c=model.checkoutCart.takeIf {model.paymentStage!="choose"}?:model.cart
    val pending=model.paymentStage in listOf("pending","unresolved","placing")
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        FocusHeader(if(BuildConfig.LOCAL_DEMO)"Mock payment" else "Payment"){model.goBack()}
        Text(c?.optString("restaurant")?:"Your basket",color=Muted,fontSize=14.sp)
        DisplayText(rupees(c?.optDouble("payable")?:0.0),48)
        Text(if(BuildConfig.LOCAL_DEMO)"Simulator only. No money leaves your account." else if(model.environment=="staging")"Swiggy staging · no real order or charge" else "Powered by Swiggy",color=Accent,fontSize=12.sp)
        if(model.loading)ThinkingDots()
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp)
        if(model.paymentStage=="choose"){
            Panel{
                Text("Pay using",color=White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                val methods=model.paymentOptions?.optJSONArray("allMethods")?:JSONArray()
                for(i in 0 until methods.length()){
                    val m=methods.getJSONObject(i);val enabled=m.optBoolean("enabled",true)&&!model.loading
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Raised).clickable(enabled=enabled){model.selectedMethodId=m.getString("id")}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.Payments,null,tint=White,modifier=Modifier.size(24.dp))
                        Text(m.optString("displayName",m.getString("id")),color=White,fontSize=14.sp,modifier=Modifier.weight(1f).padding(horizontal=12.dp))
                        RadioButton(model.selectedMethodId==m.getString("id"),onClick={model.selectedMethodId=m.getString("id")},enabled=enabled)
                    }
                }
                if(methods.length()==0)Text("Payment options will appear after the basket is verified.",color=Muted,fontSize=14.sp)
            }
            FilledInput(model.checkoutNote,{model.checkoutNote=it.take(200)},"Note to kitchen (optional)",Modifier.fillMaxWidth())
            if(model.cartRefreshRequired)OutlineButton("Refresh basket & payment options"){model.choosePayment()}
            PrimaryButton("Review order · "+rupees(c?.optDouble("payable")?:0.0),Icons.Outlined.Lock,enabled=!model.loading&&!model.cartRefreshRequired&&model.paymentOptions!=null&&model.selectedMethodId.isNotBlank()){confirm=true}
        }else if(pending){
            Panel{
                Icon(Icons.Outlined.HourglassTop,null,tint=Accent,modifier=Modifier.size(32.dp))
                DisplayText(if(model.paymentStage=="unresolved")"Checking your order" else "Payment pending",26)
                Text(if(BuildConfig.LOCAL_DEMO)"Choose a mock outcome. Hungii checks payment before confirming the order." else "We haven’t confirmed this order yet. Check its status before trying another payment or placing another order.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
                if(BuildConfig.LOCAL_DEMO){
                    PrimaryButton("Simulate success",Icons.Outlined.Check,enabled=!model.loading){model.simulatePayment("SUCCESS")}
                    OutlineButton("Simulate failure"){if(!model.loading)model.simulatePayment("FAILED")}
                    TextButton(onClick={if(!model.loading)model.simulatePayment("CANCELLED")}){Text("Cancel mock payment",color=SoftCrimson)}
                }else{
                    model.payment?.optString("bridgeUrl")?.takeIf {it.startsWith("https://")&&it!="null"}?.let {url->
                        PrimaryButton("Continue to UPI payment",Icons.Outlined.Payments,enabled=!model.loading){try{androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url))}catch(_:Exception){model.connectionMessage="Open Swiggy to check payment. No browser is available."}}
                    }
                    OutlineButton("Check in Swiggy"){openSwiggy(context)}
                }
                PrimaryButton("Check payment status",Icons.Outlined.Refresh,enabled=!model.loading){model.refreshPayment()}
            }
        }else if(model.paymentStage=="failed")Panel{
            DisplayText("Payment didn’t complete",27)
            Text("Review your basket and current payment options before another attempt.",color=Muted,fontSize=14.sp)
            PrimaryButton("Choose payment again",Icons.Outlined.Refresh,enabled=!model.loading){model.retryPayment()}
        }else if(model.paymentStage=="confirmed")PrimaryButton("View confirmed order",Icons.Outlined.Check){model.screen=Screen.Order;model.refreshOrder()}
        McpActivity(model);Spacer(Modifier.height(20.dp))
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},containerColor=Surface,title={Text(if(BuildConfig.LOCAL_DEMO)"Confirm mock order" else if(model.environment=="staging")"Confirm staging order" else "Place your Swiggy order?")},text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(c?.optString("restaurant")?:"")
            Text("Deliver to: "+(c?.optString("address")?:""))
            val items=c?.optJSONArray("items")?:JSONArray()
            for(i in 0 until items.length()){val item=items.getJSONObject(i);Text("${item.optInt("quantity")} × ${item.optString("name")}")}
            val methods=model.paymentOptions?.optJSONArray("allMethods")?:JSONArray()
            val method=(0 until methods.length()).map {methods.getJSONObject(it)}.firstOrNull{it.optString("id")==model.selectedMethodId}
            Text("${rupees(c?.optDouble("payable")?:0.0)} · ${method?.optString("displayName")?:model.selectedMethodId}",color=Accent)
            Text(if(BuildConfig.LOCAL_DEMO||model.environment=="staging")"Test order only. No real money or delivery." else "Confirming starts a real Swiggy order. UPI payment is authorized in your UPI app.",color=Muted,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick={confirm=false;model.beginPayment()}){Text(if(BuildConfig.LOCAL_DEMO||model.environment=="staging")"Confirm test order" else "Place order")}},dismissButton={TextButton(onClick={confirm=false}){Text("Keep reviewing")}})
}
@Composable internal fun OrderScreen(model:HungiiModel) {
    val context=LocalContext.current
    val tracking=model.order?.optJSONObject("tracking")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)){
        Box(Modifier.size(90.dp).background(Accent,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Check,"Order confirmed",tint=Charcoal,modifier=Modifier.size(42.dp))}
        DisplayText(if(BuildConfig.LOCAL_DEMO)"Test order confirmed" else if(model.environment=="staging")"Staging order confirmed" else "Swiggy order confirmed",30)
        Panel{
            Text(if(BuildConfig.LOCAL_DEMO||model.environment=="staging")"Test environment · no delivery" else "Powered by Swiggy",color=Accent,fontSize=14.sp)
            Text("Order #${model.payment?.optString("orderId")?:model.order?.optString("orderId")?:""}",color=Muted,fontSize=12.sp)
            Text(model.checkoutCart?.optString("restaurant")?:"",color=White,fontSize=19.sp,fontWeight=FontWeight.Bold)
            BillLine("Total",rupees(model.checkoutCart?.optDouble("payable")?:0.0));HorizontalDivider(color=Line)
            if(BuildConfig.LOCAL_DEMO)Text("Synthetic delivery estimate: 20–25 min",color=Muted,fontSize=12.sp)
            else {
                Text(tracking?.optString("statusMessage")?.takeIf {it.isNotBlank()}?:tracking?.optString("orderStatus")?.takeIf{it.isNotBlank()}?:"Tracking not available yet",color=White,fontSize=16.sp)
                tracking?.optString("etaText")?.takeIf{it.isNotBlank()}?.let{Text(it,color=Muted,fontSize=14.sp)}
            }
        }
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp)
        OutlineButton("Refresh tracking"){model.refreshOrder()}
        if(!BuildConfig.LOCAL_DEMO)OutlineButton("Support or cancellation · Swiggy"){openSwiggy(context)}
        Text("Log food when you eat it. Ordering alone does not count toward your nutrition totals.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
        PrimaryButton(if(BuildConfig.LOCAL_DEMO)"Log as eaten · estimated macros" else "Log what I ate",Icons.Outlined.Restaurant){model.logOrderAsEaten()}
        OutlineButton("Back to my day"){model.screen=Screen.Day};McpActivity(model)
    }
}
