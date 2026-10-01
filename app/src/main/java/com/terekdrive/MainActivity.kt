package com.terekdrive

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.content.pm.ActivityInfo
import java.util.Locale
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import kotlin.math.cos
import kotlin.math.sin

private const val STYLE_URL="https://tiles.openfreemap.org/styles/liberty"
private const val RELEASES_URL="https://github.com/kshalovo72-star/TerekDrive/releases"
private const val REMOTE_CONFIG_URL="https://raw.githubusercontent.com/kshalovo72-star/TerekDrive/main/remote-config.json"
private val BG=Color(0xFF07090C); private val PANEL=Color(0xFF10151B)
private val RED=Color(0xFFFF3B30); private val CYAN=Color(0xFF00D9FF)
private val GREEN=Color(0xFF00E5A0); private val MUTED=Color(0xFF8995A3)
private data class Assistant(val name:String,val pitch:Float,val rate:Float)
private val assistants=listOf(Assistant("Алина",1.08f,.98f),Assistant("Милана",1.18f,1.02f),Assistant("София",.94f,.96f),Assistant("Виктория",1.12f,1.08f),Assistant("Ева",.88f,1.00f))

private data class Car(val name:String,val type:String,val hp:Int,val top:Int)
private data class Gauge(val name:String,val color:Color,val max:Int)
private val cars=listOf(
 Car("BMW M5","SPORT",730,305),Car("Mercedes G63","SUV",585,240),Car("Audi RS7","SPORT",600,305),
 Car("Toyota Supra","SPORT",387,250),Car("Lamborghini Huracán","SUPER",640,325),Car("Porsche 911","SPORT",650,320),
 Car("Range Rover SVR","SUV",575,283),Car("Ford Mustang","MUSCLE",480,290),Car("Lexus LX 570","SUV",383,220),Car("Nissan GT-R","SUPER",565,315))
private val gauges=listOf(
 Gauge("Классика",RED,300),Gauge("Спорт",Color(0xFFFF1744),320),Gauge("Будущее",CYAN,360),Gauge("Минимализм",Color.White,280),
 Gauge("Ночь",Color(0xFF4C9AFF),300),Gauge("Внедорожник",Color(0xFFFFB300),260),Gauge("Ретро",Color(0xFFE6D0A8),220),
 Gauge("Хром",Color(0xFFB8C2CC),300),Gauge("Неон",Color(0xFFB66CFF),340),Gauge("Матыч",GREEN,320))

class MainActivity:ComponentActivity(){
 override fun onCreate(state:Bundle?){super.onCreate(state);requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER;setContent{TerekDrive()}}
}

@Composable private fun TerekDrive(){
 var tab by remember{mutableIntStateOf(0)}
 var sound by remember{mutableStateOf(true)}
 var animations by rememberSaveable{mutableStateOf(true)}
 var assistant by rememberSaveable{mutableIntStateOf(0)}
 MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=PANEL,primary=RED,onBackground=Color.White,onSurface=Color.White)){
  Surface(Modifier.fillMaxSize(),color=BG){Column{
   Header(sound){sound=!sound}
   Box(Modifier.weight(1f)){when(tab){
    0->MapScreen();1->DriveScreen(sound,animations,assistant);2->GarageScreen();3->MediaScreen(sound);else->SettingsScreen(sound,animations,assistant,{sound=!sound},{animations=!animations},{assistant=it})
   }}
   NavigationBar(containerColor=Color(0xFF090C10)){
    val items=listOf(Icons.Default.Map to"Карта",Icons.Default.Speed to"Драйв",Icons.Default.DirectionsCar to"Гараж",Icons.Default.MusicNote to"Медиа",Icons.Default.Settings to"Настройки")
    items.forEachIndexed{i,item->NavigationBarItem(tab==i,{tab=i},{Icon(item.first,null)},{Text(item.second,fontSize=9.sp)})}
   }
  }}
 }
}

@Composable private fun Header(sound:Boolean,onSound:()->Unit){
 Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
  Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(RED),contentAlignment=Alignment.Center){Text("TD",fontWeight=FontWeight.Black,fontSize=16.sp)}
  Spacer(Modifier.width(12.dp));Column{Text("ТЕРЕК ДРАЙВ",fontWeight=FontWeight.Black,fontSize=19.sp,letterSpacing=1.3.sp);Text("MAP • DRIVE • MUSIC",color=MUTED,fontSize=9.sp,letterSpacing=1.sp)}
  Spacer(Modifier.weight(1f));IconButton(onClick=onSound){Icon(if(sound)Icons.Default.VolumeUp else Icons.Default.VolumeOff,null,tint=if(sound)Color.White else MUTED)}
 }
}

@Composable private fun MapScreen(){
 var status by remember{mutableStateOf("Онлайн-карта готова")}
 val context=LocalContext.current
 Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(24.dp))){
  AndroidView(Modifier.fillMaxSize(),factory={ctx->
   MapView(ctx).also{v->v.onCreate(null);v.onStart();v.onResume();v.getMapAsync{map->
    map.setStyle(STYLE_URL);map.cameraPosition=CameraPosition.Builder().target(LatLng(43.3178,45.6985)).zoom(11.0).build()
   }}
  })
  Column(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopCenter)){
   Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xEE111820)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.Map,null,tint=CYAN);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("MAPLIBRE • OPENFREEMAP",fontWeight=FontWeight.Black,fontSize=12.sp);Text(status,color=MUTED,fontSize=10.sp)};Text("OFFLINE",color=GREEN,fontWeight=FontWeight.Black,fontSize=10.sp)
   }
   Spacer(Modifier.height(8.dp))
   Button({status="Скачивание офлайн-карты…";downloadOffline(context){status=it}},Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xEE18242B))){
    Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("СКАЧАТЬ ГРОЗНЫЙ ДЛЯ OFFLINE")
   }
  }
  Row(Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xF20B1015)).padding(16.dp),verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){Text("OpenStreetMap + OpenFreeMap",fontWeight=FontWeight.Bold);Text("Без 2ГИС-ключа • офлайн-пакет хранится на телефоне",color=MUTED,fontSize=11.sp)};Icon(Icons.Default.WifiOff,null,tint=GREEN)
  }
 }
}

private fun downloadOffline(context:android.content.Context,done:(String)->Unit){
 val bounds=LatLngBounds.from(43.55,45.90,43.10,45.45)
 val definition=OfflineTilePyramidRegionDefinition(STYLE_URL,bounds,8.0,14.0,1f)
 OfflineManager.getInstance(context).createOfflineRegion(definition,"TerekDrive-Grozny".toByteArray(),object:OfflineManager.CreateOfflineRegionCallback{
  override fun onCreate(region:OfflineRegion){region.setDownloadState(OfflineRegion.STATE_ACTIVE);done("Офлайн-загрузка запущена")}
  override fun onError(error:String){done("Ошибка: $error")}
 })
}

@Composable private fun DriveScreen(sound:Boolean,animations:Boolean,assistant:Int){
 var gauge by remember{mutableIntStateOf(0)};var speed by remember{mutableIntStateOf(0)}
 var running by remember{mutableStateOf(false)};var started by remember{mutableLongStateOf(0L)};var elapsed by remember{mutableLongStateOf(0L)}
 val context=LocalContext.current
 val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){}
 DisposableEffect(Unit){
  val lm=context.getSystemService(LocationManager::class.java)
  val listener=object:LocationListener{override fun onLocationChanged(location:Location){speed=(location.speed*3.6f).toInt().coerceAtLeast(0)}}
  if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED)runCatching{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,500L,1f,listener)}
  onDispose{runCatching{lm.removeUpdates(listener)}}
 }
 LaunchedEffect(running){while(running){elapsed=SystemClock.elapsedRealtime()-started;kotlinx.coroutines.delay(50)}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
  Text("DRIVE LAB",fontSize=25.sp,fontWeight=FontWeight.Black);Text("10 тем • GPS скорость • секундомер",color=MUTED,fontSize=12.sp)
  Spacer(Modifier.height(8.dp));RoadAnimation(speed,animations,Modifier.fillMaxWidth().height(72.dp));Spacer(Modifier.height(8.dp));SpeedGauge(gauges[gauge],speed.coerceIn(0,gauges[gauge].max),Modifier.fillMaxWidth().height(280.dp))
  LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){itemsIndexed(gauges){i,g->Box(Modifier.width(100.dp).clip(RoundedCornerShape(14.dp)).background(if(i==gauge)g.color.copy(alpha=.18f)else PANEL).clickable{gauge=i}.padding(10.dp)){Text((i+1).toString()+". "+g.name,fontSize=10.sp,fontWeight=FontWeight.Bold)}}}
  Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()){
   Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=PANEL)){Column(Modifier.padding(16.dp)){Text(speed.toString(),fontSize=34.sp,fontWeight=FontWeight.Black,color=CYAN);Text("км/ч • GPS",color=MUTED,fontSize=11.sp)}}
   Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=PANEL)){Column(Modifier.padding(16.dp)){val sec=elapsed/1000;Text(String.format("%02d:%02d.%02d",sec/60,sec%60,(elapsed%1000)/10),fontSize=24.sp,fontWeight=FontWeight.Black,color=GREEN);Text("секундомер",color=MUTED,fontSize=11.sp)}}
  }
  Spacer(Modifier.height(10.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
   Button({if(!running)started=SystemClock.elapsedRealtime()-elapsed;running=!running},Modifier.weight(1f),shape=RoundedCornerShape(15.dp)){Text(if(running)"ПАУЗА" else "СТАРТ")}
   OutlinedButton({running=false;elapsed=0},Modifier.weight(1f),shape=RoundedCornerShape(15.dp)){Text("СБРОС")}
  }
  Spacer(Modifier.height(8.dp));Button({permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))},Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){Icon(Icons.Default.GpsFixed,null);Spacer(Modifier.width(8.dp));Text("ВКЛЮЧИТЬ GPS-СКОРОСТЬ")}
  Text(if(sound)"🔊 сигналы включены" else "🔇 сигналы выключены",color=MUTED,fontSize=10.sp,modifier=Modifier.padding(top=6.dp));AssistantPanel(sound,assistant)
 }
}

@Composable private fun SpeedGauge(g:Gauge,speed:Int,m:Modifier){
 val pulse by rememberInfiniteTransition(label="g").animateFloat(.85f,1.08f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="pulse")
 Canvas(m){
  val c=Offset(size.width/2,size.height/2);val r=minOf(size.width,size.height)*.36f
  drawCircle(Color(0xFF0A0D11),r+20,c);drawCircle(Color(0xFF151A20),r,c);drawCircle(g.color.copy(alpha=.08f*pulse),r+12,c)
  drawArc(g.color.copy(alpha=.18f),135f,270f,false,style=Stroke(22f));drawArc(g.color,135f,270f*speed/g.max,false,style=Stroke(9f))
  for(i in 0..30){val a=Math.toRadians((135+i*9).toDouble());val r1=r-5;val r2=r-(if(i%5==0)19 else 11);drawLine(g.color.copy(alpha=.7f),Offset(c.x+cos(a).toFloat()*r1,c.y+sin(a).toFloat()*r1),Offset(c.x+cos(a).toFloat()*r2,c.y+sin(a).toFloat()*r2),if(i%5==0)4f else 2f)}
  val a=Math.toRadians(135.0+270.0*speed/g.max);val n=Offset(c.x+cos(a).toFloat()*r*.76f,c.y+sin(a).toFloat()*r*.76f);drawLine(g.color,c,n,7f,StrokeCap.Round);drawCircle(g.color,10f,c)
  drawContext.canvas.nativeCanvas.drawText(speed.toString(),c.x-45,c.y+25,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=54f;typeface=android.graphics.Typeface.DEFAULT_BOLD})
 }
}

@Composable private fun GarageScreen(){
 var selected by remember{mutableIntStateOf(0)};val car=cars[selected]
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
  Text("МОЙ ГАРАЖ",fontSize=25.sp,fontWeight=FontWeight.Black);Text("10 машин • живой выбор",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
  LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(cars){i,c->Column(Modifier.width(150.dp).clip(RoundedCornerShape(20.dp)).background(if(i==selected)Color(0xFF211317)else PANEL).border(1.dp,if(i==selected)RED else Color(0xFF252C34),RoundedCornerShape(20.dp)).clickable{selected=i}.padding(12.dp)){
   Box(Modifier.fillMaxWidth().height(88.dp).background(Color(0xFF0A0E12)),contentAlignment=Alignment.Center){Text("🚘",fontSize=52.sp)};Spacer(Modifier.height(8.dp));Text(c.name,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(c.hp.toString()+" л.с. • "+c.top+" км/ч",color=MUTED,fontSize=10.sp)
  }}}
  Spacer(Modifier.height(18.dp));Text(car.name,fontSize=28.sp,fontWeight=FontWeight.Black);Text(car.type+" • "+car.hp+" л.с. • "+car.top+" км/ч",color=MUTED)
 }
}

@Composable private fun MediaScreen(sound:Boolean){
 var player by remember{mutableStateOf<MediaPlayer?>(null)};var title by remember{mutableStateOf("Музыка не выбрана")}
 var weather by remember{mutableStateOf("Нажми «Обновить»")};var loading by remember{mutableStateOf(false)}
 val context=LocalContext.current
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->uri?.let{player?.release();player=MediaPlayer.create(context,it);title=it.lastPathSegment?:"Трек";if(sound)ToneGenerator(AudioManager.STREAM_NOTIFICATION,80).startTone(ToneGenerator.TONE_PROP_BEEP,120)}}
 DisposableEffect(Unit){onDispose{player?.release()}}
 LaunchedEffect(loading){if(loading){weather=withContext(Dispatchers.IO){runCatching{
  val c=URL("https://api.open-meteo.com/v1/forecast?latitude=43.3178&longitude=45.6985&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto").openConnection() as HttpURLConnection
  c.connectTimeout=7000;c.readTimeout=7000;val body=c.inputStream.bufferedReader().use{it.readText()};c.disconnect()
  val temp=Regex("\\"temperature_2m\\"\\s*:\\s*(-?[0-9.]+)").find(body)?.groupValues?.get(1)?:"?"
  val wind=Regex("\\"wind_speed_10m\\"\\s*:\\s*([0-9.]+)").find(body)?.groupValues?.get(1)?:"?"
  "Грозный • "+temp+"°C • ветер "+wind+" км/ч"
 }.getOrElse{"Погода пока недоступна"}};loading=false}}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
  Text("МЕДИА",fontSize=25.sp,fontWeight=FontWeight.Black);Text("музыка • погода • таймеры",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
  Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.MusicNote,null,tint=RED);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("ПЛЕЕР",fontWeight=FontWeight.Black);Text(title,color=MUTED,fontSize=11.sp)};IconButton({player?.let{if(it.isPlaying)it.pause() else it.start()}}){Icon(Icons.Default.PlayArrow,null)}}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({picker.launch("audio/*")}){Icon(Icons.Default.LibraryMusic,null);Spacer(Modifier.width(6.dp));Text("ВЫБРАТЬ ТРЕК")};OutlinedButton({player?.seekTo(0);player?.pause()}){Text("СТОП")}}
  }}
  Spacer(Modifier.height(10.dp));Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Default.Cloud,null,tint=CYAN);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("ПОГОДА • ГРОЗНЫЙ",fontWeight=FontWeight.Black);Text(weather,color=MUTED,fontSize=11.sp)};IconButton({loading=true}){Icon(Icons.Default.Refresh,null)}
  }}
  Spacer(Modifier.height(10.dp));Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("ЗВУК",fontWeight=FontWeight.Black);Text(if(sound)"Системные сигналы включены" else "Звук выключен",color=MUTED,fontSize=11.sp)}}
 }
}

@Composable private fun SettingsScreen(sound:Boolean,animations:Boolean,assistant:Int,toggleSound:()->Unit,toggleAnimations:()->Unit,setAssistant:(Int)->Unit){
 var remote by remember{mutableStateOf("Проверить удалённую конфигурацию")};val context=LocalContext.current
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
  Text("НАСТРОЙКИ",fontSize=25.sp,fontWeight=FontWeight.Black);Text("всё под контролем",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
  SettingRow("🔊","Звук","Сигналы, подсказки и клики",sound,toggleSound);SettingRow("✨","Анимации","Пульсация, стрелки, переходы",animations,toggleAnimations);Spacer(Modifier.height(8.dp));AssistantSettings(assistant,setAssistant)
  Spacer(Modifier.height(8.dp));Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){
   Text("ДИСТАНЦИОННЫЕ ОБНОВЛЕНИЯ",fontWeight=FontWeight.Black);Text(remote,color=MUTED,fontSize=11.sp);Spacer(Modifier.height(8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({remote="Проверяю…";Thread{remote=checkRemote()}.start()}){Icon(Icons.Default.CloudDownload,null);Spacer(Modifier.width(6.dp));Text("ПРОВЕРИТЬ")};OutlinedButton({context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(RELEASES_URL)))}){Text("РЕЛИЗЫ")}}
  }}
  Spacer(Modifier.height(8.dp));Text("Контент и конфиг можно менять удалённо без пересборки APK. Для новой версии кода GitHub Actions автоматически собирает APK.",color=MUTED,fontSize=10.sp)
 }
}

@Composable
private fun RoadAnimation(speed:Int,enabled:Boolean,modifier:Modifier){
 val shift by rememberInfiniteTransition(label="road").animateFloat(0f,1f,infiniteRepeatable(tween((1100-speed*4).coerceAtLeast(280)),RepeatMode.Restart),label="shift")
 Canvas(modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF080B0F))){
  val mid=size.width/2
  if(enabled) for(i in 0..10){
   val y=((i*90f)+(shift*90f))%(size.height+90f)-45f
   val width=4f+(y/size.height)*10f
   drawRoundRect(Color(0xFF4D5660),Offset(mid-width/2,y),androidx.compose.ui.geometry.Size(width,12f),6f,6f)
  }
  drawLine(CYAN,Offset(0f,size.height*.82f),Offset(size.width*.22f,size.height*.55f),3f)
  drawLine(CYAN,Offset(size.width,size.height*.82f),Offset(size.width*.78f,size.height*.55f),3f)
  drawCircle(RED,9f,Offset(mid,size.height*.82f))
 }
}

@Composable
private fun AssistantPanel(sound:Boolean,assistantIndex:Int){
 var selected by remember(assistantIndex){mutableIntStateOf(assistantIndex)}
 val context=LocalContext.current
 var tts by remember{mutableStateOf<TextToSpeech?>(null)}
 DisposableEffect(Unit){
  var engine:TextToSpeech?=null
  engine=TextToSpeech(context){status->if(status==TextToSpeech.SUCCESS)engine?.let{applyVoice(it,assistants[selected])}}
  tts=engine
  onDispose{engine?.stop();engine?.shutdown()}
 }
 val greeting=timeGreeting()
 LaunchedEffect(tts,sound){
  if(sound && tts!=null){
   val hello=greeting+", водитель. Я "+assistants[selected].name+". Хорошей дороги!"
   tts?.let{applyVoice(it,assistants[selected]);it.speak(hello,TextToSpeech.QUEUE_FLUSH,null,"auto_greeting")}
  }
 }
 Column{
  Spacer(Modifier.height(10.dp));Text("ГОЛОСОВОЙ ШТУРМАН",fontWeight=FontWeight.Black,fontSize=15.sp)
  Text("5 женских профилей • $greeting",color=MUTED,fontSize=10.sp)
  LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){itemsIndexed(assistants){i,a->
   Box(Modifier.width(112.dp).clip(RoundedCornerShape(14.dp)).background(if(i==selected)RED.copy(alpha=.18f)else PANEL).clickable{selected=i;tts?.let{applyVoice(it,a)}}.padding(11.dp)){
    Text(a.name,fontWeight=FontWeight.Bold,fontSize=12.sp);Text("голос ${i+1}",color=MUTED,fontSize=9.sp)
   }
  }}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){
   Button({if(sound)tts?.let{applyVoice(it,assistants[selected]);it.speak("$greeting, водитель. Хорошей дороги!",TextToSpeech.QUEUE_FLUSH,null,"greeting")}},Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.RecordVoiceOver,null);Spacer(Modifier.width(6.dp));Text("ПРИВЕТСТВИЕ")}
   OutlinedButton({if(sound)tts?.speak("Впереди спокойный участок. Соблюдайте скорость и следите за дорогой.",TextToSpeech.QUEUE_FLUSH,null,"hint")},Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("ПОДСКАЗКА")}
  }
 }
}

private fun applyVoice(tts:TextToSpeech,profile:Assistant){
 tts.language=Locale("ru","RU")
 val voices=tts.voices?.filter{it.locale.language=="ru"}.orEmpty()
 if(voices.isNotEmpty())tts.voice=voices[(profile.name.hashCode().and(Int.MAX_VALUE))%voices.size]
 tts.setPitch(profile.pitch);tts.setSpeechRate(profile.rate)
}
private fun timeGreeting():String{
 val h=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
 return when(h){in 5..11->"Доброе утро";in 12..17->"Добрый день";in 18..22->"Добрый вечер";else->"Доброй ночи"}
}
@Composable
private fun AssistantSettings(selected:Int,onSelect:(Int)->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(16.dp)){
   Text("ГОЛОСОВЫЕ ПОМОЩНИКИ",fontWeight=FontWeight.Black)
   Text("5 профилей. Реальные доступные голоса зависят от TTS-движка телефона.",color=MUTED,fontSize=10.sp)
   assistants.forEachIndexed{i,a->Row(Modifier.fillMaxWidth().clickable{onSelect(i)}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
    RadioButton(selected==i,{onSelect(i)});Text(a.name,fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Text("тембр ${a.pitch} • скорость ${a.rate}",color=MUTED,fontSize=9.sp)
   }}
  }
 }
}
@Composable private fun SettingRow(icon:String,title:String,subtitle:String,value:Boolean,onChange:()->Unit){
 Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)){Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically){
  Text(icon,fontSize=22.sp);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,color=MUTED,fontSize=10.sp)};Switch(value,onChange)
 }}
}

private fun checkRemote():String{
 return runCatching{val c=URL(REMOTE_CONFIG_URL).openConnection() as HttpURLConnection;c.connectTimeout=5000;c.readTimeout=5000;val body=c.inputStream.bufferedReader().use{it.readText()};c.disconnect();Regex("\\"message\\"\\s*:\\s*\\"([^\\"]+)\\"").find(body)?.groupValues?.get(1)?:"Конфигурация обновлена"}.getOrElse{"Нет сети — локальные настройки сохранены"}
}
