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
import androidx.compose.animation.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
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
private data class Language(val name:String,val tag:String)
private val languages=listOf(Language("Русский","ru"),Language("English","en"),Language("Deutsch","de"),Language("Français","fr"),Language("Español","es"))
private data class Assistant(val name:String,val pitch:Float,val rate:Float)
private val gena=Assistant("Гена",0.96f,1.02f)

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
 val assistant=0
 var language by rememberSaveable{mutableIntStateOf(0)}
 var splash by rememberSaveable{mutableStateOf(true)}
 LaunchedEffect(Unit){kotlinx.coroutines.delay(1800);splash=false}
 MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=PANEL,primary=RED,onBackground=Color.White,onSurface=Color.White)){
  Surface(Modifier.fillMaxSize(),color=BG){
  AnimatedContent(targetState=splash,transitionSpec={fadeIn(animationSpec=tween(450))+scaleIn(initialScale=.88f,animationSpec=tween(650)) togetherWith fadeOut(animationSpec=tween(300))},label="startup",content={showSplash->if(showSplash) SplashScreen() else Column{
   Header(sound){sound=!sound}
   Box(Modifier.weight(1f)){when(tab){
    0->MapScreen();1->DriveScreen(sound,animations,assistant,language);2->GarageScreen();3->MediaScreen(sound);else->SettingsScreen(sound,animations,assistant,language,{sound=!sound},{animations=!animations},{assistant=it},{language=it})
   }}
   NavigationBar(containerColor=Color(0xFF090C10)){
    val items=listOf(Icons.Default.Map to "Карта",Icons.Default.Speed to "Драйв",Icons.Default.DirectionsCar to "Гараж",Icons.Default.MusicNote to "Медиа",Icons.Default.Settings to "Настройки")
    items.forEachIndexed { i,item -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(item.first,null)},label={Text(item.second,fontSize=9.sp)}) }
   }
   }})
 }
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
  AndroidView(factory={ctx->
   MapView(ctx).also{v->v.onCreate(null);v.onStart();v.onResume();v.getMapAsync{map->
    map.setStyle(STYLE_URL);map.cameraPosition=CameraPosition.Builder().target(LatLng(43.3178,45.6985)).zoom(11.0).build()
   }}
  },onRelease={v->v.onPause();v.onStop();v.onDestroy()})
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

@Composable private fun DriveScreen(sound:Boolean,animations:Boolean,assistant:Int,language:Int){
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
  Spacer(Modifier.height(8.dp));RoadAnimation(speed,animations,Modifier.fillMaxWidth().height(72.dp));Spacer(Modifier.height(8.dp));RoadAnimation(speed,animations,Modifier.fillMaxWidth().height(72.dp));Spacer(Modifier.height(8.dp));SpeedGauge(gauges[gauge],speed.coerceIn(0,gauges[gauge].max),Modifier.fillMaxWidth().height(280.dp))
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
  NavigationPlanner(language,sound)
  Text(if(sound)"🔊 сигналы включены" else "🔇 сигналы выключены",color=MUTED,fontSize=10.sp,modifier=Modifier.padding(top=6.dp));AssistantPanel(sound,assistant,language)
 }
}

private data class Maneuver(val instruction:String,val distance:Int,val icon:String)
private data class RouteResult(val distanceKm:Double,val durationMin:Int,val maneuvers:List<Maneuver>)
@Composable private fun NavigationPlanner(language:Int,sound:Boolean){
 var destination by rememberSaveable{mutableStateOf("")};var loading by remember{mutableStateOf(false)};var route by remember{mutableStateOf<RouteResult?>(null)};var error by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){
  Column(Modifier.padding(15.dp)){
   Text("ПОЛНОЦЕННАЯ НАВИГАЦИЯ",fontWeight=FontWeight.Black,fontSize=15.sp);Text("точка назначения • маршрут • манёвры",color=MUTED,fontSize=10.sp)
   Spacer(Modifier.height(8.dp))
   OutlinedTextField(value=destination,onValueChange={destination=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Куда едем?")},placeholder={Text("Например: аэропорт Грозного")})
   Spacer(Modifier.height(7.dp))
   Button({if(destination.isNotBlank()){loading=true;error="";scope.launch{val result=withContext(Dispatchers.IO){buildRoute(destination)};route=result;loading=false;if(result==null)error="Маршрут не найден или нет сети."}}},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Route,null);Spacer(Modifier.width(7.dp));Text(if(loading)"СТРОЮ МАРШРУТ…" else "ПОСТРОИТЬ МАРШРУТ")}
   if(error.isNotBlank())Text(error,color=RED,fontSize=10.sp)
   route?.let{r->Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){SpecCard("ДИСТАНЦИЯ",String.format("%.1f км",r.distanceKm),CYAN,Modifier.weight(1f));SpecCard("ВРЕМЯ",r.durationMin.toString()+" мин",GREEN,Modifier.weight(1f));SpecCard("ШАГИ",r.maneuvers.size.toString(),RED,Modifier.weight(1f))}
    Spacer(Modifier.height(7.dp));Text("МАНЁВРЫ",fontWeight=FontWeight.Black,fontSize=11.sp)
    r.maneuvers.take(8).forEach{m->Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Text(m.icon,fontSize=18.sp);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(m.instruction,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(if(m.distance<1000)m.distance.toString()+" м" else String.format("%.1f км",m.distance/1000.0),color=MUTED,fontSize=9.sp)}}}
   }
  }
 }
}
private fun buildRoute(query:String):RouteResult?{
 return runCatching{
  val gConn=URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q="+Uri.encode(query)).openConnection() as HttpURLConnection
  gConn.setRequestProperty("User-Agent","TerekDrive/1.1");gConn.connectTimeout=7000;gConn.readTimeout=7000
  val g=JSONArray(gConn.inputStream.bufferedReader().use{it.readText()});gConn.disconnect();if(g.length()==0)return null
  val lat=g.getJSONObject(0).getDouble("lat");val lon=g.getJSONObject(0).getDouble("lon")
  val c=URL("https://router.project-osrm.org/route/v1/driving/45.6985,43.3178;$lon,$lat?overview=false&steps=true").openConnection() as HttpURLConnection
  c.setRequestProperty("User-Agent","TerekDrive/1.1");c.connectTimeout=8000;c.readTimeout=8000
  val root=JSONObject(c.inputStream.bufferedReader().use{it.readText()});c.disconnect();val rr=root.getJSONArray("routes").getJSONObject(0)
  val steps=rr.getJSONArray("legs").getJSONObject(0).getJSONArray("steps");val list=mutableListOf<Maneuver>()
  for(i in 0 until steps.length()){val st=steps.getJSONObject(i);val m=st.getJSONObject("maneuver");val type=m.optString("type");val mod=m.optString("modifier");val dist=st.optDouble("distance",0.0).toInt();if(dist>0){val icon=when(mod){"left"->"←";"right"->"→";"slight left"->"↖";"slight right"->"↗";"straight"->"↑";else->"●"};val text=when(type){"depart"->"Начало движения";"arrive"->"Прибытие";"roundabout"->"Круговое движение";"turn"->"Поворот "+when(mod){"left"->"налево";"right"->"направо";else->"прямо"};else->"Продолжайте движение"};list.add(Maneuver(text,dist,icon))}}
  RouteResult(rr.getDouble("distance")/1000.0,(rr.getDouble("duration")/60.0).toInt(),list)
 }.getOrNull()
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
 var selected by rememberSaveable{mutableIntStateOf(0)};val car=cars[selected]
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)){
  Text("МОЙ ГАРАЖ",fontSize=25.sp,fontWeight=FontWeight.Black);Text("10 машин • характеристики • режимы",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
  Card(colors=CardDefaults.cardColors(containerColor=PANEL),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){
   Text(carEmoji(car.type),fontSize=72.sp);Spacer(Modifier.width(14.dp));Column{Text(car.name,fontSize=24.sp,fontWeight=FontWeight.Black);Text(car.type,color=RED,fontWeight=FontWeight.Bold);Text("Подготовлена для DRIVE режима",color=MUTED,fontSize=10.sp)}
  }}
  Spacer(Modifier.height(12.dp))
  LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(cars){i,c->Column(Modifier.width(155.dp).clip(RoundedCornerShape(20.dp)).background(if(i==selected)Color(0xFF211317)else PANEL).border(1.dp,if(i==selected)RED else Color(0xFF252C34),RoundedCornerShape(20.dp)).clickable{selected=i}.padding(12.dp)){
   Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF0A0E12)),contentAlignment=Alignment.Center){Text(carEmoji(c.type),fontSize=54.sp)}
   Spacer(Modifier.height(8.dp));Text(c.name,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(c.hp.toString()+" л.с. • "+c.top+" км/ч",color=MUTED,fontSize=10.sp)
  }}}
  Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
   SpecCard("МОЩНОСТЬ",car.hp.toString()+" л.с.",RED,Modifier.weight(1f));SpecCard("МАКС.",car.top.toString()+" км/ч",CYAN,Modifier.weight(1f));SpecCard("КЛАСС",car.type,GREEN,Modifier.weight(1f))
  }
 }
}
private fun carEmoji(type:String)=when(type){"SUV"->"🚙";"SUPER"->"🏎️";"MUSCLE"->"🚗";else->"🏎️"}
@Composable private fun SpecCard(title:String,value:String,tint:Color,modifier:Modifier){
 Card(modifier,colors=CardDefaults.cardColors(containerColor=PANEL)){Column(Modifier.padding(12.dp)){Text(title,color=MUTED,fontSize=8.sp);Text(value,color=tint,fontSize=13.sp,fontWeight=FontWeight.Black)}}
}

