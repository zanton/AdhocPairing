package com.cognition.navyclock;

import com.cognition.navyclock.iNavyClockCallback;

interface iNavyClock{

	/*
	 * Requests current time to be sent to the callback on the specified interval.
	 * Intervals are specified in milliseconds.
	 */
	 void registerTimeListener(iNavyClockCallback callback);

    /*
     * Remove a previously registered callback interface.
     */
    void unregisterTimeListener(iNavyClockCallback callback);
   
}