package com.hungii.prototype

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private val voice=mutableStateOf(VoiceState())
    private var recognizer: SpeechRecognizer?=null
    private var onWords: (String)->Unit={}
    private val microphonePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if(allowed) listen() else Toast.makeText(this,"You can type your check-in instead.",Toast.LENGTH_SHORT).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(16,18,17)))
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Charcoal,background=Charcoal,surface=Surface,onSurface=White,onBackground=White,outline=Line)) {
                val model=remember { DemoModel() }
                onWords={ model.update(it) }
                HungiiApp(model,voice.value,::requestVoice)
            }
        }
    }
    private fun requestVoice() {
        if(voice.value.listening) { recognizer?.stopListening(); voice.value=VoiceState(); return }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this,"Speech service unavailable on this device. Type your check-in.",Toast.LENGTH_LONG).show(); return
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) listen()
        else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun listen() {
        recognizer?.destroy()
        recognizer=SpeechRecognizer.createSpeechRecognizer(this).apply {
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
private fun HungiiApp(model: DemoModel,voice: VoiceState,onVoice: ()->Unit) {
    var goalsOpen by remember { mutableStateOf(false) }
    var detailMeal by remember { mutableStateOf<Meal?>(null) }
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
    val reduceMotion=remember { Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
    val focused=model.screen in listOf(Screen.Finalists,Screen.Draw,Screen.Winner,Screen.Review,Screen.Ordered)
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
        Box(Modifier.fillMaxSize().padding(insets).imePadding()) {
            when(model.screen) {
                Screen.Home -> HomeScreen(model,voice,onVoice,{goalsOpen=true})
                Screen.Discover -> DiscoverScreen(model,{detailMeal=it})
                Screen.Finalists -> FinalistsScreen(model)
                Screen.Draw -> DrawScreen(model,reduceMotion)
                Screen.Winner -> WinnerScreen(model,{detailMeal=it})
                Screen.Review -> ReviewScreen(model)
                Screen.Ordered -> OrderedScreen(model)
                Screen.Saved -> SavedScreen(model)
                Screen.Day -> DayScreen(model,{goalsOpen=true})
            }
        }
    }
    if(goalsOpen) GoalsDialog(model) { goalsOpen=false }
    detailMeal?.let { meal ->
        ModalBottomSheet(onDismissRequest={detailMeal=null},containerColor=Surface) {
            Column(Modifier.padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Eyebrow("THE WHOLE PICTURE",Lime)
                DisplayText(meal.name.uppercase(),34)
                Text(meal.benefit,color=White,fontSize=15.sp)
                Text(meal.compromise,color=Coral,fontSize=15.sp)
                val n=meal.nutrition
                val cal=Span(model.caloriesLeft.low-n.calories.high,model.caloriesLeft.high-n.calories.low)
                val protein=Span(model.proteinLeft.low-n.protein.high,model.proteinLeft.high-n.protein.low)
                Text("After eating: ${cal.label} kcal and ${protein.label}g protein left. After paying: roughly ₹${model.moneyLeft-meal.price.high}–${model.moneyLeft-meal.price.low} left.",color=Muted,lineHeight=23.sp)
                Text("One portion. Fictional sample nutrition and prices; this stock photo illustrates the meal card. Actual portion-level nutrition and final checkout require separate verification.",color=Muted,fontSize=12.sp,lineHeight=19.sp)
                LimeButton("Got it",Icons.Outlined.Check) { detailMeal=null }
            }
        }
    }
}

@Composable
private fun BrandHeader(onDay: ()->Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("hungii",fontSize=29.sp,fontWeight=FontWeight.Black,letterSpacing=(-1.5).sp,color=White)
            Text(".",fontSize=36.sp,fontWeight=FontWeight.Black,color=Lime)
            Spacer(Modifier.width(10.dp)); Badge("DEMO",Muted,Surface)
        }
        IconButton(onClick=onDay) { Icon(Icons.Outlined.Tune,"Edit your day",tint=White,modifier=Modifier.size(23.dp)) }
    }
}

@Composable
private fun HomeScreen(model: DemoModel,voice: VoiceState,onVoice: ()->Unit,onDay: ()->Unit) {
    var message by remember { mutableStateOf("") }
    val compact=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<380
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 12.dp)) {
        BrandHeader(onDay)
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
        LimeButton("Find my next meal",Icons.Outlined.ArrowForward) { model.screen=Screen.Discover }
        Text("Sample day · scripted check-in · optional device voice",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=14.dp))
    }
}

@Composable
private fun DiscoverScreen(model: DemoModel,onDetails: (Meal)->Unit) {
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
            SmallChip("High protein",model.highProtein) { model.highProtein=!model.highProtein }
            SmallChip("Under ₹250",model.budgetOnly) { model.budgetOnly=!model.budgetOnly }
            SmallChip("Veg only",model.vegOnly) { model.vegOnly=!model.vegOnly }
            SmallChip("Fast",model.fast) { model.fast=!model.fast }
        }
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
                DisplayText("NO MORE IN\nTHIS BATCH.",44)
                Spacer(Modifier.height(18.dp))
                Text("Keep the choices you liked, or change the plan. Your filters stay yours.",color=Muted,textAlign=TextAlign.Center,lineHeight=22.sp)
                Spacer(Modifier.height(24.dp))
                if(model.finalists.isNotEmpty()) LimeButton("See my finalists",Icons.Outlined.ArrowForward) {model.showFinalists()}
                TextButton(onClick={model.passed.clear()}) {Text("Revisit passed meals",color=Lime)}
            }
        }
    }
}

@Composable
private fun MealProfile(meal: Meal,model: DemoModel,modifier: Modifier,onDetails: (Meal)->Unit,onSwipe: (Boolean)->Unit) {
    var drag by remember(meal.id) { mutableFloatStateOf(0f) }
    val n=meal.nutrition
    Column(modifier.graphicsLayer { translationX=drag;rotationZ=drag/35f }.clip(RoundedCornerShape(26.dp)).background(Surface)
        .pointerInput(meal.id) {
            detectHorizontalDragGestures(onDragEnd={ if(abs(drag)>100.dp.toPx()) onSwipe(drag>0);drag=0f },onDragCancel={drag=0f}) { change, amount -> change.consume(); drag+=amount }
        }) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Image(painterResource(meal.image),"Illustrative sample food photo",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.25f)))))
            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Badge(meal.badge,Charcoal,Lime)
                IconButton(onClick={model.toggleSaved(meal)},modifier=Modifier.size(38.dp).background(Charcoal.copy(alpha=.7f),CircleShape)) {
                    Icon(if(meal.id in model.saved) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"Save ${meal.name}",tint=if(meal.id in model.saved)Lime else White,modifier=Modifier.size(20.dp))
                }
            }
            if(abs(drag)>30) Badge(if(drag>0) "KEEP" else "PASS",if(drag>0)Charcoal else White,if(drag>0)Lime else Color.Black.copy(alpha=.7f),Modifier.align(Alignment.Center).graphicsLayer { rotationZ=if(drag>0)-12f else 12f })
            Text("ILLUSTRATIVE PHOTO",fontSize=8.sp,color=White.copy(alpha=.85f),letterSpacing=1.sp,modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(meal.restaurant,color=Muted,fontSize=11.sp,lineHeight=16.sp)
                Text(if(meal.veg) "● VEG" else "● NON-VEG",color=if(meal.veg)Lime else Coral,fontSize=9.sp,lineHeight=12.sp,fontWeight=FontWeight.Bold)
            }
            DisplayText(meal.name.uppercase(),28,maxLines=2)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Row(verticalAlignment=Alignment.Bottom) {DisplayText("₹${meal.price.label}",29);Text("  est. total",color=Muted,fontSize=10.sp,modifier=Modifier.padding(bottom=4.dp))}
                Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.Schedule,null,tint=Muted,modifier=Modifier.size(13.dp));Spacer(Modifier.width(4.dp));Text("${meal.minutes.label} min",color=White,fontSize=11.sp)}
            }
            MacroStats(n)
            Text("SAMPLE ESTIMATES · ONE PORTION",color=Muted,fontSize=8.sp,lineHeight=12.sp,letterSpacing=.8.sp)
            TradeLine(Icons.Outlined.Add,Lime,if(model.taste in meal.tags) "Matches your ${model.taste} craving." else "${n.protein.label}g protein contribution.")
            val proteinAfter=Span((model.proteinLeft.low-n.protein.high).coerceAtLeast(0),(model.proteinLeft.high-n.protein.low).coerceAtLeast(0))
            TradeLine(Icons.Outlined.Remove,Coral,if(model.opportunities==1&&proteinAfter.high>0) "Still ~${proteinAfter.label}g short of your protein target." else if(meal.price.mid>model.mealMoneyGuide) "~₹${(meal.price.mid-model.mealMoneyGuide).toInt()} above your next-meal guide." else meal.compromise,maxLines=1)
            Row(Modifier.fillMaxWidth().clickable { onDetails(meal) },horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text("Leaves ~₹${model.moneyLeft-meal.price.high}–${model.moneyLeft-meal.price.low} for later",fontSize=10.sp,lineHeight=14.sp,color=Muted)
                Icon(Icons.Outlined.ArrowOutward,"Meal trade-off details",tint=Lime,modifier=Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun FinalistsScreen(model: DemoModel) {
    Column(Modifier.fillMaxSize().padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FocusHeader("YOUR FINALISTS") {model.goBack()}
        DisplayText("YOU PICKED\nTHE GOOD ONES.",52)
        Text("All of these got a yes. The last choice can be the easy one.",color=Muted,lineHeight=23.sp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterVertically)) {
            model.finalists.toList().forEachIndexed { i,m ->
                Row(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(20.dp)).background(Surface),verticalAlignment=Alignment.CenterVertically) {
                    Image(painterResource(m.image),"Illustrative ${m.name}",Modifier.width(105.dp).fillMaxHeight(),contentScale=ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Eyebrow("FINALIST 0${i+1}",Lime)
                        Text(m.name,color=White,fontSize=15.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,maxLines=2)
                        Text("₹${m.price.label} est. · ${m.nutrition.protein.label}g P",color=Muted,fontSize=10.sp,lineHeight=14.sp)
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
private fun DrawScreen(model: DemoModel,reduceMotion: Boolean) {
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
                            Image(painterResource(m.image),null,Modifier.weight(1f).fillMaxWidth(),contentScale=ContentScale.Crop)
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
private fun WinnerScreen(model: DemoModel,onDetails: (Meal)->Unit) {
    val meal=model.winner ?: return
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        FocusHeader("YOUR LUCKY PICK") {model.goBack()}
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.AutoAwesome,null,tint=Lime,modifier=Modifier.size(22.dp));Spacer(Modifier.width(10.dp));DisplayText("IT’S A MEAL MATCH.",40)}
        Box(Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(26.dp))) {
            Image(painterResource(meal.image),"Illustrative sample meal photo",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Badge(meal.badge,Charcoal,Lime,Modifier.align(Alignment.TopStart).padding(15.dp))
        }
        DisplayText(meal.name.uppercase(),40)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(meal.restaurant,color=Muted,fontSize=12.sp);Text("${meal.minutes.label} min",color=Muted,fontSize=12.sp)}
        MacroStats(meal.nutrition)
        Text("Sample nutrition estimates · one portion",color=Muted,fontSize=10.sp)
        TradeLine(Icons.Outlined.Add,Lime,meal.benefit)
        TradeLine(Icons.Outlined.Remove,Coral,meal.compromise,maxLines=3)
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {DisplayText("₹${meal.price.label}",37);Text("estimated full checkout",color=Muted,fontSize=11.sp)}
        TextButton(onClick={onDetails(meal)},modifier=Modifier.fillMaxWidth()) {Text("See what this leaves for later",color=Muted,fontSize=12.sp)}
        Text("Fictional meal & estimates. The draw never places an order.",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=20.dp))
    }
    Box(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal=22.dp,vertical=12.dp)) {
        LimeButton("Let’s have this",Icons.Outlined.ArrowForward) {model.side=false;model.screen=Screen.Review}
    }
    }
}

@Composable
private fun ReviewScreen(model: DemoModel) {
    val meal=model.winner ?: return
    val total=meal.quote(model.side)
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        FocusHeader("MEAL REVIEW") {model.goBack()}
        DisplayText("A GOOD PICK.\nA CLEAR BILL.",50)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Image(painterResource(meal.image),null,Modifier.size(75.dp).clip(RoundedCornerShape(13.dp)),contentScale=ContentScale.Crop)
            Column {Text(meal.name,color=White,fontSize=16.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(6.dp));Text("One meal${if(model.side)" + curd side" else ""}",color=Muted,fontSize=12.sp)}
        }
        MacroStats(meal.nutrients(model.side))
        Text("Sample nutrition estimates · one portion",color=Muted,fontSize=10.sp)
        if(meal.sideDeal) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Lime.copy(alpha=.09f)).clickable {model.side=!model.side}.padding(12.dp),verticalAlignment=Alignment.Top) {
            Checkbox(checked=model.side,onCheckedChange={model.side=it},colors=CheckboxDefaults.colors(checkedColor=Lime,checkmarkColor=Charcoal),modifier=Modifier.size(32.dp))
            Column(Modifier.padding(start=9.dp)) {Text("MORE FOOD. SMALLER BILL.",fontFamily=Display,fontSize=21.sp,color=Lime);Text("A ₹40 curd side unlocks the sample ₹100 offer. Save ₹20 overall. Its nutrition is included above.",color=Muted,fontSize=12.sp,lineHeight=19.sp)}
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Surface).padding(20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            Eyebrow("SIMULATED CHECKOUT",Muted)
            BillLine("Food${if(model.side)" + side" else ""}","₹${meal.menuPrice+if(model.side)40 else 0}")
            BillLine("Delivery & other charges","₹${meal.charges}")
            BillLine("Sample coupon","−₹${if(model.side&&meal.sideDeal)100 else meal.discount}",Lime)
            HorizontalDivider(color=Line)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {Text("Sample total",color=White,fontWeight=FontWeight.SemiBold);DisplayText("₹$total",39,Lime)}
        }
        Text("₹${model.moneyLeft-total} would remain for today. Food is reserved when confirmed, and logged only when eaten.",color=Muted,lineHeight=22.sp,fontSize=13.sp)
        if(total>model.moneyLeft) Text("This bill exceeds your remaining allowance. Revisit your finalists or edit your day.",color=Coral,fontSize=12.sp)
        Text("No real cart, offer, payment or order is connected.",color=Muted,fontSize=10.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(bottom=24.dp))
    }
    Column(Modifier.fillMaxWidth().background(Charcoal).padding(horizontal=22.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        BillLine("Sample total","₹$total",Lime)
        LimeButton("Confirm demo meal",Icons.Outlined.Check,enabled=total<=model.moneyLeft) {model.confirm()}
    }
    }
}

@Composable
private fun OrderedScreen(model: DemoModel) {
    val order=model.orders.lastOrNull() ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(19.dp)) {
        FocusHeader("YOUR MEAL") {model.screen=Screen.Home}
        Box(Modifier.fillMaxWidth().height(140.dp),contentAlignment=Alignment.Center) {Box(Modifier.size(88.dp).background(Lime,CircleShape),contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Check,null,tint=Charcoal,modifier=Modifier.size(42.dp))}}
        DisplayText(if(order.eaten>=1) "FUEL LOGGED.\nDAY UPDATED." else "ONE LESS THING\nTO THINK ABOUT.",52)
        Text(order.meal.name,color=White,fontSize=20.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {MetricTile("SPENT","₹${order.cost}","Already recorded",White,Modifier.weight(1f));MetricTile("FOOD LOGGED","${(order.eaten*100).toInt()}%",if(order.eaten<1)"Rest is reserved" else "Intake updated",Lime,Modifier.weight(1f))}
        Text(if(order.eaten==0f) "Sample spending is recorded. Your meal hasn’t been counted as eaten yet." else if(order.eaten<1) "Half logged. The remaining half is set aside, and stays out of consumed totals." else "Calories and macros reflect the portion you ate. Your next decision starts from here.",color=Muted,lineHeight=24.sp)
        if(order.eaten<1) {
            LimeButton(if(order.eaten==0f) "I ate the whole meal" else "Log the remaining half",Icons.Outlined.Restaurant) {model.eat(1f-order.eaten)}
            if(order.eaten==0f) OutlineButton("I ate half · save the rest") {model.eat(.5f)}
        }
        if(model.receipt.isNotEmpty()) Text(model.receipt,color=Muted,fontSize=12.sp)
        OutlineButton("Back to my day") {model.screen=Screen.Home}
        TextButton(onClick={model.nextMeal()},modifier=Modifier.fillMaxWidth()) {Text("Plan my next meal →",color=Lime)}
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SavedScreen(model: DemoModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        BrandHeader {model.screen=Screen.Day}
        DisplayText("YOUR USUALS.\nYOUR SHORTCUT.",50)
        Text("Familiar meals, saved for this prototype session.",color=Muted,fontSize=13.sp,lineHeight=22.sp)
        demoMeals.filter {it.id in model.saved}.forEach {meal ->
            Row(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(22.dp)).background(Surface),verticalAlignment=Alignment.CenterVertically) {
                Image(painterResource(meal.image),"Illustrative sample meal photo",Modifier.width(115.dp).fillMaxHeight(),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f).padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(meal.name,color=White,fontWeight=FontWeight.Bold,fontSize=15.sp,lineHeight=19.sp,maxLines=2)
                    Text("₹${meal.price.label} est. · ${meal.nutrition.protein.label}g protein",color=Muted,fontSize=10.sp,lineHeight=14.sp,maxLines=2)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={
                            if(meal !in model.finalists&&model.finalists.size<3&&(!model.vegOnly||meal.veg)&&meal.price.low<=model.moneyLeft&&(!model.budgetOnly||meal.price.high<=250)) {model.like(meal);if(model.finalists.size<3)model.screen=Screen.Discover}
                            else model.receipt="This saved meal doesn’t fit the current pool, or your finalist slots are full."
                        },contentPadding=PaddingValues(0.dp)) {Text("Shortlist →",color=Lime,fontSize=12.sp)}
                        IconButton(onClick={model.toggleSaved(meal)},modifier=Modifier.size(35.dp)) {Icon(Icons.Outlined.Favorite,"Unsave ${meal.name}",tint=Lime,modifier=Modifier.size(17.dp))}
                    }
                }
            }
        }
        if(model.saved.isEmpty()) Text("Tap the heart on a meal to make it a go-to.",color=Muted)
        if(model.receipt.isNotEmpty()) Text(model.receipt,color=Muted,fontSize=12.sp)
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
private fun DayScreen(model: DemoModel,onEdit: ()->Unit) {
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
        model.orders.forEachIndexed { index,order ->
            if(order.eaten<1f) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                Eyebrow("SAVED FOR LATER",Lime)
                Text(order.meal.name,color=White,fontSize=15.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold)
                Text("${((1f-order.eaten)*100).toInt()}% left · ${order.meal.nutrients(order.side).calories.portion(1f-order.eaten).label} kcal reserved",color=Muted,fontSize=12.sp,lineHeight=18.sp)
                TextButton(onClick={model.eatAt(index,1f-order.eaten)}) {Text("I ate the rest",color=Lime)}
            }
        }
        OutlineButton("Edit my targets & allowance") {onEdit()}
        Text("Prototype food log. Estimates stay estimates; ordering does not mean eating.",color=Muted,fontSize=11.sp,lineHeight=19.sp,modifier=Modifier.padding(bottom=20.dp))
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
private fun BottomTabs(model: DemoModel) {
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
private fun MacroStats(n: Nutrition) {
    Row(Modifier.fillMaxWidth().border(1.dp,Line,RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        listOf(Triple(n.calories.label,"KCAL",White),Triple(n.protein.label,"PROTEIN · G",Lime),Triple(n.carbs.label,"CARBS · G",Cyan),Triple(n.fat.label,"FAT · G",Coral)).forEach { (value,label,color) ->
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
    Row(Modifier.fillMaxWidth().height(62.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {IconButton(onClick=onBack,modifier=Modifier.size(40.dp).border(1.dp,Line,CircleShape)) {Icon(Icons.Outlined.ArrowBack,"Back",tint=White,modifier=Modifier.size(20.dp))};Eyebrow(label,Muted);Spacer(Modifier.weight(1f));Badge("DEMO",Muted,Surface)}
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
private fun GoalsDialog(model: DemoModel,onClose: ()->Unit) {
    var cal by remember {mutableStateOf(model.calorieGoal.toString())};var protein by remember {mutableStateOf(model.proteinGoal.toString())}
    var carbs by remember {mutableStateOf(model.carbGoal.toString())};var fat by remember {mutableStateOf(model.fatGoal.toString())}
    var allowance by remember {mutableStateOf(model.allowance.toString())};var opportunities by remember {mutableStateOf(model.opportunities.toString())}
    val valid=listOf(cal,protein,carbs,fat,allowance).all { (it.toIntOrNull()?:0)>0 } && (opportunities.toIntOrNull()?:-1) in 0..8
    AlertDialog(onDismissRequest=onClose,containerColor=Surface,title={DisplayText("YOUR DAY. YOUR RULES.",31)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf(Triple("Daily calories",cal,{v:String->cal=v}),Triple("Protein goal (g)",protein,{v:String->protein=v}),Triple("Carbs goal (g)",carbs,{v:String->carbs=v}),Triple("Fat goal (g)",fat,{v:String->fat=v}),Triple("Food allowance (₹)",allowance,{v:String->allowance=v}),Triple("Opportunities left",opportunities,{v:String->opportunities=v})).forEach { (label,value,update) ->
                OutlinedTextField(value,onValueChange={v->update(v.filter {it.isDigit()})},label={Text(label,fontSize=12.sp)},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
            }
            Text("Food already eaten and money already spent stay recorded.",color=Muted,fontSize=11.sp,lineHeight=18.sp)
        }
    },confirmButton={TextButton(enabled=valid,onClick={model.invalidateCheckInUndo();model.calorieGoal=cal.toInt();model.proteinGoal=protein.toInt();model.carbGoal=carbs.toInt();model.fatGoal=fat.toInt();model.allowance=allowance.toInt();model.opportunities=opportunities.toInt();onClose()}) {Text("Update my day",color=if(valid)Lime else Muted)}},dismissButton={TextButton(onClick=onClose) {Text("Cancel",color=Muted)}})
}
