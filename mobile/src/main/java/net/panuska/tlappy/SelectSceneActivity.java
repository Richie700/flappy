package net.panuska.tlappy;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import net.panuska.tlappy.mobile.R;

import java.util.Random;

/**
 * Flappy:
 * Original game created by dB-SOFT in 1984 for SHARP MZ-800 computer.
 * Java version by Petr Slechta, 2014.
 * Android version by Petr Panuska, 2015.
 */
public class SelectSceneActivity extends TlappyActivity {
    private static final String TAG = SelectSceneActivity.class.getSimpleName();
    private int currentShift;
    private int minShift;
    private int maxShift;
    private int sceneWidth;
    private int sceneHeight;
    private int sceneWidthSpace;
    private FingerUpGestureDetector detector;
    private int openScenes;
    private ViewGroup buttons;
    private int focusedIndex;

    static final float START_FLING_INHIBITION = 0.05f;
    static final float FLING_INHIBITION = 0.95f;
    static final float FLING_LIMIT = 1f;
    static final int NUM_VISIBLE_SCENES = 2;  // how many scenes visible in the screen

    static int getSceneWidth(int sceneSpace) {
        return (Device.displayWidth - (NUM_VISIBLE_SCENES - 1) * sceneSpace) / NUM_VISIBLE_SCENES;
    }

    static int getSceneHeight(int sceneWidth) {
        return Constants.SCREEN_HEIGHT * sceneWidth / Constants.SCREEN_WIDTH;
    }

    static int getMinShift(int sceneWidth) {
        return (sceneWidth - Device.displayWidth) / 2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selectscene);
        view = (SurfaceView) findViewById(R.id.sceneList);
        buttons = (ViewGroup) findViewById(R.id.selectSceneButtons);
        focusedIndex = 1;  // play button
    }

    @Override
    protected void onResume() {
        super.onResume();

        int sceneSpace = getResources().getInteger(R.integer.sceneSpace);
        sceneWidth = getSceneWidth(sceneSpace);
        sceneWidthSpace = sceneWidth + sceneSpace;
        sceneHeight = getSceneHeight(sceneWidth);
        Log.d(TAG, "SceneSpace: "+sceneSpace+"; SceneWidth: "+sceneWidth+"; SceneHeight: "+sceneHeight);
        openScenes = retrieveOpenScenes();
        Log.d(TAG, "OpenScenes: "+openScenes);
        minShift = getMinShift(sceneWidth);
        maxShift = (openScenes + 1) * sceneWidthSpace - (Device.displayWidth + sceneWidth) / 2 - sceneSpace;
        currentShift = retrieveCurrentShift(minShift);
        Log.d(TAG, "minShift: "+minShift+"; maxShift: "+maxShift+"; currentShift: "+currentShift);

        detector = new FingerUpGestureDetector(this, new FingerUpGestureDetector.SimpleOnGestureListener() {
            private AsyncTask<Void, Void, Void> flingScrolling;

            @Override
            public synchronized boolean onFling(MotionEvent e1, MotionEvent e2, final float velocityX, float velocityY) {
                Log.d(TAG, "fling " + velocityX);

                cancelFlingScrolling();
                flingScrolling = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        float slowingVelocityX = velocityX * START_FLING_INHIBITION;
                        while (!isCancelled() && Math.abs(slowingVelocityX) > FLING_LIMIT) {
                            shiftScenes(-slowingVelocityX);
                            adjustMinMaxCenterShiftPosition(slowingVelocityX);
                            slowingVelocityX = slowingVelocityX * FLING_INHIBITION;
                        }
                        synchronized(SelectSceneActivity.this) {
                            flingScrolling = null;
                        }
                        return null;
                    }
                }.execute();
                return true;
            }

            @Override
            public synchronized void onShowPress(MotionEvent e) {
                Log.d(TAG, "Finger down");
                buttons.setVisibility(View.GONE);
                cancelFlingScrolling();
            }

            @Override
            public synchronized boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                Log.d(TAG, "scroll " + distanceX);
                buttons.setVisibility(View.GONE);
                cancelFlingScrolling();
                shiftScenes(distanceX);
                return true;
            }

            @Override
            public boolean onFingerUp(MotionEvent e) {
                Log.d(TAG, "Finger up");
                adjustMinMaxCenterShiftPosition(0f);
                return true;
            }

            void shiftScenes(float distanceX) {
                currentShift += distanceX;
                drawScenes();
            }

            boolean adjustShiftEvent;
            void adjustMinMaxCenterShiftPosition(float slowingVelocityX) {
                int aimedShift;
                if (currentShift < minShift) {
                    // adjust min position
                    aimedShift = minShift;
                } else if (currentShift > maxShift) {
                    // adjust max position
                    aimedShift = maxShift;
                } else {
                    Log.d(TAG, "slowingVelocityX: "+slowingVelocityX);
                    // center position only if fling velocity is slowing down
                    if (Math.abs(slowingVelocityX) > 2f) return;
                    aimedShift = ((currentShift-2*minShift) / sceneWidthSpace) * sceneWidthSpace + minShift;
                    // add one minShift to make currentShift always positive; add another minShift to make screen center to be the vertical decision line
                    // round up to sceneWidthSpace; subtract minShift back to move it to middle of the sceneWidth
                }

                Log.d(TAG, "aimedShift: "+aimedShift+"; currentShift: "+currentShift);

                if ((currentShift < aimedShift)) {
                    Log.d(TAG, "center start - left");
                    cancelFlingScrolling();
                    adjustShiftEvent = true;
                    while (adjustShiftEvent && currentShift < aimedShift) {
                        Log.d(TAG, "center continue - left; currentShift: "+currentShift);
                        currentShift += (aimedShift - currentShift) / 10 + 1;
                        drawScenes();
                    }
                    Log.d(TAG, "Leaving cycle - left: "+currentShift);
                }
                if ((currentShift > aimedShift)) {
                    Log.d(TAG, "center start - right");
                    cancelFlingScrolling();
                    adjustShiftEvent = true;
                    while (adjustShiftEvent && currentShift > aimedShift) {
                        Log.d(TAG, "center continue - right; currentShift: "+currentShift);
                        currentShift -= (currentShift - aimedShift) / 10 + 1;
                        drawScenes();
                    }
                    Log.d(TAG, "Leaving cycle - right: "+currentShift);
                }
                SelectSceneActivity.this.buttons.post(new Runnable() { //make sure it's run in UI thread
                    public void run() {
                        buttons.setVisibility(View.VISIBLE);
                        Log.d(TAG, "Buttons visible");
                    }
                });

            }

            private void cancelFlingScrolling() {
                synchronized (SelectSceneActivity.this) {
                    if (flingScrolling != null) {
                        Log.d(TAG, "Cancelling fling");
                        flingScrolling.cancel(true);
                    }
                }
                adjustShiftEvent = false;
            }
        });

        predrawScenes();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (detector.onTouchEvent(event)) return true;
        return super.onTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                detector.fListener.onScroll(null, null, +sceneWidthSpace, 0); //todo scroll smoothly (now it jumps to the next screen)
                detector.fListener.onFingerUp(null);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                detector.fListener.onScroll(null, null, -sceneWidthSpace, 0);
                detector.fListener.onFingerUp(null);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                focusedIndex--;
                if (focusedIndex < 0) focusedIndex = buttons.getChildCount()-1;
                buttons.getChildAt(focusedIndex).requestFocus();
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                focusedIndex++;
                if (focusedIndex >= buttons.getChildCount()) focusedIndex = 0;
                buttons.getChildAt(focusedIndex).requestFocus();
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER: // button can have no focus (then handle its click)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    buttons.getChildAt(focusedIndex).callOnClick();
                    break;
                } else {
                    return super.dispatchKeyEvent(event);
                }
            default: return super.dispatchKeyEvent(event);
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            oldShift = currentShift-1; // make sure to draw the first time
            drawScenes();
        }
    }

    private int oldShift;
    private void drawScenes() {
        if (currentShift == oldShift) return;    // no change, nothing to draw
        int firstVisibleScene = currentShift / sceneWidthSpace;
        int remnant = currentShift % sceneWidthSpace;
        Canvas c = view.getHolder().lockCanvas();
        if (c != null) {
            c.drawColor(android.R.color.black, PorterDuff.Mode.CLEAR);

            int y = (Device.displayHeight - sceneHeight) / 2;
            if (currentShift > oldShift) {
                for (int i = 0; i < NUM_VISIBLE_SCENES + 1; i++) {
                    drawBitmap(c, firstVisibleScene+i, sceneWidthSpace * i - remnant, y);
                }
            } else {
                for (int i = NUM_VISIBLE_SCENES; i >= 0; i--) {
                    drawBitmap(c, firstVisibleScene+i, sceneWidthSpace * i - remnant, y);
                }
            }

            view.getHolder().unlockCanvasAndPost(c);
        }
        oldShift = currentShift;
    }

    private void drawBitmap(Canvas c, int num, int x, int y) {
        Bitmap b = getSceneBitmap(num);
        if (b == null) return;
        c.drawBitmap(b, x, y, null);
    }

    int firstPredrawnScene;
    private static final int NUM_PREDRAWN_SCENES = NUM_VISIBLE_SCENES + 1;
    Bitmap predrawnScenes[];

    private Bitmap getSceneBitmap(int num) {
        if (num < 0 || num >= openScenes+NUM_VISIBLE_SCENES+2) {
            return null;
        }
        if (num < firstPredrawnScene) {
            Log.d(TAG, "Decreasing ");
            System.arraycopy(predrawnScenes, 0, predrawnScenes, 1, NUM_PREDRAWN_SCENES - 1);

            predrawnScenes[0] = drawSceneBitmap(num);
            firstPredrawnScene--;
        } else {
            if (num >= firstPredrawnScene + NUM_PREDRAWN_SCENES) {
                Log.d(TAG, "Increasing ");
                System.arraycopy(predrawnScenes, 1, predrawnScenes, 0, NUM_PREDRAWN_SCENES - 1);

                predrawnScenes[NUM_PREDRAWN_SCENES - 1] = drawSceneBitmap(num);
                firstPredrawnScene++;
            }
        }
        return predrawnScenes[num - firstPredrawnScene];
    }

    private void predrawScenes() {
        firstPredrawnScene = currentShift / sceneWidthSpace;
        Log.d(TAG, "First predrawn scene: "+firstPredrawnScene);
        predrawnScenes = new Bitmap[NUM_PREDRAWN_SCENES];
        for (int i = 0; i < predrawnScenes.length; i++) {
            predrawnScenes[i] = drawSceneBitmap(firstPredrawnScene + i);
        }
    }

    private Bitmap drawSceneBitmap(int num) {
        Log.d(TAG, "Drawing scaled scene " + num);
        if (num >= Constants.NUM_SCENES || num < 0) {
            return null;
        }
        Scene scene = new Scene(num+1, new VRAM());
        ScreenScore score = ScreenScore.screenScores[num];
        int lives = getLives(num);
        if (num <= openScenes) {
            scene.predrawScene(true, lives, score.myBestScore);
        } else {
            scene.predrawSceneNumberScreen(lives, score.myBestScore);
        }
        Bitmap b = scene.getVRAM().getImage().getBitmap();
        return Bitmap.createScaledBitmap(b, sceneWidth, sceneHeight, true);
    }

    // copied from http://android-er.blogspot.com/2013/09/adjust-saturation-of-bitmap-with.html
    private Bitmap updateSaturation(Bitmap src, float settingSat) {

        int w = src.getWidth();
        int h = src.getHeight();

        Bitmap bitmapResult =
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvasResult = new Canvas(bitmapResult);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(settingSat);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvasResult.drawBitmap(src, 0, 0, paint);

        return bitmapResult;
    }

    private int getSceneNo() {
        return (currentShift + Device.displayWidth / 2) / sceneWidthSpace;
    }

    public void startGame(View v) {
        int scNo = getSceneNo();
        startActivity(new Intent(SelectSceneActivity.this, FullscreenActivity.class).putExtra(SCENE_NUMBER, scNo + 1));
        storeSceneNumber(scNo);
        finish();
        return;
    }

    public void showHighScores(View v) {
        int scNo = getSceneNo();
        startActivity(new Intent(SelectSceneActivity.this, BestScoreActivity.class).putExtra(SCENE_NUMBER, scNo));
    }

    public void showHint(View v) {
        int scNo = getSceneNo();
        String videoIds = SceneVideoId.YOU_TUBE_IDS[scNo];
        String[] idS = videoIds.split(";");                 // multiple videos about a single scene are split by ";"
        int videoIndex = new Random().nextInt(idS.length);  // select random video
        String id = idS[videoIndex];
        try {
            Intent videoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id));
            startActivity(videoIntent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "No YouTube application found!! Trying web instead!!");
            try {
                Intent videoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.youtube.com/watch?v=" + id));
                startActivity(videoIntent);
            } catch (Exception e) {
                showMessage(getString(R.string.cant_show_hints, id));
                Log.e(TAG, "Can't start the video");
                return;
            }
        }
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, IntroActivity.class));
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        storeScrollShift(currentShift);
    }
}
