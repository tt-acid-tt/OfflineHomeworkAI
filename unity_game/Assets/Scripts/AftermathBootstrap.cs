using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

public class AftermathBootstrap : MonoBehaviour
{
    Material ground, bark, leaves, metal, glass, orange, cloth, cyan;
    Transform player;
    Camera cam;
    Text status;
    int wood, scrap, supplies;
    bool blueprint, campfire;
    float yaw, pitch = 12f, timeOfDay = .22f;
    Vector2 lastLook;
    readonly List<GameObject> loot = new List<GameObject>();

    void Start()
    {
        Application.targetFrameRate = 60;
        Screen.sleepTimeout = SleepTimeout.NeverSleep;
        ground = Mat(new Color(.19f,.29f,.17f),0);
        bark = Mat(new Color(.23f,.13f,.07f),0);
        leaves = Mat(new Color(.10f,.30f,.15f),0);
        metal = Mat(new Color(.34f,.40f,.44f),.65f);
        glass = Mat(new Color(.025f,.13f,.20f),.7f);
        orange = Mat(new Color(.77f,.25f,.07f),.1f);
        cloth = Mat(new Color(.10f,.18f,.20f),0);
        cyan = Mat(new Color(.1f,.8f,.95f),.25f);
        MakeWorld(); MakePlayer(); MakeHud();
    }

    Material Mat(Color c,float metallic)
    {
        Shader s=Shader.Find("Standard");
        if(s==null)s=Shader.Find("Universal Render Pipeline/Lit");
        Material m=new Material(s);m.color=c;
        if(m.HasProperty("_Metallic"))m.SetFloat("_Metallic",metallic);
        if(m.HasProperty("_Glossiness"))m.SetFloat("_Glossiness",.45f);
        return m;
    }

    GameObject Part(PrimitiveType type,string n,Vector3 p,Vector3 scale,Material m,Transform parent=null)
    {
        GameObject g=GameObject.CreatePrimitive(type);g.name=n;g.transform.position=p;g.transform.localScale=scale;
        if(parent)g.transform.SetParent(parent,true);
        if(m)g.GetComponent<Renderer>().sharedMaterial=m;
        return g;
    }

    void MakeWorld()
    {
        RenderSettings.ambientLight=new Color(.5f,.54f,.59f);
        RenderSettings.fog=true;RenderSettings.fogColor=new Color(.59f,.72f,.82f);RenderSettings.fogDensity=.003f;
        Material sky=new Material(Shader.Find("Skybox/Procedural"));
        if(sky.shader!=null){sky.SetFloat("_AtmosphereThickness",1.15f);sky.SetFloat("_Exposure",1.2f);RenderSettings.skybox=sky;}
        GameObject sun=new GameObject("Sun");Light light=sun.AddComponent<Light>();light.type=LightType.Directional;light.intensity=1.2f;light.color=new Color(1,.88f,.73f);sun.transform.rotation=Quaternion.Euler(42,-32,0);
        Part(PrimitiveType.Plane,"Forest floor",new Vector3(0,-.12f,0),new Vector3(80,1,80),ground);
        for(int i=0;i<18;i++){float a=i*Mathf.PI*2/18;Part(PrimitiveType.Sphere,"Distant green hills",new Vector3(Mathf.Cos(a)*53,-2,Mathf.Sin(a)*53),new Vector3(18,8+Mathf.Sin(i*2)*2,18),ground);}
        MakePlane();
        for(int i=0;i<90;i++){Vector2 p=Random.insideUnitCircle*39;if(p.magnitude>8)MakeTree(new Vector3(p.x,0,p.y));}
        for(int i=0;i<15;i++){Vector2 p=Random.insideUnitCircle*18;if(p.magnitude<5)p=p.normalized*7;MakeLoot(new Vector3(p.x,.35f,p.y),i%3);}
    }

    void MakePlane()
    {
        Transform root=new GameObject("Crashed passenger aircraft").transform;root.position=new Vector3(4,1.5f,10);root.rotation=Quaternion.Euler(0,20,0);
        Part(PrimitiveType.Cylinder,"Long cylindrical fuselage",new Vector3(4,1.6f,10),new Vector3(2,8.4f,2),metal,root).transform.localRotation=Quaternion.Euler(90,0,0);
        Part(PrimitiveType.Sphere,"Rounded nose",new Vector3(4,1.6f,18.3f),new Vector3(1.95f,1.7f,2.3f),metal,root);
        Part(PrimitiveType.Cube,"Left wing",new Vector3(-2.3f,1.2f,10),new Vector3(12,.22f,2.7f),metal,root);
        Part(PrimitiveType.Cube,"Right wing",new Vector3(10.3f,1.2f,10),new Vector3(12,.22f,2.7f),metal,root);
        Part(PrimitiveType.Cube,"Tail wing",new Vector3(4,2.8f,2.7f),new Vector3(6,.2f,1.3f),metal,root);
        Part(PrimitiveType.Cube,"Tail fin",new Vector3(4,3.7f,2.7f),new Vector3(.25f,2.3f,1.4f),metal,root);
        for(int row=0;row<8;row++)for(int side=-1;side<=1;side+=2)
            Part(PrimitiveType.Sphere,"Blue cabin window",new Vector3(4+side*1.02f,1.95f,4.2f+row*1.42f),new Vector3(.11f,.36f,.46f),glass,root);
        for(int side=-1;side<=1;side+=2)Part(PrimitiveType.Cylinder,"Engine nacelle",new Vector3(4+side*3.2f,.85f,10),new Vector3(.75f,1.8f,.75f),metal,root).transform.localRotation=Quaternion.Euler(90,0,0);
    }

    void MakeTree(Vector3 p)
    {
        float h=Random.Range(9f,15f);Transform t=new GameObject("Tall tree").transform;t.position=p;
        Part(PrimitiveType.Cylinder,"Textured brown trunk",p+Vector3.up*h*.34f,new Vector3(.55f,h*.34f,.55f),bark,t);
        Part(PrimitiveType.Sphere,"Lower canopy",p+Vector3.up*h*.62f,new Vector3(4.5f,4.8f,4.5f),leaves,t);
        Part(PrimitiveType.Sphere,"Upper canopy",p+Vector3.up*h*.82f,new Vector3(3.5f,3.7f,3.5f),leaves,t);
    }

    void MakeLoot(Vector3 p,int kind)
    {
        Material m=kind==0?orange:kind==1?metal:cloth;
        GameObject g=Part(PrimitiveType.Cube,kind==0?"Supply suitcase":kind==1?"Metal scraps":"Food supplies",p,new Vector3(.9f,.62f,.65f),m);
        g.transform.rotation=Quaternion.Euler(0,Random.Range(0,360),0);
        Part(PrimitiveType.Cube,"Handle",p+Vector3.up*.34f,new Vector3(.25f,.08f,.12f),metal,g.transform);
        loot.Add(g);
    }

    void MakePlayer()
    {
        player=new GameObject("Survivor").transform;player.position=Vector3.zero;
        Part(PrimitiveType.Capsule,"Survivor body",new Vector3(0,.9f,0),new Vector3(.62f,1.8f,.62f),cloth,player);
        cam=new GameObject("Camera").AddComponent<Camera>();cam.transform.SetParent(player);
        cam.transform.localPosition=new Vector3(0,1.55f,-3.2f);cam.transform.localRotation=Quaternion.Euler(pitch,0,0);cam.fieldOfView=72;
    }

    void MakeHud()
    {
        GameObject go=new GameObject("AFTERMATH HUD");Canvas c=go.AddComponent<Canvas>();c.renderMode=RenderMode.ScreenSpaceOverlay;
        CanvasScaler scaler=go.AddComponent<CanvasScaler>();scaler.uiScaleMode=CanvasScaler.ScaleMode.ScaleWithScreenSize;scaler.referenceResolution=new Vector2(1080,1920);go.AddComponent<GraphicRaycaster>();
        status=Text(c.transform,"AFTERMATH  •  Найди ресурсы и построй укрытие",new Vector2(.5f,1),new Vector2(.94f,100),new Vector2(0,-70),30);
        Text(c.transform,"Левая часть экрана — движение • правая — обзор",new Vector2(.5f,0),new Vector2(.94f,70),new Vector2(0,30),20);
        AddButton(c.transform,"СОБРАТЬ",new Vector2(.81f,.23f),()=>Collect());
        AddButton(c.transform,"ЧЕРТЁЖ",new Vector2(.81f,.14f),()=>{blueprint=!blueprint;Say(blueprint?"Нажми по земле, чтобы поставить основу":"Режим чертежа выключен");});
        AddButton(c.transform,"КОСТЁР",new Vector2(.5f,.14f),()=>BuildFire());
        AddButton(c.transform,"ДЕНЬ / НОЧЬ",new Vector2(.19f,.14f),()=>timeOfDay=(timeOfDay+.5f)%1f);
    }

    Text Text(Transform parent,string value,Vector2 anchor,Vector2 size,Vector2 offset,int font)
    {
        GameObject g=new GameObject(value);g.transform.SetParent(parent,false);RectTransform r=g.AddComponent<RectTransform>();
        r.anchorMin=r.anchorMax=anchor;r.sizeDelta=size;r.anchoredPosition=offset;
        Text t=g.AddComponent<Text>();t.font=Resources.GetBuiltinResource<Font>("Arial.ttf");t.text=value;t.fontSize=font;t.color=Color.white;t.alignment=TextAnchor.MiddleCenter;t.horizontalOverflow=HorizontalWrapMode.Wrap;return t;
    }
    void AddButton(Transform parent,string title,Vector2 anchor,UnityEngine.Events.UnityAction action)
    {
        GameObject g=new GameObject(title);g.transform.SetParent(parent,false);RectTransform r=g.AddComponent<RectTransform>();
        r.anchorMin=r.anchorMax=anchor;r.sizeDelta=new Vector2(260,105);
        Image im=g.AddComponent<Image>();im.color=new Color(.025f,.12f,.16f,.9f);
        Button b=g.AddComponent<Button>();b.targetGraphic=im;b.onClick.AddListener(action);
        Text(g.transform,title,new Vector2(.5f,.5f),new Vector2(240,90),Vector2.zero,24);
    }

    void Update()
    {
        Vector3 move=Vector3.zero;
        if(Input.GetKey(KeyCode.W)||Input.GetKey(KeyCode.UpArrow))move+=player.forward;
        if(Input.GetKey(KeyCode.S)||Input.GetKey(KeyCode.DownArrow))move-=player.forward;
        if(Input.GetKey(KeyCode.A)||Input.GetKey(KeyCode.LeftArrow))move-=player.right;
        if(Input.GetKey(KeyCode.D)||Input.GetKey(KeyCode.RightArrow))move+=player.right;
        for(int i=0;i<Input.touchCount;i++)
        {
            Touch t=Input.GetTouch(i);
            if(t.position.x<Screen.width*.48f&&t.phase!=TouchPhase.Ended&&t.phase!=TouchPhase.Canceled)
            {Vector2 n=new Vector2((t.position.x-Screen.width*.23f)/(Screen.width*.23f),(t.position.y-Screen.height*.25f)/(Screen.height*.25f));move+=player.forward*Mathf.Clamp(n.y,-1,1)+player.right*Mathf.Clamp(n.x,-1,1);}
            else if(t.position.x>=Screen.width*.48f)
            {if(t.phase==TouchPhase.Began)lastLook=t.position;else if(t.phase==TouchPhase.Moved){Vector2 d=t.position-lastLook;yaw+=d.x*.12f;pitch=Mathf.Clamp(pitch-d.y*.09f,-20,50);lastLook=t.position;}}
        }
        player.position+=Vector3.ClampMagnitude(move,1)*4.2f*Time.deltaTime;
        player.position=new Vector3(Mathf.Clamp(player.position.x,-43,43),0,Mathf.Clamp(player.position.z,-43,43));
        player.rotation=Quaternion.Euler(0,yaw,0);cam.transform.localRotation=Quaternion.Euler(pitch,0,0);
        timeOfDay=(timeOfDay+Time.deltaTime*.002f)%1f;
        GameObject sun=GameObject.Find("Sun");if(sun){Light l=sun.GetComponent<Light>();sun.transform.rotation=Quaternion.Euler(timeOfDay*360-90,-32,0);l.intensity=Mathf.Lerp(.12f,1.25f,Mathf.Clamp01(Mathf.Sin(timeOfDay*Mathf.PI*2)*.5f+.5f));}
        if(blueprint&&Input.GetMouseButtonDown(0)){Ray ray=cam.ScreenPointToRay(Input.mousePosition);if(Physics.Raycast(ray,out RaycastHit hit,35)){Vector3 p=hit.point;p.x=Mathf.Round(p.x/2)*2;p.z=Mathf.Round(p.z/2)*2;Part(PrimitiveType.Cube,"Shelter blueprint foundation",new Vector3(p.x,.12f,p.z),new Vector3(3,.18f,3),cyan);Say("Основа укрытия установлена");}}
    }

    void Collect()
    {
        GameObject found=null;float closest=3.6f;
        foreach(GameObject g in loot){if(!g)continue;float d=Vector3.Distance(player.position,g.transform.position);if(d<closest){closest=d;found=g;}}
        if(!found){Say("Подойди ближе к чемодану или ресурсам");return;}
        if(found.name.Contains("Suitcase"))supplies+=2;else if(found.name.Contains("Metal"))scrap+=2;else wood++;
        loot.Remove(found);Destroy(found);Say($"Дерево {wood}  •  Металл {scrap}  •  Запасы {supplies}");
    }
    void BuildFire()
    {
        if(campfire){Say("Костёр уже построен");return;}campfire=true;Vector3 p=player.position+player.forward*2;
        Part(PrimitiveType.Cylinder,"Campfire stone ring",p+Vector3.up*.1f,new Vector3(.8f,.12f,.8f),metal);
        GameObject flame=Part(PrimitiveType.Sphere,"Campfire glow",p+Vector3.up*.5f,new Vector3(.4f,.65f,.4f),orange);
        Light l=flame.AddComponent<Light>();l.type=LightType.Point;l.range=9;l.intensity=2;l.color=new Color(1,.3f,.08f);Say("Костёр разожжён");
    }
    void Say(string s){if(status)status.text=s;}
}