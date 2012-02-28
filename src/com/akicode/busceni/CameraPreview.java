package com.akicode.busceni;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
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
public class CameraPreview extends ViewGroup implements SurfaceHolder.Callback, Camera.FaceDetectionListener {
    private final String TAG = "Preview";

    SurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Size mPreviewSize;
    List<Size> mSupportedPreviewSizes;
    Camera mCamera;

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);

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
            
            mCamera.setFaceDetectionListener(this);
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
    
    public void startFaceDetection() {
        //mCamera.startFaceDetection();
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
		}
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

	@Override
	public void onFaceDetection(Face[] faces, Camera camera) {
		for (Face f : faces) {
			Log.i(TAG, "Face: " + f.leftEye.toString());
		}
	}

}