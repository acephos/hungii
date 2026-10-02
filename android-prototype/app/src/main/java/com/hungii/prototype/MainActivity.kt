package com.hungii.prototype

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val Charcoal=Color(0xFF101211)
private val Surface=Color(0xFF1B1F1D)
private val Raised=Color(0xFF232925)
private val Lime=Color(0xFFCAFF48)
private val White=Color(0xFFF5F7EE)
private val Muted=Color(0xFF99A29B)
private val Line=Color(0xFF303831)
private val Coral=Color(0xFFFF9A82)
private val Cyan=Color(0xFF90DAD7)
private val Display=FontFamily(Font(R.font.barlow_condensed_bold,FontWeight.Bold))

data class VoiceState(val listening: Boolean=false, val level: Float=0f)

class MainActivity: ComponentActivity() {
    private val model: HungiiModel by viewModels()
    private val voice=mutableStateOf(VoiceState())
    private var recognizer: SpeechRecognizer?=null
    private var onWords: (String)->Unit={}
    private val microphonePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) listen() else Toast.makeText(this,"You can type your check-in instead.",Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(16,18,17)))
        intent.data?.let(model::handleCallback)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Charcoal,background=Charcoal,surface=Surface,onSurface=White,onBackground=White,outline=Line)) {
                onWords={ model.update(it) }
                HungiiApp(model,voice.value,::requestVoice)
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.data?.let(model::handleCallback) }
    private fun requestVoice() {
        if(voice.value.listening) { recognizer?.stopListening(); voice.value=VoiceState(); return }
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Toast.makeText(this,"On-device voice unavailable. Type your check-in; audio stays on your phone.",Toast.LENGTH_LONG).show(); return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) listen()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun listen() {
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this).apply {
            setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voice.value=VoiceState(true) }
                override fun onBeginningOfSpeech() { voice.value=VoiceState(true) }
                override fun onRmsChanged(rmsdB: Float) { voice.value=VoiceState(true,((rmsdB+2)/12).coerceIn(0f,1f)) }
                override fun onEndOfSpeech() { voice.value=VoiceState() }
                override fun onResults(results: Bundle?) { voice.value=VoiceState(); results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onWords) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onError(error: Int) { voice.value=VoiceState(); Toast.makeText(this@MainActivity,"Voice did not connect. You can still type.",Toast.LENGTH_SHORT).show() }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEvent(eventType: Int,params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-IN")
            })
        }
    }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HungiiApp(model: HungiiModel,voice: VoiceState,onVoice: ()->Unit) {
    var goalsOpen by remember { mutableStateOf(false) }
    var detailMeal by remember { mutableStateOf<Meal?>(null) }
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    val reduceMotion=remember { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    if(model.initializing || model.accountLoading) {
        Box(Modifier.fillMaxSize().background(Charcoal),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                CircularProgressIndicator(color=Lime)
                Text("Restoring your Hungii account…",color=White)
            }
        }
        return
    }
    if(!BuildConfig.LOCAL_DEMO && !model.signedIn && !model.offlineMode) {
        AccountEntry(model)
        return
    }
    if(!BuildConfig.LOCAL_DEMO && model.signedIn && model.cloudSetupPending) {
        CloudEntry(model)
        return
    }
    val focused=model.screen in listOf(Screen.Finalists,Screen.Draw,Screen.Winner,Screen.Review)
    BackHandler(model.screen!=Screen.Home) { model.goBack() }
    LaunchedEffect(model.shuffling) {
        if(model.shuffling) {
            delay(if(reduceMotion) 40 else 1900)
            model.shuffling=false
            model.canPick=true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(model.pickedIndex) {
        if(model.pickedIndex!=null && model.screen==Screen.Draw) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(if(reduceMotion) 40 else 700)
            if(model.screen==Screen.Draw) model.screen=Screen.Winner
        }
    }
    Scaffold(
        modifier=Modifier.fillMaxSize(),containerColor=Charcoal,
        contentWindowInsets=WindowInsets.safeDrawing,
        bottomBar={ if(!focused) BottomTabs(model) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
            if(BuildConfig.LOCAL_DEMO) Text("LOCAL DEMO · Synthetic meals · No Swiggy connection",color=Lime,fontSize=10.sp,modifier=Modifier.fillMaxWidth().background(Surface).padding(8.dp),textAlign=TextAlign.Center)
            Box(Modifier.weight(1f)) {
            when(model.screen) {
                Screen.Home -> HomeScreen(model,voice,onVoice,{goalsOpen=true})
                Screen.Discover -> DiscoverScreen(model,{detailMeal=it})
                Screen.Finalists -> FinalistsScreen(model)
                Screen.Draw -> DrawScreen(model,reduceMotion)
                Screen.Winner -> WinnerScreen(model,{detailMeal=it})
                Screen.Review -> ReviewScreen(model)
                Screen.Saved -> SavedScreen(model)
                Screen.Day -> DayScreen(model,{goalsOpen=true})
            }
            }
        }
    }
    if(model.accountOpen) AccountDialog(model) { model.accountOpen=false }
    model.pendingSave?.let { meal ->
        AlertDialog(onDismissRequest={model.pendingSave=null},containerColor=Surface,title={Text("Remember this meal?",color=White)},text={Text("Allow Hungii to keep ${meal.name}, its name, restaurant and menu identifiers encrypted on this device for up to 30 days. Prices and photos are not saved. Delete saved meals and withdraw permission from Saved. Notice ${model.privacyVersion}.",color=Muted)},confirmButton={TextButton(onClick={model.acceptSaving()}){Text("Allow & save",color=Lime)}},dismissButton={TextButton(onClick={model.pendingSave=null}){Text("Cancel",color=Muted)}})
    }
    model.privacyAction?.let {action ->
        val title=when(action){"state_save"->"Sync profile & preferences?";"disconnect"->"Disconnect Swiggy?";"delete_account"->"Delete your Hungii account?";"delete_cloud_tracker"->"Erase your cloud tracker?";else->"Erase data on this device?"}
        val explanation=when(action){"state_save"->"Allow automatic sync of your entered profile, goals, food allowance, daily totals and meal filters to your encrypted Hungii account in Mumbai. Restore them on another device after sign-in. Cloud data expires after 90 days without updates. You can stop syncing or erase the cloud copy from Accounts. Notice ${model.privacyVersion}.";"disconnect"->"Erase Hungii's Swiggy connection and saved meals. Hungii also asks Swiggy to revoke access; remote success is reported separately.";"delete_account"->"Erase your cloud tracker, Swiggy connection and this account's device data, then delete your WorkOS login. If provider deletion fails, you can retry. An account hash is kept for 24 hours to prevent requests from restoring erased data. This cannot be undone.";"delete_cloud_tracker"->"Erase your cloud profile, preferences and tracker, and stop syncing this device. Your device copy stays available.";else->"Erase your entered totals and saved meals on this device. Your cloud data is managed separately."}
        AlertDialog(onDismissRequest={model.privacyAction=null},containerColor=Surface,title={Text(title,color=White)},text={Text(explanation,color=Muted)},confirmButton={TextButton(onClick={when(action){"state_save"->{model.privacyAction=null;model.syncTracker()};"disconnect"->{model.privacyAction=null;model.disconnect()};else->model.performPrivacyAction()}}){Text(if(action=="state_save")"Allow & sync" else "Confirm",color=if(action=="state_save")Lime else Coral)}},dismissButton={TextButton(onClick={model.privacyAction=null}){Text("Cancel",color=Muted)}})
    }
    if(goalsOpen) GoalsDialog(model) { goalsOpen=false }
    detailMeal?.let { meal ->
        ModalBottomSheet(onDismissRequest={detailMeal=null},containerColor=Surface) {
            Column(Modifier.padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Eyebrow("THE WHOLE PICTURE",Lime)
                DisplayText(meal.name.uppercase(),34)
                Text(meal.benefit,color=White,fontSize=15.sp)
                Text(meal.compromise,color=Coral,fontSize=15.sp)
                Text("${meal.priceLabel} is the menu price. Delivery, taxes and offers can change the final cart bill. Nutrition isn't published for this item in the connected menu.",color=Muted,lineHeight=23.sp)
                LimeButton("Got it",Icons.Outlined.Check) { detailMeal=null }
            }
        }
    }
}

@Composable
private fun AccountEntry(model:HungiiModel) {
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().background(Charcoal).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(36.dp))
        Text("hungii.",color=Lime,fontSize=42.sp,fontWeight=FontWeight.Black)
        Eyebrow("YOUR ACCOUNT. YOUR FUEL.",Muted)
        DisplayText("YOUR DAY.\nYOUR FUEL.\nYOUR CHOICE.",54)
        Text("Keep your goals, food allowance and meal preferences together. Sign in to restore your profile across devices.",color=Muted,fontSize=16.sp,lineHeight=25.sp)
        Column(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(20.dp)).padding(18.dp)) {TradeLine(Icons.Outlined.Person,Lime,"Hungii account · your profile and cloud sync");Spacer(Modifier.height(12.dp));TradeLine(Icons.Outlined.Restaurant,Muted,"Connect Swiggy later to discover meals.")}
        Spacer(Modifier.height(12.dp))
        LimeButton("Continue with email",Icons.Outlined.Login,enabled=BuildConfig.WORKOS_AUTH_READY&&!model.loading) {
            try {CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(model.signInUrl()))} catch(e:ApiFailure){model.connectionMessage=e.message} catch(_:Exception){model.connectionMessage="A browser is needed to sign in."}
        }
        Text("New here? The same email flow creates your account. Choose Email sign-in code if a password screen appears.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
        TextButton(onClick={model.useOffline()}) {Text("Use locally without an account",color=Muted)}
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=Coral,fontSize=13.sp)
        Text("WorkOS handles email sign-in. Cloud sync is your choice after sign-in. No Swiggy login is needed to save your goals.",color=Muted,fontSize=11.sp,lineHeight=18.sp)
    }
}

@Composable
private fun CloudEntry(model:HungiiModel) {
    Column(Modifier.fillMaxSize().background(Charcoal).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(36.dp))
        Text("hungii.",color=Lime,fontSize=42.sp,fontWeight=FontWeight.Black)
        Eyebrow("HUNGII ACCOUNT CONNECTED",Lime)
        DisplayText("YOUR PROFILE.\nYOUR RULES.",51)
        Text("Allow encrypted cloud sync for your name, nutrition goals, food allowance, entered daily totals and meal filters. Changes save automatically and restore when you sign in on another device.",color=Muted,fontSize=16.sp,lineHeight=25.sp)
        Text("Stored in Hungii's Mumbai database. Cloud data expires after 90 days without updates. Erase the cloud copy or stop syncing in Accounts. Saved Swiggy meal shortcuts stay on this device. Privacy notice ${model.privacyVersion}.",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        LimeButton("Allow profile & preference sync",Icons.Outlined.CloudUpload,enabled=!model.loading) {model.syncTracker()}
        if(model.cloudConflict) {
            Text(model.cloudSyncMessage,color=Coral,fontSize=13.sp)
            TextButton(onClick={model.useCloudCopy()}) {Text("Use my cloud profile",color=Lime)}
            TextButton(onClick={model.keepLocalCopy()}) {Text("Sync this device's profile instead",color=Coral)}
        }
        TextButton(onClick={model.keepDeviceOnly()}) {Text("Continue without cloud sync",color=Muted)}
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=Coral,fontSize=13.sp)
    }
}

@Composable
private fun BrandHeader(onDay: ()->Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("hungii",fontSize=29.sp,fontWeight=FontWeight.Black,letterSpacing=(-1.5).sp,color=White)
            Text(".",fontSize=36.sp,fontWeight=FontWeight.Black,color=Lime)
            Spacer(Modifier.width(10.dp)); Badge("HUNGII",Muted,Surface)
        }
        IconButton(onClick=onDay) { Icon(Icons.Outlined.Tune,"Edit your day",tint=White,modifier=Modifier.size(23.dp)) }
    }
}

@Composable
private fun HomeScreen(model: HungiiModel,voice: VoiceState,onVoice: ()->Unit,onDay: ()->Unit) {
    var message by remember { mutableStateOf("") }
    val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<380
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 12.dp)) {
        BrandHeader(onDay)
        ConnectionStrip(model)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
            DisplayText("LESS THINKING.\nMORE FUEL.",if(compact)43 else 49)
            Column(horizontalAlignment=Alignment.End,modifier=Modifier.padding(bottom=6.dp)) {
                Eyebrow("YOUR DAY",Muted);Text("STILL IN PLAY",fontSize=10.sp,color=Lime,fontWeight=FontWeight.Bold)
            }
        }
        Box(Modifier.fillMaxWidth().height(if(compact)115.dp else 155.dp),contentAlignment=Alignment.Center) {
            Orb(Modifier.size(200.dp),voice.level,voice.listening)
            Column(Modifier.align(Alignment.CenterEnd).padding(end=3.dp),horizontalAlignment=Alignment.End) {
                Text(if(voice.listening) "LISTENING" else "READY WHEN\nYOU ARE",fontSize=9.sp,color=Muted,lineHeight=15.sp,textAlign=TextAlign.End,letterSpacing=1.4.sp)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            MetricTile("KCAL LEFT",model.caloriesLeft.label,"${model.intake.calories.label} consumed",Lime,Modifier.weight(1f))
            MetricTile("RUPEES LEFT","₹${model.moneyLeft}","~₹${model.mealMoneyGuide} per opportunity",White,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical=2.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${model.opportunities} eating opportunities left",color=Muted,fontSize=12.sp)
            Text("P ${model.intake.protein.label}g  C ${model.intake.carbs.label}g  F ${model.intake.fat.label}g",color=Muted,fontSize=10.sp)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(6.dp).background(Lime,CircleShape))
            Text("What are you craving?",color=White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value=message,onValueChange={message=it},placeholder={Text("Spicy? Cheesy? Tell me.",fontSize=13.sp)},singleLine=true,modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Lime,unfocusedBorderColor=Line,focusedTextColor=White,unfocusedTextColor=White),trailingIcon={IconButton(onClick={if(message.isNotBlank()){model.update(message);message=""}}) {Icon(Icons.Outlined.ArrowUpward,"Send check-in",tint=Lime)}})
            RoundAction(if(voice.listening) Icons.Outlined.Stop else Icons.Outlined.MicNone,if(voice.listening) "Stop listening" else "Voice check-in",Lime,Charcoal,onVoice)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Spicy","Cheesy","₹350 left","2 meals left").forEach { label -> SmallChip(label,false) { model.update(label) } }
        }
        if(model.receipt.isNotEmpty()) {
            if(model.canUndoInput) Receipt(model.receipt) { model.undoInput() }
            else Text(model.receipt,color=Muted,fontSize=11.sp,lineHeight=18.sp)
        }
        LimeButton("Find my next meal",Icons.Outlined.ArrowForward,enabled=!model.loading) { model.search() }
        Text("Your targets · local check-in · optional device voice",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=14.dp))
    }
}

@Composable
private fun DiscoverScreen(model: HungiiModel,onDetails: (Meal)->Unit) {
    val haptic=LocalHapticFeedback.current
    val meal=model.pool.firstOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(Modifier.fillMaxWidth().height(54.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            DisplayText("FIND YOUR THREE.",34)
            Badge("${model.finalists.size} / 3 PICKED",Lime,Raised)
        }
        Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("₹${model.moneyLeft} left",color=White,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
            Text("${model.caloriesLeft.label} kcal",color=Muted,fontSize=12.sp,lineHeight=16.sp)
            Text("${model.opportunities} opportunities",color=Muted,fontSize=12.sp,lineHeight=16.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            SmallChip("Spicy",model.taste=="spicy") { model.taste=if(model.taste=="spicy") "any" else "spicy" }
            SmallChip("Health menus",model.highProtein) { model.highProtein=!model.highProtein }
            SmallChip("Under ₹250",model.budgetOnly) { model.budgetOnly=!model.budgetOnly }
            SmallChip("Veg only",model.vegOnly) { model.vegOnly=!model.vegOnly }
            SmallChip("Fast",model.fast) { model.fast=!model.fast }
        }
        OutlinedTextField(model.query,onValueChange={model.query=it},label={Text("Search meals",fontSize=11.sp)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp),trailingIcon={IconButton(onClick={model.search()}){Icon(Icons.Outlined.Search,"Search Swiggy",tint=Lime)}})
        if(model.loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Lime)
        if(model.connectionMessage.isNotBlank()) Text(model.connectionMessage,color=Coral,fontSize=12.sp,lineHeight=18.sp)
        if(meal!=null) {
            MealProfile(meal,model,Modifier.weight(1f),onDetails,onSwipe={ like ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if(like) model.like(meal) else model.pass(meal)
            })
            Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(18.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                RoundAction(Icons.Outlined.Undo,"Undo swipe",Surface,Muted,{model.undoSwipe()},48)
                RoundAction(Icons.Outlined.Close,"Pass meal",Surface,White,{model.pass(meal)},60)
                RoundAction(Icons.Outlined.FavoriteBorder,"Shortlist meal",Lime,Charcoal,{model.like(meal);haptic.performHapticFeedback(HapticFeedbackType.LongPress)},66)
            }
            Row(Modifier.fillMaxWidth().padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Swipe left to pass. Right to keep.",color=Muted,fontSize=10.sp,lineHeight=14.sp)
                if(model.finalists.isNotEmpty()) TextButton(onClick={model.showFinalists()},contentPadding=PaddingValues(0.dp)) { Text("See ${model.finalists.size} finalist${if(model.finalists.size>1)"s" else ""} →",fontSize=11.sp,color=Lime) }
                else Text("${model.pool.size} in this batch",color=Muted,fontSize=10.sp,lineHeight=14.sp)
            }
        } else {
            Column(Modifier.weight(1f).fillMaxWidth(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                DisplayText(if(!model.connected) "CONNECT.\nTHEN DISCOVER." else "NO MORE IN\nTHIS BATCH.",44)
                Spacer(Modifier.height(18.dp))
                Text(if(!model.connected) "Connect your account and choose a delivery address to see Swiggy meals." else "Try a different dish or choose an open restaurant below. Your dietary filters stay yours.",color=Muted,textAlign=TextAlign.Center,lineHeight=22.sp)
                Spacer(Modifier.height(24.dp))
                if(model.finalists.isNotEmpty()) LimeButton("See my finalists",Icons.Outlined.ArrowForward) {model.showFinalists()}
                if(!model.connected||model.addressId==null) LimeButton("Connect Swiggy",Icons.Outlined.Link) {model.accountOpen=true}
                Column(Modifier.heightIn(max=160.dp).verticalScroll(rememberScrollState())) {
                    model.restaurants.take(8).forEach {r->TextButton(onClick={model.restaurantMeals(r)}) {Text(r.name+(r.distanceKm?.let {" · $it km"} ?: "")+(r.etaMinutes?.let {" · ~$it min"} ?: ""),color=Lime,maxLines=2)}}
                }
                TextButton(onClick={model.passed.clear()}) {Text("Revisit passed meals",color=Lime)}
            }
        }
    }
}

@Composable
private fun MealProfile(meal: Meal,model: HungiiModel,modifier: Modifier,onDetails: (Meal)->Unit,onSwipe: (Boolean)->Unit) {
    var drag by remember(meal.id) { mutableFloatStateOf(0f) }
    val n=meal.nutrition
    Column(modifier.graphicsLayer { translationX=drag;rotationZ=drag/35f }.clip(RoundedCornerShape(26.dp)).background(Surface)
        .pointerInput(meal.id) {
            detectHorizontalDragGestures(onDragEnd={ if(abs(drag)>100.dp.toPx()) onSwipe(drag>0);drag=0f },onDragCancel={drag=0f}) { change, amount -> change.consume(); drag+=amount }
        }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MealImage(meal,Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.25f)))))
            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Badge(meal.badge,Charcoal,Lime)
                IconButton(onClick={model.toggleSaved(meal)},modifier=Modifier.size(38.dp).background(Charcoal.copy(alpha=.7f),CircleShape)) {
                    Icon(if(meal.id in model.saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Save ${meal.name}",tint=if(meal.id in model.saved)Lime else White,modifier=Modifier.size(20.dp))
                }
            }
            if(abs(drag)>30) Badge(if(drag>0) "KEEP" else "PASS",if(drag>0)Charcoal else White,if(drag>0)Lime else Color.Black.copy(alpha=.7f),Modifier.align(Alignment.Center).graphicsLayer { rotationZ=if(drag>0)-12f else 12f })
            Text(if(BuildConfig.LOCAL_DEMO) "SYNTHETIC DEMO" else "SWIGGY MENU PHOTO",fontSize=8.sp,color=White.copy(alpha=.85f),letterSpacing=1.sp,modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text(meal.badge,color=Lime,fontSize=9.sp,lineHeight=12.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(meal.restaurant,color=Muted,fontSize=11.sp,lineHeight=16.sp)
                Text(if(meal.veg==true) "● VEG" else if(meal.veg==false) "● NON-VEG" else "DIET UNKNOWN",color=if(meal.veg==true)Lime else Coral,fontSize=9.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold)
            }
            DisplayText(meal.name.uppercase(),28,maxLines=2)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Row(verticalAlignment=Alignment.Bottom) {DisplayText(meal.priceLabel,if(meal.itemPrice==null)18 else 29);Text("  menu price",color=Muted,fontSize=10.sp,modifier=Modifier.padding(bottom=4.dp))}
                Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.Schedule,null,tint=Muted,modifier=Modifier.size(13.dp));Spacer(Modifier.width(4.dp));Text(meal.etaLabel,color=White,fontSize=11.sp)}
            }
            MacroStats(n)
            Text("NUTRITION NOT PUBLISHED · FEES EXTRA",color=Muted,fontSize=8.sp,lineHeight=12.sp,letterSpacing=.8.sp)
            TradeLine(Icons.Outlined.Add,Lime,if(model.taste in meal.tags) "Your craving appears in the menu description." else meal.distanceKm?.let { "${it} km away · ${meal.etaLabel}" } ?: meal.etaLabel)
            TradeLine(Icons.Outlined.Remove,Coral,meal.compromise,maxLines=2)
            Row(Modifier.fillMaxWidth().clickable { onDetails(meal) },horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Final bill checked in Swiggy cart",fontSize=10.sp,lineHeight=14.sp,color=Muted)
                Icon(Icons.Outlined.ArrowOutward,"Meal trade-off details",tint=Lime,modifier=Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FinalistsScreen(model: HungiiModel) {
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FocusHeader("YOUR FINALISTS") {model.goBack()}
        DisplayText("YOU PICKED\nTHE GOOD ONES.",52)
        Text("All of these got a yes. The last choice can be the easy one.",color=Muted,lineHeight=23.sp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)) {
            model.finalists.toList().forEachIndexed { i,m ->
                Row(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(Surface),verticalAlignment=Alignment.CenterVertically) {
                    MealImage(m,Modifier.width(105.dp).fillMaxHeight())
                    Column(Modifier.weight(1f).padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Eyebrow("FINALIST 0${i+1}",Lime)
                        Text(m.name,color=White,fontSize=15.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Text("${m.priceLabel} · ${m.etaLabel}",color=Muted,fontSize=10.sp,lineHeight=14.sp)
                    }
                    IconButton(onClick={model.remove(m)},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.Close,"Remove ${m.name}",tint=Muted,modifier=Modifier.size(16.dp))}
                }
            }
        }
        LimeButton("Turn over & shuffle",Icons.Outlined.Shuffle) {model.startDraw()}
        Text("Same meals. Equal chances. Nothing ordered.",color=Muted,fontSize=11.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=24.dp))
    }
}

@Composable
private fun DrawScreen(model: HungiiModel,reduceMotion: Boolean) {
    var backsUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { backsUp=true }
    val infinite=rememberInfiniteTransition(label="shuffle")
    val phase by infinite.animateFloat(0f,1f,infiniteRepeatable(tween(850,easing=LinearEasing)),label="mix")
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp)) {
        FocusHeader("THE LUCKY DRAW") {model.goBack()}
        Spacer(Modifier.height(28.dp))
        Eyebrow("ROUND TWO",Lime)
        DisplayText(if(model.shuffling) "LET LUCK\nDO ITS THING." else if(model.pickedIndex!=null) "THAT’S\nYOUR PICK." else "GO WITH\nYOUR GUT.",60)
        Spacer(Modifier.height(14.dp))
        Text(if(model.shuffling) "Turning your three maybes into one next meal." else "Tap a card. Every meal already earned its place.",color=Muted,lineHeight=23.sp)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            val cardWidth=(maxWidth-24.dp)/3
            val travel=cardWidth+12.dp
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                model.drawOrder.forEachIndexed { i,m ->
                    val move=if(model.shuffling&&!reduceMotion) sin((phase*2*PI).toFloat()+i*2*PI.toFloat()/3)*travel.value*.75f else 0f
                    val picked=model.pickedIndex==i
                    val turned by animateFloatAsState(if(picked||!backsUp)0f else 180f,tween(if(reduceMotion)0 else 450),label="flip")
                    Box(Modifier.width(cardWidth).height(cardWidth*1.62f).graphicsLayer {
                        translationX=move.dp.toPx();translationY=if(model.shuffling&&!reduceMotion) cos(phase*2*PI+i.toDouble()).toFloat()*18.dp.toPx() else if(picked)-12.dp.toPx() else 0f
                        rotationZ=if(model.shuffling&&!reduceMotion) move/12 else (i-1)*4f
                        alpha=if(model.pickedIndex!=null&&!picked).25f else 1f
                        rotationY=turned;cameraDistance=16*density
                    }.clip(RoundedCornerShape(18.dp)).background(if(picked) Surface else Lime)
                        .border(1.dp,if(picked)Lime else Lime.copy(alpha=.5f),RoundedCornerShape(18.dp))
                        .clickable(enabled=model.canPick&&!model.shuffling&&model.pickedIndex==null) {model.pick(i)}
                        .semantics {contentDescription=if(picked) "Revealed ${m.name}" else "Pick card ${i+1}"},contentAlignment=Alignment.Center) {
                        if(turned<90) Column(Modifier.fillMaxSize()) {
                            MealImage(m,Modifier.weight(1f).fillMaxWidth())
                            Text(m.name,color=White,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp),maxLines=3)
                        } else Column(Modifier.fillMaxSize().graphicsLayer {rotationY=180f}.padding(10.dp).border(1.dp,Charcoal.copy(alpha=.25f),RoundedCornerShape(10.dp)),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text("h.",fontSize=54.sp,fontWeight=FontWeight.Black,letterSpacing=(-3).sp,color=Charcoal)
                            Spacer(Modifier.height(15.dp));Text("FUEL YOUR DAY",fontSize=7.sp,color=Charcoal,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom=20.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Icon(if(model.shuffling)Icons.Outlined.Shuffle else Icons.Outlined.TouchApp,null,tint=Lime,modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp));Text(if(model.shuffling)"Mixing…" else "Pick any card. You’re in control.",color=White,fontSize=13.sp)
        }
        TextButton(onClick={model.shuffling=false;model.screen=Screen.Finalists},modifier=Modifier.fillMaxWidth().padding(bottom=16.dp)) {Text("View my finalists",color=Muted,fontSize=12.sp)}
    }
}

@Composable
private fun WinnerScreen(model: HungiiModel,onDetails: (Meal)->Unit) {
    val meal=model.winner ?: return
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FocusHeader("YOUR LUCKY PICK") {model.goBack()}
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.AutoAwesome,null,tint=Lime,modifier=Modifier.size(22.dp));Spacer(Modifier.width(10.dp));DisplayText("IT’S A MEAL MATCH.",40)}
        Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(26.dp))) {
            MealImage(meal,Modifier.fillMaxSize())
            Badge(meal.badge,Charcoal,Lime,Modifier.align(Alignment.TopStart).padding(15.dp))
        }
        DisplayText(meal.name.uppercase(),40)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(meal.restaurant,color=Muted,fontSize=12.sp);Text(meal.etaLabel,color=Muted,fontSize=12.sp)}
        MacroStats(meal.nutrition)
        Text("Nutrition not published · calories and macros unknown",color=Muted,fontSize=10.sp)
        TradeLine(Icons.Outlined.Add,Lime,meal.benefit)
        TradeLine(Icons.Outlined.Remove,Coral,meal.compromise,maxLines=3)
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {DisplayText(meal.priceLabel,if(meal.itemPrice==null)22 else 37);Text("menu price · fees extra",color=Muted,fontSize=11.sp)}
        TextButton(onClick={onDetails(meal)},modifier=Modifier.fillMaxWidth()) {Text("See what this leaves for later",color=Muted,fontSize=12.sp)}
        Text("The draw never changes a cart or places an order.",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=20.dp))
    }
    Box(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal=22.dp,vertical=12.dp)) {
        LimeButton("Let’s have this",Icons.Outlined.ArrowForward) {model.review()}
    }
    }
}

@Composable
private fun ReviewScreen(model: HungiiModel) {
    val meal=model.winner ?: return
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FocusHeader("MEAL REVIEW") {model.goBack()}
            DisplayText("YOUR PICK.\nYOUR LIVE CART.",48)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MealImage(meal,Modifier.size(75.dp).clip(RoundedCornerShape(13.dp)))
                Column {Text(meal.name,color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(meal.priceLabel+" menu price",color=Muted,fontSize=12.sp)}
            }
            MacroStats(meal.nutrition)
            Text("Nutrition is unavailable for this item. Log your own known intake in My day.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
            Text("Choosing this meal has not changed your Swiggy cart or placed an order. Complete your basket and checkout in Swiggy.",color=Muted,lineHeight=22.sp)
            if(model.loading) CircularProgressIndicator(color=Lime)
            if(model.connectionMessage.isNotBlank()) Text(model.connectionMessage,color=Coral,fontSize=13.sp)
            model.cart?.let {cart ->
                val matching=cart.optString("restaurantId")==meal.restaurantId
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                    Eyebrow("YOUR EXISTING SWIGGY CART",Muted)
                    Text(cart.optString("restaurant").takeIf {it!="null"} ?: "Empty cart",color=White)
                    val items=cart.optJSONArray("items")
                    if(items==null||items.length()==0) Text("Add your chosen meal in Swiggy.",color=Muted)
                    else for(i in 0 until items.length()) {val item=items.getJSONObject(i);Text("${item.optInt("quantity")} × ${item.optString("name")}",color=Muted,fontSize=12.sp)}
                    if(!matching&&items!=null&&items.length()>0) Text("This cart belongs to a different restaurant from your pick.",color=Coral,fontSize=12.sp)
                    if(!cart.isNull("payable")) {
                        BillLine("Food",rupees(cart.optDouble("itemTotal")))
                        BillLine("Delivery",rupees(cart.optDouble("deliveryCharge")))
                        BillLine("Taxes & charges",rupees(cart.optDouble("taxes")))
                        if(!cart.isNull("appliedCoupon")) BillLine("Applied: ${cart.optString("appliedCoupon")}","−"+rupees(cart.optDouble("couponDiscount")),Lime)
                        HorizontalDivider(color=Line)
                        BillLine("Current cart payable",rupees(cart.getDouble("payable")),Lime)
                    } else Text("Live total is unavailable until the connection is verified.",color=Muted,fontSize=12.sp)
                }
            }
            model.coupons?.optJSONArray("sections")?.let {sections ->
                for(i in 0 until sections.length()) {
                    val section=sections.getJSONObject(i);val offers=section.optJSONArray("coupons") ?: continue
                    for(j in 0 until minOf(offers.length(),5)) {
                        val offer=offers.getJSONObject(j)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(offer.optString("title","Offer"),color=Lime,fontWeight=FontWeight.Bold)
                            Text(offer.optString("description").ifBlank {offer.optString("subtitle")},color=Muted,fontSize=12.sp,lineHeight=18.sp)
                            Text("Eligibility depends on your current cart and payment method. This offer has not been applied.",color=Muted,fontSize=10.sp,lineHeight=16.sp)
                        }
                    }
                }
            }
            TextButton(onClick={model.review()}) {Text("Refresh cart & offers",color=Lime)}
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().background(Charcoal).padding(22.dp)) {
            LimeButton(if(BuildConfig.LOCAL_DEMO) "Demo · checkout unavailable" else "Continue in Swiggy",Icons.Outlined.OpenInNew,enabled=!BuildConfig.LOCAL_DEMO) {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.swiggy.com/")))}
        }
    }
}

@Composable
private fun SavedScreen(model: HungiiModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        BrandHeader {model.screen=Screen.Day}
        DisplayText("YOUR USUALS.\nYOUR SHORTCUT.",50)
        Text("Saved on this device with your permission. Search again to check today's availability and price.",color=Muted,fontSize=13.sp,lineHeight=22.sp)
        model.savedMeals.toList().forEach {meal ->
            Row(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(22.dp)).background(Surface),verticalAlignment=Alignment.CenterVertically) {
                MealImage(meal,Modifier.width(115.dp).fillMaxHeight())
                Column(Modifier.weight(1f).padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(meal.name,color=White,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=19.sp,maxLines=2)
                    Text(meal.restaurant,color=Muted,fontSize=10.sp,lineHeight=14.sp,maxLines=2)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={model.query=meal.name;model.search()},contentPadding=PaddingValues(0.dp)) {Text("Find again →",color=Lime,fontSize=12.sp)}
                        IconButton(onClick={model.toggleSaved(meal)},modifier=Modifier.size(35.dp)) {Icon(Icons.Outlined.Favorite,"Unsave ${meal.name}",tint=Lime,modifier=Modifier.size(17.dp))}
                    }
                }
            }
        }
        if(model.saved.isEmpty()) Text("Tap the heart on a live meal to make it a go-to.",color=Muted)
        if(model.savedConsent) TextButton(onClick={model.forgetSaved()}) {Text("Delete saved meals & withdraw saving permission",color=Coral,fontSize=12.sp)}
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
private fun DayScreen(model: HungiiModel,onEdit: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        BrandHeader(onEdit)
        DisplayText("YOUR DAY.\nNO FIXED SCHEDULE.",47)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface).padding(22.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(120.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {drawCircle(Line,style=Stroke(8.dp.toPx()));drawArc(Lime,-90f,(model.intake.calories.mid/model.calorieGoal).coerceIn(0f,1f)*360,false,style=Stroke(8.dp.toPx(),cap=StrokeCap.Round))}
                Column(horizontalAlignment=Alignment.CenterHorizontally) {DisplayText(model.caloriesLeft.label,37,Lime);Text("KCAL LEFT",color=Muted,fontSize=9.sp,letterSpacing=1.sp)}
            }
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {Text("${model.intake.calories.label} consumed",color=White,fontSize=13.sp);Text("${model.calorieGoal} daily target",color=Muted,fontSize=12.sp);Text("${model.opportunities} opportunities left",color=Muted,fontSize=12.sp)}
        }
        listOf(Triple("PROTEIN",model.intake.protein,model.proteinGoal),Triple("CARBS",model.intake.carbs,model.carbGoal),Triple("FAT",model.intake.fat,model.fatGoal)).forEachIndexed {i,(label,n,goal)->
            val color=listOf(Lime,Cyan,Coral)[i]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Eyebrow(label,color);Text("${n.label} / ${goal}g eaten",color=White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
                LinearProgressIndicator(progress={ (n.mid/goal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=color,trackColor=Line)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {MetricTile("ALLOWANCE LEFT","₹${model.moneyLeft}","₹${model.spent} spent / ₹${model.allowance}",Lime,Modifier.weight(1f));MetricTile("RESERVED FOOD",model.reservedCalories.label,"kcal not yet eaten",White,Modifier.weight(1f))}
        OutlineButton("Edit my targets & allowance") {onEdit()}
        Text("Your food log. Orders are not automatically counted as eaten. Nutrition totals contain only what you entered.",color=Muted,fontSize=11.sp,lineHeight=19.sp,modifier=Modifier.padding(bottom=20.dp))
    }
}

@Composable
private fun Orb(modifier: Modifier,level: Float,listening: Boolean) {
    val context=LocalContext.current
    val reduce=remember {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}
    val transition=rememberInfiniteTransition(label="orb")
    val phase by transition.animateFloat(0f,(2*PI).toFloat(),infiniteRepeatable(tween(9000,easing=LinearEasing)),label="orb rotation")
    val t=if(reduce)0f else phase
    Canvas(modifier.semantics {contentDescription=if(listening) "Orb listening to your voice" else "Hungii voice companion"}) {
        val c=center;val r=size.minDimension*.31f*(1f+level*.1f)
        drawCircle(Brush.radialGradient(listOf(Lime.copy(alpha=.12f+level*.1f),Color.Transparent),c,r*2.05f),r*2.05f,c)
        val path=Path()
        for(i in 0..100) {
            val a=(2*PI*i/100).toFloat()
            val ripple=1f+.055f*sin(a*5+t)+.035f*cos(a*3-t*1.5f)
            val x=c.x+cos(a)*r*ripple;val y=c.y+sin(a)*r*ripple
            if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
        }
        path.close()
        drawPath(path,Brush.radialGradient(listOf(Color(0xFFFAFFD8),Lime,Color(0xFF64A233),Color(0xFF1C3525)),Offset(c.x-r*.4f,c.y-r*.55f),r*1.7f))
        drawPath(path,White.copy(alpha=.2f),style=Stroke(1.1.dp.toPx()))
        drawArc(White.copy(alpha=.7f),195f+t*10,95f,false,topLeft=Offset(c.x-r*.76f,c.y-r*.8f),size=androidx.compose.ui.geometry.Size(r*1.5f,r*1.55f),style=Stroke(1.dp.toPx(),cap=StrokeCap.Round))
        drawCircle(White.copy(alpha=.65f),r*.16f,Offset(c.x-r*.32f,c.y-r*.42f))
    }
}

@Composable
private fun BottomTabs(model: HungiiModel) {
    Row(Modifier.fillMaxWidth().background(Charcoal).navigationBarsPadding().padding(horizontal=24.dp,vertical=9.dp).height(49.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        listOf(Triple(Screen.Home,"Home",Icons.Outlined.Home),Triple(Screen.Discover,"Discover",Icons.Outlined.Style),Triple(Screen.Saved,"Saved",Icons.Outlined.FavoriteBorder),Triple(Screen.Day,"My day",Icons.Outlined.BarChart)).forEach { (screen,label,icon) ->
            val active=model.screen==screen
            Column(Modifier.width(70.dp).fillMaxHeight().clip(RoundedCornerShape(15.dp)).clickable {model.screen=if(screen==Screen.Discover&&model.finalists.size>=3)Screen.Finalists else screen},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                Icon(icon,label,tint=if(active)Lime else Muted,modifier=Modifier.size(22.dp))
                Text(label,color=if(active)Lime else Muted,fontSize=10.sp,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MacroStats(n: Nutrition?) {
    Row(Modifier.fillMaxWidth().border(1.dp,Line,RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        listOf(Triple((n?.calories?.label ?: "—"),"KCAL",White),Triple((n?.protein?.label ?: "—"),"PROTEIN · G",Lime),Triple((n?.carbs?.label ?: "—"),"CARBS · G",Cyan),Triple((n?.fat?.label ?: "—"),"FAT · G",Coral)).forEach { (value,label,color) ->
            Column {DisplayText(value,25,color);Text(label,color=Muted,fontSize=7.sp,lineHeight=10.sp,letterSpacing=.4.sp)}
        }
    }
}
@Composable
private fun MetricTile(label: String,value: String,note: String,color: Color,modifier: Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Surface).padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {Eyebrow(label,Muted);DisplayText(value,if(value.length>7)34 else 42,color);Text(note,color=Muted,fontSize=10.sp,lineHeight=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
}
@Composable
private fun FocusHeader(label: String,onBack: ()->Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {IconButton(onClick=onBack,modifier=Modifier.size(40.dp).border(1.dp,Line,CircleShape)) {Icon(Icons.Outlined.ArrowBack,"Back",tint=White,modifier=Modifier.size(20.dp))};Eyebrow(label,Muted);Spacer(Modifier.weight(1f));Badge("HUNGII",Muted,Surface)}
}
@Composable
private fun DisplayText(text: String,size: Int,color: Color=White,maxLines: Int=Int.MAX_VALUE) {Text(text,color=color,fontFamily=Display,fontSize=size.sp,lineHeight=(size*.97f).sp,fontWeight=FontWeight.Bold,letterSpacing=(-.4).sp,maxLines=maxLines,overflow=TextOverflow.Ellipsis)}
@Composable
private fun Eyebrow(text: String,color: Color) {Text(text,color=color,fontSize=9.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)}
@Composable
private fun Badge(text: String,color: Color,bg: Color,modifier: Modifier=Modifier) {Box(modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal=9.dp,vertical=6.dp)) {Text(text,color=color,fontSize=8.sp,lineHeight=11.sp,fontWeight=FontWeight.Bold,letterSpacing=.5.sp)}}
@Composable
private fun SmallChip(text: String,selected: Boolean,onClick: ()->Unit) {Box(Modifier.clip(CircleShape).background(if(selected)Lime else Surface).border(1.dp,if(selected)Lime else Line,CircleShape).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=10.dp)) {Text(text,color=if(selected)Charcoal else Muted,fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)}}
@Composable
private fun LimeButton(text: String,icon: ImageVector,enabled: Boolean=true,onClick: ()->Unit) {Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().height(57.dp),shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Charcoal),contentPadding=PaddingValues(horizontal=19.dp)) {Text(text,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));Icon(icon,null,modifier=Modifier.size(21.dp))}}
@Composable
private fun OutlineButton(text: String,onClick: ()->Unit) {OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().height(53.dp),shape=RoundedCornerShape(17.dp),border=BorderStroke(1.dp,Line)) {Text(text,color=White,fontSize=14.sp)}}
@Composable
private fun RoundAction(icon: ImageVector,label: String,bg: Color,tint: Color,onClick: ()->Unit,size: Int=53) {IconButton(onClick=onClick,modifier=Modifier.size(size.dp).background(bg,CircleShape)) {Icon(icon,label,tint=tint,modifier=Modifier.size(if(size>=60)27.dp else 22.dp))}}
@Composable
private fun TradeLine(icon: ImageVector,color: Color,text: String,maxLines: Int=2) {Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Top) {Icon(icon,null,tint=color,modifier=Modifier.size(15.dp));Text(text,color=Muted,fontSize=11.sp,lineHeight=17.sp,maxLines=maxLines,overflow=TextOverflow.Ellipsis)}}
@Composable
private fun BillLine(label: String,value: String,color: Color=White) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(label,color=Muted,fontSize=13.sp);Text(value,color=color,fontSize=14.sp,fontWeight=FontWeight.Medium)}}
@Composable
private fun Receipt(text: String,onUndo: ()->Unit) {Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Raised).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {Text(text,color=Muted,fontSize=11.sp,lineHeight=17.sp,modifier=Modifier.weight(1f));TextButton(onClick=onUndo) {Text("Undo",color=Lime,fontSize=11.sp)}}}

@Composable
private fun GoalsDialog(model: HungiiModel,onClose: ()->Unit) {
    var name by remember {mutableStateOf(model.displayName)}
    var cal by remember {mutableStateOf(model.calorieGoal.toString())};var protein by remember {mutableStateOf(model.proteinGoal.toString())}
    var carbs by remember {mutableStateOf(model.carbGoal.toString())};var fat by remember {mutableStateOf(model.fatGoal.toString())}
    var allowance by remember {mutableStateOf(model.allowance.toString())};var opportunities by remember {mutableStateOf(model.opportunities.toString())}
    var eatenCal by remember {mutableStateOf(model.intake.calories.low.toString())};var eatenP by remember {mutableStateOf(model.intake.protein.low.toString())}
    var eatenC by remember {mutableStateOf(model.intake.carbs.low.toString())};var eatenF by remember {mutableStateOf(model.intake.fat.low.toString())}
    var spent by remember {mutableStateOf(model.spent.toString())}
    val valid=listOf(cal,protein,carbs,fat,allowance).all { (it.toIntOrNull()?:0)>0 } && (opportunities.toIntOrNull()?:-1) in 0..8 && listOf(eatenCal,eatenP,eatenC,eatenF,spent).all { (it.toIntOrNull()?:-1)>=0 }
    AlertDialog(onDismissRequest=onClose,containerColor=Surface,title={DisplayText("YOUR DAY. YOUR RULES.",31)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name,onValueChange={name=it.take(60)},label={Text("Your name (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            listOf(Triple("Daily calories",cal,{v:String->cal=v}),Triple("Protein goal (g)",protein,{v:String->protein=v}),Triple("Carbs goal (g)",carbs,{v:String->carbs=v}),Triple("Fat goal (g)",fat,{v:String->fat=v}),Triple("Food allowance (₹)",allowance,{v:String->allowance=v}),Triple("Opportunities left",opportunities,{v:String->opportunities=v}),Triple("Calories eaten today",eatenCal,{v:String->eatenCal=v}),Triple("Protein eaten (g)",eatenP,{v:String->eatenP=v}),Triple("Carbs eaten (g)",eatenC,{v:String->eatenC=v}),Triple("Fat eaten (g)",eatenF,{v:String->eatenF=v}),Triple("Money spent today (₹)",spent,{v:String->spent=v})).forEach { (label,value,update) ->
                OutlinedTextField(value,onValueChange={v->update(v.filter {it.isDigit()})},label={Text(label,fontSize=12.sp)},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            }
            Text("Enter your actual daily totals. Swiggy orders do not automatically become consumed nutrition.",color=Muted,fontSize=11.sp,lineHeight=18.sp)
        }
    },confirmButton={TextButton(enabled=valid,onClick={model.invalidateCheckInUndo();model.displayName=name;model.calorieGoal=cal.toInt();model.proteinGoal=protein.toInt();model.carbGoal=carbs.toInt();model.fatGoal=fat.toInt();model.allowance=allowance.toInt();model.opportunities=opportunities.toInt();model.spent=spent.toInt();model.intake=Nutrition(Span(eatenCal.toInt(),eatenCal.toInt()),Span(eatenP.toInt(),eatenP.toInt()),Span(eatenC.toInt(),eatenC.toInt()),Span(eatenF.toInt(),eatenF.toInt()));onClose()}) {Text("Update my day",color=if(valid)Lime else Muted)}},dismissButton={TextButton(onClick=onClose) {Text("Cancel",color=Muted)}})
}


private fun rupees(value: Double) = if(value==value.toInt().toDouble()) "₹${value.toInt()}" else "₹"+"%.2f".format(java.util.Locale.ROOT,value)

@Composable
private fun MealImage(meal: Meal,modifier: Modifier=Modifier) {
    Box(modifier.background(Raised),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Restaurant,null,tint=Muted,modifier=Modifier.size(36.dp))
        if(meal.imageUrl!=null) AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(meal.imageUrl).diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED).build(),contentDescription=meal.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}

@Composable
private fun ConnectionStrip(model: HungiiModel) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable {model.accountOpen=true}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(if(BuildConfig.LOCAL_DEMO) "SYNTHETIC LOCAL MCP" else if(model.connected) "Powered by Swiggy · ${model.environment}" else "CONNECT SWIGGY",color=if(model.connected)Lime else Muted,fontSize=10.sp,fontWeight=FontWeight.Bold)
        Text(if(model.connected&&model.addressId!=null) "Change address →" else "Accounts →",color=Lime,fontSize=10.sp)
    }
}

@Composable
private fun AccountDialog(model: HungiiModel,onClose: ()->Unit) {
    val context=LocalContext.current
    var consent by remember {mutableStateOf(false)}
    fun browse(url: String) {try {CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url))} catch(_: Exception) {model.connectionMessage="No browser is available. Install a browser to connect."}}
    AlertDialog(onDismissRequest=onClose,containerColor=Surface,title={DisplayText("YOUR ACCOUNTS.",34)},text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(BuildConfig.LOCAL_DEMO) "This local protocol demo uses fictional meals and an address. No Swiggy account, payment or order is involved." else "Your device tracker is encrypted. Cloud sync is optional. Swiggy supplies meals for the address you choose after separate authorization.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            Text("PRIVACY · ${model.privacyVersion}\nWorkOS handles email sign-in; Supabase hosts your account data in Mumbai. On-device voice only, with typed fallback. No audio recording, ads or training on your food data. Cloud totals expire after 90 days without updates; saved meal shortcuts after 30 days. Connection credentials expire within five days. Location and selected address are used for your requested meal search. No order is placed in Hungii.",color=Muted,fontSize=11.sp,lineHeight=18.sp)
            if(!model.configured) Text("The live connection is not available in this build yet. You can keep using your offline tracker while account setup is completed.",color=Coral,fontSize=13.sp,lineHeight=20.sp)
            if(!model.signedIn) LimeButton(if(BuildConfig.WORKOS_AUTH_READY) "Continue with email" else "Email sign-in · setup pending",Icons.Outlined.Login,enabled=model.configured&&BuildConfig.WORKOS_AUTH_READY&&!model.loading) {
                try {browse(model.signInUrl())} catch(e: ApiFailure) {model.connectionMessage=e.message}
            } else {
                Text(if(BuildConfig.LOCAL_DEMO) "Local demo session" else "Signed in to Hungii",color=Lime,fontSize=13.sp)
                if(!BuildConfig.LOCAL_DEMO) {
                Text("Hungii profile · "+if(model.cloudSyncEnabled) "Cloud sync enabled" else "Device only",color=Lime,fontSize=12.sp)
                if(model.cloudSyncMessage.isNotBlank())Text(model.cloudSyncMessage,color=Muted,fontSize=12.sp)
                if(model.cloudConflict) {
                    TextButton(onClick={model.useCloudCopy()}) {Text("Use cloud profile",color=Lime)}
                    TextButton(onClick={model.keepLocalCopy()}) {Text("Keep this device's profile",color=Coral)}
                }
                if(model.cloudSyncEnabled) TextButton(onClick={model.pauseCloudSync()}) {Text("Stop cloud sync on this device",color=Muted)}
                TextButton(onClick={model.restoreProfile()}) {Text("Restore cloud profile",color=Lime)}
                TextButton(onClick={model.privacyAction="state_save"}) {Text(if(model.cloudSyncEnabled) "Sync profile now" else "Enable profile & preference sync",color=Lime)}
                TextButton(onClick={model.privacyAction="delete_cloud_tracker"}) {Text("Erase cloud profile & tracker",color=Coral)}
                }
                if(!model.connected) {
                    Row(verticalAlignment=Alignment.Top) {
                        Checkbox(consent,onCheckedChange={consent=it},colors=CheckboxDefaults.colors(checkedColor=Lime,checkmarkColor=Charcoal))
                        Text("Allow Hungii to encrypt and retain my Swiggy token, chosen address identifier and Food session for up to five days, to perform my requested meal searches. Disconnect to erase them. Notice ${model.privacyVersion}.",color=Muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(top=10.dp))
                    }
                    LimeButton(if(BuildConfig.LOCAL_DEMO) "Start synthetic MCP demo" else "Connect Swiggy",Icons.Outlined.Link,enabled=consent&&!model.loading) {model.connect(::browse)}
                    if(!BuildConfig.LOCAL_DEMO) TextButton(onClick={model.privacyAction="disconnect"}) {Text("Erase previous connection before reconnecting",color=Muted,fontSize=11.sp)}
                } else {
                    Text(if(BuildConfig.LOCAL_DEMO) "Local demo connected" else "Swiggy connected · ${model.environment}",color=Lime,fontSize=12.sp)
                    TextButton(onClick={model.addressList()}) {Text("Choose delivery address",color=Lime)}
                    model.addresses.forEach {address ->
                        OutlinedButton(onClick={model.selectAddress(address)},modifier=Modifier.fillMaxWidth()) {
                            Column {Text(address.label,color=White,fontWeight=FontWeight.Bold);Text(address.addressLine,color=Muted,fontSize=12.sp,lineHeight=18.sp,maxLines=3)}
                        }
                    }
                    Row {
                        if(model.addressPage>1) TextButton(onClick={model.addressList(model.addressPage-1)}) {Text("Previous",color=Lime)}
                        if(model.moreAddresses) TextButton(onClick={model.addressList(model.addressPage+1)}) {Text("More addresses",color=Lime)}
                    }
                    TextButton(onClick={model.privacyAction="disconnect"}) {Text(if(BuildConfig.LOCAL_DEMO) "Disconnect demo" else "Disconnect Swiggy",color=Coral)}
                }
                TextButton(onClick={model.refresh()}) {Text("Refresh connection",color=Lime)}
                if(!BuildConfig.LOCAL_DEMO) TextButton(onClick={model.privacyAction="delete_account"}) {Text("Delete Hungii account",color=Coral)}
                TextButton(onClick={model.signOut(::browse)}) {Text("Sign out of Hungii",color=Muted)}
            }
            TextButton(onClick={model.privacyAction="erase_local"}) {Text("Erase device tracker & saved meals",color=Coral)}
            if(model.loading) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Lime)
            if(model.connectionMessage.isNotBlank()) Text(model.connectionMessage,color=Coral,fontSize=12.sp,lineHeight=18.sp)
        }
    },confirmButton={TextButton(onClick=onClose) {Text("Done",color=Lime)}})
}
