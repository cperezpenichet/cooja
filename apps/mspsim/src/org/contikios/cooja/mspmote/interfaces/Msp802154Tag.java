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

import java.util.Collection;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.interfaces.Radio.RadioEvent;
import org.contikios.cooja.mspmote.MspMote;
import org.contikios.cooja.mspmote.MspMoteTimeEvent;

import org.contikios.cooja.mspmote.BackscatterTag;
import se.sics.mspsim.chip.TagModule;
import se.sics.mspsim.chip.BackscatterTagRadio; 

import se.sics.mspsim.chip.CC2420;
import se.sics.mspsim.chip.ChannelListener;
import se.sics.mspsim.chip.RFListener;
import se.sics.mspsim.chip.Radio802154;
import se.sics.mspsim.core.Chip;
import se.sics.mspsim.core.OperatingModeListener;

/**
 * MSPSim 802.15.4 radio to COOJA wrapper.
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("IEEE 802.15.4 Tag")
public class Msp802154Tag extends Msp802154Radio {
  private static Logger logger = Logger.getLogger(Msp802154Tag.class);

  /**
   * Cross-level:
   * Inter-byte delay for delivering cross-level packet bytes.
   */
  public static final long DELAY_BETWEEN_BYTES =
    (long) (1000.0*Simulation.MILLISECOND/(250000.0/8.0)); /* us. Corresponds to 250kbit/s */

  private RadioEvent lastEvent = RadioEvent.UNKNOWN;

  //protected final MspMote mote;
  //protected final Radio802154 radio;
  protected final BackscatterTagRadio tag;
  
  private boolean isInterfered = false;
  private boolean isTransmitting = false;
  private boolean isReceiving = false;
  private boolean isSynchronized = false;
  private boolean isGeneratingCarrier = false;
  private boolean isListeningCarrier = false;
  
  protected byte lastOutgoingByte;
  protected byte lastIncomingByte;

  private RadioPacket lastOutgoingPacket = null;
  private RadioPacket lastIncomingPacket = null;

  public Msp802154Tag(Mote m) {
    super(m);
/**/System.out.println("Msp802154Tag");
    //this.mote = (MspMote)m;
    //this.radio = this.mote.getCPU().getChip(Radio802154.class);
    this.tag = this.mote.getCPU().getChip(BackscatterTagRadio.class);
            
//    if (tag == null) {
//        throw new IllegalStateException("Mote is not equipped with a tag");
//    }
//    
    
    tag.addRFListener(new RFListener() {
      int len = 0;
      int expMpduLen = 0;
      byte[] buffer = new byte[127 + 6];
      final private byte[] syncSeq = {0,0,0,0,0x7A};
      
      public void receivedByte(byte data) {
/**/    System.out.println("tag: " + mote.getID() + "- receivedByte");
        if (!isTransmitting()) {
/**/    System.out.println("mote: " + mote.getID() + "- receivedByte, isTransmitting");         
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
    
//    radio.addOperatingModeListener(new OperatingModeListener() {
//      public void modeChanged(Chip source, int mode) {
///**/    System.out.println("mote: " + mote.getID() + "- mode= "  + mode);
//        if (mode == CC2420.MODE_TEST_CARRIER_ON) {
///**/        System.out.println("mote: " + mote.getID() + "- MODE_TEST_CARRIER_ON");
//            lastEvent = RadioEvent.HW_ON;
//            setChanged();
//            notifyObservers();
//            lastEvent = RadioEvent.CARRIER_STARTED;
//            isGeneratingCarrier = true;
///**/        System.out.println("mote: " + mote.getID() +  "- isGeneratingCarrier= " + isGeneratingCarrier);
//            setChanged();
//            notifyObservers();
//            return;
//        }
//        
//        if ((mode == CC2420.MODE_POWER_OFF) || (mode == CC2420.MODE_TXRX_OFF)) {
//            if (isGeneratingCarrier) {
///**/            System.out.println("mote: " + mote.getID() + " - MODE_TEST_CARRIER_OFF");
//                lastEvent = RadioEvent.CARRIER_STOPPED;
//                isGeneratingCarrier = false;
///**/            System.out.println("mote: " + mote.getID() + " - isGeneratingCarrier=" + isGeneratingCarrier);
//                setChanged();
//                notifyObservers();
//                //return;
///**/            System.out.println("mote: " + mote.getID() + " - 1.radioOff");
//                radioOff(); // actually it is a state change, not necessarily to OFF
//                return;
//            }
//        }
//        
///**/    System.out.println("mote: " + mote.getID() + " - addOperatingModeListener");
//        if (radio.isReadyToReceive()) {
///**/      System.out.println("mote: " + mote.getID() + " - isReadyToReceive");          
//          lastEvent = RadioEvent.HW_ON;
//          setChanged();
//          notifyObservers();
//        } else {
///**/      System.out.println("mote: " + mote.getID() + " - 2.radioOff");
//          radioOff(); // actually it is a state change, not necessarily to OFF
//        }
//      }
//    });

//    radio.addChannelListener(new ChannelListener() {
//      public void channelChanged(int channel) {
///**/    System.out.println("mote: " + mote.getID() + " - addChannelListener");
//        /* XXX Currently assumes zero channel switch time */
//        lastEvent = RadioEvent.UNKNOWN;
//        setChanged();
//        notifyObservers();
//      }
//    });
  }


  private void finishTransmission() {
    if (isTransmitting()) {
/**/   System.out.println("mote: " + mote.getID() + " - isTransmitting");
      //logger.debug("----- 802.15.4 TRANSMISSION FINISHED -----");
      isTransmitting = false;
      isSynchronized = false;
/**/  System.out.println("mote: " + mote.getID() + " - Within finishedTransmission");
      lastEvent = RadioEvent.TRANSMISSION_FINISHED;
      setChanged();
      notifyObservers();
    }
  }
}
