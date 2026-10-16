package com.acid.duality;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.*;
import android.bluetooth.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.UUID;

public class MainActivity extends Activity {
    GameView game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) { WindowManager.LayoutParams lp = getWindow().getAttributes(); lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; getWindow().setAttributes(lp); }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        game = new GameView(this);
        setContentView(game);
    }
    @Override public void onBackPressed() {
        if (game != null) game.back();
        else super.onBackPressed();
    }

    class GameView extends View {
        Canvas c; Paint p = new Paint(3); final Handler handler = new Handler(Looper.getMainLooper());
        int screen=0; // menu, modes, lobby, game
        int mode=0; // wifi, bluetooth
        boolean host=false, connected=false, started=false, red=true, showSettings=false;
        String status="СОЗДАЙТЕ ЛОББИ ИЛИ ПОДКЛЮЧИТЕСЬ", ip="", roomName="ЗАЛ СИГНАЛОВ";
        Socket socket; ServerSocket server; BufferedReader reader; PrintWriter writer;
        BluetoothAdapter bt; BluetoothSocket btSocket; BluetoothServerSocket btServer;
        final UUID BT_UUID=UUID.fromString("b6a8a2d1-63ab-4ec3-b4c9-4c6c9b210101");
        Thread netThread;
        float anim=0, px=0.18f, py=0.65f, ox=0.78f, oy=0.65f;
        int room=0, progress=0, coins=0, moves=0;
        boolean doorOpen=false, remoteReady=false, pathHint=false;
        String[] rooms={"ЗАЛ СИГНАЛОВ","ШИФР НА СТЕНЕ","НЕВИДИМЫЙ МОСТ","ЗЕРКАЛЬНЫЙ КОРИДОР","ДВЕ ПЛИТЫ","ПОСЛЕДНЯЯ ДВЕРЬ"};
        final int BG=Color.rgb(9,13,25), PANEL=Color.rgb(19,27,45), WHITE=Color.rgb(237,244,255), MUTED=Color.rgb(142,159,186), RED=Color.rgb(255,77,96), BLUE=Color.rgb(71,157,255), MINT=Color.rgb(96,255,210), GOLD=Color.rgb(255,198,94);
        HashMap<String,RectF> buttons=new HashMap<>();
        GameView(Context ctx){super(ctx); setLayerType(View.LAYER_TYPE_SOFTWARE,null); bt=BluetoothAdapter.getDefaultAdapter(); setFocusable(true);}
        void rect(float l,float t,float r,float b,float radius,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,radius,radius,p);}
        void stroke(float l,float t,float r,float b,float radius,int color,float sw){p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(sw);c.drawRoundRect(l,t,r,b,radius,radius,p);p.setStyle(Paint.Style.FILL);}
        void text(String s,float x,float y,float size,int color,boolean bold){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));c.drawText(s,x,y,p);}
        void center(String s,float x,float y,float size,int color,boolean bold){p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",1):Typeface.create("sans-serif",0));p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawText(s,x-p.measureText(s)/2,y,p);}
        void button(String id,String label,float x,float y,float w,float h,int accent,boolean filled){
            RectF r=new RectF(x,y,x+w,y+h); buttons.put(id,r);
            p.setShadowLayer(filled?15:8,0,4,Color.argb(filled?85:45,Color.red(accent),Color.green(accent),Color.blue(accent)));
            if(filled){
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(x,y,x+w,y+h,brighten(accent),accent,Shader.TileMode.CLAMP));
                c.drawRoundRect(x,y,x+w,y+h,17,17,p);
                p.setShader(null);p.clearShadowLayer();
                rect(x+4,y+3,x+w-4,y+Math.max(5,h*.12f),4,Color.argb(48,255,255,255));
            }else{
                p.setShader(new LinearGradient(x,y,x+w,y+h,Color.rgb(32,46,70),Color.rgb(16,24,41),Shader.TileMode.CLAMP));
                c.drawRoundRect(x,y,x+w,y+h,17,17,p);p.setShader(null);p.clearShadowLayer();
                stroke(x,y,x+w,y+h,17,Color.argb(220,Color.red(accent),Color.green(accent),Color.blue(accent)),2);
            }
            center(label,x+w/2,y+h/2+6,Math.min(19,h*.29f),filled?BG:WHITE,true);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas); c=canvas; float w=getWidth(),h=getHeight(); anim+=0.035f;
            c.drawColor(BG);
            // Ambient aurora, deliberately lightweight for phones.
            p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(15,35,55),BG,Color.rgb(31,18,39)},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            for(int i=0;i<24;i++){float x=(float)((i*97+Math.sin(anim+i)*18)%Math.max(1,w));float y=(float)((i*53+Math.cos(anim*.7+i)*13)%Math.max(1,h));p.setColor(Color.argb(30+(i%4)*12,150,205,255));c.drawCircle(x,y,1+(i%3),p);}
            buttons.clear();
            if(screen==0) drawMenu(w,h);
            else if(screen==1) drawModes(w,h);
            else if(screen==2) drawLobby(w,h);
            else drawGame(w,h);
            if(showSettings){rect(w*.27f,h*.18f,w*.73f,h*.82f,24,PANEL);stroke(w*.27f,h*.18f,w*.73f,h*.82f,24,BLUE,2);center("НАСТРОЙКИ",w/2,h*.30f,27,WHITE,true);center("DUALITY • два игрока — один разум",w/2,h*.41f,17,MUTED,false);button("settingsBack","НАЗАД",w*.39f,h*.64f,w*.22f,h*.1f,MINT,true);}
            postInvalidateDelayed(33);
        }
        void drawMenu(float w,float h){
            p.setShader(new RadialGradient(w*.23f,h*.23f,w*.34f,new int[]{Color.argb(48,96,255,210),Color.argb(18,71,157,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP)); c.drawRect(0,0,w,h,p); p.setShader(null);
            text("DUALITY",w*.075f,h*.22f,Math.min(62,w*.078f),WHITE,true);
            rect(w*.078f,h*.245f,w*.21f,h*.253f,4,MINT);
            text("TWO MINDS. ONE WAY OUT.",w*.08f,h*.30f,Math.min(19,w*.025f),MINT,true);
            text("ДВА ИГРОКА. ОДНА ГОЛОВОЛОМКА.",w*.08f,h*.38f,Math.min(17,w*.022f),WHITE,false);
            // Isometric platform and two softly bobbing 3D-styled characters.
            drawPlatform(w*.53f,h*.19f,w*.40f,h*.58f);
            c.save(); c.scale(2.75f,2.75f,w*.64f,h*.48f);
            drawCharacter(w*.64f,h*.48f,RED,(float)Math.sin(anim)*1.5f,true); c.restore();
            c.save(); c.scale(2.75f,2.75f,w*.82f,h*.48f);
            drawCharacter(w*.82f,h*.48f,BLUE,(float)Math.cos(anim*1.2f)*1.5f,true); c.restore();
            center("01",w*.64f,h*.67f,15,RED,true);center("02",w*.82f,h*.67f,15,BLUE,true);
            button("play","▶   ИГРАТЬ",w*.08f,h*.56f,w*.27f,h*.115f,MINT,true);
            button("how","КАК ИГРАТЬ",w*.08f,h*.70f,w*.25f,h*.10f,BLUE,false);
            text("CO-OP PUZZLE  •  2 PLAYERS  •  LANDSCAPE",w*.08f,h*.91f,12,MUTED,true);
            button("settings","⚙",w*.91f,h*.07f,w*.055f,h*.10f,BLUE,false);
        }
        void drawModes(float w,float h){
            button("back","← НАЗАД",w*.06f,h*.08f,w*.15f,h*.09f,BLUE,false);
            center("КАК ВЫ БУДЕТЕ ИГРАТЬ?",w/2,h*.22f,Math.min(31,w*.04f),WHITE,true);
            center("Выберите способ соединить два устройства",w/2,h*.29f,17,MUTED,false);
            rect(w*.15f,h*.37f,w*.47f,h*.74f,22,PANEL);stroke(w*.15f,h*.37f,w*.47f,h*.74f,22,BLUE,2);
            center("⌁",w*.31f,h*.51f,45,BLUE,true);center("ОДНА WI-FI СЕТЬ",w*.31f,h*.59f,21,WHITE,true);center("По локальной сети",w*.31f,h*.64f,15,MUTED,false);
            button("wifi","ВЫБРАТЬ",w*.21f,h*.665f,w*.20f,h*.055f,BLUE,true);
            rect(w*.53f,h*.37f,w*.85f,h*.74f,22,PANEL);stroke(w*.53f,h*.37f,w*.85f,h*.74f,22,RED,2);
            center("⌁",w*.69f,h*.51f,45,RED,true);center("BLUETOOTH",w*.69f,h*.59f,21,WHITE,true);center("Рядом, без роутера",w*.69f,h*.64f,15,MUTED,false);
            button("bt","ВЫБРАТЬ",w*.60f,h*.665f,w*.18f,h*.055f,RED,true);
        }
        void drawLobby(float w,float h){
            // Full-bleed lobby backdrop: layered aurora, portal rings and floating light.
            p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(9,18,39),Color.rgb(17,25,53),Color.rgb(31,16,43)},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.23f,h*.47f,w*.38f,new int[]{Color.argb(90,255,50,86),Color.argb(25,255,50,86),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.78f,h*.45f,w*.40f,new int[]{Color.argb(100,40,135,255),Color.argb(25,40,135,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);p.setShader(null);
            for(int i=0;i<34;i++){
                float xx=(i*137f+(float)Math.sin(anim*.8f+i)*22)%w;
                float yy=(i*71f+(float)Math.cos(anim*.55f+i)*17)%h;
                p.setColor(Color.argb(35+(i%5)*13,160,210,255));c.drawCircle(xx,yy,1.2f+(i%3)*.65f,p);
            }
            // Large, subtle portals behind the player cards.
            drawPortal(w*.27f,h*.47f,Math.min(w*.16f,h*.32f),RED,anim);
            drawPortal(w*.75f,h*.47f,Math.min(w*.16f,h*.32f),BLUE,-anim*.8f);
            button("back","← В МЕНЮ",w*.045f,h*.055f,w*.15f,h*.085f,BLUE,false);
            center("КОМНАТА ОЖИДАНИЯ",w/2,h*.135f,Math.min(29,w*.038f),WHITE,true);
            center("Два игрока • одна команда • один выход",w/2,h*.195f,Math.min(16,w*.022f),Color.rgb(169,188,218),false);
            // Connection status pill
            float pillW=w*.22f;
            rect(w/2-pillW/2,h*.225f,w/2+pillW/2,h*.275f,22,Color.argb(180,13,25,44));
            stroke(w/2-pillW/2,h*.225f,w/2+pillW/2,h*.275f,22,connected?MINT:Color.rgb(104,133,172),1.5f);
            p.setColor(connected?MINT:GOLD);c.drawCircle(w/2-pillW*.37f,h*.25f,4,p);
            center(connected?"ИГРОК ПОДКЛЮЧЁН":"ОЖИДАНИЕ НАПАРНИКА",w/2+7,h*.257f,Math.min(13,w*.017f),connected?MINT:GOLD,true);
            // Floating translucent player cards.
            float top=h*.315f,bottom=h*.705f;
            rect(w*.095f,top,w*.455f,bottom,25,Color.argb(205,17,29,51));
            stroke(w*.095f,top,w*.455f,bottom,25,Color.argb(205,Color.red(RED),Color.green(RED),Color.blue(RED)),2);
            rect(w*.545f,top,w*.905f,bottom,25,Color.argb(205,17,29,51));
            stroke(w*.545f,top,w*.905f,bottom,25,connected?Color.argb(220,Color.red(BLUE),Color.green(BLUE),Color.blue(BLUE)):Color.rgb(63,81,112),2);
            drawPlayerCard(w*.11f,h*.34f,w*.44f,h*.68f,host?"ВЫ • ХОСТ":"ИГРОК 1",red?RED:BLUE,true);
            drawPlayerCard(w*.56f,h*.34f,w*.89f,h*.68f,connected?"ИГРОК 2":"СВОБОДНОЕ МЕСТО",connected?(red?BLUE:RED):Color.rgb(105,126,158),connected);
            if(host){
                button("color","СМЕНИТЬ ЦВЕТ",w*.17f,h*.735f,w*.25f,h*.09f,GOLD,false);
                button("start","НАЧАТЬ ИГРУ",w*.58f,h*.735f,w*.25f,h*.09f,MINT,connected);
            }else{
                center(status,w/2,h*.78f,Math.min(15,w*.02f),connected?MINT:GOLD,true);
            }
            String connectionLine=mode==0?(host?"ВАШ IP: "+localIp()+"  •  ПОРТ 47822":"ПОДКЛЮЧЕНИЕ ПО ЛОКАЛЬНОЙ СЕТИ"):"BLUETOOTH • СОПРЯГИТЕ УСТРОЙСТВА";
            center(connectionLine,w/2,h*.91f,Math.min(13,w*.018f),Color.rgb(145,166,197),true);
            center("DUALITY  /  CO-OP",w/2,h*.96f,11,Color.rgb(90,113,148),true);
        }
        void drawPortal(float x,float y,float radius,int color,float phase){
            p.setStyle(Paint.Style.STROKE);
            for(int i=0;i<4;i++){
                float rr=radius*(.62f+i*.13f)+(float)Math.sin(phase+i)*2.5f;
                p.setColor(Color.argb(25+i*13,Color.red(color),Color.green(color),Color.blue(color)));
                p.setStrokeWidth(2.5f+i*.8f);
                c.drawOval(x-rr,y-rr*1.18f,x+rr,y+rr*1.18f,p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(x,y,radius,new int[]{Color.argb(35,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            c.drawCircle(x,y,radius,p);p.setShader(null);
        }
        void drawPlayerCard(float l,float t,float r,float b,String name,int col,boolean active){
            // Character is deliberately scaled up in lobby cards to read as the hero.
            float cx=(l+r)/2, cy=t+(b-t)*.48f;
            p.setColor(Color.argb(75,Color.red(col),Color.green(col),Color.blue(col)));
            c.drawOval(cx-(r-l)*.14f,cy+(b-t)*.20f,cx+(r-l)*.14f,cy+(b-t)*.28f,p);
            c.save();c.scale(1.8f,1.8f,cx,cy);
            drawCharacter(cx,cy,col,(float)Math.sin(anim*1.2f)*2.5f,active);
            c.restore();
            center(name,cx,b-12,Math.min(17,(r-l)*.055f),active?WHITE:Color.rgb(148,166,194),true);
            if(active){
                rect(cx-(r-l)*.105f,b-35,cx+(r-l)*.105f,b-31,2,col);
            }
        }
        void drawGame(float w,float h){
            text("DUALITY  /  "+(room+1)+" OF "+rooms.length,w*.04f,h*.07f,14,MUTED,true);
            text(rooms[Math.min(room,rooms.length-1)],w*.04f,h*.13f,Math.min(25,w*.035f),WHITE,true);
            drawRoom(0,h*.15f,w,h);
            float gx=w*.055f,gy=h*.69f,sz=Math.min(w*.065f,h*.13f);
            button("left","←",gx,gy,sz,sz,BLUE,false);
            button("up","↑",gx+sz+6,gy-sz-5,sz,sz,BLUE,false);
            button("down","↓",gx+sz+6,gy,sz,sz,BLUE,false);
            button("right","→",gx+2*(sz+6),gy,sz,sz,BLUE,false);
            button("action","ДЕЙСТВИЕ",w*.72f,h*.76f,w*.20f,h*.09f,MINT,true);
            button("exit","Ⅱ",w*.91f,h*.06f,w*.055f,h*.09f,WHITE,false);
            text("ХОДЫ "+moves,w*.43f,h*.92f,12,MUTED,true);
            text("ЦЕЛЬ: "+goalText(),w*.42f,h*.98f,13,GOLD,true);
            if(room==2 && !pathHint) center(red?"ТЫ ВИДИШЬ МОСТ — ВЕДИ СИНЕГО":"ПУТЬ НЕ ВИДЕН • СЛУШАЙ НАПАРНИКА",w*.55f,h*.25f,14,GOLD,true);
            if(doorOpen) center("ДВЕРЬ ОТКРЫТА — НАЖМИ «ДЕЙСТВИЕ»",w*.55f,h*.31f,15,MINT,true);
        }
        String goalText(){switch(room){case 0:return "ОБА НАЖМИТЕ НА ПЛИТЫ";case 1:return "ВВЕДИ КОД НА СТЕНЕ";case 2:return "ПРОЙДИТЕ МОСТ ВМЕСТЕ";case 3:return "НАЙДИТЕ ДВЕ РЫЧАГА";case 4:return "ВСТАНЬТЕ НА ДВЕ ПЛИТЫ";default:return "ОТКРОЙТЕ ВЫХОД ВМЕСТЕ";}}
        void drawPlatform(float cx,float top,float width,float height){
            Path floor=new Path();floor.moveTo(cx-width*.48f,top+height*.44f);floor.lineTo(cx,top);floor.lineTo(cx+width*.48f,top+height*.44f);floor.lineTo(cx,top+height);floor.close();
            p.setShader(new LinearGradient(cx-width/2,top,cx+width/2,top+height,Color.rgb(31,53,78),Color.rgb(14,23,41),Shader.TileMode.CLAMP));c.drawPath(floor,p);p.setShader(null);
            p.setColor(Color.argb(90,90,180,220));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawPath(floor,p);p.setStyle(Paint.Style.FILL);
            for(int i=1;i<5;i++){float t=i/5f;Path line=new Path();line.moveTo(cx-width*.48f*(1-t),top+height*.44f*(1-t));line.lineTo(cx+width*.48f*(1-t),top+height*.44f*(1-t));p.setColor(Color.argb(35,130,200,255));c.drawPath(line,p);}
        }
        void drawRoom(float l,float t,float r,float b){
            // Perspective walls and tiled floor; lightweight faux-3D, no heavy assets.
            Path wall=new Path();wall.moveTo(l,t+ (b-t)*.22f);wall.lineTo((l+r)/2,t);wall.lineTo(r,t+(b-t)*.22f);wall.lineTo(r,b*.0f+t+(b-t)*.55f);wall.lineTo(l,t+(b-t)*.55f);wall.close();
            p.setShader(new LinearGradient(l,t,r,t+(b-t)*.55f,Color.rgb(35,58,84),Color.rgb(16,24,42),Shader.TileMode.CLAMP));c.drawPath(wall,p);p.setShader(null);
            Path floor=new Path();floor.moveTo(l,t+(b-t)*.55f);floor.lineTo(r,t+(b-t)*.55f);floor.lineTo(r-(r-l)*.12f,b);floor.lineTo(l+(r-l)*.12f,b);floor.close();
            p.setShader(new LinearGradient(l,t+(b-t)*.55f,r,b,Color.rgb(30,44,64),Color.rgb(12,18,31),Shader.TileMode.CLAMP));c.drawPath(floor,p);p.setShader(null);
            p.setColor(Color.argb(65,105,153,190));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);
            for(int i=1;i<7;i++){float yy=t+(b-t)*(.55f+i*.065f);c.drawLine(l+(r-l)*.02f,yy,r-(r-l)*.02f,yy,p);}
            for(int i=0;i<7;i++){float xx=l+(r-l)*i/6f;c.drawLine((l+r)/2,t+(b-t)*.55f,xx,b,p);}p.setStyle(Paint.Style.FILL);
            // door, clue panel and glowing floor plates
            float doorX=(l+r)/2, doorY=t+(b-t)*.24f;
            rect(doorX-(r-l)*.075f,doorY,doorX+(r-l)*.075f,t+(b-t)*.59f,8,doorOpen?Color.rgb(31,95,84):Color.rgb(8,13,24));
            stroke(doorX-(r-l)*.075f,doorY,doorX+(r-l)*.075f,t+(b-t)*.59f,8,doorOpen?MINT:BLUE,3);
            if(room==1){rect(l+(r-l)*.12f,t+(b-t)*.25f,l+(r-l)*.34f,t+(b-t)*.40f,10,Color.rgb(7,16,28));stroke(l+(r-l)*.12f,t+(b-t)*.25f,l+(r-l)*.34f,t+(b-t)*.40f,10,GOLD,2);center("4  1  7",l+(r-l)*.23f,t+(b-t)*.34f,18,GOLD,true);}
            if(room==2){for(int i=0;i<5;i++){float xx=l+(r-l)*(.30f+i*.09f);float yy=t+(b-t)*(.62f+i*.055f);if(red||pathHint)rect(xx,yy,xx+(r-l)*.055f,yy+14,4,Color.argb(210,96,255,210));}}
            if(room==3){rect(l+(r-l)*.12f,t+(b-t)*.30f,l+(r-l)*.18f,t+(b-t)*.43f,5,GOLD);rect(r-(r-l)*.18f,t+(b-t)*.30f,r-(r-l)*.12f,t+(b-t)*.43f,5,BLUE);}
            if(room==4){rect(l+(r-l)*.22f,t+(b-t)*.70f,l+(r-l)*.34f,t+(b-t)*.76f,5,RED);rect(r-(r-l)*.34f,t+(b-t)*.70f,r-(r-l)*.22f,t+(b-t)*.76f,5,BLUE);}
            float ax=l+(r-l)*(.22f+px*.54f), ay=t+(b-t)*(.58f+py*.25f);
            float bx=l+(r-l)*(.22f+ox*.54f), by=t+(b-t)*(.58f+oy*.25f);
            drawCharacter(ax,ay,red?RED:BLUE,(float)Math.sin(anim*2)*2,true);
            drawCharacter(bx,by,red?BLUE:RED,(float)Math.cos(anim*2)*2,connected);
            if(room==2&&!red&&!pathHint){rect(l+(r-l)*.27f,t+(b-t)*.60f,r-(r-l)*.27f,b,0,Color.argb(90,5,8,15));}
        }
        // Full humanoid characters with layered clothing, hair, seams and fine fabric texture.
        void drawPuzzleBackdrop(float w,float h,boolean prominent){
            c.save();
            c.rotate(-13,w*.5f,h*.5f);
            float cell=Math.max(54,w*.105f);
            for(int row=-2;row<=(int)(h/cell)+2;row++){
                for(int col=-2;col<=(int)(w/cell)+2;col++){
                    float x=col*cell+(row%2)*cell*.5f;
                    float y=row*cell;
                    int alpha=prominent?24:13;
                    int tint=((row+col)&1)==0?MINT:BLUE;
                    p.setColor(Color.argb(alpha,Color.red(tint),Color.green(tint),Color.blue(tint)));
                    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);
                    Path piece=new Path();
                    float z=cell*.28f;
                    piece.moveTo(x,y);piece.lineTo(x+cell*.30f,y);
                    piece.cubicTo(x+cell*.30f,y-z*.6f,x+cell*.53f,y-z*.6f,x+cell*.53f,y);
                    piece.lineTo(x+cell,y);piece.lineTo(x+cell,y+cell*.30f);
                    piece.cubicTo(x+cell+z*.6f,y+cell*.30f,x+cell+z*.6f,y+cell*.53f,x+cell,y+cell*.53f);
                    piece.lineTo(x+cell,y+cell);piece.lineTo(x+cell*.30f,y+cell);
                    piece.cubicTo(x+cell*.30f,y+cell+z*.6f,x+cell*.53f,y+cell+z*.6f,x+cell*.53f,y+cell);
                    piece.lineTo(x,y+cell);piece.close();c.drawPath(piece,p);
                    p.setStyle(Paint.Style.FILL);
                    if(prominent && ((row*3+col*5)%7==0)){
                        p.setColor(Color.argb(13,Color.red(tint),Color.green(tint),Color.blue(tint)));
                        c.drawRoundRect(x+cell*.12f,y+cell*.12f,x+cell*.88f,y+cell*.88f,8,8,p);
                    }
                }
            }
            p.setStyle(Paint.Style.FILL);c.restore();
        }
        void drawCharacter(float x,float y,int color,float bob,boolean active){
            float scale=Math.max(.72f,getWidth()/1000f);
            float u=scale;
            float headR=5.7f*u;
            float top=y-18*u+bob;
            // Ground shadow
            p.setColor(Color.argb(85,0,0,0));
            c.drawOval(x-10*u,y+19*u,x+10*u,y+23*u,p);
            // Legs and shoes, slightly separated for a natural human silhouette
            p.setShader(new LinearGradient(x-5*u,top+18*u,x+5*u,y+19*u,new int[]{Color.rgb(39,48,68),Color.rgb(13,19,31)},null,Shader.TileMode.CLAMP));
            Path legs=new Path();
            legs.moveTo(x-5*u,top+17*u);legs.lineTo(x+5*u,top+17*u);
            legs.lineTo(x+4.7f*u,y+17*u);legs.lineTo(x+1.2f*u,y+18*u);
            legs.lineTo(x-.7f*u,top+24*u);legs.lineTo(x-2.5f*u,y+17*u);
            legs.lineTo(x-7*u,y+17*u);legs.close();c.drawPath(legs,p);p.setShader(null);
            rect(x-8*u,y+16*u,x-1*u,y+20*u,2*u,Color.rgb(8,12,21));
            rect(x+1*u,y+16*u,x+8*u,y+20*u,2*u,Color.rgb(8,12,21));
            // Neck
            rect(x-2.2f*u,top+4*u,x+2.2f*u,top+9*u,1.5f*u,Color.rgb(202,157,130));
            // Arms behind the jacket
            p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(4.1f*u);
            p.setColor(Color.rgb(181,131,108));
            c.drawLine(x-5*u,top+9*u,x-8.4f*u,top+17*u,p);
            c.drawLine(x+5*u,top+9*u,x+8.4f*u,top+17*u,p);
            // Human torso / jacket silhouette
            Path torso=new Path();
            torso.moveTo(x-3.8f*u,top+7*u);torso.lineTo(x-6*u,top+8.5f*u);
            torso.lineTo(x-5.8f*u,top+16*u);torso.lineTo(x-3.3f*u,top+17*u);
            torso.lineTo(x+3.3f*u,top+17*u);torso.lineTo(x+5.8f*u,top+16*u);
            torso.lineTo(x+6*u,top+8.5f*u);torso.lineTo(x+3.8f*u,top+7*u);torso.close();
            p.setShader(new LinearGradient(x-6*u,top+7*u,x+6*u,top+17*u,new int[]{brighten(color),color,Color.rgb(17,26,42)},null,Shader.TileMode.CLAMP));
            c.drawPath(torso,p);p.setShader(null);
            // Collar, center zip and jacket panels
            p.setColor(Color.argb(210,235,244,255));
            Path collar=new Path();collar.moveTo(x-3.5f*u,top+7.4f*u);collar.lineTo(x,top+10*u);collar.lineTo(x-1.1f*u,top+11.5f*u);collar.close();c.drawPath(collar,p);
            Path collar2=new Path();collar2.moveTo(x+3.5f*u,top+7.4f*u);collar2.lineTo(x,top+10*u);collar2.lineTo(x+1.1f*u,top+11.5f*u);collar2.close();c.drawPath(collar2,p);
            p.setColor(Color.argb(190,9,18,31));p.setStrokeWidth(.9f*u);c.drawLine(x,top+10*u,x,top+16.3f*u,p);
            p.setColor(Color.argb(150,255,255,255));p.setStrokeWidth(.65f*u);
            c.drawLine(x-4.2f*u,top+10*u,x-3.7f*u,top+15.5f*u,p);
            c.drawLine(x+4.2f*u,top+10*u,x+3.7f*u,top+15.5f*u,p);
            // Fabric microtexture: short woven strokes on both jacket panels
            for(int i=0;i<5;i++){
                float yy=top+(10.5f+i*1.1f)*u;
                p.setColor(Color.argb(35,255,255,255));
                c.drawLine(x-4.4f*u,yy,x-2.5f*u,yy+.55f*u,p);
                c.drawLine(x+2.5f*u,yy+.55f*u,x+4.4f*u,yy,p);
            }
            // Shoulder patch with player color
            rect(x-4.8f*u,top+9*u,x-3.2f*u,top+11*u,1*u,brighten(color));
            // Face, ears, hair and fringe
            p.setColor(Color.rgb(215,174,147));c.drawCircle(x,top+1.2f*u,headR,p);
            p.setColor(Color.rgb(192,142,116));c.drawCircle(x-headR*.92f,top+1.5f*u,1.2f*u,p);c.drawCircle(x+headR*.92f,top+1.5f*u,1.2f*u,p);
            p.setShader(new LinearGradient(x-headR,top-headR,x+headR,top+2*u,new int[]{Color.rgb(43,34,38),Color.rgb(14,19,30)},null,Shader.TileMode.CLAMP));
            Path hair=new Path();hair.moveTo(x-headR,top+1.5f*u);hair.quadTo(x-headR*1.15f,top-headR*1.15f,x,top-headR*.95f);hair.quadTo(x+headR*1.1f,top-headR*.9f,x+headR,top+1*u);hair.lineTo(x+headR*.25f,top+.1f*u);hair.lineTo(x-.35f*u,top+1.1f*u);hair.lineTo(x-headR,top+1.5f*u);hair.close();c.drawPath(hair,p);p.setShader(null);
            // Eyes and small face shading
            p.setColor(Color.rgb(35,31,35));c.drawCircle(x-2*u,top+1.8f*u,.55f*u,p);c.drawCircle(x+2*u,top+1.8f*u,.55f*u,p);
            p.setColor(Color.argb(120,255,235,215));c.drawLine(x-.8f*u,top+4*u,x+.8f*u,top+4*u,p);
            // Subtle rim light and a thin player-color aura
            if(active){
                p.setColor(Color.argb(45,Color.red(color),Color.green(color),Color.blue(color)));
                c.drawCircle(x,y,24*u,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*u);p.setColor(Color.argb(175,Color.red(color),Color.green(color),Color.blue(color)));
                c.drawRoundRect(x-8*u,top-7*u,x+8*u,y+21*u,7*u,7*u,p);p.setStyle(Paint.Style.FILL);
            }
        }
        int brighten(int color){return Color.rgb(Math.min(255,Color.red(color)+35),Math.min(255,Color.green(color)+35),Math.min(255,Color.blue(color)+35));}
        void drawPlatform(float x,float y,float w,float h,boolean ignored){drawPlatform(x,y,w,h);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();
            if(showSettings){showSettings=false;invalidate();return true;}
            for(Map.Entry<String,RectF> entry:buttons.entrySet())if(entry.getValue().contains(x,y)){click(entry.getKey());return true;}
            if(screen==3){if(x<getWidth()*.5f){if(y>getHeight()*.60f){px=Math.max(.03f,Math.min(.95f,px+(x<getWidth()*.25f?-.06f:.06f)));moves++;sendState();}else{py=Math.max(.05f,py-.07f);moves++;sendState();}}else if(y>getHeight()*.58f){ox=Math.max(.03f,Math.min(.95f,ox+(x<getWidth()*.75f?-.05f:.05f)));moves++;sendState();}invalidate();}
            return true;
        }
        void click(String id){
            switch(id){
                case "play":screen=1;break;
                case "settings":showSettings=true;break;
                case "how":new AlertDialog.Builder(MainActivity.this).setTitle("Как играть").setMessage("Красный и синий игроки видят разные подсказки. Обменивайтесь информацией, двигайтесь кнопками и нажимайте ДЕЙСТВИЕ у механизмов. Пройдите шесть комнат вместе.").setPositiveButton("ПОНЯТНО",null).show();break;
                case "back":if(screen==2){closeNetwork();screen=1;}else screen=0;break;
                case "wifi":mode=0;showLobbyChoice();break;
                case "bt":mode=1;showLobbyChoice();break;
                case "settingsBack":showSettings=false;break;
                case "color":red=!red;send("COLOR,"+(red?"RED":"BLUE"));break;
                case "start":if(host&&connected){started=true;screen=3;room=0;progress=0;doorOpen=false;send("START");}else toast("Сначала дождитесь второго игрока");break;
                case "left":px=Math.max(.03f,px-.05f);moves++;sendState();break;
                case "right":px=Math.min(.95f,px+.05f);moves++;sendState();break;
                case "up":py=Math.max(.05f,py-.06f);moves++;sendState();break;
                case "down":py=Math.min(.95f,py+.06f);moves++;sendState();break;
                case "action":doAction();break;
                case "exit":new AlertDialog.Builder(MainActivity.this).setTitle("Покинуть комнату?").setMessage("Прогресс текущей сессии будет остановлен.").setNegativeButton("ОСТАТЬСЯ",null).setPositiveButton("ВЫЙТИ",(d,v)->{closeNetwork();screen=0;}).show();break;
            }
            invalidate();
        }
        void showLobbyChoice(){
            String[] options={"Создать лобби","Подключиться к лобби"};
            new AlertDialog.Builder(MainActivity.this).setTitle("МУЛЬТИПЛЕЕР").setItems(options,(d,which)->{if(which==0)createLobby();else joinLobby();}).setNegativeButton("ОТМЕНА",null).show();
        }
        void createLobby(){
            host=true;connected=false;started=false;screen=2;status="ОЖИДАНИЕ ИГРОКА";invalidate();
            if(mode==0){
                netThread=new Thread(()->{try{server=new ServerSocket(47822);Socket s=server.accept();socket=s;setupStreams(s);connected=true;status="ИГРОК ПОДКЛЮЧИЛСЯ";send("WELCOME");runReader();}catch(Exception ex){status="Не удалось создать лобби: "+ex.getMessage();postInvalidate();}});netThread.start();
            }else{
                if(bt==null){toast("На устройстве нет Bluetooth");return;}
                if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=0){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN"},401);return;}
                netThread=new Thread(()->{try{btServer=bt.listenUsingRfcommWithServiceRecord("DUALITY",BT_UUID);btSocket=btServer.accept();connected=true;setupStreams(new BluetoothStreamSocket(btSocket));status="ИГРОК ПОДКЛЮЧИЛСЯ";runReader();}catch(Exception ex){status="Bluetooth: "+ex.getMessage();postInvalidate();}});netThread.start();
            }
        }
        void joinLobby(){
            if(mode==0){
                EditText input=new EditText(MainActivity.this);input.setSingleLine(true);input.setHint("IP хоста, например 192.168.1.5");input.setInputType(16);
                new AlertDialog.Builder(MainActivity.this).setTitle("Подключиться по Wi-Fi").setView(input).setNegativeButton("ОТМЕНА",null).setPositiveButton("ПОДКЛЮЧИТЬ",(d,v)->{String address=input.getText().toString().trim();if(address.isEmpty()){toast("Введи IP хоста");return;}ip=address;host=false;screen=2;status="ПОДКЛЮЧЕНИЕ…";netThread=new Thread(()->{try{Socket s=new Socket();s.connect(new InetSocketAddress(ip,47822),7000);socket=s;setupStreams(s);connected=true;status="СОЕДИНЕНО";send("JOIN");runReader();}catch(Exception ex){status="Не удалось подключиться. Проверь IP и Wi-Fi.";postInvalidate();}});netThread.start();invalidate();}).show();
            }else{
                if(bt==null){toast("На устройстве нет Bluetooth");return;}
                if(Build.VERSION.SDK_INT>=31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=0){requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT","android.permission.BLUETOOTH_SCAN"},402);return;}
                try{Set<BluetoothDevice> devices=bt.getBondedDevices();if(devices.isEmpty()){toast("Сначала сопрягите телефоны в настройках Bluetooth");return;}ArrayList<BluetoothDevice> list=new ArrayList<>(devices);String[] names=new String[list.size()];for(int i=0;i<list.size();i++)names[i]=list.get(i).getName()+"\n"+list.get(i).getAddress();new AlertDialog.Builder(MainActivity.this).setTitle("Выберите сопряжённое устройство").setItems(names,(d,which)->{host=false;screen=2;netThread=new Thread(()->{try{BluetoothSocket bs=list.get(which).createRfcommSocketToServiceRecord(BT_UUID);bs.connect();btSocket=bs;setupStreams(new BluetoothStreamSocket(bs));connected=true;send("JOIN");status="СОЕДИНЕНО";runReader();}catch(Exception ex){status="Не удалось подключиться по Bluetooth";postInvalidate();}});netThread.start();invalidate();}).show();}catch(Exception ex){toast("Разрешите доступ к Bluetooth");}
            }
        }
        // Adapts RFCOMM streams to the same simple message protocol used by Wi-Fi.
        class BluetoothStreamSocket extends Socket {
            BluetoothSocket b; BluetoothStreamSocket(BluetoothSocket s){b=s;}
            @Override public InputStream getInputStream() throws IOException{return b.getInputStream();}
            @Override public OutputStream getOutputStream() throws IOException{return b.getOutputStream();}
            @Override public synchronized void close() throws IOException{b.close();}
        }
        void setupStreams(Socket s)throws IOException{reader=new BufferedReader(new InputStreamReader(s.getInputStream()));writer=new PrintWriter(new OutputStreamWriter(s.getOutputStream()),true);}
        void runReader(){try{String line;while(reader!=null&&(line=reader.readLine())!=null){final String msg=line;handler.post(()->handleMessage(msg));}}catch(Exception ignored){}}
        void send(String s){try{if(writer!=null)writer.println(s);}catch(Exception ignored){}}
        void sendState(){send("POS,"+px+","+py+","+ox+","+oy+","+room+","+moves);}
        void handleMessage(String s){
            try{
                String[] a=s.split(",");
                if(a[0].equals("JOIN")){connected=true;status="ВТОРОЙ ИГРОК ПОДКЛЮЧИЛСЯ";}
                else if(a[0].equals("WELCOME")){connected=true;status="СОЕДИНЕНО";}
                else if(a[0].equals("COLOR")){red=a.length>1&&a[1].equals("RED");}
                else if(a[0].equals("START")){started=true;screen=3;room=0;}
                else if(a[0].equals("POS")&&a.length>=7){ox=Float.parseFloat(a[1]);oy=Float.parseFloat(a[2]);px=Float.parseFloat(a[3]);py=Float.parseFloat(a[4]);room=Integer.parseInt(a[5]);moves=Integer.parseInt(a[6]);}
                else if(a[0].equals("ROOM")){room=Integer.parseInt(a[1]);doorOpen=false;progress=0;}
                else if(a[0].equals("DOOR")){doorOpen=true;}
            }catch(Exception ignored){}
            invalidate();
        }
        void doAction(){
            if(room==0){progress++;if(progress>=2){doorOpen=true;send("DOOR");}}
            else if(room==1){new AlertDialog.Builder(MainActivity.this).setTitle("ПАНЕЛЬ ДОСТУПА").setMessage("Подсказка на стене: 4 — 1 — 7").setView(new EditText(MainActivity.this)).setNegativeButton("ОТМЕНА",null).setPositiveButton("ВВЕСТИ",(d,v)->{doorOpen=true;send("DOOR");invalidate();}).show();return;}
            else if(room==2){pathHint=!pathHint;progress++;if(progress>=2){doorOpen=true;send("DOOR");}}
            else {progress++;if(progress>=2){doorOpen=true;send("DOOR");}}
            if(doorOpen){room=Math.min(rooms.length-1,room+1);progress=0;doorOpen=false;send("ROOM,"+room);if(room==rooms.length-1)toast("Финальная комната — найдите общий выход!");}
            invalidate();
        }
        String localIp(){try{for(java.util.Enumeration<java.net.NetworkInterface> en=NetworkInterface.getNetworkInterfaces();en.hasMoreElements();){NetworkInterface ni=en.nextElement();for(java.util.Enumeration<InetAddress> a=ni.getInetAddresses();a.hasMoreElements();){InetAddress addr=a.nextElement();if(!addr.isLoopbackAddress()&&addr instanceof Inet4Address)return addr.getHostAddress();}}}catch(Exception ignored){}return "IP не найден";}
        void closeNetwork(){try{if(socket!=null)socket.close();}catch(Exception ignored){}try{if(server!=null)server.close();}catch(Exception ignored){}try{if(btSocket!=null)btSocket.close();}catch(Exception ignored){}try{if(btServer!=null)btServer.close();}catch(Exception ignored){}socket=null;server=null;writer=null;reader=null;connected=false;host=false;}
        void toast(String s){Toast.makeText(MainActivity.this,s,Toast.LENGTH_LONG).show();}
        void back(){if(showSettings){showSettings=false;}else if(screen==3){screen=2;}else if(screen==2){closeNetwork();screen=1;}else if(screen==1)screen=0;else superBack();invalidate();}
        void superBack(){MainActivity.super.onBackPressed();}
    }
}
