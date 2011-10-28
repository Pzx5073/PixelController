/**
 * Copyright (C) 2011 Michael Vogt <michu@neophob.com>
 * Copyright (C) 2011 Rainer Ostendorf <mail@linlab.de>
 *
 * This file is part of PixelController.
 *
 * PixelController is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PixelController is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PixelController.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neophob.sematrix.output;

import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import artnet4j.ArtNet;
import artnet4j.packets.ArtDmxPacket;

import com.neophob.sematrix.properties.PropertiesHelper;

/**
 * The Class ArtnetDevice.
 *
 * @author michu
 * @author Rainer Ostendorf <mail@linlab.de>
 * 
 * TODO:
 * -support more universe
 */
public class ArtnetDevice extends OnePanelResolutionAwareOutput {

	private static final Logger LOG = Logger.getLogger(ArtnetDevice.class.getName());

	private static final int PIXELS_PER_DMX_UNIVERSE = 170;
	
	private int sequenceID;
	private int nrOfUniverse;
	private ArtNet artnet;
	private boolean initialized;
	
	private InetAddress targetAdress;

	/**
	 * 
	 * @param controller
	 */
	public ArtnetDevice(PropertiesHelper ph, PixelControllerOutput controller) {
		super(OutputDeviceEnum.ARTNET, ph, controller, 8);

		this.initialized = false;
		this.artnet = new ArtNet();				
		try {
			String ip = ph.getArtNetIp();
		    this.artnet.init();
		    this.artnet.start();
		    this.targetAdress = InetAddress.getByName(ip); 
		    		
		    this.nrOfUniverse = 1;
		    int bufferSize=xResolution*yResolution;
		    if (bufferSize > PIXELS_PER_DMX_UNIVERSE) {
		    	while (bufferSize > PIXELS_PER_DMX_UNIVERSE) {
		    		this.nrOfUniverse++;
		    		bufferSize -= PIXELS_PER_DMX_UNIVERSE;
		    	}
		    }
		    
		    this.initialized = true;
			LOG.log(Level.INFO, "ArtNet device initialized at {0}, using {1} universe and {2} pixels.", 
					new Object[] {this.targetAdress.toString(), this.nrOfUniverse, xResolution*yResolution});
		} catch (Exception e) {
			LOG.log(Level.WARNING, "failed to initialize ArtNet port:", e);
		}
	}
	
	/**
	 * 
	 * @param frameBuf
	 * @return
	 */
	private byte[] convertIntToByteBuffer(int[] frameBuf) {
		byte[] buffer = new byte[frameBuf.length*3];
		int ofs;
		for (int i = 0; i < frameBuf.length; i++) {
		    ofs = i*3;
			buffer[ofs++] = (byte) ((frameBuf[i]>>16) & 0xff);
			buffer[ofs++] = (byte) ((frameBuf[i]>>8)  & 0xff);
			buffer[ofs  ] = (byte) ( frameBuf[i]      & 0xff);
		}
		
		return buffer;
	}
	
    /* (non-Javadoc)
     * @see com.neophob.sematrix.output.Output#update()
     */
	@Override
	public void update() {
		if (this.initialized) {
			if (this.nrOfUniverse == 1) {
				sendBufferToArtnetReceiver(0, convertIntToByteBuffer(getTransformedBuffer()) );
			} else {
				int[] fullBuffer = getTransformedBuffer();				
				int remainingInt = fullBuffer.length;
				int ofs=0;
				for (int i=0; i<this.nrOfUniverse; i++) {
					int tmp=PIXELS_PER_DMX_UNIVERSE;
					if (remainingInt<PIXELS_PER_DMX_UNIVERSE) {
						tmp = remainingInt;
					}
					int[] buffer = new int[tmp];
					System.arraycopy(fullBuffer, ofs, buffer, 0, tmp);
					remainingInt-=tmp;
					ofs+=tmp;
					sendBufferToArtnetReceiver(i, convertIntToByteBuffer(buffer));
				}
			}
			
		}
	}


	/**
	 * send buffer to a dmx universe
	 * a DMX universe can address up to 512 channels - this means up to
	 * 170 RGB LED (510 Channels)
	 * 
	 * @param artnetReceiver
	 * @param frameBuf
	 */
	private void sendBufferToArtnetReceiver(int universeId, byte[] buffer) {
		ArtDmxPacket dmx = new ArtDmxPacket();
		
		//parameter: int subnetID, int universeID
		dmx.setUniverse(0, universeId);
		dmx.setSequenceID(sequenceID % 255);
		dmx.setDMX(buffer, buffer.length);
		this.artnet.unicastPacket(dmx, this.targetAdress);
		this.sequenceID++;
	}

	@Override
	public void close()	{
	    if (initialized) {
	        this.artnet.stop();   
	    }	    
	}
}

