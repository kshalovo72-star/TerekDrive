package com.terekdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val BG=Color(0xFF07090C); private val PANEL=Color(0xFF10151B)
private val RED=Color(0xFFFF3B30); private val CYAN=Color(0xFF00D9FF); private val MUTED=Color(0xFF8995A3)
private data class Car(val name:String,val type:String,val hp:Int,val top:Int)
private data class Gauge(val name:String,val color:Color,val max:Int)
private val cars=listOf(
 Car("BMW M5","SPORT",730,305),Car("Mercedes G63","SUV",585,240),Car("Audi RS7","SPORT",600,305),
 Car("Toyota Supra","SPORT",387,250),Car("Lamborghini Huracán","SUPER",640,325),Car("Porsche 911","SPORT",650,320),
 Car("Range Rover SVR","SUV",575,283),Car("Ford Mustang","MUSCLE",480,290),Car("Lexus LX 570","SUV",383,220),Car("Nissan GT-R","SUPER",565,315))
private val gauges=listOf(
 Gauge("Классика",RED,300),Gauge("Спорт",Color(0xFFFF1744),320),Gauge("Будущее",CYAN,360),Gauge("Минимализм",Color.White,280),
 Gauge("Ночь",Color(0xFF4C9AFF),300),Gauge("Внедорожник",Color(0xFFFFB300),260),Gauge("Ретро",Color(0xFFE6D0A8),220),
 Gauge("Хром",Color(0xFFB8C2CC),300),Gauge("Неон",Color(0xFFB66CFF),340),Gauge("Матыч",Color(0xFF00FFA3),320))

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}
}
@Composable fun App(){
 var tab by remember{mutableIntStateOf(0)}; var car by remember{mutableIntStateOf(0)}; var gauge by remember{mutableIntStateOf(0)}; var nav by remember{mutableStateOf(false)}
 MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=PANEL,primary=RED,onBackground=Color.White,onSurface=Color.White)){
  Surface(Modifier.fillMaxSize(),color=BG){Column{Top();Box(Modifier.weight(1f)){when(tab){
   0->Map(nav){nav=!nav};1->Gauges(gauge){gauge=it};2->Garage(car){car=it}}};Bottom(tab){tab=it}}}}
}
@Composable fun Top(){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
 Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(RED),contentAlignment=Alignment.Center){Text("TD",fontWeight=FontWeight.Black)}
 Spacer(Modifier.width(12.dp));Column{Text("ТЕРЕК ДРАЙВ",fontWeight=FontWeight.Black,fontSize=19.sp,letterSpacing=1.3.sp);Text("ТВОЙ ПУТЬ. ТВОЙ РИТМ.",color=MUTED,fontSize=9.sp)}
 Spacer(Modifier.weight(1f));Icon(Icons.Default.Notifications,null)}} 
@Composable fun Map(nav:Boolean,toggle:()->Unit){Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(24.dp))){
 MapDraw(nav,Modifier.fillMaxSize());Search();Column(Modifier.align(Alignment.CenterEnd).padding(10.dp)){listOf("+","−","⌾").forEach{Round(it)}}
 Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF20B1015)).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(nav)"12 мин • 4,8 км"else"Грозный → Центр",fontSize=18.sp,fontWeight=FontWeight.Bold);Text(if(nav)"Поворот через 1,2 км"else"Самый быстрый маршрут",color=MUTED,fontSize=12.sp)};Icon(Icons.Default.Navigation,null,tint=RED)}
  Spacer(Modifier.height(10.dp));Button(toggle,Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=if(nav)Color(0xFF292D32)else RED)){Text(if(nav)"ЗАВЕРШИТЬ"else"НАЧАТЬ НАВИГАЦИЮ",fontWeight=FontWeight.Black)}
 }}}
@Composable fun Search(){Row(Modifier.fillMaxWidth(.88f).padding(top=12.dp).align(Alignment.TopCenter).clip(RoundedCornerShape(18.dp)).background(Color(0xEE151C24)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Search,null,tint=MUTED);Spacer(Modifier.width(10.dp));Text("Куда едем?",color=MUTED);Spacer(Modifier.weight(1f));Icon(Icons.Default.Mic,null,tint=CYAN)}}
@Composable fun Round(s:String){Box(Modifier.size(44.dp).padding(3.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xEE151C24)),contentAlignment=Alignment.Center){Text(s,fontSize=22.sp,fontWeight=FontWeight.Bold)}}
@Composable fun MapDraw(nav:Boolean,m:Modifier){val pulse by rememberInfiniteTransition(label="p").animateFloat(.7f,1.2f,infiniteRepeatable(tween(900),RepeatMode.Reverse),label="p");Canvas(m.background(Color(0xFF0D151A))){
 val w=size.width;val h=size.height;for(i in 0..9)drawLine(Color(0xFF1B2A2D),Offset(0f,h*i/10f),Offset(w,h*i/10f+h*.08f),1.5f)
 val p=Path().apply{moveTo(w*.08f,h*.78f);cubicTo(w*.28f,h*.62f,w*.26f,h*.52f,w*.43f,h*.49f);cubicTo(w*.61f,h*.45f,w*.59f,h*.32f,w*.86f,h*.18f)}
 drawPath(p,Color(0x5529B6F6),Stroke(18f,cap=StrokeCap.Round));drawPath(p,Color(0xFF1696FF),Stroke(7f,cap=StrokeCap.Round))
 val c=Offset(w*.43f,h*.49f);drawCircle(CYAN.copy(alpha=.18f),42f*pulse,c);drawCircle(CYAN,9f,c);drawCircle(Color.White,4f,c)
 if(nav){val e=Offset(w*.86f,h*.18f);drawCircle(RED,13f,e);drawCircle(Color.White,5f,e)}
}}
@Composable fun Gauges(sel:Int,pick:(Int)->Unit){Column(Modifier.fillMaxSize().padding(14.dp)){Text("10 СПИДОМЕТРОВ",fontSize=25.sp,fontWeight=FontWeight.Black);Text("Эффект, подсветка и живая стрелка",color=MUTED,fontSize=12.sp);Speed(gauges[sel],Modifier.fillMaxWidth().height(310.dp));Text("ТЕМЫ",fontWeight=FontWeight.Bold,color=MUTED,fontSize=12.sp);LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){itemsIndexed(gauges){i,g->Column(Modifier.width(92.dp).clickable{pick(i)}){Speed(g,Modifier.height(92.dp).fillMaxWidth(),true,i*17+62);Text((i+1).toString()+". "+g.name,fontSize=10.sp)}}}}}
@Composable fun Speed(g:Gauge,m:Modifier,compact:Boolean=false,demo:Int=120){val tr=rememberInfiniteTransition(label="needle");val f by tr.animateFloat(0f,1f,infiniteRepeatable(tween(1700,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="needle");val speed=if(compact)demo else(60+f*140).toInt();Canvas(m){
 val c=Offset(size.width/2,size.height/2);val r=minOf(size.width,size.height)*.39f;drawCircle(Color(0xFF0A0D11),r+16,c);drawCircle(Color(0xFF151A20),r,c)
 drawArc(g.color.copy(alpha=.18f),135f,270f,false,style=Stroke(24f));drawArc(g.color,135f,270f*speed/g.max,false,style=Stroke(10f))
 for(i in 0..30){val a=Math.toRadians((135+i*9).toDouble());val r1=r-5;val r2=r-(if(i%5==0)20 else 12);drawLine(g.color.copy(alpha=.7f),Offset(c.x+cos(a).toFloat()*r1,c.y+sin(a).toFloat()*r1),Offset(c.x+cos(a).toFloat()*r2,c.y+sin(a).toFloat()*r2),if(i%5==0)4f else 2f)}
 val a=Math.toRadians(135.0+270.0*speed/g.max);val n=Offset(c.x+cos(a).toFloat()*r*.75f,c.y+sin(a).toFloat()*r*.75f);drawLine(g.color,c,n,if(compact)3f else 7f,StrokeCap.Round);drawCircle(g.color,10f,c)
 if(!compact){drawContext.canvas.nativeCanvas.drawText(speed.toString(),c.x-58,c.y+25,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=68f;typeface=android.graphics.Typeface.DEFAULT_BOLD})}
}}
@Composable fun Garage(sel:Int,pick:(Int)->Unit){Column(Modifier.fillMaxSize().padding(14.dp)){Text("МОЙ ГАРАЖ",fontSize=25.sp,fontWeight=FontWeight.Black);Text("10 машин • выбирай свою",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){itemsIndexed(cars){i,c->Column(Modifier.width(150.dp).clip(RoundedCornerShape(20.dp)).background(if(i==sel)Color(0xFF211317)else PANEL).border(if(i==sel)2.dp else 1.dp,if(i==sel)RED else Color(0xFF252C34),RoundedCornerShape(20.dp)).clickable{pick(i)}.padding(12.dp)){Box(Modifier.fillMaxWidth().height(88.dp).background(Color(0xFF0A0E12)),contentAlignment=Alignment.Center){Text("🚘",fontSize=52.sp)};Spacer(Modifier.height(8.dp));Text(c.name,fontWeight=FontWeight.Bold,fontSize=13.sp);Text(c.hp.toString()+" л.с.",color=MUTED,fontSize=11.sp)}}};Spacer(Modifier.height(20.dp));val c=cars[sel];Text(c.name,fontSize=27.sp,fontWeight=FontWeight.Black);Text(c.type+" • "+c.hp+" л.с. • "+c.top+" км/ч",color=MUTED);Spacer(Modifier.height(18.dp));Button({},Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=RED)){Icon(Icons.Default.DirectionsCar,null);Spacer(Modifier.width(8.dp));Text("ВЫБРАТЬ АВТО",fontWeight=FontWeight.Black)}}}
@Composable fun Bottom(sel:Int,pick:(Int)->Unit){NavigationBar(containerColor=Color(0xFF090C10)){listOf(Icons.Default.Map to"Карта",Icons.Default.Speed to"Спидометр",Icons.Default.DirectionsCar to"Гараж").forEachIndexed{i,p->NavigationBarItem(sel==i,{pick(i)},{Icon(p.first,null)},{Text(p.second,fontSize=10.sp)})}}}
