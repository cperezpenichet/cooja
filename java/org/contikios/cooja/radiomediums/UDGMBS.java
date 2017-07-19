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

  private ArrayList<RadioConnection> activeConnectionsFromCarrier = new ArrayList<RadioConnection>();
  
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
      
//      ((UDGM)UDGMBS.this).radioEventsObserver = new Observer() {
//          public void update(Observable obs, Object obj) {
//              if (!(obs instanceof Radio)) {
//                  logger.fatal("Radio event dispatched by non-radio object");
//                  return;
//              }
//    /**/      System.out.println("UDGMBS.observable");
//                
//              (UDGMBS.this).legacyRadioObserver.update(obs, obj); 
//              
//          }
//      };
    }
  
 // private Observer legacyRadioObserver = ((UDGM)UDGMBS.this).radioEventsObserver;
  
  
  public void removed() {
      super.removed();
      
      Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
  }

  private void removeFromActiveConnections(Radio radio) {
      /* This radio must not be a connection source */
      RadioConnection connection = getActiveConnectionFrom(radio);
      if (connection != null) {
          logger.fatal("Connection source turned off radio: " + radio);
      }
      
      /* Set interfered if currently a connection destination */
      for (RadioConnection conn : activeConnectionsFromCarrier) {
          if (conn.isDestination(radio)) {
              conn.addInterfered(radio);
              if (!radio.isInterfered()) {
                  radio.interfereAnyReception();
              }
          }
      }
  }
  
  
  /**
   * @return All active connections created by a radio which acts as
   *         a carrier generator.
   */
  public RadioConnection[] getActiveConnectionsFromCarrier() {
      /* NOTE: toArray([0]) creates array and handles synchronization */
      return activeConnectionsFromCarrier.toArray(new RadioConnection[0]);
  }

  private RadioConnection getActiveConnectionFrom(Radio source) {
      for (RadioConnection conn : activeConnectionsFromCarrier) {
          if (conn.getSource() == source) {
              return conn;
          }
      }
      return null;
  }
  
  private Observer radioEventsObserver = new Observer() {
      public void update(Observable obs, Object obj) {
          if (!(obs instanceof Radio)) {
              logger.fatal("Radio event dispatched by non-radio object");
              return;
          }
          
/**/      System.out.println("UDGMBS.radioEventsObserver");
          
          Radio radio = (Radio) obs;
          
          final Radio.RadioEvent event = radio.getLastEvent();
          
/**/      System.out.println("UDGMBS.radio: " + radio.getMote().getID() + " - event= " + event);

          switch (event) {
              case CARRIER_LISTENING_STARTED:
              case CARRIER_LISTENING_STOPPED:
                  break;
              
              case CARRIER_STARTED: {
  /**/            System.out.println("radio= " + radio.getMote().getID() + " - CARRIER_STARTED");
                      
                  RadioConnection newConnection = createConnections(radio);
                  activeConnectionsFromCarrier.add(newConnection);
                      
                      
  /**/            System.out.println("ActiveConnections: " + activeConnectionsFromCarrier.size());
  /**/            System.out.println("newConnectionID: " + newConnection.getID());
  /**/            System.out.println("AllDestinations: " + newConnection.getAllDestinations().length);
  /**/            System.out.println("InterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
                  
                  for (Radio intfRadio: newConnection.getInterferedNonDestinations()) {
                    if (intfRadio.isInterfered()) {
                      if (!intfRadio.isListeningCarrier()) {    
  /**/                  System.out.println("intfRadio: " + intfRadio.getMote().getID() + "  - carrierListeningStart");                          
                        intfRadio.carrierListeningStart();
                      }
                    }  
                  }
                  
                  /* Update signal strength */
                  updateSignalStrengths();
                  
                  /* Notify observers */
                  radioTransmissionObservable.setChangedAndNotify();
              }
              break;
                  
              case CARRIER_STOPPED: { 
  /**/            System.out.println("radio= " + radio.getMote().getID() + " - CARRIER_STOPPED");
                  
                  RadioConnection connection = getActiveConnectionFrom(radio);
                  
                  if(connection == null) {
                      logger.fatal("No radio connection found");
                          return;
                  }
                      
                  activeConnectionsFromCarrier.remove(connection);
                      
/**/              System.out.println("carrier stops from conn: " + connection.getID());                   

                  boolean isStillInterfered = false;
                      
                  for (Radio intfRadio: connection.getInterferedNonDestinations()) {
/**/                System.out.println("conn: " + connection.getID() + " - carier.gen: " + radio.getMote().getID() + " - intfRadio: " + intfRadio.getMote().getID());                        
                    if (intfRadio.isInterfered()) {
                      if (intfRadio.isBackscatterTag()) {
/**/                      System.out.println("intfRadio: " + intfRadio.getMote().getID() + " of last conn: " + connection.getID() + " isInterfered");
/**/                      System.out.println("ActiveConnections: " + getActiveConnections().length);
                          for(RadioConnection conn: getActiveConnectionsFromCarrier()) {
                            if(conn.isInterfered(intfRadio)) {
/**/                          System.out.println("intfRadio: " + intfRadio.getMote().getID() + " of active conn: " + conn.getID() + " isInterfered");
                              isStillInterfered = true;
                              break;
                            }
/**/                        System.out.println("nonTagConnections: " + isStillInterfered);
                          }
                          
                          if(!isStillInterfered) {
/**/                        System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - carrierListeningEnd");
                            intfRadio.carrierListeningEnd();
                          }
                      } else {
/**/                      System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - signalReceptionEnd");               
                          intfRadio.signalReceptionEnd();
                      }  
                    } 
                  }
                    
                  /* Update signal strength */
                  updateSignalStrengths();
/**/              System.out.println("connection: " + connection.getID() + " is about to finish");
                  
                  /* Notify observers */
                  radioTransmissionObservable.setChangedAndNotify();
  /**/            System.out.println("connection: " + connection.getID() + " finished");
              }
              break;
              
              default:
                  logger.fatal("Unsupported radio event: " + event);
          }            
      }
  };
      
      
  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection = super.createConnections(sender);
      
/**/System.out.println("\nUDGMBS.NewConnID: " + newConnection.getID());

    HashSet<Integer> txChannels = new HashSet<Integer>();
    int debugChannel = 0;

    /* Fail radio transmission randomly - no radios will hear this transmission */
    if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
      return newConnection;
    }
      
    /* Calculate ranges: grows with radio output power */
    double moteTransmissionRange = TRANSMITTING_RANGE *
      ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());
      
/**/System.out.println("moteTransmissionRange: " + moteTransmissionRange);
      
    double moteInterferenceRange = INTERFERENCE_RANGE *
      ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**/System.out.println("moteInterferenceRange: " + moteInterferenceRange);
    
//    /* Get all potential destination radios */
//    DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
//    if (potentialDestinations == null) {
//      return newConnection;
//    }
    
///**/System.out.println("sender: " + sender.getMote().getID() + " is a cc2420 radio");
//    if (sender.getChannel() >=0) {
//      txChannels.add(sender.getChannel());
///**/  System.out.println("Ch= " +  sender.getChannel() + " is stored in txChannels");               
//    }
    
        
    if (sender.isGeneratingCarrier()) {
      //RadioConnection newConnection = new RadioConnection(sender);
      //RadioConnection newConnection = super.createConnections(sender);
      
      for (Radio r: newConnection.getDestinations()) {
          newConnection.addInterfered(r);
          r.interfereAnyReception();
      }
    }  
      
      
        
        
    return newConnection;    
    }

      
  
  
  public void registerRadioInterface(Radio radio, Simulation sim) {
      super.registerRadioInterface(radio, sim);
      
/**/  System.out.println("UDGMBS.registerRadioInterface");      
      
      radio.addObserver(radioEventsObserver);
      
      radioMediumObservable.setChangedAndNotify();
      
      /* Update signal strengths */
      updateSignalStrengths();
  }
  
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
      super.unregisterRadioInterface(radio, sim);

/**/  System.out.println("UDGMBS.unregisterRadioInterface");
      
      radio.deleteObserver(radioEventsObserver);
      
      removeFromActiveConnections(radio);
      
      radioMediumObservable.setChangedAndNotify();
      
      /* Update signal strengths */
      updateSignalStrengths();
  }
  
} /* createConnections */
