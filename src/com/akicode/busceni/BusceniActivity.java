package com.akicode.busceni;

import android.app.Activity;
import android.os.Bundle;

public class BusceniActivity extends Activity {
	private CameraPreview mPreview;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mPreview = (CameraPreview)findViewById(R.id.cameraPreview); 
    }
    
    protected void onStart() {
    	super.onStart();
    	
    	mPreview.startFaceDetection();
    }
}