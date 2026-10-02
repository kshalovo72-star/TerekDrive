import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' as ui;
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

class SpeedPage extends StatefulWidget {
  final Position? position;
  final VoidCallback onStart;
  final GaugeStyle gaugeStyle;
  const SpeedPage({super.key, this.position, required this.onStart, required this.gaugeStyle});

  @override
  State<SpeedPage> createState() => _SpeedPageState();
}

class _SpeedPageState extends State<SpeedPage> with SingleTickerProviderStateMixin {
  late final AnimationController controller;

  @override
  void initState() {
    super.initState();
    controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..repeat();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final speed = widget.position == null ? 0.0 : math.max(0.0, widget.position!.speed * 3.6);
    final gpsReady = widget.position != null;
    return Stack(
      children: [
        Positioned.fill(child: CustomPaint(painter: CockpitBackgroundPainter(controller.value))),
        SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
                child: Row(
                  children: [
                    const Header('СКОРОСТЬ', 'TEREK DRIVE / DIGITAL COCKPIT'),
                    const Spacer(),
                    _HudChip(icon: Icons.gps_fixed, text: gpsReady ? 'GPS LIVE' : 'GPS OFF', active: gpsReady),
                  ],
                ),
              ),
              const SizedBox(height: 8),
              Expanded(
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final d = math.min(constraints.maxWidth - 22, constraints.maxHeight - 22).clamp(280.0, 430.0).toDouble();
                    return Center(
                      child: AnimatedBuilder(
                        animation: controller,
                        builder: (_, __) => SizedBox(
                          width: d,
                          height: d,
                          child: CustomPaint(
                            painter: AdvancedGaugePainter(
                              speed: speed,
                              style: widget.gaugeStyle,
                              pulse: controller.value,
                              gpsReady: gpsReady,
                            ),
                            child: Center(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    speed.toStringAsFixed(0),
                                    style: TextStyle(
                                      fontSize: d * .205,
                                      height: .88,
                                      fontWeight: FontWeight.w900,
                                      letterSpacing: -3,
                                      shadows: [
                                        Shadow(color: widget.gaugeStyle.accent.withValues(alpha: .55), blurRadius: 22),
                                      ],
                                    ),
                                  ),
                                  Text(
                                    'KM/H',
                                    style: TextStyle(
                                      color: widget.gaugeStyle.accent,
                                      fontSize: d * .045,
                                      fontWeight: FontWeight.w900,
                                      letterSpacing: 4,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Text(
                                    widget.gaugeStyle.title,
                                    style: const TextStyle(color: Colors.white38, fontSize: 9, letterSpacing: 2.2, fontWeight: FontWeight.w800),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                    );
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 0, 14, 12),
                child: Row(
                  children: [
                    Expanded(child: _MetricCard(label: 'GPS', value: gpsReady ? 'LOCK' : 'WAIT', icon: Icons.satellite_alt)),
                    const SizedBox(width: 8),
                    Expanded(child: _MetricCard(label: 'MAX', value: '300', icon: Icons.speed)),
                    const SizedBox(width: 8),
                    Expanded(child: _MetricCard(label: 'MODE', value: widget.gaugeStyle.shortName, icon: Icons.tune)),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 0, 18, 12),
                child: SizedBox(
                  width: double.infinity,
                  height: 54,
                  child: FilledButton.icon(
                    onPressed: widget.onStart,
                    icon: const Icon(Icons.gps_fixed),
                    label: Text(gpsReady ? 'GPS ПОДКЛЮЧЕН' : 'ВКЛЮЧИТЬ GPS'),
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _HudChip extends StatelessWidget {
  final IconData icon;
  final String text;
  final bool active;
  const _HudChip({required this.icon, required this.text, required this.active});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
    decoration: BoxDecoration(
      color: active ? red.withValues(alpha: .10) : Colors.white.withValues(alpha: .04),
      borderRadius: BorderRadius.circular(20),
      border: Border.all(color: active ? red.withValues(alpha: .35) : Colors.white10),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 13, color: active ? red : Colors.white38),
        const SizedBox(width: 6),
        Text(text, style: TextStyle(fontSize: 9, fontWeight: FontWeight.w900, letterSpacing: 1.2, color: active ? Colors.white : Colors.white38)),
      ],
    ),
  );
}

class _MetricCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  const _MetricCard({required this.label, required this.value, required this.icon});

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
    decoration: BoxDecoration(
      color: Colors.white.withValues(alpha: .035),
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: Colors.white.withValues(alpha: .07)),
      boxShadow: const [BoxShadow(color: Colors.black54, blurRadius: 18, offset: Offset(0, 8))],
    ),
    child: Row(
      children: [
        Icon(icon, size: 17, color: cyan),
        const SizedBox(width: 7),
        Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(color: Colors.white38, fontSize: 8, fontWeight: FontWeight.w800, letterSpacing: 1)),
          const SizedBox(height: 2),
          Text(value, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w900)),
        ]),
      ],
    ),
  );
}

class CockpitBackgroundPainter extends CustomPainter {
  final double t;
  CockpitBackgroundPainter(this.t);

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height * .45);
    final glow = Paint()
      ..shader = RadialGradient(
        colors: [red.withValues(alpha: .09), Colors.transparent],
      ).createShader(Rect.fromCircle(center: center, radius: size.width * .55));
    canvas.drawCircle(center, size.width * .55, glow);

    final grid = Paint()..color = Colors.white.withValues(alpha: .018)..strokeWidth = 1;
    for (var x = 0.0; x < size.width; x += 28) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), grid);
    }
    for (var y = 0.0; y < size.height; y += 28) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), grid);
    }

    final scan = Paint()..color = red.withValues(alpha: .035);
    final sy = (t * size.height * 1.4) % (size.height + 80) - 40;
    canvas.drawRect(Rect.fromLTWH(0, sy, size.width, 2), scan);
  }

  @override
  bool shouldRepaint(covariant CockpitBackgroundPainter old) => old.t != t;
}

class AdvancedGaugePainter extends CustomPainter {
  final double speed;
  final GaugeStyle style;
  final double pulse;
  final bool gpsReady;
  AdvancedGaugePainter({required this.speed, required this.style, required this.pulse, required this.gpsReady});

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final r = math.min(size.width, size.height) / 2;
    final accent = style.accent;
    final value = (speed / 300).clamp(0.0, 1.0);
    final start = math.pi * .73;
    final sweep = math.pi * 1.54;

    void arc(double radius, double width, Color color, double from, double amount, {bool glow = false}) {
      final p = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = width
        ..strokeCap = StrokeCap.round
        ..color = color;
      if (glow) p.maskFilter = const ui.MaskFilter.blur(ui.BlurStyle.normal, 9);
      canvas.drawArc(Rect.fromCircle(center: c, radius: radius), from, amount, false, p);
    }

    final disc = Paint()
      ..shader = RadialGradient(
        colors: [const Color(0xFF151A22), const Color(0xFF080A0E), const Color(0xFF030406)],
        stops: const [.0, .62, 1],
      ).createShader(Rect.fromCircle(center: c, radius: r));
    canvas.drawCircle(c, r * .94, disc);

    arc(r * .91, 2, Colors.white.withValues(alpha: .12), 0, math.pi * 2);
    arc(r * .84, 2, accent.withValues(alpha: .12), start, sweep, glow: true);
    arc(r * .84, 8, accent.withValues(alpha: .08), start, sweep);
    arc(r * .84, 8, accent, start, sweep * value, glow: value > .01);

    for (var i = 0; i < 60; i++) {
      final a = start + sweep * i / 59;
      final rr1 = r * .77;
      final rr2 = r * (i % 5 == 0 ? .70 : .735);
      final p = Paint()
        ..color = i / 59 <= value ? accent.withValues(alpha: .72) : Colors.white.withValues(alpha: .12)
        ..strokeWidth = i % 5 == 0 ? 3 : 1.5;
      canvas.drawLine(
        Offset(c.dx + math.cos(a) * rr1, c.dy + math.sin(a) * rr1),
        Offset(c.dx + math.cos(a) * rr2, c.dy + math.sin(a) * rr2),
        p,
      );
    }

    for (var i = 0; i <= 30; i++) {
      final a = start + sweep * i / 30;
      final major = i % 5 == 0;
      final rr1 = r * (major ? .67 : .705);
      final rr2 = r * .735;
      final p = Paint()
        ..color = major ? Colors.white.withValues(alpha: .72) : Colors.white.withValues(alpha: .22)
        ..strokeWidth = major ? 3 : 1.4;
      canvas.drawLine(
        Offset(c.dx + math.cos(a) * rr1, c.dy + math.sin(a) * rr1),
        Offset(c.dx + math.cos(a) * rr2, c.dy + math.sin(a) * rr2),
        p,
      );
      if (major) {
        final tp = TextPainter(
          text: TextSpan(text: (i * 10).toString(), style: TextStyle(color: Colors.white.withValues(alpha: .72), fontSize: r * .055, fontWeight: FontWeight.w800)),
          textDirection: TextDirection.ltr,
        )..layout();
        final lr = r * .59;
        tp.paint(canvas, Offset(c.dx + math.cos(a) * lr - tp.width / 2, c.dy + math.sin(a) * lr - tp.height / 2));
      }
    }

    final redline = Paint()..color = red.withValues(alpha: .16);
    canvas.drawArc(Rect.fromCircle(center: c, radius: r * .76), start + sweep * .82, sweep * .18, false, redline);

    final needleAngle = start + sweep * value;
    final tip = Offset(c.dx + math.cos(needleAngle) * r * .68, c.dy + math.sin(needleAngle) * r * .68);
    final glowNeedle = Paint()
      ..color = accent.withValues(alpha: .45)
      ..strokeWidth = 9
      ..strokeCap = StrokeCap.round
      ..maskFilter = const ui.MaskFilter.blur(ui.BlurStyle.normal, 10);
    canvas.drawLine(c, tip, glowNeedle);
    final needle = Paint()..color = Colors.white..strokeWidth = 3..strokeCap = StrokeCap.round;
    canvas.drawLine(c, tip, needle);
    final needleAccent = Paint()..color = accent..strokeWidth = 5..strokeCap = StrokeCap.round;
    canvas.drawLine(c, Offset(c.dx + math.cos(needleAngle) * r * .18, c.dy + math.sin(needleAngle) * r * .18), tip, needleAccent);

    final hubGlow = Paint()..color = accent.withValues(alpha: .35)..maskFilter = const ui.MaskFilter.blur(ui.BlurStyle.normal, 12);
    canvas.drawCircle(c, r * .075, hubGlow);
    canvas.drawCircle(c, r * .075, Paint()..color = const Color(0xFF0A0D12));
    canvas.drawCircle(c, r * .048, Paint()..color = accent);
    canvas.drawCircle(c, r * .022, Paint()..color = Colors.white);

    final panelRect = RRect.fromRectAndRadius(
      Rect.fromCenter(center: Offset(c.dx, c.dy + r * .49), width: r * 1.05, height: r * .12),
      Radius.circular(r * .05),
    );
    canvas.drawRRect(panelRect, Paint()..color = Colors.white.withValues(alpha: .035));
    final tele = TextPainter(
      text: TextSpan(
        children: [
          TextSpan(text: gpsReady ? 'GPS LOCKED' : 'GPS SEARCH', style: TextStyle(color: gpsReady ? accent : Colors.white38, fontSize: r * .032, fontWeight: FontWeight.w900, letterSpacing: 1.4)),
          TextSpan(text: '   •   0—300', style: TextStyle(color: Colors.white38, fontSize: r * .032, fontWeight: FontWeight.w700)),
        ],
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    tele.paint(canvas, Offset(c.dx - tele.width / 2, c.dy + r * .49 - tele.height / 2));

    for (var i = 0; i < 5; i++) {
      final active = i < (value * 5).ceil();
      canvas.drawCircle(
        Offset(c.dx - r * .20 + i * r * .10, c.dy - r * .64),
        r * .012,
        Paint()..color = active ? accent : Colors.white12,
      );
    }
  }

  @override
  bool shouldRepaint(covariant AdvancedGaugePainter old) =>
      old.speed != speed || old.style != style || old.pulse != pulse || old.gpsReady != gpsReady;
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
  neon('NEON RED', 'NEON', red),
  blue('BLUE SPORT', 'BLUE', cyan),
  classic('CLASSIC', 'CLASSIC', Colors.white);

  final String title;
  final String shortName;
  final Color accent;
  const GaugeStyle(this.title, this.shortName, this.accent);
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
    particles = List.generate(110, (i) => SeasonParticle(
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
      snow.lineTo(size.width, size.height);
      snow.close();
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
