package com.cognition.navyclock;

interface iNavyClockCallback{

	/*
	 * Called frequently to notify the callback of the current atomic time.
	 *
	 * atomicTime is the actual atomic time.
	 * localTime is the time on the phone that the atomicTime was measured.
	 *
	 * The difference between atomicTime and localTime can be interpreted as the offset
	 * or skew of the local clock from the atomic clock.
	 */  
	void onTimeUpdate(long atomicTime, long localTime);
	
	/*
	  * Notifies the callback of the current atomic clock state.  Will be one of the 
	  * states defined in the class AtomicClockState
	  *
	  * newState - the identifier of the new state
	  * server   - the name of the server that the service is currently talking to.
	  * location - the common name/description of the server
	  */
	 void onClockStateChange(int newState, String server, String location);
	 
	 
	 
}