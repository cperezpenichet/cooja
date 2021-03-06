/*
 * Copyright (c) 2018, Uppsala University.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.mspmote.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.*;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.RadioConnection;

import org.contikios.cooja.interfaces.Radio.RadioEvent;
import org.contikios.cooja.mspmote.MspMoteTimeEvent;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.radiomediums.UDGMCA;

import se.sics.mspsim.chip.BackscatterTXRadio;
import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.RFListener;
import se.sics.mspsim.core.Chip;
import se.sics.mspsim.core.OperatingModeListener;

import org.contikios.cooja.RadioConnection;


/**
 * MSPSim 802.15.4 tag radio to COOJA wrapper.
 *
 * @author George Daglaridis
 * @author Carlos Perez Penichet
 */

@ClassDescription("IEEE 802.15.4 Tag")
public class Msp802154Tag extends Msp802154Radio {
  private static Logger logger = Logger.getLogger(Msp802154Tag.class);
  
  private final BackscatterTXRadio radio;

  private boolean isListeningCarrier = false;
  private boolean isReceiving = false;
  
//  public int FREQSHIFT = 2;


  
  /* Keeps a record of the transmitted power from the tag */
  private Hashtable<Integer, Hashtable<RadioConnection, Double>> tagTXPower = 
                                                new Hashtable<Integer, Hashtable<RadioConnection, Double>>();
  
  private ReentrantLock lock = new ReentrantLock();
//  private MyReentrantLock lock = new MyReentrantLock();

  public Msp802154Tag(Mote m) {
    super(m);
    this.FREQSHIFT = 2;
    this.radio = this.mote.getCPU().getChip(BackscatterTXRadio.class);
    
    if (radio == null) {
        throw new IllegalStateException("Mote is not equipped with a tag");
    }
    
    radio.addRFListener(new RFListener() {
      int len = 0;
      int expMpduLen = 0;
      byte[] buffer = new byte[127 + 6];
      final private byte[] syncSeq = {0,0,0,0,0x7A};
      
      public void receivedByte(byte data) {
        if (!isTransmitting()) {
          lastEvent = RadioEvent.TRANSMISSION_STARTED;
          lastOutgoingPacket = null;
          isTransmitting = true;
          len = 0;
          expMpduLen = 0;
          setChanged();
          notifyObservers();
          /*logger.debug("----- 802.15.4 TRANSMISSION STARTED -----");*/
        }

        /* send this byte to all nodes */
        lastOutgoingByte = data;
        lastEvent = RadioEvent.CUSTOM_DATA_TRANSMITTED;
        setChanged();
        notifyObservers();

        if (len < buffer.length)
          buffer[len] = data;

        len ++;

        if (len == 5) {
          isSynchronized = true;
          for (int i=0; i<5; i++) {
            if (buffer[i] != syncSeq[i]) {
              // this should never happen, but it happens
              logger.error(String.format("Bad outgoing sync sequence %x %x %x %x %x", buffer[0], buffer[1], buffer[2], buffer[3], buffer[4]));
              isSynchronized = false;
              break;
            }
          }
        }
        else if (len == 6) {
//          System.out.println("## CC2420 Packet of length: " + data + " expected...");
          expMpduLen = data & 0xFF;
          if ((expMpduLen & 0x80) != 0) {
            logger.error("Outgoing length field is larger than 127: " + expMpduLen);
          }
        }

        if (((expMpduLen & 0x80) == 0) && len == expMpduLen + 6 && isSynchronized) {
          lastOutgoingPacket = CC2420RadioPacketConverter.fromCC2420ToCooja(buffer);
          if (lastOutgoingPacket != null) {
            lastEvent = RadioEvent.PACKET_TRANSMITTED;  
            //logger.debug("----- 802.15.4 PACKET TRANSMITTED -----");
            setChanged();
            notifyObservers();
          }
          finishTransmission();
        }
      }
    }); /* addRFListener */
    
    radio.addOperatingModeListener(new OperatingModeListener() {
      public void modeChanged(Chip source, int mode) {
        if ( (mode == BackscatterTXRadio.MODE_TX_ON) || 
             (mode == BackscatterTXRadio.MODE_RX_ON)) {
          lastEvent = RadioEvent.HW_ON;
          setChanged();
          notifyObservers();
//          isTransmitting = true;
//        lastEvent = RadioEvent.TRANSMISSION_STARTED;
//          setChanged();
//          notifyObservers();
          return;
        }
      
        if ((mode == BackscatterTXRadio.MODE_TXRX_OFF)) {
          lastEvent = RadioEvent.HW_OFF;
            setChanged();
            notifyObservers();
            return;
          }
        }
    });    
  }

  public boolean isBackscatterTag() {
      return true;
  }
  
  public boolean isListeningCarrier() {
      return isListeningCarrier;
  }
  
  @Override
  public boolean isReceiving() {
	  return isReceiving;
   }
  
  /*
   * The channel of the tag returns 
   * -1 skipping this way the usual
   * channel check and using our own.
   */
  public int getChannel() {
    return -1;
  }

  @Override
  public void signalCarrierReceptionStart() {
    isListeningCarrier = true;
    lastEvent = RadioEvent.CARRIER_LISTENING_STARTED;
    setChanged();
    notifyObservers();
  }
  
  @Override
  public void signalCarrierReceptionEnd() {
    isListeningCarrier = false;
    isInterfered = false;
    lastEvent = RadioEvent.CARRIER_LISTENING_STOPPED;
    setChanged();
    notifyObservers();
  }
  
  /* Concerns the start of the carrier listening */
  @Override
  public void signalReceptionStart() {
	  isReceiving = true;
	  lastEvent = RadioEvent.RECEPTION_STARTED;
    setChanged();
    notifyObservers();
  }
  
  /* Concerns the end of the carrier listening */
  @Override
  public void signalReceptionEnd() {
	  isReceiving = false;
    lastEvent = RadioEvent.RECEPTION_FINISHED;
    setChanged();
    notifyObservers();
  }
  
  public void interfereAnyReception() {
    isInterfered = false;
    lock.lock();
    try {
      if (tagTXPower !=null) {
        Enumeration<Integer> channels = tagTXPower.keys();
        while (channels.hasMoreElements()) {
          Integer channel = (Integer)channels.nextElement();
          if (interfere_anyway || this.getNumberOfConnectionsFromChannel(channel) >= 2) {
        	  interfere_anyway = false;
            isInterfered = true;
            lastEvent = RadioEvent.RECEPTION_INTERFERED;
            setChanged();
            notifyObservers();
          }
        }
      }
    } finally {
      lock.unlock();
    }
        
  }
  
  public void updateTagTXPower(RadioConnection conn) {
    lock.lock();
    try {
      int tagTXChannel = conn.getSource().getChannel()+FREQSHIFT;
      if (tagTXPower.get(tagTXChannel) != null) {
        tagTXPower.get(tagTXChannel).remove(conn);
        if (tagTXPower.get(tagTXChannel).isEmpty()) {
          tagTXPower.remove(tagTXChannel);
        }
      } 
    } finally {
      lock.unlock();
    }
    
  }
  
  public void putTagTXPower(int channel, RadioConnection conn, double tagCurrentTXPower) {
    lock.lock();
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        tagTXPower.get(channel).put(conn, tagCurrentTXPower);
      } else {
        Hashtable<RadioConnection, Double> txPower = new Hashtable<RadioConnection, Double>();
        txPower.put(conn, tagCurrentTXPower);
        tagTXPower.put(channel, txPower);
      }
    } finally {
      lock.unlock();
    }
    
  }
  
  public double getTagCurrentOutputPower(Radio radio, int channel) {
    lock.lock();
    /* When there is no entry in the Hashtable 
     * return something really small */
    double power = Double.NEGATIVE_INFINITY;
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
        while (conns.hasMoreElements()) {
          RadioConnection conn = (RadioConnection)conns.nextElement();
          if (conn.getSource() == radio) {
            power = tagTXPower.get(channel).get(conn);
          }
        }
      }
    } finally {
      lock.unlock();
    }
    
    return power;
        
  }
  
  public double getTagCurrentOutputPowerMax(int channel) {
    double maxValue = 0.0;

    lock.lock();
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        maxValue = Collections.max(tagTXPower.get(channel).values());
      } else {
        /* When there is no entry in the Hashtable 
         * return something really small */
        maxValue = Double.NEGATIVE_INFINITY;
      }
    } finally {
      lock.unlock();
    }

    return maxValue;

  }
  
  public RadioConnection getConnectionFromMaxOutputPower(int channel) {
    RadioConnection activeConn = null;
    
    lock.lock();
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        double maxPower = this.getTagCurrentOutputPowerMax(channel);
        Enumeration<RadioConnection> carrierConns = tagTXPower.get(channel).keys();
        while (carrierConns.hasMoreElements()) {
          RadioConnection carrierConn = (RadioConnection)carrierConns.nextElement();
          if (tagTXPower.get(channel).get(carrierConn) == maxPower) {
            activeConn = carrierConn;
          }
        }
      }
    } finally {
      lock.unlock();
    }
    
    return activeConn;

  }
  
  public boolean isTXChannelFromActiveTransmitter(int channel) {
    boolean isTXChannelFromActiveTransmitter = false;
    
    lock.lock();
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
       Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
        while (conns.hasMoreElements()) {
          RadioConnection activeConn = (RadioConnection)conns.nextElement();
          if (!activeConn.getSource().isGeneratingCarrier()) {
           isTXChannelFromActiveTransmitter = true;
           break;
          }
        }
      }
    } finally {
      lock.unlock();
    }
    
    return isTXChannelFromActiveTransmitter;
  
  }
  
  public int getNumberOfConnectionsFromChannel(int channel) {
    int size = 0;
    
    lock.lock();
    try {
      if (channel >=0 && tagTXPower.containsKey(channel)) {
        size = tagTXPower.get(channel).size();
      } 
    } finally {
      lock.unlock();
    }
    
    return size;
        
  }
  
  public boolean isTagTXPowersEmpty() {
    if (tagTXPower.isEmpty()) {
      return true;
    } else {
      return false;
    }
    
  }
   
  @Override
  public boolean isRadioOn() {
    return (radio.getMode() != BackscatterTXRadio.MODE_TXRX_OFF);
//    return true;
  }
  
  
  public boolean canReceiveFrom(CustomDataRadio radio) {
	  if (radio.getClass() == Msp802154Radio.class) {
		  return true;
	  }
    return false;
  }
  
  public void receiveCustomData(Object data) {
	    if (!(data instanceof Byte)) {
	      logger.fatal("Bad custom data: " + data);
	      return;
	    }
	    lastIncomingByte = (Byte) data;

	    final byte inputByte;
	    if (isInterfered()) {
	      inputByte = (byte)0xFF;
	    } else {
	      inputByte = lastIncomingByte;
	    }
	    mote.getSimulation().scheduleEvent(new MspMoteTimeEvent(mote, 0) {
	      public void execute(long t) {
	        super.execute(t);
	        radio.receivedByte(inputByte);
	        mote.requestImmediateWakeup();
	      }
	    }, mote.getSimulation().getSimulationTime());
  }
  
//  /* Found on the internet in case the owner (thread) of the lock
//   * is needed to be found. */
//  class MyReentrantLock extends ReentrantLock {
//      
//      String owner() {
//        Thread t =  this.getOwner();
//        if (t != null) {
//          return t.getName();
//        } else {
//          return "none";
//        }
//      }
//    }

  
}
