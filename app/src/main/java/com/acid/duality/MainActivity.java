package com.acid.duality;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;
import android.content.pm.ActivityInfo;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private ThreeDGameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        game = new ThreeDGameView(this);
        root.addView(game, new FrameLayout.LayoutParams(-1,-1));
        root.addView(new ThreeDGameView.Hud(this, game), new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }
    @Override protected void onPause() { super.onPause(); if(game!=null) game.onPause(); }
    @Override protected void onResume() { super.onResume(); if(game!=null) game.onResume(); }
    @Override public void onBackPressed() {
        if(game!=null) { game.resetToMenu(); } else super.onBackPressed();
    }
}
