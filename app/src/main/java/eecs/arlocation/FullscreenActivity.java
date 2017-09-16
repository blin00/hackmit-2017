package eecs.arlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.location.Location;
import android.media.Image;
import android.os.HandlerThread;
import android.os.Looper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private SurfaceView mContentView;
    private View mControlsView;
    private CameraDevice mCameraDevice;
    private Surface mDisplaySurface;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    private CameraCaptureSession previewCaptureSession;
    // arbitrary constants for permissions request
    private static final int MY_PERMISSIONS_REQUEST_READ_CAMERA = 0;
/*
            final Handler h = new Handler(Looper.getMainLooper());
            final Runnable r = new Runnable() {
                public void run() {
                    Location destination = new Location("destination"); //replace
                    Location source = new Location("source"); //replace


                    View left = findViewById(R.id.left);
                    View right = findViewById(R.id.right);
                    right.setVisibility(View.INVISIBLE);
                    h.postDelayed(this, 300);

                }

            };
            h.post(r);
*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = (SurfaceView) findViewById(R.id.fullscreen_content);

        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        mCameraHandlerThread = new HandlerThread("CameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        setupCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraHandlerThread.quit();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("AR", "requesting camera perms");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_READ_CAMERA);
        } else {
            Log.d("AR", "already have camera perms");
            finishSetupCamera();
        }
    }

    @SuppressWarnings({"MissingPermission"})
    private void finishSetupCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] idList = cameraManager.getCameraIdList();
            for (String id : idList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == LENS_FACING_BACK) {
                    Log.d("AR", "using back-facing camera with id " + id);
                    StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizes = configs.getOutputSizes(SurfaceTexture.class);
                    for (Size s : sizes) {
                        Log.d("AR", "" + s.getWidth() + "/" + s.getHeight() + " = " + (double) s.getWidth() / s.getHeight());
                    }
                    cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            Log.d("AR", "camera opened");
                            mCameraDevice = camera;

                            final SurfaceHolder displaySurfaceHolder = mContentView.getHolder();
                            displaySurfaceHolder.addCallback(new SurfaceHolder.Callback() {
                                @Override
                                public void surfaceCreated(final SurfaceHolder holder) {
                                    Log.d("AR", "surface created");
                                    mDisplaySurface = holder.getSurface();
                                    startPreview();
                                }

                                @Override
                                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                    Log.d("AR", "surface changed");
                                    mDisplaySurface = holder.getSurface();
                                }

                                @Override
                                public void surfaceDestroyed(SurfaceHolder holder) {
                                    Log.d("AR", "surface destroyed");
                                    mDisplaySurface = null;
                                    previewCaptureSession = null;
                                }
                            });
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            Log.d("AR", "camera disconnected");
                            mCameraDevice = null;
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.d("AR", "camera error");
                            mCameraDevice = null;
                        }
                    }, null);
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
//                Log.d("AR", "capture completed");
            }

            @Override
            public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
                Log.d("AR", "capture failed");
            }

            @Override
            public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                         @NonNull CaptureRequest request, long timestamp, long frameNumber) {
//                Log.d("AR", "capture started");
            }
        };
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            final CameraCaptureSession.StateCallback cameraStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        Log.d("AR", "configured completed");
                        previewCaptureSession = session;
                        session.setRepeatingRequest(captureRequestBuilder.build(), captureListener, null);
                    } catch (CameraAccessException cae) {
                        throw new RuntimeException("Capture failed");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.d("AR", "configured failed");
                    throw new RuntimeException("capture configuration failed");
                }
            };
            createCaptureSession(captureRequestBuilder, cameraStateCallback, mDisplaySurface);
        } catch (CameraAccessException cae) {
            throw new RuntimeException("creating capture request failed");
        }
    }

    private void createCaptureSession(CaptureRequest.Builder captureRequestBuilder, CameraCaptureSession.StateCallback cameraStateCallback, Surface surface) {
        captureRequestBuilder.addTarget(surface);
        List<Surface> outputs = new ArrayList<>();
        outputs.add(surface);
        try {
            mCameraDevice.createCaptureSession(outputs, cameraStateCallback, mCameraHandler);
        } catch (CameraAccessException cae) {
            throw new RuntimeException("Cannot create capture session");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_CAMERA) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("AR", "camera perms success");
                finishSetupCamera();
            } else {
                // try again
                setupCamera();
            }
        }
    }
}
