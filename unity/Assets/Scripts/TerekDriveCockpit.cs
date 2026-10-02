using UnityEngine;
using UnityEngine.UI;
using System.Collections.Generic;

public class TerekDriveCockpit : MonoBehaviour
{
    public float speedKmh = 96f;
    public bool demoMotion = true;

    readonly List<Transform> particles = new();
    Transform needle;
    Text speedText;
    Text gearText;
    Material glowMat;
    float t;

    void Start()
    {
        Application.targetFrameRate = 60;
        BuildCamera();
        BuildWorld();
    }

    void Update()
    {
        t += Time.deltaTime;
        if (demoMotion)
            speedKmh = 55f + Mathf.Abs(Mathf.Sin(t * 0.55f)) * 155f + Mathf.Sin(t * 1.7f) * 8f;

        UpdateGauge();
        UpdateParticles();
    }

    void BuildCamera()
    {
        var existing = Camera.main;
        if (existing != null) return;

        var go = new GameObject("Main Camera");
        var camera = go.AddComponent<Camera>();
        go.tag = "MainCamera";
        go.transform.position = new Vector3(0f, 0.2f, -8f);
        go.transform.LookAt(new Vector3(0f, 0f, 1.7f));
        camera.fieldOfView = 48f;
        camera.nearClipPlane = 0.03f;
        camera.farClipPlane = 60f;
        camera.clearFlags = CameraClearFlags.SolidColor;
        camera.backgroundColor = new Color(0.003f, 0.005f, 0.009f);
    }

    void BuildWorld()
    {
        RenderSettings.ambientIntensity = 0.12f;
        RenderSettings.reflectionIntensity = 0.25f;
        RenderSettings.fog = true;
        RenderSettings.fogDensity = 0.018f;

        var light = new GameObject("CockpitKeyLight").AddComponent<Light>();
        light.type = LightType.Point;
        light.range = 14f;
        light.intensity = 18f;
        light.transform.position = new Vector3(0, 2.5f, 2.5f);

        glowMat = MakeMat(new Color(0.8f, 0.02f, 0.015f), 8f);

        BuildCockpit();
        BuildGauge();
        BuildWeather();
        BuildHud();
    }

    void BuildCockpit()
    {
        CreateCube("Dashboard", new Vector3(0, -1.2f, 2.1f), new Vector3(7.5f, 1.5f, 1.4f), new Color(.018f,.02f,.025f));
        CreateCube("LeftPod", new Vector3(-4.1f, .0f, 2.6f), new Vector3(1.1f, 3.2f, 1.0f), new Color(.012f,.014f,.018f));
        CreateCube("RightPod", new Vector3(4.1f, .0f, 2.6f), new Vector3(1.1f, 3.2f, 1.0f), new Color(.012f,.014f,.018f));

        for (int i = 0; i < 6; i++)
        {
            var bar = CreateCube("AmbientBar", new Vector3(-3.2f + i * 1.28f, -.25f, 1.35f),
                new Vector3(1.0f,.035f,.035f), new Color(.65f,.015f,.01f));
            bar.GetComponent<Renderer>().material = glowMat;
        }
    }

    void BuildGauge()
    {
        var root = new GameObject("3D_Speedometer");
        root.transform.position = new Vector3(0, .35f, 1.1f);

        CreateCylinder(root.transform, "GaugeBody", .0f, 1.75f, .28f, new Color(.008f,.009f,.012f));
        CreateCylinder(root.transform, "GaugeGlass", .02f, 1.52f, .04f, new Color(.025f,.03f,.04f, .65f), true);

        for (int i = 0; i < 60; i++)
        {
            float a = i * 6f * Mathf.Deg2Rad;
            float r = 1.42f;
            float len = i % 5 == 0 ? .18f : .09f;
            var tick = CreateCube("Tick", root.transform,
                new Vector3(Mathf.Sin(a)*r, Mathf.Cos(a)*r, -.17f),
                new Vector3(.025f, len, .025f), i > 48 ? new Color(.95f,.02f,.01f) : new Color(.55f,.58f,.62f));
            tick.localRotation = Quaternion.Euler(0, 0, -i * 6f);
        }

        for (int i = 0; i < 24; i++)
        {
            float a = i * 15f * Mathf.Deg2Rad;
            var led = CreateSphere(root.transform, "LED", new Vector3(Mathf.Sin(a)*1.62f, Mathf.Cos(a)*1.62f, -.2f), .035f,
                i > 18 ? new Color(.8f,.01f,.005f) : new Color(.05f,.35f,.7f));
            led.GetComponent<Renderer>().material.EnableKeyword("_EMISSION");
        }

        needle = CreateCube("Needle", root.transform, new Vector3(0,.55f,-.25f), new Vector3(.035f,1.05f,.035f), new Color(.95f,.015f,.01f)).transform;
        needle.localRotation = Quaternion.Euler(0,0,-38f);

        CreateSphere(root.transform, "Hub", Vector3.zero + Vector3.forward * -.28f, .16f, new Color(.12f,.12f,.14f));

        var canvas = new GameObject("GaugeCanvas").AddComponent<Canvas>();
        canvas.renderMode = RenderMode.WorldSpace;
        canvas.transform.SetParent(root.transform, false);
        canvas.transform.localPosition = new Vector3(0,-.25f,-.34f);
        canvas.transform.localRotation = Quaternion.Euler(0,0,0);
        canvas.transform.localScale = Vector3.one * .0025f;

        speedText = MakeText(canvas.transform, "000", 78, new Vector2(0,-35), Color.white);
        gearText = MakeText(canvas.transform, "D", 30, new Vector2(0,-115), new Color(.8f,.82f,.86f));
    }

    void UpdateGauge()
    {
        float normalized = Mathf.Clamp01(speedKmh / 240f);
        float z = Mathf.Lerp(38f, -218f, normalized);
        if (needle) needle.localRotation = Quaternion.Euler(0,0,z);
        if (speedText) speedText.text = Mathf.RoundToInt(speedKmh).ToString("000");
        if (gearText) gearText.text = speedKmh > 210 ? "S+" : speedKmh > 120 ? "S" : "D";
    }

    void BuildWeather()
    {
        for (int i = 0; i < 80; i++)
        {
            var p = CreateCube("SnowParticle", new Vector3(Random.Range(-7f,7f), Random.Range(-1f,6f), Random.Range(.5f,5f)),
                Vector3.one * Random.Range(.012f,.035f), new Color(.65f,.8f,1f));
            particles.Add(p.transform);
        }
    }

    void UpdateParticles()
    {
        foreach (var p in particles)
        {
            p.position += Vector3.down * Time.deltaTime * (0.45f + p.position.z * .08f);
            p.position += Vector3.right * Mathf.Sin(t * 1.2f + p.position.z) * Time.deltaTime * .08f;
            if (p.position.y < -1.8f) p.position = new Vector3(Random.Range(-7f,7f), Random.Range(4f,6f), Random.Range(.5f,5f));
        }
    }

    void BuildHud()
    {
        var canvas = new GameObject("HUD").AddComponent<Canvas>();
        canvas.renderMode = RenderMode.ScreenSpaceOverlay;
        MakeText(canvas.transform, "TEREK DRIVE", 30, new Vector2(-220, -80), new Color(.8f,.82f,.86f));
        MakeText(canvas.transform, "SPORT // GPS // LIVE", 14, new Vector2(-220, -115), new Color(.5f,.55f,.62f));
        MakeText(canvas.transform, "N 43° 18'  •  E 45° 41'", 14, new Vector2(220, -80), new Color(.5f,.55f,.62f));
    }

    Material MakeMat(Color c, float emission)
    {
        var m = new Material(Shader.Find("Universal Render Pipeline/Lit"));
        m.color = c;
        m.EnableKeyword("_EMISSION");
        m.SetColor("_EmissionColor", c * emission);
        return m;
    }

    GameObject CreateCube(string n, Vector3 pos, Vector3 scale, Color color)
        => CreateCube(null, n, pos, scale, color);

    GameObject CreateCube(Transform parent, string n, Vector3 pos, Vector3 scale, Color color)
    {
        var g = GameObject.CreatePrimitive(PrimitiveType.Cube);
        g.name = n;
        if (parent) g.transform.SetParent(parent, false);
        g.transform.localPosition = pos;
        g.transform.localScale = scale;
        g.GetComponent<Renderer>().material = MakeMat(color, 0.5f);
        return g;
    }

    GameObject CreateCylinder(Transform parent, string n, float x, float radius, float height, Color color, bool transparent=false)
    {
        var g = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
        g.name = n;
        g.transform.SetParent(parent, false);
        g.transform.localPosition = new Vector3(x,0,0);
        g.transform.localRotation = Quaternion.Euler(90,0,0);
        g.transform.localScale = new Vector3(radius, height, radius);
        g.GetComponent<Renderer>().material = MakeMat(color, transparent ? 1.2f : .2f);
        return g;
    }

    GameObject CreateSphere(Transform parent, string n, Vector3 pos, float radius, Color color)
    {
        var g = GameObject.CreatePrimitive(PrimitiveType.Sphere);
        g.name = n;
        g.transform.SetParent(parent, false);
        g.transform.localPosition = pos;
        g.transform.localScale = Vector3.one * radius;
        g.GetComponent<Renderer>().material = MakeMat(color, 2f);
        return g;
    }

    Text MakeText(Transform parent, string value, int size, Vector2 pos, Color color)
    {
        var g = new GameObject(value);
        g.transform.SetParent(parent, false);
        var t = g.AddComponent<Text>();
        t.text = value;
        t.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        t.fontSize = size;
        t.alignment = TextAnchor.MiddleCenter;
        t.color = color;
        t.rectTransform.sizeDelta = new Vector2(700, 120);
        t.rectTransform.anchoredPosition = pos;
        return t;
    }
}
