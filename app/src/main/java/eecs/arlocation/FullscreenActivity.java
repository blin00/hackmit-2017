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
import static eecs.arlocation.Util.minf;

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

    // temp values - is set by code below
    private float horizontalAngle = 60;
    private float verticalAngle = 60;

    private float azimuth;
    private ExpFilter pitch;

    private Location myLocation;

    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] mAccelerometer = null;
    private float[] mGeomagnetic = null;

    private Runnable updateRunnable;
    private Handler updateHandler;

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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        pitch = new ExpFilter(0.25);

        mContentView = (SurfaceView) findViewById(R.id.fullscreen_content);

        mCameraHandlerThread = new HandlerThread("CameraThread");
        mCameraHandlerThread.start();
        mCameraHandler = new Handler(mCameraHandlerThread.getLooper());

        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            public void run() {
                Target brandon = new Target("brandon", new Location("brandon_location"), R.drawable.brandon_left,R.drawable.brandon_right,R.drawable.brandon_center,findViewById(R.id.brandon_target), findViewById(R.id.brandon) );
                Target zhongxia = new Target("z", new Location("zhongxia_location"),R.drawable.zhongxia_left,R.drawable.zhongxia_right,R.drawable.zhongxia_center ,findViewById(R.id.zhongxia_target), findViewById(R.id.zhongxia));
                Target alex = new Target("alex", new Location("alex_location"), R.drawable.alex_left,R.drawable.alex_right,R.drawable.alex_center ,findViewById(R.id.alex_target), findViewById(R.id.alex));

                zhongxia.setLocation(-91.3019655, 42.1545758);
                alex.setLocation(-71.0019655, 43.3545758);
                brandon.setLocation(-71.1019655, 42.3545758);

                ArrayList<Target> targets = new ArrayList<Target>();
                targets.add(brandon);
                targets.add(zhongxia);
                targets.add(alex);


                for (Target t : targets) {
                    Location source = new Location("source");
                    source.setLatitude(42.356);
                    source.setLongitude(-71.102);

                    float distance = source.distanceTo(t.getLocation());
                    float mybear = azimuth;
                    float desirebear = source.bearingTo(t.getLocation());
                    float diffRaw = mybear - desirebear;
                    if (diffRaw < -180) diffRaw += 360;
                    if (diffRaw >= 180) diffRaw -= 360;
                    t.exp.update(diffRaw);
                    int width = mContentView.getWidth();
                    int height = mContentView.getHeight();
                    double x = distance * Math.sin(Math.toRadians(t.exp.getValue()));
                    double y = distance * Math.cos(Math.toRadians(t.exp.getValue()));
                    double z = y * Math.tan(Math.toRadians(horizontalAngle / 2));
                    int offset_x = -(int) Math.round(x / z * width / 2);
                    double a = distance * Math.sin(Math.toRadians(pitch.getValue()));
                    double b = distance * Math.cos(Math.toRadians(pitch.getValue()));
                    double c = b * Math.tan(Math.toRadians(verticalAngle / 2));
                    int offset_y = -(int) Math.round(a / c * height / 2);
                    View target = t.plumbbob;
                    // check unfiltered angle is inside 2 * FOV to avoid spazz at exactly 180 away
                    if (diffRaw < horizontalAngle && diffRaw > -horizontalAngle) {
                        target.animate().x(width / 2 + offset_x).y(height / 2 + offset_y).setDuration(75).start();
                        target.setVisibility(View.VISIBLE);
                    } else {
                        target.setVisibility(View.GONE);
                    }

                    final TextView distancetext = (TextView) findViewById(R.id.distance_id);
                    String distance_string = String.valueOf((int) Math.round(distance)) + " meters away";

                    distancetext.setText(distance_string);
                    Log.d(t.name, String.valueOf(azimuth));
                    Log.d(t.name, String.valueOf(t.exp));

                    if (t.exp.getValue() > 18) {
                        Log.d("thing", "LEFT LEFT LEFT");
                        //t_dir.setImageResource(R.drawable.brandon_left);
                        t.set_pointer_direction(-1);
                    } else if (t.exp.getValue() < -18) {
                        Log.d("thing", "RIGHT RIGHT RIGHT");
                        //t_dir.setImageResource(R.drawable.brandon_right);
                        t.set_pointer_direction(1);
                    } else {
                        Log.d("thing", "CENTER CENTER CENTER");
                        //t_dir.setImageResource(R.drawable.brandon_center);
                        t.set_pointer_direction(0);
                    }
                }

                updateHandler.postDelayed(this, 100);
            }
        };
        locationController = new LocationController(this);
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
                azimuth = (float) Math.toDegrees(orientation[0]);
                pitch.update(Math.toDegrees(orientation[1]));
//                double roll = 180 * orientation[2] / Math.PI;
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
                    float focalLength = minf(focalLengths);
                    horizontalAngle = (float) Math.toDegrees(2 * Math.atan(w / focalLength));
                    verticalAngle = (float) Math.toDegrees(2 * Math.atan(h / focalLength));
                    Log.d("AR", "using smallest focalLength = " + focalLength + "mm");
                    Log.d("AR", "horizonalAngle = " + horizontalAngle);
                    Log.d("AR", "verticalAngle = " + verticalAngle);

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
        if (locationController.checkPermission()) {
            updateHandler.post(updateRunnable);
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
                Log.d("AR", "camera perms retry");
                setupCamera();
            }
        } else if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (locationController.checkPermission()) {
                updateHandler.post(updateRunnable);
            }
        }
    }

    public void setLocation(Location l){
        myLocation = new Location(l);
    }
}

