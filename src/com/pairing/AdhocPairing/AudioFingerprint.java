package com.pairing.AdhocPairing;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioFingerprint {
	// Debugging
	public static final String TAG = "AudioFingerprint";
	public static final boolean D = false;
	
	// Constants that indicate the current recording state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_RECORDING = 1;   
	
    // Constant for PairingManager to access
    public static int fingerprintBits = 0;
    
	// Default arguments
	public static final int DEFAULT_SAMPLING_TIME = 2125;//6375; //6375; //millisecond
	public static final int DEFAULT_DELAYED_TIME = 2000; //millisecond, waiting time before sampling
	public static final int DEFAULT_REDUNDANT_TIME = 100; //for each forward and backward
	public static final int DEFAULT_SAMPLING_RATE = 44100; //Hz
	public static final int DEFAULT_FRAME_LENGTH = 8192;//16384; //2^12, must be a power of 2
	public static final int DEFAULT_BAND_LENGTH = 700;//497; //Frequency band, result of dividing frame
	//public static final int DEFAULT_MAX_FREQUENCY = 10000; //max frequency
	//public static final int DEFAULT_MIN_FREQUENCY = 70; //min frequency
	
	// Constant for pattern sync
	public static final int NUMBER_OF_MATCHING_POSITIONS = 10;

	// Arguments for sampling audio
	private int sampling_time; //millisecond
	private int delayed_time; //millisecond, waiting time before sampling
	private int redundant_time; //for each forward and backward
	private int sampling_rate; //Hz
	private int nFrame; //number of time frames
	private int nBand; //number of frequency bands
	private int frame_length; //must be a power of 2 in order to be able to FFT
	private int band_length; //number of samples in a frequency band
	private int sample_start; // beginning position of the main sample
	private int sample_end; // end position (plus 1) of the main sample
	
	// Member fields
	private int mState;
	private int bufferSize; // in shorts
	private short[] audioBuffer = null;
	private long recordingTime = 0;
	private byte[] fingerprint = null; // the result of pattern-sync
	private int[] pattern_matching_pos = new int[NUMBER_OF_MATCHING_POSITIONS]; // millisecond
	private Random mRandom = new Random();
	private int[] random_matching_pos = new int[NUMBER_OF_MATCHING_POSITIONS];
	
	// Tool object
	private Context mContext;
	private Handler mHandler;
	private Timer mTimer;
	private AudioRecord mAudioRecord = null;
	private RecordThread mRecordThread = null;
	private ComputingThread mComputingThread = null;
	private MatchingPatternThread mMatchingPatternThread = null;
	private HashMap<String, byte[][]> mHashMap = null;
	
	// for Sync Pattern
	int[] pattern = {-17,-51,-102,-141,-158,-141,-96,-40,5,11,
					-12,-57,-113,-164,-237,-315,-377,-400,-383,-327,
					-282,-304,-372,-445,-462,-400,-299,-192,-147,-152,
					-175,-169,-124,-62,-29,-45,-85,-119,-119,-74,
					-34,-34,-85,-164,-225,-237,-192,-119,-51,-6,
					-12,-51,-113,-169,-214,-231,-203,-152,-96,-62,
					-74,-147,-242,-338,-400,-383,-293,-152,5,129,
					174,140,44,-85,-197,-265,-265,-214,-135,-57,
					-12,-12,-51,-119,-175,-220,-214,-158,-68,33,
					123,174,185,157,101,44,-6,-74,-130,-175};
	int pattern_length = 100;
	
	
	public AudioFingerprint(Context context, Handler handler, Timer timer) {
		mContext = context;
		mHandler = handler;
		mTimer = timer;
		mState = STATE_NONE;
	}
	
	public void initialize() {
		// initialize arguments
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
		sampling_time = Integer.parseInt(pref.getString("sampling_time", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_TIME)));
		delayed_time = 	Integer.parseInt(pref.getString("delayed_time", Integer.toString(AudioFingerprint.DEFAULT_DELAYED_TIME)));
    	redundant_time = Integer.parseInt(pref.getString("redundant_time", Integer.toString(AudioFingerprint.DEFAULT_REDUNDANT_TIME)));
    	sampling_rate = Integer.parseInt(pref.getString("sampling_rate", Integer.toString(AudioFingerprint.DEFAULT_SAMPLING_RATE)));
    	frame_length =  Integer.parseInt(pref.getString("frame_length", Integer.toString(AudioFingerprint.DEFAULT_FRAME_LENGTH)));
    	band_length = 	Integer.parseInt(pref.getString("band_length", Integer.toString(AudioFingerprint.DEFAULT_BAND_LENGTH)));
		
		// initialize bufferSize
		int minSize = AudioRecord.getMinBufferSize(sampling_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		double nSample = sampling_rate * ((double) (sampling_time + 2*redundant_time))/1000f;
		bufferSize = (int) Math.ceil(nSample);
		if (bufferSize < minSize) 
			bufferSize = minSize;
		
		// nFrame, nBand
		nFrame = (int) Math.ceil(sampling_rate*sampling_time/1000f/frame_length);
		nBand = (int) Math.ceil(((double) frame_length)/band_length);
		AudioFingerprint.fingerprintBits = (nFrame-1)*(nBand-1);
		
		// sample_start, sample_end
		sample_start = Math.round(sampling_rate * redundant_time / 1000f);
		sample_end = Math.round(sampling_rate * (redundant_time+sampling_time) / 1000f);
		
		// initialize AudioRecord
		try {
			mAudioRecord = new AudioRecord( MediaRecorder.AudioSource.MIC, sampling_rate, 
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize*2 );
		} catch (Exception e) {
			Log.e(TAG, "Unable to initialize AudioRecord instance");
			postStatus(TAG + ": Unable to initialize AudioRecord instance");
		}
		
		// Finger-print Array
		mHashMap = new HashMap<String, byte[][]>(20);
		
		// initialize pattern_matching_pos array
		for (int i=0; i<NUMBER_OF_MATCHING_POSITIONS; i++)
			pattern_matching_pos[i] = 0;
		
		// Inform the initialized state of AudioRecord 
		if (D) postStatus(TAG + ": State of AudioRecord (0 = UNINITIALIZED) is " + Integer.toString(mAudioRecord.getState()) );
	}
	
	public void setArguments(int x1, int x2, int x3, int x4, int x5, int x6) {
		// Firstly, cancel() the current state
		cancel();
		
		sampling_time = x1;
		delayed_time = x2;
		redundant_time = x3;
		sampling_rate = x4;
		frame_length = x5;
		band_length = x6;
		
		// initialize bufferSize
		int minSize = AudioRecord.getMinBufferSize(sampling_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		double nSample = sampling_rate * ((double) (sampling_time + 2*redundant_time))/1000f;
		bufferSize = (int) Math.ceil(nSample);
		if (bufferSize < minSize) 
			bufferSize = minSize;
		
		// nFrame, nBand
		nFrame = (int) Math.ceil(sampling_rate*sampling_time/1000f/frame_length);
		nBand = (int) Math.ceil(((double) frame_length)/band_length);
		AudioFingerprint.fingerprintBits = (nFrame-1)*(nBand-1);
		
		// sample_start, sample_end
		sample_start = Math.round(sampling_rate * redundant_time / 1000f);
		sample_end = Math.round(sampling_rate * (redundant_time+sampling_time) / 1000f);
		
		// initialize AudioRecord
		try {
			mAudioRecord = new AudioRecord( MediaRecorder.AudioSource.MIC, sampling_rate, 
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize*2 );
		} catch (Exception e) {
			Log.e(TAG, "Unable to initialize AudioRecord instance");
			postStatus(TAG + ": Unable to initialize AudioRecord instance");
		}
		
		// Finger-print Array
		mHashMap = new HashMap<String, byte[][]>(20);
		
		// Inform the initialized state of AudioRecord 
		if (D) postStatus(TAG + ": State of AudioRecord (0 = UNINITIALIZED) is " + Integer.toString(mAudioRecord.getState()) );
	}
	
	public String getArgumentsString() {
		String s = "Sampling Time = " + Integer.toString(sampling_time)
				+ "\nDelayed Time = " + Integer.toString(delayed_time)
				+ "\nRedundant Time = " + Integer.toString(redundant_time)
				+ "\nSampling Rate = " + Integer.toString(sampling_rate)
				+ "\nFrame Length = " + Integer.toString(frame_length)
				+ "\nBand Length = " + Integer.toString(band_length)
				+ "\nNumber of Frames = " + Integer.toString(nFrame)
				+ "\nNumber of Bands = " + Integer.toString(nBand);
		return s;
	}
	
	public void cancel() {
		audioBuffer = null;
		mAudioRecord.release();
		mAudioRecord = null;
		setState(STATE_NONE);
		if (mRecordThread != null)
			mRecordThread = null;
	}
	
	public int getState() {
		return mState;
	}
	
	private void setbackTitle(String s) {
		mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_RECORDING_STATE_CHANGE, s).sendToTarget();
	}
	
	private void setState(int state) {
		mState = state;
		setbackTitle(null);
	}
	
	// Create a RecordThread to record at the time of uptimeMillis
	public long startSampling(long uptimeMillis) {
		if (uptimeMillis == 0)
			uptimeMillis = System.currentTimeMillis() + delayed_time - redundant_time;
		
		mRecordThread = new RecordThread(uptimeMillis);
		mRecordThread.start();
		return uptimeMillis;
	}
	
	private void postStatus(String status) {
    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_POST);
        Bundle bundle = new Bundle();
        bundle.putString(AdhocPairingActivity.STATUS, status);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
	
	// Extract finger-print from the recorded audio sample
	/*private byte[][] extractFingerprint(int shiftTime) {
		
		// determine the start and end point in the audioBuffer array
		int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
		int start = sample_start + shiftSample;
		int end = sample_end + shiftSample;
		if (start<0 || end>bufferSize) {
			postStatus(TAG + ": access out of recorded range, shiftTime=" + Integer.toString(shiftTime));
			return null;
		}
		
		// copy the sampled audio sequence
		int n = end - start;
		short[] audioSequence = new short[n];
		for (int i=0; i<n; i++)
			audioSequence[i] = audioBuffer[i+start];
		
		// for each frame, calculate FFT, then calculate energy
		double[][] energy = new double[nFrame][nBand];
		short[] frame = new short[frame_length];
		for (int i=0; i<nFrame; i++) {
			// Report the process
			setbackTitle("FFT " + Integer.toString(i) + "/" + Integer.toString(nFrame));
			
			// pick the frame i-th
			for (int j=0; j<frame_length; j++)
				if (i*frame_length+j < n)
					frame[j] = audioSequence[i*frame_length + j];
				else 
					frame[j] = 0;
			
			// FFT the frame i-th
			Complex[] complex = new Complex[frame_length];
			for (int j=0; j<frame_length; j++)
				complex[j] = new Complex(frame[j], 0f);
			complex = FFT.fft(complex);
			
			// calculate energy of each band
			double e = 0;
			for (int j=0; j<frame_length; j++)
				if ( j>0 && j%band_length==0 ) {
					energy[i][j/band_length-1] = e;
					e = complex[j].abs();
					if (j == frame_length-1)
						energy[i][j/band_length] = e;
				} else if (j == frame_length-1) {
					e += complex[j].abs();
					energy[i][j/band_length] = e;
				} else
					e += complex[j].abs();
		}
		// calculate finger-print matrix
		byte[][] finger = new byte[nFrame-1][nBand-1];
		for (int i=1; i<nFrame; i++)
			for (int j=0; j<nBand-1; j++)
				if ( (energy[i][j]-energy[i][j+1]) - 
						(energy[i-1][j]-energy[i-1][j+1]) > 0 )
					finger[i-1][j] = 1;
				else
					finger[i-1][j] = 0;
		return finger;
	}*/
	
	public void calculateFingerprint(int shiftTime) {
		if (mComputingThread != null) return;
		mComputingThread = new ComputingThread(shiftTime, shiftTime);
		mComputingThread.start();
	}
	
	public void calculateFPwithAllPossibleShiftTime() {
    	int min = getMinShiftTime();
    	int max = getMaxShiftTime();
    	if (mComputingThread != null) return;
		mComputingThread = new ComputingThread(min, max);
		mComputingThread.start();
    }
	
	public void finishCalculating(int minShiftTime, int maxShiftTime) {
		mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_FINISH_CALCULATING_FP, minShiftTime, maxShiftTime).sendToTarget();
	}
	
	/*public int calculateHammingDistance(byte[][] f, int shiftTime) {
		byte[][] fp = extractFingerprint(shiftTime);
		int count = 0;
		for (int i=0; i<nFrame-1; i++)
			for (int j=0; j<nBand-1; j++)
				if (fp[i][j] != f[i][j]) count++;
		return count;
	}*/
	
	public int calculateHammingDistance(byte[] f, int shiftTime) {
		if (f.length != fingerprintBits) {
			Log.e(TAG, "Illegal number of bits in the finger-print");
			postStatus(TAG + ": ERROR f.length != fingerprintBits");
			return -1;
		}
		byte[][] fp = getFingerprint(shiftTime);
		if (fp == null) 
			return -1;
		int count = 0;
		for (int i=0; i<nFrame-1; i++)
			for (int j=0; j<nBand-1; j++)
				if (fp[i][j] != f[i*(nBand-1) + j]) count++;
		return count;
	}
	
	public byte[][] getFingerprint(int shiftTime) {
		return mHashMap.get(Integer.toString(shiftTime));
	}
	
	public boolean isDataReady() {
		return (audioBuffer != null);
	}
	
	// Return the minimum of possible shift time (millisecond)
	public int getMinShiftTime() {
		int shiftTime = 0;
		int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
		int start = sample_start + shiftSample;
		while (start >= 0) {
			shiftTime--;
			shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
			start = sample_start + shiftSample;
		}
		return shiftTime + 1;
	}
	
	// Return the maximum of possible shift time (millisecond)
	public int getMaxShiftTime() {
		int shiftTime = 0;
		int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
		int end = sample_end + shiftSample;
		while (end <= bufferSize) {
			shiftTime++;
			shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
			end = sample_end + shiftSample;
		}
		return shiftTime - 1;
	}
	
	public void saveRecordedData() {
		if (audioBuffer == null) { 
			postStatus("There are no recorded data to save.");
			return ;
		}
		// Create file name
		String device_name = ((AdhocPairingActivity) mContext).mBluetoothAdapter.getName();
		device_name = device_name.replace(' ', '-');
		Date date = new Date(recordingTime);
    	SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
    	String time_string = sdf.format(date);
    	String args_string = Integer.toString(sampling_rate) + "_" 
    						+ Integer.toString(sampling_time) + "_"
    						+ Integer.toString(redundant_time) + "_"
    						+ Integer.toString(frame_length) + "_"
    						+ Integer.toString(band_length);
		String filename = time_string + "_" + args_string + "_" + device_name + ".raw";
		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		String path_filename = path + "/AdhocPairing/" + filename;
		
		try {
			
			// Using FileWriter
			/*FileWriter f = new FileWriter(path_filename);
			for (int i=0; i<bufferSize; i++) {
				//if (i%10 == 0)
					//f.write("\n");
				f.write(Short.toString(audioBuffer[i]) + ",");
			}
			f.flush();
			f.close();*/
			
			// Using FileOutputStream
			File file = new File(path_filename);
			FileOutputStream fos = new FileOutputStream(file);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			DataOutputStream dos = new DataOutputStream(bos);
			for (int i=0; i<bufferSize; i++) {
				dos.writeShort(audioBuffer[i]);
			}
			dos.flush();
			dos.close();
			
		} catch(IOException e) {
			postStatus("Cannot open file to write.");
			return ;
		}
		postStatus("Data saved successfully.\n" + filename);
	}
	
	public void setAtomicRecordingTime(long time) {
		this.recordingTime = time;
	}
	
	// get matching score
	public int get_matching_score(int a, int b, int mode) {
		// mode: 1->match, 2->deletion, 3->insertion
		int score;
		if (mode == 1) {
			score = 65535 - Math.abs(a-b);
		} else {
			score = -5000; // penalty
		}
		return score;
	}
	
	// sync pattern
	public void sync_pattern() {
		if (mMatchingPatternThread != null) return;
		mMatchingPatternThread = new MatchingPatternThread();
		mMatchingPatternThread.start();
	}
	
	public void sync_pattern_run() {
		// check if there is recorded data
		if (audioBuffer == null) { 
			postStatus("There are no recorded data to sync.");
			return ;
		}
		// declare variables
		int sample_boundary = Math.round(sampling_rate * 2 * redundant_time / 1000f);
		short[][] H = new short[sample_boundary][pattern_length];
		short[][] trace = new short[sample_boundary][pattern_length];
		// initialize 
		for (int i=0; i<sample_boundary; i++) {
			H[i][0] = 0;
			trace[i][0] = 0;
		}
		for (int j=0; j<pattern_length; j++) {
			H[0][j] = 0;
			trace[0][j] = 0;
		}
		// calculate matching-score matrix H
		for (int i=1; i<sample_boundary; i++)
			for (int j=1; j<pattern_length; j++) {
				int max = 0;
				int score;
				// case 1 (Match)
				score = H[i-1][j-1] + get_matching_score(audioBuffer[i], pattern[j], 1);
				if (score > max) {
					H[i][j] = (short) score; 
					trace[i][j] = 1;
				}
				// case 2 (Deletion)
				score = H[i-1][j] + get_matching_score(audioBuffer[i], pattern[j], 2);
				if (score > max) {
					H[i][j] = (short) score; 
					trace[i][j] = 2;
				}
				// case 3 (Insertion)
				score = H[i][j-1] + get_matching_score(audioBuffer[i], pattern[j], 3);
				if (score > max) {
					H[i][j] = (short) score; 
					trace[i][j] = 3;
				}
			}
		// find the best matching positions
		// initialize flag array
		int[] flag = new int[sample_boundary];
		for (int i=0; i<sample_boundary; i++)
			flag[i] = 1;
		// calculate unavailable radius as 2.5% of sample number
		int radius = Math.round(0.025f * sample_boundary);
		// find
		for (int k=0; k<NUMBER_OF_MATCHING_POSITIONS; k++) {
			// get initial besti
			int besti = 0;
			while (flag[besti] == 0)
				besti++;
			// find besti
			int j = pattern_length - 1;
			for (int i=0; i<sample_boundary; i++)
				if (H[i][j] > H[besti][j] && flag[i] == 1) {
					besti = i;
				}
			// save besti
			pattern_matching_pos[k] = besti + 1;
			// set unavailable area around besti
			for (int i=besti-radius; i<besti+radius; i++)
				if (i>=0 && i<sample_boundary)
					flag[i] = 0;
		}
		// convert to millisecond
		for (int k=0; k<NUMBER_OF_MATCHING_POSITIONS; k++) {
			pattern_matching_pos[k] = Math.round(pattern_matching_pos[k] * 1000f / (float) sampling_rate);
		}
		// inform matching result
		String str = "Matching Positions: \n";
		for (int k=0; k<NUMBER_OF_MATCHING_POSITIONS; k++) {
			str += Integer.toString(pattern_matching_pos[k]) + " milliseconds (shiftTime=" + Integer.toString(pattern_matching_pos[k] - redundant_time) + ")";
			if (k < NUMBER_OF_MATCHING_POSITIONS-1)
				str += "\n";
		}
		postStatus(str); 
		
		// save recorded data first
		//saveRecordedData();
		
		// then save sync data
		// Create file name
		/*String device_name = ((AdhocPairingActivity) mContext).mBluetoothAdapter.getName();
		device_name = device_name.replace(' ', '-');
		Date date = new Date(recordingTime);
    	SimpleDateFormat sdf = new SimpleDateFormat("MMddHHmmss");
    	String time_string = sdf.format(date);
    	String args_string = Integer.toString(sampling_rate) + "_" 
    						+ Integer.toString(sampling_time) + "_"
    						+ Integer.toString(redundant_time) + "_"
    						+ Integer.toString(frame_length) + "_"
    						+ Integer.toString(band_length);
		String filename = time_string + "_" + args_string + "_" + device_name + "_syncpat.txt";
		
		try {
			//FileWriter f = new FileWriter("/sdcard/AdhocPairing/" + filename);
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			FileWriter f = new FileWriter(path + "/AdhocPairing/" + filename);
			for (int i=sync_start; i<sync_end; i++) {
				//if (i%10 == 0)
					//f.write("\n");
				f.write(Short.toString(audioBuffer[i]) + ",");
			}
			f.close();
		} catch(IOException e) {
			postStatus("Cannot open file to write.");
			return ;
		}
		postStatus("Sync Data saved successfully.\n" + filename);*/
	}
	
	// Return the pattern_matching_pos (milliseconds)
	public int getPatternMatchingShiftTime() {
		return pattern_matching_pos[0] - redundant_time;
	}
	
	public int getPatternMatchingShiftTime(int index) {
		return pattern_matching_pos[index] - redundant_time;
	}
	
	public void generateRandomShiftTime() {
		for (int i=0; i<NUMBER_OF_MATCHING_POSITIONS; i++) {
			random_matching_pos[i] = mRandom.nextInt(2 * redundant_time + 1) - redundant_time;
		}
	}
	
	public int getRandomShiftTime(int index) {
		return random_matching_pos[index];
	}
	
	private class RecordThread extends Thread {
		private long uptimeMillis;
		
		public RecordThread(long uptimeMillis) {
			this.uptimeMillis = uptimeMillis;
		}
		
		public void run() {
			try {
				long sleepTime = uptimeMillis - System.currentTimeMillis();
				if (sleepTime < 0) {
					postStatus(TAG + ": sleep time < 0 -> continue without sleeping");
					sleepTime = 0;
				}
				
				setbackTitle("waiting...");
				Thread.sleep(sleepTime);
				
				setState(STATE_RECORDING);
				mAudioRecord.startRecording();
			} catch (InterruptedException e) {
				Log.e(TAG + ".RecordThread", "sleep() interrupted");
				postStatus(TAG + ".RecordThread: sleep() interrupted");
				setState(STATE_NONE);
				return;
			} catch (IllegalStateException e) {
				Log.e(TAG + ".RecordThread", "cannot start Recording");
				postStatus(TAG + ".RecordThread: cannot start Recording");
				setState(STATE_NONE);
				return;
			}
			audioBuffer = new short[bufferSize];
			int nShort = mAudioRecord.read(audioBuffer, 0, bufferSize);
			postStatus(TAG + ".RecordThread: recorded " + 
					Integer.toString(nShort) + " shorts out of desired " + 
					Integer.toString(bufferSize) + " shorts");
			mAudioRecord.stop();
			setState(STATE_NONE);
		}
	}

	private class ComputingThread extends Thread {
		private int minShiftTime;
		private int maxShiftTime;
		private int shiftTime;
		
		public ComputingThread(int minShiftTime, int maxShiftTime) {
			this.minShiftTime = minShiftTime;
			this.maxShiftTime = maxShiftTime;
			this.shiftTime = minShiftTime;
		}
		
		public void run() {
			while (shiftTime <= maxShiftTime && getFingerprint(shiftTime)==null) {
				// determine the start and end point in the audioBuffer array
				int shiftSample = Math.round(sampling_rate * ((float) shiftTime) / 1000f);
				int start = sample_start + shiftSample;
				int end = sample_end + shiftSample;
				if (start<0 || end>bufferSize) {
					postStatus(TAG + ": access out of recorded range, shiftTime=" + Integer.toString(shiftTime));
					mComputingThread = null;
					return ;
				}
				
				// copy the sampled audio sequence
				int n = end - start;
				short[] audioSequence = new short[n];
				for (int i=0; i<n; i++)
					audioSequence[i] = audioBuffer[i+start];
				
				// for each frame, calculate FFT, then calculate energy
				double[][] energy = new double[nFrame][nBand];
				short[] frame = new short[frame_length];
				for (int i=0; i<nFrame; i++) {
					// Report the process
					String audio_status = Integer.toString(i) + "/" + Integer.toString(nFrame);
					if (maxShiftTime > minShiftTime)
						audio_status += ", " + Integer.toString(shiftTime-minShiftTime+1) + "/" + Integer.toString(maxShiftTime-minShiftTime+1);
					setbackTitle("FFT " + audio_status);
					
					// pick the frame i-th
					for (int j=0; j<frame_length; j++)
						if (i*frame_length+j < n)
							frame[j] = audioSequence[i*frame_length + j];
						else 
							frame[j] = 0;
					
					// apply windown function
					for (int j=0; j<frame_length; j++)
						frame[j] = (short) (FFT.window_hanning(j, frame_length) * (double) frame[j]);
					
					// FFT the frame i-th
					Complex[] complex = new Complex[frame_length];
					for (int j=0; j<frame_length; j++)
						complex[j] = new Complex(frame[j], 0f);
					complex = FFT.fft(complex);
					
					// calculate energy of each band
					double e = 0;
					for (int j=0; j<frame_length; j++)
						if ( j>0 && j%band_length==0 ) {
							energy[i][j/band_length-1] = e;
							e = complex[j].abs();
							if (j == frame_length-1)
								energy[i][j/band_length] = e;
						} else if (j == frame_length-1) {
							e += complex[j].abs();
							energy[i][j/band_length] = e;
						} else
							e += complex[j].abs();
				}
				// calculate finger-print matrix
				byte[][] finger = new byte[nFrame-1][nBand-1];
				for (int i=1; i<nFrame; i++)
					for (int j=0; j<nBand-1; j++)
						if ( (energy[i][j]-energy[i][j+1]) - 
								(energy[i-1][j]-energy[i-1][j+1]) > 0 )
							finger[i-1][j] = 1;
						else
							finger[i-1][j] = 0;
				
				// Ending the computing
				mHashMap.put(Integer.toString(shiftTime), finger);
				
				shiftTime++;
			}
			setbackTitle(null);
			mComputingThread = null;
			finishCalculating(minShiftTime, maxShiftTime);
		}
		
	}
	
	private class MatchingPatternThread extends Thread {
		
		public MatchingPatternThread() {
			// do nothing
		}
		
		public void run() {
			setbackTitle("matching pattern...");
			sync_pattern_run();
			setbackTitle(null);
			mMatchingPatternThread = null;
		}
	}
}
