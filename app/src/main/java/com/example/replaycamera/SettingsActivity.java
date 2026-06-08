package com.example.replaycamera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    public static final String LOG_TAG = "myLogs";
    Spinner spResolution;
    Spinner spFps;
    SharedPreferences sPref;
    final String CURRENT_FPS = "currentFps";
    final String CURRENT_WIDTH = "currentWidth";
    final String CURRENT_HEIGHT = "currentHeight";

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        spResolution = findViewById(R.id.spResolution);
        spFps = findViewById(R.id.spFps);


        sPref = getSharedPreferences("camera_settings", MODE_PRIVATE);
        int currentFps = sPref.getInt(CURRENT_FPS, 0);
        Size currentResolution = new Size(sPref.getInt(CURRENT_WIDTH, 0), sPref.getInt(CURRENT_HEIGHT, 0));

        CameraManager mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        MediaCodec encoder = null;
        try {
            encoder = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CameraCharacteristics characteristics = null;
        try {
            assert mCameraManager != null;
            characteristics = mCameraManager.getCameraCharacteristics("0");
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

        Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        ArrayList<Integer> listFps = new ArrayList<Integer>();
        assert fpsRanges != null;
        for(Range<Integer> fps : fpsRanges){
            if(fps.getLower().equals(fps.getUpper())){
                listFps.add(fps.getLower());
            }
        }
        listFps.sort(Collections.reverseOrder());
        ArrayAdapter<Integer> fps = new ArrayAdapter<Integer>(this, android.R.layout.simple_spinner_item, listFps);
        fps.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFps.setAdapter(fps);
        int pos = fps.getPosition(currentFps);
        spFps.setSelection(pos);

        StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizesJPEG;
        if (configurationMap != null) {
            sizesJPEG = configurationMap.getOutputSizes(ImageFormat.JPEG);
            ArrayList<Size> supportedSizes = new ArrayList<Size>();
            for (Size s : sizesJPEG) {
                if (Objects.requireNonNull(encoder.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities()).isSizeSupported(s.getWidth(), s.getHeight())) {
                    supportedSizes.add(s);
                }
            }
            Comparator<Size> byWidth = new Comparator<Size>() {
                @Override
                public int compare(Size s1, Size s2) {
                    return s2.getWidth() - s1.getWidth();
                }
            };
            supportedSizes.sort(byWidth);
            ArrayList<String> listResolution = new ArrayList<String>();
            for(Size s : supportedSizes){
                listResolution.add(s.toString());
            }
            ArrayAdapter<String> resolution = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listResolution);
            resolution.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spResolution.setAdapter(resolution);
            pos = resolution.getPosition(currentResolution.toString());
            spResolution.setSelection(pos);

            spFps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Integer FPS = fps.getItem(position);
                    if(FPS != null) {
                        SharedPreferences.Editor ed = sPref.edit();
                        ed.putInt(CURRENT_FPS, FPS);
                        ed.apply();
                        ed.commit();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
            spResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Size res = Size.parseSize(resolution.getItem(position));
                    Log.i(LOG_TAG, String.valueOf(res) + position);
                    SharedPreferences.Editor ed = sPref.edit();
                    ed.putInt(CURRENT_WIDTH, res.getWidth());
                    ed.putInt(CURRENT_HEIGHT, res.getHeight());
                    ed.apply();
                    ed.commit();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });
        }
    }
}