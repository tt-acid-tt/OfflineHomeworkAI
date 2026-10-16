package com.acid.duality;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ThreeDGameView extends GLSurfaceView {
    volatile boolean playing=false, completed=false, actionPulse=false;
    volatile float stickX=0, stickY=0, playerX=0, playerZ=-2;
    volatile int room=0, moves=0;
    private final World renderer;
    public ThreeDGameView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        renderer = new World();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
    }
    void startGame(){ playing=true; completed=false; playerX=0; playerZ=-2; room=0; moves=0; stickX=stickY=0; }
    void resetToMenu(){ playing=false; stickX=stickY=0; }
    void action(){
        if(!playing) { startGame(); return; }
        moves++;
        actionPulse=true;
        if(playerZ < -7.0f*(room+1)+2.3f && room<5) { room++; }
        if(room>=5 && playerZ < -36) completed=true;
    }

    static class Hud extends View {
        final ThreeDGameView game; final Paint p=new Paint(3);
        float downX,downY,baseX,baseY; boolean dragging=false;
        Hud(Context c,ThreeDGameView g){super(c);game=g;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void round(Canvas c,float l,float t,float r,float b,float rad,int color){
            p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);
        }
        void label(Canvas c,String s,float x,float y,float size,int color,boolean bold){
            p.setShader(null);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",1):android.graphics.Typeface.create("sans-serif",0));c.drawText(s,x,y,p);
        }
        void center(Canvas c,String s,float x,float y,float size,int color,boolean bold){
            p.setTextSize(size);p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",1):android.graphics.Typeface.create("sans-serif",0));label(c,s,x-p.measureText(s)/2,y,size,color,bold);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            if(!game.playing){
                p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.rgb(8,15,32),Color.rgb(15,30,53),Color.rgb(26,17,43)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
                p.setColor(Color.argb(60,50,230,255));c.drawCircle(w*.77f,h*.46f,h*.36f,p);
                p.setColor(Color.argb(50,255,65,100));c.drawCircle(w*.68f,h*.48f,h*.25f,p);
                label(c,"DUALITY",w*.09f,h*.28f,Math.min(70,w*.085f),Color.WHITE,true);
                round(c,w*.095f,h*.315f,w*.25f,h*.328f,5,Color.rgb(82,255,218));
                label(c,"TWO MINDS. ONE WAY OUT.",w*.10f,h*.39f,Math.min(20,w*.027f),Color.rgb(82,255,218),true);
                label(c,"Настоящее 3D • кооперативная головоломка",w*.10f,h*.47f,Math.min(20,w*.025f),Color.rgb(205,220,242),false);
                round(c,w*.10f,h*.59f,w*.36f,h*.73f,22,Color.rgb(82,255,218));
                center(c,"▶  ИГРАТЬ",w*.23f,h*.675f,Math.min(24,w*.03f),Color.rgb(7,19,31),true);
                label(c,"OPENGL ES 2.0  •  3D WORLD",w*.10f,h*.84f,13,Color.rgb(130,153,185),true);
                // Stylized hero silhouettes; actual animated 3D heroes are rendered behind this overlay.
                round(c,w*.68f,h*.39f,w*.75f,h*.62f,18,Color.argb(160,18,33,56));
                center(c,"01",w*.715f,h*.69f,18,Color.rgb(255,83,108),true);
                round(c,w*.79f,h*.36f,w*.86f,h*.62f,18,Color.argb(160,18,33,56));
                center(c,"02",w*.825f,h*.69f,18,Color.rgb(78,160,255),true);
            } else {
                round(c,20,18,245,75,16,Color.argb(180,8,16,31));
                label(c,"DUALITY  /  "+(game.room+1)+"/6",38,43,17,Color.WHITE,true);
                label(c,"Шаги: "+game.moves,38,63,12,Color.rgb(142,255,224),false);
                round(c,w*.39f,18,w*.61f,59,15,Color.argb(160,8,16,31));
                center(c,game.completed?"ВЫ ВЫБРАЛИСЬ!":"НАЙДИТЕ ВЫХОД И РЕШАЙТЕ ЗАГАДКИ",w*.50f,44,Math.min(15,w*.018f),Color.WHITE,true);
                // Left joystick, with a clear fixed base and a separate movable knob.
                float bx=Math.min(118,w*.13f), by=h-105;
                p.setShader(null);p.setColor(Color.argb(75,180,210,245));c.drawCircle(bx,by,67,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.argb(125,200,225,255));c.drawCircle(bx,by,67,p);p.setStyle(Paint.Style.FILL);
                c.drawCircle(bx+game.stickX*34,by+game.stickY*34,29,p);p.setColor(Color.argb(230,222,238,255));c.drawCircle(bx+game.stickX*34,by+game.stickY*34,22,p);
                round(c,w-184,h-139,w-34,h-61,26,Color.rgb(76,158,255));
                center(c,"ДЕЙСТВИЕ",w-109,h-92,17,Color.WHITE,true);
                center(c,"Двигайтесь к светящейся двери",w*.5f,h-24,14,Color.argb(220,225,235,255),false);
                if(game.completed){
                    round(c,w*.35f,h*.25f,w*.65f,h*.72f,25,Color.argb(235,10,19,36));
                    center(c,"ГОЛОВОЛОМКА РЕШЕНА",w*.5f,h*.40f,25,Color.rgb(82,255,218),true);
                    center(c,"Вы нашли выход из комплекса",w*.5f,h*.49f,17,Color.WHITE,false);
                    round(c,w*.42f,h*.56f,w*.58f,h*.65f,16,Color.rgb(82,255,218));
                    center(c,"СНАЧАЛА",w*.5f,h*.615f,17,Color.rgb(7,19,31),true);
                }
            }
            postInvalidateDelayed(32);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY(),w=getWidth(),h=getHeight();
            if(!game.playing){
                if(e.getAction()==MotionEvent.ACTION_UP){if(x>w*.07f&&x<w*.43f&&y>h*.53f&&y<h*.79f)game.startGame();invalidate();return true;}return true;
            }
            float bx=Math.min(118,w*.13f),by=h-105;
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=x;downY=y;
                    if(x<w*.27f&&y>h*.48f){dragging=true;updateStick(x,y,bx,by);return true;}
                    if(x>w*.70f&&y>h*.65f){if(game.completed)game.startGame();else game.action();invalidate();return true;}
                    if(game.completed){game.startGame();invalidate();return true;}
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if(dragging)updateStick(x,y,bx,by);return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    dragging=false;game.stickX=game.stickY=0;invalidate();return true;
            }
            return true;
        }
        void updateStick(float x,float y,float bx,float by){
            float dx=x-bx,dy=y-by,len=(float)Math.sqrt(dx*dx+dy*dy),max=55;
            if(len>max){dx=dx/len*max;dy=dy/len*max;}
            game.stickX=dx/max;game.stickY=dy/max;
        }
    }

    static class World implements GLSurfaceView.Renderer {
        private final float[] projection=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        private int program,posLoc,colorLoc,mvpLoc,lightLoc;
        private FloatBuffer vertices;
        private final float[] cube={
            // front
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            // back
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            // left
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            // right
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            // top
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            // bottom
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        };
        private float px=0,pz=-2,elapsed=0;
        private final String vs="attribute vec3 aPos; uniform mat4 uMVP; varying vec3 vPos; void main(){vPos=aPos; gl_Position=uMVP*vec4(aPos,1.0);}";
        private final String fs="precision mediump float; uniform vec4 uColor; uniform vec3 uLight; varying vec3 vPos; void main(){float l=0.62+0.38*max(0.0,dot(normalize(vec3(vPos.x+0.5,vPos.y+0.5,vPos.z+0.5)),normalize(uLight))); gl_FragColor=vec4(uColor.rgb*l,uColor.a);}";
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(0.025f,0.045f,0.09f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);GLES20.glCullFace(GLES20.GL_BACK);
            program=link(shader(GLES20.GL_VERTEX_SHADER,vs),shader(GLES20.GL_FRAGMENT_SHADER,fs));
            ByteBuffer b=ByteBuffer.allocateDirect(cube.length*4).order(ByteOrder.nativeOrder());vertices=b.asFloatBuffer();vertices.put(cube).position(0);
            posLoc=GLES20.glGetAttribLocation(program,"aPos");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");colorLoc=GLES20.glGetUniformLocation(program,"uColor");lightLoc=GLES20.glGetUniformLocation(program,"uLight");
        }
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        int link(int v,int f){int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,58f,(float)w/Math.max(1,h),0.1f,100f);}
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);elapsed+=0.025f;
            if(playing){
                px+=stickX*0.075f; pz+=stickY*0.075f;
                px=Math.max(-4.5f,Math.min(4.5f,px));pz=Math.min(-1.0f,Math.max(-42f,pz));
                playerX=px;playerZ=pz;
            }
            Matrix.setLookAtM(view,0,px+5.0f,5.1f,pz+7.3f,px,0.9f,pz-1.4f,0,1,0);
            GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(posLoc);vertices.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glUniform3f(lightLoc,-0.4f,1.0f,0.35f);
            // Seamless full-bleed floor, with repeating luminous floor panels.
            box(0,-0.18f,-20,12,0.35f,48,new float[]{0.045f,0.075f,0.13f,1});
            for(int i=0;i<24;i++){
                float z=-i*2f;
                box(0,0.005f,z,10.8f,0.025f,1.9f,new float[]{0.075f,0.12f,0.19f,1});
                box(-5.1f,0.035f,z,0.035f,0.04f,1.7f,new float[]{0.08f,0.55f,0.64f,1});
                box(5.1f,0.035f,z,0.035f,0.04f,1.7f,new float[]{0.08f,0.55f,0.64f,1});
            }
            // Tall corridor walls and repeated architectural ribs.
            box(-5.7f,1.8f,-20,0.5f,3.8f,48,new float[]{0.10f,0.16f,0.27f,1});
            box(5.7f,1.8f,-20,0.5f,3.8f,48,new float[]{0.10f,0.16f,0.27f,1});
            for(int i=0;i<12;i++){float z=-i*4f-1;box(-5.35f,1.8f,z,0.16f,3.8f,0.18f,new float[]{0.11f,0.72f,0.77f,1});box(5.35f,1.8f,z,0.16f,3.8f,0.18f,new float[]{0.11f,0.72f,0.77f,1});}
            // Colored puzzle gates, platforms and glowing beacons.
            for(int i=0;i<6;i++){
                float z=-7.5f-i*7f;
                box(-2.7f,1.0f,z,0.12f,2.0f,0.22f,new float[]{0.95f,0.20f,0.34f,1});
                box(2.7f,1.0f,z,0.12f,2.0f,0.22f,new float[]{0.20f,0.52f,1.0f,1});
                box(0,2.1f,z,5.5f,0.12f,0.22f,new float[]{0.18f,0.25f,0.40f,1});
                box((i%2==0)?-2.1f:2.1f,0.14f,z-2.4f,1.5f,0.28f,1.5f,i%2==0?new float[]{0.95f,0.20f,0.34f,1}:new float[]{0.20f,0.52f,1,1});
                box(0,0.08f,z-3.2f,1.2f,0.16f,1.2f,new float[]{0.16f,0.8f,0.66f,1});
            }
            // Two stylized, genuinely 3D low-poly heroes made from lit meshes.
            hero(px,0,pz,new float[]{1.0f,0.20f,0.32f,1},elapsed);
            hero(px+1.2f,0,pz-0.5f,new float[]{0.20f,0.48f,1.0f,1},elapsed+1.2f);
            // Door frame and animated portal at the end of the corridor.
            float doorZ=-42f;
            box(-1.5f,1.7f,doorZ,0.22f,3.4f,0.25f,new float[]{0.2f,0.8f,0.72f,1});
            box(1.5f,1.7f,doorZ,0.22f,3.4f,0.25f,new float[]{0.2f,0.8f,0.72f,1});
            box(0,3.35f,doorZ,3.2f,0.22f,0.25f,new float[]{0.2f,0.8f,0.72f,1});
            box(0,1.6f,doorZ-0.05f,2.65f,2.9f,0.08f,new float[]{0.06f,0.6f,0.57f,1});
            GLES20.glDisableVertexAttribArray(posLoc);
        }
        void hero(float x,float y,float z,float[] col,float t){
            box(x,y+0.86f+(float)Math.sin(t)*0.035f,z,0.62f,0.82f,0.42f,col);
            box(x,y+1.48f+(float)Math.sin(t)*0.035f,z,0.49f,0.49f,0.44f,new float[]{0.83f,0.9f,0.98f,1});
            box(x-0.38f,y+0.83f,z,0.16f,0.65f,0.18f,col);box(x+0.38f,y+0.83f,z,0.16f,0.65f,0.18f,col);
            box(x-0.19f,y+0.25f,z,0.19f,0.5f,0.23f,new float[]{0.24f,0.31f,0.43f,1});
            box(x+0.19f,y+0.25f,z,0.19f,0.5f,0.23f,new float[]{0.24f,0.31f,0.43f,1});
            box(x,y+0.93f,z-0.22f,0.23f,0.18f,0.035f,new float[]{0.85f,1.0f,0.98f,1});
        }
        void box(float x,float y,float z,float sx,float sy,float sz,float[] color){
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,projection,0,mv,0);
            GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4fv(colorLoc,1,color,0);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
