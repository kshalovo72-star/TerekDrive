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
  Season season = Season.winter;
  final tts = FlutterTts();

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    final p = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      sound = p.getBool('sound') ?? true;
      final i = p.getInt('season') ?? Season.winter.index;
      season = Season.values[math.min(i, Season.values.length - 1)];
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
      SpeedPage(position: position, onStart: startGps),
      SettingsPage(sound: sound, season: season, onSound: toggleSound, onSeason: setSeason),
    ];
    return Scaffold(
      body: SafeArea(child: pages[tab]),
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
    Positioned.fill(child: IgnorePointer(child: SeasonalEffects(season: widget.season))),
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
              setState(() => message = 'Маршрут к «\function () { [native code] }» готовится');
              speak('Маршрут к \function () { [native code] }');
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
  const SpeedPage({super.key, this.position, required this.onStart});

  @override
  Widget build(BuildContext context) {
    final speed = position == null ? 0.0 : math.max(0.0, position!.speed * 3.6);
    return Column(children: [
      const Padding(padding: EdgeInsets.all(16), child: Header('СКОРОСТЬ', 'GPS / 0—300 KM/H')),
      Expanded(
        child: Center(
          child: CustomPaint(
            size: const Size(300, 300),
            painter: GaugePainter(speed),
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
  final ValueChanged<Season> onSeason;
  final VoidCallback onSound;
  const SettingsPage({super.key, required this.sound, required this.season, required this.onSeason, required this.onSound});

  @override
  Widget build(BuildContext context) => ListView(padding: const EdgeInsets.all(16), children: [
    const Header('НАСТРОЙКИ', 'TEREK DRIVE'),
    SwitchListTile(value: sound, onChanged: (_) => onSound(), title: const Text('Голос Гены'), subtitle: const Text('Голосовые подсказки')),
    const Divider(),
    const Text('СЕЗОН', style: TextStyle(color: cyan, fontWeight: FontWeight.bold)),
    for (final s in Season.values)
      RadioListTile<Season>(value: s, groupValue: season, onChanged: (v) { if (v != null) onSeason(v); }, title: Text(s.title)),
  ]);
}

class Header extends StatelessWidget {
  final String title;
  final String subtitle;
  const Header(this.title, this.subtitle, {super.key});
  @override
  Widget build(BuildContext context) => Row(children: [
    const Icon(Icons.bolt, color: red, size: 30),
    const SizedBox(width: 8),
    Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(title, style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 20)),
      Text(subtitle, style: const TextStyle(color: Colors.white54, fontSize: 10)),
    ]),
  ]);
}

class GaugePainter extends CustomPainter {
  final double speed;
  GaugePainter(this.speed);
  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 20;
    final base = Paint()..style = PaintingStyle.stroke..strokeWidth = 14..color = Colors.white12;
    final active = Paint()..style = PaintingStyle.stroke..strokeWidth = 14..color = cyan..strokeCap = StrokeCap.round;
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), math.pi * .75, math.pi * 1.5, false, base);
    final value = (speed / 300).clamp(0.0, 1.0);
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), math.pi * .75, math.pi * 1.5 * value, false, active);
  }
  @override bool shouldRepaint(covariant GaugePainter oldDelegate) => oldDelegate.speed != speed;
}

enum Season {
  spring('Весна'), summer('Лето'), autumn('Осень'), winter('Зима');
  final String title;
  const Season(this.title);
}

class SeasonalEffects extends StatelessWidget {
  final Season season;
  const SeasonalEffects({super.key, required this.season});
  @override
  Widget build(BuildContext context) {
    if (season == Season.summer) return const SizedBox.shrink();
    return Container(decoration: BoxDecoration(gradient: LinearGradient(
      begin: Alignment.topCenter, end: Alignment.bottomCenter,
      colors: [season == Season.winter ? Colors.white.withValues(alpha: .04) : Colors.orange.withValues(alpha: .03), Colors.transparent],
    )));
  }
}
