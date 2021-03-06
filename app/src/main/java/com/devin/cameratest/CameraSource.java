package com.devin.cameratest;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static java.lang.Math.min;

/**
 * Created by li on 2016/9/29.
 */

public class CameraSource implements Camera.PreviewCallback {
    private final String TAG = this.getClass().toString() + "-DY";
    private Camera camera;
    private int preWidth = -1;
    private int preHeight = -1;
    private boolean isPreviewing;
    private String mFocusMode;
    private SurfaceTexture surfaceTexture;
    private NewFrameListener frameListener;
    public void setNewFrameListener(NewFrameListener listener){
        frameListener = listener;
    }

    private static CameraSource _instance = new CameraSource();

    public static CameraSource Instance(){
        return _instance;
    }

    CameraSource() {
        preWidth = 1280;
        preHeight = 720;
//        preWidth = 640;
//        preHeight = 480;
        isPreviewing = false;
        mFocusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
    }


    private int getFrontCameraIndex() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras(); // get cameras number
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                return camIdx;
            }
        }
        return 0;
    }

    private int getBackCameraIndex() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras(); // get cameras number
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                return camIdx;
            }
        }
        return 0;
    }
    public final static int  CAMERA_DIRECTION_DEFAULT = 0;
    public final static int  CAMERA_DIRECTION_BACK = 1;
    public final static int  CAMERA_DIRECTION_FRONT = 2;
    public final static int  CAMERA_DIRECTION_G200_FRONT = 3;

    public int openCamera(int direction) {
        if (camera != null) {
            camera.release();
            isPreviewing=false;
            camera = null;
        }
        try {
            switch (direction) {
                case CAMERA_DIRECTION_DEFAULT:
                    camera = Camera.open();
                    break;
                case CAMERA_DIRECTION_BACK:
                    camera = Camera.open(getBackCameraIndex());
//                    camera = Camera.open(1);
                    break;
                case CAMERA_DIRECTION_FRONT:
                    camera = Camera.open(getFrontCameraIndex());
                    break;
                case CAMERA_DIRECTION_G200_FRONT:
                    camera = Camera.open(1);
                    break;
                default:
                    camera = Camera.open();
                    break;
            }
            List<Size> sizes = camera.getParameters().getSupportedPreviewSizes();
        } catch (Exception e) {
            camera=null;
            return -1;
        }
        return 0;
    }

    public List<Size> getSupportsSizes(){
        if(camera == null) return null;
        return camera.getParameters().getSupportedPreviewSizes();
    }

    public int getPreviewWidth(){
        return preWidth;
    }

    public int getPreviewHeight(){
        return preHeight;
    }

    public int setPreviewSize(int width, int height) {
        preWidth = width;
        preHeight = height;
        return 0;
    }

    public boolean isPreviewing() {
        return isPreviewing;
    }

    public void setPreviewing(boolean isPreviewing){
        this.isPreviewing=isPreviewing;
    }

    public int startPreview() {
        if (camera == null) return -1;
        if (isPreviewing) return 0;
        try {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(preWidth, preHeight);
            parameters.setPreviewFormat(ImageFormat.NV21);
//            List<int[]> tmp = parameters.getSupportedPreviewFpsRange();
//            for (int i = 0; i < tmp.size(); i++) {
//                Log.d(TAG, "getSupportedPreviewFpsRange: " + tmp.get(i)[0] + tmp.get(i)[1]);
//            }
            parameters.setPreviewFpsRange(30000, 30000);
//            parameters.setPreviewFpsRange(15000, 15000);

            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(mFocusMode)) {
                parameters.setFocusMode(mFocusMode);
            }
            camera.setParameters(parameters);
            surfaceTexture = new SurfaceTexture(-1);
            camera.setPreviewTexture(surfaceTexture);
            Camera.Parameters para = camera.getParameters();
            int pixelformat = para.getPreviewFormat();
            PixelFormat pixelinfo = new PixelFormat();
            PixelFormat.getPixelFormatInfo(pixelformat, pixelinfo);
            int bufSize = preWidth * preHeight * pixelinfo.bitsPerPixel / 8;
            for (int i = 0; i < 5; i++) {
                camera.addCallbackBuffer(new byte[bufSize]);
            }
            camera.setPreviewCallbackWithBuffer(this);
            System.gc();
            camera.startPreview();
            isPreviewing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int stopPreview() {
        if (camera != null) {
            try {
                camera.stopPreview();
                //这句要在stopPreview后执行，不然会卡顿或者花屏
                camera.setPreviewDisplay(null);
                isPreviewing = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public int closeCamera() {

        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }

        if (camera != null) {
            camera.cancelAutoFocus();
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
                isPreviewing = false;
                camera.release();
                camera = null;

        }


        return 0;
    }

    public boolean recording = false;
    public boolean recordingFull = true;
    public boolean recordingRoi = false;
    public boolean folderNeedChange = false;
    public String renewDir = "";
    private String[] dir = new String[2];
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (isPreviewing && data != null) {
            if(frameListener != null){
                frameListener.onNewFrame(data, preWidth, preHeight);

                if (recording){
                    File sdRoot = Environment.getExternalStorageDirectory();
                    Date now = new Date();

                    if (folderNeedChange) {
                        dir[0] = "/cameraData/Full/" + renewDir;
                        File mkDir = new File(sdRoot, dir[0]);
                        if (!mkDir.exists()) {
                            mkDir.mkdirs();
                            Log.d(TAG, "Make folder: " + dir[0]);
                        } else {
//                          Log.d(TAG, "" + "Dir existed");
                        }

                        dir[1] = "/cameraData/Roi/" + renewDir;
                        mkDir = new File(sdRoot, dir[1]);
                        if (!mkDir.exists()) {
                            mkDir.mkdirs();
                            Log.d(TAG, "Make folder: " + dir[1]);
                        } else {
//                          Log.d(TAG, "" + "Dir existed");
                        }
                        folderNeedChange = false;
                    }

                    Log.d(TAG, "Recording Folder: " + dir[0] + " " + dir[1]);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("HH-mm-ss-SSS");
                    String timeStamp = dateFormat.format(now);
                    File pictureFile = new File(sdRoot, dir[0] + '/' + timeStamp + ".nv21");
                    if (!pictureFile.exists() && recordingFull) {
                        try {
                            pictureFile.createNewFile();
                            FileOutputStream filecon = new FileOutputStream(pictureFile);
                            filecon.write(data);
                            filecon.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }else{
                        ;
                    }

                    pictureFile = new File(sdRoot, dir[1] + '/' + timeStamp + ".nv21");
                    if (!pictureFile.exists() && recordingRoi) {
                        try {
                            pictureFile.createNewFile();
                            FileOutputStream filecon = new FileOutputStream(pictureFile);

                            int drawH = (int)(min(preHeight, preWidth * DrawView.mHWratio) * DrawView.mStandardRatio);
                            int drawW = (int)(drawH / DrawView.mHWratio);
                            drawH = (0 == drawH % 2) ? drawH : (drawH - 1);
                            drawW = (0 == drawW % 2) ? drawW : (drawW - 1);

                            int top = (preHeight - drawH) / 2;
                            int left = (preWidth - drawW) / 2;
                            byte[] dstImg = new byte[drawH * drawW * 3 / 2];
                            boolean rtv = NativeInterface.cutNV21(data, left, top, preWidth, preHeight, dstImg, drawW, drawH);

                            filecon.write(dstImg);
                            filecon.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }else{
                    ;
                }
//                Log.d(TAG, "" + data.length);
//                Log.d(TAG, "" + dateFormat.format(now));
            }
        }
        camera.addCallbackBuffer(data);
    }

    public int setFlashTorchMode(boolean on) {
        if (camera == null) return -1;
        Camera.Parameters parameters = camera.getParameters();
        if (on) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);//
        } else {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);//
        }
        camera.setParameters(parameters);
        return 0;
    }

    public static final int FOCUS_MODE_NORMAL = 0;           ///< Default focus mode
    public static final int FOCUS_MODE_TRIGGERAUTO = 1;      ///< Triggers a single autofocus operation
    public static final int FOCUS_MODE_CONTINUOUSAUTO = 2;   ///< Continuous autofocus mode
    public static final int FOCUS_MODE_INFINITY = 3;         ///< Focus set to infinity
    public static final int FOCUS_MODE_MACRO = 4;             ///< Macro mode for close-up focus
    public int setFocusMode(int mode) {
        if (camera == null) return -1;
        Camera.Parameters parameters = camera.getParameters();
        List<String> focusModes = parameters.getSupportedFocusModes();
        switch (mode) {
            case 0:
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                break;
            case 1:
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                break;
            case 2:
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                break;
            case 3:
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                break;
            case 4:
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO))
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
                break;
            default:
                break;
        }
        camera.setParameters(parameters);
        return 0;
    }
}