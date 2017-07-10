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

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Radio.RadioEvent;

import se.sics.mspsim.chip.BackscatterTXRadio;
import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.RFListener;

/**
 * MSPSim 802.15.4 radio to COOJA wrapper.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IEEE 802.15.4 Tag")
public class Msp802154Tag extends Msp802154Radio {
  private static Logger logger = Logger.getLogger(Msp802154Tag.class);

  private final BackscatterTXRadio tag;
  
  //private boolean isTransmitting = false;
  private boolean isListeningCarrier = false;

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
/**/    System.out.println("tag: " + mote.getID() + " - receivedByte");
/**/    System.out.println("tag: " + mote.getID() + " - isTransmitting: " + isTransmitting());
        if (!isTransmitting()) {
/**/    System.out.println("tag: " + mote.getID() + "- receivedByte, isTransmitting");
/**/    System.out.println("tag: " + mote.getID() + " - isListeningCarrier: " + isListeningCarrier());
            if(isListeningCarrier()) {
              lastEvent = RadioEvent.TRANSMISSION_STARTED;
              lastOutgoingPacket = null;
              isTransmitting = true;
              len = 0;
              expMpduLen = 0;
              setChanged();
              notifyObservers();
              /*logger.debug("----- 802.15.4 TRANSMISSION STARTED -----");*/
            }
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
    
  }

  
  public boolean isBackscatterTag() {
      return true;
  }
  
  public boolean isListeningCarrier() {
      return isListeningCarrier;
  }
  
  public void carrierListeningStart() {
/**/  System.out.println("tag: " + mote.getID() + " - carrier_listening_started");
      isListeningCarrier = true;
      lastEvent = RadioEvent.CARRIER_LISTENING_STARTED;
      setChanged();
      notifyObservers();
  }
  
  public void carrierListeningEnd() {
/**/  System.out.println("tag: " + mote.getID() + " - carrier_listening_stopped");
      //isReceiving = false;
      isListeningCarrier = false;
      isInterfered = false;
      lastEvent = RadioEvent.CARRIER_LISTENING_STOPPED;
      setChanged();
      notifyObservers();
  }
  
  public int getCurrentOutputPowerIndicator() {
      return 31;
  }

  public int getOutputPowerIndicatorMax() {
      return 31;
  }
  
  /* HACK: tag doesn't have a radio but we need this method to be true whenever
     tag is listening to the carrier in order to draw the red line on the 
     timeline that dictates that tag is interfered */
  public boolean isRadioOn() {
/**/  System.out.println("1.Msp802154Tag.isRadioOn(): " + mote.getID());
      if(isListeningCarrier()) {
/**/  System.out.println("2.Msp802154Tag.isRadioOn(): " + mote.getID());          
          return true;
      }
          
      return false;
  }
}
