import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:maplibre_gl/maplibre_gl.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:shared_preferences/shared_preferences.dart';

const bg = Color(0xFF07090C);
const panel = Color(0xFF10151B);
const red = Color(0xFFFF3B30);
const cyan = Color(0xFF00D9FF);

void main() => runApp(const MyApp());

class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: 'Терек Драйв',
    theme: ThemeData.dark(useMaterial3: true).copyWith(
      scaffoldBackgroundColor: bg,
      colorScheme: const ColorScheme.dark(primary: red, secondary: cyan),
      cardTheme: CardThemeData(color: panel, elevation: 0, margin: EdgeInsets.zero),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: const Color(0xFF080B0F),
        indicatorColor: red.withValues(alpha: .18),
        labelTextStyle: WidgetStateProperty.all(const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true, fillColor: const Color(0xFF121923),
        hintStyle: const TextStyle(color: Colors.white38),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: BorderSide.none),
      ),
    ),
    home: const DriveShell(),
  );
}

class DriveShell extends StatefulWidget {
  const DriveShell({super.key});
  @override
  State<DriveShell> createState() => _DriveShellState();
}

class _DriveShellState extends State<DriveShell> {
  int tab = 0;
  Position? position;
  StreamSubscription<Position>? gps;
  bool sound = true;
  Season season = _seasonForDate(DateTime.now());
  GaugeStyle gaugeStyle = GaugeStyle.neon;
  final tts = FlutterTts();

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      sound = p.getBool('sound') ?? true;
      final savedSeason = p.getInt('season');
      season = savedSeason == null ? _seasonForDate(DateTime.now()) : Season.values[math.min(savedSeason, Season.values.length - 1)];
      final savedGauge = p.getInt('gauge') ?? GaugeStyle.neon.index;
      gaugeStyle = GaugeStyle.values[math.min(savedGauge, GaugeStyle.values.length - 1)];
    });
  }

  Future<void> startGps() async {
    if (!await Geolocator.isLocationServiceEnabled()) return;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) return;
    await gps?.cancel();
    gps = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.best, distanceFilter: 2),
    ).listen((p) { if (mounted) setState(() => position = p); });
  }

  Future<void> toggleSound() async {
    final p = await SharedPreferences.getInstance();
    setState(() => sound = !sound);
    await p.setBool('sound', sound);
  }

  Future<void> setGauge(GaugeStyle value) async {
    final p = await SharedPreferences.getInstance();
    setState(() => gaugeStyle = value);
    await p.setInt('gauge', value.index);
  }

  Future<void> setSeason(Season value) async {
    final p = await SharedPreferences.getInstance();
    setState(() => season = value);
    await p.setInt('season', value.index);
  }

  @override
  void dispose() { gps?.cancel(); tts.stop(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final pages = [
      MapPage(position: position, season: season, onLocate: startGps),
      NavigationPage(position: position, tts: tts, sound: sound),
      SpeedPage(position: position, onStart: startGps, gaugeStyle: gaugeStyle),
      SettingsPage(sound: sound, season: season, gaugeStyle: gaugeStyle, onSound: toggleSound, onSeason: setSeason, onGauge: setGauge),
    ];
    return Scaffold(
      body: Stack(children: [
        SafeArea(child: pages[tab]),
        Positioned.fill(child: IgnorePointer(child: SeasonalEffects(season: season))),
      ]),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() => tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.map_outlined), label: 'Карта'),
          NavigationDestination(icon: Icon(Icons.navigation_outlined), label: 'Навигация'),
          NavigationDestination(icon: Icon(Icons.speed_outlined), label: 'Скорость'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'Настройки'),
        ],
      ),
    );
  }
}

class MapPage extends StatefulWidget {
  final Position? position;
  final Season season;
  final VoidCallback onLocate;
  const MapPage({super.key, this.position, required this.season, required this.onLocate});
  @override State<MapPage> createState() => _MapPageState();
}

class _MapPageState extends State<MapPage> {
  MapLibreMapController? controller;
  static const home = LatLng(43.3178, 45.6985);

  @override
  Widget build(BuildContext context) => Stack(children: [
    MapLibreMap(
      styleString: 'https://tiles.openfreemap.org/styles/liberty',
      initialCameraPosition: const CameraPosition(target: home, zoom: 10),
      myLocationEnabled: true,
      onMapCreated: (c) => controller = c,
    ),
    const Positioned(top: 12, left: 12, right: 12, child: Header('ТЕРЕК ДРАЙВ', 'MAP / FLUTTER ENGINE')),
    Positioned(
      bottom: 18, right: 16,
      child: FloatingActionButton(
        backgroundColor: red,
        onPressed: () {
          widget.onLocate();
          final p = widget.position;
          if (p != null) controller?.animateCamera(CameraUpdate.newLatLngZoom(LatLng(p.latitude, p.longitude), 14));
        },
        child: const Icon(Icons.my_location),
      ),
    ),
  ]);
}

class NavigationPage extends StatefulWidget {
  final Position? position;
  final FlutterTts tts;
  final bool sound;
  const NavigationPage({super.key, this.position, required this.tts, required this.sound});
  @override State<NavigationPage> createState() => _NavigationPageState();
}

class _NavigationPageState extends State<NavigationPage> {
  final query = TextEditingController();
  String message = 'Введите адрес или город';

  @override void dispose() { query.dispose(); super.dispose(); }

  Future<void> speak(String text) async {
    if (!widget.sound) return;
    await widget.tts.setLanguage('ru-RU');
    await widget.tts.setSpeechRate(.48);
    await widget.tts.speak(text);
  }

  @override
  Widget build(BuildContext context) => Column(children: [
    const Padding(padding: EdgeInsets.fromLTRB(16, 16, 16, 8), child: Header('НАВИГАЦИЯ', 'LIVE / ГЕНА')),
    Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Card(
        color: panel,
        child: TextField(
          controller: query,
          onSubmitted: (v) {
            final text = v.trim();
            if (text.isNotEmpty) {
              setState(() => message = 'Маршрут к «$text» готовится');
              speak('Маршрут к $text');
            }
          },
          decoration: const InputDecoration(
            hintText: 'Куда едем?',
            prefixIcon: Icon(Icons.search, color: cyan),
            border: InputBorder.none,
          ),
        ),
      ),
    ),
    Expanded(
      child: Center(
        child: Card(
          color: panel,
          margin: const EdgeInsets.all(16),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.navigation, size: 64, color: cyan),
              const SizedBox(height: 12),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton.icon(
                onPressed: () => speak('Гена готов. Куда едем?'),
                icon: const Icon(Icons.record_voice_over),
                label: const Text('ГЕНА'),
              ),
            ]),
          ),
        ),
      ),
    ),
  ]);
}

class SpeedPage extends StatelessWidget {
  final Position? position;
  final VoidCallback onStart;
  final GaugeStyle gaugeStyle;
  const SpeedPage({super.key, this.position, required this.onStart, required this.gaugeStyle});

  @override
  Widget build(BuildContext context) {
    final speed = position == null ? 0.0 : math.max(0.0, position!.speed * 3.6);
    return Column(children: [
      const Padding(padding: EdgeInsets.all(16), child: Header('СКОРОСТЬ', 'GPS / 0—300 KM/H')),
      Expanded(
        child: Center(
          child: CustomPaint(
            size: const Size(300, 300),
            painter: GaugePainter(speed, gaugeStyle),
            child: SizedBox(width: 300, height: 300, child: Center(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text(speed.toStringAsFixed(0), style: const TextStyle(fontSize: 72, fontWeight: FontWeight.w900)),
                const Text('KM/H', style: TextStyle(color: cyan)),
              ]),
            )),
          ),
        ),
      ),
      Padding(padding: const EdgeInsets.all(20), child: FilledButton.icon(
        onPressed: onStart, icon: const Icon(Icons.gps_fixed), label: const Text('ВКЛЮЧИТЬ GPS'),
      )),
    ]);
  }
}

class SettingsPage extends StatelessWidget {
  final bool sound;
  final Season season;
  final GaugeStyle gaugeStyle;
  final ValueChanged<Season> onSeason;
  final ValueChanged<GaugeStyle> onGauge;
  final VoidCallback onSound;
  const SettingsPage({super.key, required this.sound, required this.season, required this.gaugeStyle, required this.onSeason, required this.onGauge, required this.onSound});

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [
    const Header('НАСТРОЙКИ', 'TEREK DRIVE'),
    SwitchListTile(value: sound, onChanged: (_) => onSound(), title: const Text('Голос Гены'), subtitle: const Text('Голосовые подсказки')),
    const Divider(),
    const Text('СПИДОМЕТР', style: TextStyle(color: cyan, fontWeight: FontWeight.bold)),
    RadioGroup<GaugeStyle>(
      groupValue: gaugeStyle,
      onChanged: (v) { if (v != null) onGauge(v); },
      child: Column(children: [
        for (final g in GaugeStyle.values) RadioListTile<GaugeStyle>(value: g, title: Text(g.title)),
      ]),
    ),
    const SizedBox(height: 12),
    const Text('СЕЗОН', style: TextStyle(color: cyan, fontWeight: FontWeight.bold)),
    RadioGroup<Season>(
      groupValue: season,
      onChanged: (v) { if (v != null) onSeason(v); },
      child: Column(
        children: [
          for (final s in Season.values)
            RadioListTile<Season>(value: s, title: Text(s.title)),
        ],
      ),
    ),
  ]);
}

class Header extends StatelessWidget {
  final String title;
  final String subtitle;
  const Header(this.title, this.subtitle, {super.key});
  @override
  Widget build(BuildContext context) => Row(children: [
    Container(
      width: 42, height: 42,
      decoration: BoxDecoration(color: red.withValues(alpha: .12), borderRadius: BorderRadius.circular(13), border: Border.all(color: red.withValues(alpha: .35))),
      child: const Icon(Icons.bolt, color: red, size: 26),
    ),
    const SizedBox(width: 10),
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 20, letterSpacing: .4)),
      Text(subtitle, style: const TextStyle(color: Colors.white38, fontSize: 9, letterSpacing: 1.2)),
    ]),
  ]);
}

class GaugePainter extends CustomPainter {
  final double speed;
  final GaugeStyle style;
  GaugePainter(this.speed, this.style);
  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 20;
    final base = Paint()..style = PaintingStyle.stroke..strokeWidth = style == GaugeStyle.classic ? 12 : 16..color = Colors.white10;
    final accent = style == GaugeStyle.blue ? cyan : red;
    final active = Paint()..style = PaintingStyle.stroke..strokeWidth = style == GaugeStyle.classic ? 12 : 16..color = accent..strokeCap = StrokeCap.round;
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), math.pi * .75, math.pi * 1.5, false, base);
    final value = (speed / 300).clamp(0.0, 1.0);
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), math.pi * .75, math.pi * 1.5 * value, false, active);
    final tick = Paint()..color = (style == GaugeStyle.classic ? Colors.white54 : Colors.white24)..strokeWidth = 2;
    for (var i = 0; i <= 30; i++) {
      final a = math.pi * .75 + math.pi * 1.5 * i / 30;
      final r1 = radius - (i % 5 == 0 ? 19 : 11);
      final r2 = radius - 5;
      canvas.drawLine(Offset(center.dx + math.cos(a) * r1, center.dy + math.sin(a) * r1), Offset(center.dx + math.cos(a) * r2, center.dy + math.sin(a) * r2), tick);
      if (i % 5 == 0 && style != GaugeStyle.classic) {
        final label = TextPainter(text: TextSpan(text: '${i * 10}', style: TextStyle(color: Colors.white70, fontSize: 10, fontWeight: FontWeight.w700)), textDirection: TextDirection.ltr)..layout();
        final lr = radius - 36;
        label.paint(canvas, Offset(center.dx + math.cos(a) * lr - label.width / 2, center.dy + math.sin(a) * lr - label.height / 2));
      }
    }
  }
  @override bool shouldRepaint(covariant GaugePainter oldDelegate) => oldDelegate.speed != speed || oldDelegate.style != style;
}

enum GaugeStyle {
  neon('NEON RED'), blue('BLUE SPORT'), classic('CLASSIC');
  final String title;
  const GaugeStyle(this.title);
}

enum Season {
  spring('Весна'), summer('Лето'), autumn('Осень'), winter('Зима');
  final String title;
  const Season(this.title);
}

class SeasonalEffects extends StatefulWidget {
  final Season season;
  const SeasonalEffects({super.key, required this.season});
  @override State<SeasonalEffects> createState() => _SeasonalEffectsState();
}

class _SeasonalEffectsState extends State<SeasonalEffects> with SingleTickerProviderStateMixin {
  late final AnimationController animation;
  final random = math.Random(73);
  late final List<SeasonParticle> particles;

  @override
  void initState() {
    super.initState();
    particles = List.generate(70, (i) => SeasonParticle(
      x: random.nextDouble(),
      y: random.nextDouble(),
      size: 1.5 + random.nextDouble() * 4,
      phase: random.nextDouble() * math.pi * 2,
      speed: .35 + random.nextDouble() * .8,
      rotation: random.nextDouble() * math.pi,
    ));
    animation = AnimationController(vsync: this, duration: const Duration(seconds: 20))..repeat();
  }

  @override
  void dispose() { animation.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: animation,
    builder: (_, __) => CustomPaint(
      painter: SeasonPainter(widget.season, particles, animation.value),
      child: const SizedBox.expand(),
    ),
  );
}

class SeasonParticle {
  final double x, y, size, phase, speed, rotation;
  const SeasonParticle({required this.x, required this.y, required this.size, required this.phase, required this.speed, required this.rotation});
}

class SeasonPainter extends CustomPainter {
  final Season season;
  final List<SeasonParticle> particles;
  final double t;
  SeasonPainter(this.season, this.particles, this.t);

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint();
    final top = switch (season) {
      Season.winter => Colors.blueGrey.withValues(alpha: .13),
      Season.autumn => Colors.deepOrange.withValues(alpha: .09),
      Season.spring => Colors.pink.withValues(alpha: .06),
      Season.summer => Colors.amber.withValues(alpha: .08),
    };
    canvas.drawRect(
      Offset.zero & size,
      Paint()..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [top, Colors.transparent, Colors.transparent],
        stops: const [.0, .38, 1],
      ).createShader(Offset.zero & size),
    );

    if (season == Season.summer) {
      p.color = Colors.amber.withValues(alpha: .08);
      for (var i = 0; i < 5; i++) {
        final x = size.width * (.12 + i * .22);
        canvas.drawCircle(Offset(x, size.height * .10), size.width * .12, p);
      }
    }

    for (var i = 0; i < particles.length; i++) {
      final s = particles[i];
      final drift = math.sin(t * math.pi * 2 * s.speed + s.phase);
      final x = (s.x * size.width + drift * (season == Season.autumn ? 28 : 10)) % size.width;
      final travel = (s.y + t * s.speed * (season == Season.winter ? .55 : .42)) % 1;
      final y = travel * size.height;

      if (season == Season.winter) {
        p.color = Colors.white.withValues(alpha: .62 + (i % 4) * .07);
        canvas.drawCircle(Offset(x, y), s.size * .65, p);
        p.color = Colors.white.withValues(alpha: .08);
        canvas.drawCircle(Offset(x, y), s.size * 2.2, p);
      } else if (season == Season.autumn) {
        p.color = [Colors.deepOrange, Colors.orange, Colors.amber, Colors.brown][i % 4].withValues(alpha: .72);
        final leaf = Path()
          ..moveTo(0, -s.size)
          ..quadraticBezierTo(s.size * 1.7, -s.size * .25, 0, s.size)
          ..quadraticBezierTo(-s.size * 1.7, -s.size * .25, 0, -s.size);
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(s.rotation + drift * .5);
        canvas.drawPath(leaf, p);
        canvas.restore();
      } else if (season == Season.spring) {
        p.color = [Colors.pinkAccent, Colors.white, Colors.purpleAccent][i % 3].withValues(alpha: .45);
        canvas.drawCircle(Offset(x, y), s.size * .55, p);
        canvas.drawCircle(Offset(x + s.size, y + s.size * .3), s.size * .35, p);
      }
    }

    if (season == Season.winter) {
      final snow = Path()..moveTo(0, size.height);
      for (var x = 0.0; x <= size.width; x += 28) {
        snow.lineTo(x, size.height - 22 - math.sin(x * .035) * 7 - math.sin(x * .09) * 3);
      }
      snow.lineTo(size.width, size.height)..close();
      p.color = Colors.white.withValues(alpha: .16);
      canvas.drawPath(snow, p);
      p.color = Colors.white.withValues(alpha: .07);
      canvas.drawRect(Rect.fromLTWH(0, size.height - 34, size.width, 34), p);
    } else if (season == Season.autumn) {
      p.color = Colors.orange.withValues(alpha: .035);
      canvas.drawRect(Rect.fromLTWH(0, size.height - 26, size.width, 26), p);
    }
  }

  @override
  bool shouldRepaint(covariant SeasonPainter old) => old.t != t || old.season != season;
}

Season _seasonForDate(DateTime d) { if (d.month >= 3 && d.month <= 5) return Season.spring; if (d.month >= 6 && d.month <= 8) return Season.summer; if (d.month >= 9 && d.month <= 11) return Season.autumn; return Season.winter; }
