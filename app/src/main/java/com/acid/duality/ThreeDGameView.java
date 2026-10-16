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
import android.graphics.Typeface;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
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
    volatile int stage=0, moves=0;
    volatile int choiceIndex=0, peerChoice=0, resultCode=0, lastPeerResult=0;
    volatile boolean started=false, solo=false, ready=false;
    volatile float stickX=0, stickY=0, playerX=0, playerZ=0, yaw=0, pitch=0;
    volatile float peerX=0, peerZ=-7, peerYaw=0;
    volatile boolean peerReady=false, confirmed=false;
    volatile String notice="Создайте комнату или подключитесь к другу";
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
        playerX=0; playerZ=0; yaw=0; pitch=0;
        lan.host();
        notice="Комната создана";
        hud.invalidate();
    }
    void joinRoom(String ip) {
        solo=false; started=false; screen=1; stage=0; ready=false;
        playerX=0; playerZ=0; yaw=0; pitch=0;
        lan.join(ip);
        notice="Подключение...";
        hud.invalidate();
    }
    void beginSolo() {
        lan.close(); solo=true; started=true; screen=2; stage=0; ready=false; moves=0;
        playerX=0; playerZ=0; yaw=0; pitch=0; stickX=stickY=0;
        notice="Тренировка: пройдите по светящимся меткам";
        hud.invalidate();
    }
    void startOnlineGame() {
        if(lan.hosting && lan.connected) { started=true; screen=2; stage=0; ready=false; playerX=0;playerZ=0;yaw=0;pitch=0;notice="Вместе откройте шлюзы"; }
        hud.invalidate();
    }
    void backToMenu() {
        lan.close(); screen=0; started=false; solo=false; ready=false; stickX=stickY=0;
        notice="Создайте комнату или подключитесь к другу"; hud.invalidate();
    }
    void selectPrevious() { if(screen==2 && nearInteract && !confirmed){choiceIndex=(choiceIndex+3)%4;hud.invalidate();} }
    void selectNext() { if(screen==2 && nearInteract && !confirmed){choiceIndex=(choiceIndex+1)%4;hud.invalidate();} }
    void interact() {
        if(screen!=2) return;
        if(stage>=4) { backToMenu(); return; }
        if(!nearInteract || confirmed) return;
        confirmed=true; ready=true; moves++;
        notice="Ответ зафиксирован. Ждите второго игрока.";
        hud.invalidate();
    }

    void packetTick() {
        if(!solo && lan.connected) {
            lan.setOutgoing(String.format(Locale.US,"%.3f,%.3f,%.3f,%d,%d,%d,%d,%d",playerX,playerZ,yaw,stage,started?1:0,ready?1:0,choiceIndex,resultCode));
            String[] p=lan.remote.split(",");
            if(p.length>=8) try {
                peerX=Float.parseFloat(p[0]); peerZ=Float.parseFloat(p[1]); peerYaw=Float.parseFloat(p[2]);
                int remoteStage=Integer.parseInt(p[3]); boolean remoteStarted=Integer.parseInt(p[4])==1;
                peerReady=Integer.parseInt(p[5])==1; peerChoice=Integer.parseInt(p[6]); int remoteResult=Integer.parseInt(p[7]);
                if(!lan.hosting) {
                    boolean stageChanged=remoteStage!=stage;
                    stage=remoteStage; started=remoteStarted; if(started) screen=2;
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
                p.setColor(Color.argb(24,40,238,218));c.drawCircle(w*.78f,h*.42f,h*.48f,p);
                text(c,"DUALITY",w*.085f,h*.24f,Math.min(70,w*.09f),Color.WHITE,true);
                fill(c,w*.09f,h*.275f,w*.24f,h*.285f,5,Color.rgb(70,255,214));
                text(c,"TWO MINDS. ONE WAY OUT.",w*.09f,h*.35f,Math.min(19,w*.025f),Color.rgb(70,255,214),true);
                text(c,"Кооперативная 3D-головоломка",w*.09f,h*.43f,Math.min(20,w*.026f),Color.rgb(211,225,245),false);
                float bw=w*.30f,bx=w*.09f,top=h*.52f,bh=h*.105f,gap=h*.025f;
                fill(c,bx,top,bx+bw,top+bh,18,Color.rgb(70,255,214));center(c,"СОЗДАТЬ КОМНАТУ",bx+bw/2,top+bh*.64f,Math.min(20,w*.025f),Color.rgb(4,20,31),true);
                fill(c,bx,top+bh+gap,bx+bw,top+2*bh+gap,18,Color.argb(220,28,46,69));stroke(c,bx,top+bh+gap,bx+bw,top+2*bh+gap,18,Color.argb(100,120,200,230),1);
                center(c,"ПОДКЛЮЧИТЬСЯ ПО IP",bx+bw/2,top+bh+gap+bh*.64f,Math.min(19,w*.023f),Color.WHITE,true);
                fill(c,bx,top+2*(bh+gap),bx+bw,top+3*bh+2*gap,18,Color.argb(130,28,46,69));
                center(c,"ТРЕНИРОВКА",bx+bw/2,top+2*(bh+gap)+bh*.64f,Math.min(19,w*.023f),Color.rgb(202,216,239),true);
                // Clean two-character key art, only on menu.
                drawMenuHero(c,w*.72f,h*.54f,Color.rgb(255,73,107),1.0f);
                drawMenuHero(c,w*.84f,h*.49f,Color.rgb(65,145,255),0.88f);
                text(c,"ОДНА СЕТЬ Wi-Fi  •  ДВА ИГРОКА",w*.58f,h*.84f,Math.min(16,w*.019f),Color.rgb(155,180,212),true);
            } else if(g.screen==1){
                p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(5,12,25),Color.rgb(10,27,44)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                text(c,"КОМНАТА DUALITY",w*.09f,h*.22f,Math.min(38,w*.05f),Color.WHITE,true);
                text(c,g.lan.hosting?"Передай другу IP-адрес ниже. Оба телефона должны быть в одной Wi-Fi сети.":"Подключаемся к комнате друга в этой же Wi-Fi сети.",w*.09f,h*.32f,Math.min(18,w*.022f),Color.rgb(194,213,237),false);
                if(g.lan.hosting){text(c,"ТВОЙ IP: "+LanSession.localIp(),w*.09f,h*.44f,Math.min(26,w*.033f),Color.rgb(70,255,214),true);text(c,"ПОРТ: "+LanSession.PORT,w*.09f,h*.50f,18,Color.WHITE,true);}
                text(c,g.lan.status,w*.09f,h*.60f,Math.min(19,w*.024f),g.lan.connected?Color.rgb(70,255,214):Color.rgb(255,195,104),true);
                if(g.lan.hosting && g.lan.connected){
                    fill(c,w*.62f,h*.68f,w*.88f,h*.81f,18,Color.rgb(70,255,214));center(c,"НАЧАТЬ ИГРУ",w*.75f,h*.755f,19,Color.rgb(4,20,31),true);
                } else if(!g.lan.hosting && g.lan.connected){
                    center(c,"Ждём, пока создатель комнаты начнёт игру…",w*.5f,h*.77f,18,Color.WHITE,false);
                }
                fill(c,w*.09f,h*.77f,w*.27f,h*.86f,14,Color.argb(180,33,49,70));center(c,"НАЗАД",w*.18f,h*.825f,18,Color.WHITE,true);
            } else {
                // In-game overlay is intentionally sparse: world stays full-bleed.
                fill(c,18,16,Math.min(w*.64f,590),145,15,Color.argb(220,5,13,27));
                text(c,"DUALITY  /  КОМНАТА "+(Math.min(g.stage+1,4))+" ИЗ 4",34,42,17,Color.WHITE,true);
                String objective=g.stage==0?"СИГНАЛЬНЫЙ КОД":g.stage==1?"ЗЕРКАЛЬНЫЙ ШИФР":g.stage==2?"СХЕМА ПИТАНИЯ":"ПОСЛЕДОВАТЕЛЬНОСТЬ АВАРИИ";
                text(c,objective,34,66,Math.min(14,w*.018f),Color.rgb(109,255,221),true);
                String clue="";
                if(g.stage==0) clue=g.lan.hosting||g.solo?"КАНАЛ A: 4 импульса. Узнай B и введи БОЛЬШЕЕ из двух.":"КАНАЛ B: 2 импульса. Узнай A и введи МЕНЬШЕЕ из двух.";
                else if(g.stage==1) clue=g.lan.hosting||g.solo?"ШИФР: символ B — ←. Отрази его по вертикали; таблица ↑1 →2 ↓3 ←4.":"ТВОЙ ДИСК: ←. Передай символ партнёру; введи номер своего символа по таблице ↑1 →2 ↓3 ←4.";
                else if(g.stage==2) clue=g.lan.hosting||g.solo?"СХЕМА A: 3 линии горят. Уточни у B контрольный канал; введи число линий, если он нечётный.":"СХЕМА B: контрольный канал №1. Скажи A номер канала и введи номер активного канала.";
                else if(g.stage==3) clue=g.lan.hosting||g.solo?"ЖУРНАЛ: 1, 3, 2, 4. Передай порядок B; отсортируй по возрастанию и введи последнее число.":"ПРИОРИТЕТ: после сортировки введи второй элемент. Попроси A продиктовать порядок из журнала.";
                text(c,clue,34,92,Math.min(12,w*.016f),Color.rgb(225,235,250),true);
                text(c,"Обсудите подсказки голосом. Консоли находятся по разным сторонам комнаты.",34,116,Math.min(11,w*.014f),Color.rgb(163,188,216),false);
                if(!g.solo && !g.lan.connected){fill(c,w*.36f,16,w*.64f,53,12,Color.argb(210,80,30,39));center(c,"СВЯЗЬ ПОТЕРЯНА",w*.5f,39,13,Color.WHITE,true);}
                // Left movement stick.
                float bx=Math.min(112,w*.12f),by=h-100;
                p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(72,185,215,245));c.drawCircle(bx,by,62,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.argb(135,185,225,255));c.drawCircle(bx,by,62,p);p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(235,214,236,255));c.drawCircle(bx+g.stickX*32,by+g.stickY*32,23,p);
                center(c,"ДВИЖЕНИЕ",bx,by+88,11,Color.argb(220,220,235,250),true);
                // Look zone hint, no extra directional buttons.
                center(c,"ПРОВЕДИ ПАЛЬЦЕМ — ПОВОРОТ КАМЕРЫ",w*.69f,h-20,Math.min(13,w*.016f),Color.argb(185,225,236,250),true);
                if(g.nearInteract){
                    fill(c,w-340,h-144,w-274,h-58,15,Color.argb(225,25,44,66));center(c,"◀",w-307,h-91,25,Color.WHITE,true);
                    fill(c,w-270,h-144,w-204,h-58,15,Color.argb(225,25,44,66));center(c,"▶",w-237,h-91,25,Color.WHITE,true);
                    fill(c,w-198,h-144,w-22,h-58,15,g.confirmed?Color.rgb(255,190,75):Color.rgb(70,255,214));
                    center(c,g.confirmed?"ОЖИДАНИЕ...":"ЗАФИКСИРОВАТЬ",w-110,h-112,Math.min(14,w*.018f),Color.rgb(4,20,31),true);
                    center(c,"КОД: "+new String[]{"1  △","2  ○","3  ◇","4  ☾"}[g.choiceIndex],w-110,h-82,18,Color.rgb(4,20,31),true);
                }
                if(g.stage>=4){
                    fill(c,w*.30f,h*.25f,w*.70f,h*.70f,26,Color.argb(235,6,17,31));
                    center(c,"КОМПЛЕКС ПРОЙДЕН",w*.5f,h*.40f,Math.min(30,w*.04f),Color.rgb(70,255,214),true);
                    center(c,"Четыре комнаты. Четыре разных шифра.",w*.5f,h*.49f,Math.min(17,w*.023f),Color.WHITE,false);
                    fill(c,w*.40f,h*.56f,w*.60f,h*.65f,15,Color.rgb(70,255,214));center(c,"В МЕНЮ",w*.5f,h*.615f,17,Color.rgb(4,20,31),true);
                }
            }
            postInvalidateDelayed(50);
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
                    int id=e.getPointerId(0); lastX=x;lastY=y;
                    if(g.nearInteract&&y>h-160&&x>w-205&&x<w-20){g.interact();return true;}
                    if(g.nearInteract&&y>h-160&&x>w-340&&x<=w-270){g.selectPrevious();return true;}
                    if(g.nearInteract&&y>h-160&&x>w-270&&x<=w-205){g.selectNext();return true;}
                    if(g.stage>=4&&x>w*.40f&&x<w*.60f&&y>h*.52f&&y<h*.70f){g.backToMenu();return true;}
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
                        if(look&&id==lookPointer){float dx=tx-lastX,dy=ty-lastY;g.yaw+=dx*.006f;g.pitch=Math.max(-0.72f,Math.min(0.72f,g.pitch+dy*.0045f));lastX=tx;lastY=ty;}
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP: {
                    int id=e.getPointerId(e.getActionIndex());
                    if(id==stickPointer){stick=false;stickPointer=-1;g.stickX=g.stickY=0;}
                    if(id==lookPointer){look=false;lookPointer=-1;}
                    return true;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    stick=false;look=false;stickPointer=lookPointer=-1;g.stickX=g.stickY=0;return true;
            }
            return true;
        }
        void updateStick(float x,float y,float w,float h){
            float bx=Math.min(112,w*.12f),by=h-100,dx=x-bx,dy=y-by,len=(float)Math.sqrt(dx*dx+dy*dy),max=54;
            if(len>max){dx=dx/len*max;dy=dy/len*max;}g.stickX=dx/max;g.stickY=-dy/max;
        }
    }

    static class World implements GLSurfaceView.Renderer {
        final ThreeDGameView g;
        final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        int program,posLoc,colorLoc,mvpLoc,lightLoc;
        FloatBuffer vertices;
        final float[] cube={
        -0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,
        0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,
        0.5f,-0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,-0.5f,0.5f,0.5f,0.5f,
        -0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,
        -0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f};
        float px=0,pz=0,elapsed=0;
        final String vs="attribute vec3 aPos; uniform mat4 uMVP; varying vec3 vPos; void main(){vPos=aPos;gl_Position=uMVP*vec4(aPos,1.0);}";
        final String fs="precision mediump float;uniform vec4 uColor;uniform vec3 uLight;varying vec3 vPos;void main(){float l=0.58+0.42*max(0.0,dot(normalize(vPos+vec3(0.51)),normalize(uLight)));gl_FragColor=vec4(uColor.rgb*l,uColor.a);}";
        World(ThreeDGameView game){g=game;}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(.018f,.035f,.07f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glDisable(GLES20.GL_CULL_FACE);
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
            ByteBuffer b=ByteBuffer.allocateDirect(cube.length*4).order(ByteOrder.nativeOrder());vertices=b.asFloatBuffer();vertices.put(cube).position(0);
            posLoc=GLES20.glGetAttribLocation(program,"aPos");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");colorLoc=GLES20.glGetUniformLocation(program,"uColor");lightLoc=GLES20.glGetUniformLocation(program,"uLight");
        }
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(proj,0,72,(float)w/Math.max(1,h),.1f,100);}
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);elapsed+=.025f;
            if(g.screen==2 && g.started){
                float speed=.075f,sy=g.stickY,sx=g.stickX;
                float rightX=(float)Math.cos(g.yaw),rightZ=(float)Math.sin(g.yaw);
                float forwardX=(float)Math.sin(g.yaw),forwardZ=-(float)Math.cos(g.yaw);
                px+= (rightX*sx+forwardX*sy)*speed; pz+=(rightZ*sx+forwardZ*sy)*speed;
                px=Math.max(-4.3f,Math.min(4.3f,px));pz=Math.max(-56f,Math.min(2f,pz));
                g.playerX=px;g.playerZ=pz;g.packetTick();
                if(g.solo){
                    g.peerX=2.0f; g.peerZ=roomZ(g.stage); g.peerReady=true; g.peerChoice=clientAnswer(g.stage);
                    if(g.stage<4 && g.ready && g.confirmed && playerAtRoom(px,pz,g.stage,-2.0f)) {
                        if(g.choiceIndex==hostAnswer(g.stage)) {
                            g.stage++; g.ready=false; g.confirmed=false; g.choiceIndex=0;
                            g.notice=g.stage>=4?"Все комнаты пройдены!":"Верно. Следующая комната открыта.";
                        } else {
                            g.ready=false; g.confirmed=false; g.resultCode++; g.notice="Ошибка декодирования. Проверьте подсказку.";
                        }
                    }
                }
                if(!g.solo&&!g.lan.hosting&&g.lan.connected) { /* host publishes authoritative stage in each packet */ }
                g.nearInteract=g.stage<4 && nearConsole(px,pz,g.stage);
            } else {px=g.playerX;pz=g.playerZ;}
            float eyeY=1.62f;
            float cy=(float)Math.cos(g.yaw),sy=(float)Math.sin(g.yaw),cp=(float)Math.cos(g.pitch),sp=(float)Math.sin(g.pitch);
            float tx=px+sy*cp,tz=pz-cy*cp,ty=eyeY+sp;
            Matrix.setLookAtM(view,0,px,eyeY,pz,tx,ty,tz,0,1,0);
            GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(posLoc);vertices.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,12,vertices);GLES20.glUniform3f(lightLoc,-.4f,1,.35f);
            // Four distinct rooms; each has its own console pair and gate.
            box(0,-.18f,-26,12,.36f,62,new float[]{.035f,.065f,.11f,1});
            for(int i=0;i<34;i++){float z=2-i*1.8f;box(0,.012f,z,10.7f,.025f,1.72f,new float[]{.065f,.105f,.16f,1});box(-5.15f,.04f,z,.045f,.05f,1.6f,new float[]{.06f,.65f,.68f,1});box(5.15f,.04f,z,.045f,.05f,1.6f,new float[]{.06f,.65f,.68f,1});}
            box(-5.55f,1.8f,-26,.45f,3.7f,62,new float[]{.08f,.13f,.22f,1});box(5.55f,1.8f,-26,.45f,3.7f,62,new float[]{.08f,.13f,.22f,1});
            for(int i=0;i<17;i++){float z=1-i*3.6f;box(-5.28f,1.8f,z,.13f,3.65f,.16f,new float[]{.09f,.58f,.68f,1});box(5.28f,1.8f,z,.13f,3.65f,.16f,new float[]{.09f,.58f,.68f,1});}
            for(int i=0;i<4;i++) station(i,roomZ(i));
            for(int i=0;i<4;i++) if(g.stage<=i) gate(i,roomZ(i)-4.5f);
            if(g.screen==2 && g.started && !g.solo && g.lan.connected) remoteHero(g.peerX,g.peerZ,g.lan.hosting?new float[]{.2f,.45f,1,1}:new float[]{1,.22f,.36f,1});
            if(g.screen==2 && g.started && g.solo) remoteHero(g.peerX,g.peerZ,new float[]{.2f,.45f,1,1});
            // Soft overhead lamps and destination portal.
            for(int i=0;i<6;i++){float z=-i*6f-2;box(0,3.55f,z,2.2f,.08f,.42f,new float[]{.08f,.3f,.42f,1});}
            box(-1.5f,1.7f,-53,.22f,3.4f,.22f,new float[]{.16f,.9f,.72f,1});box(1.5f,1.7f,-53,.22f,3.4f,.22f,new float[]{.16f,.9f,.72f,1});box(0,3.35f,-53,3.2f,.22f,.22f,new float[]{.16f,.9f,.72f,1});
            GLES20.glDisableVertexAttribArray(posLoc);
        }
        void updateHostPuzzle(){
            if(g.stage>=4) return;
            boolean hostAt=playerAtRoom(g.playerX,g.playerZ,g.stage,-2.0f);
            boolean clientAt=playerAtRoom(g.peerX,g.peerZ,g.stage,2.0f);
            if(hostAt && clientAt && g.ready && g.peerReady) {
                if(g.choiceIndex==hostAnswer(g.stage) && g.peerChoice==clientAnswer(g.stage)) {
                    g.stage++; g.ready=false; g.confirmed=false; g.choiceIndex=0;
                    g.resultCode=0; g.notice=g.stage>=4?"Шлюз открыт. Вы прошли весь комплекс!":"Доступ подтверждён. Переходите в следующую комнату.";
                } else {
                    g.ready=false; g.confirmed=false; g.resultCode++;
                    g.notice="Код не принят. Сопоставьте подсказки ещё раз.";
                }
            }
        }
        boolean nearConsole(float x,float z,int stage){
            if(stage>=4) return false;
            float side=g.solo?-2.0f:(g.lan.hosting?-2.0f:2.0f);
            return Math.abs(x-side)<1.65f && Math.abs(z-roomZ(stage))<2.0f;
        }
        static float roomZ(int n){return -7.0f-n*11.5f;}
        static int hostAnswer(int n){return new int[]{3,1,2,3}[Math.max(0,Math.min(3,n))];}
        static int clientAnswer(int n){return new int[]{1,3,0,1}[Math.max(0,Math.min(3,n))];}
        static boolean playerAtRoom(float x,float z,int stage,float side){
            return Math.abs(x-side)<1.75f && Math.abs(z-roomZ(stage))<2.1f;
        }
        void station(int n,float z){
            float[][] colors={{1,.18f,.3f,1},{.12f,.38f,.9f,1},{.16f,.9f,.72f,1},{.9f,.45f,.12f,1}};
            box(-2,.09f,z,1.7f,.18f,1.7f,colors[n]);
            box(2,.09f,z,1.7f,.18f,1.7f,colors[(n+1)%4]);
            box(-2,.20f,z,1.15f,.06f,1.15f,new float[]{.16f,.9f,.72f,1});
            box(2,.20f,z,1.15f,.06f,1.15f,new float[]{.3f,.6f,1,1});
            if(n==0){box(-2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.9f,.15f,.25f,1});box(2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.15f,.4f,1,1});}
            if(n==1){box(-2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.9f,.65f,.15f,1});box(2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.15f,.8f,.7f,1});}
            if(n==2){box(-2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.9f,.25f,.38f,1});box(2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.15f,.48f,1,1});}
            if(n==3){box(-2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.65f,.35f,1,1});box(2,.8f,z-1.35f,.7f,1.4f,.35f,new float[]{.9f,.35f,.2f,1});}
            box(0,.04f,z-2.7f,1.0f,.08f,.35f,new float[]{.15f,.8f,.68f,1});
        }
        void gate(int n,float z){
            float glow=n%2==0?.2f:.9f;
            box(-3,1.25f,z,.18f,2.5f,.28f,new float[]{.2f,.85f,.77f,1});
            box(3,1.25f,z,.18f,2.5f,.28f,new float[]{.2f,.85f,.77f,1});
            box(0,2.5f,z,6.1f,.18f,.28f,new float[]{.2f,.85f,.77f,1});
            box(0,1.25f,z,.08f,2.3f,.12f,new float[]{.12f,.25f,.34f,1});
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
            GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4fv(colorLoc,1,color,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
