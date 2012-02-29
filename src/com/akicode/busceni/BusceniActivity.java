package com.akicode.busceni;

import com.akicode.busceni.CameraPreview.DetectionListener;
import com.akicode.util.RepeatingRunnable;

import android.app.Activity;
import android.media.FaceDetector.Face;
import android.os.Bundle;

public class BusceniActivity extends Activity implements DetectionListener {
	private CameraPreview mPreview;
	
	private RepeatingRunnable mDetector = new RepeatingRunnable(1000) {
		
		@Override
		public void task() {
			mPreview.doFaceDetection();
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mPreview = (CameraPreview)findViewById(R.id.cameraPreview);
        
        //mPreview.setListener(this);
    }
    
    protected void onStart() {
    	super.onStart();
    	
    	mDetector.start();
    }

	@Override
	public void onDetection(Face[] faces) {
		
	}
}