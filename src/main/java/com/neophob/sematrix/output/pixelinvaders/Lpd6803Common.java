package com.neophob.sematrix.output.pixelinvaders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Adler32;

import com.neophob.sematrix.output.OutputHelper;
import com.neophob.sematrix.output.gamma.RGBAdjust;
import com.neophob.sematrix.output.tpm2.Tpm2NetProtocol;
import com.neophob.sematrix.properties.ColorFormat;

public abstract class Lpd6803Common {
	
	/** The log. */
	private static final Logger LOG = Logger.getLogger(Lpd6803Common.class.getName());

	/** number of leds horizontal<br> TODO: should be dynamic, someday. */
	public static final int NR_OF_LED_HORIZONTAL = 8;

	/** number of leds vertical<br> TODO: should be dynamic, someday. */
	public static final int NR_OF_LED_VERTICAL = NR_OF_LED_HORIZONTAL;

	/** The Constant BUFFERSIZE. */
	protected static final int BUFFERSIZE = NR_OF_LED_HORIZONTAL*NR_OF_LED_VERTICAL;

	/** The Constant START_OF_CMD. */
	protected static final byte START_OF_CMD = 0x01;
	
	/** The Constant CMD_SENDFRAME. */
	protected static final byte CMD_SENDFRAME = 0x03;
	
	/** The Constant CMD_PING. */
	protected static final byte CMD_PING = 0x04;

	/** The Constant START_OF_DATA. */
	protected static final byte START_OF_DATA = 0x10;
	
	/** The Constant END_OF_DATA. */
	protected static final byte END_OF_DATA = 0x20;

	protected static Adler32 adler = new Adler32();
	
	/** The connection error counter. */
	protected int connectionErrorCounter;
	
	/** map to store checksum of image. */
	protected Map<Byte, Long> lastDataMap = new HashMap<Byte, Long>();

	/** correction map to store adjustment data, contains offset and correction data */
	protected Map<Integer, RGBAdjust> correctionMap = new HashMap<Integer, RGBAdjust>();

	protected boolean initialized;
	
	/** The ack errors. */
	protected long ackErrors = 0;

	
	/**
	 * return connection state of lib.
	 *
	 * @return whether a lpd6803 device is connected
	 */
	public boolean connected() {
		return initialized;
	}	

	/**
	 * wrapper class to send a RGB image to the lpd6803 device.
	 * the rgb image gets converted to the lpd6803 device compatible
	 * "image format"
	 *
	 * @param ofs the image ofs
	 * @param data rgb data (int[64], each int contains one RGB pixel)
	 * @param colorFormat the color format
	 * @return nr of sended update frames
	 */
	public int sendRgbFrame(byte ofs, int[] data, ColorFormat colorFormat) {
		if (data.length!=BUFFERSIZE) {
			throw new IllegalArgumentException("data lenght must be 64 bytes!");
		}
		
		int ofsAsInt = ofs;
		if (correctionMap.containsKey(ofsAsInt)) {
			RGBAdjust correction = correctionMap.get(ofsAsInt);
			return sendFrame(ofs, OutputHelper.convertBufferTo15bit(data, colorFormat, correction));			
		}

		return sendFrame(ofs, OutputHelper.convertBufferTo15bit(data, colorFormat));
	}
	


	
	/**
	 * send a frame to the active lpd6803 device.
	 *
	 * @param ofs - the offset get multiplied by 32 on the arduino!
	 * @param data byte[3*8*4]
	 * @return nr of sended frames
	 * @throws IllegalArgumentException the illegal argument exception
	 */
	public int sendFrame(byte ofs, byte data[]) throws IllegalArgumentException {		
		if (data.length!=128) {
			throw new IllegalArgumentException("data lenght must be 128 bytes!");
		}
		
		byte[] imagePayload = Tpm2NetProtocol.createCmdPayload(data);

		int returnValue = 0;
		//send frame one
		if (didFrameChange(ofs, imagePayload)) {
			
			if (sendData(imagePayload)) {
				returnValue++;
			} else {
				//in case of an error, make sure we send it the next time!
				lastDataMap.put(ofs, 0L);
			}
		}
		
		return returnValue;
	}
	
	/**
	 * send a serial ping command to the arduino board.
	 * 
	 * @return wheter ping was successfull (arduino reachable) or not
	 */
	public boolean ping() {
		byte[] pingPayload = Tpm2NetProtocol.createCmdPayload(new byte[] {CMD_PING});

		try {
			writeData(pingPayload);
			return waitForAck();			
		} catch (Exception e) {
			return false;
		}
	}
	
	/**
	 * Send serial data.
	 *
	 * @param cmdfull the cmdfull
	 * @return true, if successful
	 */
	protected boolean sendData(byte cmdfull[]) {
		try {
			writeData(cmdfull);

			//just write out debug output from the microcontroller
			byte[] replyFromController = getReplyFromController();
			if (replyFromController!=null && replyFromController.length > 0) {                        
				LOG.log(Level.INFO, "<<< ("+Arrays.toString(replyFromController)+")");
			}  			
			return true;
		} catch (Exception e) {
			LOG.log(Level.WARNING, "sending serial data failed: {0}", e);
		}
		return false;
	}

	
	/**
	 * 
	 * @return
	 */
	public int getConnectionErrorCounter() {
		return connectionErrorCounter;
	}

	/**
	 * 
	 * @param cmdfull
	 * @throws WriteDataException
	 */
	protected abstract void writeData(byte[] cmdfull) throws WriteDataException;
	
	/**
	 * 
	 * @return
	 */
	protected abstract boolean waitForAck();
	
	/**
	 * get all data which are sent back from the controller
	 * 
	 * @return
	 */
	protected abstract byte[] getReplyFromController();
	
	/**
	 * get md5 hash out of an image. used to check if the image changed
	 *
	 * @param ofs the ofs
	 * @param data the data
	 * @return true if send was successful
	 */
	protected boolean didFrameChange(byte ofs, byte data[]) {
		adler.reset();
		adler.update(data);
		long l = adler.getValue();
		
		if (!lastDataMap.containsKey(ofs)) {
			//first run
			lastDataMap.put(ofs, l);
			return true;
		}
		
		if (lastDataMap.get(ofs) == l) {
			//last frame was equal current frame, do not send it!
			//log.log(Level.INFO, "do not send frame to {0}", addr);
			return false;
		}
		//update new hash
		lastDataMap.put(ofs, l);
		return true;
	}


	
    /**
	 * Sleep wrapper.
	 *
	 * @param ms the ms
	 */
	protected void sleep(int ms) {
		try {
			Thread.sleep(ms);
		}
		catch(InterruptedException e) {
		}
	}


}