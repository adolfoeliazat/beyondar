package com.beyondar.android.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.FrameLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.beyondar.android.opengl.renderer.ARRenderer.FpsUpdatable;
import com.beyondar.android.screenshot.OnScreenshotListener;
import com.beyondar.android.screenshot.ScreenshotHelper;
import com.beyondar.android.sensor.BeyondarSensorManager;
import com.beyondar.android.util.math.geom.Ray;
import com.beyondar.android.view.BeyondarGLSurfaceView;
import com.beyondar.android.view.BeyondarViewAdapter;
import com.beyondar.android.view.CameraView;
import com.beyondar.android.view.OnClickBeyondarObjectListener;
import com.beyondar.android.view.OnTouchBeyondarViewListener;
import com.beyondar.android.world.BeyondarObject;
import com.beyondar.android.world.GeoObject;
import com.beyondar.android.world.World;

public class BeyondarFragmentSupport extends Fragment implements FpsUpdatable, OnClickListener,
		OnTouchListener {

	private static final int CORE_POOL_SIZE = 1;
	private static final int MAXIMUM_POOL_SIZE = 1;
	private static final long KEEP_ALIVE_TIME = 1000; // 1000 ms

	private CameraView mBeyondarCameraView;
	private BeyondarGLSurfaceView mBeyondarGLSurface;
	private TextView mFpsTextView;
	private RelativeLayout mMainLayout;

	private World mWorld;

	private OnTouchBeyondarViewListener mTouchListener;
	private OnClickBeyondarObjectListener mClickListener;

	private float mLastScreenTouchX, mLastScreenTouchY;

	private ThreadPoolExecutor mThreadPool;
	private BlockingQueue<Runnable> mBlockingQueue;

	private SensorManager mSensorManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mBlockingQueue = new LinkedBlockingQueue<Runnable>();
		mThreadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_TIME,
				TimeUnit.MILLISECONDS, mBlockingQueue);
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mSensorManager = (SensorManager) (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
	}

	private void init() {
		android.view.ViewGroup.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);

		mMainLayout = new RelativeLayout(getActivity());
		mBeyondarGLSurface = createBeyondarGLSurfaceView();
		mBeyondarGLSurface.setOnTouchListener(this);

		mBeyondarCameraView = createCameraView();

		mMainLayout.addView(mBeyondarCameraView, params);
		mMainLayout.addView(mBeyondarGLSurface, params);
	}

	private void checkIfSensorsAvailable() {
		PackageManager pm = getActivity().getPackageManager();
		boolean compass = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS);
		boolean accelerometer = pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
		if (!compass && !accelerometer) {
			throw new IllegalStateException(getClass().getName()
					+ " can not run without the compass and the acelerometer sensors.");
		} else if (!compass) {
			throw new IllegalStateException(getClass().getName() + " can not run without the compass sensor.");
		} else if (!accelerometer) {
			throw new IllegalStateException(getClass().getName()
					+ " can not run without the acelerometer sensor.");
		}

	}

	/**
	 * Override this method to personalize the {@link BeyondarGLSurfaceView}
	 * that will be instantiated
	 * 
	 * @return
	 */
	protected BeyondarGLSurfaceView createBeyondarGLSurfaceView() {
		return new BeyondarGLSurfaceView(getActivity());
	}

	/**
	 * Override this method to personalize the {@link CameraView} that will be
	 * instantiated
	 * 
	 * @return
	 */
	protected CameraView createCameraView() {
		return new CameraView(getActivity());
	}

	/**
	 * 
	 * Returns the CameraView for this class instance.
	 * 
	 * @return
	 */
	public CameraView getCameraView() {
		return mBeyondarCameraView;
	}

	/**
	 * Returns the SurfaceView for this class instance.
	 * 
	 * @return
	 */
	public BeyondarGLSurfaceView getGLSurfaceView() {
		return mBeyondarGLSurface;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		init();
		startRenderingAR();
		return mMainLayout;
	}

	@Override
	public void onResume() {
		super.onResume();
		mBeyondarCameraView.startPreviewCamera();
		mBeyondarGLSurface.onResume();
		BeyondarSensorManager.resume(mSensorManager);
	}

	@Override
	public void onPause() {
		super.onPause();
		mBeyondarCameraView.releaseCamera();
		mBeyondarGLSurface.onPause();
		BeyondarSensorManager.pause(mSensorManager);
	}

	/**
	 * Set the listener to get notified when the user touch the AR view
	 * 
	 * @param listener
	 */
	public void setOnTouchBeyondarViewListener(OnTouchBeyondarViewListener listener) {
		mTouchListener = listener;
	}

	public void setOnClickBeyondarObjectListener(OnClickBeyondarObjectListener listener) {
		mClickListener = listener;
		mMainLayout.setClickable(listener != null);
		mMainLayout.setOnClickListener(this);
	}

	@Override
	public boolean onTouch(View v, final MotionEvent event) {
		mLastScreenTouchX = event.getX();
		mLastScreenTouchY = event.getY();

		if (mWorld == null || mTouchListener == null || event == null) {
			return false;
		}
		mTouchListener.onTouchBeyondarView(event, mBeyondarGLSurface);
		return false;
	}

	@Override
	public void onClick(View v) {
		if (v == mMainLayout) {
			if (mClickListener == null) {
				return;
			}
			final float lastX = mLastScreenTouchX;
			final float lastY = mLastScreenTouchY;

			mThreadPool.execute(new Runnable() {
				@Override
				public void run() {
					final ArrayList<BeyondarObject> beyondarObjects = new ArrayList<BeyondarObject>();
					mBeyondarGLSurface.getBeyondarObjectsOnScreenCoordinates(lastX, lastY, beyondarObjects);
					if (beyondarObjects.size() == 0)
						return;
					mBeyondarGLSurface.post(new Runnable() {
						@Override
						public void run() {
							OnClickBeyondarObjectListener listener = mClickListener;
							if (listener != null) {
								listener.onClickBeyondarObject(beyondarObjects);
							}
						}
					});
				}
			});
		}
	}

	/**
	 * Get the world in use by the fragment
	 * 
	 * @return
	 */
	public World getWorld() {
		return mWorld;
	}

	/**
	 * Set the world to be shown
	 * 
	 * @param world
	 * 
	 * @throws IllegalStateException
	 *             If the device do not have the required sensors available
	 */
	public void setWorld(World world) {
		try {
			checkIfSensorsAvailable();
		} catch (IllegalStateException e) {
			throw e;
		}
		mWorld = world;
		mBeyondarGLSurface.setWorld(world);
	}

	/**
	 * Specify the delay to apply to the accelerometer and the magnetic field
	 * sensor. If you don't know what is the best value, don't touch it. The
	 * following values are applicable:<br>
	 * <br>
	 * SensorManager.SENSOR_DELAY_UI<br>
	 * SensorManager.SENSOR_DELAY_NORMAL <br>
	 * SensorManager.SENSOR_DELAY_GAME <br>
	 * SensorManager.SENSOR_DELAY_GAME <br>
	 * SensorManager.SENSOR_DELAY_FASTEST <br>
	 * <br>
	 * You can find more information in the
	 * {@link android.hardware.SensorManager} class
	 * 
	 * 
	 * @param delay
	 */
	public void setSensorDelay(int delay) {
		mBeyondarGLSurface.setSensorDelay(delay);
	}

	/**
	 * Get the current sensor delay. See {@link android.hardware.SensorManager}
	 * for more information
	 * 
	 * @return sensor delay
	 */
	public int getSensorDelay() {
		return mBeyondarGLSurface.getSensorDelay();
	}

	public void setFpsUpdatable(FpsUpdatable fpsUpdatable) {
		mBeyondarGLSurface.setFpsUpdatable(fpsUpdatable);
	}

	/**
	 * Force the GLSurface to stop rendering the AR world
	 */
	public void stopRenderingAR() {
		mBeyondarGLSurface.setVisibility(View.INVISIBLE);
	}

	/**
	 * Force the GLSurface to start rendering the AR world
	 */
	public void startRenderingAR() {
		mBeyondarGLSurface.setVisibility(View.VISIBLE);
	}

	/**
	 * Get the GeoObject that intersect with the coordinates x, y on the screen.<br>
	 * NOTE: When this method is called a new {@link List} is created.
	 * 
	 * @param x
	 * @param y
	 * 
	 * @return A new list with the {@link BeyondarObject} that collide with the
	 *         screen cord
	 */
	public List<BeyondarObject> getBeyondarObjectsOnScreenCoordinates(float x, float y) {
		ArrayList<BeyondarObject> beyondarObjects = new ArrayList<BeyondarObject>();
		mBeyondarGLSurface.getBeyondarObjectsOnScreenCoordinates(x, y, beyondarObjects);
		return beyondarObjects;
	}

	/**
	 * Get the GeoObject that intersect with the coordinates x, y on the screen
	 * 
	 * @param x
	 * @param y
	 * @param beyondarObjects
	 *            The output list to place all the {@link BeyondarObject} that
	 *            collide with the screen cord
	 * @return
	 */
	public void getBeyondarObjectsOnScreenCoordinates(float x, float y,
			ArrayList<BeyondarObject> beyondarObjects) {
		mBeyondarGLSurface.getBeyondarObjectsOnScreenCoordinates(x, y, beyondarObjects);
	}

	/**
	 * Get the GeoObject that intersect with the coordinates x, y on the screen
	 * 
	 * @param x
	 * @param y
	 * @param beyondarObjects
	 *            The output list to place all the {@link BeyondarObject} that
	 *            collide with the screen cord
	 * @param ray
	 *            The ray that will hold the direction of the screen coordinate
	 * @return
	 */
	public void getBeyondarObjectsOnScreenCoordinates(float x, float y,
			ArrayList<BeyondarObject> beyondarObjects, Ray ray) {
		mBeyondarGLSurface.getBeyondarObjectsOnScreenCoordinates(x, y, beyondarObjects, ray);

	}

	/**
	 * When a {@link GeoObject} is rendered according to its position it could
	 * look very small if it is far away. Use this method to render far objects
	 * as if there were closer.<br>
	 * For instance if there is an object at 100 meters and we want to have
	 * everything at least at 25 meters, we could use this method for that
	 * purpose. <br>
	 * To set it to the default behavior just set it to 0
	 * 
	 * @param maxDistanceSize
	 *            The top far distance (in meters) which we want to draw a
	 *            {@link GeoObject} , 0 to set again the default behavior
	 */
	public void setMaxFarDistance(float maxDistanceSize) {
		mBeyondarGLSurface.setMaxDistanceSize(maxDistanceSize);
	}

	/**
	 * Get the max distance which a {@link GeoObject} will be rendered.
	 * 
	 * @return The current max distance. 0 is the default behavior
	 */
	public float getMaxDistanceSize() {
		return mBeyondarGLSurface.getMaxDistanceSize();
	}

	/**
	 * When a {@link GeoObject} is rendered according to its position it could
	 * look very big if it is too close. Use this method to render near objects
	 * as if there were farther.<br>
	 * For instance if there is an object at 1 meters and we want to have
	 * everything at least at 10 meters, we could use this method for that
	 * purpose. <br>
	 * To set it to the default behavior just set it to 0
	 * 
	 * @param minDistanceSize
	 *            The top near distance (in meters) which we want to draw a
	 *            {@link GeoObject} , 0 to set again the default behavior
	 */
	public void setMinFarDistanceSize(float minDistanceSize) {
		mBeyondarGLSurface.setMinDistanceSize(minDistanceSize);
	}

	/**
	 * Get the minimum distance which a {@link GeoObject} will be rendered.
	 * 
	 * @return The current minimum distance. 0 is the default behavior
	 */
	public float getMinDistanceSize() {
		return mBeyondarGLSurface.getMinDistanceSize();
	}

	/**
	 * Take a screenshot of the beyondar fragment. The screenshot will contain
	 * the camera + the AR world
	 * 
	 * @param listener
	 */
	public void takeScreenshot(OnScreenshotListener listener) {
		ScreenshotHelper.takeScreenshot(getCameraView(), getGLSurfaceView(), listener);
	}

	/**
	 * Show the number of frames per second. False by default
	 * 
	 * @param show
	 *            True to show the FPS, false otherwise
	 */
	public void showFPS(boolean show) {
		if (show) {
			if (mFpsTextView == null) {
				mFpsTextView = new TextView(getActivity());
				mFpsTextView.setBackgroundResource(android.R.color.black);
				mFpsTextView.setTextColor(getResources().getColor(android.R.color.white));
				LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
						ViewGroup.LayoutParams.WRAP_CONTENT);
				mMainLayout.addView(mFpsTextView, params);
			}
			mFpsTextView.setVisibility(View.VISIBLE);
			setFpsUpdatable(this);
		} else if (mFpsTextView != null) {
			mFpsTextView.setVisibility(View.GONE);
			setFpsUpdatable(null);
		}
	}

	@Override
	public void onFpsUpdate(final float fps) {
		if (mFpsTextView != null) {
			mFpsTextView.post(new Runnable() {
				@Override
				public void run() {
					mFpsTextView.setText("fps: " + fps);
				}
			});
		}
	}

	/**
	 * Set the adapter to draw the views on top of the AR View.
	 * 
	 * @param adapter
	 */
	public void setBeyondarViewAdapter(BeyondarViewAdapter adapter) {
		mBeyondarGLSurface.setBeyondarViewAdapter(adapter, mMainLayout);
	}

	public void forceFillBeyondarObjectPositionsOnRendering(boolean fill) {
		mBeyondarGLSurface.forceFillBeyondarObjectPositionsOnRendering(fill);
	}

	/**
	 * Use this method to fill all the screen positions of the
	 * {@link BeyondarObject}. After this method is called you can use the
	 * following:<br>
	 * {@link BeyondarObject#getScreenPositionBottomLeft()}<br>
	 * {@link BeyondarObject#getScreenPositionBottomRight()}<br>
	 * {@link BeyondarObject#getScreenPositionTopLeft()}<br>
	 * {@link BeyondarObject#getScreenPositionTopRight()}
	 * 
	 * @param beyondarObject
	 *            The {@link BeyondarObject} to compute
	 */
	public void fillBeyondarObjectPositions(BeyondarObject beyondarObject) {
		mBeyondarGLSurface.fillBeyondarObjectPositions(beyondarObject);
	}
}