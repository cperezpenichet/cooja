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
import java.util.Iterator;

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

  private ArrayList<RadioConnection> activeConnectionsFromCarrier = new ArrayList<RadioConnection>();
  
//  private RadioConnection lastConnection = null;
  
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

      /* Register visualizer skin */
      Visualizer.registerVisualizerSkin(UDGMBSVisualizerSkin.class);
      
    }
  
  
  public void removed() {
      super.removed();

      Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
  }
  
  public void setTxRange(double r) {
      TRANSMITTING_RANGE = r;
      dgrm.requestEdgeAnalysis();
    }

    public void setInterferenceRange(double r) {
      INTERFERENCE_RANGE = r;
      dgrm.requestEdgeAnalysis();
    }
  
  
  //It might be deleted because in a connection created by a carrier generator radio will never be a destination
  private void removeFromActiveConnections(Radio radio) {
/**/  System.out.println("UDGMBS.removeFromActiveConnections");      
      /* This radio must not be a connection source */
      RadioConnection connection = getActiveConnectionFromCarrier(radio);
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

  private RadioConnection getActiveConnectionFromCarrier(Radio source) {
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
            
            case UNKNOWN:  
            case HW_ON: {
/**/          System.out.println("\nradio= " + radio.getMote().getID() + " - HW_ON");

              if(radio.isInterfered()) {
                if(!radio.isBackscatterTag()) {
/**/              System.out.println("HW was off when radio: " + radio.getMote().getID() + " was signaled and now that it is on it is signaled again");                      
/**/              System.out.println("radio: " + radio.getMote().getID() + " - interfereAnyReception");
                  radio.interfereAnyReception();
                } 
//                else {
///**/                System.out.println("HW was off when radio: " + radio.getMote().getID() + "was about to start listening the carrier and now that it is on it is signaled again");/**/                System.out.println("radio: " + radio.getMote().getID() + " - carrierListeningStart"); 
///**/                System.out.println("radio: " + radio.getMote().getID() + " - carrierListeningStart");
//                    radio.carrierListeningStart();
//                }
              }
            
              /* Update signal strength */
              updateSignalStrengths();
            }
            break;
            case HW_OFF: {
/**/          System.out.println("\nradio= " + radio.getMote().getID() + " - HW_OFF");                
                 
              /* The radio is about to stop generating its carrier(or turn off the radio). If it is still
               * interfered by any other connection stop it and signal every one of them to remove it from
               * their interfered radios as well.
               */ 
              if(radio.isInterfered()) {
                if(!radio.isBackscatterTag()) {
/**/              System.out.println("HW is off and radio: " + radio.getMote().getID() + " stops its reception(not being interfered)");
/**/              System.out.println("radio: " + radio.getMote().getID() + " - signalReceptionEnd");
                  radio.signalReceptionEnd();
                } 
//                else {
///**/                System.out.println("HW is off and radio: " + radio.getMote().getID() + " stops listening the carrier(not being interfered)");
///**/                System.out.println("radio: " + radio.getMote().getID() + " - carrierListeningEnd");
//                    radio.carrierListeningEnd();
//                }
                    
//                for(RadioConnection conn: activeConnectionsFromCarrier) {
//                  if(conn.isInterfered(radio)) {
///**/                System.out.println("\nradio: " + radio.getMote().getID() + " is also Interfered in carrierConn: " + conn.getID());
///**/                System.out.println("carrierConn: " + conn.getID() + " - getInterferedNonDestinations: " + conn.getInterferedNonDestinations().length);
//                    conn.removeInterfered(radio);
///**/                System.out.println("carrierConn: " + conn.getID() + " - getInterferedNonDestinations: " + conn.getInterferedNonDestinations().length);
//                  }
//                }
              }
            
              /* Remove any radio connections from this radio */
              removeFromActiveConnections(radio);
              /* Update signal strength */
              updateSignalStrengths();
            }
            break;
            
            case CARRIER_STARTED: {
/**/          System.out.println("\nradio= " + radio.getMote().getID() + " - CARRIER_STARTED");
                  
//              if(radio.isInterfered()) {
///**/            System.out.println("radio: " + radio.getMote().getID() + " was interfered by another connection and now it is signaled");                      
//                radio.interfereAnyReception();
//              }
                      
              RadioConnection newConnection = createConnections(radio);
              activeConnectionsFromCarrier.add(newConnection);
                      
                      
/**/          System.out.println("\nActiveConnectionsFromCarrier: " + activeConnectionsFromCarrier.size());
/**/          System.out.println("carrierConnID: " + newConnection.getID());
/**/          System.out.println("carrierConn: " + newConnection.getID() + " - AllDestinations: " + newConnection.getAllDestinations().length);
/**/          System.out.println("carrierConn: " + newConnection.getID() + " - InterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
                  
              for (Radio intfRadio: newConnection.getInterferedNonDestinations()) {
                if (intfRadio.isInterfered() && intfRadio.isBackscatterTag()) {
/**/              System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - isListeningCarrier(): " + intfRadio.isListeningCarrier());                    
                  if (!intfRadio.isListeningCarrier()) { //this is true for active recvs as well but the carrier   
/**/                System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - carrierListeningStart");                          
                    intfRadio.carrierListeningStart();
                  }
                }  
              }
                  
              /* Update signal strength */
              updateSignalStrengths();
                  
              /* Notify observers */
//              lastConnection = null;
              radioTransmissionObservable.setChangedAndNotify(); //Maybe it is not needed here as long arrows are not drawn for carrer start
            }
            break;
            case CARRIER_STOPPED: { 
/**/          System.out.println("\nradio= " + radio.getMote().getID() + " - CARRIER_STOPPED");
                  
              RadioConnection connection = getActiveConnectionFromCarrier(radio);
                  
              if(connection == null) {
                logger.fatal("No radio connection found");
                return;
              }
                  
/**/          System.out.println("\nActiveConnectionsFromCarrier: " + getActiveConnectionsFromCarrier().length);
/**/          System.out.println("carrier stops from conn: " + connection.getID()); 
              activeConnectionsFromCarrier.remove(connection);
//              lastConnection = connection;
/**/          System.out.println("ActiveConnectionsFromCarrier left: " + getActiveConnectionsFromCarrier().length);                  
                      
              boolean isStillInterfered = false;
                  
                  
              for (Radio intfRadio: connection.getInterferedNonDestinations()) {
                if (intfRadio.isInterfered()) {
                  if(!intfRadio.isBackscatterTag()) {
/**/                System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - signalReceptionEnd");                         
                    intfRadio.signalReceptionEnd(); 
                  } else {
/**/                  System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - carrierListeningEnd");                         
                      intfRadio.carrierListeningEnd();
                  }
                }
              }
                    
              /* Update signal strength */
              updateSignalStrengths();
/**/          System.out.println("connection: " + connection.getID() + " is about to finish");
                  
              /* Notify observers */
              radioTransmissionObservable.setChangedAndNotify(); //Maybe it is not needed here as long arrows are not drawn for carrier stop
  /**/        System.out.println("connection: " + connection.getID() + " finished");
            }
            break;
            
            /* When a radio interface event is detected both radioEventsObserver observers in UDGMBS and 
             * AbstractRadioMedium classes are called.
             * 
             * These empty additions intend to make the default fatal message disappeared for cases the 
             * are not being updated by the observer in this class.
             */
            case RECEPTION_STARTED:
            case RECEPTION_INTERFERED:
            case RECEPTION_FINISHED:
            case TRANSMISSION_STARTED: 
            case TRANSMISSION_FINISHED: 
            case CUSTOM_DATA_TRANSMITTED:
            case PACKET_TRANSMITTED:
                break;            
            
            default:
              logger.fatal("Unsupported radio event: " + event);
        }            
    }
  };
  
  public HashSet<Integer> getTXChannels(Radio sender) {
    /* Store the channels for an active or backscatter transmission */
    HashSet<Integer> txChannels = new HashSet<Integer>();
      
    /* 
     * Every tag that is listening to a carrier is interfered to the  
     * connection whose source generated that carrier.
     *  
     * Hence, check every active connection... 
     */
    if(sender.isBackscatterTag()) {         
      for (RadioConnection conn : getActiveConnectionsFromCarrier()) {
/**/    System.out.println("A.conn: " + conn.getID() + " with sender: " + conn.getSource().getMote().getID());                              
              
        /* ... and also check to which connection the tag belongs. Then take the  
           channel of the source (carrier generator) of that connection, move 
           it two channels apart (+2) and store that channel to a set. */  
        if (conn.isInterfered(sender)) {
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
        /* Store the channel of the sender who is responsible for an active transmissions */
/**/    System.out.println("sender: " + sender.getMote().getID() + " is a cc2420 radio");
        if (sender.getChannel() >=0) {
          txChannels.add(sender.getChannel());
/**/      System.out.println("Ch= " +  sender.getChannel() + " is stored in txChannels");               
        }
    }
    return txChannels;
  }
  
      
  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection;

    /* Store the channels to which the tag can transmit */
    HashSet<Integer> tagTXChannels = new HashSet<Integer>();
    
   int debugChannel = 0;
    
    if (!sender.isBackscatterTag()) {
      /* 
       * For an active transmission with a sender which may generate
       * a carrier or not use the already implemented super() method 
       * for creating the new connection.
       */  
      newConnection = super.createConnections(sender);
/**/  System.out.println("sender:" + sender.getMote().getID() + " is a carrier generator");      

      /* 
       * If the receiver is a tag, within the transmission range of the sender, which  is either 
       * transmitting a package or generating a carrier, it is initially added as a destination 
       * concerning the newConnection. This is happening because in backscattering communication 
       * the channel of the tag is not being concerned. Hence, the getChannel() method of the tag
       * always returns -1 avoiding this way the usual channel check.
       *  
       * Hence, if they are added as destinations we are removing them from destinations and adding
       * them in the interfered radios following the procedure below.
       */  
/**/  System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length);
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
      if(sender.isGeneratingCarrier()) {
        for (Radio r: newConnection.getAllDestinations()) {
          newConnection.removeDestination(r);
/**/      System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
          newConnection.addInterfered(r);
/**/      System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/      System.out.println("r: " + r.getMote().getID() + " interfereAnyReception");
          r.interfereAnyReception();
        } 
      } else {
        for (Radio r: newConnection.getAllDestinations()) {
          if (r.isBackscatterTag()) {
            newConnection.removeDestination(r);
/**/        System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
            newConnection.addInterfered(r);
/**/        System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/        System.out.println("r: " + r.getMote().getID() + " interfereAnyReception");
           // r.interfereAnyReception();
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

        /* Calculate ranges: grows with radio output power */
        double moteTransmissionRange = TRANSMITTING_RANGE *
                ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**///    System.out.println("moteTransmissionRange: " + moteTransmissionRange);

        double moteInterferenceRange = INTERFERENCE_RANGE *
                ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**///    System.out.println("moteInterferenceRange: " + moteInterferenceRange);
            
          
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

//            if (!recv.isBackscatterTag()) {
            
          debugChannel = recv.getChannel();
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
            Position recvPos = recv.getPosition();
            double distance = senderPos.getDistanceTo(recvPos);
/**/        System.out.println("senderRecvDistance: " + distance);

            if (distance <= moteTransmissionRange) {
              /* Within transmission range */
/**/          System.out.println("WithinTR");
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
            } else if (distance <= moteInterferenceRange) {
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
  

  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUpdate signal strengths");
    
    /* Reset signal strengths */
/**/System.out.printf("Reset signal strength \n");
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(getBaseRssi(radio));      
/**/  System.out.printf("Reset Radio: %d, Signal strength: %.2f\n", radio.getMote().getID(), radio.getCurrentSignalStrength());
    }
    
    HashSet<Integer> txChannels = new HashSet<Integer>();
    
    /* Concerning connections created either by an active transmitter on a tag */

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

        double maxTxDist = TRANSMITTING_RANGE
        * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
/**///    System.out.printf("maxTxDist = %.2f\n", maxTxDist);
        
        double distFactor = dist/maxTxDist;
/**///    System.out.printf("distFactor = %.2f\n", distFactor);

        double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
        if (dstRadio.getCurrentSignalStrength() < signalStrength) {
          dstRadio.setCurrentSignalStrength(signalStrength);
/**/      System.out.printf("dstRadio = %d , signal = %.2f\n", dstRadio.getMote().getID(), dstRadio.getCurrentSignalStrength());
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
/**///    System.out.printf("dist = %.2f\n", dist);

        double maxTxDist = TRANSMITTING_RANGE
        * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
/**///    System.out.printf("maxTxDist = %.2f\n", maxTxDist);
        
        double distFactor = dist/maxTxDist;
/**///    System.out.printf("distFactor = %.2f\n", distFactor);

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
          /*logger.warn("Radio was not interfered: " + intfRadio);*/
/**/      System.out.printf("intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
          intfRadio.interfereAnyReception();
        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
      
    } /* Concerning connections created either by an active transmitter on a tag */
    
    /* Concerning connections created by a carrier generator */
/**/System.out.println("\nUpdate signal strengths for radios belonging to connections generated by a carrier");    
    RadioConnection[] connFromCarrier = getActiveConnectionsFromCarrier();
    
      /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : connFromCarrier) {
/**/  System.out.println("\nUDGMBS.Set signal strength to below weak on interfered");
/**/  System.out.println("\nconnFromCarrier: " + conn.getID() + " - source: " + conn.getSource().getMote().getID());  
      for (Radio intfRadio : conn.getInterfered()) {
/**/    System.out.println("UDGMBS.ActiveConnID: " + conn.getID());

        if (conn.getSource().getChannel() >= 0 && intfRadio.getChannel() >= 0 &&
            conn.getSource().getChannel() != intfRadio.getChannel()) {
              continue;
        }
/**/    System.out.printf("UDGMBS.intfRadio = %d\n", intfRadio.getMote().getID()) ;        

        double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
/**///    System.out.printf("dist = %.2f\n", dist);

        double maxTxDist = TRANSMITTING_RANGE *
          ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
/**///    System.out.printf("maxTxDist = %.2f\n", maxTxDist);
          
        double distFactor = dist/maxTxDist;
/**///    System.out.printf("distFactor = %.2f\n", distFactor);

        if (distFactor < 1) {
          double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
          if (intfRadio.getCurrentSignalStrength() < signalStrength) {
            intfRadio.setCurrentSignalStrength(signalStrength);
/**/       System.out.printf("1.UDGMBS.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
          }
        } else {
            intfRadio.setCurrentSignalStrength(SS_WEAK);
            if (intfRadio.getCurrentSignalStrength() < SS_WEAK) {
              intfRadio.setCurrentSignalStrength(SS_WEAK);
/**/          System.out.printf("2.UDGMBS.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
            }
        }
        
        if(!intfRadio.isInterfered()) {
/**/      System.out.printf("UDGMBS.intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
          if(!intfRadio.isBackscatterTag()) {
/**/        System.out.println("radio: " + intfRadio.getMote().getID() + " - interfereAnyReception");
            intfRadio.interfereAnyReception();
          } else {
/**/          System.out.println("radio: " + intfRadio.getMote().getID() + " - interfereAnyReception");
              intfRadio.interfereAnyReception();
/**/          System.out.println("radio: " + intfRadio.getMote().getID() + " - carrierListeningStart");              
              intfRadio.carrierListeningStart();
          }
        }
        
      }
    } /* Concerning connections created by a carrier generator */

 /**/System.out.println();

  } /* uptadeSignalStrengths */
  
  
//  public RadioConnection getLastConnection() {
//      return lastConnection;
//  }
  
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
