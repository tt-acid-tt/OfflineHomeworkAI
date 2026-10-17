using System;
using System.Globalization;
using System.Net;
using System.Net.Sockets;
using System.Text;
using UnityEngine;

public class AftermathBootstrap : MonoBehaviour
{
    const int Port=47822;
    Material floor,wall,metal,dark,cyan,red,blue,gold,doorMaterial;
    Transform world,player,partner;
    CharacterController mover;
    Camera cam;
    UdpClient udp;
    IPEndPoint host,peer;
    string page="menu",ip="192.168.1.2",netText="Одна Wi-Fi сеть • порт 47822",hint="Код 2 → 4 → 1";
    bool isHost=true,connected,solved,doorOpen;
    int room=1,progress,remoteProgress;
    float yaw,pitch,sendClock,moveX,moveY,interactClock;
    Vector3 partnerPos;
    float partnerYaw;
    Texture2D px;
    GUIStyle title,sub,body,button,label;
    readonly string[] answer={"2","4","1"};
    Vector3 leftPlate,rightPlate;
    bool leftOn,rightOn;
    Light roomLight;

    void Start(){
        Application.targetFrameRate=60; Screen.sleepTimeout=SleepTimeout.NeverSleep;
        QualitySettings.antiAliasing=2; QualitySettings.shadowDistance=28;
        floor=Mat(new Color(.10f,.14f,.19f),.32f); wall=Mat(new Color(.19f,.24f,.31f),.22f);
        metal=Mat(new Color(.35f,.43f,.52f),.65f); dark=Mat(new Color(.025f,.045f,.07f),.1f);
        cyan=Mat(new Color(.03f,.78f,.96f),.25f); red=Mat(new Color(.95f,.15f,.25f),.2f);
        blue=Mat(new Color(.10f,.40f,1f),.25f); gold=Mat(new Color(1f,.62f,.14f),.25f);
        doorMaterial=Mat(new Color(.07f,.18f,.24f),.65f);
        px=new Texture2D(1,1);px.SetPixel(0,0,Color.white);px.Apply();Styles();
        var p=new GameObject("First-person explorer");player=p.transform;player.position=new Vector3(0,.15f,-5);
        mover=p.AddComponent<CharacterController>();mover.height=1.7f;mover.radius=.3f;mover.center=new Vector3(0,.85f,0);
        var cg=new GameObject("Player camera");cg.transform.SetParent(player);cg.transform.localPosition=new Vector3(0,1.48f,0);
        cam=cg.AddComponent<Camera>();cam.fieldOfView=76;cam.nearClipPlane=.04f;cam.backgroundColor=new Color(.018f,.03f,.05f);
        partner=new GameObject("Co-op partner").transform;MakeAvatar(partner,blue);partner.gameObject.SetActive(false);
    }
    Material Mat(Color c,float m){var s=Shader.Find("Standard");if(s==null)s=Shader.Find("Diffuse");var a=new Material(s);a.color=c;if(a.HasProperty("_Metallic"))a.SetFloat("_Metallic",m);if(a.HasProperty("_Glossiness"))a.SetFloat("_Glossiness",.55f);if(a.HasProperty("_EmissionColor")){a.EnableKeyword("_EMISSION");a.SetColor("_EmissionColor",c*.1f);}return a;}
    GameObject Part(PrimitiveType t,string n,Vector3 p,Vector3 sc,Material m,Transform par=null){var g=GameObject.CreatePrimitive(t);g.name=n;g.transform.position=p;g.transform.localScale=sc;if(par)g.transform.SetParent(par,true);if(m)g.GetComponent<Renderer>().sharedMaterial=m;return g;}
    void MakeAvatar(Transform t,Material m){Part(PrimitiveType.Capsule,"Suit",t.position+Vector3.up*.85f,new Vector3(.62f,1.2f,.48f),m,t);Part(PrimitiveType.Sphere,"Helmet",t.position+Vector3.up*1.65f,new Vector3(.55f,.55f,.55f),metal,t);Part(PrimitiveType.Sphere,"Glowing visor",t.position+new Vector3(0,1.68f,.24f),new Vector3(.39f,.23f,.12f),cyan,t);Part(PrimitiveType.Cube,"Chest light",t.position+new Vector3(0,1.12f,.24f),new Vector3(.18f,.1f,.05f),gold,t);}
    void Styles(){title=new GUIStyle(GUI.skin.label){fontSize=42,fontStyle=FontStyle.Bold,alignment=TextAnchor.MiddleLeft};title.normal.textColor=Color.white;sub=new GUIStyle(GUI.skin.label){fontSize=12};sub.normal.textColor=new Color(.35f,.78f,.9f);body=new GUIStyle(GUI.skin.label){fontSize=16,wordWrap=true};body.normal.textColor=new Color(.75f,.83f,.9f);button=new GUIStyle(GUI.skin.button){fontSize=16,fontStyle=FontStyle.Bold};button.normal.textColor=Color.white;label=new GUIStyle(GUI.skin.label){fontSize=18,fontStyle=FontStyle.Bold};label.normal.textColor=Color.white;}
    void BuildRoom(){
        if(world)Destroy(world.gameObject);world=new GameObject("DUALITY 3D chamber "+room).transform;float z=(room-1)*24f;
        RenderSettings.ambientLight=new Color(.28f,.35f,.45f);RenderSettings.fog=true;RenderSettings.fogColor=new Color(.035f,.06f,.09f);RenderSettings.fogDensity=.016f;
        if(roomLight)Destroy(roomLight.gameObject);var lg=new GameObject("Warm key light");lg.transform.position=new Vector3(0,5.7f,z);roomLight=lg.AddComponent<Light>();roomLight.type=LightType.Point;roomLight.range=20;roomLight.intensity=5;roomLight.color=room==2?new Color(.25f,.68f,1):new Color(1,.72f,.43f);roomLight.shadows=LightShadows.Soft;lg.transform.SetParent(world);
        Part(PrimitiveType.Cube,"Reflective floor",new Vector3(0,-.18f,z),new Vector3(16,.36f,16),floor,world);
        Part(PrimitiveType.Cube,"Back wall",new Vector3(0,3.3f,z-8),new Vector3(16,6.6f,.4f),wall,world);
        Part(PrimitiveType.Cube,"Left wall",new Vector3(-8,3.3f,z),new Vector3(.4f,6.6f,16),wall,world);
        Part(PrimitiveType.Cube,"Right wall",new Vector3(8,3.3f,z),new Vector3(.4f,6.6f,16),wall,world);
        Part(PrimitiveType.Cube,"Ceiling",new Vector3(0,6.6f,z),new Vector3(16,.3f,16),dark,world);
        Part(PrimitiveType.Cube,"Exit wall L",new Vector3(-5.7f,3.3f,z+8),new Vector3(4.6f,6.6f,.4f),wall,world);
        Part(PrimitiveType.Cube,"Exit wall R",new Vector3(5.7f,3.3f,z+8),new Vector3(4.6f,6.6f,.4f),wall,world);
        Part(PrimitiveType.Cube,"Exit lintel",new Vector3(0,6.1f,z+8),new Vector3(7,1,.45f),wall,world);
        for(int i=0;i<8;i++){float x=-6.8f+i*1.95f;Part(PrimitiveType.Cube,"Floor seam",new Vector3(x,.012f,z),new Vector3(.025f,.02f,15),metal,world);}
        for(int i=0;i<4;i++){float x=-5.4f+i*3.6f;Part(PrimitiveType.Cube,"Ceiling luminous panel",new Vector3(x,6.38f,z-1),new Vector3(1.6f,.05f,2.2f),cyan,world);var l=new GameObject("Blue bounce light");l.transform.position=new Vector3(x,5.7f,z-1);var li=l.AddComponent<Light>();li.type=LightType.Point;li.range=5;li.intensity=1.2f;li.color=new Color(.1f,.55f,1);l.transform.SetParent(world);}
        Part(PrimitiveType.Cube,"Puzzle console",new Vector3(0,1.1f,z+4.9f),new Vector3(5.3f,1.7f,.8f),dark,world);
        Part(PrimitiveType.Cube,"Console light",new Vector3(0,2.03f,z+4.43f),new Vector3(4.7f,.09f,.07f),cyan,world);
        Material[] keys={red,gold,cyan,blue};
        for(int i=0;i<4;i++){float x=-3.3f+i*2.2f;Part(PrimitiveType.Cube,"Number key "+(i+1),new Vector3(x,2.05f,z+4.25f),new Vector3(1.05f,.7f,.42f),keys[i],world);Part(PrimitiveType.Cube,"Key inset "+i,new Vector3(x,1.67f,z+4.2f),new Vector3(.62f,.05f,.08f),metal,world);}
        leftPlate=new Vector3(-4,0,z-.4f);rightPlate=new Vector3(4,0,z-.4f);
        Part(PrimitiveType.Cylinder,"Red pressure plate",leftPlate+Vector3.up*.06f,new Vector3(2.15f,.12f,2.15f),red,world);
        Part(PrimitiveType.Cylinder,"Blue pressure plate",rightPlate+Vector3.up*.06f,new Vector3(2.15f,.12f,2.15f),blue,world);
        Part(PrimitiveType.Cube,"Exit frame left",new Vector3(-3.35f,3,z+7.7f),new Vector3(.35f,6,.5f),metal,world);
        Part(PrimitiveType.Cube,"Exit frame right",new Vector3(3.35f,3,z+7.7f),new Vector3(.35f,6,.5f),metal,world);
        Part(PrimitiveType.Cube,"Puzzle bulkhead",new Vector3(0,3,z+7.65f),new Vector3(6.35f,5.8f,.32f),doorMaterial,world);
        Part(PrimitiveType.Cube,"Door status light",new Vector3(0,5.8f,z+7.43f),new Vector3(2.2f,.12f,.05f),red,world);
        Part(PrimitiveType.Cube,"Door display",new Vector3(0,3.6f,z+7.4f),new Vector3(3.1f,.8f,.08f),dark,world);
        for(int i=0;i<3;i++)Part(PrimitiveType.Cube,"Code progress "+i,new Vector3(-.72f+i*.72f,3.6f,z+7.32f),new Vector3(.4f,.35f,.05f),metal,world);
        for(int side=-1;side<=1;side+=2)for(int i=0;i<3;i++){float zz=z-5+i*3.3f;Part(PrimitiveType.Cube,"Wall recess",new Vector3(side*7.72f,3.1f,zz),new Vector3(.06f,2.4f,2.3f),dark,world);Part(PrimitiveType.Cube,"Recess light",new Vector3(side*7.66f,4.1f,zz),new Vector3(.06f,.06f,1.5f),cyan,world);}
        progress=0;remoteProgress=0;solved=false;doorOpen=false;leftOn=false;rightOn=false;hint="Осмотрите комнату • код 2 → 4 → 1";
    }
    void Begin(bool hostMode){isHost=hostMode;page="game";room=1;BuildRoom();player.position=new Vector3(0,.15f,-5);yaw=0;pitch=0;StartNetwork();}
    void StartNetwork(){StopNetwork();try{if(isHost){udp=new UdpClient(Port);netText="Лобби создано • порт "+Port+" • сообщите другу IP телефона";}else{host=new IPEndPoint(IPAddress.Parse(ip.Trim()),Port);udp=new UdpClient(0);netText="Ищем хоста "+ip+"…";}udp.Client.Blocking=false;connected=false;}catch(Exception e){netText="Сеть: "+e.Message;}}
    void StopNetwork(){try{if(udp!=null)udp.Close();}catch{}udp=null;}
    string F(float f){return f.ToString("0.00",CultureInfo.InvariantCulture);}
    float N(string s){float v;float.TryParse(s,NumberStyles.Float,CultureInfo.InvariantCulture,out v);return v;}
    void NetworkTick(){
        if(udp==null)return;
        try{
            if(!isHost){string msg="C|"+F(player.position.x)+"|"+F(player.position.z)+"|"+F(yaw)+"|"+room+"|"+progress;byte[] b=Encoding.UTF8.GetBytes(msg);udp.Send(b,b.Length,host);}
            for(int k=0;k<4;k++){
                IPEndPoint ep=new IPEndPoint(IPAddress.Any,0);byte[] b;
                try{b=udp.Receive(ref ep);}catch(SocketException){break;}
                string[] a=Encoding.UTF8.GetString(b).Split('|');
                if(isHost && a.Length>=6 && a[0]=="C"){
                    peer=ep;connected=true;partnerPos=new Vector3(N(a[1]),0,N(a[2]));partnerYaw=N(a[3]);remoteProgress=int.Parse(a[5]);if(remoteProgress>=3)solved=true;
                    string outMsg="H|"+F(player.position.x)+"|"+F(player.position.z)+"|"+F(yaw)+"|"+F(partnerPos.x)+"|"+F(partnerPos.z)+"|"+F(partnerYaw)+"|"+room+"|"+(doorOpen?1:0)+"|"+progress;
                    byte[] outB=Encoding.UTF8.GetBytes(outMsg);udp.Send(outB,outB.Length,ep);
                }else if(!isHost && a.Length>=10 && a[0]=="H"){
                    connected=true;netText="Подключено к хосту • комната "+a[7];partner.gameObject.SetActive(true);partner.position=new Vector3(N(a[1]),0,N(a[2]));partner.rotation=Quaternion.Euler(0,N(a[3]),0);
                    int ri=int.Parse(a[7]);if(ri!=room){room=ri;BuildRoom();player.position=new Vector3(0,.15f,(room-1)*24-5);}
                    doorOpen=a[8]=="1";remoteProgress=int.Parse(a[9]);if(remoteProgress>=3)solved=true;
                }
            }
        }catch(Exception){}
    }
    void Update(){
        if(page!="game")return;
        if(!Application.isMobilePlatform){float h=Input.GetAxisRaw("Horizontal"),v=Input.GetAxisRaw("Vertical");Vector3 mv=player.right*h+player.forward*v;if(mv.sqrMagnitude>1)mv.Normalize();mover.Move(mv*3.4f*Time.deltaTime);if(Input.GetMouseButton(0)){yaw+=Input.GetAxis("Mouse X")*2.2f;pitch-=Input.GetAxis("Mouse Y")*1.7f;pitch=Mathf.Clamp(pitch,-70,70);}if(Input.GetKeyDown(KeyCode.E))Interact();}
        else if(moveX!=0||moveY!=0){Vector3 mv=player.right*moveX+player.forward*moveY;if(mv.sqrMagnitude>1)mv.Normalize();mover.Move(mv*3.2f*Time.deltaTime);}
        player.rotation=Quaternion.Euler(0,yaw,0);cam.transform.localRotation=Quaternion.Euler(pitch,0,0);
        sendClock+=Time.deltaTime;if(sendClock>.08f){sendClock=0;NetworkTick();}
        UpdatePuzzle();
        if(isHost&&doorOpen&&connected&&partner.gameObject.activeSelf){float z=(room-1)*24;if(player.position.z>z+6.5f&&partner.position.z>z+6.5f){room++;if(room>3){page="complete";StopNetwork();return;}BuildRoom();player.position=new Vector3(0,.15f,(room-1)*24-5);partner.position=new Vector3(2,0,(room-1)*24-4);}}
    }
    void UpdatePuzzle(){
        float z=(room-1)*24;bool ll=Near(player.position,new Vector3(-4,0,z-.4f),1.2f),lr=Near(player.position,new Vector3(4,0,z-.4f),1.2f);
        bool rl=partner.gameObject.activeSelf&&Near(partner.position,new Vector3(-4,0,z-.4f),1.2f),rr=partner.gameObject.activeSelf&&Near(partner.position,new Vector3(4,0,z-.4f),1.2f);
        leftOn=ll||rl;rightOn=lr||rr;
        if(world){foreach(Transform t in world.GetComponentsInChildren<Transform>()){if(t.name=="Red pressure plate")t.GetComponent<Renderer>().sharedMaterial=leftOn?gold:red;if(t.name=="Blue pressure plate")t.GetComponent<Renderer>().sharedMaterial=rightOn?gold:blue;if(t.name=="Puzzle bulkhead"){t.GetComponent<Renderer>().sharedMaterial=doorOpen?cyan:doorMaterial;t.position=new Vector3(0,doorOpen?6.1f:3,(room-1)*24+7.65f);t.localScale=new Vector3(6.35f,doorOpen?.1f:5.8f,.32f);}}}
        if(isHost&&solved&&((ll&&rr)||(lr&&rl))){doorOpen=true;hint="Дверь открыта! Идите к выходу вместе";}
        else if(!solved)hint="Код 2 → 4 → 1  •  "+progress+"/3";
        else if(!doorOpen)hint="Код принят • встаньте на плиты разных цветов";
    }
    bool Near(Vector3 a,Vector3 b,float d){return Vector2.Distance(new Vector2(a.x,a.z),new Vector2(b.x,b.z))<d;}
    void PressKey(int key){if(solved)return;int expected=int.Parse(answer[progress]);if(key==expected){progress++;if(progress>=3){solved=true;hint="Код принят! Теперь нажмите плиты вместе";}}else{progress=0;hint="Ошибка кода • попробуйте снова";}}
    void Interact(){if(Time.unscaledTime<interactClock)return;interactClock=Time.unscaledTime+.2f;float z=(room-1)*24;if(Near(player.position,new Vector3(0,0,z+3.5f),6))PressKey(progress==0?2:progress==1?4:1);}
    void OnGUI(){
        if(px==null)return;
        if(page!="game"){Menu();return;}
        Box(new Rect(18,18,Mathf.Min(430,Screen.width*.64f),100),new Color(.02f,.04f,.065f,.84f));GUI.Label(new Rect(32,25,370,28),"DUALITY  /  ROOM 0"+room,label);GUI.Label(new Rect(32,58,390,24),hint,sub);GUI.Label(new Rect(32,82,390,22),connected?"● PARTNER CONNECTED":"○ ОЖИДАНИЕ ИГРОКА",sub);
        if(Application.isMobilePlatform){
            float s=58,bx=18,by=Screen.height-s*2-20;TouchBtn(new Rect(bx+s,by,s,s),"▲",0,1);TouchBtn(new Rect(bx,by+s,s,s),"◀",-1,0);TouchBtn(new Rect(bx+s,by+s,s,s),"▼",0,-1);TouchBtn(new Rect(bx+s*2,by+s,s,s),"▶",1,0);
            Button(new Rect(Screen.width-152,Screen.height-100,132,76),"ДЕЙСТВИЕ",new Color(.02f,.42f,.58f,.94f),Interact);
            if(!solved&&Near(player.position,new Vector3(0,0,(room-1)*24+3.5f),6)){for(int i=1;i<=4;i++){int k=i;Button(new Rect(Screen.width-292+(i-1)*65,Screen.height-176,58,55),i.ToString(),new Color(.08f,.21f,.31f,.96f),()=>PressKey(k));}}
            if(Event.current.type==EventType.MouseDrag&&Event.current.mousePosition.x>Screen.width*.35f){yaw+=Event.current.delta.x*.18f;pitch=Mathf.Clamp(pitch-Event.current.delta.y*.14f,-70,70);}if(Event.current.type==EventType.MouseUp){moveX=0;moveY=0;}
        }else GUI.Label(new Rect(20,Screen.height-35,500,25),"WASD движение • мышь обзор • E действие",sub);
    }
    void TouchBtn(Rect r,string t,float x,float y){Box(r,new Color(.035f,.11f,.17f,.9f));if(GUI.RepeatButton(r,t,button)){moveX=x;moveY=y;}}
    void Menu(){
        Box(new Rect(0,0,Screen.width,Screen.height),new Color(.014f,.025f,.043f,1));
        for(int i=0;i<12;i++)Box(new Rect((i*173+Time.unscaledTime*16)%Screen.width,Screen.height*.08f+i*Screen.height*.07f,Screen.width*.38f,2),new Color(.02f,.32f,.43f,.24f));
        float w=Mathf.Min(650,Screen.width-34),x=(Screen.width-w)/2,y=Screen.height*.09f,h=Screen.height*.82f;Box(new Rect(x,y,w,h),new Color(.035f,.055f,.085f,.97f));Box(new Rect(x,y,5,h),new Color(.04f,.78f,.94f,1));
        GUI.Label(new Rect(x+30,y+22,w-60,60),"DUALITY",title);GUI.Label(new Rect(x+33,y+82,w-66,26),"ESCAPE TOGETHER  /  3D CO-OP PUZZLE",sub);Box(new Rect(x+32,y+119,w-64,1),new Color(.16f,.29f,.38f,1));
        if(page=="menu"){
            GUI.Label(new Rect(x+33,y+142,w-66,65),"ДВЕ ГОЛОВЫ. ОДНА КОМНАТА.\nТолько вместе вы найдёте выход.",body);
            Button(new Rect(x+33,y+220,w-66,56),"СОЗДАТЬ ЛОББИ  •  ХОСТ",new Color(.03f,.42f,.58f,1),()=>{isHost=true;page="lobby";});
            Button(new Rect(x+33,y+289,w-66,56),"ПОДКЛЮЧИТЬСЯ К ДРУГУ",new Color(.11f,.18f,.27f,1),()=>page="join");
            Button(new Rect(x+33,y+358,w-66,46),"КАК ИГРАТЬ",new Color(.08f,.13f,.2f,1),()=>page="help");
            GUI.Label(new Rect(x+33,y+h-36,w-66,20),"UNITY 6 • 3D FIRST PERSON • LOCAL WI-FI",sub);
        }else if(page=="lobby"){
            GUI.Label(new Rect(x+33,y+145,w-66,90),"Игрок 1 — КРАСНЫЙ\nИгрок 2 — СИНИЙ\nПорт локальной сети: "+Port,body);
            Button(new Rect(x+33,y+260,w-66,58),"НАЧАТЬ ИГРУ",new Color(.03f,.42f,.58f,1),()=>Begin(true));
            Button(new Rect(x+33,y+333,w-66,48),"НАЗАД",new Color(.08f,.13f,.2f,1),()=>page="menu");
        }else if(page=="join"){
            GUI.Label(new Rect(x+33,y+143,w-66,28),"IP-АДРЕС ТЕЛЕФОНА ХОСТА",body);ip=GUI.TextField(new Rect(x+33,y+181,w-66,48),ip);
            GUI.Label(new Rect(x+33,y+239,w-66,55),"Пример: 192.168.1.24\nОба телефона должны быть в одной Wi-Fi сети.",sub);
            Button(new Rect(x+33,y+310,w-66,56),"ПОДКЛЮЧИТЬСЯ",new Color(.03f,.42f,.58f,1),()=>Begin(false));
            Button(new Rect(x+33,y+379,w-66,44),"НАЗАД",new Color(.08f,.13f,.2f,1),()=>page="menu");
        }else if(page=="help"){
            GUI.Label(new Rect(x+33,y+142,w-66,220),"01  Осмотрите объёмную 3D-комнату.\n\n02  Введите код 2 → 4 → 1.\n\n03  Оба игрока одновременно занимают плиты разных цветов.\n\n04  Идите к двери вместе. Всего три комнаты.",body);
            Button(new Rect(x+33,y+380,w-66,48),"ПОНЯТНО",new Color(.03f,.42f,.58f,1),()=>page="menu");
        }else if(page=="complete"){
            GUI.Label(new Rect(x+33,y+150,w-66,95),"ВЫ СБЕЖАЛИ!\nВсе три комнаты пройдены вместе.",title);
            Button(new Rect(x+33,y+300,w-66,55),"В МЕНЮ",new Color(.03f,.42f,.58f,1),()=>page="menu");
        }
        GUI.Label(new Rect(x+33,Screen.height-31,w-66,22),netText,sub);
    }
    void Box(Rect r,Color c){Color old=GUI.color;GUI.color=c;GUI.DrawTexture(r,px);GUI.color=old;}
    void Button(Rect r,string t,Color c,Action a){Box(r,c);if(GUI.Button(r,t,button))a();}
    void OnApplicationQuit(){StopNetwork();}
    void OnDestroy(){StopNetwork();}
}