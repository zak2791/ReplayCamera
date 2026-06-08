package com.example.replaycamera;


import static android.media.AudioRecord.ERROR;
import static android.media.AudioRecord.ERROR_BAD_VALUE;
import static android.media.AudioRecord.ERROR_DEAD_OBJECT;
import static android.media.AudioRecord.ERROR_INVALID_OPERATION;
import static android.media.AudioRecord.READ_BLOCKING;

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
import android.app.ActivityManager;
import android.app.Application;
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

import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.os.HandlerThread;

import android.os.Looper;
import android.os.StrictMode;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.widget.ImageButton;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.io.IOException;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.io.PrintWriter;

import java.lang.reflect.Array;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "myLogs";
    public static final String LOG_TAG2 = "myLogs2";

    //SharedPreferences sPref;
    long prevTimeEncode;

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

    Socket socket = null;
    Socket socketAudio = null;
    //Socket socketSound = null;
    OutputStream output;
    OutputStream outputAudio;
    String ip_address = "127.0.0.1";
    InetAddress address;
    int portVideo;
    int portAudio;
    int portConnection;
    int portCommonConnection = 6666;
    boolean enabledCameraButtons;
    int currentCamera = 0;
    boolean icConnection = false;
    DatagramSocket udpSocket = null;
    InetAddress local;

    TcpClient mTcpClient;
    SoundThread soundThread = null;

    AudioServer audioServer = null;
    final String CAMERA_NUMBER = "cameraNumber";
    final String CURRENT_FPS = "currentFps";
    final String CURRENT_WIDTH = "currentWidth";
    final String CURRENT_HEIGHT = "currentHeight";
    Handler handler;
    SurfaceTexture st;
    Surface s;

    long prevTime = 0;
    long currTime = 0;

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
            e.printStackTrace();
        }
    }

    private long getTime(){
        if(prevTime != 0){
            long temp = prevTime;
            prevTime = System.nanoTime();
            currTime += prevTime - temp;
            return currTime;
        }else{
            prevTime = System.nanoTime();
            return 0;
        }
    }

    boolean isLandscape = false;

    @SuppressLint("NewApi")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "  onCreate");

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        EdgeToEdge.enable(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id. main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        st = new SurfaceTexture(true);

        s = new Surface(st);
        st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener(){
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Log.i(LOG_TAG, "onFrameAvailable");
            }
        });

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(LOG_TAG, "Запрашиваем разрешение");
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ||
                    (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            ) {
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }

            isLandscape = true;

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            Log.i(LOG_TAG, "  ORIENTATION_LANDSCAPE");
            mImageView = findViewById(R.id.textureView);


            SharedPreferences sPref = getSharedPreferences("camera_settings", MODE_PRIVATE);
            currentCamera = sPref.getInt(CAMERA_NUMBER, 1);
            currentFps = sPref.getInt(CURRENT_FPS, 0);
            currentResolution = new Size(sPref.getInt(CURRENT_WIDTH, 0), sPref.getInt(CURRENT_HEIGHT, 0));

            Log.i(LOG_TAG, String.valueOf(currentResolution) + " create " + currentFps);

            if(commonServer != null){
                commonServer.stopProcess();
            }

            commonServer = new ConnectionCommonServer(portCommonConnection);
            commonServer.start();

            if(connectionServer != null){
                connectionServer.stopProcess();
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
            connectionServer = new ConnectionServer(portConnection);
            connectionServer.start();

            btnCamera1 = findViewById(R.id.btnCamera1);
            //btnCamera1.setOnClickListener(onClickCameras);
            //btnCamera1.setBackgroundResource(R.drawable.camera1_on);

            btnCamera2 = findViewById(R.id.btnCamera2);
            //btnCamera2.setImageResource(R.drawable.camera2_off);

            btnCamera3 = findViewById(R.id.btnCamera3);
            //btnCamera3.setImageResource(R.drawable.camera3_off);

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

            btnPlay = findViewById(R.id.btnPlay);
            btnPlay.setBackgroundResource(R.drawable.stream_off);

            btnPlay.setOnClickListener(new View.OnClickListener() {
                @SuppressLint("NewApi")
                @Override
                public void onClick(View v) {
                    if (stateButtonPlay == STATE_PLAY) {
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
                        //closeConnection();
                        soundThread.stopRecord();
                        soundThread = null;
//                        if(audioServer != null) {
//                            audioServer.stopProcess() ;
//                            audioServer = null;
//                        }
                        if(socket != null){
                            try {
                                socket.close();
                                socket = null;
                            } catch (IOException e) {
                                Log.e(LOG_TAG, "error close socket = " + e.getMessage());
                            }
                        }
                        if(socketAudio != null){
                            try {
                                socketAudio.close();
                                socketAudio = null;
                            } catch (IOException e) {
                                Log.e(LOG_TAG, "error close socketAudio = " + e.getMessage());
                            }
                        }
                    } else {
                        try {
                            socket = new Socket(InetAddress.getByName("127.0.0.1"), portVideo);
                            //socketAudio = new Socket(InetAddress.getByName("127.0.0.1"), 5556);
                            try {
                                output = socket.getOutputStream();
                                Log.i(LOG_TAG, "  есть output socket" + output);
                            } catch (IOException ee) {
                                Log.i(LOG_TAG, "  ошибка создания output socket " + ee.getMessage());
                                return;
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "ошибка создания socket " + e.getMessage());
                            return;
                        }
                        try {
                            socketAudio = new Socket(InetAddress.getByName("127.0.0.1"), portAudio);
                            if(socketAudio.isConnected()) {
                                try {
                                    outputAudio = socketAudio.getOutputStream();
                                    Log.i(LOG_TAG, "  есть output socketAudio" + output);
                                } catch (IOException ee) {
                                    Log.i(LOG_TAG, "  ошибка создания output socketAudio " + ee.getMessage());
                                    return;
                                }
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "ошибка создания socketAudio " + e.getMessage());
                            return;
                        }



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


//                        if(audioServer == null) {
//                            audioServer = new AudioServer(portAudio);
//                            audioServer.start();
//                        }
                        if(soundThread == null) {
                            soundThread = new SoundThread();
                            soundThread.start();
                        }
                        currentCamera = sPref.getInt(CAMERA_NUMBER, 1);
                        currentFps = sPref.getInt(CURRENT_FPS, 0);
                        currentResolution = new Size(sPref.getInt(CURRENT_WIDTH, 0), sPref.getInt(CURRENT_HEIGHT, 0));
                        Log.i(LOG_TAG, String.valueOf(currentResolution) + " click " + currentFps);
                        mCodec.stop();
                        myCameras[CAMERA1].closeCamera();
                        setUpMediaCodec();
                        myCameras[CAMERA1].openCamera();
                        if (socket != null) {
                            icConnection = true;
                        }
                    }
                }
            });
//            if(soundThread == null) {
//                soundThread = new SoundThread();
//                soundThread.start();
//            }
//            AudioEncoder audioEncoder = new AudioEncoder();
//            try {
//                audioEncoder.startRecording(outputAudio);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }

            MediaCodec encoder;



            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                // Получение списка камер с устройства
                myCameras = new CameraService[mCameraManager.getCameraIdList().length];

                if(currentFps == 0) {
                    encoder = MediaCodec.createEncoderByType("video/avc");


                    //for (String cameraID : mCameraManager.getCameraIdList()) {
                    //Log.i(LOG_TAG, "cameraID: " + cameraID);
                    //int id = Integer.parseInt(cameraID);
                    // создаем обработчик для камеры
                    CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics("0");

                    Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    ArrayList<Integer> listFps = new ArrayList<Integer>();
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
                    Log.i(LOG_TAG, String.valueOf(listFps));
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
                                Log.i(LOG_TAG, String.valueOf(currentResolution) + " init " + currentFps);
                                ed.putInt(CURRENT_WIDTH, currentResolution.getWidth());
                                ed.putInt(CURRENT_HEIGHT, currentResolution.getHeight());
                                ed.apply();
                                ed.commit();
                                break;
                            }
                        }
                    }


                    Log.i(LOG_TAG, "currentResolution = " + currentResolution);


                    //}
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
        }

        handler = new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull android.os.Message msg) {
                if(msg.what == 1) {
                    if(stateButtonPlay == STATE_PLAY) {
                        btnPlay.callOnClick();
                    }
                }else if(msg.what == 2){
                    if(stateButtonPlay == STATE_PAUSE) {
                        btnPlay.callOnClick();
                    }
                }
            }
        };
    }

    public void select_camera(View view) {
        if(stateButtonPlay)
            return;
        if(connectionServer != null)
            connectionServer.stopProcess();
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
        connectionServer = new ConnectionServer(portConnection);
        connectionServer.start();
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


        private CameraDevice.StateCallback mCameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCameraDevice = camera;

                Log.i(LOG_TAG, "Open camera  with id:" + mCameraDevice.getId());
                startCameraPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                mCameraDevice.close();
                Log.i(LOG_TAG, "disconnect camera  with id:" + mCameraDevice.getId());
                mCameraDevice = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                Log.i(LOG_TAG, "error! camera id:" + camera.getId() + " error:" + error);
            }
        };

        private void startCameraPreviewSession() {
            previewSurface = mImageView.getHolder().getSurface();
            Log.i(LOG_TAG, "startCameraPreviewSession");
            try {


                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                mPreviewBuilder.addTarget(previewSurface);
                mPreviewBuilder.addTarget(mEncoderSurface);
                //mPreviewBuilder.addTarget(s);
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
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {
                            }
                        }, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
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
                Log.i(LOG_TAG, e.getMessage());
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

//    private void closeConnection() {
//        if (socket != null && !socket.isClosed()) {
//            try {
//                socket.close();
//            } catch (IOException e) {
//                Log.e(LOG_TAG, "ошибка закрытия сокета" + e.getMessage());
//            } finally {
//                socket = null;
//            }
//        }
//        if (socketSound != null && !socketSound.isClosed()) {
//            try {
//                socketSound.close();
//            } catch (IOException e) {
//                Log.e(LOG_TAG, "ошибка закрытия socketSound" + e.getMessage());
//            } finally {
//                socketSound = null;
//            }
//        }
//    }

    private class EncoderCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
        }

        @SuppressLint("NewApi")
        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            try {
                outPutByteBuffer = mCodec.getOutputBuffer(index);
                //socketServerThread.writeBuffer(outPutByteBuffer);

            } catch (Exception e) {
                Log.e(LOG_TAG, " error codec" + e.getMessage());
            }
            Log.i("fff", "outPutByteBuffer = " + outPutByteBuffer);
            if (outPutByteBuffer != null) {
                if (socket != null) {
                    /// /////////////////////////////////////////////////////////////////////
                    /// Заголовок: "packet0", где ноль - флаг ключевого кадра(если 1),    ///
                    /// 8 байтов - PTS, 4 байта - длина пакета, итого 7 + 8 + 4 = 19 байт ///
                    /// /////////////////////////////////////////////////////////////////////

                    Log.i("fff", "header");

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

//                        int s = Long.SIZE;
//                        Integer.
                    }
                    sumV += pts;
                    //countV
                    Log.i("fff", "timeVideo = " + " " + info.presentationTimeUs + "sumV = " + sumV + " countV = " + ++countV + "flags = " + info.flags);

                    String header = "packet" + info.flags;

                    ByteBuffer bHeader = StandardCharsets.UTF_8.encode(header);
                    //ByteBuffer bDuration = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(pts);
                    //bDuration.position(0);
                    ByteBuffer bTimestamp = ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(info.presentationTimeUs);
                    bTimestamp.position(0);

                    ByteBuffer bSize = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(info.size);
                    bSize.position(0);

                    bHeader.get(outDate, 0, 7);
                    //bDuration.get(outDate, 4, 4);
                    bTimestamp.get(outDate, 7, 8);
                    bSize.get(outDate, 15, 4);

                    outPutByteBuffer.get(outDate, 19, info.size);

                    Log.i(LOG_TAG2, String.valueOf(info.size));
                    try {
                        output.write(outDate);
                        output.flush();
                    } catch (IOException e) {
                        Log.e(LOG_TAG, " error write wideo " + e.getMessage());
                        //handler.sendEmptyMessage(1);
                    }
                }
            }
            mCodec.releaseOutputBuffer(index, false);
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.i(LOG_TAG, "Error: " + e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.i(LOG_TAG, "encoder output format changed: " + format);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {

        if (isLandscape) {
            if (myCameras[CAMERA1].isOpen()) {
                myCameras[CAMERA1].closeCamera();
                Log.i(LOG_TAG, "pause camera");
                stopBackgroundThread();
            }
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        Log.e(LOG_TAG, "onResume");
        if (isLandscape) {
            //if(mBackgroundThread.isAlive()) {
            startBackgroundThread();
            //}
            Log.e(LOG_TAG, "startBackgroundThread");
        }
        super.onResume();
    }

    @RequiresApi(api = Build.VERSION_CODES.BAKLAVA)
    private void setUpMediaCodec() {
        try {
            mCodec = MediaCodec.createEncoderByType("video/avc"); // H264 кодек
        } catch (Exception e) {
            Log.i(LOG_TAG, "а нету кодека");
        }
        MediaCodecInfo codecInfo = mCodec.getCodecInfo();
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
        int[] supportedFormats = capabilities.colorFormats;
        for (int each:
                supportedFormats) {

            Log.i("fff", "supportedFormats =  " + each);

        }

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
        Log.i(LOG_TAG, "запустили кодек " + colorFormat);
    }


    public static class TcpClient {

        public  final String TAG = TcpClient.class.getSimpleName();
        public static final String SERVER_IP = "127.0.0.1"; //server IP address
        public static final int SERVER_PORT = 5565;
        // message to send to the server
        private String mServerMessage;
        // sends message received notifications
        private OnMessageReceived mMessageListener = null;
        // while this is true, the server will continue running
        private boolean mRun = false;
        // used to send messages
        private PrintWriter mBufferOut;
        // used to read messages from the server
        private BufferedReader mBufferIn;

        /**
         * Constructor of the class. OnMessagedReceived listens for the messages received from server
         */
        public TcpClient(OnMessageReceived listener) {
            mMessageListener = listener;
        }

        /**
         * Sends the message entered by client to the server
         *
         * @param message text entered by client
         */
        public void sendMessage(final String message) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (mBufferOut != null) {
                        Log.d(TAG, "Sending: " + message);
                        mBufferOut.println(message);
                        mBufferOut.flush();
                    }
                }
            };
            Thread thread = new Thread(runnable);
            thread.start();
        }

        public void run() {

            mRun = true;

            try {
                //here you must put your computer's IP address.
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

                Log.d("TCP Client", "C: Connecting...");

                //create a socket to make the connection with the server
                Socket socket = new Socket(serverAddr, SERVER_PORT);

                try {

                    //sends the message to the server
                    mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                    //receives the message which the server sends back
                    mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                    //in this while the client listens for the messages sent by the server
                    while (mRun) {

                        mServerMessage = mBufferIn.readLine();

                        if (mServerMessage != null && mMessageListener != null) {
                            //call the method messageReceived from MyActivity class
                            mMessageListener.messageReceived(mServerMessage);
                        }

                    }

                    Log.d("RESPONSE FROM SERVER", "S: Received Message: '" + mServerMessage + "'");

                } catch (Exception e) {
                    Log.e("TCP", "S: Error", e);
                } finally {
                    //the socket must be closed. It is not possible to reconnect to this socket
                    // after it is closed, which means a new socket instance has to be created.
                    socket.close();
                }

            } catch (Exception e) {
                Log.e("TCP", "C: Error", e);
            }

        }

        public interface OnMessageReceived {
            public void messageReceived(String message);
        }

    }

    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

        @Override
        protected TcpClient doInBackground(String... message) {

            //we create a TCPClient object
            mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            });
            mTcpClient.run();
            return null;
        }
        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            //response received from server
            Log.d("test", "response " + values[0]);
            //process server response here....

        }
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
            Log.i(LOG_TAG, "pause sound");
        }

        public void resumeRecord(){
            this.audioRecord.startRecording();
            Log.i(LOG_TAG, "resume sound");
        }

        @Override
        public void run() {
            rec = true;
            Log.i(LOG_TAG, "create sound");
//            BUFFER_SIZE = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//            myBufferSize = BUFFER_SIZE * 2;
//            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE * 2);
//            audioRecord.startRecording();

            byte[] buffer = new byte[myBufferSize];



            try {//
                audioEncoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            MediaFormat format = MediaFormat.createAudioFormat("audio/mp4a-latm", 44100, 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, myBufferSize);
            audioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();



            ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();



            while (rec) {
                //Thread.sleep(90); //Recording 90ms duration packets
                int out = audioRecord.read(buffer, 0, myBufferSize, READ_BLOCKING);
                if(out > 0) {
                    /// ///////////////////////////////////////////////////////////////////////////

                    int inputIndex = audioEncoder.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer inBuf = inputBuffers[inputIndex];

                        inBuf.clear();
                        inBuf.put(buffer, 0, out);
                        long presentationTimeUs = System.nanoTime() / 1000;
                        Log.i("fff", "timeAudio presentationTimeUs = " + " " + presentationTimeUs);
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
                            Log.i("fff", "bTimestamp = " + " " + bTimestamp.array()[0] + " " + bTimestamp.array()[1] + " " + bTimestamp.array()[2] + " " + bTimestamp.array()[3]
                                    + " " + bTimestamp.array()[4] + " " + bTimestamp.array()[5] + " " + bTimestamp.array()[6] + " " + bTimestamp.array()[7]);
                            bTimestamp.position(0);

                            ByteBuffer bSize = ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(info.size);
                            bSize.position(0);

                            bHeader.get(aacData, 0, 4);
                            //bDuration.get(aacData, 4, 4);
                            bTimestamp.get(aacData, 4, 8);
                            bSize.get(aacData, 12, 4);

                            //outPutByteBuffer.get(aacData, 8, info.size);

                            outBuf.get(aacData, 16, info.size);

                            //long startTime = System.nanoTime(); // выполнение кода
                            //long endTime = System.nanoTime();



//                            ptsAudio = info.presentationTimeUs - ptsAudio;
//                            Log.i("fff", "ptsAudio = " + " " + ptsAudio);
//                            Log.i("fff", "info.presentationTimeU = " + " " + info.presentationTimeUs);

                            Log.i("fff", "timeAudio = " + " " + info.presentationTimeUs + " sumA = " + sumA + " countA = " + ++countA);

                            // Write to file/stream (ADTS header if necessary)
                            if(socketAudio != null) {
                                if (socketAudio.isConnected()) {
                                    try {
                                        Log.i("fff", String.valueOf(aacData.length));
                                        outputAudio.write(aacData);
                                        outputAudio.flush();
//                                output.write(aacData);
//                                output.flush();
                                    } catch (IOException e) {
                                        //throw new RuntimeException(e);
                                        Log.i("fff", "err sound");
                                    }
                                }
                            }
                            audioEncoder.releaseOutputBuffer(outputIndex, info.presentationTimeUs);
                            //Log.i("fff", "pres 2 " + info.presentationTimeUs);
                        } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // handle format change if needed
                        }
                    } while (outputIndex >= 0);
                    /// ///////////////////////////////////////////////////////////////////////////
//                    if(socketAudio != null) {
//                        try {
//                            outputAudio.write(buffer);
//                            outputAudio.flush();
//                            Log.i("outputAudio", "outputAudio " + buffer.length);
//                        } catch (IOException e) {
//                            Log.i("outputAudio", "outputAudio error " + e.getMessage());
//                        }
//                    }
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
            //encoder.close();
            Log.i(LOG_TAG, "exit sound");
        }
    }

    private class AudioServer extends Thread {

        ServerSocket serverSocket;
        private Socket clientSocket; //сокет для общения
        byte[] buf = new byte[7];
        //private boolean isClosed = false;
        private boolean process = true;
        BufferedInputStream in;
        int countNoData = 0;
        BufferedOutputStream out = null;
        boolean isData = false;
        byte[] data = null;
        int port;

        AudioServer(int _port){
            port = _port;
        }
        public void sendData(ByteBuffer buffer){
            //Log.e(LOG_TAG, "sendData();   " );
            data = new byte[buffer.limit()];
            buffer.get(data);
            isData = true;
        }
        public void stopProcess(){

            process = false;
            Log.d(LOG_TAG, "stopProcess 1 ");
            try {
                if(out != null) {
                    out.close();
                    out = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error out.close()   " + e.getMessage());
            }
            Log.d(LOG_TAG, "stopProcess 2 ");
            try {
                if(clientSocket != null) {
                    clientSocket.close();
                    clientSocket = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error clientSocket.close()   " + e.getMessage());
            }
            Log.d(LOG_TAG, "stopProcess 3 ");
            if(serverSocket != null) {
                try {
                    serverSocket.close();
                    Log.e(LOG_TAG, "audio server stop   " );
                } catch (Exception e) {
                    Log.e(LOG_TAG, "error audio serverSocket.close()  " + e.getMessage());
                }
            }
            Log.d(LOG_TAG, "stopProcess 4 ");
        }
        @Override
        public void run() {
            try {
                //Log.i(LOG_TAG, "audio server create ");
                serverSocket = new ServerSocket();
                //serverSocket.setReuseAddress(true);
                //serverSocket.setSoTimeout(10);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
                Log.i(LOG_TAG, "audio server bind ");
            }catch(IOException e) {
                Log.e(LOG_TAG, "audio server error create bind " + e.getMessage());
                return;
            }
//            }catch (SocketTimeoutException e){
//                Log.e(LOG_TAG, "audio SocketTimeoutException " + e.getMessage());
//            }
            while(process) {
                try {
                    //Log.i(LOG_TAG, "start accept videoServer ");
                    clientSocket = serverSocket.accept();
                    //clientSocket.setSoTimeout(10);
                    Log.i(LOG_TAG, "accept audio server ");
                    //in = new BufferedInputStream(clientSocket.getInputStream());
                    out = new BufferedOutputStream(clientSocket.getOutputStream());

                }catch(IOException e) {
                    Log.e(LOG_TAG, "audio server accept error " + e.getMessage());
                }
                //Log.i(LOG_TAG, "SocketServerThread accept ");


                while (process) {
                    if(isData){
                        try {
                            if(out != null) {
                                out.write(data);
                                out.flush();
                            }
                            //Log.i(LOG_TAG, "send audio data " + data.length);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "error send audio data " + e.getMessage());
                            //handler.sendEmptyMessage(1);
                        }finally {
                            data = null;
                            isData = false;
                        }
                    }
                }

            }
            Log.i(LOG_TAG, "audio server exit ");
        }

    }

    private class ConnectionCommonServer extends Thread {
        ServerSocket serverSocket;
        private Socket clientSocket; //сокет для общения
        private boolean process = true;
        BufferedOutputStream out = null;
        int port;
        ConnectionCommonServer(int _port){
            port = _port;
        }
        public void stopProcess(){
            process = false;
            try {
                if(out != null) {
                    out.close();
                    out = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error out.close()   " + e.getMessage() + port);
            }
            try {
                if(clientSocket != null) {
                    clientSocket.close();
                    clientSocket = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error clientSocket.close()   " + e.getMessage() + port);
            }
            if(serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "error audio serverSocket.close()  " + e.getMessage() + port);
                }
            }
        }
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
            }catch(IOException e) {
                Log.e(LOG_TAG, "connection server error bind " + e.getMessage() + port);
                return;
            }
            while(process) {
                try {
                    clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout(2000);
                    out = new BufferedOutputStream(clientSocket.getOutputStream());
                }catch(IOException e) {
                    Log.e(LOG_TAG, "audio server accept error " + e.getMessage() + port);
                }
                try {
                    if(out != null) {
                        String data = String.valueOf(currentCamera) + "=" +
                                String.valueOf(currentResolution.getWidth()) + "=" +
                                String.valueOf(currentResolution.getHeight()) + "=" +
                                String.valueOf(currentFps);
                        out.write(data.getBytes());
                        out.flush();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "error send conn data " + e.getMessage() + port);
                    try {
                        clientSocket.close();
                        break;
                    } catch (IOException ex) {
                        Log.e(LOG_TAG, "error close conn client " + e.getMessage() + port);
                        break;
                    }
                }
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
                }
            }
        }
    }

    private class ConnectionServer extends Thread {
        ServerSocket serverSocket;
        private Socket clientSocket; //сокет для общения
        private boolean process = true;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        boolean isData = false;
        byte[] data = null;
        int port;
        ConnectionServer(int _port){
            port = _port;
        }
        public void stopProcess(){
            process = false;
            Log.d(LOG_TAG, "stopProcess 1 " + port);
            try {
                if(out != null) {
                    out.close();
                    out = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error out.close()   " + e.getMessage() + port);
            }
            Log.d(LOG_TAG, "stopProcess 2 " + port);
            try {
                if(clientSocket != null) {
                    clientSocket.close();
                    clientSocket = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error clientSocket.close()   " + e.getMessage() + port);
            }
            Log.d(LOG_TAG, "stopProcess 3 " + port);
            if(serverSocket != null) {
                try {
                    serverSocket.close();
                    Log.e(LOG_TAG, "audio server stop   "  + port);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "error audio serverSocket.close()  " + e.getMessage() + port);
                }
            }
            Log.d(LOG_TAG, "stopProcess 4 " + port);
        }
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket();
                serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 10);
                Log.i(LOG_TAG, "connection server bind " + port);

            }catch(IOException e) {
                Log.e(LOG_TAG, "connection server error bind " + e.getMessage() + port);
                return;
            }
            while(process) {
                try {
                    //Log.i(LOG_TAG, "start accept videoServer ");
                    clientSocket = serverSocket.accept();
                    //clientSocket.setSoTimeout(5000);
                    Log.i(LOG_TAG, "accept connect server " + port);
                    in = new BufferedInputStream(clientSocket.getInputStream());
                    out = new BufferedOutputStream(clientSocket.getOutputStream());
                    //handler.sendEmptyMessage(2);    //включаем енкодер
                }catch(IOException e) {
                    Log.e(LOG_TAG, "connection server accept error " + e.getMessage() + port);
                }
                while(process) {
                    Log.i(LOG_TAG, "process");
                    byte[] b = new byte[10];
                    int ret;
                    try {
//                        int av = in.available();
//                        Log.i(LOG_TAG, "av = " + av);
//                        if(av > 0)
                        //while(in.available() < 1);
                        Log.i(LOG_TAG, "in.available() = " + in.available());
                        ret = in.read(b, 0, 10);

                        Log.i(LOG_TAG, "ret = " + ret + Arrays.toString(b));
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "read connection error " + e.getMessage());
                        break;
                    }
//                    String sB = Arrays.toString(b);
                    Log.i(LOG_TAG, "ret = " + b.toString());
//                    if(ret != 10)
//                        break;
                    if(Arrays.equals(b, "parameters".getBytes())){
                        Log.i(LOG_TAG, "Arrays.toString(b).equals(connect)");
                        try {
                            if(out != null) {
                                String data = String.valueOf(currentResolution.getWidth()) + "=" +
                                        String.valueOf(currentResolution.getHeight()) + "=" +
                                        String.valueOf(currentFps);
                                out.write(data.getBytes());
                                out.flush();
                                handler.sendEmptyMessage(2);    //включаем енкодер
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "error send conn data " + e.getMessage());
                            break;
                        }
                    }
                    else{
                        try {
                            if(out != null) {
                                out.write("ok".getBytes());
                                out.flush();
                            }
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "error send conn data " + e.getMessage());
                            break;
                        }
                    }
                }
                handler.sendEmptyMessage(1);    //выключаем енкодер
                try {
                    if (in != null) {
                        in.close();
                    }
                    if (out != null) {
                        out.close();
                    }
                    clientSocket.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
                }
                Log.i(LOG_TAG, "connection read error " + port);
            }
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
                clientSocket.close();
                serverSocket.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "error clientSocket.close() " + e.getMessage() + port);
            }
            Log.i(LOG_TAG, "connection server exit " + port);
        }

    }



}
