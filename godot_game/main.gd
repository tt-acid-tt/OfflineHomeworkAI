extends Node3D

const WOOD_COST := [3, 4, 3, 2, 6, 8]
const METAL_COST := [1, 1, 1, 0, 2, 3]
const PLAN_NAMES := ["Костёр", "Фундамент", "Стена", "Потолок", "Дверной проём", "Укрытие"]

var player: CharacterBody3D
var camera: Camera3D
var world_env: WorldEnvironment
var hud: CanvasLayer
var message_label: Label
var inventory_panel: PanelContainer
var book_panel: PanelContainer
var wood := 0
var metal := 0
var day_clock := 0.0
var yaw := 0.0
var pitch := 0.0
var touch_origin := Vector2.ZERO
var active_touch := -1
var move_vector := Vector2.ZERO
var selected_plan := 1
var placing := false
var strict_grid := true
var placed_plans: Array[Dictionary] = []
var interactables: Array[Dictionary] = []
var trees: Array[Node3D] = []
var suitcases: Array[Node3D] = []
var tree_falls: Dictionary = {}
var last_harvest_time := -10.0
var game_time := 0.0
var inventory_open := false
var book_open := false
var intro_active := true
var intro_time := 0.0
var intro_overlay: ColorRect
var intro_title: Label
var nearby: Dictionary = {}
var status_text := "Выбери точку высадки и найди припасы."

func _ready() -> void:
    _build_environment()
    _build_world()
    _build_player()
    _build_hud()
    _start_intro()

func _process(delta: float) -> void:
    game_time += delta
    if intro_active:
        intro_time += delta
        camera.rotation.x = lerp(camera.rotation.x, -0.12 if intro_time < 3.0 else 0.0, delta * 0.5)
        if intro_time > 7.5:
            _finish_intro()
    else:
        day_clock = fposmod(day_clock + delta * 0.008, TAU)
        var daylight := 0.58 + 0.42 * maxf(0.0, sin(day_clock + 0.9))
        if world_env and world_env.environment:
            world_env.environment.ambient_light_energy = 0.45 + daylight * 0.7
        var sun := get_node_or_null("Sun") as DirectionalLight3D
        if sun:
            sun.light_energy = 0.18 + daylight * 1.2
            sun.rotation.x = -0.2 - daylight * 0.8
        _update_nearby()
        if placing:
            _update_ghost()
    if intro_overlay:
        intro_overlay.modulate.a = maxf(0.0, intro_overlay.modulate.a - delta * 0.7)

func _physics_process(delta: float) -> void:
    if intro_active or not is_instance_valid(player):
        return
    var wish := Vector3(move_vector.x, 0.0, move_vector.y)
    wish = camera.global_basis * wish
    wish.y = 0
    wish = wish.normalized()
    var speed := 3.0
    player.velocity.x = move_toward(player.velocity.x, wish.x * speed, delta * 12.0)
    player.velocity.z = move_toward(player.velocity.z, wish.z * speed, delta * 12.0)
    if not player.is_on_floor():
        player.velocity.y -= 16.0 * delta
    else:
        player.velocity.y = 0
    player.move_and_slide()

func _input(event: InputEvent) -> void:
    if intro_active:
        return
    if event is InputEventScreenTouch:
        if event.pressed:
            if event.position.x < get_viewport().get_visible_rect().size.x * 0.42 and event.position.y > get_viewport().get_visible_rect().size.y * 0.45:
                active_touch = event.index
                touch_origin = event.position
                move_vector = Vector2.ZERO
        elif event.index == active_touch:
            active_touch = -1
            move_vector = Vector2.ZERO
    elif event is InputEventScreenDrag:
        if event.index == active_touch:
            var delta := event.position - touch_origin
            move_vector = Vector2(clampf(delta.x / 90.0, -1, 1), clampf(delta.y / 90.0, -1, 1))
        elif not book_open and not inventory_open:
            yaw -= event.relative.x * 0.004
            pitch = clampf(pitch - event.relative.y * 0.0035, -1.0, 0.8)
            player.rotation.y = yaw
            camera.rotation.x = pitch
    elif event is InputEventKey and event.pressed:
        if event.keycode == KEY_E:
            _interact()
        elif event.keycode == KEY_B:
            _toggle_book()
        elif event.keycode == KEY_I:
            _toggle_inventory()
        elif event.keycode == KEY_ESCAPE:
            book_open = false
            inventory_open = false
            _refresh_panels()

func _build_environment() -> void:
    var env := Environment.new()
    env.background_mode = Environment.BG_SKY
    var sky := Sky.new()
    var sky_mat := ProceduralSkyMaterial.new()
    sky_mat.sky_top_color = Color(0.13, 0.36, 0.66)
    sky_mat.sky_horizon_color = Color(0.73, 0.82, 0.91)
    sky_mat.ground_bottom_color = Color(0.12, 0.16, 0.11)
    sky_mat.ground_horizon_color = Color(0.48, 0.56, 0.52)
    sky_mat.sun_angle_max = 18.0
    sky.sky_material = sky_mat
    env.sky = sky
    env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
    env.ambient_light_energy = 0.9
    env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
    env.fog_enabled = true
    env.fog_light_color = Color(0.62, 0.72, 0.77)
    env.fog_density = 0.0015
    world_env = WorldEnvironment.new()
    world_env.name = "WorldEnvironment"
    world_env.environment = env
    add_child(world_env)
    var sun := DirectionalLight3D.new()
    sun.name = "Sun"
    sun.rotation = Vector3(-0.8, -0.5, 0.0)
    sun.light_color = Color(1.0, 0.92, 0.79)
    sun.light_energy = 1.3
    sun.shadow_enabled = true
    add_child(sun)

func _build_world() -> void:
    var ground_mat := _mat(Color(0.20, 0.27, 0.17), 0.95)
    var ground := MeshInstance3D.new()
    var ground_mesh := PlaneMesh.new()
    ground_mesh.size = Vector2(260, 260)
    ground.mesh = ground_mesh
    ground.material_override = ground_mat
    ground.position.y = -0.12
    add_child(ground)
    var ground_body := StaticBody3D.new()
    ground_body.position.y = -0.25
    var ground_shape := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = Vector3(260, 0.5, 260)
    ground_shape.shape = shape
    ground_body.add_child(ground_shape)
    add_child(ground_body)
    _make_plane(Vector3(0, 0, -13))
    for i in range(68):
        var x := float((i * 17) % 49) - 24.0
        var z := 12.0 - float((i * 23) % 82)
        if absf(x) < 5.5 and z > -24:
            x += -7.0 if i % 2 == 0 else 7.0
        _make_tree(Vector3(x, 0, z), 3.8 + float(i % 5) * 0.42)
    for i in range(7):
        _make_suitcase(Vector3(-3.8 + float(i % 3) * 1.0, 0.1, -5.0 - float(i / 3) * 2.1), i)
    _make_rock_cluster()

func _build_player() -> void:
    player = CharacterBody3D.new()
    player.name = "Player"
    player.position = Vector3(0, 0.12, 2.8)
    var capsule := CapsuleMesh.new()
    capsule.radius = 0.28
    capsule.height = 1.65
    var body_mesh := MeshInstance3D.new()
    body_mesh.mesh = capsule
    body_mesh.position.y = 0.83
    body_mesh.material_override = _mat(Color(0.16, 0.24, 0.25), 0.8)
    player.add_child(body_mesh)
    var head := MeshInstance3D.new()
    var head_mesh := SphereMesh.new()
    head_mesh.radius = 0.19
    head_mesh.height = 0.38
    head.mesh = head_mesh
    head.position = Vector3(0, 1.72, 0)
    head.material_override = _mat(Color(0.72, 0.54, 0.40), 0.7)
    player.add_child(head)
    var collider := CollisionShape3D.new()
    var cap_shape := CapsuleShape3D.new()
    cap_shape.radius = 0.29
    cap_shape.height = 1.75
    collider.shape = cap_shape
    collider.position.y = 0.88
    player.add_child(collider)
    camera = Camera3D.new()
    camera.position = Vector3(0, 1.56, 0)
    camera.current = true
    camera.fov = 76.0
    player.add_child(camera)
    add_child(player)

func _make_plane(pos: Vector3) -> void:
    var plane_root := Node3D.new()
    plane_root.name = "PassengerJet"
    plane_root.position = pos
    add_child(plane_root)
    var fuselage := MeshInstance3D.new()
    var tube := CylinderMesh.new()
    tube.top_radius = 1.18
    tube.bottom_radius = 1.18
    tube.height = 17.5
    tube.radial_segments = 48
    tube.rings = 12
    fuselage.mesh = tube
    fuselage.rotation.x = PI / 2
    fuselage.material_override = _mat(Color(0.72, 0.76, 0.75), 0.28, 0.55)
    plane_root.add_child(fuselage)
    _sphere(plane_root, Vector3(0, 0, -9.0), Vector3(0.84, 0.84, 1.45), Color(0.76, 0.79, 0.76), 0.3)
    _sphere(plane_root, Vector3(0, 0.15, 8.8), Vector3(1.0, 0.92, 1.0), Color(0.65, 0.70, 0.70), 0.32)
    _box_mesh(plane_root, Vector3(0, -0.20, 0), Vector3(12.5, 0.20, 3.1), Color(0.08, 0.30, 0.43), 0.38)
    _box_mesh(plane_root, Vector3(0, 0.0, 7.4), Vector3(5.5, 0.12, 2.0), Color(0.70, 0.73, 0.71), 0.4)
    _box_mesh(plane_root, Vector3(0, 0.15, -6.9), Vector3(4.0, 0.10, 1.6), Color(0.67, 0.70, 0.69), 0.4)
    for side in [-1.0, 1.0]:
        for i in range(9):
            var window := MeshInstance3D.new()
            var sphere := SphereMesh.new()
            sphere.radius = 0.19
            sphere.height = 0.12
            window.mesh = sphere
            window.scale = Vector3(1.0, 0.72, 1.0)
            window.position = Vector3(side * 1.13, 0.23, -6.0 + i * 1.38)
            window.material_override = _mat(Color(0.035, 0.13, 0.21), 0.15, 0.7)
            plane_root.add_child(window)
    for side in [-1.0, 1.0]:
        var engine := MeshInstance3D.new()
        var engine_mesh := CylinderMesh.new()
        engine_mesh.top_radius = 0.62
        engine_mesh.bottom_radius = 0.72
        engine_mesh.height = 2.0
        engine_mesh.radial_segments = 32
        engine.mesh = engine_mesh
        engine.rotation.x = PI / 2
        engine.position = Vector3(side * 3.2, -0.1, 0.5)
        engine.material_override = _mat(Color(0.43, 0.47, 0.48), 0.35, 0.65)
        plane_root.add_child(engine)
        var intake := MeshInstance3D.new()
        var ring := TorusMesh.new()
        ring.inner_radius = 0.43
        ring.outer_radius = 0.53
        intake.mesh = ring
        intake.position = Vector3(side * 3.2, -0.1, -0.52)
        intake.material_override = _mat(Color(0.07, 0.09, 0.10), 0.28)
        plane_root.add_child(intake)
    _box_mesh(plane_root, Vector3(1.2, 0.65, -4.0), Vector3(0.08, 1.4, 1.05), Color(0.95, 0.47, 0.10), 0.4)
    var exit_text := Label3D.new()
    exit_text.text = "EMERGENCY EXIT  →"
    exit_text.font_size = 48
    exit_text.pixel_size = 0.004
    exit_text.position = Vector3(1.35, 1.3, -4.0)
    exit_text.rotation.y = PI / 2
    exit_text.modulate = Color(1.0, 0.7, 0.2)
    plane_root.add_child(exit_text)
    var collision := StaticBody3D.new()
    var body_shape := CollisionShape3D.new()
    var box_shape := BoxShape3D.new()
    box_shape.size = Vector3(2.1, 2.1, 16.5)
    body_shape.shape = box_shape
    collision.add_child(body_shape)
    plane_root.add_child(collision)

func _make_tree(pos: Vector3, height: float) -> void:
    var root := Node3D.new()
    root.position = pos
    root.name = "Tree"
    add_child(root)
    var trunk := MeshInstance3D.new()
    var trunk_mesh := CylinderMesh.new()
    trunk_mesh.top_radius = 0.19
    trunk_mesh.bottom_radius = 0.34
    trunk_mesh.height = height
    trunk_mesh.radial_segments = 20
    trunk.mesh = trunk_mesh
    trunk.position.y = height * 0.5
    trunk.material_override = _mat(Color(0.29, 0.18, 0.10), 0.92)
    root.add_child(trunk)
    for k in range(5):
        var crown := MeshInstance3D.new()
        var crown_mesh := SphereMesh.new()
        crown_mesh.radius = 1.45 - float(k % 2) * 0.18
        crown_mesh.height = 2.2
        crown.mesh = crown_mesh
        crown.position = Vector3(sin(float(k) * 2.2) * 0.58, height - 0.2 + float(k % 3) * 0.55, cos(float(k) * 2.1) * 0.48)
        crown.material_override = _mat(Color(0.12 + float(k % 3) * 0.025, 0.25 + float(k % 2) * 0.035, 0.13), 0.95)
        root.add_child(crown)
    var collider := StaticBody3D.new()
    var cs := CollisionShape3D.new()
    var shape := CylinderShape3D.new()
    shape.radius = 0.45
    shape.height = height
    cs.shape = shape
    cs.position.y = height * 0.5
    collider.add_child(cs)
    root.add_child(collider)
    trees.append(root)
    interactables.append({"node": root, "kind": "tree", "used": false})

func _make_suitcase(pos: Vector3, idx: int) -> void:
    var root := Node3D.new()
    root.position = pos
    root.rotation.y = float(idx) * 0.42
    root.name = "Suitcase_%02d" % idx
    add_child(root)
    _box_mesh(root, Vector3(0, 0.24, 0), Vector3(0.92, 0.46, 0.62), Color(0.20 + float(idx % 3) * 0.08, 0.22, 0.19), 0.65)
    _box_mesh(root, Vector3(0, 0.49, 0), Vector3(0.78, 0.08, 0.54), Color(0.33, 0.32, 0.27), 0.72)
    _box_mesh(root, Vector3(0, 0.55, -0.22), Vector3(0.26, 0.06, 0.09), Color(0.12, 0.13, 0.12), 0.5)
    var collider := StaticBody3D.new()
    var cs := CollisionShape3D.new()
    var shape := BoxShape3D.new()
    shape.size = Vector3(1.0, 0.65, 0.7)
    cs.shape = shape
    cs.position.y = 0.28
    collider.add_child(cs)
    root.add_child(collider)
    suitcases.append(root)
    interactables.append({"node": root, "kind": "suitcase", "used": false})

func _make_rock_cluster() -> void:
    for i in range(26):
        var rock := MeshInstance3D.new()
        var mesh := SphereMesh.new()
        mesh.radius = 0.45 + float(i % 3) * 0.13
        mesh.height = 0.55 + float(i % 3) * 0.2
        rock.mesh = mesh
        rock.scale = Vector3(1.2, 0.7, 0.85)
        rock.position = Vector3(sin(float(i) * 5.7) * 23.0, 0.15, -float(i) * 3.0)
        rock.material_override = _mat(Color(0.25, 0.28, 0.25), 0.9)
        add_child(rock)

func _build_hud() -> void:
    hud = CanvasLayer.new()
    add_child(hud)
    var root := Control.new()
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    root.mouse_filter = Control.MOUSE_FILTER_PASS
    hud.add_child(root)
    var top := HBoxContainer.new()
    top.position = Vector2(18, 16)
    top.add_theme_constant_override("separation", 10)
    root.add_child(top)
    var title := Label.new()
    title.text = "AFTERMATH  /  ОСТРОВ"
    title.add_theme_font_size_override("font_size", 22)
    title.add_theme_color_override("font_color", Color(0.92, 0.93, 0.84))
    top.add_child(title)
    message_label = Label.new()
    message_label.text = "ДЕНЬ 01  •  Дерево 0  •  Металл 0"
    message_label.position = Vector2(20, 48)
    message_label.add_theme_font_size_override("font_size", 16)
    message_label.add_theme_color_override("font_color", Color(0.84, 0.88, 0.79))
    root.add_child(message_label)
    var buttons := VBoxContainer.new()
    buttons.anchor_left = 1.0
    buttons.anchor_right = 1.0
    buttons.offset_left = -208
    buttons.offset_right = -18
    buttons.offset_top = 18
    buttons.add_theme_constant_override("separation", 9)
    root.add_child(buttons)
    _make_button(buttons, "ЧЕРТЕЖИ", _toggle_book)
    _make_button(buttons, "ИНВЕНТАРЬ", _toggle_inventory)
    _make_button(buttons, "СЕТКА: ВКЛ", _toggle_grid)
    _make_button(buttons, "СОБРАТЬ / ЗАПОЛНИТЬ", _interact)
    var help := Label.new()
    help.text = "Слева — движение\nСправа — обзор камеры"
    help.position = Vector2(20, -70)
    help.anchor_top = 1.0
    help.anchor_bottom = 1.0
    help.add_theme_font_size_override("font_size", 15)
    help.add_theme_color_override("font_color", Color(0.91, 0.93, 0.84))
    root.add_child(help)
    inventory_panel = PanelContainer.new()
    inventory_panel.visible = false
    inventory_panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
    inventory_panel.custom_minimum_size = Vector2(420, 250)
    root.add_child(inventory_panel)
    book_panel = PanelContainer.new()
    book_panel.visible = false
    book_panel.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
    book_panel.custom_minimum_size = Vector2(560, 400)
    root.add_child(book_panel)
    _refresh_panels()

func _make_button(parent: Control, caption: String, callback: Callable) -> void:
    var button := Button.new()
    button.text = caption
    button.custom_minimum_size = Vector2(190, 48)
    button.add_theme_font_size_override("font_size", 16)
    button.add_theme_color_override("font_color", Color(0.93, 0.95, 0.89))
    button.add_theme_stylebox_override("normal", _stylebox(Color(0.07, 0.15, 0.17, 0.92), 12, Color(0.22, 0.68, 0.57)))
    button.add_theme_stylebox_override("hover", _stylebox(Color(0.10, 0.24, 0.23, 0.98), 12, Color(0.30, 0.90, 0.73)))
    button.pressed.connect(callback)
    parent.add_child(button)

func _refresh_panels() -> void:
    if not is_instance_valid(inventory_panel) or not is_instance_valid(book_panel):
        return
    inventory_panel.visible = inventory_open
    book_panel.visible = book_open
    for child in inventory_panel.get_children():
        child.queue_free()
    if inventory_open:
        var col := VBoxContainer.new()
        col.add_theme_constant_override("separation", 14)
        inventory_panel.add_child(col)
        var label := Label.new()
        label.text = "РЮКЗАК  /  ДОБЫЧА"
        label.add_theme_font_size_override("font_size", 24)
        col.add_child(label)
        var items := Label.new()
        items.text = "Дерево: %d\nМеталл: %d\nРазмещённых чертежей: %d\nИнструменты и находки: чемоданы вокруг самолёта" % [wood, metal, placed_plans.size()]
        items.add_theme_font_size_override("font_size", 18)
        col.add_child(items)
        _make_button(col, "ЗАКРЫТЬ", _toggle_inventory)
    for child in book_panel.get_children():
        child.queue_free()
    if book_open:
        var col2 := VBoxContainer.new()
        col2.add_theme_constant_override("separation", 8)
        book_panel.add_child(col2)
        var title := Label.new()
        title.text = "ПОЛЕВАЯ КНИГА  /  ЧЕРТЕЖИ"
        title.add_theme_font_size_override("font_size", 22)
        col2.add_child(title)
        for i in range(PLAN_NAMES.size()):
            var button := Button.new()
            button.text = "%s    •    %d дерева / %d металла" % [PLAN_NAMES[i], WOOD_COST[i], METAL_COST[i]]
            button.custom_minimum_size = Vector2(460, 40)
            button.pressed.connect(func(): selected_plan = i; placing = true; book_open = false; _refresh_panels(); _set_status("Чертёж выбран: %s. Нажми «ПОСТАВИТЬ»." % PLAN_NAMES[i]);)
            col2.add_child(button)
        _make_button(col2, "ЗАКРЫТЬ", _toggle_book)
        _make_button(col2, "ПОСТАВИТЬ ЧЕРТЁЖ", _place_blueprint)

func _start_intro() -> void:
    intro_overlay = ColorRect.new()
    intro_overlay.color = Color(0.01, 0.025, 0.04, 0.94)
    intro_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    hud.add_child(intro_overlay)
    var stack := VBoxContainer.new()
    stack.set_anchors_and_offsets_preset(Control.PRESET_CENTER)
    stack.position = Vector2(-280, -70)
    stack.custom_minimum_size = Vector2(560, 140)
    intro_overlay.add_child(stack)
    intro_title = Label.new()
    intro_title.text = "FLIGHT 417  /  EMERGENCY"
    intro_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    intro_title.add_theme_font_size_override("font_size", 30)
    intro_title.add_theme_color_override("font_color", Color(0.96, 0.74, 0.43))
    stack.add_child(intro_title)
    var subtitle := Label.new()
    subtitle.text = "Сбой автопилота. Турбулентность. Удар.\nТы очнулся на острове — найди припасы и укрытие."
    subtitle.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    subtitle.add_theme_font_size_override("font_size", 19)
    subtitle.add_theme_color_override("font_color", Color(0.92, 0.93, 0.87))
    stack.add_child(subtitle)
    var skip := Button.new()
    skip.text = "ПРОДОЛЖИТЬ"
    skip.pressed.connect(_finish_intro)
    stack.add_child(skip)

func _finish_intro() -> void:
    if not intro_active:
        return
    intro_active = false
    if intro_overlay:
        var tween := create_tween()
        tween.tween_property(intro_overlay, "modulate:a", 0.0, 1.0)
        tween.tween_callback(func(): if is_instance_valid(intro_overlay): intro_overlay.queue_free())
    _set_status("Ты выбрался из самолёта. Обыщи чемоданы и собери материалы.")

func _update_nearby() -> void:
    nearby = {}
    var best := 3.1
    for item in interactables:
        var node: Node3D = item.node
        if not is_instance_valid(node) or item.used:
            continue
        var d := player.global_position.distance_to(node.global_position)
        if d < best:
            best = d
            nearby = item
    for plan in placed_plans:
        if plan.filled:
            continue
        if player.global_position.distance_to(plan.node.global_position) < best:
            best = player.global_position.distance_to(plan.node.global_position)
            nearby = {"kind": "plan", "plan": plan}
    if not nearby.is_empty():
        var kind: String = nearby.kind
        if kind == "suitcase":
            _set_status("Чемодан рядом — нажми «СОБРАТЬ / ЗАПОЛНИТЬ».")
        elif kind == "tree":
            _set_status("Дерево рядом — нажми «СОБРАТЬ / ЗАПОЛНИТЬ», чтобы добыть древесину.")
        elif kind == "plan":
            _set_status("Чертёж: %s — нужны %d дерева и %d металла." % [PLAN_NAMES[nearby.plan.type], WOOD_COST[nearby.plan.type], METAL_COST[nearby.plan.type]])
    else:
        _set_status("Исследуй остров • Дерево %d • Металл %d" % [wood, metal])

func _interact() -> void:
    if intro_active:
        return
    if placing:
        _place_blueprint()
        return
    if nearby.is_empty():
        _set_status("Подойди ближе к дереву, чемодану или чертежу.")
        return
    var kind: String = nearby.kind
    if kind == "suitcase":
        var item: Dictionary = nearby
        var node: Node3D = item.node
        var tween := create_tween()
        tween.tween_property(node, "rotation:x", -0.9, 0.22)
        tween.parallel().tween_property(node, "position:y", 0.42, 0.22)
        item.used = true
        wood += 1 + randi_range(0, 2)
        metal += 1 + randi_range(0, 1)
        _set_status("Чемодан открыт: найдены материалы!")
        _refresh_panels()
    elif kind == "tree":
        if game_time - last_harvest_time < 1.1:
            _set_status("Подожди немного перед следующим действием.")
            return
        last_harvest_time = game_time
        var item: Dictionary = nearby
        var tree: Node3D = item.node
        item.used = true
        var tween := create_tween()
        tween.tween_property(tree, "rotation:z", 1.42, 0.85).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
        tween.parallel().tween_property(tree, "position:y", -0.35, 0.85)
        tween.tween_callback(func(): if is_instance_valid(tree): tree.queue_free())
        wood += 3
        _set_status("Дерево упало. Получено 3 древесины.")
        _refresh_panels()
    elif kind == "plan":
        var plan: Dictionary = nearby.plan
        var idx: int = plan.type
        if wood < WOOD_COST[idx] or metal < METAL_COST[idx]:
            _set_status("Не хватает материалов: %d дерева, %d металла." % [WOOD_COST[idx], METAL_COST[idx]])
            return
        wood -= WOOD_COST[idx]
        metal -= METAL_COST[idx]
        plan.filled = true
        var node: Node3D = plan.node
        node.queue_free()
        _build_structure(idx, plan.position)
        _set_status("Постройка готова: %s." % PLAN_NAMES[idx])
        _refresh_panels()

func _toggle_book() -> void:
    book_open = not book_open
    if book_open:
        inventory_open = false
    _refresh_panels()

func _toggle_inventory() -> void:
    inventory_open = not inventory_open
    if inventory_open:
        book_open = false
    _refresh_panels()

func _toggle_grid() -> void:
    strict_grid = not strict_grid
    _set_status("Ровная постройка: %s" % ("ВКЛ" if strict_grid else "ВЫКЛ"))

func _place_blueprint() -> void:
    if not placing:
        placing = true
    var forward := -player.global_basis.z
    var pos := player.global_position + forward * 3.4
    pos.y = 0.08
    if strict_grid:
        pos.x = roundf(pos.x / 2.5) * 2.5
        pos.z = roundf(pos.z / 2.5) * 2.5
    var ghost := _make_blueprint_ghost(selected_plan)
    ghost.position = pos
    add_child(ghost)
    placed_plans.append({"type": selected_plan, "position": pos, "node": ghost, "filled": false})
    placing = false
    _set_status("Чертёж %s оставлен на земле. Можно поставить следующий." % PLAN_NAMES[selected_plan])

func _update_ghost() -> void:
    pass

func _make_blueprint_ghost(kind: int) -> Node3D:
    var root := Node3D.new()
    root.name = "Blueprint_%s" % PLAN_NAMES[kind]
    var mat := _mat(Color(0.15, 0.95, 0.78, 0.42), 0.4)
    mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
    mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
    if kind == 0:
        _box_mesh(root, Vector3(0, 0.1, 0), Vector3(0.9, 0.12, 0.9), Color(0.2, 0.9, 0.65, 0.65), 0.2)
        _box_mesh(root, Vector3(0, 0.2, 0), Vector3(0.45, 0.22, 0.45), Color(0.9, 0.45, 0.15, 0.75), 0.2)
    elif kind == 1:
        _box_mesh(root, Vector3(0, 0.12, 0), Vector3(2.5, 0.18, 2.5), Color(0.18, 0.9, 0.72, 0.42), 0.25)
    elif kind == 2:
        _box_mesh(root, Vector3(0, 1.0, 0), Vector3(2.5, 2.0, 0.18), Color(0.18, 0.9, 0.72, 0.42), 0.2)
    elif kind == 3:
        _box_mesh(root, Vector3(0, 2.0, 0), Vector3(2.5, 0.15, 2.5), Color(0.18, 0.9, 0.72, 0.42), 0.2)
    elif kind == 4:
        _box_mesh(root, Vector3(-0.75, 1.0, 0), Vector3(0.18, 2.0, 0.18), Color(0.18, 0.9, 0.72, 0.42), 0.2)
        _box_mesh(root, Vector3(0.75, 1.0, 0), Vector3(0.18, 2.0, 0.18), Color(0.18, 0.9, 0.72, 0.42), 0.2)
        _box_mesh(root, Vector3(0, 1.95, 0), Vector3(1.6, 0.18, 0.18), Color(0.18, 0.9, 0.72, 0.42), 0.2)
    else:
        _box_mesh(root, Vector3(0, 1.0, -0.95), Vector3(2.5, 0.12, 0.12), Color(0.18, 0.9, 0.72, 0.42), 0.2)
        _box_mesh(root, Vector3(-1.2, 1.0, 0), Vector3(0.12, 2.0, 2.0), Color(0.18, 0.9, 0.72, 0.42), 0.2)
        _box_mesh(root, Vector3(1.2, 1.0, 0), Vector3(0.12, 2.0, 2.0), Color(0.18, 0.9, 0.72, 0.42), 0.2)
        _box_mesh(root, Vector3(0, 2.0, 0), Vector3(2.5, 0.12, 2.2), Color(0.18, 0.9, 0.72, 0.42), 0.2)
    return root

func _build_structure(kind: int, pos: Vector3) -> void:
    var root := Node3D.new()
    root.position = pos
    root.name = "Built_%s" % PLAN_NAMES[kind]
    add_child(root)
    if kind == 0:
        _make_fire(root)
    elif kind == 1:
        _box_mesh(root, Vector3(0, 0.12, 0), Vector3(2.5, 0.22, 2.5), Color(0.42, 0.25, 0.13), 0.22)
        for i in range(7):
            _box_mesh(root, Vector3(-1.05 + float(i) * 0.35, 0.245, 0), Vector3(0.025, 0.025, 2.4), Color(0.70, 0.47, 0.26), 0.05)
    elif kind == 2:
        _box_mesh(root, Vector3(0, 1.0, 0), Vector3(2.5, 2.0, 0.18), Color(0.44, 0.27, 0.14), 0.15)
        for i in range(5):
            _box_mesh(root, Vector3(0, 0.25 + float(i) * 0.36, -0.105), Vector3(2.42, 0.018, 0.025), Color(0.72, 0.48, 0.27), 0.02)
    elif kind == 3:
        _box_mesh(root, Vector3(0, 2.0, 0), Vector3(2.5, 0.15, 2.5), Color(0.43, 0.26, 0.13), 0.15)
        for i in range(7):
            _box_mesh(root, Vector3(-1.05 + float(i) * 0.35, 2.09, 0), Vector3(0.025, 0.025, 2.4), Color(0.72, 0.48, 0.27), 0.04)
    elif kind == 4:
        _box_mesh(root, Vector3(-0.78, 1.0, 0), Vector3(0.94, 2.0, 0.18), Color(0.43, 0.26, 0.13), 0.14)
        _box_mesh(root, Vector3(0.78, 1.0, 0), Vector3(0.94, 2.0, 0.18), Color(0.43, 0.26, 0.13), 0.14)
        _box_mesh(root, Vector3(0, 1.93, 0), Vector3(2.5, 0.14, 0.18), Color(0.43, 0.26, 0.13), 0.14)
    else:
        _box_mesh(root, Vector3(0, 0.12, 0), Vector3(2.5, 0.22, 2.5), Color(0.43, 0.26, 0.13), 0.14)
        _box_mesh(root, Vector3(-1.2, 1.0, 0), Vector3(0.14, 2.0, 2.3), Color(0.43, 0.26, 0.13), 0.14)
        _box_mesh(root, Vector3(1.2, 1.0, 0), Vector3(0.14, 2.0, 2.3), Color(0.43, 0.26, 0.13), 0.14)
        _box_mesh(root, Vector3(0, 2.0, 0), Vector3(2.5, 0.14, 2.5), Color(0.43, 0.26, 0.13), 0.14)
    _set_status("Материалы использованы. Чертёж заполнен.")

func _make_fire(parent: Node3D) -> void:
    var base := MeshInstance3D.new()
    var log_mesh := CylinderMesh.new()
    log_mesh.top_radius = 0.11
    log_mesh.bottom_radius = 0.11
    log_mesh.height = 0.95
    base.mesh = log_mesh
    base.rotation.z = PI / 2
    base.position.y = 0.18
    base.material_override = _mat(Color(0.26, 0.14, 0.07), 0.9)
    parent.add_child(base)
    var flame := MeshInstance3D.new()
    var flame_mesh := SphereMesh.new()
    flame_mesh.radius = 0.28
    flame_mesh.height = 0.72
    flame.mesh = flame_mesh
    flame.position.y = 0.52
    flame.material_override = _mat(Color(1.0, 0.31, 0.06), 0.35, 0.0, true)
    parent.add_child(flame)
    var light := OmniLight3D.new()
    light.position.y = 0.65
    light.light_color = Color(1.0, 0.37, 0.12)
    light.omni_range = 7.0
    light.light_energy = 1.6
    parent.add_child(light)
    var tw := create_tween().set_loops()
    tw.tween_property(flame, "scale", Vector3(0.9, 1.18, 0.9), 0.28)
    tw.tween_property(flame, "scale", Vector3(1.12, 0.82, 1.12), 0.31)
    tw.parallel().tween_property(light, "light_energy", 1.0, 0.3)
    tw.tween_property(light, "light_energy", 1.8, 0.27)

func _box_mesh(parent: Node3D, pos: Vector3, size: Vector3, color: Color, roughness: float = 0.8) -> MeshInstance3D:
    var mesh_node := MeshInstance3D.new()
    var mesh := BoxMesh.new()
    mesh.size = size
    mesh_node.mesh = mesh
    mesh_node.position = pos
    mesh_node.material_override = _mat(color, roughness)
    parent.add_child(mesh_node)
    return mesh_node

func _sphere(parent: Node3D, pos: Vector3, scale_value: Vector3, color: Color, roughness: float = 0.7) -> MeshInstance3D:
    var node := MeshInstance3D.new()
    var mesh := SphereMesh.new()
    mesh.radius = 1.0
    mesh.height = 2.0
    mesh.radial_segments = 32
    mesh.rings = 16
    node.mesh = mesh
    node.position = pos
    node.scale = scale_value
    node.material_override = _mat(color, roughness)
    parent.add_child(node)
    return node

func _mat(color: Color, roughness: float = 0.8, metallic: float = 0.0, emission: bool = false) -> StandardMaterial3D:
    var mat := StandardMaterial3D.new()
    mat.albedo_color = color
    mat.roughness = roughness
    mat.metallic = metallic
    if emission:
        mat.emission_enabled = true
        mat.emission = color
        mat.emission_energy_multiplier = 1.5
    return mat

func _stylebox(bg: Color, radius: int, border: Color) -> StyleBoxFlat:
    var style := StyleBoxFlat.new()
    style.bg_color = bg
    style.corner_radius_top_left = radius
    style.corner_radius_top_right = radius
    style.corner_radius_bottom_left = radius
    style.corner_radius_bottom_right = radius
    style.border_width_left = 1
    style.border_width_right = 1
    style.border_width_top = 1
    style.border_width_bottom = 1
    style.border_color = border
    style.content_margin_left = 10
    style.content_margin_right = 10
    return style

func _set_status(value: String) -> void:
    status_text = value
    if is_instance_valid(message_label):
        message_label.text = "ДЕНЬ %02d  •  ДЕРЕВО %d  •  МЕТАЛЛ %d\n%s" % [int(day_clock * 2.0) + 1, wood, metal, status_text]

func _mat_texture_placeholder() -> void:
    pass
