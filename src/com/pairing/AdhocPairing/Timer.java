package com.pairing.AdhocPairing;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.cognition.navyclock.AtomicClockState;
import com.cognition.navyclock.iNavyClock;
import com.cognition.navyclock.iNavyClockCallback;

public class Timer {
	// Debugging
	public static final String TAG = "Timer";
	public static final boolean D = true;
	
	// System
	private Context mContext;
	private Handler mHandler;
	
	// Member Field
	private long time_offset;
	private long sum;
	private long count;
	private boolean boundToService;
	private AtomicClockState clockState;
	
	private iNavyClock mService = null;
	
	
	public Timer(Context context, Handler handler) {
		mContext = context;
		mHandler = handler;
		
		time_offset = sum = count = 0;
		boundToService = false;
		clockState = AtomicClockState.UNAVAILABLE;
	}
	
	public void start() {
		bindToService();
	}
	
	public void stop() {
		if (mCallback != null && boundToService && mService != null)
			try {
				mService.unregisterTimeListener(mCallback);
			} catch (RemoteException e) {
				Log.e(TAG, "Unable to unregister time listener.");
			}
		
		unbindFromService();    	
	}
	
	public long getTimeOffset() {
		return time_offset;
	}
	
	public long getAverageTimeOffset() {
		return sum/count;
	}
	private void postStatus(String status) {
		Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_POST);
	    Bundle bundle = new Bundle();
	    bundle.putString(AdhocPairingActivity.STATUS, status);
	    msg.setData(bundle);
	    mHandler.sendMessage(msg);
	}

	private void bindToService(){
		boundToService = mContext.bindService(new Intent("com.cognition.navyclock.NavyClockService"), 
		 		mConnection, Context.BIND_AUTO_CREATE); 
		if (boundToService) {
			Log.i(TAG, "Bound to Navy Clock Service");
			postStatus(TAG + ".bindToService() bound to Navy Clock Service");
		}
		else {
			Log.e(TAG, "Unabe to bind to Navy Clock Service.");
			postStatus(TAG + ".bindToService() binding failed!");
		}
    	
    }
    private void unbindFromService(){
		if(boundToService){
			try{
				mContext.unbindService(mConnection);
				Log.i(TAG, "Unbound from Navy Clock Service.");
				postStatus(TAG + ".unbindFromService() successful");
			} catch (IllegalArgumentException ex){
				Log.e("Gravel", "Unable to unbind the Navy Clock Service. " + ex.getMessage());
				postStatus(TAG + ".unbindFromService() failed");
			}
		} else {
			Log.w(TAG, "Never bound to Navy Clock Service.");
		}
    }
    
    private iNavyClockCallback mCallback = new iNavyClockCallback.Stub(){
		
		public void onClockStateChange(int newState, String server,
				String location) throws RemoteException {
		
			Log.d(TAG, "Clock State change from " + clockState + 
					" to " + AtomicClockState.fromInt(newState));

			clockState = AtomicClockState.fromInt(newState);
			
		}

		public void onTimeUpdate(long atomicTime, long localTime)
				throws RemoteException {       
	       
	        time_offset = atomicTime - localTime;   
	        sum += time_offset;
	        count++;
	        
		}
		
	};
	
    private ServiceConnection mConnection  = new ServiceConnection() {
    	
        public void onServiceConnected(ComponentName className,
                IBinder service) {

        	mService = iNavyClock.Stub.asInterface(service);

            if (mCallback != null){
	            try {
	                mService.registerTimeListener(mCallback);
	            } catch (RemoteException e) {
	            	// do nothing
	            } catch (Throwable t){
	            	Log.e(TAG, "Unable to bind to service: " + t.getMessage());
	            	t.printStackTrace();
	            }
            } 
            
        	Log.i(TAG, "Binding to Navy Clock complete");

        }

        public void onServiceDisconnected(ComponentName className) {
        	mService = null;
           	Log.i(TAG, "Unbound from Navy Clock");
        }
        
    };
    
}
