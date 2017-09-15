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
  
  /* Keeps a record of the maximum transmitted power from the tag */
  private Hashtable<Integer, Hashtable<RadioConnection, Double>> tagTXPowerMax = 
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
/**/    System.out.println("tag: " + mote.getID() + " (" + mote.hashCode() + ")" + " - receivedByte");
/**/    System.out.println("tag: " + mote.getID() + " (" + mote.hashCode() + ")" + " - isTransmitting: " + isTransmitting());
        if (!isTransmitting()) {
/**/        System.out.println("tag: " + mote.getID() + " (" + mote.hashCode() + ")" + "- receivedByte, isTransmitting");
/**/        System.out.println("tag: " + mote.getID() + " (" + mote.hashCode() + ")" + " - isListeningCarrier: " + isListeningCarrier());
            if(isListeningCarrier()) {
              setLastEvent(RadioEvent.TRANSMISSION_STARTED);  
              setLastOutgoingtPacket(null);
              setTransmitting(true);
              len = 0;
              expMpduLen = 0;
              setChanged();
              notifyObservers();
              /*logger.debug("----- 802.15.4 TRANSMISSION STARTED -----");*/
            }
        }

        /* send this byte to all nodes */
        setLastOutgoingByte(data);
        setLastEvent(RadioEvent.CUSTOM_DATA_TRANSMITTED); 
        setChanged();
        notifyObservers();

        if (len < buffer.length)
          buffer[len] = data;

        len ++;

        if (len == 5) {
          setSynchronized(true);
          for (int i=0; i<5; i++) {
            if (buffer[i] != syncSeq[i]) {
              // this should never happen, but it happens
              logger.error(String.format("Bad outgoing sync sequence %x %x %x %x %x", buffer[0], buffer[1], buffer[2], buffer[3], buffer[4]));
              setSynchronized(false);
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

        if (((expMpduLen & 0x80) == 0) && len == expMpduLen + 6 && getSynchronized()) {
          setLastOutgoingtPacket(CC2420RadioPacketConverter.fromCC2420ToCooja(buffer));
          if (getLastPacketTransmitted() != null) {
            setLastEvent(RadioEvent.PACKET_TRANSMITTED);  
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
/**/System.out.println("I am a tag");
/**/System.out.println("isReceiving: "  + false);      
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
    setLastEvent(RadioEvent.CARRIER_LISTENING_STARTED);
    setChanged();
    notifyObservers();
  }
  
  /* Concerns the end of the carrier listening */
  @Override
  public void signalReceptionEnd() {
/**/System.out.println("tag: " + mote.getID() + " - carrier_listening_stopped");
    //isReceiving = false;
    isListeningCarrier = false;
    setInterfered(false);
    setLastEvent(RadioEvent.CARRIER_LISTENING_STOPPED);
    setChanged();
    notifyObservers();
  }
  
  /* 
   * Based on the absence of the receiving capabilities of
   * the tag, it does not get interfered. 
   */
  public void interfereAnyReception() {
/**/System.out.println("tag: " + mote.getID() + " does not get interfered");
    setInterfered(false);
  }
  
  
  public void updateTagTXPowers(RadioConnection conn) {
/**/System.out.println("updateTagTXPowers");
/**/System.out.println("2.lastConnID: " + conn.getID());
/**/System.out.println("3.tagTXPower: " + tagTXPower); 
    //if(tagTXPower.get(conn.getSource().getChannel()+2).containsKey(conn)) {
      tagTXPower.get(conn.getSource().getChannel()+2).remove(conn);
    //}
    
/**/System.out.println("4.tagTXPower: " + tagTXPower);
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
  
  public double getTagTXPower(int channel) {
/**/System.out.println("getTagTXPower");
/**/System.out.println("2.tagTXPower: " + tagTXPower);
/**/System.out.println(tagTXPower.get(channel).values());
/**/System.out.println("maxTXPower: " + Collections.max(tagTXPower.get(channel).values(), null));
    
    return Collections.max(tagTXPower.get(channel).values(), null);
  }
  
  public double getTagCurrentOutputPowerMax(int channel) {
    
    RadioConnection conn = null;
    
    double tagPower = Collections.max(tagTXPower.get(channel).values(), null);
    Enumeration<RadioConnection > conns = tagTXPower.get(channel).keys();
    while (conns.hasMoreElements()) {
      conn = conns.nextElement();
      if(tagTXPower.get(channel).get(conn) == tagPower) {
        return (double) conn.getSource().getCurrentOutputPowerIndicator();
      }
    }
    return 0.0;
  }
  
  
     
//  @Override
//  public int getOutputPowerIndicatorMax() {
//    return 31;
//  }
  
  @Override
  public boolean isRadioOn() {
    /**/  System.out.println("1.Msp802154Tag.isRadioOn(): " + mote.getID());
    return true;
  }
  
}
