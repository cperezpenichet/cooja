/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
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

package org.contikios.cooja.radiomediums;

import java.lang.Integer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.omg.CORBA.INTF_REPOS;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.skins.UDGMBSVisualizerSkin;
import org.contikios.cooja.plugins.skins.UDGMVisualizerSkin;

/**
 * The enhancement of COOJA in order to support realistic simulations of backscatter radio links
 * is based on the master thesis of George Daglaridis that was carried out with the help of his supervisor
 * Carlos Perez Penichet, at UNO Group, Uppsala University. The goal of this thesis was to experimentally 
 * investigate what affects the quality of backscatter radio links and model all the phenomena retrieved 
 * form these experiments so as to augment COOJA to support backscatter devices. For additional information 
 * you can address here: https://www.dropbox.com/s/dglxt46kifnn9nu/MasterThesis_GeorgeDaglaridis.pdf?dl=0.
 * 
 * 
 * The Unit Disk Graph Radio Medium for Backscatter Communication abstracts radio 
 * transmission range as circles.
 * 
 * It uses two different range parameters: one for transmissions, and one for
 * interfering with other radios and transmissions.
 * 
 * When a connection has as as source an active radio acting either as active transmitter
 * or carrier generator, both radio ranges grow with the radio output power indicator as 
 * it was in the original UDGM (parent class). What changes here is the the way that these
 * ranges are calculated concerning a connection that has a backscatter tag as a source.
 * The tag's output power changes dynamically based on the energy loss that the tag
 * experiences due to the distance between the tag and the active radio.  
 *
 * This class is an extension of the UDGM class written by Fredrik Osterlind.0
 *
 * @author George Daglaridis
 * @author Carlos Perez Penichet
 */

@ClassDescription("Unit Disk Graph Medium for Backscater Communication (UDGMBS): Distance Loss")
public class UDGMBS extends UDGM {
  private static Logger logger = Logger.getLogger(UDGMBS.class);
  
  /* Gain of the transmitting antenna */
  public  double GT = 0;
  /* Gain of the receiving antenna */
  public  double GR = 0;
  /* Wavelength */
  public  double WAVELENGTH = 0.122;
  /* Backscatter Coefficient */
  /* Derived from the experimental part of the master thesis mentioned above */
  public double BACKSCATTER_COEFFICIENT =  13.4;
  /* Energy loss */
  public  double ENERGYLOSS = 1.4;  
  /* Sensitivity threshold for Tmote sky in dBm */
  /* Derived from the experimental part of the master thesis mentioned above */
  public final double STH = -86.4;

  
  public UDGMBS(Simulation simulation) {
    super(simulation);
  
    /* 
     * Re-calculate the TX range of the tag when the position
     * of the tag or the active node (active transmitter or 
     * carrier generator) changes. 
     */
    
    final Observer positionObserver = new Observer() {
      public void update(Observable o, Object arg) {
        Mote mote = (Mote) arg;
        Radio radio = mote.getInterfaces().getRadio();
        
        if (radio.isBackscatterTag()) {
          for (RadioConnection conn: getActiveConnections()) {
            /* Calculating the tag's output power does not involve 
             * the tag as a source of a connection, which in fact  
             * would add an unwanted entry in the tag's hashtable. */
              if (!conn.getSource().isBackscatterTag()) {
                calculateTagCurrentTxPower(conn.getSource(), radio, conn);
              }
          }
        } else {
          for (RadioConnection conn: getActiveConnections()) {
            if (conn.getSource() == radio) {
              if (conn.getSource().isGeneratingCarrier()) {
                for (Radio destRadio: conn.getAllDestinations()) {
                  if (destRadio.isBackscatterTag()) {
                    calculateTagCurrentTxPower(radio, destRadio, conn);
                  }
                }
              } else {
                for (Radio intfRadio: conn.getInterfered()) {
                  if (intfRadio.isBackscatterTag()) {
                    calculateTagCurrentTxPower(radio, intfRadio, conn);
                      
                  }
                }
              }
            }
          }
        } 
      }
    };

    /* Re-analyze potential receivers if radios are added/removed. */
    simulation.getEventCentral().addMoteCountListener(new MoteCountListener() {
      public void moteWasAdded(Mote mote) {
        mote.getInterfaces().getPosition().addObserver(positionObserver);
      }
      public void moteWasRemoved(Mote mote) {
        mote.getInterfaces().getPosition().deleteObserver(positionObserver);
      }
    });
    for (Mote mote: simulation.getMotes()) {
      mote.getInterfaces().getPosition().addObserver(positionObserver);
    }
    
    /* Remove the UDGMVisualizerSkin since visualization 
     * is being handled by UDGMBSVisualizerSkin */
    super.removed();

    /* Register visualizer skin */
    Visualizer.registerVisualizerSkin(UDGMBSVisualizerSkin.class);
      
  }
  
  public void removed() {
      Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
  }
  
  /**
   * Returns the loss in signal strength of the propagation wave
   * 
   * @param distance
   */
  public double pathLoss(double distance) {
    return 20*(Math.log10(WAVELENGTH / (4*Math.PI*distance)));
  }
  
  /**
   * Returns the incident power that reaches the destination
   * considering the energy loss due to the distance between
   * the source and the destination.  
   * 
   * @param source
   * @param dest
   */
  public double friisEquation(Radio source, Radio dest) {
    double distance = source.getPosition().getDistanceTo(dest.getPosition());
    double transmitttedPower = 0.0;

    transmitttedPower = getTransmissionPower(source);
    
    double incidentPower = transmitttedPower + GT + GR + pathLoss(distance);
    return incidentPower;
  
  }

  public double getTransmissionPower(Radio radio) {
    return radio.CC2420OutputPower[radio.getCurrentOutputPowerIndicator()];
  }


  /**
   * Calculate the transmission power of the tag for the given connection, conn, 
   * and the given source of that connection, activeRadio (active transmitter or 
   * a carrier generator).
   * 
   * @param carrierGen
   * @param tag
   * @param conn
   */
  public void calculateTagCurrentTxPower(Radio activeRadio,  Radio tag, RadioConnection conn) {
    /* Calculate the output power of the tag subtracting the REFLECTION LOSS of the 
    target (backscatter tag) from the incident power that reaches it. Keep a record 
    of that output power indexing it by the appropriate backscatter transmission  
    channel derived by the activeRadio from which that incident power came. */
    
    /* Incident power in dBm */
    double incidentPower = friisEquation(activeRadio, tag);
    /* Reflection loss */
    double reflectionLoss = BACKSCATTER_COEFFICIENT + ENERGYLOSS;
    /* Current power of the tag in dBm */
    double tagCurrentTXPower = incidentPower - reflectionLoss;
    tag.putTagTXPower(activeRadio.getChannel() + 2, conn, tagCurrentTXPower);
    
  } 
  
  /**
   * Calculate tag's transmission range: dynamically grows with tag's output power
   * 
   * @param tagCurrentOutputPowerIndicator
   * @return tag's transmission range
   */
  public double calculateTagTransmissionRange(double tagCurrentOutputPowerIndicator) {
    return Math.pow(10,((GT + GR + tagCurrentOutputPowerIndicator - STH + 20*(Math.log10(WAVELENGTH / (4*Math.PI)))) / 20));
    
  }
  
  /**
   * Calculate tag's interference range: dynamically grows with tag's output power
   * 
   * @param tagCurrentOutputPowerIndicator
   * @return tag's interference range
   */
  public double calculateTagInterferenceRange(double tagCurrentOutputPowerIndicator) {
    return Math.pow(10,((GT + GR + tagCurrentOutputPowerIndicator - (STH - 3) + 20*(Math.log10(WAVELENGTH / (4*Math.PI)))) / 20));
    
  }
  
  /**
   * Returns a hashset with the appropriate TX channels of each 
   * tag considering only the ongoing connections created by an 
   * active radio (transmitter or carrier generator) whose signal 
   * the tag is currently listening to.
   * 
   * @param sender
   */
  public HashSet<Integer> getRadioChannels(Radio radio) {
    /* Store the channels for an active or backscatter transmission */
    HashSet<Integer> radioChannels = new HashSet<Integer>();
    /*
     * Every tag that is listening to a carrier is interfered by the
     * connection whose source generated that carrier.
     *
     * Hence, check every active connection generated by a carrier generator
     */
    if(radio.isBackscatterTag()) {
      for (RadioConnection conn : getActiveConnections()) {

        /* ... and also check to which connection the tag belongs. Then take the
           channel of the source (carrier generator) of that connection, move
           it two channels apart (+2) and store that channel to a set. */
        if (conn.isDestination(radio)) {
          if (conn.getSource().isGeneratingCarrier()) { // TODO It might be interesting to reflect active transmissions as interference too
            if (conn.getSource().getChannel() >=0) {
              radioChannels.add(conn.getSource().getChannel() + 2);
            }
          }
        }
      }
    } else {
      /* Store the channel of the sender which is responsible for an active transmission */
      if (radio.getChannel() >= 0) {
        radioChannels.add(radio.getChannel());
      }
    }
    return radioChannels;
  }

  //this method can be removed if the proposed hashmap for carrier generators and channels is implemented
  public Radio getCarrierSource(Radio radio, int channel) {

    if(radio.isBackscatterTag() && radio.isListeningCarrier()) {
      for (RadioConnection conn : getActiveConnections()) {
        if (conn.isDestination(radio)) {
          if (conn.getSource().isGeneratingCarrier()) {
            if (conn.getSource().getChannel() == (channel - 2)) {
              return conn.getSource();
            }
          }
        }
      }
    }
    return null;
  }

  /*
   * Currently the tag does not have any receiving capabilities.
   * Hence, the tag is not affected by interferences, which is 
   * a case that was taken into account during the implementation
   * of the following method. 
   */
  public RadioConnection createConnections(Radio sender) {
	  RadioConnection newConnection;

    /* Store the channels to which the tag can transmit */
    HashSet<Integer> tagTXChannels = new HashSet<Integer>();
    
    if (!sender.isBackscatterTag()) {
      /* 
       * For a transmission started by an active radio acting either as  
       * an active transmitter or a carrier generator, the already implemented
       * parent method for creating the new connection is used.
       */  
      newConnection = super.createConnections(sender);

      /* 
       * In case the sender is a carrier generator, every radio within its TX 
       * range that was added as destination, except for a tag, is removed from 
       * the destinations and is added to the interfered. The opposite happens 
       * only in case the tag is within carrier generator's INT range.
       */ 
      if(sender.isGeneratingCarrier()) { 
        for (Radio r: newConnection.getAllDestinations()) {
          if (!r.isBackscatterTag()) {
            newConnection.removeDestination(r);
            newConnection.addInterfered(r);
            //r.interfereAnyReception();
          } else {
            calculateTagCurrentTxPower(sender, r, newConnection);

          	  if (r.isListeningCarrier()) { // || r.isReceiving()) { // If it was listening to a carrier before, we check if this one interferes
          		  HashSet<Integer> tx_channels = new HashSet<Integer>();
          		  tx_channels = getRadioChannels(r);
          		  int sender_channel = sender.getChannel();
          		  for (int i = 1; i <= 2; i++) {
          			  if (tx_channels.contains(sender_channel + i + 2) || 
          			      tx_channels.contains(sender_channel - i + 2)) {
          				  newConnection.removeDestination(r);
          				  newConnection.addInterfered(r);
          				  r.interfere_anyway = true; //// Ugly hack!!!!!
          				  r.interfereAnyReception(); //?
          				  break;
          			  }
          		  }
          	  } //else {
//  	            // NOTE: the node is only removed from the onlyInterfered ArrayList
//  	            // and not from the allInterfered ArrayList.
//    	  		}
          } 
        }
        for (Radio r: newConnection.getInterfered()) {
          if (r.isBackscatterTag()) {
	            newConnection.addDestination(r);
	            calculateTagCurrentTxPower(sender, r, newConnection);
          }
        }
      }  else {
        /* In case the sender transmits a packet and the tag is within its TX
         * range the tag is removed from the destinations and added to the interfered.----this is changed now */
        for (Radio r: newConnection.getAllDestinations()) {
          if (r.isBackscatterTag() && r.isListeningCarrier()) {
//        	  if (r.isReceiving()) { // If this tag is already receiving check that this transmission doesnt interfere
//        		  for (int i = 1; i <= 2; i++) {
//        			  if (r.isTXChannelFromActiveTransmitter(sender.getChannel() + i + 2)) { // If this sender causes
//        				  																	 // channel interference to
//        				  																	 // this tag
//        				 double sender_power = friisEquation(sender, r);
////        				 double tx_power = friisEquation(source, r); // How to find out the source?
//        				 int CHANNEL_REJECTION[] = {3, 14, 22};
////        				 if (sender_power >= tx_power + CHANNEL_REJECTION[Math.abs(i)]) {
////        					newConnection.removeDestination(r); 
////        				 	continue;
////        				 }
//        			  }
//        		  }
//        	  }


            RadioConnection carrier_connection = r.getConnectionFromMaxOutputPower(sender.getChannel());
            if (carrier_connection == null) {
              continue; // There is no carrier generator in the appropriate channel for this sender so we skip this tag.
            }
            
            double B = -64.91;//A and B are negative
            double A = -1.025;
            Radio carrierGenerator = carrier_connection.getSource();
            double incidentPowerOfTag_CG= friisEquation(carrierGenerator,r);
            double Sensitivity_Threshold = A*incidentPowerOfTag_CG + B;
            double incidentPowerOfTag_Sender= friisEquation(sender,r);
            //Sensitivity_Threshold is greater than, but here we compare negative values and therefore sign is lessthan
            if (incidentPowerOfTag_Sender > Sensitivity_Threshold){
              //do nothing, automatically added to destination
              System.out.println("-----can receive ----");
            }
            else if(incidentPowerOfTag_Sender > (Sensitivity_Threshold - 3)) {
              newConnection.removeDestination(r);
              System.out.println("-----Interfered -----");
            }
            else {
              newConnection.removeDestination(r);
              newConnection.addInterfered(r);
              System.out.println("-----cannot receive ----");
            }
          }
          else{
            newConnection.removeDestination(r);
            /* tag (r) is added to Interfered but without setting any flag since the tag
               has no receiving capabilities. */
            newConnection.addInterfered(r);
          }
        }
      }
    } else {
      /* The tag starts a new connection. */   
      newConnection = new RadioConnection(sender);
      
      /* Fail radio transmission randomly - no radios will hear this transmission */
      if (getTxSuccessProbability(sender) < 1.0 && getRandom().nextDouble() > getTxSuccessProbability(sender)) {
        return newConnection;
      }

      /* Collect the channels for backscattering communication */
      tagTXChannels = getRadioChannels(sender);
          
      /* Get all potential destination radios */
      DestinationRadio[] potentialDestinations = getDirectedGraphMedium().getPotentialDestinations(sender);
      if (potentialDestinations == null) {
        return newConnection;
      }
          
      /* Loop through all potential destinations */
      Position senderPos = sender.getPosition();
      
      for (DestinationRadio dest: potentialDestinations) {

        Radio recv = dest.radio;

        if(recv.getChannel() >= 0 && !tagTXChannels.contains(recv.getChannel())) {

          /* Add the connection in a dormant state;
             it will be activated later when the radio will bes
             turned on and switched to the right channel. This behavior
             is consistent with the case when receiver is turned off. */
          newConnection.addInterfered(recv);
        } else {
          double tagCurrentOutputPowerIndicator = sender.getTagCurrentOutputPowerMax(recv.getChannel());
          
          /* Tag's transmission range */
          double tagTransmissionRange = calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);
          /* Tag's interference range */
          double tagInterferenceRange = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);

          Position recvPos = recv.getPosition();
          double distance = senderPos.getDistanceTo(recvPos);
        
          if (sender.getNumberOfConnectionsFromChannel(recv.getChannel()) != 0) {
          
          /* 
           * When a tag starts a new connection and at least one of the ongoing connections has 
           * an active transmitter as a source treat the receiver as an interfered radio.
           */
          if (sender.isTXChannelFromActiveTransmitter(recv.getChannel()) || 
                            sender.getNumberOfConnectionsFromChannel(recv.getChannel()) >= 2) {
            if (distance <= tagInterferenceRange) {
              /* Within interference range */
              newConnection.addInterfered(recv);
              recv.interfereAnyReception();
            } 
          } else {
            /* 
             * When a tag starts a new connection and the ongoing connections have as a source only a 
             * carrier generator. */
            if (distance <= tagTransmissionRange) {
              /* Within transmission range */

              if (!recv.isRadioOn()) {
                newConnection.addInterfered(recv);
                recv.interfereAnyReception();
              } else if (recv.isInterfered()) {
                /* Was interfered: keep interfering */
                newConnection.addInterfered(recv);
              } else if (recv.isTransmitting()) {
                newConnection.addInterfered(recv);
              } else if (recv.isReceiving() || (getRandom().nextDouble() > getRxSuccessProbability(sender, recv))) {
                /* Was receiving, or reception failed: start interfering */
                newConnection.addInterfered(recv);
                recv.interfereAnyReception();

                /* Interfere receiver in all other active radio connections */
                for (RadioConnection conn : getActiveConnections()) {
                  if (conn.isDestination(recv)) {
                    conn.addInterfered(recv);
                  }
                }
              } else {
                if(!recv.isBackscatterTag()) {
                  /* Success: radio starts receiving */
                  newConnection.addDestination(recv);
                } else {
                  /* In case the receiver is a tag */  
                  newConnection.addInterfered(recv);
                }
              }
              /* Everything beyond this range is not considered as a valid destination */
            } else if (distance <= tagInterferenceRange) {
              /* Within interference range */
              newConnection.addInterfered(recv);
              recv.interfereAnyReception();
            }
          }
         }
        }  
      } 
    }
    return newConnection;
    
  } /* createConnections */
  
  @Override
  public double getRxSuccessProbability(Radio source, Radio dest) {
    double rxSuccessProbability = 0.0;

    if(!source.isBackscatterTag()) {
      rxSuccessProbability = super.getRxSuccessProbability(source, dest);
    } else {
      double distance = source.getPosition().getDistanceTo(dest.getPosition());
      double distanceSquared = Math.pow(distance,2.0);
      
      double tagCurrentOutputPowerIndicator = source.getTagCurrentOutputPowerMax(dest.getChannel());
      
      double distanceMax = calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);
      if (distanceMax == 0.0) {
        return 0.0;
      }      
      
      double distanceMaxSquared = Math.pow(distanceMax,2.0);
      double ratio = distanceSquared / distanceMaxSquared;
      if (ratio > 1.0) {
        return 0.0;
      }

      rxSuccessProbability = 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
    }
    
    return rxSuccessProbability;
        
  }
 
  @Override
  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */

    /* Reset signal strengths */
    for (Radio radio : getRegisteredRadios()) {
      /* 
       * Update the Hashtable of the tag in case it is a 
       * destination or an interfered radio to the connection 
       * that just finished. 
       */
      if(radio.isBackscatterTag()) {
        /* Would have been enabled if the tag had receiving capabilities */
        //radio.setCurrentSignalStrength(-100);
        RadioConnection lastConn = getLastConnection();
        if (lastConn != null) {
          if( lastConn.isDestination(radio) || lastConn.isInterfered(radio) ) {
            radio.updateTagTXPower(lastConn);
          }
        }
      } else {
        // In the future, do the same for the tag in case it 
        // acquires receiving capabilities.
        radio.setCurrentSignalStrength(getBaseRssi(radio));      
      }
    }
    
    HashSet<Integer> txChannels = new HashSet<Integer>();
    
    /* Concerning connections created either by an active transmitter or a tag */

    /* Set signal strength to below strong on destinations */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
      txChannels = getRadioChannels(conn.getSource());
        
      if(!conn.getSource().isBackscatterTag()) {
        if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
          conn.getSource().setCurrentSignalStrength(SS_STRONG);
        }
      }
      
      for (Radio dstRadio : conn.getDestinations()) {
        if(dstRadio.getChannel() >= 0 && !txChannels.contains(dstRadio.getChannel())) {
            continue;
        }
        
        /* In case the destination radio is a tag, don't calculate its signal strength
         * since it does not have receiving capabilities. */
        if (!dstRadio.isBackscatterTag()) { //|| !dstRadio.isGeneratingCarrier()) {
  
          double dist = conn.getSource().getPosition().getDistanceTo(dstRadio.getPosition());
          
          double signalStrength = 0.0;
  
          if (conn.getSource().isBackscatterTag()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(dstRadio.getChannel());
            
            /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
            signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
          } else {
            double maxTxDist = TRANSMITTING_RANGE
            * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
              
            double distFactor = dist/maxTxDist;
            signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
          }
          
          if (dstRadio.getCurrentSignalStrength() < signalStrength) {
            dstRadio.setCurrentSignalStrength(signalStrength);
          }
        }
        
        /* In case the tag stops listening the carrier from one connection but
         * it is still listening the carrier from another connection keep it signaled. */
        if (dstRadio.isBackscatterTag() && !dstRadio.isListeningCarrier()) {
          if (conn.getSource().isGeneratingCarrier()) {
            dstRadio.signalCarrierReceptionStart();
          }
        }
        /* If a dstRadio is already receiving a packet transmitted from a tag and a second because of tag which listens to 
         * the carrier radiated by two carrier generators transmitting on the same channel at the same time */
        if (conn.getSource().isListeningCarrier() && conn.getSource().getNumberOfConnectionsFromChannel(dstRadio.getChannel()) >= 2) {
          if (dstRadio.isReceiving()) {
            dstRadio.interfereAnyReception();
          }
        }
        /* In case two active transmitters or two carrier generators or one active and one carrier
         * exist, the tag that is simultaneously accepting their signal gets interfered */
        
        if (conn.getSource().isGeneratingCarrier() && dstRadio.isListeningCarrier()) {
          if (dstRadio.getNumberOfConnectionsFromChannel(conn.getSource().getChannel() + 2) >= 2) {
            dstRadio.interfereAnyReception();  
          }
        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
    }

    /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
      txChannels = getRadioChannels(conn.getSource());
  
      for (Radio intfRadio : conn.getInterfered()) {
        if(intfRadio.getChannel() >= 0 && !txChannels.contains(intfRadio.getChannel())) {
          continue;
        }

        /* In case the interfered radio is a tag, don't calculate its signal strength
         * since it does not have receiving capabilities. */
        if (!intfRadio.isBackscatterTag()) { //|| !intfRadio.isGeneratingCarrier()) {
          
          double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
          double signalStrength = 0.0;
          double distFactor = 0.0;
          double ss_weak = STH;
          
          if (conn.getSource().isBackscatterTag()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(intfRadio.getChannel());
  
            double maxTxDist = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);
            
            distFactor = dist/maxTxDist;
            
            /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
            signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
          } else {
            double maxTxDist = TRANSMITTING_RANGE
            * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
    
            distFactor = dist/maxTxDist;
            ss_weak = SS_WEAK;
            signalStrength = SS_STRONG + distFactor*(ss_weak - SS_STRONG);
          }
          
          if (distFactor < 1) { 
            if (intfRadio.getCurrentSignalStrength() < signalStrength) {
              intfRadio.setCurrentSignalStrength(signalStrength);
            }
          } else {
            intfRadio.setCurrentSignalStrength(ss_weak);
            if (intfRadio.getCurrentSignalStrength() < ss_weak) {
              intfRadio.setCurrentSignalStrength(ss_weak);
            }
          }
        }
        
        if (!intfRadio.isInterfered()) {
          /* Corner case */
          /* if the tag is already transmitting and the recv is interfered because the appropriate transmitting 
           * channel does not exist, when an active transmitter starts a new connection make sure that the recv 
           * gets interfered due to the reflections caused by the signal coming from that transmitter */
          if(conn.getSource().isBackscatterTag() && conn.getSource().isTransmitting()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(intfRadio.getChannel());
            double tagInterferenceRange = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);
            double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
            if (dist <= tagInterferenceRange) {
                intfRadio.interfereAnyReception();
            }
          } else {
            intfRadio.interfereAnyReception();
          }
        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
    }

  } /* uptadeSignalStrengths */

  
  private void removeFromActiveConnections(Radio radio) {
    /* 
     * When an active module (transmitter or carrier generator) is removed 
     * from an ongoing connection every tag that was receiving its signal or
     * its carrier respectively, updates the corresponding entry in its Hashtable
     * and stops any current signal reflection.
     */
//    if (!radio.isBackscatterTag()) {
      for (RadioConnection conn : getActiveConnections()) {
        if (conn.getSource() == radio) {
          for (Radio dstRadio : conn.getAllDestinations()) {
            if (conn.getDestinationDelay(dstRadio) == 0) {
              dstRadio.updateTagTXPower(conn);
              dstRadio.signalReceptionEnd();
            } else {
              /* EXPERIMENTAL: Simulating propagation delay */
              final Radio delayedRadio = dstRadio;
              TimeEvent delayedEvent = new TimeEvent(0) {
                public void execute(long t) {
                  delayedRadio.updateTagTXPower(conn);
                  delayedRadio.signalReceptionEnd();
                }
              };
              getSimulation().scheduleEvent(delayedEvent,
                  getSimulation().getSimulationTime() + conn.getDestinationDelay(dstRadio));
            }
          }
          
          for (Radio intRadio : conn.getInterferedNonDestinations()) {
            /* A connection having as source an active transmitter considers the tag as an interfered radio */ 
            intRadio.updateTagTXPower(conn);
            if (intRadio.isInterfered()) {
              intRadio.signalReceptionEnd();
            }
          }
          lastConnection = conn;
          getActiveConnectionsArrayList().remove(conn);
        }
      }

  } /* removeFromActiveConnections */
  
  @Override
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
    super.unregisterRadioInterface(radio, sim);
    
    removeFromActiveConnections(radio);
    
    radioMediumObservable.setChangedAndNotify();
    
    /* Update signal strengths */
    updateSignalStrengths();
    
  }

  
} /* UDGMBS */
