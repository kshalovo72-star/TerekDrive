package com.terekdrive

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val RELEASES_URL = "https://github.com/kshalovo72-star/TerekDrive/releases"
private const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/kshalovo72-star/TerekDrive/main/remote-config.json"
private const val PREFS = "terek_drive"
private const val PREF_LAST_UPDATE = "last_notified_update"
private const val PREF_SEARCH_HISTORY = "search_history"
private const val GROZNY_LAT = 43.3178
private const val GROZNY_LON = 45.6985
private const val APP_VERSION_CODE = 12

private val BG = Color(0xFF07090C)
private val PANEL = Color(0xFF10151B)
private val RED = Color(0xFFFF3B30)
private val CYAN = Color(0xFF00D9FF)
private val GREEN = Color(0xFF00E5A0)
private val MUTED = Color(0xFF8995A3)

private enum class Season(val title:String,val emoji:String,val accent:Color,val bg:Color) {
    SPRING("ВЕСНА","🌱",Color(0xFF55D66A),Color(0xFF07120C)),
    SUMMER("ЛЕТО","☀️",Color(0xFFFFB300),Color(0xFF111006)),
    AUTUMN("ОСЕНЬ","🍂",Color(0xFFFF7043),Color(0xFF160B07)),
    WINTER("ЗИМА","❄️",Color(0xFF64D8FF),Color(0xFF071016))
}
private fun currentSeason():Season=when(java.time.LocalDate.now().monthValue){
    3,4,5->Season.SPRING;6,7,8->Season.SUMMER;9,10,11->Season.AUTUMN;else->Season.WINTER
}

private data class Language(val name:String,val tag:String)
private val languages=listOf(
    Language("Русский","ru"),Language("English","en"),Language("Deutsch","de"),
    Language("Français","fr"),Language("Español","es")
)
private data class Assistant(val name:String,val pitch:Float,val rate:Float)
private val gena=Assistant("Гена",0.96f,1.02f)

private data class Car(
    val name:String,val type:String,val hp:Int,val top:Int,val torque:Int,val drive:String,val weight:Int
)
private val cars=listOf(
    Car("BMW M5","SPORT",730,305,1000,"xDrive",1970),
    Car("Mercedes G63","SUV",585,240,850,"4MATIC",2485),
    Car("Lamborghini Huracán","SUPER",640,325,600,"AWD",1422)
)
private data class Gauge(val name:String,val accent:Color,val secondary:Color,val max:Int)
private val gauges=listOf(
    Gauge("КЛАССИКА",RED,Color(0xFFFF8A80),300),
    Gauge("СПОРТ",Color(0xFFFF1744),Color(0xFFFFB000),320),
    Gauge("БУДУЩЕЕ",CYAN,Color(0xFF7C4DFF),360),
    Gauge("ICE",Color(0xFFEAF6FF),Color(0xFF75BFFF),280),
    Gauge("НОЧЬ",Color(0xFF4C9AFF),Color(0xFFB66CFF),300),
    Gauge("OFFROAD",Color(0xFFFFB300),Color(0xFF66BB6A),260),
    Gauge("РЕТРО",Color(0xFFE6D0A8),Color(0xFFFF7043),220),
    Gauge("ХРОМ",Color(0xFFB8C2CC),Color.White,300),
    Gauge("НЕОН",Color(0xFFB66CFF),CYAN,340),
    Gauge("ТЕРЕК",GREEN,CYAN,320)
)

class MainActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        createUpdateChannel(this)
        setContent{TerekDrive()}
        playStartupSound(this)
    }
}


private fun playStartupSound(context:Context){
    runCatching{
        val tg=ToneGenerator(AudioManager.STREAM_MUSIC,85)
        Thread{
            try{tg.startTone(ToneGenerator.TONE_PROP_BEEP2,90);Thread.sleep(110);tg.startTone(ToneGenerator.TONE_PROP_ACK,110);Thread.sleep(130);tg.startTone(ToneGenerator.TONE_PROP_BEEP,180)}
            finally{tg.release()}
        }.start()
    }
}
private data class WeatherState(val temp:Double,val feels:Double,val wind:Double,val humidity:Int,val code:Int)
private fun weatherText(code:Int)=when(code){0->"Ясно";1,2->"Переменная облачность";3->"Пасмурно";45,48->"Туман";51,53,55->"Морось";61,63,65,80,81,82->"Дождь";71,73,75,77,85,86->"Снег";95,96,99->"Гроза";else->"Погода"}
private fun weatherIcon(code:Int)=when(code){0->"☀";1,2->"⛅";3->"☁";45,48->"🌫";51,53,55,61,63,65,80,81,82->"🌧";71,73,75,77,85,86->"❄";95,96,99->"⛈";else->"🌤"}
private fun fetchWeather():WeatherState?=runCatching{
    val u=URL("https://api.open-meteo.com/v1/forecast?latitude=$GROZNY_LAT&longitude=$GROZNY_LON&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m").openConnection() as HttpURLConnection
    u.connectTimeout=6000;u.readTimeout=6000
    val o=JSONObject(u.inputStream.bufferedReader().use{it.readText()});u.disconnect();val x=o.getJSONObject("current")
    WeatherState(x.getDouble("temperature_2m"),x.getDouble("apparent_temperature"),x.getDouble("wind_speed_10m"),x.getInt("relative_humidity_2m"),x.getInt("weather_code"))
}.getOrNull()
@Composable
private fun WeatherOverlay(season:Season,animations:Boolean){
    var weather by remember{mutableStateOf<WeatherState?>(null)}
    LaunchedEffect(Unit){weather=withContext(Dispatchers.IO){fetchWeather()}}
    Box(Modifier.fillMaxSize()){
        val flow by rememberInfiniteTransition(label="weather").animateFloat(0f,1f,infiniteRepeatable(tween(if(animations)1200 else 9000),RepeatMode.Restart),label="weather-flow")
        Canvas(Modifier.fillMaxSize()){
            val code=weather?.code?:0
            val rain=code in 51..67 || code in 80..82;val snow=code in 71..77 || code in 85..86;val storm=code in 95..99
            if(rain)repeat(if(animations)55 else 10){i->{val x=((i*83)%100)/100f*size.width;val y=((i*47)%100)/100f*size.height+flow*size.height;drawLine(Color(0xFF78BFFF).copy(alpha=.30f),Offset(x,y),Offset(x-5f,y+16f),2f)}}
            if(snow)repeat(if(animations)42 else 8){i->{val x=(((i*71)%100)/100f*size.width+sin((flow+i)*4f)*12f)%size.width;val y=(((i*53)%100)/100f*size.height+flow*size.height)%size.height;drawCircle(Color.White.copy(alpha=.62f),if(i%3==0)3.2f else 2f,Offset(x,y))}}
            if(storm&&((flow>.04f&&flow<.11f)||(flow>.55f&&flow<.59f)))drawRect(Color.White.copy(alpha=.15f))
        }
        weather?.let{w->
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xDD0D141B)),modifier=Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)){
                Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(weatherIcon(w.code),fontSize=28.sp);Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)){Text(weatherText(w.code),fontWeight=FontWeight.Black,fontSize=12.sp);Text("Грозный • ощущается %.0f°C • ветер %.0f км/ч".format(w.feels,w.wind),color=MUTED,fontSize=8.sp)}
                    Column(horizontalAlignment=Alignment.End){Text("%.0f°".format(w.temp),color=season.accent,fontSize=21.sp,fontWeight=FontWeight.Black);Text("влажн. "+w.humidity+"%",color=MUTED,fontSize=8.sp)}
                }
            }
        }
    }
}
@Composable
private fun TerekDrive(){
    var season by rememberSaveable{mutableStateOf(currentSeason())}
    var tab by remember{mutableIntStateOf(0)}
    var sound by rememberSaveable{mutableStateOf(true)}
    var animations by rememberSaveable{mutableStateOf(true)}
    var assistant by rememberSaveable{mutableIntStateOf(0)}
    var language by rememberSaveable{mutableIntStateOf(0)}
    var splash by rememberSaveable{mutableStateOf(true)}
    var genaOpen by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    LaunchedEffect(Unit){
        if(Build.VERSION.SDK_INT>=33 &&
            ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        delay(1800);splash=false;checkForUpdates(context)
    }
    MaterialTheme(colorScheme=darkColorScheme(background=season.bg,surface=PANEL,primary=season.accent,onBackground=Color.White,onSurface=Color.White)){
        Surface(Modifier.fillMaxSize(),color=season.bg){
            AnimatedContent(
                targetState=splash,
                transitionSpec={fadeIn(tween(450))+scaleIn(initialScale=.88f,animationSpec=tween(650)) togetherWith fadeOut(tween(300))},
                label="startup"
            ){showSplash->
                if(showSplash) SplashScreen(season) else Box(Modifier.fillMaxSize()){
                    SeasonEffects(season,animations)
                    Column(Modifier.fillMaxSize()){
                        Header(sound,season){sound=!sound}
                        Box(Modifier.weight(1f)){
                            when(tab){
                                0->MapScreen(season)
                                1->NavigationScreen(language,sound,{tab=0})
                                2->DriveScreen(sound,animations,assistant,language,{tab=0})
                                3->GarageScreen(season)
                                else->SettingsScreen(sound,animations,assistant,language,{sound=!sound},{animations=!animations},{assistant=it},{language=it},season){season=it}
                            }
                        }
                        NavigationBar(containerColor=Color(0xFF090C10)){
                            val items=listOf(
                                Icons.Default.Map to "Карта",
                                Icons.Default.Navigation to "Навигация",
                                Icons.Default.Speed to "Скорость",
                                Icons.Default.DirectionsCar to "Гараж",
                                Icons.Default.Settings to "Настройки"
                            )
                            items.forEachIndexed{i,item->
                                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.first,null)},label={Text(item.second,fontSize=8.sp)})
                            }
                        }
                    }
                    GenaQuickCall(open=genaOpen,onOpen={genaOpen=true},onClose={genaOpen=false},sound=sound,language=language,season=season)
                }
            }
        }
    }
}


private val genaReplies = listOf(
    "как дела" to listOf("В порядке, шеф. Мотор не жалуется, значит и я не жалуюсь.","Отлично. Я на связи, дорога под контролем, настроение — турбо."),
    "скучно" to listOf("Скучно? Тогда включай музыку. Но только не устраивай гонки с голубями.","Я могу шутить бесконечно. Но обещай, что руль всё-таки держишь двумя руками."),
    "кто ты" to listOf("Я Гена — твой голосовой штурман. Не идеальный, зато всегда рядом и без кофе не капризничаю.","Гена. Навигатор, собеседник и человек, который никогда не скажет: я же говорил."),
    "помоги" to listOf("Конечно. Скажи, куда едем, что включить или просто спроси меня о чём-нибудь.","Я рядом. Могу подсказать по дороге, пошутить или напомнить не торопиться."),
    "спасибо" to listOf("Всегда пожалуйста. За рулём главное — спокойствие, а не геройство.","Пожалуйста, шеф. Премию можно выдать бензином."),
    "привет" to listOf("Привет, шеф! Гена на связи. Куда держим курс?","О, водитель объявился. Я уже думал, ты опять молча смотришь на светофор."),
    "устал" to listOf("Если реально устал — лучше остановись и отдохни. Я никуда не тороплюсь.","Отдых важнее маршрута. Остановись в безопасном месте, а я подожду."),
    "анекдот" to listOf("Почему навигатор не спорит с водителем? Потому что знает: водитель всё равно сделает по-своему.","Едет машина в сервис. Механик спрашивает: что случилось? Машина отвечает: меня опять водили за нос."),
    "шутка" to listOf("Шутка дня: самый короткий маршрут — тот, который водитель не пропустил.","Я хотел пошутить про тормоза, но передумал. С ними лучше не шутить."),
    "погода" to listOf("Погоду я могу подсказать через погодный модуль, а окно откроешь сам — я пока руки не научился высовывать.","Если на улице мокро, помни: физика тоже едет с тобой."),
    "музыка" to listOf("Музыка — твоя. Выбирай трек, а я сделаю вид, что не слышу твой вокал.","Давай музыку погромче. Но так, чтобы сирены всё равно было слышно.")
)
private val genaFallbacks = listOf(
    "Интересный вопрос. Скажи чуть проще — я постараюсь не потеряться на втором повороте.",
    "Я услышал тебя. Можешь спросить про дорогу, машину, музыку или просто попросить шутку.",
    "Хороший вопрос. У меня пока нет ответа, зато есть чувство юмора — уже неплохо.",
    "Не понял до конца. Повтори ещё раз, шеф. Только без крика — я не ГИБДД.",
    "Я рядом. Давай ещё раз — Гена любит сложные задачи."
)

private fun genaAnswer(text:String):String {
    val q=text.trim().lowercase(Locale.getDefault())
    if(q.isBlank()) return "Я слушаю, шеф."
    genaReplies.firstOrNull{q.contains(it.first)}?.let{return it.second.random()}
    if(q.contains("скорост")||q.contains("быстро")||q.contains("едем")) return "Скорость смотрю по GPS. Главное — выбирай её по дороге и условиям, а не по настроению."
    if(q.contains("маршрут")||q.contains("куда")) return "Назови пункт назначения — построим маршрут. А я буду напоминать о поворотах."
    if(q.contains("машин")||q.contains("авто")||q.contains("машина")) return "В гараже есть десять машин. Выбирай характер: спорт, суперкар, SUV или мускул-кар."
    if(q.contains("время")||q.contains("который час")) return "Сейчас "+java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(java.util.Date())+". Время ехать спокойно, а не торопиться."
    if(q.contains("молодец")||q.contains("круто")) return listOf("Спасибо! Я стараюсь. У меня даже стрелка настроения есть — почти в красной зоне.","Вот это разговор. Едем дальше, шеф.").random()
    return genaFallbacks.random()
}

@Composable
private fun QuickNavBar(tab:Int,onMap:()->Unit,onDrive:()->Unit,onMusic:()->Unit,onGena:()->Unit,season:Season){
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        val items=listOf(
            Triple("КАРТА",Icons.Default.Map,onMap),
            Triple("НАВИГ",Icons.Default.Navigation,onDrive),
            Triple("МУЗЫКА",Icons.Default.MusicNote,onMusic),
            Triple("ГЕНА",Icons.Default.RecordVoiceOver,onGena)
        )
        items.forEachIndexed{index,item->
            FilledTonalButton(onClick=item.third,modifier=Modifier.weight(1f).height(42.dp),contentPadding=PaddingValues(horizontal=5.dp),shape=RoundedCornerShape(14.dp),
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=if((index==0&&tab==0)||(index==1&&tab==1)||(index==2&&tab==3))season.accent.copy(alpha=.18f) else Color(0xEE111820))){
                Icon(item.second,null,Modifier.size(17.dp),tint=season.accent);Spacer(Modifier.width(4.dp));Text(item.first,fontSize=8.sp,fontWeight=FontWeight.Black)
            }
        }
    }
}

@Composable
private fun GenaQuickCall(
    open:Boolean,onOpen:()->Unit,onClose:()->Unit,sound:Boolean,language:Int,season:Season
){
    val context=LocalContext.current
    var input by rememberSaveable{mutableStateOf("")}
    var answer by rememberSaveable{mutableStateOf("Гена на связи. Нажми микрофон или напиши мне что-нибудь.") }
    var listening by remember{mutableStateOf(false)}
    val speechPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted) startGenaListening(context){spoken->input=spoken;answer=genaAnswer(spoken)}
    }
    val recognizer=remember{
        if(SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer){
        recognizer?.setRecognitionListener(object:android.speech.RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){listening=true}
            override fun onBeginningOfSpeech(){}
            override fun onRmsChanged(r:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){listening=false}
            override fun onError(e:Int){listening=false}
            override fun onResults(b:Bundle?){
                listening=false
                val text=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if(text.isNotBlank()){input=text;answer=genaAnswer(text)}
            }
            override fun onPartialResults(b:Bundle?){}
            override fun onEvent(t:Int,b:Bundle?){}
        })
        onDispose{recognizer?.destroy()}
    }
    val speak: (String)->Unit = {text->
        if(sound){
            var engine:TextToSpeech?=null
            engine=TextToSpeech(context){status->
                if(status==TextToSpeech.SUCCESS){
                    engine?.let{
                        applyVoice(it,gena,language)
                        it.speak(text,TextToSpeech.QUEUE_FLUSH,null,"gena_reply")
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()){
        if(!open){
            FloatingActionButton(
                onClick=onOpen,
                modifier=Modifier.align(Alignment.BottomEnd).padding(end=16.dp,bottom=82.dp),
                containerColor=RED,contentColor=Color.White
            ){Icon(Icons.Default.RecordVoiceOver,"Гена")}
        } else {
            Dialog(onDismissRequest=onClose){
                Card(
                    Modifier.fillMaxWidth().padding(10.dp),
                    colors=CardDefaults.cardColors(containerColor=Color(0xFF0D1218)),
                    shape=RoundedCornerShape(26.dp)
                ){
                    Column(Modifier.padding(18.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(season.accent),contentAlignment=Alignment.Center){
                                Text("Г",color=BG,fontSize=28.sp,fontWeight=FontWeight.Black)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){
                                Text("ГЕНА",fontSize=21.sp,fontWeight=FontWeight.Black)
                                Text("быстрый вызов • разговор • шутки",color=MUTED,fontSize=10.sp)
                            }
                            IconButton(onClick=onClose){Icon(Icons.Default.Close,null)}
                        }
                        Spacer(Modifier.height(10.dp))
                        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
                            Column(Modifier.padding(14.dp)){
                                Text(answer,fontSize=14.sp,fontWeight=FontWeight.Medium)
                                Spacer(Modifier.height(5.dp))
                                Text(if(listening)"СЛУШАЮ…" else "Гена понимает короткие фразы и отвечает голосом",color=season.accent,fontSize=9.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value=input,onValueChange={input=it},modifier=Modifier.fillMaxWidth(),singleLine=true,
                            label={Text("Скажи или напиши Гене")},
                            trailingIcon={
                                IconButton(onClick={
                                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
                                        startGenaListening(context){spoken->input=spoken;answer=genaAnswer(spoken)}
                                    } else speechPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }){
                                    Icon(if(listening)Icons.Default.MicOff else Icons.Default.Mic,null,tint=if(listening)RED else season.accent)
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
                            Button({
                                val q=genaAnswer(input);answer=q;speak(q)
                            },Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("СПРОСИТЬ")}
                            OutlinedButton({
                                val q=listOf(
                                    "Знаешь, почему хорошие водители не спорят с навигатором? Потому что навигатор запоминает.",
                                    "Если настроение на нуле — прибавь музыки, но не скорость.",
                                    "Я не опаздываю. Я просто выбираю очень длинный маршрут.",
                                    "Главное на дороге — не победить всех, а спокойно доехать."
                                ).random()
                                answer=q;speak(q)
                            },Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("ШУТКА")}
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()){
                            listOf("Как дела?","Анекдот","Куда едем?").forEach{phrase->
                                AssistChip(onClick={input=phrase;answer=genaAnswer(phrase);speak(answer)},label={Text(phrase,fontSize=9.sp)})
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startGenaListening(context:Context,onText:(String)->Unit){
    if(!SpeechRecognizer.isRecognitionAvailable(context)) return
    val recognizer=SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object:android.speech.RecognitionListener{
        override fun onReadyForSpeech(p:Bundle?){}
        override fun onBeginningOfSpeech(){}
        override fun onRmsChanged(r:Float){}
        override fun onBufferReceived(b:ByteArray?){}
        override fun onEndOfSpeech(){}
        override fun onError(e:Int){recognizer.destroy()}
        override fun onResults(b:Bundle?){
            b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
            recognizer.destroy()
        }
        override fun onPartialResults(b:Bundle?){}
        override fun onEvent(t:Int,b:Bundle?){}
    })
    recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT,"Гена слушает")
    })
}

@Composable
private fun SplashScreen(season:Season){
    val pulse by rememberInfiniteTransition(label="splash").animateFloat(.88f,1.08f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="pulse")
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.size((132*pulse).dp).clip(RoundedCornerShape(38.dp)).background(season.accent),contentAlignment=Alignment.Center){
                Text("TD",color=BG,fontWeight=FontWeight.Black,fontSize=42.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("ТЕРЕК ДРАЙВ",fontWeight=FontWeight.Black,fontSize=28.sp,letterSpacing=2.sp)
            Text(season.emoji+" "+season.title+" • GPS • MAP • DRIVE",color=season.accent,fontSize=11.sp)
        }
    }
}

@Composable
private fun Header(sound:Boolean,season:Season,onSound:()->Unit){
    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(season.accent),contentAlignment=Alignment.Center){
            Text("TD",color=BG,fontWeight=FontWeight.Black,fontSize=16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column{
            Text("ТЕРЕК ДРАЙВ",fontWeight=FontWeight.Black,fontSize=19.sp,letterSpacing=1.3.sp)
            Text(season.emoji+" "+season.title+" • MAP • DRIVE • MUSIC",color=MUTED,fontSize=9.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick=onSound){Icon(if(sound)Icons.Default.VolumeUp else Icons.Default.VolumeOff,null,tint=if(sound)season.accent else MUTED)}
    }
}

@Composable
private fun SeasonEffects(season:Season,animations:Boolean){
    val progress by rememberInfiniteTransition(label="season").animateFloat(
        0f,1f,infiniteRepeatable(tween(if(animations)4200 else 12000),RepeatMode.Restart),label="flow"
    )
    Canvas(Modifier.fillMaxSize()){
        val count=if(animations)42 else 10
        repeat(count){i->
            val x=((i*73)%100)/100f*size.width
            val base=((i*41)%100)/100f*size.height
            val y=(base+progress*size.height*.55f)%size.height
            when(season){
                Season.WINTER->drawCircle(season.accent.copy(alpha=.22f),if(i%3==0)4f else 2f,Offset(x,y))
                Season.AUTUMN->rotate(((i*37)%50-25).toFloat(),Offset(x,y)){drawOval(color=season.accent.copy(alpha=.30f),topLeft=Offset(x,y),size=androidx.compose.ui.geometry.Size(9f,5f))}
                Season.SPRING->drawCircle(season.accent.copy(alpha=.22f),3f,Offset(x,y))
                Season.SUMMER->drawCircle(season.accent.copy(alpha=.10f),if(i%4==0)7f else 3f,Offset(x,y))
            }
        }
    }
}

private fun createUpdateChannel(context:Context){
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("updates","Обновления Терек Драйв",NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}
private fun checkForUpdates(context:Context){
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch{
        runCatching{
            val c=URL(REMOTE_CONFIG_URL).openConnection() as HttpURLConnection
            c.connectTimeout=5000;c.readTimeout=5000
            val root=JSONObject(c.inputStream.bufferedReader().use{it.readText()});c.disconnect()
            val remote=root.optInt("version",0)
            val last=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(PREF_LAST_UPDATE,0)
            if(remote>APP_VERSION_CODE && remote>last){
                if(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
                    val intent=Intent(Intent.ACTION_VIEW,Uri.parse(RELEASES_URL))
                    val pending=PendingIntent.getActivity(context,1201,intent,PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23)PendingIntent.FLAG_IMMUTABLE else 0)
                    val builder=if(Build.VERSION.SDK_INT>=26) android.app.Notification.Builder(context,"updates") else android.app.Notification.Builder(context)
                    val n=builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("Терек Драйв • новая версия")
                        .setContentText(root.optString("message","Доступно обновление приложения"))
                        .setAutoCancel(true).setContentIntent(pending).build()
                    context.getSystemService(NotificationManager::class.java).notify(1201,n)
                    context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(PREF_LAST_UPDATE,remote).apply()
                }
            }
        }
    }
}

@Composable
private fun MapScreen(season:Season){
    var status by remember{mutableStateOf("Онлайн-карта готова")}
    val context=LocalContext.current
    Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(24.dp))){
        AndroidView(
            factory={ctx->
                MapLibre.getInstance(ctx)
                MapView(ctx).also{v->
                    v.onCreate(null);v.onStart();v.onResume()
                    v.getMapAsync{map->
                        map.setStyle(STYLE_URL)
                        map.cameraPosition=CameraPosition.Builder().target(LatLng(43.3178,45.6985)).zoom(11.0).build()
                    }
                }
            },
            onRelease={v->v.onPause();v.onStop();v.onDestroy()},
            modifier=Modifier.fillMaxSize()
        )
        WeatherOverlay(season,true)
        Column(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter)){
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xEE111820)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Icon(Icons.Default.Map,null,tint=season.accent);Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)){Text("MAPLIBRE • OPENFREEMAP",fontWeight=FontWeight.Black,fontSize=12.sp);Text(status,color=MUTED,fontSize=10.sp)}
                Text("OFFLINE",color=GREEN,fontWeight=FontWeight.Black,fontSize=10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button({status="Офлайн-загрузка запущена…";downloadOffline(context){status=it}},Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){
                Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("СКАЧАТЬ ГРОЗНЫЙ ДЛЯ OFFLINE")
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xF20B1015)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("OpenStreetMap + OpenFreeMap",fontWeight=FontWeight.Bold);Text("Карта работает отдельно от 2ГИС-ключа",color=MUTED,fontSize=11.sp)}
            Icon(Icons.Default.WifiOff,null,tint=GREEN)
        }
    }
}

private fun downloadOffline(context:Context,done:(String)->Unit){
    runCatching{
        val bounds=LatLngBounds.from(43.55,45.90,43.10,45.45)
        val definition=OfflineTilePyramidRegionDefinition(STYLE_URL,bounds,8.0,14.0,1f)
        OfflineManager.getInstance(context).createOfflineRegion(definition,"TerekDrive-Grozny".toByteArray(),object:OfflineManager.CreateOfflineRegionCallback{
            override fun onCreate(region:OfflineRegion){region.setDownloadState(OfflineRegion.STATE_ACTIVE);done("Офлайн-загрузка запущена")}
            override fun onError(error:String){done("Ошибка: $error")}
        })
    }.onFailure{done("Ошибка offline: "+it.message)}
}

@Composable
private fun DriveScreen(sound:Boolean,animations:Boolean,assistant:Int,language:Int,onOpenMap:()->Unit){
    var gauge by rememberSaveable{mutableIntStateOf(0)}
    var gpsSpeed by remember{mutableFloatStateOf(0f)}
    var measuredSpeed by remember{mutableFloatStateOf(0f)}
    var lastFixMs by remember{mutableLongStateOf(0L)}
    var running by rememberSaveable{mutableStateOf(false)}
    var started by rememberSaveable{mutableLongStateOf(0L)}
    var elapsed by rememberSaveable{mutableLongStateOf(0L)}
    var gpsEnabled by rememberSaveable{mutableStateOf(false)}
    var currentLat by remember{mutableStateOf<Double?>(null)}
    var currentLon by remember{mutableStateOf<Double?>(null)}
    var locationState by remember{mutableStateOf("GPS не подключён")}
    val context=LocalContext.current
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        gpsEnabled=result[Manifest.permission.ACCESS_FINE_LOCATION]==true || result[Manifest.permission.ACCESS_COARSE_LOCATION]==true
    }

    DisposableEffect(gpsEnabled){
        if(!gpsEnabled)return@DisposableEffect onDispose{}
        val lm=context.getSystemService(LocationManager::class.java)
        val listener=object:LocationListener{
            override fun onLocationChanged(location:Location){
                currentLat=location.latitude
                currentLon=location.longitude
                val raw=if(location.hasSpeed() && location.speed.isFinite())(location.speed*3.6f).coerceIn(0f,380f) else 0f
                measuredSpeed=raw
                gpsSpeed=when{
                    !location.hasSpeed()->0f
                    raw<1.5f->0f
                    abs(raw-gpsSpeed)>45f->gpsSpeed*0.35f+raw*0.65f
                    raw>gpsSpeed->gpsSpeed*0.72f+raw*0.28f
                    else->gpsSpeed*0.84f+raw*0.16f
                }
                lastFixMs=SystemClock.elapsedRealtime()
                locationState=if(location.hasSpeed())"GPS • "+location.accuracy.toInt()+" м" else "GPS • позиция"
            }
        }
        runCatching{
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,250L,0.5f,listener)
            }
        }.onFailure{locationState="GPS недоступен"}
        onDispose{runCatching{lm.removeUpdates(listener)}}
    }

    LaunchedEffect(Unit){
        while(true){
            if(lastFixMs!=0L && SystemClock.elapsedRealtime()-lastFixMs>2200L){
                measuredSpeed=0f
                gpsSpeed=0f
                locationState=if(gpsEnabled)"GPS • ожидание сигнала" else "GPS не подключён"
            }
            delay(250)
        }
    }
    LaunchedEffect(running){
        while(running){elapsed=SystemClock.elapsedRealtime()-started;delay(50)}
    }

    val target=gpsSpeed.coerceIn(0f,gauges[gauge].max.toFloat())
    val speedDisplay by animateFloatAsState(
        targetValue=target,
        animationSpec=tween(if(target>0.5f)130 else 220,easing=FastOutSlowInEasing),
        label="gps-speed"
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("ИЗМЕРЕНИЕ СКОРОСТИ",fontSize=25.sp,fontWeight=FontWeight.Black)
        Text("GPS • скорость • секундомер • приборы",color=MUTED,fontSize=12.sp)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(9.dp)).background(if(gpsEnabled && lastFixMs!=0L)GREEN else RED))
            Spacer(Modifier.width(7.dp));Text(locationState+" • "+if(measuredSpeed<1.5f)"0" else "%.1f".format(measuredSpeed)+" км/ч",color=MUTED,fontSize=10.sp)
        }
        Spacer(Modifier.height(7.dp))
        RoadAnimation(speedDisplay,animations,Modifier.fillMaxWidth().height(68.dp))
        Spacer(Modifier.height(7.dp))
        SpeedometerGauge(gauges[gauge],speedDisplay,Modifier.fillMaxWidth().height(330.dp),animations)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            itemsIndexed(gauges){i,g->
                Column(Modifier.width(108.dp).clip(RoundedCornerShape(15.dp)).background(if(i==gauge)g.accent.copy(alpha=.16f) else PANEL)
                    .border(1.dp,if(i==gauge)g.accent else Color(0xFF252C34),RoundedCornerShape(15.dp)).clickable{gauge=i}.padding(10.dp)){
                    Text("%02d".format(i+1),color=g.accent,fontSize=10.sp,fontWeight=FontWeight.Black)
                    Text(g.name,fontSize=10.sp,fontWeight=FontWeight.Bold);Text("0—"+g.max,color=MUTED,fontSize=9.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
            Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=PANEL)){
                Column(Modifier.padding(16.dp)){Text("%.0f".format(speedDisplay),fontSize=34.sp,fontWeight=FontWeight.Black,color=gauges[gauge].accent);Text("км/ч • GPS",color=MUTED,fontSize=11.sp)}
            }
            Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=PANEL)){
                Column(Modifier.padding(16.dp)){val sec=elapsed/1000;Text("%02d:%02d.%02d".format(sec/60,sec%60,(elapsed%1000)/10),fontSize=24.sp,fontWeight=FontWeight.Black,color=GREEN);Text("секундомер",color=MUTED,fontSize=11.sp)}
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
            Button({if(!running)started=SystemClock.elapsedRealtime()-elapsed;running=!running},Modifier.weight(1f),shape=RoundedCornerShape(15.dp)){Text(if(running)"ПАУЗА" else "СТАРТ")}
            OutlinedButton({running=false;elapsed=0L},Modifier.weight(1f),shape=RoundedCornerShape(15.dp)){Text("СБРОС")}
        }
        Spacer(Modifier.height(8.dp))
        Button({permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){
            Icon(Icons.Default.GpsFixed,null);Spacer(Modifier.width(8.dp));Text(if(gpsEnabled)"ОБНОВИТЬ GPS-ДОСТУП" else "ВКЛЮЧИТЬ GPS-СКОРОСТЬ")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpeedometerGauge(gauge:Gauge,speed:Float,modifier:Modifier,animations:Boolean){
    val fraction=(speed/gauge.max).coerceIn(0f,1f)
    val redline=.86f
    val pulse by rememberInfiniteTransition(label="gauge-pulse").animateFloat(
        .92f,1f,infiniteRepeatable(tween(if(animations)850 else 2200),RepeatMode.Reverse),label="pulse"
    )
    Canvas(modifier){
        val cx=size.width/2f
        val cy=size.height*.50f
        val r=minOf(size.width,size.height)*.43f
        val outer=r+18f
        val angleFor:(Float)->Double={f->Math.toRadians(135.0+270.0*f)}
        val point={radius:Float,f:Float->
            val a=angleFor(f)
            Offset(cx+cos(a).toFloat()*radius,cy+sin(a).toFloat()*radius)
        }

        // Deep instrument housing.
        drawCircle(Color(0xFF030507),outer+12f,Offset(cx,cy))
        drawCircle(Color(0xFF29313A),outer+7f,Offset(cx,cy))
        drawCircle(Color(0xFF090D12),outer+3f,Offset(cx,cy))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF1A2028),Color(0xFF0B0F14),Color(0xFF05070A)),
                center=Offset(cx-r*.18f,cy-r*.25f),
                radius=r*1.25f
            ),r,Offset(cx,cy)
        )
        drawCircle(Color.White.copy(alpha=.025f),r-7f,Offset(cx,cy))

        // Metallic outer highlight.
        drawArc(Color(0xFF5A646E),135f,270f,false,style=Stroke(width=4f))
        drawArc(Color(0xFF11161C),139f,262f,false,style=Stroke(width=9f))

        // Redline zone.
        drawArc(
            Color(0xFF8D171B).copy(alpha=.82f),
            135f+270f*redline,
            270f*(1f-redline),
            false,
            style=Stroke(width=15f)
        )
        if(fraction>0f){
            val liveColor=when{
                fraction>=redline->RED
                fraction>=.72f->Color(0xFFFF7A00)
                else->Color(0xFFE9EDF0)
            }
            drawArc(liveColor.copy(alpha=.16f),135f,270f*fraction,false,style=Stroke(width=13f))
            drawArc(liveColor,135f,270f*fraction,false,style=Stroke(width=3f))
        }

        // 60 precise ticks, with longer OEM-style major marks.
        for(i in 0..60){
            val f=i/60f
            val p1=point(r-15f,f)
            val p2=point(r-(if(i%5==0)38f else 27f),f)
            val red=f>=redline
            val active=f<=fraction
            val c=when{
                red->RED
                active->Color(0xFFE6EBEF)
                else->Color(0xFF68727C)
            }
            drawLine(
                c.copy(alpha=if(red||active).95f else .58f),
                p1,p2,
                if(i%5==0)2.7f else 1.15f,
                StrokeCap.Butt
            )
        }

        // Numeric scale: 0..300, exactly like a real road-car cluster.
        val numberPaint=android.graphics.Paint().apply{
            isAntiAlias=true
            color=Color(0xFFE8EDF1).toArgb()
            textAlign=android.graphics.Paint.Align.CENTER
            textSize=15f
            typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
        }
        for(i in 0..10){
            val f=i/10f
            val p=point(r-62f,f)
            drawContext.canvas.nativeCanvas.drawText(
                (gauge.max*i/10).toString(),p.x,p.y+5f,numberPaint
            )
        }

        val labelPaint=android.graphics.Paint().apply{
            isAntiAlias=true;color=Color(0xFF9DA7B0).toArgb()
            textAlign=android.graphics.Paint.Align.CENTER;textSize=9f
            typeface=android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText("km/h",cx,cy-r*.46f,labelPaint)
        drawContext.canvas.nativeCanvas.drawText("REDLINE",cx,cy-r*.30f,labelPaint)

        // Digital readout sits low and never gets confused with the needle.
        val boxW=r*.62f
        val boxH=r*.24f
        val boxTop=cy+r*.25f
        drawRoundRect(
            Color(0xFF05080B),
            Offset(cx-boxW/2f,boxTop),
            androidx.compose.ui.geometry.Size(boxW,boxH),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(12f,12f)
        )
        drawRoundRect(
            Color(0xFF39424B).copy(alpha=.7f),
            Offset(cx-boxW/2f,boxTop),
            androidx.compose.ui.geometry.Size(boxW,boxH),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(12f,12f),
            style=Stroke(width=1.2f)
        )
        val digitalPaint=android.graphics.Paint().apply{
            isAntiAlias=true
            color=Color.White.toArgb()
            textAlign=android.graphics.Paint.Align.CENTER
            textSize=42f
            typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText("%.0f".format(speed),cx,boxTop+boxH*.67f,digitalPaint)
        val unitPaint=android.graphics.Paint().apply{
            isAntiAlias=true;color=RED.toArgb();textAlign=android.graphics.Paint.Align.CENTER;textSize=8f
            typeface=android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText("GPS  •  LIVE",cx,boxTop+boxH+13f,unitPaint)

        // Short, weighted red needle with a proper central hub.
        val needleA=angleFor(fraction)
        val needleLen=r*.70f
        val tip=Offset(cx+cos(needleA).toFloat()*needleLen,cy+sin(needleA).toFloat()*needleLen)
        val tail=Offset(cx-cos(needleA).toFloat()*r*.11f,cy-sin(needleA).toFloat()*r*.11f)
        drawLine(Color.Black.copy(alpha=.75f),tail,tip,8f,StrokeCap.Round)
        drawLine(RED.copy(alpha=.92f),tail,tip,4.2f,StrokeCap.Round)
        drawLine(Color.White.copy(alpha=.82f),cx,cy,tip,1.1f,StrokeCap.Round)
        drawCircle(Color(0xFF080B0E),16f,Offset(cx,cy))
        drawCircle(Color(0xFF69747D),11f,Offset(cx,cy))
        drawCircle(RED,7f,Offset(cx,cy))
        drawCircle(Color.White.copy(alpha=.85f),2f,Offset(cx-1.5f,cy-1.5f))

        // Glass reflection.
        drawArc(
            Brush.linearGradient(listOf(Color.White.copy(alpha=.14f),Color.Transparent)),
            205f,80f,false,style=Stroke(width=10f)
        )
        drawArc(
            Color.White.copy(alpha=.035f),
            215f,65f,false,style=Stroke(width=2f)
        )

        val statusPaint=android.graphics.Paint().apply{
            isAntiAlias=true
            color=if(fraction>=redline)RED.toArgb() else MUTED.toArgb()
            textAlign=android.graphics.Paint.Align.CENTER;textSize=8f
            typeface=android.graphics.Typeface.DEFAULT_BOLD
        }
        val status=when{
            speed<1f->"СТОЯНКА  •  GPS = 0"
            fraction>=redline->"REDLINE"
            fraction>=.72f->"HIGH SPEED"
            else->"GPS LIVE"
        }
        drawContext.canvas.nativeCanvas.drawText(status,cx,cy+r*.78f,statusPaint)
    }
}

@Composable
private fun RoadAnimation(speed:Float,animations:Boolean,modifier:Modifier){
    val offset by rememberInfiniteTransition(label="road").animateFloat(0f,1f,infiniteRepeatable(tween(if(animations)900 else 5000),RepeatMode.Restart),label="road")
    Canvas(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF080B0F))){
        val moving=speed>.5f
        val roadTop=size.height*.20f;val roadBottom=size.height*.88f
        drawRect(Color(0xFF11161C),topLeft=Offset(0f,roadTop),size=androidx.compose.ui.geometry.Size(size.width,roadBottom-roadTop))
        val centerX=size.width/2f
        val dashTravel=if(moving)offset*80f else 0f
        for(i in -1..12){
            val y=roadTop+((i*45f+dashTravel)%560f)
            drawRoundRect(Color(0xFF9BA4AE).copy(alpha=.65f),Offset(centerX-3f,y),androidx.compose.ui.geometry.Size(6f,25f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4f,4f))
        }
        drawLine(Color(0xFF333A42),Offset(0f,roadTop),Offset(size.width,roadTop),2f)
        drawLine(Color(0xFF333A42),Offset(0f,roadBottom),Offset(size.width,roadBottom),2f)
        val carY=size.height*.60f
        drawRoundRect(RED.copy(alpha=.95f),Offset(centerX-26f,carY),androidx.compose.ui.geometry.Size(52f,25f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(9f,9f))
        drawCircle(Color(0xFFFF8A80),5f,Offset(centerX-15f,carY+20f));drawCircle(Color(0xFFFF8A80),5f,Offset(centerX+15f,carY+20f))
        if(!moving)drawContext.canvas.nativeCanvas.drawText("СТОИМ • GPS = 0",centerX,size.height*.14f,android.graphics.Paint().apply{isAntiAlias=true;color=MUTED.toArgb();textAlign=android.graphics.Paint.Align.CENTER;textSize=11f})
    }
}

private data class Maneuver(val instruction:String,val distance:Int,val icon:String)
private data class RouteResult(val distanceKm:Double,val durationMin:Int,val maneuvers:List<Maneuver>,val destinationLat:Double,val destinationLon:Double)


private fun loadSearchHistory(context:Context):List<String>{
    return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(PREF_SEARCH_HISTORY,"").orEmpty().split("|").map{it.trim()}.filter{it.isNotBlank()}.distinct().take(8)
}
private fun saveSearchHistory(context:Context,value:String){
    val clean=value.trim();if(clean.isBlank())return
    val old=loadSearchHistory(context);val next=(listOf(clean)+old.filterNot{it.equals(clean,true)}).take(8)
    context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(PREF_SEARCH_HISTORY,next.joinToString("|")).apply()
}
@Composable
private fun NavigationScreen(language:Int,sound:Boolean,onOpenMap:()->Unit){
    var currentLat by remember{mutableStateOf<Double?>(null)}
    var currentLon by remember{mutableStateOf<Double?>(null)}
    var gpsEnabled by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        gpsEnabled=result[Manifest.permission.ACCESS_FINE_LOCATION]==true || result[Manifest.permission.ACCESS_COARSE_LOCATION]==true
    }
    DisposableEffect(gpsEnabled){
        if(!gpsEnabled)return@DisposableEffect onDispose{}
        val lm=context.getSystemService(LocationManager::class.java)
        val listener=object:LocationListener{
            override fun onLocationChanged(location:Location){
                currentLat=location.latitude
                currentLon=location.longitude
            }
        }
        runCatching{
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,listener)
            }
        }
        onDispose{runCatching{lm.removeUpdates(listener)}}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("НАВИГАЦИЯ",fontSize=25.sp,fontWeight=FontWeight.Black)
        Text("МАРШРУТ • ПОИСК • МАНЁВРЫ • GPS",color=MUTED,fontSize=12.sp)
        Spacer(Modifier.height(10.dp))
        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(9.dp)).background(if(gpsEnabled)GREEN else RED))
                    Spacer(Modifier.width(7.dp))
                    Text(if(currentLat!=null)"GPS • текущая позиция используется" else "GPS • маршрут от Грозного",color=MUTED,fontSize=10.sp)
                }
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick={permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},
                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)
                ){
                    Icon(Icons.Default.GpsFixed,null);Spacer(Modifier.width(7.dp));Text(if(gpsEnabled)"ОБНОВИТЬ GPS" else "ВКЛЮЧИТЬ GPS")
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        NavigationPlanner(language,sound,currentLat,currentLon,onOpenMap)
    }
}

@Composable
private fun NavigationPlanner(language:Int,sound:Boolean,currentLat:Double?,currentLon:Double?,onOpenMap:()->Unit){
    var destination by rememberSaveable{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    var route by remember{mutableStateOf<RouteResult?>(null)}
    var error by remember{mutableStateOf("")}
    var expanded by rememberSaveable{mutableStateOf(true)}
    val context=LocalContext.current
    var history by remember{mutableStateOf(loadSearchHistory(context))}
    val scope=rememberCoroutineScope()
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(15.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
                Column(Modifier.weight(1f)){
                    Text("НАВИГАЦИЯ",fontWeight=FontWeight.Black,fontSize=16.sp)
                    Text(if(currentLat!=null)"GPS • маршрут от текущей позиции" else "Маршрут • карта • манёвры",color=MUTED,fontSize=9.sp)
                }
                IconButton(onClick={expanded=!expanded}){Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}
            }
            if(expanded){
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
                    AssistChip(onClick={destination="Грозный"},label={Text("Грозный",fontSize=9.sp)})
                    AssistChip(onClick={destination="Аэропорт Грозного"},label={Text("Аэропорт",fontSize=9.sp)})
                    AssistChip(onClick={destination="Центр Грозного"},label={Text("Центр",fontSize=9.sp)})
                }
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(value=destination,onValueChange={destination=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Куда едем?")},placeholder={Text("Улица, город или место")})
                if(history.isNotEmpty()){
                    Text("ПОСЛЕДНИЕ МЕСТА",fontWeight=FontWeight.Black,fontSize=9.sp,color=MUTED,modifier=Modifier.padding(top=7.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.padding(top=4.dp)){
                        itemsIndexed(history){_,item->AssistChip(onClick={destination=item},label={Text(item,fontSize=9.sp)},leadingIcon={Icon(Icons.Default.History,null,Modifier.size(14.dp))})}
                    }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
                    Button(onClick={
                        if(destination.isNotBlank()){
                            loading=true;error=""
                            scope.launch{
                                val result=withContext(Dispatchers.IO){buildRoute(destination,currentLat?:GROZNY_LAT,currentLon?:GROZNY_LON)}
                                route=result;loading=false
                                if(result==null)error="Маршрут не найден. Проверь название места или сеть."
                                else{saveSearchHistory(context,destination);history=loadSearchHistory(context)}
                            }
                        }
                    },modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){
                        Icon(Icons.Default.Route,null);Spacer(Modifier.width(6.dp));Text(if(loading)"СТРОЮ…" else "ПОСТРОИТЬ")
                    }
                    OutlinedButton(onClick=onOpenMap,modifier=Modifier.weight(.72f),shape=RoundedCornerShape(14.dp)){
                        Icon(Icons.Default.Map,null);Spacer(Modifier.width(5.dp));Text("КАРТА")
                    }
                }
                if(error.isNotBlank())Text(error,color=RED,fontSize=10.sp,modifier=Modifier.padding(top=5.dp))
                route?.let{r->
                    Spacer(Modifier.height(9.dp))
                    Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF0A1118)),modifier=Modifier.fillMaxWidth()){
                        Column(Modifier.padding(12.dp)){
                            val next=r.maneuvers.firstOrNull()
                            Row(verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).background(CYAN.copy(alpha=.12f)),contentAlignment=Alignment.Center){Text(next?.icon?:"↑",fontSize=34.sp,color=CYAN,fontWeight=FontWeight.Black)}
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)){
                                    Text("СЛЕДУЮЩИЙ МАНЁВР",color=MUTED,fontSize=8.sp,fontWeight=FontWeight.Black)
                                    Text(next?.instruction?:"Следуйте по маршруту",fontSize=16.sp,fontWeight=FontWeight.Black)
                                    Text(if((next?.distance?:0)<1000) "через "+(next?.distance?:0)+" м" else "через %.1f км".format((next?.distance?:0)/1000.0),color=CYAN,fontSize=10.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.fillMaxWidth()){
                                SpecCard("МАРШРУТ","%.1f км".format(r.distanceKm),CYAN,Modifier.weight(1f))
                                SpecCard("В ПУТИ",formatRouteTime(r.durationMin),GREEN,Modifier.weight(1f))
                                SpecCard("МАНЁВРЫ",r.maneuvers.size.toString(),RED,Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp));Text("ПЛАН МАРШРУТА",fontWeight=FontWeight.Black,fontSize=9.sp,color=MUTED)
                            r.maneuvers.take(10).forEachIndexed{index,m->
                                Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                                    Box(Modifier.size(27.dp).clip(RoundedCornerShape(9.dp)).background(if(index==0)CYAN.copy(alpha=.16f) else PANEL),contentAlignment=Alignment.Center){Text((index+1).toString(),fontSize=9.sp,fontWeight=FontWeight.Black,color=if(index==0)CYAN else MUTED)}
                                    Spacer(Modifier.width(7.dp));Text(m.icon,fontSize=17.sp);Spacer(Modifier.width(7.dp))
                                    Column(Modifier.weight(1f)){Text(m.instruction,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(if(m.distance<1000)m.distance.toString()+" м" else "%.1f км".format(m.distance/1000.0),color=MUTED,fontSize=8.sp)}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun formatRouteTime(minutes:Int):String=if(minutes>=60)(minutes/60).toString()+" ч "+(minutes%60)+" мин" else minutes.toString()+" мин"


private fun buildRoute(query:String,originLat:Double,originLon:Double):RouteResult? = runCatching{
    val gConn=URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+Uri.encode(query)).openConnection() as HttpURLConnection
    gConn.setRequestProperty("User-Agent","TerekDrive/1.3");gConn.connectTimeout=7000;gConn.readTimeout=7000
    val g=JSONArray(gConn.inputStream.bufferedReader().use{it.readText()});gConn.disconnect()
    if(g.length()==0)return@runCatching null
    val lat=g.getJSONObject(0).getDouble("lat");val lon=g.getJSONObject(0).getDouble("lon")
    val c=URL("https://router.project-osrm.org/route/v1/driving/$originLon,$originLat;$lon,$lat?overview=false&steps=true").openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent","TerekDrive/1.3");c.connectTimeout=8000;c.readTimeout=8000
    val root=JSONObject(c.inputStream.bufferedReader().use{it.readText()});c.disconnect()
    val rr=root.getJSONArray("routes").getJSONObject(0);val steps=rr.getJSONArray("legs").getJSONObject(0).getJSONArray("steps");val list=mutableListOf<Maneuver>()
    for(i in 0 until steps.length()){
        val st=steps.getJSONObject(i);val m=st.getJSONObject("maneuver");val type=m.optString("type");val mod=m.optString("modifier");val dist=st.optDouble("distance",0.0).toInt()
        if(dist>0){
            val icon=when(mod){"left"->"←";"right"->"→";"slight left"->"↖";"slight right"->"↗";"straight"->"↑";else->"●"}
            val text=when(type){"depart"->"Начало движения";"arrive"->"Прибытие";"roundabout"->"Круговое движение";"turn"->"Поворот "+when(mod){"left"->"налево";"right"->"направо";else->"прямо"};else->"Продолжайте движение"}
            list.add(Maneuver(text,dist,icon))
        }
    }
    RouteResult(rr.getDouble("distance")/1000.0,(rr.getDouble("duration")/60.0).toInt(),list,lat,lon)
}.getOrNull()

@Composable
private fun AssistantPanel(sound:Boolean,assistantIndex:Int,language:Int){
    val context=LocalContext.current
    var tts by remember{mutableStateOf<TextToSpeech?>(null)}
    DisposableEffect(Unit){
        var engine:TextToSpeech?=null
        engine=TextToSpeech(context){status->if(status==TextToSpeech.SUCCESS)engine?.let{applyVoice(it,gena,language)}}
        tts=engine
        onDispose{engine?.stop();engine?.shutdown()}
    }
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(15.dp)){
            Text("ГЕНА • ГОЛОСОВОЙ ШТУРМАН",fontWeight=FontWeight.Black,fontSize=15.sp)
            Text("Один голос • "+languages[language].name+" • TTS-навигация",color=MUTED,fontSize=10.sp)
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(top=9.dp)){
                Box(Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(RED),contentAlignment=Alignment.Center){Text("Г",fontSize=28.sp,fontWeight=FontWeight.Black)}
                Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("Гена",fontSize=18.sp,fontWeight=FontWeight.Black);Text("спокойный мужской голос",color=MUTED,fontSize=10.sp)}
                Icon(Icons.Default.RecordVoiceOver,null,tint=GREEN)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                Button({if(sound)tts?.let{applyVoice(it,gena,language);it.speak("Гена на связи. Хорошей дороги!",TextToSpeech.QUEUE_FLUSH,null,"greeting")}},Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("ПРОВЕРИТЬ")}
                OutlinedButton({if(sound)tts?.let{applyVoice(it,gena,language);it.speak("Следите за скоростью, держите дистанцию и не отвлекайтесь от дороги.",TextToSpeech.QUEUE_FLUSH,null,"hint")}},Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("ПОДСКАЗКА")}
            }
        }
    }
}
private fun applyVoice(tts:TextToSpeech,profile:Assistant,languageIndex:Int){
    val requested=Locale(languages[languageIndex].tag);val result=tts.setLanguage(requested)
    val actual=if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED)Locale.getDefault() else requested
    tts.voices?.firstOrNull{it.locale.language==actual.language}?.let{tts.voice=it}
    tts.setPitch(profile.pitch);tts.setSpeechRate(profile.rate)
}

@Composable
private fun GarageScreen(season:Season){
    var selected by rememberSaveable{mutableIntStateOf(0)}
    val car=cars[selected]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("МОЙ ГАРАЖ",fontSize=25.sp,fontWeight=FontWeight.Black)
        Text("3 машины • характеристики • реальные приборы DRIVE",color=MUTED,fontSize=12.sp)
        Spacer(Modifier.height(10.dp))
        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp)){
                CarVisual(car,Modifier.fillMaxWidth().height(205.dp),season.accent)
                Spacer(Modifier.height(8.dp));Text(car.name,fontSize=24.sp,fontWeight=FontWeight.Black)
                Text(car.type+" • "+car.drive,color=season.accent,fontWeight=FontWeight.Bold)
                Text("Подготовлена для DRIVE режима",color=MUTED,fontSize=10.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            itemsIndexed(cars){i,c->
                Column(Modifier.width(170.dp).clip(RoundedCornerShape(20.dp)).background(if(i==selected)season.accent.copy(alpha=.12f) else PANEL)
                    .border(1.dp,if(i==selected)season.accent else Color(0xFF252C34),RoundedCornerShape(20.dp)).clickable{selected=i}.padding(11.dp)){
                    CarVisual(c,Modifier.fillMaxWidth().height(112.dp),season.accent)
                    Spacer(Modifier.height(7.dp));Text(c.name,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(c.hp.toString()+" л.с. • "+c.top+" км/ч",color=MUTED,fontSize=10.sp)
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
            SpecCard("МОЩНОСТЬ",car.hp.toString()+" л.с.",RED,Modifier.weight(1f))
            SpecCard("МОМЕНТ",car.torque.toString()+" Нм",CYAN,Modifier.weight(1f))
            SpecCard("МАССА",car.weight.toString()+" кг",GREEN,Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
            SpecCard("МАКС.",car.top.toString()+" км/ч",season.accent,Modifier.weight(1f))
            SpecCard("ПРИВОД",car.drive,season.accent,Modifier.weight(1f))
            SpecCard("КЛАСС",car.type,season.accent,Modifier.weight(1f))
        }
    }
}

private fun carPhotoUrl(car:Car):String=when{
    car.name.contains("Mercedes",true)->"https://images.unsplash.com/photo-1676118497332-94ad05a7c3cf?auto=format&fit=crop&w=1200&q=82"
    car.name.contains("Lamborghini",true)->"https://images.squarespace-cdn.com/content/v1/6724111128709809c6a40d15/0ba5fde6-cce0-4314-8e56-88d4632bcde6/white%2Bhuracan%2Bfront%2Bside.jpeg"
    else->"https://images.unsplash.com/photo-1606221791496-ca89dbedb427?auto=format&fit=crop&w=1400&q=84"
}

@Composable
private fun CarVisual(car:Car,modifier:Modifier,accent:Color){
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF07090C))){
        AsyncImage(
            model=carPhotoUrl(car),
            contentDescription=car.name,
            contentScale=ContentScale.Crop,
            modifier=Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x6605080C),Color.Transparent,Color(0xD907090C))
                )
            )
        )
        Box(
            Modifier.align(Alignment.TopStart).padding(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xB3070A0E))
                .border(1.dp,Color.White.copy(alpha=.10f),RoundedCornerShape(10.dp))
                .padding(horizontal=8.dp,vertical=5.dp)
        ){
            Text(
                "PHOTO GARAGE • ${car.type}",
                color=Color.White.copy(alpha=.88f),
                fontSize=8.sp,
                fontWeight=FontWeight.Black,
                letterSpacing=.6.sp
            )
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(11.dp),
            verticalAlignment=Alignment.Bottom
        ){
            Column(Modifier.weight(1f)){
                Text(car.name,fontSize=17.sp,fontWeight=FontWeight.Black,color=Color.White)
                Text(
                    "${car.hp} л.с. • ${car.top} км/ч • ${car.drive}",
                    color=Color.White.copy(alpha=.78f),
                    fontSize=9.sp,
                    fontWeight=FontWeight.Bold
                )
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha=.88f)),
                contentAlignment=Alignment.Center
            ){
                Icon(Icons.Default.DirectionsCar,null,tint=Color.Black,modifier=Modifier.size(19.dp))
            }
        }
    }
}
@Composable
private fun MediaScreen(sound:Boolean,season:Season){
    val context=LocalContext.current;var selected by rememberSaveable{mutableStateOf("")};var playing by rememberSaveable{mutableStateOf(false)}
    var progress by remember{mutableFloatStateOf(0f)};var duration by remember{mutableIntStateOf(0)};var position by remember{mutableIntStateOf(0)};var player by remember{mutableStateOf<MediaPlayer?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{selected=it.toString();player?.release();player=runCatching{MediaPlayer.create(context,it)}.getOrNull();player?.let{p->duration=p.duration};playing=false;position=0;progress=0f}}
    DisposableEffect(Unit){onDispose{player?.release()}}
    LaunchedEffect(playing,player){while(playing&&player!=null){val p=player!!;position=p.currentPosition;duration=p.duration;progress=if(p.duration>0)p.currentPosition.toFloat()/p.duration else 0f;if(!p.isPlaying){playing=false;break};delay(250)}}
    val bars=rememberInfiniteTransition(label="eq").animateFloat(0f,1f,infiniteRepeatable(tween(650),RepeatMode.Reverse),label="eq-flow")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("MEDIA",fontSize=25.sp,fontWeight=FontWeight.Black);Text("локальная музыка • визуальный эквалайзер • управление треком",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF080C11))){
                Canvas(Modifier.fillMaxSize()){val base=size.height*.78f;for(i in 0 until 28){val h=(10f+((i*19)%55)*bars.value)*(if(playing)1f else .22f);drawRoundRect(season.accent.copy(alpha=.30f+.55f*(i%3)/3f),Offset(i*size.width/28f,base-h),androidx.compose.ui.geometry.Size(size.width/42f,h),cornerRadius=androidx.compose.ui.geometry.CornerRadius(7f,7f))};drawCircle(season.accent.copy(alpha=.09f),size.minDimension*.30f,Offset(size.width/2,base*.55f))}
                Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.MusicNote,null,tint=season.accent,modifier=Modifier.size(48.dp));Text(if(playing)"PLAYING" else "READY",fontWeight=FontWeight.Black)}
            }
            Spacer(Modifier.height(12.dp));Text(if(selected.isBlank())"Трек не выбран" else "Локальный трек",fontSize=18.sp,fontWeight=FontWeight.Black);Text(if(selected.isBlank())"Выбери аудиофайл из памяти телефона" else "Твоя музыка • без обязательного интернета",color=MUTED,fontSize=10.sp)
            Spacer(Modifier.height(8.dp));Slider(value=progress,onValueChange={v->progress=v;player?.seekTo((v*duration).toInt());position=(v*duration).toInt()},enabled=player!=null)
            Row(Modifier.fillMaxWidth()){Text("%02d:%02d".format(position/60000,(position/1000)%60),color=MUTED,fontSize=9.sp);Spacer(Modifier.weight(1f));Text("%02d:%02d".format(duration/60000,(duration/1000)%60),color=MUTED,fontSize=9.sp)}
            Spacer(Modifier.height(6.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
                OutlinedButton({picker.launch("audio/*")},Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.FolderOpen,null);Spacer(Modifier.width(5.dp));Text("ВЫБРАТЬ")}
                Button({player?.let{p->if(p.isPlaying){p.pause();playing=false}else{if(p.currentPosition>=p.duration-100)p.seekTo(0);p.start();playing=true}}},Modifier.weight(1f),enabled=player!=null,shape=RoundedCornerShape(14.dp)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null);Spacer(Modifier.width(5.dp));Text(if(playing)"ПАУЗА" else "ИГРАТЬ")}
            }
        }}
    }
}

@Composable
private fun SettingsScreen(sound:Boolean,animations:Boolean,assistant:Int,language:Int,onSound:()->Unit,onAnimations:()->Unit,onAssistant:(Int)->Unit,onLanguage:(Int)->Unit,season:Season,onSeason:(Season)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
        Text("НАСТРОЙКИ",fontSize=25.sp,fontWeight=FontWeight.Black)
        Text("ТЕРЕК ДРАЙВ • персонализация",color=MUTED,fontSize=12.sp)
        Spacer(Modifier.height(10.dp))
        SettingToggle("ЗВУК","Системные сигналы и Гена",sound,onSound,season.accent)
        SettingToggle("АНИМАЦИИ","Листья • снег • неон • дорога",animations,onAnimations,season.accent)
        Spacer(Modifier.height(8.dp))
        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp)){
                Text("СЕЗОН",fontWeight=FontWeight.Black)
                Text("Выбери оформление приложения",color=MUTED,fontSize=10.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    items(Season.values().size){index->
                        val item=Season.values()[index]
                        FilterChip(
                            selected=item==season,
                            onClick={onSeason(item)},
                            label={Text(item.emoji+" "+item.title,fontSize=9.sp)},
                            colors=FilterChipDefaults.filterChipColors(selectedContainerColor=item.accent.copy(alpha=.18f),selectedLabelColor=item.accent)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(15.dp)){
                Text("ЯЗЫК",fontWeight=FontWeight.Black)
                languages.forEachIndexed{i,lang->
                    Row(Modifier.fillMaxWidth().clickable{onLanguage(i)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(i==language,{onLanguage(i)});Text(lang.name,fontWeight=if(i==language)FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp));AssistantSettings(assistant,onAssistant)
        Spacer(Modifier.height(8.dp));Text("Версия 2.1 • Gena • Weather • Music",color=MUTED,fontSize=10.sp,modifier=Modifier.padding(4.dp))
    }
}

@Composable
private fun SettingToggle(title:String,subtitle:String,checked:Boolean,onClick:()->Unit,accent:Color){
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Black);Text(subtitle,color=MUTED,fontSize=10.sp)}
            Switch(checked,{onClick()},colors=SwitchDefaults.colors(checkedThumbColor=accent))
        }
    }
}

@Composable
private fun AssistantSettings(selected:Int,onSelect:(Int)->Unit){
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(15.dp)){
            Text("ГОЛОСОВОЙ ШТУРМАН",fontWeight=FontWeight.Black)
            Text("Гена — единственный голос приложения.",color=MUTED,fontSize=10.sp)
            Row(Modifier.fillMaxWidth().clickable{onSelect(0)}.padding(top=7.dp),verticalAlignment=Alignment.CenterVertically){
                RadioButton(true,{onSelect(0)});Text("Гена",fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Text("мужской • спокойный",color=MUTED,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun SpecCard(title:String,value:String,tint:Color,modifier:Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=PANEL)){
        Column(Modifier.padding(11.dp)){Text(title,color=MUTED,fontSize=8.sp);Text(value,color=tint,fontSize=12.sp,fontWeight=FontWeight.Black)}
    }
}
