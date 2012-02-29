package com.akicode.busceni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A simple wrapper around a Camera and a SurfaceView that renders a centered preview of the Camera
 * to the surface. We need to center the SurfaceView because not all devices have cameras that
 * support preview sizes at the same aspect ratio as the device's display.
 */
public class CameraPreview extends FrameLayout implements SurfaceHolder.Callback, PreviewCallback {
    public interface DetectionListener {
    	public void onDetection(Face[] faces);
	}

	private final String TAG = "Preview";
    private final int MAX_FACES = 1;

	static final Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	static {
		eyePaint.setColor(Color.RED);
	}

    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;
    FaceDetector mFaceDetector;
    Face[] mFaces = new Face[MAX_FACES];
    DetectionListener mListener;
    EyeView mEyeView;

    private class EyeView extends View {
    	
    	public EyeView(Context context) {
			super(context);
			// TODO Auto-generated constructor stub
		}

		public void onDraw(Canvas c) {
    		c.drawCircle(10, 10, 10, eyePaint);
    	}
		
		protected void newFaces() {
			Log.i(TAG, "New faces, redrawing");
			
			invalidate();
		}
    }
    
    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

        mEyeView = new EyeView(context);
        addView(mEyeView);
        
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                
        setCamera(Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT));
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        mCamera.setDisplayOrientation(90);
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();
        }
    }
    
    public void switchCamera(Camera camera) {
       setCamera(camera);
       try {
           camera.setPreviewDisplay(mHolder);
       } catch (IOException exception) {
           Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
       }
       
       Camera.Parameters parameters = camera.getParameters();
       parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
       requestLayout();

       camera.setParameters(parameters);
    }

    public DetectionListener getListener() {
		return mListener;
	}

	public void setListener(DetectionListener mListener) {
		this.mListener = mListener;
	}

	@Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
            mFaceDetector = new FaceDetector(mPreviewSize.width, mPreviewSize.height, MAX_FACES);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);

            final int width = r - l;
            final int height = b - t;

            int previewWidth = width;
            int previewHeight = height;
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
            }
            
            child.layout(0, 0,width,height);
            
            /*// Center the child SurfaceView within the parent.
            if (width * previewHeight > height * previewWidth) {
                final int scaledChildWidth = previewWidth * height / previewHeight;
                child.layout((width - scaledChildWidth) / 2, 0,
                        height, (width + scaledChildWidth) / 2);
            } else {
                final int scaledChildHeight = previewHeight * width / previewWidth;
                child.layout(0, (height - scaledChildHeight) / 2,
                        width, (height + scaledChildHeight) / 2);
            }*/
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }


    private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        
   //     int w2 = optimalSize.width;
     //   optimalSize.width = optimalSize.height;
       // optimalSize.height = w2;
        
        return optimalSize;
    }
    
    public void doFaceDetection() {
    	//mCamera.getParameters().setPreviewFormat(ImageFormat.RGB_565);
    	mCamera.setOneShotPreviewCallback(this);
    	
       /* //mCamera.startFaceDetection();
    	Log.i(TAG, "Max faces: " + mCamera.getParameters().getMaxNumDetectedFaces());
    	Log.i(TAG, "Forcing SW face detection...");
    	
    	// Replicates functionality of Camera.startFaceDetection() via reflection but specifies type=1 (S/W mode) instead of type=0.  
    	try {
    		Field detectionRunning = mCamera.getClass().getDeclaredField("mFaceDetectionRunning");
    		detectionRunning.setAccessible(true);
    		if (detectionRunning.getBoolean(mCamera)) {
    			Log.e(TAG, "Face detection already running");
    			return;
    		}
    		
			Method nativeStart = mCamera.getClass().getDeclaredMethod("_startFaceDetection", new Class[]{int.class});
			nativeStart.setAccessible(true);
			nativeStart.invoke(mCamera, 1);
			
			detectionRunning.set(mCamera, true);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "Failed to start face detection", e);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "Failed to start face detection", e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "Failed to start face detection", e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "Failed to start face detection", e);
		} catch (NoSuchFieldException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "Failed to start face detection", e);
		}*/
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        requestLayout();

        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }

	/*@Override
	public void onFaceDetection(Face[] faces, Camera camera) {
		for (Face f : faces) {
			Log.i(TAG, "Face: " + f.leftEye.toString());
		}
	}*/

	@Override
	public void onPreviewFrame(byte[] arg0, Camera arg1) {
		Log.i(TAG, "Starting face detection");
		new DetectFacesTask().execute(arg0); 
	}
	
	private class DetectFacesTask extends AsyncTask<byte[], Integer, Face[]> {

		@Override
		protected Face[] doInBackground(byte[]... params) {
			if (params.length < 1 || params[0] == null) {
				Log.e(TAG, "No image data....");
				return null;
			}
			
			YuvImage image = new YuvImage(params[0], ImageFormat.NV21, mPreviewSize.width, mPreviewSize.height, null);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			image.compressToJpeg(new Rect(0, 0, mPreviewSize.width, mPreviewSize.height), 80, baos);
			byte[] jdata = baos.toByteArray();
			Bitmap bitmap = BitmapFactory.decodeByteArray(jdata, 0, jdata.length);
			Bitmap b2 = bitmap.copy(Bitmap.Config.RGB_565, true);
			bitmap.recycle();
			int found = mFaceDetector.findFaces(b2, mFaces);
			b2.recycle();
			
			Log.i(TAG, "Async, found " + found + " faces");
			
			return mFaces;
		}

		@Override
		protected void onPostExecute(Face[] result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			
			//mListener.onDetection(result);
			mEyeView.newFaces();
		}
		
	}

}