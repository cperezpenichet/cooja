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
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import java.util.HashSet;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.SimEventCentral.MoteCountListener;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.skins.UDGMBSVisualizerSkin;
import org.contikios.cooja.plugins.skins.UDGMVisualizerSkin;

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

  //public double SUCCESS_RATIO_TX = 1.0; /* Success ratio of TX. If this fails, no radios receive the packet */
  //public double SUCCESS_RATIO_RX = 1.0;
  //public double TRANSMITTING_RANGE = 50; /* Transmission range. */
  //public double INTERFERENCE_RANGE = 100; /* Interference range. Ignored if below transmission range. */
  
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

      /* Register visualizer skin */
      Visualizer.registerVisualizerSkin(UDGMBSVisualizerSkin.class);
    }
  
  public void removed() {
      super.removed();
      
      Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
  }
  
 
  public RadioConnection createConnections(Radio sender) {
      RadioConnection newConnection = new RadioConnection(sender);
/**/  System.out.println("\nUDGMBS.NewConnID: " + newConnection.getID());

      if (sender.isGeneratingCarrier()) {
/**/    System.out.println("carrierID: " + sender.getMote().getID() + " carrierCh: " + sender.getChannel());
      } else if (sender.isListeningCarrier()) {
/**/      System.out.println("tagID: " + sender.getMote().getID());          
      } else {
/**/      System.out.println("senderID: " + sender.getMote().getID() + " senderCh: " + sender.getChannel());
      }

      HashSet<Integer> txChannels = new HashSet<Integer>();
      int debugChannel = 0;

      /* Fail radio transmission randomly - no radios will hear this transmission */
      if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
        return newConnection;
      }
      
      /* Calculate ranges: grows with radio output power */
      double moteTransmissionRange = TRANSMITTING_RANGE *
      ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());
      
/**/  System.out.println("moteTransmissionRange: " + moteTransmissionRange);
      
      double moteInterferenceRange = INTERFERENCE_RANGE *
      ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**/  System.out.println("moteInterferenceRange: " + moteInterferenceRange);

      /* Calculate ranges: grows with radio output power */
      double carrierInterferenceRange = INTERFERENCE_RANGE *
      ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**/  System.out.println("carrierInterferenceRange: " + carrierInterferenceRange);

      /* Get all potential destination radios */
      DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
      if (potentialDestinations == null) {
        return newConnection;
      }
      
      /* In case a tag(sender) tries to create a new Connection */
      if (sender.isBackscatterTag() && sender.isListeningCarrier()) {
/**/      System.out.println("sender is a tag");
          
        /* 
         * Every tag that is listening to a carrier is interfered to the  
         * connection whose source generated that carrier.
         *  
         * Hence, check every active connection... 
         */
         
        for (RadioConnection conn : getActiveConnections()) {
/**/      System.out.println("A.conn: " + conn.getID());                              
          
          /* ... and also check to which connection the tag belongs. Then take the  
             channel of the source (carrier generator) of that connection, move 
             it two channels apart (+2) and store that channel to a set of channels. */  
          if (conn.isInterfered(sender)) {
/**/        System.out.println("tag: " + sender.getMote().getID() + " belongs to conn: " + conn.getID());                
            if (conn.getSource().isGeneratingCarrier()) { // TODO It might be interesting to reflect active transmissions as interference too
              if (conn.getSource().getChannel() >=0) {
/**/            System.out.println("carier.g: " +  conn.getSource().getMote().getID() + " of conn: " + conn.getID() +  " - Ch= " + conn.getSource().getChannel());                    
                txChannels.add(conn.getSource().getChannel() + 2);
/**/            System.out.println("Ch= " +  (conn.getSource().getChannel() + 2) + " is stored in txChannels");                  
              }    
            }
          } 
        } 
      } else if (sender.isBackscatterTag() && !sender.isListeningCarrier()) {
          return newConnection;  
      } 
      /* 
       * When the sender that is about to start a new connection is a carrier 
       * generator we don't care about storing its channel since it generates
       * a carrier based on a specific range and its channel is handled by the
       * case above, where the tag is about to start a new connection.
       * 
       * In this case we have a usual active transmission.
       */
      else if (!sender.isBackscatterTag() && !sender.isGeneratingCarrier()) {
/**/    System.out.println("sender: " + sender.getMote().getID() + " is a cc2420 radio");
        if (sender.getChannel() >=0) {
          txChannels.add(sender.getChannel());
/**/      System.out.println("Ch= " +  sender.getChannel() + " is stored in txChannels");               
        }
      }
      

      /* Loop through all potential destinations */
      Position senderPos = sender.getPosition();
/**/  System.out.println("UDGMBS.PotentialDestinations: " + potentialDestinations.length);
      for (DestinationRadio dest: potentialDestinations) {
/**/    System.out.printf("UDGMBS.PotDest = %d\n", dest.radio.getMote().getID());
        
        Radio recv = dest.radio;

        /*
         * When the recv is a common node carrying a CC2420 radio we have an active 
         * transmission either we are talking about a usual transmission between two
         * "CC2420" nodes or a transmission between a tag and
         */

        if (!recv.isBackscatterTag()) {
          debugChannel = recv.getChannel();
          if(recv.getChannel() >= 0 && !txChannels.contains(recv.getChannel())) {
                
/**/        System.out.println("sender - recv: diff channels");
/**/        System.out.println(sender.isListeningCarrier() ? "sender(tag): " + sender.getMote().getID() : "sender: " + sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/        System.out.println("recv: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());


            /* Add the connection in a dormant state;
               it will be activated later when the radio will bes
               turned on and switched to the right channel. This behavior
               is consistent with the case when receiver is turned off. */
            newConnection.addInterfered(recv);

            continue;
          }
        }
        
        Position recvPos = recv.getPosition();

        double distance = senderPos.getDistanceTo(recvPos);
/**/    System.out.println("senderRecvDistance: " + distance);
             
        if (sender.isGeneratingCarrier()) {
/**/      System.out.println("carrier.g: " + sender.getMote().getID() + " - isGeneratingCarrier: " + sender.isGeneratingCarrier());
/**/      System.out.println("carrier.g: " + sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**///      System.out.println("tag: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());
          if (distance <= carrierInterferenceRange) {
/**/        System.out.println("Within carrierInterferenceRange");
            newConnection.addInterfered(recv);
/**///        System.out.println("newConnection.getInterferedNonDestinations: " + newConnection.onlyInterfered.get(0).getMote().getID());           
/**/        System.out.println("tag: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
            recv.interfereAnyReception();
          }
        } else {
            if (distance <= moteTransmissionRange) {
/**/          System.out.println("WithinTR");                
/**/          System.out.println(sender.isListeningCarrier() ? "carrierChannel: " + (debugChannel -2) + " - tag: " + sender.getMote().getID() : "sender: " + sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/          System.out.println("recv: " +  recv.getMote().getID() + " - Ch= " + debugChannel);

              /* Within transmission range */
/**/          System.out.println("sender: " + sender.getMote().getID() + " isListeningCarrier: " + sender.isListeningCarrier());

              if (!recv.isRadioOn()) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - radio is off");
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
                recv.interfereAnyReception();
              } else if (recv.isInterfered()) {
/**/              System.out.println("recv: " + recv.getMote().getID() + " - isInterfered");
                  /* Was interfered: keep interfering */
                  newConnection.addInterfered(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              } else if (recv.isTransmitting()) {
/**/              System.out.println("recv: " + recv.getMote().getID() + " - isTransmitting");
                  newConnection.addInterfered(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              } else if (recv.isReceiving() || 
                  (random.nextDouble() > getRxSuccessProbability(sender, recv))) {
/**///              System.out.println("recv: " + recv.getMote().getID() + " - isReceiving");
                  /* Was receiving, or reception failed: start interfering */
                  newConnection.addInterfered(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
                  recv.interfereAnyReception();

                  /* Interfere receiver in all other active radio connections */
                  for (RadioConnection conn : getActiveConnections()) {
                    if (conn.isDestination(recv)) {
/**/                  System.out.println("recv: " + recv.getMote().getID() + " added as interfered to conn: " + conn.getID());
                      conn.addInterfered(recv);
                    }
                  }

              } else {
                  /* Success: radio starts receiving */
                  newConnection.addDestination(recv);
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as new destination to newConnection " + newConnection.getID());
              }
            } else if (distance <= moteInterferenceRange) {
/**/            System.out.println("WithinIR");
                /* Within interference range */
                newConnection.addInterfered(recv);
/**/            System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
                recv.interfereAnyReception();
            }
        }
 
      }

      return newConnection;
  }  
} /* createConnections */
