package com.example.replaycamera;

import static android.media.AudioRecord.ERROR;
import static android.media.AudioRecord.ERROR_BAD_VALUE;
import static android.media.AudioRecord.ERROR_DEAD_OBJECT;
import static android.media.AudioRecord.ERROR_INVALID_OPERATION;
import static android.media.AudioRecord.READ_BLOCKING;
import static android.view.Surface.ROTATION_90;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.StrictMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "myLogs";
    public static final String LOG_TAG2 = "myLogs2";
    static final String LOG_TAG_SERVER = "serverLogs";
    private final boolean STATE_PLAY = true;
    private final boolean STATE_PAUSE = false;
    private boolean stateButtonPlay = STATE_PAUSE;
    CameraService[] myCameras = null;
    private CameraManager mCameraManager = null;
    private final int CAMERA1 = 0;
    ImageButton btnPlay;
    ImageButton btnCamera1;
    ImageButton btnCamera2;
    ImageButton btnCamera3;
    ImageButton btnSettings;
    public SurfaceView mImageView = null;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler = null;
    private MediaCodec mCodec = null;
    Surface mEncoderSurface;
    Surface previewSurface;
    ByteBuffer outPutByteBuffer;
    VideoAudioServer videoServer = null;
    VideoAudioServer audioServer = null;
    InetAddress address;
    int portVideo;
    int portAudio;
    int portConnection;
    int currentCamera = 0;
    SoundThread soundThread = null;
    final String CAMERA_NUMBER = "cameraNumber";
    final String CURRENT_FPS = "currentFps";
    final String CURRENT_WIDTH = "currentWidth";
    final String CURRENT_HEIGHT = "currentHeight";
    public Handler handler;
    int currentFps;
    Size currentResolution;
    ConnectionCommonServer commonServer = null;
    ConnectionServer connectionServer = null;
    int sumA = 0;
    int sumV = 0;
    int countA = 0;
    int countV = 0;
    long ptsAudio = 0;
    long ptsVideo = 0;
    TextView tv;
    public Handler mHandler;
    BlockingQueue<byte[]> videoQueue = null;
    BlockingQueue<byte[]> audioQueue = null;
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            //e.printStackTrace();
            Log.e(LOG_TAG, "error stopBackgroundThread " + e.getMessage());
        }
    }
    boolean isLandscape = false;
    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Log.i(LOG_TAG, "  onCreate");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        EdgeToEdge.enable(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        audioQueue = new LinkedBlockingQueue<>();
        videoQueue = new LinkedBlockingQueue<>();

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //Log.d(LOG_TAG, "Запрашиваем разрешение");
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ||
                    (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
            isLandscape = true;
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            //Log.i(LOG_TAG, "  ORIENTATION_LANDSCAPE");
            mImageView = findViewById(R.id.textureView);
            //ImageView.setRotation(-90);

            SharedPreferences sPref = getSharedPreferences("camera_settings", MODE_PRIVATE);
            currentCamera = sPref.getInt(CAMERA_NUMBER, 1);
            currentFps = sPref.getInt(CURRENT_FPS, 0);
            currentResolution = new Size(sPref.getInt(CURRENT_WIDTH, 0), sPref.getInt(CURRENT_HEIGHT, 0));

            //Log.i(LOG_TAG, currentResolution + " create " + currentFps);

            if(commonServer != null){
                commonServer.stopProcess();
            }

            commonServer = new ConnectionCommonServer(currentCamera, currentResolution, currentFps);
            commonServer.start();

            if(connectionServer != null){
                connectionServer.stopProcess();
                connectionServer = null;
            }

            if(currentCamera == 1) {
                portVideo = 5551;
                portAudio = 5552;
                portConnection = 5553;
            }
            else if(currentCamera == 2) {
                portVideo = 5561;
                portAudio = 5562;
                portConnection = 5563;
            }
            else {
                portVideo = 5571;
                portAudio = 5572;
                portConnection = 5573;
            }

            mHandler = new Handler(Looper.getMainLooper()) {
                //@SuppressLint("SetTextI18n")
                @Override
                public void handleMessage(@NonNull Message msg) {
                    super.handleMessage(msg);
                    String text = (String) msg.obj; // Извлекаем текст
                    tv.append(text + "\n"); // Обновляем текст в TextView
                }
            };

            handler = new Handler(Looper.getMainLooper()) {
                public void handleMessage(@NonNull Message msg) {
                    //Log.i(LOG_TAG, "handle.msg = " + msg.what);
                    tv.append("msg.what = " +  msg.what + "\n"); // Обновляем текст в TextView
                    if(msg.what == 1) {
//                        if(mCodec != null){
//

                        if(stateButtonPlay == STATE_PLAY) {
                            btnPlay.callOnClick();
                            tv.append("turn off " + "\n"); // Обновляем текст в TextView
                        }
                    }else if(msg.what == 2){
                        if(stateButtonPlay == STATE_PAUSE) {
                            btnPlay.callOnClick();
                            tv.append("turn on " + "\n"); // Обновляем текст в TextView
                        }
                    }
                }
            };

            connectionServer = new ConnectionServer(portConnection, handler, mHandler, currentFps, currentResolution);
            connectionServer.start();

            btnCamera1 = findViewById(R.id.btnCamera1);

            btnCamera2 = findViewById(R.id.btnCamera2);

            btnCamera3 = findViewById(R.id.btnCamera3);

            btnSettings = findViewById(R.id.btnSettings);
            btnSettings.setBackgroundResource(R.drawable.settings_enabled);

            if(currentCamera == 1){
                btnCamera1.setImageResource(R.drawable.camera1_on);
                btnCamera2.setImageResource(R.drawable.camera2_off);
                btnCamera3.setImageResource(R.drawable.camera3_off);
            } else if (currentCamera == 2){
                btnCamera1.setImageResource(R.drawable.camera1_off);
                btnCamera2.setImageResource(R.drawable.camera2_on);
                btnCamera3.setImageResource(R.drawable.camera3_off);
            } else {
                btnCamera1.setImageResource(R.drawable.camera1_off);
                btnCamera2.setImageResource(R.drawable.camera2_off);
                btnCamera3.setImageResource(R.drawable.camera3_on);
            }

            try {
                address = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            tv = findViewById(R.id.textView);
            tv.setMovementMethod(new ScrollingMovementMethod());
//            tv.addTextChangedListener(new TextWatcher() {
//                @Override
//                public void afterTextChanged(Editable s) {
//                    scrollView.fullScroll(View.FOCUS_DOWN);
//                }
//                // другие методы watcher...
//            });

            btnPlay = findViewById(R.id.btnPlay);
            btnPlay.setBackgroundResource(R.drawable.stream_off);

            btnPlay.setOnClickListener(new View.OnClickListener() {
                @SuppressLint("NewApi")
                @Override
                public void onClick(View v) {
                    tv.append("onclick " + stateButtonPlay + "\n");
                    if (stateButtonPlay == STATE_PLAY) {
//                        if(connectionServer != null){
//                            tv.append("connectionServer != null\n");
//                            connectionServer.stopProcess();
//                            connectionServer = null;
//                            connectionServer = new ConnectionServer(portConnection, handler, mHandler, currentFps, currentResolution);
//                            connectionServer.start();
//
//                        }
//                        mCodec.stop();
//                        mCodec.release();
//                        myCameras[CAMERA1].closeCamera();
                        stateButtonPlay = STATE_PAUSE;
                        btnPlay.setBackgroundResource(R.drawable.stream_off);
                        btnCamera1.setBackgroundResource(R.drawable.camera1_on);
                        btnSettings.setBackgroundResource(R.drawable.settings_enabled);
                        if(currentCamera == 1){
                            btnCamera1.setImageResource(R.drawable.camera1_on);
                            btnCamera2.setImageResource(R.drawable.camera2_off);
                            btnCamera3.setImageResource(R.drawable.camera3_off);
                        } else if (currentCamera == 2){
                            btnCamera1.setImageResource(R.drawable.camera1_off);
                            btnCamera2.setImageResource(R.drawable.camera2_on);
                            btnCamera3.setImageResource(R.drawable.camera3_off);
                        } else {
                            btnCamera1.setImageResource(R.drawable.camera1_off);
                            btnCamera2.setImageResource(R.drawable.camera2_off);
                            btnCamera3.setImageResource(R.drawable.camera3_on);
                        }

                        soundThread.stopRecord();
                        soundThread = null;

                        if(videoServer != null){
                            videoServer.stopProcess();
                            videoServer = null;
                        }

                        if(audioServer != null){
                            audioServer.stopProcess();
                            audioServer = null;
                        }
                        //Log.d(LOG_TAG_SERVER, "stop button");
                    } else {
                        //Log.d(LOG_TAG_SERVER, "start button");
                        videoServer = new VideoAudioServer(portVideo, videoQueue, mHandler);
                        videoServer.start();

                        audioServer = new VideoAudioServer(portAudio, audioQueue, mHandler);
                        audioServer.start();

                        stateButtonPlay = STATE_PLAY;
                        btnPlay.setBackgroundResource(R.drawable.stream_on);
                        btnCamera1.setBackgroundResource(R.drawable.camera1_on_disabled);
                        btnSettings.setBackgroundResource(R.drawable.settings_disabled);

                        if(currentCamera == 1){
                            btnCamera1.setImageResource(R.drawable.camera1_on_disabled);
                            btnCamera2.setImageResource(R.drawable.camera2_off_disabled);
                            btnCamera3.setImageResource(R.drawable.camera3_off_disabled);
                        } else if (currentCamera == 2){
                            btnCamera1.setImageResource(R.drawable.camera1_off_disabled);
                            btnCamera2.setImageResource(R.drawable.camera2_on_disabled);
                            btnCamera3.setImageResource(R.drawable.camera3_off_disabled);
                        } else {
                            btnCamera1.setImageResource(R.drawable.camera1_off_disabled);
                            btnCamera2.setImageResource(R.drawable.camera2_off_disabled);
                            btnCamera3.setImageResource(R.drawable.camera3_on_disabled);
                        }

                        if(soundThread == null) {
                            soundThread = new SoundThread();
                            soundThread.start();
                        }
                        currentCamera = sPref.getInt(CAMERA_NUMBER, 1);
                        currentFps = sPref.getInt(CURRENT_FPS, 0);
                        currentResolution = new Size(sPref.getInt(CURRENT_WIDTH, 0), sPref.getInt(CURRENT_HEIGHT, 0));
                        //Log.i(LOG_TAG, String.valueOf(currentResolution) + " click " + currentFps);
                        mCodec.stop();
                        mCodec.release();
                        myCameras[CAMERA1].closeCamera();
                        setUpMediaCodec();
                        myCameras[CAMERA1].openCamera();
                    }
                }
            });

            MediaCodec encoder;
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                // Получение списка камер с устройства
                myCameras = new CameraService[mCameraManager.getCameraIdList().length];

                if(currentFps == 0) {
                    encoder = MediaCodec.createEncoderByType("video/avc");

                    // создаем обработчик для камеры
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics("0");

                    Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    ArrayList<Integer> listFps = new ArrayList<Integer>();
                    assert fpsRanges != null;
                    for(Range<Integer> fps : fpsRanges){
                        if(fps.getLower().equals(fps.getUpper())){
                            listFps.add(fps.getLower());
                        }
                    }
                    listFps.sort(Collections.reverseOrder());
                    currentFps = listFps.get(0);
                    SharedPreferences.Editor ed = sPref.edit();
                    ed.putInt(CURRENT_FPS, currentFps);
                    ed.apply();
                    ed.commit();
                    //Log.i(LOG_TAG, String.valueOf(listFps));
                    //Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizesJPEG;
                    if (configurationMap != null) {
                        sizesJPEG = configurationMap.getOutputSizes(ImageFormat.JPEG);
                        ArrayList<Size> supportedSizes = new ArrayList<Size>();
                        for (Size s : sizesJPEG) {
                            if (Objects.requireNonNull(encoder.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities()).isSizeSupported(s.getWidth(), s.getHeight())) {
                                supportedSizes.add(s);
                                //Log.i(LOG_TAG, s + " supported");
                            }
                        }
                        Comparator<Size> byWidth = new Comparator<Size>() {
                            @Override
                            public int compare(Size s1, Size s2) {
                                return s2.getWidth() - s1.getWidth();
                            }
                        };
                        Log.i(LOG_TAG, String.valueOf(supportedSizes));
                        supportedSizes.sort(byWidth);
                        //supportedSizes.sort(supportedSizes, byWidth);
                        Log.i(LOG_TAG, String.valueOf(supportedSizes));
                        for(Size s : supportedSizes){
                            if(s.getWidth() <= 1920){
                                currentResolution = new Size(s.getWidth(), s.getHeight());
                                Log.i(LOG_TAG, currentResolution + " init " + currentFps);
                                ed.putInt(CURRENT_WIDTH, currentResolution.getWidth());
                                ed.putInt(CURRENT_HEIGHT, currentResolution.getHeight());
                                ed.apply();
                                ed.commit();
                                break;
                            }
                        }
                    }
                    //Log.i(LOG_TAG, "currentResolution = " + currentResolution);
                }
                myCameras[0] = new CameraService(mCameraManager, "0");
            } catch (CameraAccessException | IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }


            setUpMediaCodec();
            if (myCameras[CAMERA1] != null) {// открываем камеру
                if (!myCameras[CAMERA1].isOpen()) myCameras[CAMERA1].openCamera();
                //myCameras[CAMERA1].
                //mCameraDevice
            }
            tv.append("onCreate\n");
        }

    }

    public void select_camera(View view) {
        if(stateButtonPlay)
            return;
        if(connectionServer != null)
            connectionServer.stopProcess();
        if(videoServer != null)
            videoServer.stopProcess();
        if(audioServer != null)
            audioServer.stopProcess();
        if(view.getId() == R.id.btnCamera1){
            btnCamera1.setImageResource(R.drawable.camera1_on);
            btnCamera2.setImageResource(R.drawable.camera2_off);
            btnCamera3.setImageResource(R.drawable.camera3_off);
            currentCamera = 1;
            portVideo = 5551;
            portAudio = 5552;
            portConnection = 5553;
        } else if (view.getId() == R.id.btnCamera2){
            btnCamera1.setImageResource(R.drawable.camera1_off);
            btnCamera2.setImageResource(R.drawable.camera2_on);
            btnCamera3.setImageResource(R.drawable.camera3_off);
            currentCamera = 2;
            portVideo = 5561;
            portAudio = 5562;
            portConnection = 5563;
        } else {
            btnCamera1.setImageResource(R.drawable.camera1_off);
            btnCamera2.setImageResource(R.drawable.camera2_off);
            btnCamera3.setImageResource(R.drawable.camera3_on);
            currentCamera = 3;
            portVideo = 5571;
            portAudio = 5572;
            portConnection = 5573;
        }
        connectionServer = new ConnectionServer(portConnection, handler, mHandler, currentFps, currentResolution);
        connectionServer.start();
        videoServer = new VideoAudioServer(portVideo, videoQueue, mHandler);
        videoServer.start();
        audioServer = new VideoAudioServer(portAudio, audioQueue, mHandler);
        audioServer.start();

        SharedPreferences sPref = getSharedPreferences("camera_settings", MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putInt(CAMERA_NUMBER, currentCamera);
        ed.apply();
        ed.commit();
    }

    public void open_settings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }
    public class CameraService {
        final private String mCameraID;
        private CameraDevice mCameraDevice = null;
        private CameraCaptureSession mSession;
        private CaptureRequest.Builder mPreviewBuilder;

        public CameraService(CameraManager cameraManager, String cameraID) {
            mCameraManager = cameraManager;
            mCameraID = cameraID;
        }


        private final CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                mCameraDevice = camera;

                //Log.i(LOG_TAG, "Open camera  with id:" + mCameraDevice.getId());
                startCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                mCameraDevice.close();
                //Log.i(LOG_TAG, "disconnect camera  with id:" + mCameraDevice.getId());
                mCameraDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                Log.e(LOG_TAG, "error! camera id:" + camera.getId() + " error:" + error);
            }
        };

        private void startCameraPreviewSession() {
            previewSurface = mImageView.getHolder().getSurface();


            //SurfaceTexture texture = mImageView.getSurfaceTexture();


            //assert texture != null;
            //texture.setDefaultBufferSize(1920, 1080);

            //previewSurface = new Surface(texture);


            Log.i(LOG_TAG, "startCameraPreviewSession " + mEncoderSurface);
            try {


                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                mPreviewBuilder.addTarget(previewSurface);
                mPreviewBuilder.addTarget(mEncoderSurface);

                Range<Integer> fpsRange = new Range<>(currentFps, currentFps);
                mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                //mPreviewBuilder.set(CaptureRequest.CNTROL_AE_, fpsRange);
                //mEncoderSurface.setOnFrameAvailableListener
                mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mEncoderSurface),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                mSession = session;
                                try {
                                    mSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    //e.printStackTrace();
                                    Log.e(LOG_TAG, "error mSession.setRepeatingRequest " + e.getMessage());
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                            }
                        }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                //e.printStackTrace();
                Log.e(LOG_TAG, "error createCaptureRequest " + e.getMessage());
            }
        }

        public boolean isOpen() {
            if (mCameraDevice == null) {
                return false;
            } else {
                return true;
            }
        }

        public void openCamera() {
            try {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    mCameraManager.openCamera(mCameraID, mCameraCallback, mBackgroundHandler);
                }

            } catch (CameraAccessException e) {
                Log.e(LOG_TAG, e.getMessage());
            }
        }

        public void closeCamera() {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        public void stopStreamingVideo() {
            if (mCameraDevice != null & mCodec != null) {
                try {
                    mSession.stopRepeating();
                    mSession.abortCaptures();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                mCodec.stop();
                mCodec.release();
                mEncoderSurface.release();
                closeCamera();
            }
        }
    }
    private class EncoderCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
        }
        @SuppressLint("NewApi")
        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            //Log.i(LOG_TAG, "onOutputBufferAvailable");
            try {
                outPutByteBuffer = mCodec.getOutputBuffer(index);
                //socketServerThread.writeBuffer(outPutByteBuffer);

            } catch (Exception e) {
                Log.e(LOG_TAG, " error codec" + e.getMessage());
            }
            //Log.i("fff", "outPutByteBuffer = " + outPutByteBuffer);
            if (outPutByteBuffer != null) {
                if (videoServer != null) {
                    /// /////////////////////////////////////////////////////////////////////
                    /// Заголовок: "packet0", где ноль - флаг ключевого кадра(если 1),    ///
                    /// 8 байтов - PTS, 4 байта - длина пакета, итого 7 + 8 + 4 = 19 байт ///
                    /// /////////////////////////////////////////////////////////////////////

                    //Log.i("fff", "header");

                    byte[] outDate = new byte[info.size + 19];

                    int pts;
                    if(info.presentationTimeUs == 0){
                        pts = 0;
                    }else{
                        long temp = info.presentationTimeUs;
                        if(temp - ptsVideo > 100000){
                            pts = 30000;
                        }else {
                            pts = Math.toIntExact(temp - ptsVideo);
                        }
                        ptsVideo = temp;
                    }
                    sumV += pts;
                    //countV
                    //Log.i("fff", "timeVideo = " + " " + info.presentationTimeUs + " sumV = " + sumV + " countV = " + ++countV + "flags = " + info.flags + " size = " + info.size);

                    String header = "packet" + info.flags;

                    ByteBuffer bHeader = StandardCharsets.UTF_8.encode(header);
                    ByteBuffer bTimestamp = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(info.presentationTimeUs);
                    bTimestamp.position(0);
                    ByteBuffer bSize = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(info.size);
                    bSize.position(0);
                    bHeader.get(outDate, 0, 7);
                    bTimestamp.get(outDate, 7, 8);
                    bSize.get(outDate, 15, 4);

                    outPutByteBuffer.get(outDate, 19, info.size);

                    //Log.i(LOG_TAG2, String.valueOf(info.size));
                        try {
                            videoQueue.put(outDate);
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "error put queue video" + e);
                        }
                }
            }
            mCodec.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(@NonNull MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(LOG_TAG, "Error: " + e);
        }
        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.i(LOG_TAG, "encoder output format changed: " + format);
        }
    }

    @Override
    public void onDestroy() {
        if(isLandscape)
            tv.append("onDestroy\n");
        super.onDestroy();
    }

    @Override
    public void onPause() {

        if (isLandscape) {
//            Log.i(LOG_TAG, "pause camera " + mEncoderSurface.isValid());
            tv.append("onPause\n");// + previewSurface + " " + mEncoderSurface + "\n");
            //connectionServer.writePause();
            //myCameras[CAMERA1].closeCamera();



//            mCodec.stop();
//            if (myCameras[CAMERA1].isOpen()) {
//                myCameras[CAMERA1].closeCamera();
//                Log.i(LOG_TAG, "pause camera");
//                stopBackgroundThread();
//            }
//            mEncoderSurface.release();
//            mCodec.stop();
//            mCodec.release();

            //Log.i(LOG_TAG, "pause camera " + mEncoderSurface.isValid());
//            if(connectionServer != null){
//                tv.append("connectionServer != null\n");
//                connectionServer.stopProcess();
//                connectionServer = null;
//            }
//            else{
//                tv.append("connectionServer == null\n");
//            }
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        //Log.e(LOG_TAG, "onResume\n");
        if (isLandscape) {
            //Log.i(LOG_TAG, "resume camera " + mEncoderSurface.isValid());
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
//               setUpMediaCodec();
            //Log.i(LOG_TAG, "resume camera " + mEncoderSurface.isValid());
//            }
            //mCodec.start();
            //}
//            if (myCameras[CAMERA1] != null) {// открываем камеру
//                if (!myCameras[CAMERA1].isOpen()) myCameras[CAMERA1].openCamera();
//
//            startBackgroundThread();
//            }
            //if(mBackgroundThread.isAlive()) {

            //}
//            if(connectionServer == null){
//                connectionServer = new ConnectionServer(portConnection, handler, mHandler, currentFps, currentResolution);
//                connectionServer.start();
//                tv.append("connectionServer.start()\n");
//            }
//            else{
//                connectionServer.start();
//            }
//            Log.e(LOG_TAG, "startBackgroundThread");
            //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                //setUpMediaCodec();
//            //}
//            myCameras[CAMERA1].openCamera();
            tv.append("onResume\n");// + previewSurface + " " + mEncoderSurface + "\n");
        }


    }
    @RequiresApi(api = Build.VERSION_CODES.BAKLAVA)
    private void setUpMediaCodec() {
        try {
            mCodec = MediaCodec.createEncoderByType("video/avc"); // H264 кодек
        } catch (Exception e) {
            Log.e(LOG_TAG, "а нету кодека");
        }
        MediaCodecInfo codecInfo = mCodec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        int[] supportedFormats = capabilities.colorFormats;
        for (int each:
                supportedFormats) {

            Log.i("fff", "supportedFormats =  " + each);

        }

        MediaCodecInfo.VideoCapabilities vCap = capabilities.getVideoCapabilities();
        assert vCap != null;
        Log.i(LOG_TAG, "1920 " + vCap.areSizeAndRateSupported(1920,1080,30));
        Log.i(LOG_TAG, "3840 " + vCap.areSizeAndRateSupported(3840,2160,30));
        Log.i(LOG_TAG, "setup mediacodec " + currentResolution + currentFps);
        int width = currentResolution.getWidth(); // ширина видео
        int height = currentResolution.getHeight(); // высота видео
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface; // формат ввода цвета
        int videoBitrate = width * height * currentFps / 10;//6000000; // битрейт видео в bps (бит в секунду)
        int videoFramePerSecond = currentFps; // FPS
        int iframeInterval = 1; // I-Frame интервал в секундах

        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFramePerSecond);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval);
        //format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain);
        //format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);

        mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); // конфигурируем кодек как кодер

        mEncoderSurface = mCodec.createInputSurface(); // получаем Surface кодера


        mCodec.setCallback(new EncoderCallback());
        mCodec.start(); // запускаем кодер
        //Log.i(LOG_TAG, "запустили кодек " + colorFormat);
    }

    private class SoundThread extends Thread{
        AudioRecord audioRecord;
        int BUFFER_SIZE;
        int myBufferSize;
        boolean rec;
        MediaCodec audioEncoder;
        SoundThread() {
            BUFFER_SIZE = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            myBufferSize = BUFFER_SIZE * 2;
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE * 2);
            audioRecord.startRecording();
        }
        public void stopRecord(){
            this.rec = false;
        }

        public void pauseRecord(){
            this.audioRecord.stop();
            //Log.i(LOG_TAG, "pause sound");
        }
        public void resumeRecord(){
            this.audioRecord.startRecording();
            //Log.i(LOG_TAG, "resume sound");
        }
        @Override
        public void run() {
            rec = true;
            //Log.i(LOG_TAG, "create sound");

            byte[] buffer = new byte[myBufferSize];

            try {//
                audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, myBufferSize);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();

            while (rec) {
                int out = audioRecord.read(buffer, 0, myBufferSize, READ_BLOCKING);
                if(out > 0) {
                    /// ///////////////////////////////////////////////////////////////////////////

                    int inputIndex = audioEncoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inBuf = inputBuffers[inputIndex];

                        inBuf.clear();
                        inBuf.put(buffer, 0, out);
                        long presentationTimeUs = System.nanoTime() / 1000;
                        //Log.i("fff", "timeAudio presentationTimeUs = " + " " + presentationTimeUs);
                        audioEncoder.queueInputBuffer(inputIndex, 0, out, presentationTimeUs, 0);
                    }
                    // Drain encoder output
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outputIndex;
                    do {
                        outputIndex = audioEncoder.dequeueOutputBuffer(info, 0);
                        if (outputIndex >= 0) {
                            ByteBuffer outBuf = outputBuffers[outputIndex];
                            byte[] aacData = new byte[info.size + 16];
                            outBuf.position(info.offset);
                            outBuf.limit(info.offset + info.size);
                            //audioEncoder.releaseOutputBuffer(outputIndex, info.presentationTimeUs);//

                            int pts;
                            long temp = info.presentationTimeUs;
                            if(temp == 0){
                                pts = 0;
                            }else{
                                if(temp - ptsAudio > 100000){
                                    pts = 23220;
                                }else {
                                    pts = Math.toIntExact(temp - ptsAudio);
                                }
                            }
                            ptsAudio = temp;
                            sumA += pts;

                            ByteBuffer bHeader = StandardCharsets.UTF_8.encode("size");
//                            ByteBuffer bDuration = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(pts);
//                            bDuration.position(0);
                            ByteBuffer bTimestamp = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(info.presentationTimeUs);
                            //Log.i("fff", "bTimestamp = " + " " + bTimestamp.array()[0] + " " + bTimestamp.array()[1] + " " + bTimestamp.array()[2] + " " + bTimestamp.array()[3]
                            //        + " " + bTimestamp.array()[4] + " " + bTimestamp.array()[5] + " " + bTimestamp.array()[6] + " " + bTimestamp.array()[7]);
                            bTimestamp.position(0);

                            ByteBuffer bSize = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(info.size);
                            bSize.position(0);

                            bHeader.get(aacData, 0, 4);
                            //bDuration.get(aacData, 4, 4);
                            bTimestamp.get(aacData, 4, 8);
                            bSize.get(aacData, 12, 4);

                            outBuf.get(aacData, 16, info.size);

                            //Log.i("fff", "timeAudio = " + " " + info.presentationTimeUs + " sumA = " + sumA + " countA = " + ++countA);

                            try {
                                audioQueue.put(aacData);
                            } catch (InterruptedException e) {
                                Log.e(LOG_TAG, "error pur audio queue" + e);
                            }

                            audioEncoder.releaseOutputBuffer(outputIndex, info.presentationTimeUs);

                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // handle format change if needed
                        }
                    } while (outputIndex >= 0);
                    /// ///////////////////////////////////////////////////////////////////////////
                }else if(out == ERROR_INVALID_OPERATION){
                    Log.e(LOG_TAG, " ERROR_INVALID_OPERATION ");
                }else if(out == ERROR_BAD_VALUE){
                    Log.e(LOG_TAG, " ERROR_BAD_VALUE ");
                }else if(out == ERROR_DEAD_OBJECT){
                    Log.e(LOG_TAG, " ERROR_DEAD_OBJECT ");
                }else if(out == ERROR){
                    Log.e(LOG_TAG, " ERROR ");
                }
            }
            audioRecord.stop();
            audioRecord.release();
            //Log.i(LOG_TAG, "exit sound");
        }
    }
}