/*
 * Copyright (c) 2006, Swedish Institute of Computer Science. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer. 2. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution. 3. Neither the name of the
 * Institute nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.contikios.cooja.interfaces;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.contikimote.interfaces.ContikiRadio;

/**
 * A mote radio transceiver.
 *
 * @see ContikiRadio
 * @see CustomDataRadio
 * @see NoiseSourceRadio
 *
 * @author Fredrik Osterlind
 */
@ClassDescription("Radio")
public abstract class Radio extends MoteInterface {
	  ///// Ugly hack!!!! //////
	  public boolean interfere_anyway = false;
	
  private static Logger logger = Logger.getLogger(Radio.class);
  
  public int FREQSHIFT = 0;

  
  /**
   * Events that radios should notify observers about.
   */
  public enum RadioEvent {
    UNKNOWN, HW_OFF, HW_ON,
    RECEPTION_STARTED, RECEPTION_FINISHED, RECEPTION_INTERFERED,
    TRANSMISSION_STARTED, TRANSMISSION_FINISHED,
    PACKET_TRANSMITTED, CUSTOM_DATA_TRANSMITTED, CARRIER_STARTED, CARRIER_STOPPED,
    CARRIER_LISTENING_STARTED, CARRIER_LISTENING_STOPPED
  }
  
  /* Output power of CC240 radio transceiver in dBm,(p.51 - cc2420 datasheet) */
  public final double[] CC2420OutputPower = {
    -37.92, -32.98, -28.7, -25.0, -21.84, -19.15, 
    -16.89, -15.0, -13.42, -12.1, -10.98, -10.0, 
    -9.12, -8.33, -7.63, -7.0, -6.44, -5.94, -5.47, 
    -5.0, -4.52, -4.03, -3.52, -3.0, -2.47, -1.95, 
    -1.45, -1.0, -0.61, -0.3, -0.09, 0.0
  };

  /**
   * Register the radio packet that is being received during a connection. This
   * packet should be supplied to the radio medium as soon as possible.
   *
   * @param packet Packet data
   */
  public abstract void setReceivedPacket(RadioPacket packet);

  /**
   * @return Last packet transmitted by radio
   */
  public abstract RadioPacket getLastPacketTransmitted();

  /**
   * @return Last packet received by radio
   */
  public abstract RadioPacket getLastPacketReceived();

  /**
   * Signal that a new reception just begun. This method should normally be
   * called from the radio medium.
   *
   * @see #signalReceptionEnd()
   */
  public abstract void signalReceptionStart();

  /**
   * Signal that the current reception was ended. This method should normally be
   * called from the radio medium on both destination and interfered radios.
   *
   * @see #signalReceptionStart()
   */
  public abstract void signalReceptionEnd();

  public void signalCarrierReceptionStart() {}
  public void signalCarrierReceptionEnd() {}
  
  /**
   * Returns last event at this radio. This method should be used to learn the
   * reason when a radio notifies a change to observers.
   *
   * @return Last radio event
   */
  public abstract RadioEvent getLastEvent();
  
  /**
   * Checks if a radio refers to a backscatter tag or to a CC2420 radio.
   * 
   * @return True if radio refers to a backscatter tag
   */
  public boolean isBackscatterTag() {
      return false;
  }
  
  /**
   * Returns true if this radio is transmitting, or just finished transmitting,
   * data.
   *
   * @see #isReceiving()
   * @return True if radio is transmitting data
   */
  public abstract boolean isTransmitting();

  /**
   * Returns true if this radio is receiving data.
   *
   * @see #isTransmitting()
   * @return True if radio is receiving data
   */
  public abstract boolean isReceiving();

  /**
   * Returns true if this radio had a connection that was dropped due to
   * interference.
   *
   * @return True if this radio is interfered
   */
  public abstract boolean isInterfered();
  
  /**
   * @return True if the simulated radio transceiver is on
   */
  public abstract boolean isRadioOn();

  /**
   * Interferes with any current reception. If this method is called, the packet
   * will be dropped. This method can be used to simulate radio interference
   * such as high background noise or radio packet collisions.
   *
   * When the radio is no longer interfered, the {@link #signalReceptionEnd()}
   * method must be called.
   *
   * @see #signalReceptionEnd()
   */
  public abstract void interfereAnyReception();

  /**
   * @return Current output power (dBm)
   */
  public abstract double getCurrentOutputPower();

  /**
   * @return Current output power indicator
   */
  public abstract int getCurrentOutputPowerIndicator();

  /**
   * @return Maximum output power indicator
   */
  public abstract int getOutputPowerIndicatorMax();
  
  /** 
   * Returns the current LQI-value. This might differ from platform to platform 
   * @return Current LQI value.
   *  
   * 
   */
  public int getLQI() throws UnsupportedOperationException {
	  throw new UnsupportedOperationException();
  }

  /**
   * Sets the LQI. This in not supported by all platforms. Also results may differ
   * from platform to platform. 
   *
   * @param lqi The current LQI
   */
  public void setLQI(int lqi){
  }

  /**
   * @return Current surrounding signal strength
   */
  public abstract double getCurrentSignalStrength();

  /**
   * Sets surrounding signal strength. This method should normally be called by
   * the radio medium.
   *
   * @param signalStrength
   *          Current surrounding signal strength
   */
  public abstract void setCurrentSignalStrength(double signalStrength);

  /**
   * Returns the current radio channel number.
   *
   * @return Current channel number
   */
  public abstract int getChannel();

  /**
   * Returns the radio position.
   * This is typically the position of the mote.
   *
   * @return Radio position
   */
  public abstract Position getPosition();

  /**
   * This is a convenience function.
   * It returns the mote, if any, that holds this radio.
   *
   * @return Mote
   */
  public abstract Mote getMote();
  
  /* Concerning the backscatter communication */
  
  /**
   * Signal that the carrier listening just begun. This method should normally be 
   * called from the radio medium on interfered radios.
   * 
   * @see void carrierListeningEnd()
   */
  public void carrierListeningStart() {}
  
  /**
   * Signal that the carrier listening was ended. This method should normally be 
   * called from the radio medium on interfered radios.
   * 
   * @see void carrierListeningStart()
   */
  public void carrierListeningEnd() {}
  
  /**
   * Returns true if this radio is generating a carrier, or just finished generating one.
   * 
   * @see #isTransmitting()
   * @return True if radio is generating a carrier.
   */
  public boolean isGeneratingCarrier() {
    return false;
  }
  
  /**
   * Returns true if this radio is listening a carrier, or just finished listening one.
   * 
   * @see #isTransmitting()
   * @return True if radio is listening the carrier.
   */
  public boolean isListeningCarrier() {
    return false;
  }
  
  /**
   * Removes the entry that concerns the already finished connection that is given from 
   * the hashtable of the tag.
   *  
   * @param conn
   */
  public void updateTagTXPower(RadioConnection conn) {};
  
  /**
   * Keeps a record of the output power of the tag concerning each time the corresponding
   * connection from which it was produced as well as the appropriate transmission channel 
   * derived from the carrier generator that created that connection. 
   * 
   * @param channel, conn, tagCurrentTXPower
   */
  public void putTagTXPower(int channel, RadioConnection conn, double tagCurrentTXPower){};
  
  /**
   * In case there are more than one connections created by carrier generators with the 
   * same channel this method returns the output power of the tag that corresponds to the 
   * connection created by the given radio (carrier generator). 
   * 
   * @param radio
   * @param channel
   */
  public double getTagCurrentOutputPower(Radio radio, int channel) { return 0.0; }
  
  /**
   * In case there are more than one connections created by carrier generators with the 
   * same channel this method returns the max output power of the tag that corresponds to the 
   * connection created by the carrier generator which is closer to the tag. 

   * @param channel
   */
  public double getTagCurrentOutputPowerMax(int channel) { return 0.0; }
  
  /**
   * Returns the connection responsible for the tag's maximum output power, in case the carrier 
   * generators of more than one connections have the same channel.
   * 
   * @param channel
   */
  public RadioConnection getConnectionFromMaxOutputPower(int channel) { return null; }
  
  /**
   * Returns true if there is at least one active transmitter who serves as a source in one 
   * of the ongoing connections that correspond to the entry in the tag's hashtable indexed by
   * the given channel. 
   * 
   * @param channel
   */
  public boolean isTXChannelFromActiveTransmitter(int channel) { return false; }
  
  /**
   * Returns the number of connections that correspond to the entry in the tag's hashtable indexed
   * by the given channel. 
   * 
   * @param channel
   */
  public int getNumberOfConnectionsFromChannel(int channel) { return 0; }
  
  /**
   * Returns true if the tag's hashtable is empty.
   * @return
   */
  public boolean isTagTXPowersEmpty() { return false; }

  
  public JPanel getInterfaceVisualizer() {
    JPanel panel = new JPanel(new BorderLayout());
    Box box = Box.createVerticalBox();

    final JLabel statusLabel = new JLabel("");
    final JLabel lastEventLabel = new JLabel("");
    final JLabel channelLabel = new JLabel("");
    final JLabel ssLabel = new JLabel("");
    final JButton updateButton = new JButton("Update SS");

    box.add(statusLabel);
    box.add(lastEventLabel);
    box.add(ssLabel);
    box.add(updateButton);
    box.add(channelLabel);

    updateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ssLabel.setText("Signal strength (not auto-updated): "
            + String.format("%1.1f", getCurrentSignalStrength()) + " dBm");
      }
    });

    final Observer observer = new Observer() {
      public void update(Observable obs, Object obj) {
        if (isTransmitting()) {
          statusLabel.setText("Transmitting");
        } else if (isReceiving()) {
          statusLabel.setText("Receiving");
        } else {
          statusLabel.setText("Listening");
        }

        lastEventLabel.setText("Last event: " + getLastEvent());
        ssLabel.setText("Signal strength (not auto-updated): "
            + String.format("%1.1f", getCurrentSignalStrength()) + " dBm");
        if (getChannel() == -1) {
          channelLabel.setText("Current channel: ALL");
        } else {
          channelLabel.setText("Current channel: " + getChannel());
        }
      }
    };
    this.addObserver(observer);

    observer.update(null, null);

    panel.add(BorderLayout.NORTH, box);
    panel.putClientProperty("intf_obs", observer);
    return panel;
  }

  public void releaseInterfaceVisualizer(JPanel panel) {
    Observer observer = (Observer) panel.getClientProperty("intf_obs");
    if (observer == null) {
      logger.fatal("Error when releasing panel, observer is null");
      return;
    }

    this.deleteObserver(observer);
  }
}
