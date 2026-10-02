extends Node2D

var speed := 0.0
var target_speed := 0.0
var gauge_style := 0
var season := 3
var particles: Array[Dictionary] = []
var scan := 0.0
var t := 0.0

func _ready() -> void:
    randomize()
    for i in 110:
        particles.append({
            "x": randf(), "y": randf(), "v": randf_range(0.04, 0.18),
            "size": randf_range(1.0, 4.0), "rot": randf_range(0.0, TAU)
        })
    queue_redraw()

func _process(delta: float) -> void:
    t += delta
    scan = fmod(scan + delta * 0.18, 1.0)
    target_speed = 95.0 + sin(t * 0.55) * 70.0
    speed = lerp(speed, target_speed, 1.0 - exp(-delta * 4.0))
    for p in particles:
        p.y = fmod(p.y + p.v * delta, 1.15)
        p.rot += delta * 0.7
    queue_redraw()

func _draw() -> void:
    var s := get_viewport_rect().size
    var c := s * 0.5
    draw_rect(Rect2(Vector2.ZERO, s), Color("#05070b"))
    for i in 12:
        var y := float(i) / 12.0 * s.y
        draw_line(Vector2(0,y), Vector2(s.x,y), Color(0.05,0.12,0.17,0.32), 1.0)
    for i in 8:
        var x := float(i) / 8.0 * s.x
        draw_line(Vector2(x,0), Vector2(x,s.y), Color(0.05,0.12,0.17,0.25), 1.0)
    draw_circle(Vector2(c.x, c.y-60), 520, Color(0.02,0.08,0.13,0.7))
    draw_circle(Vector2(c.x, c.y-60), 410, Color(0.01,0.03,0.06,0.95))
    var center := Vector2(c.x, c.y-55)
    var r := min(s.x, s.y) * 0.32
    var accent := Color("#ff2638") if gauge_style == 0 else (Color("#28c9ff") if gauge_style == 1 else Color("#e7edf3"))
    draw_arc(center, r+26, -2.65, 0.25, 90, accent, 9.0, true)
    draw_arc(center, r+26, 0.25, 2.65, 90, Color("#18232e"), 9.0, true)
    for i in 61:
        var a := lerp(-2.55, 2.55, float(i)/60.0)
        var inner := r-10 if i%5==0 else r
        var outer := r+15 if i%5==0 else r+8
        var p1 := center + Vector2(cos(a),sin(a))*inner
        var p2 := center + Vector2(cos(a),sin(a))*outer
        draw_line(p1,p2,accent if i%5==0 else Color("#65727e"), 3.0 if i%5==0 else 1.2)
    for i in 7:
        var a := lerp(-2.45,2.45,float(i)/6.0)
        var p := center + Vector2(cos(a),sin(a))*(r-42)
        draw_string(ThemeDB.fallback_font, p, str(i*40), HORIZONTAL_ALIGNMENT_CENTER, 60, 24, Color("#c9d2db"))
    var needle_a := lerp(-2.5,2.5,clamp(speed/240.0,0.0,1.0))
    var tip := center + Vector2(cos(needle_a),sin(needle_a))*(r-35)
    draw_line(center, tip, Color("#ff3348"), 10.0)
    draw_circle(center, 28, Color("#151b22"))
    draw_circle(center, 14, accent)
    draw_string(ThemeDB.fallback_font, Vector2(center.x-70,center.y+92), "%03d" % int(speed), HORIZONTAL_ALIGNMENT_CENTER, 140, 42, Color.WHITE)
    draw_string(ThemeDB.fallback_font, Vector2(center.x-90,center.y+132), "KM/H", HORIZONTAL_ALIGNMENT_CENTER, 180, 18, Color("#8c9aa7"))
    draw_string(ThemeDB.fallback_font, Vector2(40,60), "TEREK DRIVE", HORIZONTAL_ALIGNMENT_LEFT, -1, 32, Color.WHITE)
    draw_string(ThemeDB.fallback_font, Vector2(40,98), "GODOT PERFORMANCE COCKPIT", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, accent)
    draw_string(ThemeDB.fallback_font, Vector2(s.x-220,60), "GPS  •  ONLINE", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#63ff9b"))
    draw_string(ThemeDB.fallback_font, Vector2(40,s.y-90), "NEON RED   BLUE SPORT   CLASSIC", HORIZONTAL_ALIGNMENT_LEFT, -1, 16, Color("#aab5c0"))
    draw_string(ThemeDB.fallback_font, Vector2(40,s.y-55), "SNOW / RAIN / LEAVES  •  60 FPS TARGET", HORIZONTAL_ALIGNMENT_LEFT, -1, 15, Color("#66737f"))
    for p in particles:
        var pp := Vector2(p.x*s.x,p.y*s.y)
        draw_circle(pp, p.size, Color(0.8,0.9,1.0,0.28))
