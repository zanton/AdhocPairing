package com.cognition.navyclock;

public enum AtomicClockState {
	AVAILABLE (0x0000),		// if time can be read from the server
	UNAVAILABLE (0x0001),	// if for any reason time cannot be read from server.
	UPDATING (0x0002),		// if the clock is in the process of being updated.
	NOTSET (0x0003); 		// if the clock state has not been set.

	private final int value;
	
	public static final int STATE_AVAILABLE = 0x0000;
	public static final int STATE_UNAVAILABLE = 0x0001;
	public static final int STATE_UPDATING = 0x0002;
	public static final int STATE_NOTSET = 0x0003;
	


	AtomicClockState(int value){
		this.value = value;
	}
	
	public int asInt(){
		return value;
		
	}	
	public static AtomicClockState fromInt(int state){
		AtomicClockState result = NOTSET;
		switch(state){
			case STATE_AVAILABLE:
				result = AVAILABLE;
				break;
			case STATE_UNAVAILABLE:
				result = UNAVAILABLE;
				break;
			case STATE_UPDATING:
				result = UPDATING;
				break;
			default:
				throw new IllegalArgumentException("state must be one of the valid clock states");				
		}
		return result;
	}
	
}
