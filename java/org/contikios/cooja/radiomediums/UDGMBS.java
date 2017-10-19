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

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
//import org.contikios.cooja.mspmote.interfaces.Msp802154Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.skins.UDGMBSVisualizerSkin;
import org.contikios.cooja.plugins.skins.UDGMVisualizerSkin;

//import se.sics.mspsim.chip.CC2420;


/**
 * The Unit Disk Graph Radio Medium abstracts radio transmission range as circles.
 * 
 * It uses two different range parameters: one for transmissions, and one for
 * interfering with other radios and transmissions.
 * 
 * Both radio ranges grow with the radio output power indicator.
 * The range parameters are multiplied with [output power]/[maximum output power].
 * For example, if the transmission range is 100m, the current power indicator 
 * is 50, and the maximum output power indicator is 100, then the resulting transmission 
 * range becomes 50m.
 * 
 * For radio transmissions within range, two different success ratios are used [0.0-1.0]:
 * one for successful transmissions, and one for successful receptions.
 * If the transmission fails, no radio will hear the transmission.
 * If one of receptions fail, only that receiving radio will not receive the transmission,
 * but will be interfered throughout the entire radio connection.  
 * 
 * The received radio packet signal strength grows inversely with the distance to the
 * transmitter.
 *
 * @see #SS_STRONG
 * @see #SS_WEAK
 * @see #SS_NOTHING
 *
 * @see DirectedGraphMedium
 * @see UDGMVisualizerSkin
 * @author Fredrik Osterlind
 */
@ClassDescription("Unit Disk Graph Medium for Backscaterring Communications (UDGMBS): Distance Loss")
public class UDGMBS extends UDGM {
  private static Logger logger = Logger.getLogger(UDGMBS.class);
  
  public double SUCCESS_RATIO_TX = 1.0; /* Success ratio of TX. If this fails, no radios receive the packet */
  public double SUCCESS_RATIO_RX = 1.0; /* Success ratio of RX. If this fails, the single affected receiver does not receive the packet */
 
  /* Gain of the transmitting antenna */
  public final double GT = 0;
  /* Gain of the transmitting antenna */
  public final double GR = 0;
  /* Wavelength */
  public final double WAVELENGTH = 0.122;
  /* Energy loss */
  public final double ENERGYLOSS = 4.4;
  /* Reflection loss */
  public final double REFLECTIONLOSS = 15 - ENERGYLOSS;
  /* Sensitivity threshold for Tmote sky in dBm */
  public final double STH = -92 ;

  ArrayList<Double> carrierToTagDist = new ArrayList<Double>();
  ArrayList<Double> tagToRecvDist = new ArrayList<Double>();
  ArrayList<Double> receivedPowerLst = new ArrayList<Double>();
  
  private DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */
  
  private Random random = null;
  
  public UDGMBS(Simulation simulation) {
      super(simulation);
  /**/System.out.println("UDGMBS");
      random = simulation.getRandomGenerator();
      dgrm = new DirectedGraphMedium() {
        protected void analyzeEdges() {
  /**/    System.out.println("2.DirectedGraphMedium: " + dgrm);          
          /* Create edges according to distances.
           * XXX May be slow for mobile networks */
          clearEdges();
          for (Radio source: UDGMBS.this.getRegisteredRadios()) {
            System.out.println("UDGMBS.DirectedGraphMedium");  
            Position sourcePos = source.getPosition();
            for (Radio dest: UDGMBS.this.getRegisteredRadios()) {
              Position destPos = dest.getPosition();
              /* Ignore ourselves */
              if (source == dest) {
                continue;
              }
              double distance = sourcePos.getDistanceTo(destPos);
              if (distance < Math.max(TRANSMITTING_RANGE, INTERFERENCE_RANGE)) {
                /* Add potential destination */
                addEdge(
                    new DirectedGraphMedium.Edge(source, 
                        new DGRMDestinationRadio(dest)));
              }
            }
          }
          super.analyzeEdges();
        }
      };

      /* Register as position observer.
       * If any positions change, re-analyze potential receivers. */
      final Observer positionObserver = new Observer() {
        public void update(Observable o, Object arg) {
          dgrm.requestEdgeAnalysis();
        }
      };
      /* Re-analyze potential receivers if radios are added/removed. */
      simulation.getEventCentral().addMoteCountListener(new MoteCountListener() {
        public void moteWasAdded(Mote mote) {
          mote.getInterfaces().getPosition().addObserver(positionObserver);
          dgrm.requestEdgeAnalysis();
        }
        public void moteWasRemoved(Mote mote) {
          mote.getInterfaces().getPosition().deleteObserver(positionObserver);
          dgrm.requestEdgeAnalysis();
        }
      });
      for (Mote mote: simulation.getMotes()) {
        mote.getInterfaces().getPosition().addObserver(positionObserver);
      }
      dgrm.requestEdgeAnalysis();
      
      //this.addRadioTransmissionObserver(radioMediumConnectionActivityObserver);
      
      super.removed();

      /* Register visualizer skin */
      Visualizer.registerVisualizerSkin(UDGMBSVisualizerSkin.class);
      
  }
  
  public void removed() {
     // super.removed();

      Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
  }
  
  
//  public void setTagTxRange(double r) {
//    TAG_TRANSMITTING_RANGE = r;
//    dgrm.requestEdgeAnalysis();
//  }
//
//  public void setTagInterferenceRange(double r) {
//    TAG_INTERFERENCE_RANGE = r;
//    dgrm.requestEdgeAnalysis();
//  }
  
  
  /**
   * Returns the loss in signal strength of the propagation wave
   * 
   * @param distance
   */
  public double pathLoss(double distance) {
    return 20*Math.log10(WAVELENGTH / (4*Math.PI*distance));
  }
  
  public double friisEquation(Radio source, Radio dest) {
    double distance = source.getPosition().getDistanceTo(dest.getPosition());
/**/System.out.println("distance: " + distance);

    double transmitttedPower = 0.0;
    
    /* Transform power level into output power in dBm */
    for (int i =0; i < source.CC2420OutputPower.length; i++) {
      if((double) source.getCurrentOutputPowerIndicator() == i) {
        transmitttedPower = source.CC2420OutputPower[i];
/**/    System.out.println("transmitttedPower: " + transmitttedPower);
      }
    }
    double receivedPower = transmitttedPower + GT + GR + pathLoss(distance);
    return receivedPower;
  }
  
  public HashSet<Integer> getTXChannels(Radio sender) {
    /* Store the channels for an active or backscatter transmission */
    HashSet<Integer> txChannels = new HashSet<Integer>();
      
    /* 
     * Every tag that is listening to a carrier is interfered by the  
     * connection whose source generated that carrier.
     *  
     * Hence, check every active connection generated by a carrier generator
     */
    if(sender.isBackscatterTag()) {         
      for (RadioConnection conn : getActiveConnections()) {
/**/    System.out.println("A.conn: " + conn.getID() + " with sender: " + conn.getSource().getMote().getID());                              
              
        /* ... and also check to which connection the tag belongs. Then take the  
           channel of the source (carrier generator) of that connection, move 
           it two channels apart (+2) and store that channel to a set. */  
        if (conn.isDestination(sender)) {
/**/      System.out.println("tag: " + sender.getMote().getID() + " belongs to conn: " + conn.getID());                
          if (conn.getSource().isGeneratingCarrier()) { // TODO It might be interesting to reflect active transmissions as interference too
            if (conn.getSource().getChannel() >=0) {
/**/          System.out.println("carier.g: " +  conn.getSource().getMote().getID() + " of conn: " + conn.getID() +  " - Ch= " + conn.getSource().getChannel());                    
              txChannels.add(conn.getSource().getChannel() + 2);
/**/          System.out.println("Ch= " +  (conn.getSource().getChannel() + 2) + " is stored in txChannels");                  
            }    
          }
        } 
      }
    } else {
      /* Store the channel of the sender which is responsible for an active transmission */
/**/  System.out.println(sender.isGeneratingCarrier() ? "sender: " + sender.getMote().getID() + " is a carrier gen" 
                         : "sender: " + sender.getMote().getID() + " is a cc2420 radio");
      if (sender.getChannel() >= 0) {
        txChannels.add(sender.getChannel());
/**/    System.out.println("Ch= " +  sender.getChannel() + " is stored in txChannels");               
      }
    }
    return txChannels;
  }

  
  /*
   * Currently the tag does not have any receiving capabilities.
   * Hence, the tag is not affected by interferences, which is 
   * a case we took care during the implementation of the following 
   * method. 
   */
  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection;

    /* Store the channels to which the tag can transmit */
    HashSet<Integer> tagTXChannels = new HashSet<Integer>();
    
    if (!sender.isBackscatterTag()) {
      /* 
       * For an active transmission started by an active sender which may 
       * generate a carrier or not use the already implemented super() method  
       * for creating the new connection.
       */  
      newConnection = super.createConnections(sender);

      /* 
       * In case the sender is a carrier generator, every radio that was added
       * as destination, except for a tag, is removed from destinations and is 
       * added to interfered. Whereas, in case the sender is transmitting a packet
       * only a tag is moved from destinations to the interfered radios.
       */  
/**/  System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length);
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
      if(sender.isGeneratingCarrier()) {
/**/    System.out.println("sender:" + sender.getMote().getID() + " is a carrier generator");      
        
        for (Radio r: newConnection.getAllDestinations()) {
          if (!r.isBackscatterTag()) {
            newConnection.removeDestination(r);
/**/        System.out.println("r:" + r.getMote().getID() + " is removed from destinations");      
/**/        System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
            newConnection.addInterfered(r);
/**/        System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/        //System.out.println("r: " + r.getMote().getID() + " interfereAnyReception");
            //r.interfereAnyReception();
          } else {
            // Calculate the incident power that each tag receives from the carrier gen.
            // and keep a record of that power indexing it by the appropriate backscatter
            // channel derived by the carrier generator that created that incident power.
/**/        System.out.println();
/**/        System.out.println("Start keeping a record of the tagTXPower");
/**/        System.out.println("backTag: " + r.getMote().getID());
            double dist = sender.getPosition().getDistanceTo(r.getPosition());
            carrierToTagDist.add(dist);
/**/        System.out.println("dist: " + dist);
            /* Incident power in dBm */
            double incidentPower = friisEquation(sender, r);
/**/        System.out.println("incidentPower: " + incidentPower);
            /* Current power of the tag in dBm */
            double tagCurrentTXPower = incidentPower - REFLECTIONLOSS;
/**/        System.out.println("tagCurrentTXPower: " + tagCurrentTXPower);
/**/        System.out.println(newConnection);
            r.putTagTXPower(sender.getChannel() + 2, newConnection, tagCurrentTXPower);
/**/        System.out.println("Stop keeping a record of the tagTXPower");
/**/        System.out.println();
          } 
        } 
      } else {
/**/    System.out.println("sender:" + sender.getMote().getID() + " is an active sender");      
        for (Radio r: newConnection.getAllDestinations()) {
          if (r.isBackscatterTag()) {
            newConnection.removeDestination(r);
/**/        System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
            newConnection.addInterfered(r);
/**/        System.out.println("r:" + r.getMote().getID() + " is removed from destinations");      
/**/        System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/        System.out.println("r: " + r.getMote().getID() + " interfereAnyReception");
            //r.interfereAnyReception();
          }    
        }
      }
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
    } else {
      /* 
       * For an active connection created by a tag which is listening
       * the carrier create a new connection using the following procedure.
       */   
      newConnection = new RadioConnection(sender);
      
      /* Fail radio transmission randomly - no radios will hear this transmission */
      if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
        return newConnection;
      }
      
      if (sender.isListeningCarrier()) {
/**/    System.out.println("\nNewBackscatteringConnID: " + newConnection.getID());

        /* Collect the channels for backscattering communication */
        tagTXChannels = getTXChannels(sender);
          
/**/    System.out.println("tagConn: " + newConnection.getID() + " contains: " + tagTXChannels.size() + " channels which are");          

        Iterator<Integer> itr = tagTXChannels.iterator();
        while(itr.hasNext()) {
/**/      System.out.println("channel: " + itr.next());
        }
          
        /* Get all potential destination radios */
        DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
        if (potentialDestinations == null) {
          return newConnection;
        }
          
        /* Loop through all potential destinations */
        Position senderPos = sender.getPosition();
/**/    System.out.println("PotentialDestinations: " + potentialDestinations.length);
      
        for (DestinationRadio dest: potentialDestinations) {
/**/      System.out.printf("PotDest = %d\n", dest.radio.getMote().getID());

          Radio recv = dest.radio;

          if(recv.getChannel() >= 0 && !tagTXChannels.contains(recv.getChannel())) {
                      
/**/        System.out.println("sender - recv: diff channels");
/**/        System.out.println(sender.isListeningCarrier() ? "sender(tag): " + sender.getMote().getID() : "sender: " + sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/        System.out.println("recv: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());

            /* Add the connection in a dormant state;
               it will be activated later when the radio will bes
               turned on and switched to the right channel. This behavior
               is consistent with the case when receiver is turned off. */
            newConnection.addInterfered(recv);
          } else {
/**/        System.out.println("recvID: " + recv.getMote().getID());
/**/        System.out.println("recvChannel: " + recv.getChannel());
            double tagCurrentOutputPowerIndicator = sender.getTagCurrentOutputPower(recv.getChannel());
/**/        System.out.println("tagCurrentOutputPowerIndicator: " + tagCurrentOutputPowerIndicator);
            
            /* Calculate ranges: grows with radio output power in mW */
            double tagTransmissionRange = (Math.pow(10, (GT + GR + tagCurrentOutputPowerIndicator - STH 
                                                       + 20*Math.log10(WAVELENGTH / (4*Math.PI))) / 20));
            
/**/        System.out.println("tagTransmissionRange: " + tagTransmissionRange);

            double tagInterferenceRange = (Math.pow(10, (GT + GR + tagCurrentOutputPowerIndicator - (STH - 3)
                                                        + 20*Math.log10(WAVELENGTH / (4*Math.PI))) / 20));
            
//            double tagInterferenceRange = (Math.pow(10, (GT + GR - STH + 20*Math.log10(WAVELENGTH / (4*Math.PI))) / 20));
/**/        System.out.println("maxRange: " + tagInterferenceRange);

            Position recvPos = recv.getPosition();
            double distance = senderPos.getDistanceTo(recvPos);
            //tagToRecvDist.add(distance);
/**/        System.out.println("TagRecvDistance: " + distance);

            double receivedPower = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(distance);
            //receivedPowerLst.add(receivedPower);
/**/        //System.out.println("dest: " + recv.getMote().getID() + " - receivedPower: " + receivedPower);


/**/        //System.out.println("carrierToTagDist: " + carrierToTagDist);
/**/        //System.out.println("tagToRecvDist: " + tagToRecvDist);
/**/        //System.out.println("receivedPowerLst: " + receivedPowerLst);

  
            if (distance <= tagTransmissionRange) {
              /* Within transmission range */
/**/          System.out.println("WithinTR");
  
              if (!recv.isRadioOn()) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - radio is off");
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
                recv.interfereAnyReception();
              } else if (recv.isInterfered()) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - isInterfered");
                /* Was interfered: keep interfering */
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              } else if (recv.isTransmitting()) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - isTransmitting");
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              } else if (recv.isReceiving() || (random.nextDouble() > getRxSuccessProbability(sender, recv))) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - isReceiving");
                /* Was receiving, or reception failed: start interfering */
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
                recv.interfereAnyReception();

                /* Interfere receiver in all other active radio connections */
                for (RadioConnection conn : getActiveConnections()) {
                  if (conn.isDestination(recv)) {
/**/                System.out.println("recv: " + recv.getMote().getID() + " added as interfered to conn: " + conn.getID());
                    conn.addInterfered(recv);
                  }
                }
              } else {
                if(!recv.isBackscatterTag()) {
                  /* Success: radio starts receiving */
                  newConnection.addDestination(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as new destination to newConnection " + newConnection.getID());
                } else {
                  /* In case the receiver is a tag */  
                  newConnection.addInterfered(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as new destination to newConnection " + newConnection.getID());
                }
              }
            } else if (distance <= tagInterferenceRange) {
/**/          System.out.println("WithinIR");
              /* Within interference range */
              newConnection.addInterfered(recv);
/**/          System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              recv.interfereAnyReception();
            }
          }  
        } 
      } else {
        return newConnection;
      }
    }
/**/System.out.println("return newConnection");        
    return newConnection;    
  }
 
  @Override
  public double getRxSuccessProbability(Radio source, Radio dest) {
/**/System.out.println("UDGMBS.getRxSuccessProbability");

    double rxSuccessProbability = 0.0;

    if(!source.isBackscatterTag()) {
      rxSuccessProbability = super.getRxSuccessProbability(source, dest);
    } else {
      double distance = source.getPosition().getDistanceTo(dest.getPosition());
/**/  System.out.println("UDGMBS.distance: " + distance);
      double distanceSquared = Math.pow(distance,2.0);
/**/  System.out.println("UDGMBs.distanceSquared: " + distanceSquared);
      double tagCurrentOutputPowerIndicator = source.getTagCurrentOutputPower(dest.getChannel());
      
/**/  System.out.println();
/**/  System.out.println("UDGMBS.Power Indicator");
      double distanceMax = (Math.pow(10, (GT + GR + tagCurrentOutputPowerIndicator - STH + 20*Math.log10(WAVELENGTH / (4*Math.PI))) / 20));
/**/  System.out.println("UDGMBS.distanceMax: " + distanceMax);

      if (distanceMax == 0.0) {
        return 0.0;
      }      
      
      double distanceMaxSquared = Math.pow(distanceMax,2.0);

  /**/System.out.println("UDGMBs.distanceMaxSquared: " + distanceMaxSquared);

      double ratio = distanceSquared / distanceMaxSquared;
      /**/System.out.println("UDGMBS.ratio: " + ratio);
      
      if (ratio > 1.0) {
        return 0.0;
      }
/**/System.out.println("UDGMBS.SUCCESS_RATIO_RX: " + SUCCESS_RATIO_RX);

      rxSuccessProbability = 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
    }
    return rxSuccessProbability;
        
  }

  
  
  

  
  
  
//  @Override
//  /* A little bit of a Hack */
//  public double getRxSuccessProbability(Radio source, Radio dest) {
///**/System.out.println("UDGMBS.getRxSuccessProbability");
//
//    double rxSuccessProbability = 0.0;
//    
//    /* Store the usual transmitting range of the parent medium */
//    double transmittingRange = super.TRANSMITTING_RANGE;
//    
//    /*
//     * In case the source is  a tag assign the tag's transmitting range 
//     * to the transmitting range of the parent so as it can be used by
//     * the parent method
//     */
//    if (source.isBackscatterTag()) {
//      super.TRANSMITTING_RANGE = TAG_TRANSMITTING_RANGE;
//    }
//   
//    rxSuccessProbability = super.getRxSuccessProbability(source, dest);
//   
//    /* 
//     * Re-estate the stored transmitting range so as it can be used
//     * by cc2420 radios.
//     */
//    super.TRANSMITTING_RANGE = transmittingRange;
//   
//    return rxSuccessProbability;
//  }
  
 
  @Override
  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUpdate signal strengths");

    /* Reset signal strengths */
/**/System.out.printf("Reset signal strength \n");
    for (Radio radio : getRegisteredRadios()) {
      /* 
       * Update the Hashtable of the tag in case it is a 
       * destination to the connection that just finished
       */
      if(radio.isBackscatterTag()) {
/**/    System.out.println("IN" );
        
        radio.setCurrentSignalStrength(-100);
        RadioConnection lastConn = getLastConnection();
        if (lastConn != null) {
          if(lastConn.isDestination(radio)) {
/**/        System.out.println("1.lastConnID: " + lastConn.getID());
            radio.updateTagTXPowers(lastConn);
          }
        }
      } else {
        radio.setCurrentSignalStrength(getBaseRssi(radio));      
      }
/**/  System.out.printf("Reset Radio: %d, Signal strength: %.2f\n", radio.getMote().getID(), radio.getCurrentSignalStrength());

    }
    
    HashSet<Integer> txChannels = new HashSet<Integer>();
    
    /* Concerning connections created either by an active transmitter or a tag */

    /* Set signal strength to below strong on destinations */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
/**/  System.out.println("\nSet signal strength to below strong on destinations");
/**/  System.out.println("\nconn: " + conn.getID() + " - source: " + conn.getSource().getMote().getID());  
      txChannels = getTXChannels(conn.getSource());
        
      if(!conn.getSource().isBackscatterTag()) {
        if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
          conn.getSource().setCurrentSignalStrength(SS_STRONG);
/**/      System.out.printf("source = %d , signal = %.2f\n", conn.getSource().getMote().getID(), conn.getSource().getCurrentSignalStrength());
        }
      }
      
      for (Radio dstRadio : conn.getDestinations()) {
/**/    System.out.println("ActiveConnID: " + conn.getID());
        if(dstRadio.getChannel() >= 0 && !txChannels.contains(dstRadio.getChannel())) {
            continue;
        }

        double dist = conn.getSource().getPosition().getDistanceTo(dstRadio.getPosition());
/**/    System.out.printf("dist = %.2f\n", dist);

/**/    System.out.println("source: " + conn.getSource().getMote().getID());
        
        double signalStrength = 0.0;

        if (conn.getSource().isBackscatterTag()) {
          double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPower(dstRadio.getChannel());
          /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
          signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
          
        } else {
          if (!conn.getSource().isGeneratingCarrier()) {
/**/        System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
            
            double maxTxDist = TRANSMITTING_RANGE
            * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
            
/**/        System.out.printf("maxTxDist = %.2f\n", maxTxDist);
            
            double distFactor = dist/maxTxDist;
/**/        System.out.printf("distFactor = %.2f\n", distFactor);
            signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
          }
        }
        if (dstRadio.getCurrentSignalStrength() < signalStrength) {
          dstRadio.setCurrentSignalStrength(signalStrength);
/**/      System.out.printf("dstRadio = %d , signal = %.2f\n", dstRadio.getMote().getID(), dstRadio.getCurrentSignalStrength());
        }
        
        /* 
         * In case the tag stops listening the carrier from one connection but
         * it is still listening the carrier from another connection keep it signaled.
         */
        if(dstRadio.isBackscatterTag() && !dstRadio.isListeningCarrier()) {
/**/      System.out.println("dstRadio: " + dstRadio.getMote().getID() + " is not listening the carrier...but");
          if (conn.getSource().isGeneratingCarrier()) {
/**/        System.out.println("conn: " + conn.getID() + " is still active");
/**/        System.out.println("and its source: " + conn.getSource().getMote().getID() + " isGeneratingCarrier(): " 
                               + conn.getSource().isGeneratingCarrier());
            dstRadio.signalReceptionStart();
          }
        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
    }

    /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
/**/    System.out.println("\nSet signal strength to below weak on interfered");
/**/    System.out.println("\nconn: " + conn.getID() + " - source: " + conn.getSource().getMote().getID());  
        txChannels = getTXChannels(conn.getSource());
  
        
      for (Radio intfRadio : conn.getInterfered()) {
/**/    System.out.println("ActiveConnID: " + conn.getID());
/**/    System.out.printf("1.intfRadio = %d\n", intfRadio.getMote().getID()) ;        
        if(intfRadio.getChannel() >= 0 && !txChannels.contains(intfRadio.getChannel())) {
            continue;
        }

/**/    System.out.printf("2.intfRadio = %d\n", intfRadio.getMote().getID()) ;        
        
        double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
/**/    System.out.printf("dist = %.2f\n", dist);
        
/**/    System.out.println("source: " + conn.getSource().getMote().getID());
        
        if (conn.getSource().isBackscatterTag()) {
          double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPower(intfRadio.getChannel());
          
          /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
          double signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
          if (intfRadio.getCurrentSignalStrength() < signalStrength) {
            intfRadio.setCurrentSignalStrength(signalStrength);
  /**/      System.out.printf("1.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
          }
        } else {
          if (!conn.getSource().isGeneratingCarrier()) {
/**/        System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
            double maxTxDist = TRANSMITTING_RANGE
            * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
            
/**/        System.out.printf("maxTxDist = %.2f\n", maxTxDist);
            
            double distFactor = dist/maxTxDist;
/**/        System.out.printf("distFactor = %.2f\n", distFactor);
            
            if (distFactor < 1) {
              double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
              if (intfRadio.getCurrentSignalStrength() < signalStrength) {
                intfRadio.setCurrentSignalStrength(signalStrength);
/**/            System.out.printf("2.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
              }
            } else {
              intfRadio.setCurrentSignalStrength(SS_WEAK);
              if (intfRadio.getCurrentSignalStrength() < SS_WEAK) {
                intfRadio.setCurrentSignalStrength(SS_WEAK);
/**/            System.out.printf("3.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
              }
            }
          }
        }       
        if (!intfRadio.isInterfered()) {
          /* Note: The tag cannot get interfered */
          if (!intfRadio.isBackscatterTag()) {
            /*logger.warn("Radio was not interfered: " + intfRadio);*/
/**/        System.out.printf("intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
            intfRadio.interfereAnyReception();
          }
        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
    }

  } /* uptadeSignalStrengths */
  
  private void removeFromActiveConnections(Radio radio) {
/**/System.out.println("UDGMBS.removeFromActiveConnections");
    /* 
     * When a carrier generator is removed from an ongoing connection
     * every tag that was receiving the carrier updates the corresponding
     * entry in its Hashtable and stops any current backscatter transmission .
     */
    if (radio.isGeneratingCarrier()) {
      for (RadioConnection conn : getActiveConnections()) {
        if (conn.getSource() == radio) {
          for (Radio dstRadio : conn.getAllDestinations()) {
            if (conn.getDestinationDelay(dstRadio) == 0) {
/**/          System.out.println("dstRadio: " + dstRadio.getMote().getID() + "removed from conn " + conn);
/**/          System.out.println("dstRadio: " + dstRadio.getMote().getID() + " updates its Hashtable" );
              dstRadio.updateTagTXPowers(conn);
/**/          System.out.println("UDGMBS.dstRadio: " + dstRadio.getMote().getID() + " signalReceptionEnd");
              dstRadio.signalReceptionEnd();
            } else {
/**/          System.out.println("EXPERIMENTAL_TRANSMISSION_FINISHED");
              /* EXPERIMENTAL: Simulating propagation delay */
              final Radio delayedRadio = dstRadio;
              TimeEvent delayedEvent = new TimeEvent(0) {
                public void execute(long t) {
/**/              System.out.println("dstRadio: " + dstRadio.getMote().getID() + "removed from conn " + conn);
/**/              System.out.println("dstRadio: " + dstRadio.getMote().getID() + " updates its Hashtable" );
                  delayedRadio.updateTagTXPowers(conn);
/**/              System.out.println("UDGMBS.delayedRadio: " + delayedRadio.getMote().getID() + " signalReceptionEnd");
                  delayedRadio.signalReceptionEnd();
                }
              };
              getSimulation().scheduleEvent(delayedEvent,
                  getSimulation().getSimulationTime() + conn.getDestinationDelay(dstRadio));
            }
          }
          
          for (Radio intRadio : conn.getInterferedNonDestinations()) {
            if (intRadio.isInterfered()) {
/**/          System.out.println("UDGMBS.intfRadio: " + intRadio.getMote().getID() + " signalReceptionEnd");
              intRadio.signalReceptionEnd();
            }
          }
          getActiveConnectionsArrayList().remove(conn);
          setLastConnection(conn);
        }
      }
    }

    
  }
  
  @Override
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
/**/System.out.println("UDGMBS.unregisterRadioInterface");
    super.unregisterRadioInterface(radio, sim);
    
    removeFromActiveConnections(radio);
    
    radioMediumObservable.setChangedAndNotify();
    
    /* Update signal strengths */
    updateSignalStrengths();
    
  }
  
  
  
  
  
  
  
  
  
  
  
} /* createConnections */
