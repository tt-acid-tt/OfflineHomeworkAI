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
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512 | 4096);
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
            text("DUALITY",w*.075f,h*.22f,Math.min(58,w*.075f),WHITE,true);
            rect(w*.078f,h*.245f,w*.21f,h*.253f,4,MINT);
            text("TWO MINDS. ONE WAY OUT.",w*.08f,h*.30f,Math.min(18,w*.024f),MUTED,true);
            text("ДВА ИГРОКА. ОДНА ГОЛОВОЛОМКА.",w*.08f,h*.38f,Math.min(17,w*.022f),WHITE,false);
            // Isometric platform and two softly bobbing 3D-styled characters.
            drawPlatform(w*.53f,h*.19f,w*.40f,h*.58f);
            drawCharacter(w*.64f,h*.48f,RED,(float)Math.sin(anim)*4,true);
            drawCharacter(w*.82f,h*.48f,BLUE,(float)Math.cos(anim*1.2f)*4,true);
            center("01",w*.64f,h*.67f,15,RED,true);center("02",w*.82f,h*.67f,15,BLUE,true);
            button("play","ИГРАТЬ",w*.08f,h*.56f,w*.25f,h*.115f,MINT,true);
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
        void drawCharacter(float x,float y,int color,float bob,boolean active){
            float s=Math.max(1,getWidth()/1000f), r=14*s;
            p.setColor(Color.argb(70,0,0,0));c.drawOval(x-r*1.2f,y+r*1.6f,x+r*1.2f,y+r*2.1f,p);
            // Boots, arms and glossy highlights give both mascots a more polished silhouette.
            rect(x-r*.66f,y+r*.52f+bob,x-r*.12f,y+r*1.42f+bob,4*s,Color.rgb(22,29,44));
            rect(x+r*.12f,y+r*.52f+bob,x+r*.66f,y+r*1.42f+bob,4*s,Color.rgb(22,29,44));
            p.setShader(new LinearGradient(x-r*1.2f,y-r*.1f,x+r*1.2f,y+r*.9f,brighten(color),color,Shader.TileMode.CLAMP));
            c.drawRoundRect(x-r*1.18f,y-r*.27f+bob,x-r*.72f,y+r*.52f+bob,r*.22f,r*.22f,p);
            c.drawRoundRect(x+r*.72f,y-r*.27f+bob,x+r*1.18f,y+r*.52f+bob,r*.22f,r*.22f,p);
            c.drawRoundRect(x-r,y-r*.45f+bob,x+r,y+r*.8f+bob,r*.55f,r*.55f,p);p.setShader(null);
            p.setShader(new LinearGradient(x-r*.6f,y-r*.35f,x+r*.25f,y+r*.55f,Color.argb(120,255,255,255),Color.argb(0,255,255,255),Shader.TileMode.CLAMP));
            c.drawRoundRect(x-r*.68f,y-r*.28f+bob,x-r*.05f,y+r*.45f+bob,r*.25f,r*.25f,p);p.setShader(null);
            p.setColor(brighten(color));c.drawCircle(x,y-r*.75f+bob,r*.76f,p);
            p.setColor(Color.rgb(238,248,255));c.drawOval(x-r*.44f,y-r*.90f+bob,x-r*.04f,y-r*.55f+bob,p);c.drawOval(x+r*.05f,y-r*.90f+bob,x+r*.45f,y-r*.55f+bob,p);
            p.setColor(Color.rgb(13,21,35));c.drawCircle(x-r*.22f,y-r*.72f+bob,r*.13f,p);c.drawCircle(x+r*.24f,y-r*.72f+bob,r*.13f,p);
            p.setColor(Color.argb(150,255,255,255));c.drawCircle(x-r*.32f,y-r*.96f+bob,r*.09f,p);
            if(active){p.setColor(Color.argb(90, color==RED?255:80,color==BLUE?170:90,255));c.drawCircle(x,y,r*1.55f,p);p.setColor(color);c.drawCircle(x,y,r*1.55f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);c.drawCircle(x,y,r*1.55f,p);p.setStyle(Paint.Style.FILL);}
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
