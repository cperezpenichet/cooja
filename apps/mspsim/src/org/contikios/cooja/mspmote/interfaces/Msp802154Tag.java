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
    if (tagTXPower !=null) {
      Enumeration<Integer> channels = tagTXPower.keys();
      while (channels.hasMoreElements()) {
        Integer channel = (Integer)channels.nextElement();
         if (this.getNumberOfConnectionsFromChannel(channel) >= 2) {
           isInterfered = true;
           lastEvent = RadioEvent.RECEPTION_INTERFERED;
/**/System.out.println("tag: " + mote.getID() + " does get interfered because "
                + "it listens to the carrier from 2 sources - same ch");
          setChanged();
          notifyObservers();
         }
      }
    }
    
  }
  
  public void updateTagTXPower(RadioConnection conn) {
/**/System.out.println("updateTagTXPowers");
/**/System.out.println("2.lastConnID: " + conn.getID());
/**/System.out.println("1.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower); 
    //if(tagTXPower.get(conn.getSource().getChannel()+2).containsKey(conn)) {
    if (tagTXPower.get(conn.getSource().getChannel()+2) != null) {
      tagTXPower.get(conn.getSource().getChannel()+2).remove(conn);
      if (tagTXPower.get(conn.getSource().getChannel()+2).isEmpty()) {
        tagTXPower.remove(conn.getSource().getChannel()+2);
      }
    } else { //remove it in the end
/**/  System.out.println("No connection was inserted");
    }
/**/System.out.println("2.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
  }
  
  public void putTagTXPower(int channel, RadioConnection conn, double tagCurrentTXPower) {
/**/System.out.println("putTagTXPower");

    if (tagTXPower.containsKey(channel)) {
      tagTXPower.get(channel).put(conn, tagCurrentTXPower);
    } else {
      Hashtable<RadioConnection, Double> txPower = new Hashtable<RadioConnection, Double>();
      txPower.put(conn, tagCurrentTXPower);
      tagTXPower.put(channel, txPower);
    }

/**/System.out.println("1.tagTXPower: " + tagTXPower);
  }
  
  public double getTagCurrentOutputPower(Radio radio, int channel) {
/**/System.out.println("getTagCurrentOutputPower");
/**/System.out.println("3.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
    if (!tagTXPower.isEmpty()) {
      Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
      while (conns.hasMoreElements()) {
         RadioConnection conn = (RadioConnection)conns.nextElement();
         if (conn.getSource() == radio) {
/**/       System.out.println("tag's output power: " + tagTXPower.get(channel).get(conn));
           return tagTXPower.get(channel).get(conn);
         }
      }
    }
    /* When there is no entry in the Hashtable 
     * return something really small */
    return Double.NEGATIVE_INFINITY;
  }
  
  public double getTagCurrentOutputPowerMax(int channel) {
/**/System.out.println("getTagCurrentOutputPowerMax");
/**/System.out.println("4.tag: " + this.getMote().getID() + " tagTXPower: " + tagTXPower);
    if (!tagTXPower.isEmpty()) {
      if (tagTXPower.get(channel) !=null) {
/**/    System.out.println(tagTXPower.get(channel).values());
/**/    System.out.println("maxTXPower: " + Collections.max(tagTXPower.get(channel).values(), null));
       /* In case there are more than one carrier generators with the same channel 
        * return the max output power of those that were produced by them */
        return Collections.max(tagTXPower.get(channel).values(), null);
      } else {
/**/    System.out.println("tagTXPower.get(" + channel + ")" + " == null");
/**/    System.out.println(channel == -1 ? "tag channel is: " + channel : "channel: " + channel);
      }
    }
    /* When there is no entry in the Hashtable 
     * return something really small */
    return Double.NEGATIVE_INFINITY;
  }
  
  public RadioConnection getConnectionFromMaxOutputPower(int channel) {
    double maxPower = this.getTagCurrentOutputPowerMax(channel);
    Enumeration<RadioConnection> carrierConns = tagTXPower.get(channel).keys();
    while (carrierConns.hasMoreElements()) {
      RadioConnection carrierConn = (RadioConnection)carrierConns.nextElement();
/**/   System.out.println("1.carrierConn: " + carrierConn);
      if (tagTXPower.get(channel).get(carrierConn) == maxPower) {
/**/    System.out.println("2.carrierConn: " + carrierConn);
        return carrierConn;
      }
    }
    return null;
    
  }
  
  public boolean isTXChannelFromCarrierGenerator(int channel) {
    /**/System.out.println("2.isTXChannelFromCarrierGenerator");
    if (tagTXPower.get(channel) !=null) {
/**/  System.out.println("3.isTXChannelFromCarrierGenerator");
      
      Enumeration<RadioConnection> conns = tagTXPower.get(channel).keys();
      while (conns.hasMoreElements()) {
        RadioConnection activeConn = (RadioConnection)conns.nextElement();
         if (activeConn.getSource().isGeneratingCarrier()) {
/**/       System.out.println("Conn: " + activeConn + " has as a source activeTrans " + activeConn.getSource().getMote().getID());         
           return true;
         }
      }
    }
    /**/System.out.println("4.isTXChannelFromCarrierGenerator");
    return false;
  }
  
  public int getNumberOfConnectionsFromChannel(int channel) {
    
    if (!tagTXPower.containsKey(channel)) {
      /**/System.out.println("HERE.NOcontainsKey");
      return 0;
    }
    
/**/System.out.println("From channel: " + channel + " - " + 
            tagTXPower.get(channel).size() + " connections");

    return tagTXPower.get(channel).size();
    
  }
  
  
  
  @Override
  public boolean isRadioOn() {
    /**/  System.out.println("1.Msp802154Tag.isRadioOn(): " + mote.getID());
    return true;
  }
  
}
