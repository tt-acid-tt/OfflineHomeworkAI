package com.acid.duality;

import android.app.AlertDialog;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ThreeDGameView extends GLSurfaceView {
    volatile int screen=0; // 0 menu, 1 room lobby, 2 game
    volatile int stage=0, moves=0, wood=0, scrap=0;
    volatile boolean wreckSearched=false, campfireBuilt=false, blueprintMode=false, shelterBuilt=false;
    volatile boolean axeTaken=false, inventoryOpen=false, blueprintBookOpen=false;
    volatile int blueprintType=0;
    volatile float dayClock=0f;
    volatile float chopAnim=0f;
    final boolean[] builtTypes=new boolean[6];
    final float[] buildX=new float[6], buildZ=new float[6];
    volatile float campfireX=0,campfireZ=0, shelterX=0,shelterZ=0;
    volatile float sceneTime=0f;
    volatile boolean skipHold=false;
    volatile long skipHoldStart=0L;
    volatile int nearTreeIndex=-1;
    final int[] treeHits=new int[54];
    volatile int choiceIndex=0, peerChoice=0, resultCode=0, lastPeerResult=0;
    volatile boolean started=false, solo=false, ready=false;
    volatile float stickX=0, stickY=0, playerX=0, playerZ=0, yaw=0, pitch=0;
    volatile float peerX=0, peerZ=-7, peerYaw=0;
    volatile boolean peerReady=false, confirmed=false;
    volatile String notice="Выживание начинается после крушения";
    volatile boolean nearInteract=false;
    final LanSession lan = new LanSession();
    final World renderer;
    final Hud hud;

    public ThreeDGameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer=new World(this);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        hud=new Hud(context,this);
        setFocusable(true);
    }
    void hostRoom() {
        solo=false; started=false; screen=1; stage=0; ready=false; moves=0;
        playerX=0; playerZ=0; yaw=0; pitch=0; sceneTime=0;
        lan.host(); notice="Остров ждёт"; hud.invalidate();
    }
    void joinRoom(String ip) {
        solo=false; started=false; screen=1; stage=0; ready=false;
        playerX=0; playerZ=0; yaw=0; pitch=0; sceneTime=0;
        lan.join(ip); notice="Подключение к острову..."; hud.invalidate();
    }
    void beginSolo() {
        lan.close(); solo=true; started=true; screen=3; stage=0; ready=false; moves=0;
        playerX=0; playerZ=0; yaw=0; pitch=0; sceneTime=0; stickX=stickY=0;
        notice="Крушение. Очнись и выберись из самолёта.";
        hud.invalidate();
    }
    void startOnlineGame() {
        if(lan.hosting && lan.connected) { started=true; screen=3; stage=0; ready=false; playerX=0;playerZ=0;yaw=0;pitch=0;sceneTime=0;notice="Крушение. Выживайте вместе."; }
        hud.invalidate();
    }
    void placeBlueprint() {
        if(screen!=2 || !blueprintMode) return;
        int[] needWood={3,4,3,2,6,8}; int[] needScrap={1,1,1,0,2,3};
        if(wood<needWood[blueprintType] || scrap<needScrap[blueprintType]) { notice="Не хватает ресурсов для: "+(new String[]{"костра","фундамента","стены","потолка","дверного проёма","укрытия"})[blueprintType]; hud.invalidate(); return; }
        wood-=needWood[blueprintType]; scrap-=needScrap[blueprintType];
        float bx=playerX+(float)Math.sin(yaw)*3.0f,bz=playerZ-(float)Math.cos(yaw)*3.0f;
        builtTypes[blueprintType]=true;buildX[blueprintType]=bx;buildZ[blueprintType]=bz;
        if(blueprintType==0){campfireBuilt=true;campfireX=bx;campfireZ=bz;}
        else {shelterBuilt=true;shelterX=bx;shelterZ=bz;}
        blueprintMode=false; blueprintBookOpen=false;
        notice="Постройка размещена: "+new String[]{"костёр","фундамент","стена","потолок","дверной проём","укрытие"][blueprintType]; hud.invalidate();
    }
    void skipCutscene() { if(screen==3){screen=2;sceneTime=12.1f;playerX=0;playerZ=0;yaw=0;pitch=0;stickX=stickY=0;notice="Ты очнулся у места крушения. Осмотрись и выживи.";hud.invalidate();} }
    void craftCampfire() {
        if(screen!=2) return; blueprintBookOpen=true; blueprintType=0; blueprintMode=false; hud.invalidate();
    }
    void backToMenu() {
        lan.close(); screen=0; started=false; solo=false; ready=false; stickX=stickY=0; sceneTime=0;
        notice="Выживание начинается после крушения"; hud.invalidate();
    }
    void selectPrevious() { if(screen==2 && nearInteract && !confirmed){choiceIndex=(choiceIndex+3)%4;hud.invalidate();} }
    void selectNext() { if(screen==2 && nearInteract && !confirmed){choiceIndex=(choiceIndex+1)%4;hud.invalidate();} }
    void interact() {
        if(screen!=2 || !nearInteract) return;
        moves++;
        if(!wreckSearched && Math.abs(playerX)<3.2f && Math.abs(playerZ+8.5f)<4.0f) {
            wreckSearched=true; scrap+=2; wood++;
            notice="Обломки обысканы: найден металл и дерево.";
        } else if(!axeTaken && Math.abs(playerX)<4.0f && Math.abs(playerZ+7.0f)<5.5f) {
            axeTaken=true; notice="Ты забрал аварийный топор из салона. Теперь можно рубить деревья.";
        } else if(nearTreeIndex>=0) {
            if(!axeTaken){notice="Нужен топор. Найди его в салоне самолёта.";}
            else {int i=nearTreeIndex;chopAnim=1f;
                if(treeHits[i]<3){treeHits[i]++;wood++;notice=treeHits[i]>=3?"Дерево повалено! Собери брёвна рядом.":"Удар топором: "+treeHits[i]+"/3.";}
                else {wood+=2;notice="Собраны брёвна. Дерева: "+wood;treeHits[i]=4;}
            }
        } else { wood++; notice="Собрано дерево: "+wood+"  •  металл: "+scrap; }
        hud.invalidate();
    }

    void packetTick() {
        if(!solo && lan.connected) {
            lan.setOutgoing(String.format(Locale.US,"%.3f,%.3f,%.3f,%d,%d,%d,%d,%d",playerX,playerZ,yaw,stage,started?(screen==3?2:1):0,ready?1:0,choiceIndex,resultCode));
            String[] p=lan.remote.split(",");
            if(p.length>=8) try {
                peerX=Float.parseFloat(p[0]); peerZ=Float.parseFloat(p[1]); peerYaw=Float.parseFloat(p[2]);
                int remoteStage=Integer.parseInt(p[3]); int remoteState=Integer.parseInt(p[4]); boolean remoteStarted=remoteState>0;
                peerReady=Integer.parseInt(p[5])==1; peerChoice=Integer.parseInt(p[6]); int remoteResult=Integer.parseInt(p[7]);
                if(!lan.hosting) {
                    boolean stageChanged=remoteStage!=stage;
                    stage=remoteStage; started=remoteStarted; if(started) { int targetScreen=remoteState==2?3:2; if(screen!=targetScreen){screen=targetScreen;sceneTime=0;} }
                    if(stageChanged) {
                        confirmed=false; ready=false; choiceIndex=0;
                        notice=stage>=4?"Комплекс пройден!":"Новая комната. Сопоставьте подсказки.";
                    }
                    if(remoteResult!=lastPeerResult) {
                        lastPeerResult=remoteResult;
                        if(!stageChanged) {
                            confirmed=false; ready=false;
                            notice="Код не принят. Обсудите подсказки и попробуйте снова.";
                        }
                    }
                }
            } catch(Exception ignored) {}
        }
    }

    static class Hud extends View {
        final ThreeDGameView g; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx,sy,lastX,lastY; boolean stick=false,look=false; int stickPointer=-1,lookPointer=-1;
        Hud(Context c,ThreeDGameView game){super(c);g=game;setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
        void fill(Canvas c,float l,float t,float r,float b,float rad,int col){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(col);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        void stroke(Canvas c,float l,float t,float r,float b,float rad,int col,float sw){p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);p.setColor(col);c.drawRoundRect(l,t,r,b,rad,rad,p);p.setStyle(Paint.Style.FILL);}
        void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(s,x,y,p);}
        void center(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));text(c,s,x-p.measureText(s)/2,y,size,color,bold);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float w=getWidth(),h=getHeight();
            if(g.screen==0){
                p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(5,11,25),Color.rgb(11,28,47),Color.rgb(26,16,42)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                p.setColor(Color.argb(24,160,176,141));c.drawCircle(w*.78f,h*.42f,h*.48f,p);
                drawForestMenu(c,w,h);
                text(c,"AFTERMATH",w*.065f,h*.24f,Math.min(66,w*.082f),Color.rgb(231,238,224),true);
                fill(c,w*.09f,h*.275f,w*.24f,h*.285f,5,Color.rgb(70,255,214));
                text(c,"THE ISLAND DOES NOT FORGIVE.",w*.065f,h*.35f,Math.min(18,w*.023f),Color.rgb(195,151,104),true);
                text(c,"ВЫЖИВАНИЕ • ГЛУХОЙ ЛЕС • ОБЛОМКИ",w*.065f,h*.43f,Math.min(18,w*.023f),Color.rgb(211,218,203),false);
                float bw=w*.30f,bx=w*.09f,top=h*.52f,bh=h*.105f,gap=h*.025f;
                fill(c,bx,top,bx+bw,top+bh,18,Color.rgb(70,255,214));center(c,"СОВМЕСТНОЕ ВЫЖИВАНИЕ",bx+bw/2,top+bh*.64f,Math.min(18,w*.023f),Color.rgb(4,20,31),true);
                fill(c,bx,top+bh+gap,bx+bw,top+2*bh+gap,18,Color.argb(220,28,46,69));stroke(c,bx,top+bh+gap,bx+bw,top+2*bh+gap,18,Color.argb(100,120,200,230),1);
                center(c,"ПОДКЛЮЧИТЬСЯ К ДРУГУ",bx+bw/2,top+bh+gap+bh*.64f,Math.min(18,w*.022f),Color.WHITE,true);
                fill(c,bx,top+2*(bh+gap),bx+bw,top+3*bh+2*gap,18,Color.argb(130,28,46,69));
                center(c,"НОВАЯ ИГРА",bx+bw/2,top+2*(bh+gap)+bh*.64f,Math.min(19,w*.023f),Color.rgb(225,232,216),true);
                // Clean two-character key art, only on menu.
                drawMenuHero(c,w*.72f,h*.54f,Color.rgb(255,73,107),1.0f);
                drawMenuHero(c,w*.84f,h*.49f,Color.rgb(65,145,255),0.88f);
                text(c,"ПОСЛЕ КАТАСТРОФЫ НАЧИНАЕТСЯ БОРЬБА",w*.49f,h*.84f,Math.min(14,w*.018f),Color.rgb(185,174,151),true);
            } else if(g.screen==1){
                p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(5,12,25),Color.rgb(10,27,44)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                text(c,"ЭКСПЕДИЦИЯ НА ОСТРОВ",w*.09f,h*.22f,Math.min(36,w*.047f),Color.WHITE,true);
                text(c,g.lan.hosting?"Передай другу IP-адрес ниже. Оба телефона должны быть в одной Wi-Fi сети.":"Подключаемся к комнате друга в этой же Wi-Fi сети.",w*.09f,h*.32f,Math.min(18,w*.022f),Color.rgb(194,213,237),false);
                if(g.lan.hosting){text(c,"IP ХОСТА: "+LanSession.localIp(),w*.09f,h*.44f,Math.min(26,w*.033f),Color.rgb(190,174,135),true);text(c,"ПОРТ: "+LanSession.PORT,w*.09f,h*.50f,18,Color.WHITE,true);}
                text(c,g.lan.status,w*.09f,h*.60f,Math.min(19,w*.024f),g.lan.connected?Color.rgb(70,255,214):Color.rgb(255,195,104),true);
                if(g.lan.hosting && g.lan.connected){
                    fill(c,w*.62f,h*.68f,w*.88f,h*.81f,18,Color.rgb(70,255,214));center(c,"НАЧАТЬ ВЫЖИВАНИЕ",w*.75f,h*.755f,18,Color.rgb(4,20,31),true);
                } else if(!g.lan.hosting && g.lan.connected){
                    center(c,"Ждём, пока создатель комнаты начнёт игру…",w*.5f,h*.77f,18,Color.WHITE,false);
                }
                fill(c,w*.09f,h*.77f,w*.27f,h*.86f,14,Color.argb(180,33,49,70));center(c,"НАЗАД",w*.18f,h*.825f,18,Color.WHITE,true);
            } else if(g.screen==3) {
                p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.argb(25,0,0,0),Color.argb(190,0,0,0),Color.argb(235,0,0,0)},null,Shader.TileMode.CLAMP));
                c.drawRect(0,0,w,h,p);p.setShader(null);
                fill(c,w*.06f,h*.68f,w*.72f,h*.93f,18,Color.argb(175,7,12,10));
                text(c,"FLIGHT 417  /  EMERGENCY",w*.09f,h*.74f,Math.min(15,w*.02f),Color.rgb(215,174,119),true);
                String line=g.sceneTime<2.0f?"FLIGHT 417 • АВТОПИЛОТ ОТКЛЮЧЁН":g.sceneTime<4.4f?"ТУРБУЛЕНТНОСТЬ! ДЕРЖИСЬ!":g.sceneTime<6.4f?"КРЫЛО ПОВРЕЖДЕНО • УДАР НЕИЗБЕЖЕН":g.sceneTime<8.0f?"…":g.sceneTime<10.0f?"СИРЕНА ЗАТИХАЕТ В ТУМАНЕ":"ТЫ ОДИН. НУЖНО НАЙТИ УКРЫТИЕ.";
                text(c,line,w*.09f,h*.81f,Math.min(20,w*.027f),Color.rgb(237,237,224),true);
                text(c,"ЗАЖМИ ЭКРАН НА 3 СЕКУНДЫ, ЧТОБЫ ПРОПУСТИТЬ",w*.09f,h*.88f,Math.min(13,w*.017f),Color.rgb(220,220,204),true);
                float progress=g.skipHold?Math.min(1f,(SystemClock.uptimeMillis()-g.skipHoldStart)/3000f):0f;
                fill(c,w*.09f,h*.905f,w*.63f,h*.923f,6,Color.argb(110,210,220,210));
                if(progress>0) fill(c,w*.09f,h*.905f,w*(.09f+.54f*progress),h*.923f,6,Color.rgb(223,174,105));
            } else {
                fill(c,18,16,Math.min(w*.47f,460),105,14,Color.argb(190,9,15,11));
                text(c,"AFTERMATH  /  DAY 01",34,42,17,Color.rgb(230,231,210),true);
                text(c,g.axeTaken?"ЦЕЛЬ: СОБЕРИ РЕСУРСЫ И ПОСТРОЙСЯ":"ЦЕЛЬ: НАЙДИ ТОПОР В САМОЛЁТЕ",34,68,Math.min(16,w*.021f),Color.rgb(210,174,119),true);
                text(c,"ДЕРЕВО: "+g.wood+"   МЕТАЛЛ: "+g.scrap+"   "+(g.dayClock%120<60?"ДЕНЬ":"НОЧЬ"),34,94,Math.min(14,w*.018f),Color.rgb(183,195,173),false);
                fill(c,w-260,18,w-18,82,18,Color.argb(215,104,64,36));
                center(c,"КНИГА ПОСТРОЕК",w-139,43,Math.min(18,w*.023f),Color.rgb(244,231,205),true);
                center(c,"ЧЕРТЕЖИ И РЕЦЕПТЫ",w-139,65,Math.min(13,w*.017f),Color.rgb(226,194,150),false);
                fill(c,w-260,92,w-18,156,30,Color.argb(220,35,58,44));stroke(c,w-260,92,w-18,156,30,Color.rgb(104,157,111),2);
                center(c,"▤",w-139,129,29,Color.rgb(220,239,206),true);
                fill(c,w-330,168,w-18,218,22,Color.argb(210,25,43,32));center(c,"ИНВЕНТАРЬ  ·  РЕСУРСЫ",w-174,199,Math.min(16,w*.021f),Color.rgb(232,232,211),true);
                // Left movement stick.
                float bx=Math.min(140,w*.14f),by=h-135;
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(82,185,215,245));c.drawCircle(bx,by,78,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.argb(155,185,225,255));c.drawCircle(bx,by,78,p);p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(240,214,236,255));c.drawCircle(bx+g.stickX*46,by+g.stickY*46,30,p);
                center(c,"ДВИЖЕНИЕ",bx,by+105,14,Color.argb(230,220,235,250),true);
                // Look zone hint, no extra directional buttons.
                center(c,"СВАЙП — СМОТРЕТЬ • СТИК — ИДТИ",w*.69f,h-20,Math.min(13,w*.016f),Color.argb(185,225,236,250),true);
                if(g.nearInteract){ fill(c,w-250,h-150,w-18,h-65,24,Color.rgb(178,112,61));center(c,g.nearTreeIndex>=0?(g.treeHits[g.nearTreeIndex]>=3?"СОБРАТЬ БРЁВНА":(g.axeTaken?"РУБИТЬ ДЕРЕВО":"НУЖЕН ТОПОР")):(!g.axeTaken?"ВЗЯТЬ ТОПОР":"ОБЫСКАТЬ ОБЛОМКИ"),w-134,h-100,Math.min(18,w*.023f),Color.rgb(20,18,12),true); }
                if(!g.notice.isEmpty()) text(c,g.notice,Math.max(18,w*.22f),h*.94f,Math.min(16,w*.021f),Color.rgb(232,222,199),true);
                if(g.blueprintBookOpen) drawBlueprintBook(c,w,h);
                if(g.inventoryOpen) drawInventory(c,w,h);
            }
            postInvalidateDelayed(50);
        }
        void drawBlueprintBook(Canvas c,float w,float h){
            fill(c,w*.12f,h*.10f,w*.88f,h*.90f,26,Color.argb(246,25,27,23));stroke(c,w*.12f,h*.10f,w*.88f,h*.90f,26,Color.rgb(183,147,94),3);
            fill(c,w*.49f,h*.13f,w*.505f,h*.87f,3,Color.rgb(116,88,55));
            text(c,"ПОЛЕВАЯ КНИГА • СТРОИТЕЛЬСТВО",w*.17f,h*.18f,Math.min(25,w*.033f),Color.rgb(234,218,179),true);
            String[] names={"КОСТЁР","ФУНДАМЕНТ","СТЕНА","ПОТОЛОК","ДВЕРНОЙ ПРОЁМ","УКРЫТИЕ"};
            String[] recipes={"3 дерева + 1 металл","4 дерева + 1 металл","3 дерева + 1 металл","2 дерева","6 дерева + 2 металла","8 дерева + 3 металла"};
            String[] desc={"Огонь и свет ночью","Основа для будущей базы","Защита и границы базы","Крыша над головой","Проход в стене","Небольшое лесное убежище"};
            for(int i=0;i<6;i++){float col=i%2,row=i/2;float x=w*.17f+col*w*.36f,y=h*.25f+row*h*.185f;int border=i==g.blueprintType?Color.rgb(70,255,214):Color.rgb(116,105,81);
                fill(c,x,y,x+w*.31f,y+h*.15f,14,Color.argb(230,47,48,38));stroke(c,x,y,x+w*.31f,y+h*.15f,14,border,i==g.blueprintType?3:1);
                center(c,names[i],x+w*.155f,y+h*.045f,Math.min(18,w*.023f),Color.rgb(239,227,199),true);
                // Small blueprint wireframe preview.
                p.setColor(i==g.blueprintType?Color.rgb(85,245,212):Color.rgb(165,197,168));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
                float cx=x+w*.155f,cy=y+h*.085f;
                if(i==0){c.drawCircle(cx,cy,10,p);c.drawLine(cx-13,cy+7,cx+13,cy-7,p);}
                else if(i==1||i==3){c.drawRect(cx-18,cy-7,cx+18,cy+7,p);c.drawLine(cx-18,cy-7,cx,cy-15,p);c.drawLine(cx+18,cy-7,cx,cy-15,p);}
                else {c.drawRect(cx-17,cy-17,cx+17,cy+17,p);c.drawLine(cx-17,cy-17,cx+17,cy+17,p);c.drawLine(cx+17,cy-17,cx-17,cy+17,p);}
                p.setStyle(Paint.Style.FILL);center(c,recipes[i],x+w*.155f,y+h*.122f,Math.min(13,w*.017f),Color.rgb(220,191,145),true);
            }
            text(c,"ВЫБРАНО: "+names[g.blueprintType]+" • "+recipes[g.blueprintType],w*.17f,h*.84f,Math.min(16,w*.021f),Color.rgb(70,255,214),true);
            fill(c,w*.67f,h*.82f,w*.82f,h*.875f,14,Color.rgb(70,255,214));center(c,"ВЫБРАТЬ",w*.745f,h*.855f,Math.min(16,w*.02f),Color.rgb(4,20,31),true);
            fill(c,w*.17f,h*.82f,w*.29f,h*.875f,14,Color.rgb(78,67,51));center(c,"ЗАКРЫТЬ",w*.23f,h*.855f,Math.min(15,w*.019f),Color.WHITE,true);
        }
        void drawInventory(Canvas c,float w,float h){fill(c,w*.24f,h*.19f,w*.76f,h*.81f,24,Color.argb(245,22,29,24));stroke(c,w*.24f,h*.19f,w*.76f,h*.81f,24,Color.rgb(170,145,99),3);center(c,"ИНВЕНТАРЬ",w*.5f,h*.27f,Math.min(28,w*.037f),Color.rgb(236,222,190),true);text(c,"Дерево: "+g.wood,w*.31f,h*.38f,Math.min(22,w*.029f),Color.rgb(225,221,199),true);text(c,"Металл: "+g.scrap,w*.31f,h*.46f,Math.min(22,w*.029f),Color.rgb(225,221,199),true);text(c,"Топор: "+(g.axeTaken?"есть":"не найден"),w*.31f,h*.54f,Math.min(22,w*.029f),Color.rgb(225,221,199),true);text(c,"Костёр: "+(g.campfireBuilt?"построен":"не построен"),w*.31f,h*.62f,Math.min(22,w*.029f),Color.rgb(225,221,199),true);fill(c,w*.40f,h*.70f,w*.60f,h*.77f,15,Color.rgb(70,255,214));center(c,"ЗАКРЫТЬ",w*.5f,h*.745f,17,Color.rgb(4,20,31),true);}
        void drawForestMenu(Canvas c,float w,float h){
            p.setShader(null);
            p.setColor(Color.argb(45,193,199,159));c.drawCircle(w*.82f,h*.24f,h*.105f,p);
            for(int i=0;i<18;i++){
                float x=w*(.42f+i*.035f),base=h*(.94f-(i%3)*.025f),s=.55f+(i%5)*.17f;
                p.setColor(Color.argb(135+(i%3)*20,7,14,12));
                c.drawRect(x-5*s,base-55*s,x+5*s,base,p);
                Path tr=new Path();tr.moveTo(x,base-150*s);tr.lineTo(x-36*s,base-65*s);tr.lineTo(x+36*s,base-65*s);tr.close();c.drawPath(tr,p);
                tr.reset();tr.moveTo(x,base-120*s);tr.lineTo(x-31*s,base-38*s);tr.lineTo(x+31*s,base-38*s);tr.close();c.drawPath(tr,p);
            }
            // distant crashed-aircraft silhouette in the fog
            p.setColor(Color.argb(150,18,23,20));
            c.save();c.rotate(-11,w*.78f,h*.54f);
            c.drawRoundRect(w*.67f,h*.51f,w*.91f,h*.56f,12,12,p);
            c.drawRoundRect(w*.72f,h*.47f,w*.87f,h*.60f,5,5,p);
            c.drawRoundRect(w*.60f,h*.525f,w*.97f,h*.545f,5,5,p);
            c.restore();
            p.setColor(Color.argb(45,207,172,111));c.drawCircle(w*.74f,h*.62f,Math.min(w,h)*.075f,p);
        }
        void drawMenuHero(Canvas c,float x,float y,int col,float scale){
            float s=scale;fill(c,x-28*s,y-20*s,x+28*s,y+46*s,12,Color.argb(210,25,39,62));
            fill(c,x-21*s,y-70*s,x+21*s,y-28*s,12,Color.rgb(225,236,248));
            fill(c,x-29*s,y-23*s,x+29*s,y+24*s,10,col);
            fill(c,x-20*s,y+23*s,x-5*s,y+63*s,5,Color.rgb(75,92,119));fill(c,x+5*s,y+23*s,x+20*s,y+63*s,5,Color.rgb(75,92,119));
            fill(c,x-10*s,y-53*s,x+10*s,y-45*s,3,Color.rgb(60,255,220));
        }
        void askIp(){
            EditText input=new EditText(getContext());input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT);
            input.setHint("Например: 192.168.1.25");input.setTextColor(Color.WHITE);input.setHintTextColor(Color.GRAY);
            AlertDialog d=new AlertDialog.Builder(getContext()).setTitle("Подключиться к другу").setMessage("Введи IP телефона, который создал комнату. Оба телефона должны быть в одной Wi-Fi сети.").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Подключить",(dialog,which)->g.joinRoom(input.getText().toString())).create();
            d.setOnShowListener(v->{d.getWindow().setBackgroundDrawableResource(android.R.color.background_dark);d.getButton(-1).setTextColor(Color.rgb(70,210,190));d.getButton(-2).setTextColor(Color.LTGRAY);});d.show();
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY(),w=getWidth(),h=getHeight();
            if(g.screen==0){
                if(e.getAction()==MotionEvent.ACTION_UP){
                    float bx=w*.09f,bw=w*.30f,top=h*.52f,bh=h*.105f,gap=h*.025f;
                    if(x>=bx&&x<=bx+bw&&y>=top&&y<=top+bh)g.hostRoom();
                    else if(x>=bx&&x<=bx+bw&&y>=top+bh+gap&&y<=top+2*bh+gap)askIp();
                    else if(x>=bx&&x<=bx+bw&&y>=top+2*(bh+gap)&&y<=top+3*bh+2*gap)g.beginSolo();
                }return true;
            }
            if(g.screen==1){
                if(e.getAction()==MotionEvent.ACTION_UP){
                    if(x<w*.30f&&y>h*.75f)g.backToMenu();
                    else if(g.lan.hosting&&g.lan.connected&&x>w*.60f&&y>h*.65f)g.startOnlineGame();
                }return true;
            }
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: {
                    if(g.screen==3){g.skipHold=true;g.skipHoldStart=SystemClock.uptimeMillis();return true;}
                    int id=e.getPointerId(0); lastX=x;lastY=y;
                    if(g.blueprintBookOpen){if(x>w*.64f&&y>h*.80f){g.blueprintBookOpen=false;g.blueprintMode=true;g.notice="Призрак чертежа показан впереди. Коснись мира для постройки.";g.hud.invalidate();}else if(x<w*.31f&&y>h*.80f){g.blueprintBookOpen=false;g.hud.invalidate();}else if(y>h*.23f&&y<h*.80f&&x>w*.14f&&x<w*.86f){int col=x>w*.5f?1:0;int row=Math.max(0,Math.min(2,(int)((y-h*.25f)/(h*.185f))));g.blueprintType=Math.min(5,row*2+col);g.hud.invalidate();}return true;}
                    if(g.inventoryOpen){if(y>h*.68f){g.inventoryOpen=false;g.hud.invalidate();}return true;}
                    if(y<90&&x>w-285){g.craftCampfire();return true;}
                    if(y>=90&&y<160&&x>w-285){g.blueprintBookOpen=true;g.blueprintMode=false;g.hud.invalidate();return true;}
                    if(y>=165&&y<225&&x>w-340){g.inventoryOpen=true;g.hud.invalidate();return true;}
                    if(g.nearInteract&&y>h-155&&x>w-250){g.interact();return true;}
                    if(g.blueprintMode && y>150 && !(x<w*.27f&&y>h*.55f)){g.placeBlueprint();return true;}
                    if(x<w*.27f&&y>h*.55f){stick=true;stickPointer=id;updateStick(x,y,w,h);}
                    else {look=true;lookPointer=id;}
                    return true;
                }
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int i=e.getActionIndex(),id=e.getPointerId(i);float tx=e.getX(i),ty=e.getY(i);
                    if(!stick && tx<w*.27f && ty>h*.55f){stick=true;stickPointer=id;updateStick(tx,ty,w,h);}
                    else if(!look && tx>w*.30f){look=true;lookPointer=id;lastX=tx;lastY=ty;}
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    for(int i=0;i<e.getPointerCount();i++){
                        int id=e.getPointerId(i);float tx=e.getX(i),ty=e.getY(i);
                        if(stick&&id==stickPointer)updateStick(tx,ty,w,h);
                        if(look&&id==lookPointer){float dx=tx-lastX,dy=ty-lastY;g.yaw+=dx*.006f;g.pitch=Math.max(-0.82f,Math.min(0.82f,g.pitch-dy*.0065f));lastX=tx;lastY=ty;}
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    int id=e.getPointerId(e.getActionIndex());
                    if(id==stickPointer){stick=false;stickPointer=-1;g.stickX=g.stickY=0;}
                    if(id==lookPointer){look=false;lookPointer=-1;}
                    return true;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if(g.screen==3){if(e.getActionMasked()==MotionEvent.ACTION_UP && g.skipHold && SystemClock.uptimeMillis()-g.skipHoldStart>=3000L)g.skipCutscene();g.skipHold=false;g.skipHoldStart=0L;g.hud.invalidate();return true;}
                    stick=false;look=false;stickPointer=lookPointer=-1;g.stickX=g.stickY=0;return true;
            }
            return true;
        }
        void updateStick(float x,float y,float w,float h){
            float bx=Math.min(140,w*.14f),by=h-135,dx=x-bx,dy=y-by,len=(float)Math.sqrt(dx*dx+dy*dy),max=74;
            if(len>max){dx=dx/len*max;dy=dy/len*max;}g.stickX=dx/max;g.stickY=-dy/max;
        }
    }

    static class World implements GLSurfaceView.Renderer {
        final ThreeDGameView g;
        final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        int program,posLoc,colorLoc,mvpLoc,lightLoc,modelLoc,eyeLoc;
        FloatBuffer vertices;
        final float[] cube={
        -0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,
        0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,
        0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,
        -0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f};
        float px=0,pz=0,elapsed=0;
        final String vs="attribute vec3 aPos; uniform mat4 uMVP; uniform mat4 uModel; varying vec3 vPos; varying vec3 vWorld; void main(){vPos=aPos;vec4 wp=uModel*vec4(aPos,1.0);vWorld=wp.xyz;gl_Position=uMVP*vec4(aPos,1.0);}";
        final String fs="precision mediump float;uniform vec4 uColor;uniform vec3 uLight;uniform vec3 uEye;varying vec3 vPos;varying vec3 vWorld;void main(){float l=0.48+0.52*max(0.0,dot(normalize(vPos+vec3(0.51)),normalize(uLight)));float shade=0.92+0.08*sin(vWorld.x*1.7+vWorld.z*0.43);vec3 base=uColor.rgb*l*shade;float fog=smoothstep(18.0,58.0,distance(vWorld,uEye));vec3 fogColor=vec3(0.12,0.16,0.14);gl_FragColor=vec4(mix(base,fogColor,fog),uColor.a);}";
        World(ThreeDGameView game){g=game;}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.018f,.035f,.07f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
            ByteBuffer b=ByteBuffer.allocateDirect(cube.length*4).order(ByteOrder.nativeOrder());vertices=b.asFloatBuffer();vertices.put(cube).position(0);
            posLoc=GLES20.glGetAttribLocation(program,"aPos");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");colorLoc=GLES20.glGetUniformLocation(program,"uColor");lightLoc=GLES20.glGetUniformLocation(program,"uLight");modelLoc=GLES20.glGetUniformLocation(program,"uModel");eyeLoc=GLES20.glGetUniformLocation(program,"uEye");
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(proj,0,72,(float)w/Math.max(1,h),.1f,100);}
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            elapsed+=.025f;g.dayClock+=.06f;g.chopAnim=Math.max(0,g.chopAnim-.035f);
            float night=(float)(.5-.5*Math.cos(g.dayClock*.035f));float daylight=1f-night;
            GLES20.glClearColor(.025f+.10f*daylight,.035f+.15f*daylight,.055f+.13f*daylight,1);
            if(g.screen==3){
                g.sceneTime+=.025f;
                if(g.sceneTime>=12.0f){g.screen=2;g.playerX=0;g.playerZ=0;g.yaw=0;g.pitch=0;px=0;pz=0;g.notice="Ты очнулся у места крушения. Осмотрись и выживи.";g.hud.invalidate();}
            }
            if(g.screen==3 && g.started) g.packetTick();
            if(g.screen==2 && g.started){
                float speed=.045f,sy=g.stickY,sx=g.stickX;
                float rightX=(float)Math.cos(g.yaw),rightZ=(float)Math.sin(g.yaw);
                float forwardX=(float)Math.sin(g.yaw),forwardZ=-(float)Math.cos(g.yaw);
                float nx=px+(rightX*sx+forwardX*sy)*speed,nz=pz+(rightZ*sx+forwardZ*sy)*speed;
                if(!blocked(nx,nz)){px=nx;pz=nz;} else {if(!blocked(nx,pz))px=nx;if(!blocked(px,nz))pz=nz;}
                px=Math.max(-22f,Math.min(22f,px));pz=Math.max(-78f,Math.min(8f,pz));
                g.playerX=px;g.playerZ=pz;g.packetTick();
                if(!g.solo&&!g.lan.hosting&&g.lan.connected) { /* state is shared through LAN packets */ }
                g.nearTreeIndex=findNearTree(px,pz);
                g.nearInteract=(!g.wreckSearched&&Math.abs(px)<3.2f&&Math.abs(pz+8.5f)<4.0f)||(!g.axeTaken&&Math.abs(px)<4.5f&&Math.abs(pz+7.0f)<6.0f)||g.nearTreeIndex>=0;
            } else if(g.screen!=3){px=g.playerX;pz=g.playerZ;}
            float eyeY=1.66f,camX=px,camZ=pz,camYaw=g.yaw,camPitch=g.pitch;
            if(g.screen==3){
                float t=g.sceneTime;
                if(t<2.4f){camX=(float)Math.sin(t*2.2f)*.025f;camZ=3.7f-t*.28f;eyeY=1.64f;camYaw=(float)Math.sin(t*1.7f)*.035f;camPitch=(float)Math.sin(t*2.4f)*.025f;}
                else if(t<5.3f){float crash=t-2.4f;float shake=Math.min(1f,crash*.7f);camX=(float)Math.sin(t*19f)*(.08f+shake*.18f);camZ=3.0f-crash*1.9f;eyeY=1.62f-(float)Math.pow(crash/2.9f,2)*.55f;camYaw=(float)Math.sin(t*13f)*(.08f+shake*.13f);camPitch=(float)Math.sin(t*17f)*(.12f+shake*.18f)-.18f;}
                else if(t<8.0f){float hit=t-5.3f;camX=(float)Math.sin(t*21f)*.16f*(1-hit/2.7f);camZ=-2.5f+hit*.38f;eyeY=.72f+(float)Math.min(1,hit*.55f)*.22f;camYaw=(float)Math.sin(t*12f)*.12f;camPitch=-.5f+(float)Math.sin(t*3f)*.08f;}
                else {float settle=Math.min(1f,(t-8f)/3.2f);camX=(float)Math.sin(t*8f)*.025f*(1-settle);camZ=-1.5f-settle*.7f;eyeY=.94f+settle*.68f;camPitch=-.48f*(1-settle);camYaw=(float)Math.sin(t*1.3f)*.035f;}
            }
            float cy=(float)Math.cos(camYaw),sy=(float)Math.sin(camYaw),cp=(float)Math.cos(camPitch),sp=(float)Math.sin(camPitch);
            float tx=camX+sy*cp,tz=camZ-cy*cp,ty=eyeY+sp;
            Matrix.setLookAtM(view,0,camX,eyeY,camZ,tx,ty,tz,0,1,0);
            GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(posLoc);vertices.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glUniform3f(lightLoc,-.35f,.25f+daylight,.22f);GLES20.glUniform3f(eyeLoc,camX,eyeY,camZ);
            // Damp forest floor, moss patches, stones and dense tree lines.
            box(0,-.34f,-37,58,.6f,94,new float[]{.16f,.19f,.13f,1});
            for(int i=0;i<36;i++){
                float z=5f-(i/5)*6.1f;
                float x=-25f+(i%5)*12.3f+((i%2)*1.8f);
                if(Math.abs(x)<4.5f && z>-18) x+=x<0?-5.5f:5.5f;
                tree(x,z,3.6f+(i%4)*.48f,i);
            }
            for(int i=0;i<18;i++){
                float z=3f-i*4.4f;
                float x=(i%2==0?-1:1)*(5.2f+(i%4)*1.3f);
                tree(x,z,3.0f+(i%3)*.55f,i+90);
            }
            for(int i=0;i<20;i++){
                float x=(float)Math.sin(i*12.73f)*20f;
                float z=2f-(i*2.31f)%76f;
                float scale=.35f+(i%5)*.13f;
                box(x,.1f,z,scale*1.7f,scale,scale*1.4f,new float[]{.22f,.24f,.19f,1});
                if(i%3==0) bush(x+1f,z-.4f,scale*1.5f);
            }
            // Wrecked passenger aircraft: broken fuselage, torn wing, engine and scattered seats.
            // Passenger jet silhouette with rounded-looking segmented fuselage, nose, cockpit, wing panels and painted livery.
            box(0,1.65f,-10.5f,3.15f,2.2f,11.5f,new float[]{.61f,.63f,.58f,1});
            box(0,1.63f,-17.1f,2.65f,1.8f,3.8f,new float[]{.67f,.68f,.62f,1});
            box(0,1.54f,-19.0f,1.8f,1.25f,2.2f,new float[]{.7f,.71f,.65f,1});
            box(0,2.02f,-19.45f,1.45f,.5f,.8f,new float[]{.12f,.22f,.29f,1});
            box(0,.78f,-10.8f,1.7f,.25f,11.3f,new float[]{.18f,.26f,.29f,1});
            box(-4.3f,1.22f,-10.4f,8.2f,.26f,2.45f,new float[]{.68f,.69f,.63f,1});
            box(-6.8f,1.28f,-11.7f,2.6f,.24f,1.55f,new float[]{.52f,.54f,.5f,1});
            box(2.15f,1.0f,-8.4f,.52f,1.65f,4.3f,new float[]{.48f,.5f,.46f,1});
            box(0,2.2f,-5.0f,1.8f,.22f,1.8f,new float[]{.57f,.59f,.55f,1});
            box(-.05f,.85f,-10.3f,3.2f,.13f,10.8f,new float[]{.12f,.34f,.42f,1});
            for(int wi=0;wi<7;wi++){float wz=-6.3f-wi*1.45f;box(-1.59f,1.92f,wz,.055f,.34f,.65f,new float[]{.10f,.27f,.36f,1});box(1.59f,1.92f,wz,.055f,.34f,.65f,new float[]{.10f,.27f,.36f,1});}
            box(-3.4f,1.35f,-10.0f,1.2f,.72f,1.0f,new float[]{.22f,.24f,.23f,1});box(-3.4f,.97f,-10.0f,1.3f,.12f,1.1f,new float[]{.13f,.15f,.15f,1});
            box(0,1.0f,-15.0f,2.4f,.22f,2.0f,new float[]{.58f,.6f,.55f,1});
            box(-1.1f,.75f,-5.9f,.25f,1.1f,1.8f,new float[]{.42f,.41f,.34f,1});
            box(1.0f,.55f,-6.8f,1.3f,.25f,.9f,new float[]{.19f,.2f,.18f,1});
            box(-3.2f,.25f,-7.2f,1.1f,.2f,.8f,new float[]{.31f,.3f,.25f,1});
            box(4.2f,.15f,-15.5f,2.0f,.2f,1.3f,new float[]{.3f,.31f,.28f,1});
            // Broken aircraft interior visible during the crash sequence.
            if(g.screen==3 && g.sceneTime<6.4f){
                box(-2.05f,1.5f,-1.0f,.16f,2.8f,12f,new float[]{.22f,.24f,.23f,1});
                box(2.05f,1.5f,-1.0f,.16f,2.8f,12f,new float[]{.22f,.24f,.23f,1});
                box(0,2.9f,-1.0f,4.2f,.18f,12f,new float[]{.25f,.27f,.25f,1});
                box(0,.16f,-1.0f,4.2f,.2f,12f,new float[]{.25f,.26f,.23f,1});
                for(int i=0;i<4;i++){
                    float z=1.8f-i*2.4f;
                    box(-1.05f,.75f,z,.55f,1.1f,.75f,new float[]{.2f,.22f,.22f,1});
                    box(1.05f,.75f,z,.55f,1.1f,.75f,new float[]{.2f,.22f,.22f,1});
                    box(-1.05f,1.3f,z,.55f,.18f,.6f,new float[]{.38f,.3f,.22f,1});
                    box(1.05f,1.3f,z,.55f,.18f,.6f,new float[]{.38f,.3f,.22f,1});
                }
                box(-.7f,2.15f,-5.5f,1.0f,.12f,.25f,new float[]{.8f,.28f,.09f,1});
                box(.55f,2.12f,-5.4f,.8f,.1f,.2f,new float[]{.78f,.12f,.07f,1});
            }
            // Subtle sunbeams, mossy logs, and a distant ridgeline.
            for(int i=0;i<9;i++){
                float z=-24f-i*6f;
                box((i%2==0?-1:1)*23f,7f,z,10f,.12f,.18f,new float[]{.28f,.32f,.24f,1});
            }
            for(int i=0;i<8;i++){
                float x=(i-4)*4.7f;
                box(x,.38f,-22f-i*5f,2.4f,.38f,.42f,new float[]{.24f,.25f,.2f,1});
            }
            if(g.screen==2 && g.started && !g.solo && g.lan.connected) remoteHero(g.peerX,g.peerZ,g.lan.hosting?new float[]{.38f,.4f,.32f,1}:new float[]{.38f,.4f,.32f,1});
            if(g.screen==2 && g.started && g.solo) remoteHero(0,-8,new float[]{.38f,.4f,.32f,1});
            if(g.screen==2 && g.started && g.axeTaken){float ax=camX+(float)Math.sin(camYaw)*.38f,az=camZ-(float)Math.cos(camYaw)*.38f;float swing=g.chopAnim;box(ax,eyeY-.3f,az,.07f,.7f,.07f,new float[]{.36f,.22f,.12f,1});box(ax+.15f+swing*.22f,eyeY-.05f,az-.12f,.42f,.12f,.16f,new float[]{.55f,.58f,.55f,1});}
            if(g.blueprintMode){float bx=g.playerX+(float)Math.sin(g.yaw)*3.0f,bz=g.playerZ-(float)Math.cos(g.yaw)*3.0f;float[] guide=new float[]{.12f,.95f,.82f,.8f};
                if(g.blueprintType==0){box(bx,.16f,bz,1.0f,.06f,.18f,guide);box(bx,.16f,bz,.18f,.06f,1.0f,guide);box(bx,.45f,bz,.24f,.52f,.24f,guide);}
                else if(g.blueprintType==1){box(bx,.08f,bz,3.0f,.08f,2.4f,guide);}
                else if(g.blueprintType==2){box(bx,1.0f,bz,3.0f,2.0f,.16f,guide);}
                else if(g.blueprintType==3){box(bx,2.0f,bz,3.0f,.12f,2.4f,guide);}
                else if(g.blueprintType==4){box(bx-1.0f,1.0f,bz,1.0f,2.0f,.16f,guide);box(bx+1.0f,1.0f,bz,1.0f,2.0f,.16f,guide);box(bx,1.95f,bz,1.0f,.16f,.16f,guide);}
                else {box(bx,.07f,bz,2.8f,.05f,2.2f,guide);box(bx,1.0f,bz-1.0f,2.8f,.07f,.07f,guide);box(bx-1.35f,1.0f,bz,.07f,1.9f,.07f,guide);box(bx+1.35f,1.0f,bz,.07f,1.9f,.07f,guide);box(bx,2.0f,bz,3.0f,.07f,2.2f,guide);}
            }
            for(int bt=1;bt<6;bt++)if(g.builtTypes[bt]){float bx=g.buildX[bt],bz=g.buildZ[bt];float[] wc=new float[]{.31f,.22f,.13f,1};
                if(bt==1)box(bx,.08f,bz,3.0f,.14f,2.4f,wc);
                else if(bt==2){box(bx,1.0f,bz,3.0f,2.0f,.18f,wc);for(int k=0;k<5;k++)box(bx-1.2f+k*.6f,1.0f,bz-.11f,.035f,1.8f,.02f,new float[]{.16f,.11f,.06f,1});}
                else if(bt==3)box(bx,2.0f,bz,3.0f,.18f,2.4f,wc);
                else if(bt==4){box(bx-1.0f,1.0f,bz,1.0f,2.0f,.18f,wc);box(bx+1.0f,1.0f,bz,1.0f,2.0f,.18f,wc);box(bx,1.95f,bz,1.0f,.18f,.18f,wc);}
                else {box(bx,1.0f,bz-1.0f,2.8f,1.9f,.16f,wc);box(bx-1.35f,1.0f,bz,.16f,1.9f,2.0f,wc);box(bx+1.35f,1.0f,bz,.16f,1.9f,2.0f,wc);box(bx,2.0f,bz,3.0f,.16f,2.2f,new float[]{.23f,.18f,.12f,1});}
            }
            if(g.shelterBuilt){float sx=g.shelterX,sz=g.shelterZ;float[] wc=new float[]{.31f,.22f,.13f,1};box(sx,1.0f,sz-1.0f,2.8f,1.9f,.16f,wc);box(sx-1.35f,1.0f,sz,.16f,1.9f,2.0f,wc);box(sx+1.35f,1.0f,sz,.16f,1.9f,2.0f,wc);box(sx,2.0f,sz,3.0f,.16f,2.2f,new float[]{.23f,.18f,.12f,1});}
            if(g.campfireBuilt){
                box(g.campfireX,.12f,g.campfireZ,1.15f,.16f,.22f,new float[]{.24f,.16f,.09f,1});
                box(g.campfireX,.12f,g.campfireZ,.22f,.16f,1.15f,new float[]{.24f,.16f,.09f,1});
                float flame=.42f+(float)Math.sin(elapsed*8f)*.09f;
                box(g.campfireX,flame,g.campfireZ,.32f,.7f,.32f,new float[]{1f,.27f,.035f,1});
                box(g.campfireX+.12f,flame+.12f,g.campfireZ-.08f,.18f,.46f,.18f,new float[]{1f,.68f,.12f,1});
                box(g.campfireX-.13f,flame-.05f,g.campfireZ+.08f,.15f,.36f,.15f,new float[]{.95f,.13f,.025f,1});
                if(g.dayClock%120>60){box(g.campfireX,flame+.75f,g.campfireZ,.5f,.6f,.5f,new float[]{.15f,.16f,.14f,.55f});}
            }
            GLES20.glDisableVertexAttribArray(posLoc);
        }
        boolean blocked(float x,float z){
            if(x<-21.5f||x>21.5f||z<-76f||z>6.5f)return true;
            // Solid aircraft hull and wings.
            if(Math.abs(x)<1.85f && z<-8.2f && z>-18.8f)return true;
            if(x>-8.6f&&x<-.2f&&z>-11.9f&&z<-8.8f)return true;
            if(g.shelterBuilt&&Math.abs(x-g.shelterX)<1.65f&&Math.abs(z-g.shelterZ)<1.25f)return true;
            for(int i=0;i<36;i++){float tz=5f-(i/5)*6.1f;float tx=-25f+(i%5)*12.3f+((i%2)*1.8f);if(Math.abs(tx)<4.5f&&tz>-18)tx+=tx<0?-5.5f:5.5f;if(g.treeHits[i]<3&&Math.abs(x-tx)<.62f&&Math.abs(z-tz)<.62f)return true;}
            for(int i=0;i<18;i++){float tz=3f-i*4.4f;float tx=(i%2==0?-1:1)*(5.2f+(i%4)*1.3f);if(g.treeHits[36+i]<3&&Math.abs(x-tx)<.6f&&Math.abs(z-tz)<.6f)return true;}
            return false;
        }
        void tree(float x,float z,float h,int seed){
            int idx=seed<36?seed:seed-90+36;if(idx<0||idx>=g.treeHits.length)idx=Math.abs(seed)%g.treeHits.length;int hits=g.treeHits[idx];float trunk=.28f+(seed%3)*.07f;
            if(hits>=3){box(x,.17f,z,trunk*1.8f,.32f,trunk*1.8f,new float[]{.31f,.23f,.14f,1});box(x+.8f,.22f,z-.25f,2.0f,.24f,.34f,new float[]{.29f,.20f,.12f,1});box(x+1.35f,.2f,z-.3f,.45f,.22f,.28f,new float[]{.34f,.25f,.15f,1});return;}
            float trunkH=h*.9f*(hits==2?.8f:1f);box(x,trunkH*.47f,z,trunk,trunkH,trunk,new float[]{.25f,.18f,.11f,1});
            for(int bark=0;bark<4;bark++){float bx=x+(bark-1.5f)*trunk*.36f;box(bx,trunkH*.48f,z-trunk*.53f,.035f,trunkH*.78f,.018f,new float[]{.13f,.095f,.055f,1});}
            float green=.19f+(seed%4)*.025f-(hits*.025f);
            box(x,h*.90f,z,1.65f,1.42f,1.62f,new float[]{green,.29f,.16f,1});box(x-.48f,h*1.09f,z+.2f,1.15f,1.0f,1.2f,new float[]{green*.9f,.25f,.14f,1});box(x+.48f,h*1.08f,z-.18f,1.12f,1.0f,1.17f,new float[]{green*1.05f,.31f,.17f,1});box(x,h*1.28f,z+.04f,.95f,.72f,.98f,new float[]{green*.78f,.24f,.13f,1});box(x-.22f,h*.75f,z+.34f,.88f,.58f,.92f,new float[]{green*.88f,.27f,.14f,1});box(x,h*1.0f,z,1.25f,1.05f,1.3f,new float[]{green*.94f,.28f,.15f,1});box(x+.08f,h*1.19f,z+.12f,.82f,.7f,.86f,new float[]{green*1.08f,.32f,.17f,1});
        }
        int findNearTree(float x,float z){
            for(int i=0;i<36;i++){
                float tz=5f-(i/5)*6.1f;
                float tx=-25f+(i%5)*12.3f+((i%2)*1.8f);
                if(Math.abs(tx)<4.5f && tz>-18) tx+=tx<0?-5.5f:5.5f;
                if(Math.abs(x-tx)<1.7f && Math.abs(z-tz)<1.7f && g.treeHits[i]<4) return i;
            }
            for(int i=0;i<18;i++){
                float tz=3f-i*4.4f;
                float tx=(i%2==0?-1:1)*(5.2f+(i%4)*1.3f);
                if(Math.abs(x-tx)<1.5f && Math.abs(z-tz)<1.5f && g.treeHits[36+i]<4) return 36+i;
            }
            return -1;
        }
        void bush(float x,float z,float s){
            box(x,.24f,z,s,.42f,s*.8f,new float[]{.13f,.22f,.12f,1});
            box(x+.22f,.38f,z-.12f,s*.7f,.4f,s*.7f,new float[]{.18f,.27f,.13f,1});
            box(x-.24f,.31f,z+.16f,s*.65f,.34f,s*.65f,new float[]{.15f,.24f,.12f,1});
        }
        void remoteHero(float x,float z,float[] col){
            box(x,1.0f,z,.56f,.82f,.42f,col);box(x,1.62f,z,.46f,.43f,.43f,new float[]{.83f,.91f,1,1});
            box(x-.36f,.98f,z,.14f,.65f,.17f,col);box(x+.36f,.98f,z,.14f,.65f,.17f,col);
            box(x-.16f,.28f,z,.18f,.52f,.21f,new float[]{.2f,.28f,.4f,1});box(x+.16f,.28f,z,.18f,.52f,.21f,new float[]{.2f,.28f,.4f,1});
            box(x,1.03f,z-.22f,.22f,.15f,.035f,new float[]{.6f,1,.93f,1});
        }
        void box(float x,float y,float z,float sx,float sy,float sz,float[] color){
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);
            GLES20.glUniformMatrix4fv(modelLoc,1,false,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4fv(colorLoc,1,color,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
