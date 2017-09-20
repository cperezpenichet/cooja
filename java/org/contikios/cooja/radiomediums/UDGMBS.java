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
 
  /* Gain of the transmitting antenna */
  public final double GT = 0;
  /* Gain of the transmitting antenna */
  public final double GR = 0;
  ///* Gain of the antenna of the backscatter tag */
  //public static final double GTAG = 3;
  
  /* Wavelength */
  public final double WAVELENGTH = 0.122;
  /* Energy loss */
  public final double ENERGYLOSS = 4.4;
  /* Reflection loss */
  public final double REFLECTIONLOSS = 15 - ENERGYLOSS;
  
  
  /* 
   * Backscatter tag has smaller TX and INTF ranges because of the reflection 
   * it causes to the incident wave transmitted by the carrier generator.
   */
  public double TAG_TRANSMITTING_RANGE = 50; /* Transmission range for tag */ 
  public double TAG_INTERFERENCE_RANGE = 100; /* Interference range for tag. Ignored if below transmission range. */ 
  
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
              if (distance < Math.max(TAG_TRANSMITTING_RANGE, TAG_INTERFERENCE_RANGE)) {
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
  
  public void setTagTxRange(double r) {
    TAG_TRANSMITTING_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }

  public void setTagInterferenceRange(double r) {
    TAG_INTERFERENCE_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }
  
  
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
  
  
  

//  private Observer radioMediumConnectionActivityObserver = new Observer() {
//    public void update(Observable obs, Object obj) {
//      for (Radio r: getRegisteredRadios()) {
//        if(r.isBackscatterTag()) {
//          Enumeration<Integer> channels = r.
//        }
//      }
//      
//      
//    }
//  };
  
  
//  private Observer radioEventsObserver = new Observer() {
//      public void update(Observable obs, Object obj) {
//          if (!(obs instanceof Radio)) {
//              logger.fatal("Radio event dispatched by non-radio object");
//              return;
//          }
//  };
  
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
  /**/      System.out.println("recvID: " + recv.getMote().getID());
  /**/      System.out.println("recvChannel: " + recv.getChannel());
            double tagCurrentOutputPowerIndicator = sender.getTagCurrentOutputPower(recv.getChannel());
 /**/       System.out.println("tagCurrentOutputPowerIndicator: " + tagCurrentOutputPowerIndicator);
            double tagCurrentOutputPowerIndicatorMax = sender.getTagCurrentOutputPowerMax(recv.getChannel()) 
                                                        + GT + GR - REFLECTIONLOSS;
            
/**/        System.out.println("tagCurrentOutputPowerIndicatorMax: " + tagCurrentOutputPowerIndicatorMax);

            System.out.println(Math.pow(10, (tagCurrentOutputPowerIndicator / 10)));
            System.out.println(Math.pow(10, (tagCurrentOutputPowerIndicatorMax / 10)));
  
            
            /* Calculate ranges: grows with radio output power in mW */
            double tagTransmissionRange = 30 * (TAG_TRANSMITTING_RANGE
            * (Math.pow(10, (tagCurrentOutputPowerIndicator / 10)) / Math.pow(10, (tagCurrentOutputPowerIndicatorMax / 10))));
            
  //          double tagTransmissionRange = TAG_TRANSMITTING_RANGE
  //          * (tagCurrentOutputPowerIndicator / tagCurrentOutputPowerIndicatorMax);
            
            
  /**/      System.out.println("tagTransmissionRange: " + tagTransmissionRange);
            
            double tagInterferenceRange = TAG_INTERFERENCE_RANGE
            * (Math.pow(10, (tagCurrentOutputPowerIndicator / 10)) / Math.pow(10, (tagCurrentOutputPowerIndicatorMax / 10)));
  
/**/        System.out.println("tagInterferenceRange: " + tagInterferenceRange);
  
            Position recvPos = recv.getPosition();
            double distance = senderPos.getDistanceTo(recvPos);
  /**/      System.out.println("senderRecvDistance: " + distance);
  
            if (distance <= tagTransmissionRange) {
              /* Within transmission range */
  /**/        System.out.println("WithinTR");
  //              System.out.println("sender: " + sender.getMote().getID() + " isListeningCarrier= " + sender.isListeningCarrier());
  
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
 
  
  
  
  
//  @Override 
//  public double getRxSuccessProbability(Radio source, Radio dest) {
//    HashSet<Integer> txChannels = new HashSet<Integer>();
//    
//    double distance = source.getPosition().getDistanceTo(dest.getPosition());
//  /**/System.out.println("UDGM.distance: " + distance);
//    double distanceSquared = Math.pow(distance,2.0);
//  /**/System.out.println("UDGM.distanceSquared: " + distanceSquared);
//  
//    double transmittingRange = 0.0;
//    double currentOutputPowerIndicator = 0.0;
//    double currentOutputPowerIndicatorMax = 0.0;
//    
//    if (source.isBackscatterTag()) {
//      txChannels = getTXChannels(source);
//  
//      if(dest.getChannel() >= 0 && txChannels.contains(dest.getChannel())) {
//  
//  /**/  System.out.println("UDGMBS.TAG_TRANSMITTING_RANGE: " + TAG_TRANSMITTING_RANGE);
//        transmittingRange = TAG_TRANSMITTING_RANGE;
//        currentOutputPowerIndicator = source.getTagCurrentOutputPower(dest.getChannel());
//        currentOutputPowerIndicatorMax = source.getTagCurrentOutputPowerMax(dest.getChannel())
//                                                 + GT + GR - REFLECTIONLOSS; 
//      } 
//    } else {
//  /**/System.out.println("UDGMBS.TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
//      transmittingRange = TRANSMITTING_RANGE;
//      return super.getRxSuccessProbability(source, dest);
//    }
//    
//  /**/System.out.println();
//  /**/System.out.println("UDGMBS.Power Indicator");
//  /**/System.out.println("UDGMBS.source.getCurrentOutputPowerIndicator(): " + currentOutputPowerIndicator);
//  /**/System.out.println("UDGMBS.source.getCurrentOutputPowerIndicatorMax(): " + currentOutputPowerIndicatorMax);
//  
//  /**/System.out.println("UDGMBS.TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
//    
//    double distanceMax = transmittingRange
//    * (Math.pow(10, (currentOutputPowerIndicator / 10)) / Math.pow(10, (currentOutputPowerIndicatorMax / 10)));    
//    
//    if (distanceMax == 0.0) {
//      return 0.0;
//    }
//
///**/System.out.println("UDGMBS.distanceMax: " + distanceMax);
//
//  double distanceMaxSquared = Math.pow(distanceMax,2.0);
//
///**/System.out.println("UDGMBS.distanceMaxSquared: " + distanceMaxSquared);
//
//  double ratio = distanceSquared / distanceMaxSquared;
///**/System.out.println("UDGMBS.ratio: " + ratio);
//  
//  if (ratio > 1.0) {
//    return 0.0;
//  }
//  
///**/System.out.println("UDGMBS.SUCCESS_RATIO_RX: " + SUCCESS_RATIO_RX);
//
//  
//  return 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
//  }

  
  
  
  @Override
  /* A little bit of a Hack */
  public double getRxSuccessProbability(Radio source, Radio dest) {
    double rxSuccessProbability = 0.0;
    
    /* Store the usual transmitting range of the parent medium */
    double transmittingRange = super.TRANSMITTING_RANGE;
    
    /*
     * In case the source is  a tag assign the tag's transmitting range 
     * to the transmitting range of the parent so as it can be used by
     * the parent method
     */
    if (source.isBackscatterTag()) {
      super.TRANSMITTING_RANGE = TAG_TRANSMITTING_RANGE;
    }
   
    rxSuccessProbability = super.getRxSuccessProbability(source, dest);
   
    /* 
     * Re-estate the stored transmitting range so as it can be used
     * by cc2420 radios.
     */
    super.TRANSMITTING_RANGE = transmittingRange;
   
    return rxSuccessProbability;
  }
  
 
  @Override
  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUpdate signal strengths");

    /* Reset signal strengths */
/**/System.out.printf("Reset signal strength \n");
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(getBaseRssi(radio));      
/**/  System.out.printf("Reset Radio: %d, Signal strength: %.2f\n", radio.getMote().getID(), radio.getCurrentSignalStrength());
      
      RadioConnection lastConn = getLastConnection();
      if (lastConn != null) {
        if(radio.isBackscatterTag()) {
          if(lastConn.isDestination(radio)) {
/**/        System.out.println("1.lastConnID: " + lastConn.getID());
            radio.updateTagTXPowers(lastConn);
          }
        }
      }
    }
    
    HashSet<Integer> txChannels = new HashSet<Integer>();
    
    /* Concerning connections created either by an active transmitter or a tag */

    /* Set signal strength to below strong on destinations */
    RadioConnection[] conns = getActiveConnections();
    
    for (RadioConnection conn : conns) {
/**/  System.out.println("\nSet signal strength to below strong on destinations");
/**/  System.out.println("\nconn: " + conn.getID() + " - source: " + conn.getSource().getMote().getID());  
      txChannels = getTXChannels(conn.getSource());
        
      if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
        conn.getSource().setCurrentSignalStrength(SS_STRONG);
/**/    System.out.printf("source = %d , signal = %.2f\n", conn.getSource().getMote().getID(), conn.getSource().getCurrentSignalStrength());
      }
      for (Radio dstRadio : conn.getDestinations()) {
/**/    System.out.println("ActiveConnID: " + conn.getID());
        if(dstRadio.getChannel() >= 0 && !txChannels.contains(dstRadio.getChannel())) {
            continue;
        }

        double dist = conn.getSource().getPosition().getDistanceTo(dstRadio.getPosition());
/**///    System.out.printf("dist = %.2f\n", dist);

        double transmittingRange = 0.0;
        double currentOutputPowerIndicator = 0.0;
        double currentOutputPowerIndicatorMax = 0.0;
        
/**/    System.out.println("source: " + conn.getSource().getMote().getID());
        
        if (conn.getSource().isBackscatterTag()) {
/**/      System.out.println("TAG_TRANSMITTING_RANGE: " + TAG_TRANSMITTING_RANGE);
          transmittingRange = TAG_TRANSMITTING_RANGE;
          currentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPower(dstRadio.getChannel());
          currentOutputPowerIndicatorMax = conn.getSource().getTagCurrentOutputPowerMax(dstRadio.getChannel())
                                            + GT + GR - REFLECTIONLOSS; 
        } else {
/**/      System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
          transmittingRange = TRANSMITTING_RANGE;
          currentOutputPowerIndicator = (double) conn.getSource().getCurrentOutputPowerIndicator();
          currentOutputPowerIndicatorMax = (double) conn.getSource().getOutputPowerIndicatorMax();
        }
        double maxTxDist = transmittingRange
        * (Math.pow(10, (currentOutputPowerIndicator / 10)) / Math.pow(10, (currentOutputPowerIndicatorMax / 10)));    

/**/    System.out.printf("maxTxDist = %.2f\n", maxTxDist);
        
        double distFactor = dist/maxTxDist;
/**/    System.out.printf("distFactor = %.2f\n", distFactor);

        double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
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

        double transmittingRange = 0.0;
        double currentOutputPowerIndicator = 0.0;
        double currentOutputPowerIndicatorMax = 0.0;
        
/**/    System.out.println("source: " + conn.getSource().getMote().getID());
        
        if (conn.getSource().isBackscatterTag()) {
/**/      System.out.println("TAG_TRANSMITTING_RANGE: " + TAG_TRANSMITTING_RANGE);
          transmittingRange = TAG_TRANSMITTING_RANGE;
          currentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPower(intfRadio.getChannel());
          currentOutputPowerIndicatorMax = conn.getSource().getTagCurrentOutputPowerMax(intfRadio.getChannel()) 
                                            - GT - GR - REFLECTIONLOSS; 
        } else {
/**/      System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
          transmittingRange = TRANSMITTING_RANGE;
          currentOutputPowerIndicator = (double) conn.getSource().getCurrentOutputPowerIndicator();
          currentOutputPowerIndicatorMax = (double) conn.getSource().getOutputPowerIndicatorMax();
        }        
        
        double maxTxDist = transmittingRange
        * (Math.pow(10, (currentOutputPowerIndicator / 10)) / Math.pow(10, (currentOutputPowerIndicatorMax / 10)));    
        
        double distFactor = dist/maxTxDist;
/**/    System.out.printf("distFactor = %.2f\n", distFactor);

        if (distFactor < 1) {
          double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
          if (intfRadio.getCurrentSignalStrength() < signalStrength) {
            intfRadio.setCurrentSignalStrength(signalStrength);
/**/        System.out.printf("intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
          }
        } else {
          intfRadio.setCurrentSignalStrength(SS_WEAK);
          if (intfRadio.getCurrentSignalStrength() < SS_WEAK) {
            intfRadio.setCurrentSignalStrength(SS_WEAK);
/**/        System.out.printf("intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
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
  
  
} /* createConnections */
