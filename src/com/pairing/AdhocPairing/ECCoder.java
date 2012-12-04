package com.pairing.AdhocPairing;

import java.util.Random;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.codec.reedsolomon.GenericGF;
import com.codec.reedsolomon.ReedSolomonDecoder;
import com.codec.reedsolomon.ReedSolomonEncoder;
import com.codec.reedsolomon.ReedSolomonException;

public class ECCoder {
	
	public static final int[][] defaultRSParameters = {
			/*  0 */ {     -1,  -1, -1, 0},
		    /*  1 */ {     -1,  -1, -1, 2},
		    /*  2 */ {    0x7,   1,  1, 4},
		    /*  3 */ {    0xb,   1,  1, 8},
		    /*  4 */ {   0x13,   1,  1, 16},
		    /*  5 */ {   0x25,   1,  1, 32},
		    /*  6 */ {   0x43,   1,  1, 64},
		    /*  7 */ {   0x89,   1,  1, 128},
		    /*  8 */ {  0x187, 112, 11, 256}, /* Based on CCSDS codec */
		    /*  9 */ {  0x211,   1,  1, 512},
		    /* 10 */ {  0x409,   1,  1, 1024},
		    /* 11 */ {  0x805,   1,  1, 2048},
		    /* 12 */ { 0x1053,   1,  1, 4096},
		    /* 13 */ { 0x201b,   1,  1, 8192},
		    /* 14 */ { 0x4443,   1,  1, 16384},
		    /* 15 */ { 0x8003,   1,  1, 32768},
		    /* 16 */ {0x1100b,   1,  1, 65536},
	}; // gfpoly, fcr, prim -> use gfpoly only, from the Reed-Solomon library by Phil Karn.
	
	public static final int defaultRSParamCount = 17;
	
	public static final int COMMIT_MODE = 0;  // Get in finger-print then produce plain-word and delta 
	public static final int DECOMMIT_MODE = 1;  // Get in delta and finger-print then produce plain-word
	public static final int TEST_DECOMMIT_MODE = 2;  // Get in plain-word and delta, able to do decommit many times according to input finger-print for testing purpose
	
	// Basic parameters and tools for ECCoder instance
	private int mode;
	private int n, m, symsize;
	private GenericGF gf;
	private ReedSolomonDecoder decoder;
	private ReedSolomonEncoder encoder;
	// Data objects for ECCoder instance 
	public int[] plain_word;
	public int[] delta;
	public int[] code_word;
	public byte[] finger;
	
	// Tool object
	private Handler mHandler;
	
	// Constructor
	public ECCoder(int n, int m, int symsize, int mode, Handler handler) {
		this.n = n;
		this.m = m;
		this.symsize = symsize;
		this.mode = mode;
		this.gf = new GenericGF(defaultRSParameters[symsize][0], defaultRSParameters[symsize][3]);
		this.decoder = new ReedSolomonDecoder(this.gf);
		this.encoder = new ReedSolomonEncoder(this.gf);
		
		this.plain_word = null;
		this.delta = null;
		this.code_word = null;
		this.finger = null;
		
		this.mHandler = handler;
	}
	
	// Create a plain_word (m elements) and the delta (n elements) 
	public int commit(byte[] codewordBytes) {
		// Check working mode
		if (mode != COMMIT_MODE) {
			return -1;
		}
				
		// get a random word
		plain_word = getRandomWord(m);
		code_word = new int[n];
		for (int i=0; i<m; i++)
			code_word[i] = plain_word[i];
		encoder.encode(code_word, n-m);
		
		// change the array of bytes to array of ints
		int[] codewordInts = new int[n];
		for (int i=0; i<n; i++)
			codewordInts[i] = (int) codewordBytes[i];
		
		// calculating delta array
		delta = new int[n];
		for (int i=0; i<n; i++)
			delta[i] = GenericGF.addOrSubtract(codewordInts[i], code_word[i]);
		
		return 1;
	}
	
	// Create an array of m random ints
	private int[] getRandomWord(int m) {
		int limit = defaultRSParameters[symsize][3];
		Random random = new Random();
		int[] w = new int[m];
		for (int i=0; i<m; i++)
			w[i] = random.nextInt(limit);
		return w;
	}
	
	// Calculating code_word array and plain_word array
	public int decommit(byte[] codewordBytes) {
		// Check working mode
		if (mode != DECOMMIT_MODE) {
			return -1;
		}
		
		// If there is no delta, error
		if (delta == null) return -1;
		
		// change the array of bytes to array of ints
		int[] codewordInts = new int[n];
		for (int i=0; i<n; i++)
			codewordInts[i] = (int) codewordBytes[i];
		
		// calculating the code_word array
		code_word = new int[n];
		for (int i=0; i<n; i++)
			code_word[i] = GenericGF.addOrSubtract(codewordInts[i], delta[i]);
		// decode code_word array 
		try {
			decoder.decode(code_word, n-m);
		} catch (ReedSolomonException rse) {
			String str = "[error in decoding] " + rse.getMessage();
			postStatus(str);
			Log.d("ECC", str);
			return -1;
		}
		// Create the plain_word
		plain_word = new int[m];
		for (int i=0; i<m; i++)
			plain_word[i] = code_word[i];
		
		return 1;
	}
	
	// Insert delta array into "this" and call the upper decommit() function
	public int decommit(byte[] codewordBytes, int[] delta) {
		// Check working mode
		if (mode != DECOMMIT_MODE) {
			return -1;
		}
		
		putDelta(delta);
		return decommit(codewordBytes);
	}
	
	// Put the delta array into ECCoder with DECOMMIT_MODE
	public int putDelta(int[] delta) {
		if (mode != DECOMMIT_MODE) 
			return -1;
		this.delta = delta;
		return 1;
	}
	
	// Compare the inner plain_word with the pain word from parameter
	public String comparePlainWord(int[] plain) {
		if (mode != DECOMMIT_MODE) 
			return "Not in DECOMMIT_MODE.";
		int similar = 0;
		for (int i=0; i<m; i++)
			if (plain[i] == plain_word[i])
				similar++;
		String str_sim = Integer.toString(similar);
		String str_m = Integer.toString(m);
		double percent = (double) similar * 100.0 / (double) m;
		String str_per = Double.toString(percent);
		String str = "There are " + str_sim + " similar numbers out of " + str_m 
						+ " numbers, " + str_per + "% matched.";
		return str;
	}
	
	public int getMode() {
		return mode;
	}
	
	public int[] getPlainWord() {
		return plain_word;
	}
	
	public int[] getDelta() {
		return delta;
	}
	
	private void postStatus(String status) {
    	Message msg = mHandler.obtainMessage(AdhocPairingActivity.MESSAGE_POST);
        Bundle bundle = new Bundle();
        bundle.putString(AdhocPairingActivity.STATUS, status);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
	
	// Put plain word and delta array into itself with TEST_DECOMMIT_MODE
	public int putPlainDelta(int[] plain_word, int[] delta) {
		if (mode != TEST_DECOMMIT_MODE) 
			return -1;
		this.plain_word = plain_word;
		this.delta = delta;
		return 1;
	}
	
	// Get n
	public int getN() {
		if (mode != TEST_DECOMMIT_MODE) 
			return -1;
		return n;
	}
	
	// Get m
	public int getM() {
		if (mode != TEST_DECOMMIT_MODE) 
			return -1;
		return m;
	}
	
	// Get symsize
	public int getSymsize() {
		if (mode != TEST_DECOMMIT_MODE) 
			return -1;
		return symsize;
	}
	
	// Get the finger-print as array of bytes
	public byte[] getFinger() {
		// Return the finger array if it exists
		if (finger != null)
			return finger;
		
		// Encode the plain_word
		code_word = new int[n];
		for (int i=0; i<m; i++)
			code_word[i] = plain_word[i];
		encoder.encode(code_word, n-m);
		
		// Substract code-word by delta
		int[] codewordInts = new int[n];
		for (int i=0; i<n; i++)
			codewordInts[i] = GenericGF.addOrSubtract(delta[i], code_word[i]);
		
		// Convert the array of ints to array of bytes
		finger = new byte[n];
		for (int i=0; i<n; i++)
			finger[i] = (byte) codewordInts[i];
		
		// Return
		return finger;
	}
	
	// Compare finger-print
	public String compareFinger(byte[] finger) {
		if (mode != TEST_DECOMMIT_MODE) 
			return "Not in TEST_DECOMMIT_MODE.";
		int similar = 0;
		for (int i=0; i<n; i++)
			if (this.finger[i] == finger[i])
				similar++;
		String str_sim = Integer.toString(similar);
		String str_n = Integer.toString(n);
		double percent = (double) similar * 100.0 / (double) n;
		String str_per = Double.toString(percent);
		String str = "There are " + str_sim + " similar bits out of " + str_n 
						+ " bits, " + str_per + "% matched.";
		return str;
	}
	
}
