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
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Undo
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal val Charcoal=Color(0xFF09090C)
internal val Surface=Color(0xFF171317)
internal val Raised=Color(0xFF221A20)
internal val Accent=Color(0xFFFF526F)
internal val Crimson=Color(0xFFB91D40)
internal val AccentWash=Color(0xFF3B1825)
internal val White=Color(0xFFFFF2F5)
internal val Muted=Color(0xFFC0ADB5)
internal val Line=Color(0xFF41303A)
internal val SoftCrimson=Color(0xFFFF8A9D)
internal val Rose=Color(0xFFEAB7C8)
internal val Display=FontFamily.SansSerif

data class VoiceState(val listening: Boolean=false, val level: Float=0f)

class MainActivity: ComponentActivity() {
    internal val model: HungiiModel by viewModels()
    internal val voice=mutableStateOf(VoiceState())
    private var recognizer: SpeechRecognizer?=null
    private var onWords: (String)->Unit={}
    internal val microphonePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) listen() else Toast.makeText(this,"You can type your check-in instead.",Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(9,9,12)))
        intent.data?.let(model::handleCallback)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Accent,onPrimary=Charcoal,background=Charcoal,surface=Surface,onSurface=White,onBackground=White,onSurfaceVariant=Muted,surfaceVariant=Raised,secondary=Rose,onSecondary=Charcoal,primaryContainer=AccentWash,onPrimaryContainer=White,secondaryContainer=Raised,onSecondaryContainer=Rose,surfaceTint=Accent,error=SoftCrimson,onError=Charcoal,outline=Line)) {
                onWords={ model.sendAssistantMessage(it) }
                HungiiApp(model,voice.value,::requestVoice)
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); intent.data?.let(model::handleCallback) }
    internal fun requestVoice() {
        if(voice.value.listening) { recognizer?.stopListening(); voice.value=VoiceState(); return }
        if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            Toast.makeText(this,"On-device voice unavailable. Type your check-in; audio stays on your phone.",Toast.LENGTH_LONG).show(); return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) listen()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    internal fun listen() {
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
internal fun HungiiApp(model: HungiiModel,voice: VoiceState,onVoice: ()->Unit) {
    var goalsOpen by remember { mutableStateOf(false) }
    var detailMeal by remember { mutableStateOf<Meal?>(null) }
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    val reduceMotion=remember { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    if(model.initializing || model.accountLoading) {
        Box(Modifier.fillMaxSize().background(Charcoal),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                ThinkingDots()
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
    val focused=model.screen in listOf(Screen.Finalists,Screen.Draw,Screen.Winner,Screen.Review,Screen.Payment,Screen.Order)
    BackHandler(model.screen!=Screen.Home) { model.goBack() }
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
        bottomBar={ if(!focused) ModernTabs(model) }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
            if(BuildConfig.LOCAL_DEMO) Text("Simulator · no real orders or money",color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().background(Surface).padding(8.dp),textAlign=TextAlign.Center)
            Box(Modifier.weight(1f).background(Brush.verticalGradient(listOf(AccentWash.copy(alpha=.24f), Charcoal), endY=1100f))) {
            AnimatedContent(targetState=model.screen,transitionSpec={fadeIn(tween(if(reduceMotion)0 else 220))+slideInVertically(tween(if(reduceMotion)0 else 220)){it/30} togetherWith fadeOut(tween(if(reduceMotion)0 else 120))},label="screen") { screen ->
            when(screen) {
                Screen.Home -> ModernHomeScreen(model,{goalsOpen=true})
                Screen.Assistant -> AssistantScreen(model,voice,onVoice,reduceMotion)
                Screen.Discover -> DiscoverScreen(model,{detailMeal=it})
                Screen.Finalists -> FinalistsScreen(model)
                Screen.Draw -> DrawScreen(model,reduceMotion)
                Screen.Winner -> WinnerScreen(model,{detailMeal=it})
                Screen.Review -> CheckoutScreen(model)
                Screen.Payment -> PaymentScreen(model)
                Screen.Order -> OrderScreen(model)
                Screen.Saved -> SavedScreen(model)
                Screen.Day -> DayScreen(model,{goalsOpen=true})
            }
            }
            }
        }
    }
    if(model.assistantConsentPending) AlertDialog(onDismissRequest={model.assistantConsentPending=false},containerColor=Surface,title={Text("Enable the cloud assistant?")},text={Text("Your typed or transcribed messages and selected tracker values are sent through this demo server to Groq's free cloud inference, processed outside India. No Swiggy login, real orders or payment credentials are shared. You can keep using manual filters without it.")},confirmButton={TextButton(onClick=model::acceptAssistant){Text("Enable assistant")}},dismissButton={TextButton(onClick={model.assistantConsentPending=false}){Text("Use manual filters")}})
    model.assistantAction?.let { action -> AlertDialog(onDismissRequest={model.assistantAction=null},containerColor=Surface,title={Text("Apply this change?")},text={Text(action.first.replace('_',' ')+if(action.second.isBlank()) "" else " → ${action.second}")},confirmButton={TextButton(onClick=model::applyAssistantAction){Text("Apply")}},dismissButton={TextButton(onClick={model.assistantAction=null}){Text("Cancel")}}) }
    if(model.replaceCartPending) AlertDialog(onDismissRequest={model.replaceCartPending=false;model.goBack()},containerColor=Surface,title={Text("Replace your basket?")},text={Text("This pick comes from another restaurant. Replace the existing basket to continue.")},confirmButton={TextButton(onClick={model.replaceCartPending=false;model.review(true)}){Text("Replace cart")}},dismissButton={TextButton(onClick={model.replaceCartPending=false;model.goBack()}){Text("Keep cart")}})
    if(model.accountOpen) AccountDialog(model) { model.accountOpen=false }
    model.pendingSave?.let { meal ->
        AlertDialog(onDismissRequest={model.pendingSave=null},containerColor=Surface,title={Text("Remember this meal?",color=White)},text={Text("Allow Hungii to keep ${meal.name}, its name, restaurant and menu identifiers encrypted on this device for up to 30 days. Prices and photos are not saved. Delete saved meals and withdraw permission from Saved. Notice ${model.privacyVersion}.",color=Muted)},confirmButton={TextButton(onClick={model.acceptSaving()}){Text("Allow & save",color=Accent)}},dismissButton={TextButton(onClick={model.pendingSave=null}){Text("Cancel",color=Muted)}})
    }
    model.privacyAction?.let {action ->
        val title=when(action){"state_save"->"Sync profile & preferences?";"disconnect"->"Disconnect Swiggy?";"delete_account"->"Delete your Hungii account?";"delete_cloud_tracker"->"Erase your cloud tracker?";else->"Erase data on this device?"}
        val explanation=when(action){"state_save"->"Allow automatic sync of your entered profile, goals, food allowance, daily totals and meal filters to your encrypted Hungii account in Mumbai. Restore them on another device after sign-in. Cloud data expires after 90 days without updates. You can stop syncing or erase the cloud copy from Accounts. Notice ${model.privacyVersion}.";"disconnect"->"Erase Hungii's Swiggy connection and saved meals. Checkout recovery stays in your account. This does not cancel orders. Remote revocation is reported separately.";"delete_account"->"Erase your cloud tracker, checkout recovery, Swiggy connection and this account's device data, then delete your WorkOS login. This does not cancel or refund Swiggy orders. Check unresolved orders in Swiggy first. If provider deletion fails, you can retry. An account hash is kept for 24 hours to prevent requests from restoring erased data. This cannot be undone.";"delete_cloud_tracker"->"Erase your cloud profile, preferences and tracker, and stop syncing this device. Your device copy stays available.";else->"Erase your entered totals and saved meals on this device. Your cloud data is managed separately."}
        AlertDialog(onDismissRequest={model.privacyAction=null},containerColor=Surface,title={Text(title,color=White)},text={Text(explanation,color=Muted)},confirmButton={TextButton(onClick={when(action){"state_save"->{model.privacyAction=null;model.syncTracker()};"disconnect"->{model.privacyAction=null;model.disconnect()};else->model.performPrivacyAction()}}){Text(if(action=="state_save")"Allow & sync" else "Confirm",color=if(action=="state_save")Accent else SoftCrimson)}},dismissButton={TextButton(onClick={model.privacyAction=null}){Text("Cancel",color=Muted)}})
    }
    if(goalsOpen) GoalsDialog(model) { goalsOpen=false }
    detailMeal?.let { meal ->
        ModalBottomSheet(onDismissRequest={detailMeal=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Surface) {
            Column(Modifier.heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Eyebrow("THE WHOLE PICTURE",Accent)
                DisplayText(meal.name,28)
                MacroStats(meal.nutrition)
                AllowanceContext(meal,model)
                Text(meal.benefit,color=White,fontSize=15.sp)
                Text(meal.compromise,color=SoftCrimson,fontSize=15.sp)
                Text("${meal.priceLabel} is the menu price. Delivery, taxes and offers can change the final cart bill. "+if(meal.nutrition!=null) "Nutrition ranges are Hungii-owned synthetic estimates; photos are representative." else "Nutrition is not published for this item in the connected menu.",color=Muted,lineHeight=23.sp)
                PrimaryButton("Got it",Icons.Outlined.Check) { detailMeal=null }
            }
        }
    }
}

@Composable
internal fun AccountEntry(model:HungiiModel) {
    val context=LocalContext.current
    Column(Modifier.fillMaxSize().background(Charcoal).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("hungii",color=Accent,fontSize=32.sp,fontWeight=FontWeight.ExtraBold)
        DisplayText("Your day.\nYour next meal.",36)
        Text("Start with your food allowance, goals and cravings. Keep your plan on this device, and sign in whenever you want to sync.",color=Muted,fontSize=16.sp,lineHeight=24.sp)
        Column(Modifier.fillMaxWidth().background(Surface,RoundedCornerShape(24.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Your starting plan",color=Muted,fontSize=14.sp)
            DisplayText("₹${model.allowance}",40)
            Text("${model.opportunities} meals · ${model.calorieGoal} kcal target",color=White,fontSize=16.sp)
            Text("Adjust these starting values to suit your day.",color=Muted,fontSize=14.sp,lineHeight=20.sp)
        }
        PrimaryButton("Start planning",Icons.AutoMirrored.Outlined.ArrowForward) {model.useOffline()}
        OutlinedButton(onClick={
            try {CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(model.signInUrl()))} catch(e:ApiFailure){model.connectionMessage=e.message} catch(_:Exception){model.connectionMessage="A browser is needed to sign in."}
        },enabled=BuildConfig.WORKOS_AUTH_READY&&!model.loading,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),border=BorderStroke(1.dp,Line)) {
            Text("Sign in to sync",color=if(BuildConfig.WORKOS_AUTH_READY)White else Muted,fontSize=16.sp)
        }
        Text("Track your day without an account. Swiggy ordering will become available after developer access is approved.",color=Muted,fontSize=14.sp,lineHeight=20.sp)
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=14.sp,lineHeight=20.sp)
    }
}

@Composable
internal fun CloudEntry(model:HungiiModel) {
    Column(Modifier.fillMaxSize().background(Charcoal).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(36.dp))
        Text("hungii.",color=Accent,fontSize=42.sp,fontWeight=FontWeight.Black)
        Eyebrow("HUNGII ACCOUNT CONNECTED",Accent)
        DisplayText("Your profile.\nYour rules.",34)
        Text("Allow encrypted cloud sync for your name, nutrition goals, food allowance, entered daily totals and meal filters. Changes save automatically and restore when you sign in on another device.",color=Muted,fontSize=16.sp,lineHeight=25.sp)
        Text("Stored in Hungii's Mumbai database. Cloud data expires after 90 days without updates. Erase the cloud copy or stop syncing in Accounts. Saved Swiggy meal shortcuts stay on this device. Privacy notice ${model.privacyVersion}.",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        PrimaryButton("Allow profile & preference sync",Icons.Outlined.CloudUpload,enabled=!model.loading) {model.syncTracker()}
        if(model.cloudConflict) {
            Text(model.cloudSyncMessage,color=SoftCrimson,fontSize=13.sp)
            TextButton(onClick={model.useCloudCopy()}) {Text("Use my cloud profile",color=Accent)}
            TextButton(onClick={model.keepLocalCopy()}) {Text("Sync this device's profile instead",color=SoftCrimson)}
        }
        TextButton(onClick={model.keepDeviceOnly()}) {Text("Continue without cloud sync",color=Muted)}
        if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp)
    }
}

@Composable
internal fun BrandHeader(onDay: ()->Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("hungii",fontSize=29.sp,fontWeight=FontWeight.Black,letterSpacing=(-1.5).sp,color=White)
            Text(".",fontSize=36.sp,fontWeight=FontWeight.Black,color=Accent)
            Spacer(Modifier.width(10.dp)); Badge("HUNGII",Muted,Surface)
        }
        IconButton(onClick=onDay) { Icon(Icons.Outlined.Tune,"Edit your day",tint=White,modifier=Modifier.size(23.dp)) }
    }
}

@Composable
internal fun FinalistsScreen(model: HungiiModel) {
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FocusHeader("YOUR FINALISTS") {model.goBack()}
        DisplayText(if(model.finalists.size==3) "Three good choices." else "Your shortlist.",30)
        Text("All of these got a yes. The last choice can be the easy one.",color=Muted,lineHeight=23.sp)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            model.finalists.toList().forEachIndexed { i,m ->
                Row(Modifier.fillMaxWidth().heightIn(min=144.dp).clip(RoundedCornerShape(24.dp)).background(Surface),verticalAlignment=Alignment.CenterVertically) {
                    MealImage(m,Modifier.size(88.dp).padding(start=12.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f).padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Eyebrow("FINALIST 0${i+1}",Accent)
                        Text(m.name,color=White,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold)
                        Text("${m.priceLabel} · ${m.etaLabel}",color=Muted,fontSize=12.sp,lineHeight=16.sp)
                    }
                    IconButton(onClick={model.remove(m)},modifier=Modifier.size(48.dp)) {Icon(Icons.Outlined.Close,"Remove ${m.name}",tint=Muted,modifier=Modifier.size(16.dp))}
                }
            }
        }
        if(model.finalists.size==3) PrimaryButton("Turn over & shuffle",Icons.Outlined.Shuffle) {model.startDraw()} else PrimaryButton("Keep ${3-model.finalists.size} more meal${if(model.finalists.size==2) "" else "s"}",Icons.Outlined.Add) {model.screen=Screen.Discover}
        Text("Same meals. Equal chances. Nothing ordered.",color=Muted,fontSize=12.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=24.dp))
    }
}

@Composable
internal fun DrawScreen(model: HungiiModel,reduceMotion: Boolean) {
    var backsUp by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(model.shuffling) {
        if (model.shuffling) {
            backsUp = true
            progress.snapTo(0f)
            if (!reduceMotion) {
                delay(450)
                progress.animateTo(1f, tween(1800, easing = LinearEasing))
            } else {
                progress.snapTo(1f)
            }
            model.shuffling = false
            model.canPick = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else backsUp = true
    }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        FocusHeader("THE LUCKY DRAW") {model.goBack()}
        Spacer(Modifier.height(28.dp))
        Eyebrow("ROUND TWO",Accent)
        DisplayText(if(model.shuffling) "Let luck do its thing." else if(model.pickedIndex!=null) "That’s your pick." else "Go with your gut.",36,maxLines=2,minLines=2)
        Spacer(Modifier.height(14.dp))
        Text(if(model.shuffling) "Turning your three maybes into one next meal." else "Tap a card. Every meal already earned its place.",color=Muted,lineHeight=23.sp,minLines=2,maxLines=2)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            val cardWidth=(maxWidth-40.dp)/3
            val travel=cardWidth+12.dp
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                model.drawOrder.forEachIndexed { i,m ->
                    val pose=shufflePose(if(reduceMotion)1f else progress.value,i,travel.value)
                    val picked=model.pickedIndex==i
                    val lift by animateFloatAsState(if(picked)-12f else 0f,tween(if(reduceMotion)0 else 320),label="picked lift")
                    val opacity by animateFloatAsState(if(model.pickedIndex!=null&&!picked).25f else 1f,tween(if(reduceMotion)0 else 320),label="picked fade")
                    val turned by animateFloatAsState(if(picked||!backsUp)0f else 180f,tween(if(reduceMotion)0 else 450),label="flip")
                    Box(Modifier.width(cardWidth).height(cardWidth*1.62f).graphicsLayer {
                        translationX=pose.x.dp.toPx();translationY=(pose.y+lift).dp.toPx()
                        rotationZ=pose.tilt
                        alpha=opacity
                        if(Build.VERSION.SDK_INT>=31) renderEffect=if(pose.blur>.05f) BlurEffect(pose.blur*density,pose.blur*density,TileMode.Decal) else null
                        rotationY=turned;cameraDistance=16*density
                    }.clip(RoundedCornerShape(18.dp)).background(if(turned<90) Surface else Accent)
                        .border(1.dp,if(picked)Accent else Accent.copy(alpha=.5f),RoundedCornerShape(18.dp))
                        .clickable(enabled=model.canPick&&!model.shuffling&&model.pickedIndex==null) {model.pick(i)}
                        .semantics {contentDescription=if(picked) "Revealed ${m.name}" else "Pick card ${i+1}"},contentAlignment=Alignment.Center) {
                        if(turned<90) Column(Modifier.fillMaxSize()) {
                            MealImage(m,Modifier.weight(1f).fillMaxWidth())
                            Text(m.name,color=White,fontSize=12.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp),maxLines=3)
                        } else Column(Modifier.fillMaxSize().graphicsLayer {rotationY=180f}.padding(10.dp).border(1.dp,Charcoal.copy(alpha=.25f),RoundedCornerShape(10.dp)),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text("h.",fontSize=54.sp,fontWeight=FontWeight.Black,letterSpacing=(-3).sp,color=Charcoal)
                            Spacer(Modifier.height(15.dp));Text("FUEL YOUR DAY",fontSize=10.sp,lineHeight=14.sp,textAlign=TextAlign.Center,color=Charcoal,letterSpacing=.5.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom=20.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Icon(if(model.shuffling)Icons.Outlined.Shuffle else Icons.Outlined.TouchApp,null,tint=Accent,modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp));Text(if(model.shuffling)"Mixing…" else "Pick any card. You’re in control.",color=White,fontSize=13.sp)
        }
        TextButton(onClick={model.shuffling=false;model.screen=Screen.Finalists},modifier=Modifier.fillMaxWidth().padding(bottom=16.dp)) {Text("View my finalists",color=Muted,fontSize=12.sp)}
    }
}

@Composable
internal fun WinnerScreen(model: HungiiModel,onDetails: (Meal)->Unit) {
    val meal=model.winner ?: return
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FocusHeader("YOUR LUCKY PICK") {model.goBack()}
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.AutoAwesome,null,tint=Accent,modifier=Modifier.size(22.dp));Spacer(Modifier.width(10.dp));DisplayText("Your lucky pick.",32)}
        Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(26.dp))) {
            MealImage(meal,Modifier.fillMaxSize())
            Badge(meal.badge,Charcoal,Accent,Modifier.align(Alignment.TopStart).padding(15.dp))
        }
        DisplayText(meal.name,28)
        Text(meal.priceLabel+" menu price · fees extra",color=White,fontSize=18.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(meal.restaurant,color=Muted,fontSize=12.sp);Text(meal.etaLabel,color=Muted,fontSize=12.sp)}
        MacroStats(meal.nutrition)
        AllowanceContext(meal,model)
        Text(if(meal.nutrition!=null) "Estimated nutrition · synthetic demo" else "Nutrition not published · calories and macros unknown",color=Muted,fontSize=12.sp)
        TradeLine(Icons.Outlined.Add,Accent,meal.benefit)
        TradeLine(Icons.Outlined.Remove,SoftCrimson,meal.compromise,maxLines=3)
        TextButton(onClick={onDetails(meal)},modifier=Modifier.fillMaxWidth()) {Text("See what this leaves for later",color=Muted,fontSize=12.sp)}
        Text("The draw never changes a cart or places an order.",color=Muted,fontSize=12.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=20.dp))
    }
    Box(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal=22.dp,vertical=12.dp)) {
        PrimaryButton(if(BuildConfig.LOCAL_DEMO) "Add to mock cart" else "Review basket",Icons.AutoMirrored.Outlined.ArrowForward) {model.review()}
    }
    }
}

@Composable
internal fun ReviewScreen(model: HungiiModel) {
    val meal=model.winner ?: return
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FocusHeader("MEAL REVIEW") {model.goBack()}
            DisplayText("Your pick.\nYour Swiggy cart.",32)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MealImage(meal,Modifier.size(75.dp).clip(RoundedCornerShape(13.dp)))
                Column {Text(meal.name,color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Text(meal.priceLabel+" menu price",color=Muted,fontSize=12.sp)}
            }
            MacroStats(meal.nutrition)
            Text("Nutrition is unavailable for this item. Log your own known intake in My day.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
            Text("Choosing this meal has not changed your Swiggy cart or placed an order. Complete your basket and checkout in Swiggy.",color=Muted,lineHeight=22.sp)
            if(model.loading) ThinkingDots()
            if(model.connectionMessage.isNotBlank()) Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp)
            model.cart?.let {cart ->
                val matching=cart.optString("restaurantId")==meal.restaurantId
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                    Eyebrow("YOUR EXISTING SWIGGY CART",Muted)
                    Text(cart.optString("restaurant").takeIf {it!="null"} ?: "Empty cart",color=White)
                    val items=cart.optJSONArray("items")
                    if(items==null||items.length()==0) Text("Add your chosen meal in Swiggy.",color=Muted)
                    else for(i in 0 until items.length()) {val item=items.getJSONObject(i);Text("${item.optInt("quantity")} × ${item.optString("name")}",color=Muted,fontSize=12.sp)}
                    if(!matching&&items!=null&&items.length()>0) Text("This cart belongs to a different restaurant from your pick.",color=SoftCrimson,fontSize=12.sp)
                    if(!cart.isNull("payable")) {
                        BillLine("Food",rupees(cart.optDouble("itemTotal")))
                        BillLine("Delivery",rupees(cart.optDouble("deliveryCharge")))
                        BillLine("Taxes & charges",rupees(cart.optDouble("taxes")))
                        if(!cart.isNull("appliedCoupon")) BillLine("Applied: ${cart.optString("appliedCoupon")}","−"+rupees(cart.optDouble("couponDiscount")),Accent)
                        HorizontalDivider(color=Line)
                        BillLine("Current cart payable",rupees(cart.getDouble("payable")),Accent)
                    } else Text("Live total is unavailable until the connection is verified.",color=Muted,fontSize=12.sp)
                }
            }
            model.coupons?.optJSONArray("sections")?.let {sections ->
                for(i in 0 until sections.length()) {
                    val section=sections.getJSONObject(i);val offers=section.optJSONArray("coupons") ?: continue
                    for(j in 0 until minOf(offers.length(),5)) {
                        val offer=offers.getJSONObject(j)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(offer.optString("title","Offer"),color=Accent,fontWeight=FontWeight.Bold)
                            Text(offer.optString("description").ifBlank {offer.optString("subtitle")},color=Muted,fontSize=12.sp,lineHeight=18.sp)
                            Text("Eligibility depends on your current cart and payment method. This offer has not been applied.",color=Muted,fontSize=12.sp,lineHeight=16.sp)
                        }
                    }
                }
            }
            TextButton(onClick={model.review()}) {Text("Refresh cart & offers",color=Accent)}
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().background(Charcoal).padding(24.dp)) {
            PrimaryButton(if(BuildConfig.LOCAL_DEMO) "Demo · checkout unavailable" else "Continue in Swiggy",Icons.AutoMirrored.Outlined.OpenInNew,enabled=!BuildConfig.LOCAL_DEMO) {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.swiggy.com/")))}
        }
    }
}

@Composable
internal fun SavedScreen(model: HungiiModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FocusHeader("Saved meals") {model.screen=Screen.Discover}
        if(model.savedMeals.isEmpty()) {
            MealEmptyState(Icons.Outlined.Bookmarks,"Keep your favourites close","Tap the bookmark on a meal to save it here. We'll ask before keeping it on this device.","Explore meals",{model.screen=Screen.Discover})
        } else {
            DisplayText("Your go-to meals",28)
            Text("Search again to check today's availability and price.",color=Muted,fontSize=16.sp,lineHeight=24.sp)
            model.savedMeals.toList().forEach {meal ->
                Row(Modifier.fillMaxWidth().heightIn(min=144.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    MealImage(meal,Modifier.size(80.dp).clip(RoundedCornerShape(16.dp)))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(meal.name,color=White,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp)
                        Text(meal.restaurant,color=Muted,fontSize=14.sp,lineHeight=20.sp)
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            TextButton(onClick={model.query=meal.name;model.search()},modifier=Modifier.weight(1f)) {Text("Find again",color=Accent,fontSize=14.sp)}
                            IconButton(onClick={model.toggleSaved(meal)}) {Icon(Icons.Outlined.Bookmark,"Unsave ${meal.name}",tint=Muted)}
                        }
                    }
                }
            }
        }
        if(model.savedConsent) TextButton(onClick={model.forgetSaved()}) {Text("Delete saved meals & withdraw saving permission",color=SoftCrimson,fontSize=14.sp)}
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
internal fun DayScreen(model: HungiiModel,onEdit: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        BrandHeader(onEdit)
        DisplayText("No fixed schedule.\nStill on track.",30)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Raised,Surface)))
                .border(1.dp,Line.copy(alpha=.6f),RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp),
        ) {
            val consumed by animateFloatAsState((model.intake.calories.mid/model.calorieGoal).coerceIn(0f,1f),tween(650),label="calorie progress")
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val diameter=minOf(maxWidth,192.dp)
                Box(Modifier.size(diameter),contentAlignment=Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val width=8.dp.toPx()
                        val inset=width/2
                        drawCircle(Line,radius=(size.minDimension-width)/2,style=Stroke(width))
                        if(consumed>0f) drawArc(Brush.sweepGradient(listOf(Crimson,Accent,Rose,Crimson)),-90f,consumed*360,false,
                            topLeft=Offset(inset,inset),size=androidx.compose.ui.geometry.Size(size.width-width,size.height-width),style=Stroke(width,cap=StrokeCap.Round))
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        FittedMetric(model.caloriesLeft.label,38,Accent)
                        Text("kcal left",color=Muted,fontSize=13.sp,lineHeight=18.sp,textAlign=TextAlign.Center)
                    }
                }
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("${model.intake.calories.label} kcal consumed",color=White,fontSize=15.sp,lineHeight=21.sp,textAlign=TextAlign.Center)
                Text("${model.calorieGoal} daily target · ${model.opportunities} meals left",color=Muted,fontSize=13.sp,lineHeight=19.sp,textAlign=TextAlign.Center)
            }
        }
        listOf(Triple("PROTEIN",model.intake.protein,model.proteinGoal),Triple("CARBS",model.intake.carbs,model.carbGoal),Triple("FAT",model.intake.fat,model.fatGoal)).forEachIndexed {i,(label,n,goal)->
            val color=listOf(Accent,Rose,SoftCrimson)[i]
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(17.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(label,color=color,fontSize=12.sp,fontWeight=FontWeight.SemiBold);Text("${n.label} / ${goal}g eaten",modifier=Modifier.weight(1f),textAlign=TextAlign.End,color=White,fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)}
                LinearProgressIndicator(progress={ (n.mid/goal).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=color,trackColor=Line)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {MetricTile("ALLOWANCE LEFT","₹${model.moneyLeft}","₹${model.spent} spent / ₹${model.allowance}",Accent,Modifier.weight(1f));MetricTile("RESERVED FOOD",model.reservedCalories.label,"kcal not yet eaten",White,Modifier.weight(1f))}
        OutlineButton("Edit my targets & allowance") {onEdit()}
        Text("Your food log. Orders are not automatically counted as eaten. Nutrition totals contain only what you entered.",color=Muted,fontSize=12.sp,lineHeight=19.sp,modifier=Modifier.padding(bottom=20.dp))
    }
}

@Composable
internal fun MacroStats(n: Nutrition?) {
    val values = listOf(
        Triple(n?.calories?.label ?: "—", "Calories · kcal", White),
        Triple(n?.protein?.label ?: "—", "Protein · g", White),
        Triple(n?.carbs?.label ?: "—", "Carbs · g", White),
        Triple(n?.fat?.label ?: "—", "Fat · g", White),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        values.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (value, label, color) ->
                    Column(Modifier.weight(1f)) {
                        DisplayText(value, 20, color)
                        Text(label, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}
@Composable
internal fun MetricTile(label: String,value: String,note: String,color: Color,modifier: Modifier=Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Surface).padding(17.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {Eyebrow(label,Muted);FittedMetric(value,36,color);Text(note,color=Muted,fontSize=12.sp,lineHeight=18.sp)}
}
@Composable
internal fun FittedMetric(value: String, size: Int, color: Color) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.CenterStart) {
        val style=TextStyle(fontSize=size.sp,fontWeight=FontWeight.Bold,fontFamily=Display,letterSpacing=(-.4).sp)
        val measured=measurer.measure(value,style=style,softWrap=false).size.width
        val available=with(density){maxWidth.toPx()}
        val fitted=size*(available/measured.coerceAtLeast(1)).coerceAtMost(1f)
        Text(value,modifier=Modifier.fillMaxWidth(),color=color,fontFamily=Display,fontWeight=FontWeight.Bold,
            fontSize=fitted.sp,lineHeight=(fitted*1.2f).sp,letterSpacing=(-.4).sp,maxLines=1,textAlign=TextAlign.Center)
    }
}
@Composable
internal fun FocusHeader(label: String,onBack: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconButton(onClick=onBack,modifier=Modifier.size(48.dp).border(1.dp,Line,CircleShape)) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back",tint=White,modifier=Modifier.size(24.dp))}
        Text(label.lowercase().replaceFirstChar { it.titlecase() }, color=Muted, fontSize=14.sp)
    }
}
@Composable
internal fun DisplayText(text: String,size: Int,color: Color=White,maxLines: Int=Int.MAX_VALUE,modifier: Modifier=Modifier,minLines: Int=1) {Text(text,modifier=modifier,color=color,fontFamily=Display,fontSize=size.sp,lineHeight=(size*1.2f).sp,fontWeight=FontWeight.Bold,letterSpacing=(-.4).sp,maxLines=maxLines,minLines=minLines,overflow=TextOverflow.Ellipsis)}
@Composable
internal fun Eyebrow(text: String,color: Color) {Text(text,color=color,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium)}
@Composable
internal fun Badge(text: String,color: Color,bg: Color,modifier: Modifier=Modifier) {Box(modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal=10.dp,vertical=6.dp)) {Text(text,color=color,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)}}
@Composable
internal fun SmallChip(text: String,selected: Boolean,enabled: Boolean=true,onClick: ()->Unit) {
    val tint by animateColorAsState(if(selected) AccentWash else Surface,tween(220),label="chip surface")
    val ink by animateColorAsState(if(selected) Rose else Muted,tween(220),label="chip ink")
    FilterChip(selected=selected,onClick=onClick,enabled=enabled,modifier=Modifier.heightIn(min=48.dp),
        shape=RoundedCornerShape(24.dp),label={Text(text,fontSize=14.sp,lineHeight=20.sp)},
        colors=FilterChipDefaults.filterChipColors(containerColor=tint,labelColor=ink,selectedContainerColor=tint,selectedLabelColor=ink))
}
@Composable
internal fun PrimaryButton(text: String,icon: ImageVector,enabled: Boolean=true,onClick: ()->Unit) {
    val interactions=remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val top by animateColorAsState(if(!enabled) Raised else if(pressed) SoftCrimson else Accent,tween(180),label="button highlight")
    val bottom by animateColorAsState(if(!enabled) Surface else if(pressed) Accent else Color(0xFFE93D60),tween(180),label="button shade")
    val shape=RoundedCornerShape(28.dp)
    Button(onClick=onClick,enabled=enabled,interactionSource=interactions,
        modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).clip(shape).background(Brush.linearGradient(listOf(top,bottom))),
        shape=shape,colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=Charcoal,disabledContainerColor=Color.Transparent,disabledContentColor=Muted),
        contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
        Text(text,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
        Spacer(Modifier.width(12.dp));Icon(icon,null,modifier=Modifier.size(24.dp))
    }
}
@Composable
internal fun OutlineButton(text: String,onClick: ()->Unit) {OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(28.dp),border=BorderStroke(1.dp,Line),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {Text(text,color=White,fontSize=16.sp,lineHeight=22.sp)}}
@Composable
internal fun RoundAction(icon: ImageVector,label: String,bg: Color,tint: Color,onClick: ()->Unit,size: Int=53) {IconButton(onClick=onClick,modifier=Modifier.size(size.coerceAtLeast(48).dp).background(bg,CircleShape)) {Icon(icon,label,tint=tint,modifier=Modifier.size(if(size>=60)27.dp else 24.dp))}}
@Composable
internal fun TradeLine(icon: ImageVector,color: Color,text: String,maxLines: Int=2) {Row(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalAlignment=Alignment.Top) {Icon(icon,null,tint=color,modifier=Modifier.size(15.dp));Text(text,color=Muted,fontSize=14.sp,lineHeight=20.sp,maxLines=maxLines,overflow=TextOverflow.Ellipsis)}}
@Composable
internal fun BillLine(label: String,value: String,color: Color=White) {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(label,color=Muted,fontSize=13.sp);Text(value,color=color,fontSize=14.sp,fontWeight=FontWeight.Medium)}}
@Composable
internal fun Receipt(text: String,onUndo: ()->Unit) {Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Raised).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {Text(text,color=Muted,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.weight(1f));TextButton(onClick=onUndo) {Text("Undo",color=Accent,fontSize=12.sp)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalsDialog(model: HungiiModel,onClose: ()->Unit) {
    var name by remember {mutableStateOf(model.displayName)}
    var cal by remember {mutableStateOf(model.calorieGoal.toString())};var protein by remember {mutableStateOf(model.proteinGoal.toString())}
    var carbs by remember {mutableStateOf(model.carbGoal.toString())};var fat by remember {mutableStateOf(model.fatGoal.toString())}
    var allowance by remember {mutableStateOf(model.allowance.toString())};var opportunities by remember {mutableStateOf(model.opportunities.toString())}
    var eatenCal by remember {mutableStateOf(model.intake.calories.low.toString())};var eatenP by remember {mutableStateOf(model.intake.protein.low.toString())}
    var eatenC by remember {mutableStateOf(model.intake.carbs.low.toString())};var eatenF by remember {mutableStateOf(model.intake.fat.low.toString())}
    var spent by remember {mutableStateOf(model.spent.toString())}
    var totalsOpen by remember {mutableStateOf(false)}
    var nameOpen by remember {mutableStateOf(model.displayName.isNotBlank())}
    val valid=listOf(cal,protein,carbs,fat,allowance).all { (it.toIntOrNull()?:0)>0 } && (opportunities.toIntOrNull()?:-1) in 0..8 && listOf(eatenCal,eatenP,eatenC,eatenF,spent).all { (it.toIntOrNull()?:-1)>=0 }
    ModalBottomSheet(onDismissRequest=onClose, sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true), containerColor=Surface) {
        Column(Modifier.fillMaxWidth().heightIn(max=720.dp).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                DisplayText("Your day, your rules",28)
                Text("Goals & allowance",color=Muted,fontSize=16.sp)
                if(nameOpen) FilledInput(name,{name=it.take(60)},"Your name (optional)",Modifier.fillMaxWidth(),showLabel=true)
                else TextButton(onClick={nameOpen=true}) {Text("Add your name (optional)",color=Muted,fontSize=14.sp)}
                val goals=listOf(Triple("Daily calories",cal,{v:String->cal=v}),Triple("Protein goal (g)",protein,{v:String->protein=v}),Triple("Carbs goal (g)",carbs,{v:String->carbs=v}),Triple("Fat goal (g)",fat,{v:String->fat=v}),Triple("Food allowance (₹)",allowance,{v:String->allowance=v}),Triple("Meals left",opportunities,{v:String->opportunities=v}))
                val totals=listOf(Triple("Calories eaten today",eatenCal,{v:String->eatenCal=v}),Triple("Protein eaten (g)",eatenP,{v:String->eatenP=v}),Triple("Carbs eaten (g)",eatenC,{v:String->eatenC=v}),Triple("Fat eaten (g)",eatenF,{v:String->eatenF=v}),Triple("Money spent today (₹)",spent,{v:String->spent=v}))
                listOf(goals,totals).forEachIndexed { index, fields ->
                    if(index==1) {
                        TextButton(onClick={totalsOpen=!totalsOpen}) {
                            Text(if(totalsOpen) "Hide today's food & spending" else "Edit today's food & spending",color=White,fontSize=16.sp)
                        }
                        if(totalsOpen) Text("Enter only what you've eaten or spent. Orders aren't logged automatically.",color=Muted,fontSize=14.sp,lineHeight=20.sp)
                    }
                    if(index==0 || totalsOpen) fields.forEach { (label,value,update) -> FilledInput(value,{v->update(v.filter {it.isDigit()})},label,Modifier.fillMaxWidth(),showLabel=true,keyboardType=KeyboardType.Number) }
                }
                if(!valid) Text("Use positive goals, 0–8 meals left, and non-negative food and spending totals.",color=SoftCrimson,fontSize=14.sp,lineHeight=20.sp)
                Spacer(Modifier.height(8.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp).navigationBarsPadding()) {
                PrimaryButton("Save my day",Icons.Outlined.Check,enabled=valid) {
                    model.invalidateCheckInUndo();model.displayName=name;model.calorieGoal=cal.toInt();model.proteinGoal=protein.toInt();model.carbGoal=carbs.toInt();model.fatGoal=fat.toInt();model.allowance=allowance.toInt();model.opportunities=opportunities.toInt();model.spent=spent.toInt();model.intake=Nutrition(Span(eatenCal.toInt(),eatenCal.toInt()),Span(eatenP.toInt(),eatenP.toInt()),Span(eatenC.toInt(),eatenC.toInt()),Span(eatenF.toInt(),eatenF.toInt()));onClose()
                }
                TextButton(onClick=onClose,modifier=Modifier.fillMaxWidth()) {Text("Cancel",color=Muted,fontSize=14.sp)}
            }
        }
    }
}

internal fun rupees(value: Double) = if(value==value.toInt().toDouble()) "₹${value.toInt()}" else "₹"+"%.2f".format(java.util.Locale.ROOT,value)

@Composable
internal fun MealImage(meal: Meal,modifier: Modifier=Modifier) {
    Box(modifier.background(Raised),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.Restaurant,null,tint=Muted,modifier=Modifier.size(36.dp))
        val photo=if(BuildConfig.LOCAL_DEMO && meal.photo>0) "file:///android_asset/meal-${meal.photo}.jpg" else meal.imageUrl
        if(photo!=null) AsyncImage(model=ImageRequest.Builder(LocalContext.current).data(photo).diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED).build(),contentDescription=meal.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}

@Composable
internal fun ConnectionStrip(model: HungiiModel) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable {model.accountOpen=true}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.LocationOn,null,tint=Accent,modifier=Modifier.size(20.dp))
        Column(Modifier.weight(1f)){
            Text(if(BuildConfig.LOCAL_DEMO)"Test delivery" else "Swiggy delivery",color=White,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            Text(if(model.connected&&model.addressId!=null)"Delivery address selected" else if(!BuildConfig.LOCAL_DEMO&&!model.connectionAvailable)"Developer access pending" else "Connect and choose an address",color=Muted,fontSize=12.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Delivery settings",tint=Accent,modifier=Modifier.size(18.dp))
    }
    if(model.checkoutRequestId!=null)TextButton(onClick=model::resumeCheckout,enabled=!model.loading){Text(if(model.paymentStage=="confirmed")"View latest order" else "Resume checkout · check status",color=Accent)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDialog(model: HungiiModel,onClose: ()->Unit) {
    val context=LocalContext.current
    var consent by remember {mutableStateOf(false)}
    var section by remember {mutableStateOf("Delivery")}
    var showAddresses by remember {mutableStateOf(model.connected&&model.addressId==null)}
    fun browse(url: String) {try {CustomTabsIntent.Builder().build().launchUrl(context,Uri.parse(url))} catch(_: Exception) {model.connectionMessage="No browser is available. Install a browser to connect."}}
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Surface){
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)){
            DisplayText("Your account",28)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Delivery","Profile","Privacy").forEach {name->SmallChip(name,section==name){section=name}}}
            if(model.loading)LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Accent)
            if(model.connectionMessage.isNotBlank())Text(model.connectionMessage,color=SoftCrimson,fontSize=13.sp,lineHeight=20.sp)
            when(section){
                "Delivery"->{
                    Text(if(BuildConfig.LOCAL_DEMO)"Test delivery connection" else "Connect Swiggy",color=White,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text(if(BuildConfig.LOCAL_DEMO)"Fictional meals and payments. No real money or delivery." else "Sign in with Swiggy, choose your address, then review your basket before ordering.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
                    if(!model.signedIn){
                        Text("First, sign in to Hungii to keep your connection private.",color=Muted,fontSize=13.sp)
                        PrimaryButton("Sign in to Hungii",Icons.AutoMirrored.Outlined.Login,enabled=model.configured&&BuildConfig.WORKOS_AUTH_READY&&!model.loading){try{browse(model.signInUrl())}catch(e:ApiFailure){model.connectionMessage=e.message}}
                    }else if(!model.connected){
                        if(!model.connectionAvailable&&!BuildConfig.LOCAL_DEMO)Text("Developer access is pending. Swiggy connection and ordering will open after approval and testing.",color=Accent,fontSize=14.sp,lineHeight=21.sp)
                        else{
                            if(!BuildConfig.LOCAL_DEMO)Row(verticalAlignment=Alignment.Top){
                                Checkbox(consent,onCheckedChange={consent=it},colors=CheckboxDefaults.colors(checkedColor=Accent,checkmarkColor=Charcoal))
                                Text("Keep my Swiggy connection encrypted for up to five days for my searches, basket and orders. I can disconnect anytime. Checkout recovery stays until I delete my account. Details in Privacy.",color=Muted,fontSize=13.sp,lineHeight=20.sp,modifier=Modifier.padding(top=10.dp))
                            }
                            PrimaryButton(if(BuildConfig.LOCAL_DEMO)"Reconnect test service" else "Continue to Swiggy",Icons.Outlined.Link,enabled=(BuildConfig.LOCAL_DEMO||consent)&&!model.loading){model.connect(::browse)}
                        }
                        TextButton(onClick=model::refresh,enabled=!model.loading){Text("Check connection",color=Accent)}
                    }else{
                        Text(if(BuildConfig.LOCAL_DEMO)"Test service connected" else if(model.environment=="staging")"Swiggy staging connected · no real orders" else "Swiggy connected",color=Accent,fontSize=14.sp)
                        if(!model.orderingEnabled&&!BuildConfig.LOCAL_DEMO)Text("Checkout is awaiting staging verification.",color=Muted,fontSize=13.sp)
                        OutlineButton(if(model.addressId==null)"Choose delivery address" else "Change delivery address"){if(!model.loading){showAddresses=true;model.addressList()}}
                        if(showAddresses){
                            model.addresses.forEach {address->OutlinedButton(onClick={model.selectAddress(address)},enabled=!model.loading,modifier=Modifier.fillMaxWidth()){
                                Column(Modifier.fillMaxWidth()){Text(address.label,color=White,fontWeight=FontWeight.SemiBold);Text(address.addressLine,color=Muted,fontSize=13.sp,lineHeight=20.sp)}
                            }}
                            Row{if(model.addressPage>1)TextButton(onClick={model.addressList(model.addressPage-1)},enabled=!model.loading){Text("Previous")};if(model.moreAddresses)TextButton(onClick={model.addressList(model.addressPage+1)},enabled=!model.loading){Text("More addresses")}}
                        }
                        if(model.checkoutRequestId!=null)OutlineButton("Resume latest checkout"){onClose();model.resumeCheckout()}
                        TextButton(onClick={model.privacyAction="disconnect"},enabled=!model.loading){Text("Disconnect Swiggy",color=SoftCrimson)}
                    }
                }
                "Profile"->{
                    Text("Your Hungii profile",color=White,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text("Your tracker works on this device. Sign in to optionally sync your profile and totals.",color=Muted,fontSize=14.sp,lineHeight=21.sp)
                    if(BuildConfig.LOCAL_DEMO)Text("Cloud profile sync is unavailable in this test build.",color=Muted,fontSize=13.sp)
                    else if(!model.signedIn)PrimaryButton("Sign in with email",Icons.AutoMirrored.Outlined.Login,enabled=model.configured&&BuildConfig.WORKOS_AUTH_READY&&!model.loading){try{browse(model.signInUrl())}catch(e:ApiFailure){model.connectionMessage=e.message}}
                    else{
                        Text(if(model.cloudSyncEnabled)"Cloud sync on" else "Saved on this device",color=Accent,fontSize=14.sp)
                        if(model.cloudSyncMessage.isNotBlank())Text(model.cloudSyncMessage,color=Muted,fontSize=13.sp)
                        if(model.cloudConflict){OutlineButton("Use cloud profile"){model.useCloudCopy()};OutlineButton("Keep this device’s profile"){model.keepLocalCopy()}}
                        else if(!model.cloudSyncEnabled)PrimaryButton("Enable cloud sync",Icons.Outlined.CloudUpload,enabled=!model.loading){model.privacyAction="state_save"}
                        else OutlineButton("Pause cloud sync"){model.pauseCloudSync()}
                        TextButton(onClick=model::restoreProfile,enabled=!model.loading){Text("Restore cloud profile",color=Accent)}
                        TextButton(onClick={model.signOut(::browse)},enabled=!model.loading){Text("Sign out of Hungii",color=Muted)}
                    }
                }
                "Privacy"->{
                    Text("Your data",color=White,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
                    Text("Tracker and saved meals are encrypted on your device. Optional cloud sync uses Hungii’s Mumbai database and expires after 90 days without updates. Saved meals expire after 30 days.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
                    Text(if(BuildConfig.LOCAL_DEMO)"Test carts and orders use fictional data in the local server until restart. The optional assistant sends submitted messages and selected totals to Groq outside India only after you opt in." else "WorkOS handles email sign-in. Swiggy handles its own phone verification and payment. Connection credentials expire within five days. Encrypted checkout records contain your basket, address and payment status to recover interrupted orders. They stay until you delete your Hungii account; unresolved orders block another checkout. Disconnecting does not cancel orders or erase checkout recovery.",color=Muted,fontSize=13.sp,lineHeight=20.sp)
                    Text("Voice recognition uses your device where available. Typing always works. Notice ${model.privacyVersion}.",color=Muted,fontSize=12.sp)
                    if(model.signedIn&&!BuildConfig.LOCAL_DEMO){TextButton(onClick={model.privacyAction="delete_cloud_tracker"},enabled=!model.loading){Text("Erase cloud profile & tracker",color=SoftCrimson)};TextButton(onClick={model.privacyAction="delete_account"},enabled=!model.loading){Text("Delete Hungii account & recovery data",color=SoftCrimson)}}
                    TextButton(onClick={model.privacyAction="erase_local"},enabled=!model.loading){Text("Erase data on this device",color=SoftCrimson)}
                }
            }
            OutlineButton("Done",onClose)
        }
    }
}
