package eecs.arlocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity implements SensorEventListener {
    private SurfaceView mContentView;
    private CameraDevice mCameraDevice;
    private Surface mDisplaySurface;
    private HandlerThread mCameraHandlerThread;
    private Handler mCameraHandler;
    private Size bestSize;
    private LocationController locationController;

    // arbitrary constants for permissions request
    public static final int MY_PERMISSIONS_REQUEST_READ_CAMERA = 0;
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private CameraCaptureSession previewCaptureSession;

    // hardcode temp value to be overridden later
    private float horizontalAngle = 60;

    private float azimuth;
    private ExpFilter diff;
    private Location myLocation;

    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] mAccelerometer = null;
    private float[] mGeomagnetic = null;

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mContentView = (SurfaceView) findViewById(R.id.fullscreen_content);
        diff = new ExpFilter(0.25);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        mContentView = (SurfaceView) findViewById(R.id.fullscreen_content);


        mCameraHandlerThread = new HandlerThread("CameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        final Handler h = new Handler(Looper.getMainLooper());
        final Runnable r = new Runnable() {
            public void run() {
                Location destination = new Location("destination"); //replace
                Location source = myLocation;
                source.setLatitude(42.356);
                source.setLongitude(-71.102);
                destination.setLongitude(-71.1019655);
                destination.setLatitude(42.3545758);
                float distance = source.distanceTo(destination);
                Log.d("distance",String.valueOf(distance));
                float mybear = azimuth;
                float desirebear = source.bearingTo(destination);
                float diffUpdate = mybear - desirebear;
                if (diffUpdate < -180) diffUpdate += 360;
                if (diffUpdate >= 180) diffUpdate -= 360;
                diff.update(diffUpdate);
                int width = mContentView.getWidth();
                int height = mContentView.getHeight();
                double x = distance * Math.sin(diff.getValue() * Math.PI / 180);
                double y = distance * Math.cos(diff.getValue() * Math.PI / 180);
                double z = y * Math.tan(horizontalAngle / 2);
                int offset = -(int) (x / z * width / 2);
                View target = findViewById(R.id.target);
                // check that filtered angle is inside horizontal FOV
                // and unfiltered angle is inside 2 * FOV to avoid spazz at exactly 180 away
                if (diff.getValue() < horizontalAngle / 2
                        && diff.getValue() > -horizontalAngle / 2
                        && diffUpdate < horizontalAngle
                        && diffUpdate > -horizontalAngle) {
                    target.animate().x(width / 2 + offset).y(20).setDuration(30).start();
                    target.setVisibility(View.VISIBLE);
                } else {
                    target.setVisibility(View.GONE);
                }
                ImageView right = (ImageView) findViewById(R.id.right);
                //final TextView helloTextView = (TextView) findViewById(R.id.name_id);

                final TextView distancetext = (TextView) findViewById(R.id.distance_id);
                String distance_string = String.valueOf((int) Math.round(distance)) + " meters away";

                distancetext.setText(distance_string);
                Log.d("direction", String.valueOf(azimuth));
                Log.d("thing", String.valueOf(diff));
                if (diff.getValue() > 18) {
                    Log.d("thing", "LEFT LEFT LEFT");
                    right.setImageResource(R.drawable.brandon_left);
                } else if (diff.getValue() < -18) {
                    Log.d("thing", "RIGHT RIGHT RIGHT");
                    right.setImageResource(R.drawable.brandon_right);
                }
                else {
                    Log.d("thing", "CENTER CENTER CENTER");
                    right.setImageResource(R.drawable.brandon_center);
                }

                h.postDelayed(this, 75);
            }

        };
        h.post(r);
        setupCamera();

        locationController = new LocationController(this);
        locationController.checkPermission();
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

    @Override
    protected void onResume() {
        super.onResume();

        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);

        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this, accelerometer);
        mSensorManager.unregisterListener(this, magnetometer);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        // onSensorChanged gets called for each sensor so we have to remember the values
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mAccelerometer = event.values;
        }

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }

        if (mAccelerometer != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mAccelerometer, mGeomagnetic);

            if (success) {
                float orientation[] = new float[3];
                float correctedR[] = new float[9];
                SensorManager.remapCoordinateSystem(R, SensorManager.AXIS_X, SensorManager.AXIS_Z, correctedR);
                SensorManager.getOrientation(correctedR, orientation);
                // at this point, orientation contains the azimuth(direction), pitch and roll values.
                azimuth = (float) (180 * orientation[0] / Math.PI);
                double pitch = 180 * orientation[1] / Math.PI;
                double roll = 180 * orientation[2] / Math.PI;
            }
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
                    int maxArea = -1;
                    for (int i = 0; i < sizes.length; i++) {
                        Size s = sizes[i];
                        int area = s.getWidth() * s.getHeight();
                        if (area > maxArea) {
                            maxArea = area;
                            bestSize = s;
                        }
                        Log.d("AR", "" + s.getWidth() + "/" + s.getHeight() + " = " + area + "," + (double) s.getWidth() / s.getHeight());
                    }
                    Log.d("AR", "" + bestSize);

                    float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                    float w = 0.5f * sensorSize.getWidth();
                    float h = 0.5f * sensorSize.getHeight();
                    Log.d("AR", "sensorSize = " + 2 * w + ", " + 2 * h);
                    float focalLength = focalLengths[0];
                    horizontalAngle = (float) Math.toDegrees(2 * Math.atan(w / focalLength));
//                    float verticalAngle = (float) Math.toDegrees(2 * Math.atan(h / focalLength));
                    Log.d("AR", "using first focalLength = " + focalLength + "mm");
                    Log.d("AR", "horizonalAngle = " + horizontalAngle);
//                    Log.d("AR", "verticalAngle = " + verticalAngle);

                    cameraManager.openCamera(id, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            Log.d("AR", "camera opened");
                            mCameraDevice = camera;

                            final SurfaceHolder displaySurfaceHolder = mContentView.getHolder();
                            displaySurfaceHolder.addCallback(new SurfaceHolder.Callback() {
                                @Override
                                public void surfaceCreated(SurfaceHolder holder) {
                                    Log.d("AR", "surface created");
                                    mDisplaySurface = holder.getSurface();
                                    startPreview();
                                }

                                @Override
                                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                    Log.d("AR", "surface changed");
                                }

                                @Override
                                public void surfaceDestroyed(SurfaceHolder holder) {
                                    Log.d("AR", "surface destroyed");
                                    mDisplaySurface = null;
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
        try {
            final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
            final CameraCaptureSession.StateCallback cameraStateCallback = new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        Log.d("AR", "configured completed");
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null);
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
        } else if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            locationController.checkPermission();
        }
    }

    public void setLocation(Location l){
        myLocation = new Location(l);
    }
}

