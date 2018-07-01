/*
 * Copyright (c) 2008-2012, Swedish Institute of Computer Science.
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
import org.contikios.cooja.RadioConnection;

import org.contikios.cooja.interfaces.Radio.RadioEvent;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.radiomediums.UDGMBS;

import se.sics.mspsim.chip.BackscatterTXRadio;
import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.RFListener;


import org.contikios.cooja.RadioConnection;


/**
 * MSPSim 802.15.4 radio to COOJA wrapper.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IEEE 802.15.4 Tag")
public class Msp802154Tag extends Msp802154Radio {
  private static Logger logger = Logger.getLogger(Msp802154Tag.class);
  
  private final BackscatterTXRadio tag;

  private boolean isListeningCarrier = false;
  
  /* Keeps a record of the transmitted power from the tag */
  private Hashtable<Integer, Hashtable<RadioConnection, Double>> tagTXPower = 
                                                new Hashtable<Integer, Hashtable<RadioConnection, Double>>();
  
  //ReentrantLock lock = new ReentrantLock();
  private MyReentrantLock lock = new MyReentrantLock();
  
//  volatile boolean isTXChannelFromCarrierGenerator = false;

  public Msp802154Tag(Mote m) {
    super(m);
/**/System.out.println("Msp802154Tag");
    this.tag = this.mote.getCPU().getChip(BackscatterTXRadio.class);
            
    if (tag == null) {
        throw new IllegalStateException("Mote is not equipped with a tag");
    }
    
    tag.addRFListener(new RFListener() {
      int len = 0;
      int expMpduLen = 0;
      byte[] buffer = new byte[127 + 6];
      final private byte[] syncSeq = {0,0,0,0,0x7A};
      
      public void receivedByte(byte data) {
/**/    System.out.println("tag: " + mote.getID() + " (" + tag.hashCode() + ")" + " - receivedByte");
/**/    System.out.println("tag: " + mote.getID() + " (" + tag.hashCode() + ")" + " - isTransmitting: " + isTransmitting());
        if (!isTransmitting()) {
/**/        System.out.println("tag: " + mote.getID() + " (" + tag.hashCode() + ")" + "- receivedByte, isTransmitting");
/**/        System.out.println("tag: " + mote.getID() + " (" + tag.hashCode() + ")" + " - isListeningCarrier: " + isListeningCarrier());
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

/**/    System.out.println("LEN: " + len + " - tag: " + mote.getID() + " - " + tag.hashCode());        

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
          System.out.println("## CC2420 Packet of length: " + data + " expected...");
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
    
  }

  public boolean isBackscatterTag() {
      return true;
  }
  
  public boolean isListeningCarrier() {
      return isListeningCarrier;
  }
  
  /* 
   * Currently there is no receiving 
   * capability for the tag 
   */
  @Override
  public boolean isReceiving() {
/**///System.out.println("I am a tag");
/**/System.out.println("Tag - isReceiving: "  + false);      
    return false;
   }
  
  /*
   * The channel of the tag returns 
   * -1 skipping this way the usual
   * channel check and using our own.
   */
  public int getChannel() {
/**/System.out.println("tag.getchannel");
    return -1;
  }

  /* Concerns the start of the carrier listening */
  @Override
  public void signalReceptionStart() {
/**/System.out.println("tag: " + mote.getID() + " - carrier_listening_started");
    isListeningCarrier = true;
    lastEvent = RadioEvent.CARRIER_LISTENING_STARTED;
    setChanged();
    notifyObservers();
  }
  
  /* Concerns the end of the carrier listening */
  @Override
  public void signalReceptionEnd() {
/**/System.out.println("tag: " + mote.getID() + " - carrier_listening_stopped");
    isListeningCarrier = false;
    isInterfered =false;
    lastEvent = RadioEvent.CARRIER_LISTENING_STOPPED;
    setChanged();
    notifyObservers();
  }
  
  /* 
   * Based on the absence of the receiving capabilities of
   * the tag, tag does not get interfered. 
   */
  public void interfereAnyReception() {
/**/System.out.println("tag: " + mote.getID() + " interfereAnyReception");
    isInterfered = false;
/**/System.out.println("1a.lock.owner()" + lock.owner());
/**/System.out.println("1b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("1c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    lock.lock();
/**/System.out.println("1d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("1e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    try {
      if (tagTXPower !=null) {
        Enumeration<Integer> channels = tagTXPower.keys();
        while (channels.hasMoreElements()) {
          Integer channel = (Integer)channels.nextElement();
          if (this.getNumberOfConnectionsFromChannel(channel) >= 2) {
            isInterfered = true;
            lastEvent = RadioEvent.RECEPTION_INTERFERED;
/**/        System.out.println("tag: " + mote.getID() + " does get interfered because "
                              + "it listens to the carrier from 2 sources - same ch");             
            setChanged();
            notifyObservers();
          }
        }
      }
    } finally {
      lock.unlock();
/**/  System.out.println("isInterfered: " + isInterfered);      
/**/  System.out.println("1f.lock.owner()" + lock.owner());
/**/  System.out.println("1g.lock.isLocked(): " + lock.isLocked());

    }
        
  }
        
      
  
  public void updateTagTXPower(RadioConnection conn) {
/**/System.out.println("updateTagTXPowers");
/**/System.out.println("2.lastConnID: " + conn.getID());
/**/System.out.println("1.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower); 
        //if(tagTXPower.get(conn.getSource().getChannel()+2).containsKey(conn)) {
/**/System.out.println("2a.lock.owner()" + lock.owner());
/**/System.out.println("2b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("2c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());

    lock.lock();
/**/System.out.println("2d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("2e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    try {
      int tagTXChannel = conn.getSource().getChannel()+2;
      if (tagTXPower.get(tagTXChannel) != null) {
        tagTXPower.get(tagTXChannel).remove(conn);
        if (tagTXPower.get(tagTXChannel).isEmpty()) {
          tagTXPower.remove(tagTXChannel);
        }
      } else { //remove it in the end
/**/    System.out.println("No connection was inserted");
      }
    } finally {
      lock.unlock();
/**/  System.out.println("2f.lock.owner()" + lock.owner());
/**/  System.out.println("2g.lock.isLocked(): " + lock.isLocked());
    }
/**/System.out.println("2.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
  }
  
  public void putTagTXPower(int channel, RadioConnection conn, double tagCurrentTXPower) {
/**/System.out.println("putTagTXPower");
/**/System.out.println("3a.lock.owner()" + lock.owner());
/**/System.out.println("3b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("3c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());

    lock.lock();
/**/System.out.println("3d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("3e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
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
/**/  System.out.println("3f.lock.owner()" + lock.owner());
/**/  System.out.println("3g.lock.isLocked(): " + lock.isLocked());
    }
/**/System.out.println("1. tag: " + this.getMote().getID() + " - tagTXPower: " + tagTXPower);
  }
  
  public double getTagCurrentOutputPower(Radio radio, int channel) {
/**/System.out.println("getTagCurrentOutputPower");
/**/System.out.println("3.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
/**/System.out.println("4a.lock.owner()" + lock.owner());
/**/System.out.println("4b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("4c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());

    lock.lock();
/**/System.out.println("4d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("4e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    /* When there is no entry in the Hashtable 
     * return something really small */
    double power = Double.NEGATIVE_INFINITY;
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
        while (conns.hasMoreElements()) {
          RadioConnection conn = (RadioConnection)conns.nextElement();
          if (conn.getSource() == radio) {
/**/        System.out.println("tag's output power: " + tagTXPower.get(channel).get(conn));
            power = tagTXPower.get(channel).get(conn);
          }
        }
      }
    } finally {
      lock.unlock();
/**/  System.out.println("4f.lock.owner()" + lock.owner());
/**/  System.out.println("4g.lock.isLocked(): " + lock.isLocked());
    }
    
    return power;
        
  }
  
  public double getTagCurrentOutputPowerMax(int channel) {
/**/System.out.println("1.getTagCurrentOutputPowerMax");
/**/System.out.println("4.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
/**/System.out.println("5a.lock.owner()" + lock.owner());
/**/System.out.println("5b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("5c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    lock.lock();
/**/System.out.println("5d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("5e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
      
    double maxValue = 0.0;
    try {
/**/System.out.println("tagTXPower.containsKey(channel): " + tagTXPower.containsKey(channel));
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
        maxValue = Collections.max(tagTXPower.get(channel).values());
/**/    System.out.println("1.maxValue: " + maxValue);          
      } else {
/**/    System.out.println("tagTXPower.gekt(" + channel + ") = " + tagTXPower.get(channel));
/**/    System.out.println(channel == -1 ? "tag channel is: " + channel : "channel: " + channel);
        /* When there is no entry in the Hashtable 
         * return something really small */
        maxValue = Double.NEGATIVE_INFINITY;
/**/    System.out.println("2.maxValue: " + maxValue);          
      }
    } finally {
      lock.unlock();
/**/  System.out.println("5f.lock.owner()" + lock.owner());
/**/  System.out.println("5g.lock.isLocked(): " + lock.isLocked());
    }

    return maxValue;
  }
  
  
  public RadioConnection getConnectionFromMaxOutputPower(int channel) {
/**/System.out.println("getConnectionFromMaxOutputPower");
/**/System.out.println("6a.lock.owner()" + lock.owner());
/**/System.out.println("6b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("6c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    lock.lock();
/**/System.out.println("6d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("6e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    
    RadioConnection activeConn = null;
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
/**/    System.out.println("3a.getTagCurrentOutputPowerMax");
        double maxPower = this.getTagCurrentOutputPowerMax(channel);
/**/    System.out.println("3.maxPower: " + maxPower);
/**/    System.out.println("6f.lock.owner()" + lock.owner());
/**/    System.out.println("6g.lock.isLocked(): " + lock.isLocked());

/**/    System.out.println("3b.getTagCurrentOutputPowerMax");
        Enumeration<RadioConnection> carrierConns = tagTXPower.get(channel).keys();
        while (carrierConns.hasMoreElements()) {
          RadioConnection carrierConn = (RadioConnection)carrierConns.nextElement();
/**/      System.out.println("1.carrierConn: " + carrierConn);
          if (tagTXPower.get(channel).get(carrierConn) == maxPower) {
/**/        System.out.println("2.carrierConn: " + carrierConn);
            activeConn = carrierConn;
          }
        }
      }
    } finally {
      lock.unlock();
/**/  System.out.println("6h.lock.owner()" + lock.owner());
/**/  System.out.println("6i.lock.isLocked(): " + lock.isLocked());
      
    }
    
    return activeConn;
  }
  
  
  public boolean isTXChannelFromActiveTransmitter(int channel) {
/**/System.out.println("2.isTXChannelFromCarrierGenerator");
/**/System.out.println("7a.lock.owner()" + lock.owner());
/**/System.out.println("7b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("7c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    lock.lock();
/**/System.out.println("7d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("7e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
//    volatile boolean isTXChannelFromCarrierGenerator = false;
    boolean isTXChannelFromCarrierGenerator = false;
    try {
      if (channel >= 0 && tagTXPower.containsKey(channel)) {
/**/   System.out.println("3.isTXChannelFromCarrierGenerator");
       Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
        while (conns.hasMoreElements()) {
          RadioConnection activeConn = (RadioConnection)conns.nextElement();
          if (!activeConn.getSource().isGeneratingCarrier()) {
/**/       System.out.println("Conn: " + activeConn + " has as a source activeTrans " + activeConn.getSource().getMote().getID());         
           isTXChannelFromCarrierGenerator = true;
           break;
          }
        }
      }
    } finally {
      lock.unlock();
/**/  System.out.println("7f.lock.owner()" + lock.owner());
/**/  System.out.println("7g.lock.isLocked(): " + lock.isLocked());
    }
    /**/System.out.println("4.isTXChannelFromCarrierGenerator: " + isTXChannelFromCarrierGenerator);
    return isTXChannelFromCarrierGenerator;
  }
  
  
  public int getNumberOfConnectionsFromChannel(int channel) {
/**/System.out.println("getNumberOfConnectionsFromChannel");
/**/System.out.println("8a.lock.owner()" + lock.owner());
/**/System.out.println("8b.lock.isLocked(): " + lock.isLocked());
/**/System.out.println("8c.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());

    lock.lock();
/**/System.out.println("8d.lock.owner()" + lock.owner() + " - " + lock.hashCode());
/**/System.out.println("8e.lock.isHeldByCurrentThread: " + lock.isHeldByCurrentThread());
    int size = 0;
    try {
      if (channel >=0 && tagTXPower.containsKey(channel)) {
/**/    System.out.println("From channel: " + channel + " - " + tagTXPower.get(channel).size() + " connections");
        size = tagTXPower.get(channel).size();
      } else {
/**/    System.out.println("HERE.NOcontainsKey");
//        size = 0;
      }
    } finally {
      lock.unlock();
/**/  System.out.println("8f.lock.owner()" + lock.owner());
/**/  System.out.println("8g.lock.isLocked(): " + lock.isLocked());
      
    }
    
    return size;
        
  }
  
  public boolean isTagTXPowersEmpty() {
    /**/System.out.println("isTagTXPowersEmpty");
    if (tagTXPower.isEmpty()) {
      return true;
    } else {
      return false;
    }
  }
  
  
  
   
  @Override
  public boolean isRadioOn() {
    /**/  System.out.println("1.Msp802154Tag.isRadioOn(): " + mote.getID());
    return true;
  }
  
  class MyReentrantLock extends ReentrantLock {
      
      String owner() {
        Thread t =  this.getOwner();
        if (t != null) {
          return t.getName();
        } else {
          return "none";
        }
      }
    }
  
  
  
}
