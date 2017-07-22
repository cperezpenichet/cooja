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
  
  
  //It might be deleted because in a connection created by a carrier generator radio will never be a destination
  private void removeFromActiveConnections(Radio radio) {
/**/  System.out.println("UDGMBS.removeFromActiveConnections");      
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
                if (intfRadio.isInterfered()) {
/**/              System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - isListeningCarrier(): " + intfRadio.isListeningCarrier());                    
                  if (!intfRadio.isListeningCarrier()) {    
/**/                System.out.println("intfRadio: " + intfRadio.getMote().getID() + "  - carrierListeningStart");                          
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
/**/          System.out.println("\nradio= " + radio.getMote().getID() + " - CARRIER_STOPPED");
                  
              RadioConnection connection = getActiveConnectionFrom(radio);
                  
              if(connection == null) {
                logger.fatal("No radio connection found");
                return;
              }
                  
//              /* The radio is about to stop generating its carrier(or turn off the radio). If it is still
//               * interfered by any other connection stop it and signal every one of them to remove it from
//               * their interfered radios as well.
//               */ 
//              if(radio.isInterfered()) {
///**/            System.out.println("radio: " + radio.getMote().getID() + " was interfered by another connection and now it is signaled");                      
//                radio.signalReceptionEnd();
//                    
//                for(RadioConnection conn: activeConnectionsFromCarrier) {
//                  if(conn.isInterfered(radio)) {
///**/                System.out.println("\nradio: " + radio.getMote().getID() + " is also Interfered in carrierConn: " + conn.getID());
///**/                System.out.println("carrierConn: " + conn.getID() + " - getInterferedNonDestinations: " + conn.getInterferedNonDestinations().length);
//                    conn.removeInterfered(radio);
///**/                System.out.println("carrierConn: " + conn.getID() + " - getInterferedNonDestinations: " + conn.getInterferedNonDestinations().length);
//                  }
//                }
//              }


/**/          System.out.println("\nActiveConnectionsFromCarrier: " + getActiveConnectionsFromCarrier().length);
/**/          System.out.println("carrier stops from conn: " + connection.getID()); 
              activeConnectionsFromCarrier.remove(connection);
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
//                      
//                  for (Radio intfRadio: connection.getInterferedNonDestinations()) {
///**/                System.out.println("conn: " + connection.getID() + " - carier.gen: " + radio.getMote().getID() + " - intfRadio: " + intfRadio.getMote().getID());                        
//                    if (intfRadio.isInterfered()) {
//                      //if (intfRadio.isBackscatterTag()) {
///**/                      System.out.println("intfRadio: " + intfRadio.getMote().getID() + " of last conn: " + connection.getID() + " isInterfered");
///**/                      System.out.println("ActiveConnectionsFromCarrier left: " + getActiveConnectionsFromCarrier().length);
//                          
                            //Check it with Carlos - This procedure makes more sense to me
                            //This procedure is done again in the updateSignalStregth() method. What he is usually doing is to stop any interferences 
                            //from the conn that is about to finish and in the updateSignalStrengt method signal them again for the radios that are
                            //still interfered by any of the remaining connections
              
//                            for(RadioConnection conn: getActiveConnectionsFromCarrier()) {
//                            if(conn.isInterfered(intfRadio)) {
///**/                          System.out.println("intfRadio: " + intfRadio.getMote().getID() + " is still Interfered by active conn: " + conn.getID());
//                              isStillInterfered = true;
//                              break;
//                            }
///**/                        System.out.println("isStillInterfered: " + isStillInterfered);
//                          }
//                          
//                          if(!isStillInterfered) {
//                            if (intfRadio.isBackscatterTag()) {  
///**/                          System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - carrierListeningEnd");
//                              intfRadio.carrierListeningEnd();
//                            } else {
//                                System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - signalReceptionEnd");               
//                                intfRadio.signalReceptionEnd();  
//                            }
//                          }
//                      } 
//////                          else {
///////**/                      System.out.println("intfRadio: " + intfRadio.getMote().getID() + " - signalReceptionEnd");               
//////                          intfRadio.signalReceptionEnd();
//                  }  
                  //  } 
                 // }
                    
              /* Update signal strength */
              updateSignalStrengths();
/**/          System.out.println("connection: " + connection.getID() + " is about to finish");
                  
              /* Notify observers */
              radioTransmissionObservable.setChangedAndNotify();
  /**/        System.out.println("connection: " + connection.getID() + " finished");
            }
            break;
              
            default:
              logger.fatal("Unsupported radio event: " + event);
        }            
    }
  };
  
  public HashSet<Integer> getTagTXChannels(Radio sender) {
    /* Store the channels to which the tag can transmit */
    HashSet<Integer> txChannels = new HashSet<Integer>();
      
    /* 
     * Every tag that is listening to a carrier is interfered to the  
     * connection whose source generated that carrier.
     *  
     * Hence, check every active connection... 
     */
             
    for (RadioConnection conn : getActiveConnectionsFromCarrier()) {
/**/  System.out.println("A.conn: " + conn.getID() + " with sender: " + conn.getSource().getMote().getID());                              
              
      /* ... and also check to which connection the tag belongs. Then take the  
         channel of the source (carrier generator) of that connection, move 
         it two channels apart (+2) and store that channel to a set of channels. */  
      if (conn.isInterfered(sender)) {
/**/    System.out.println("tag: " + sender.getMote().getID() + " belongs to conn: " + conn.getID());                
        if (conn.getSource().isGeneratingCarrier()) { // TODO It might be interesting to reflect active transmissions as interference too
          if (conn.getSource().getChannel() >=0) {
/**/        System.out.println("carier.g: " +  conn.getSource().getMote().getID() + " of conn: " + conn.getID() +  " - Ch= " + conn.getSource().getChannel());                    
            txChannels.add(conn.getSource().getChannel() + 2);
/**/        System.out.println("Ch= " +  (conn.getSource().getChannel() + 2) + " is stored in txChannels");                  
          }    
        }
      } 
    } 
       
    return txChannels;
  }
   
      
  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection = super.createConnections(sender);
      
/**/System.out.println("\nUDGMBS.NewConnID: " + newConnection.getID());

    HashSet<Integer> tagTXChannels = new HashSet<Integer>();
    int debugChannel = 0;

    
/**/System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length);    
    if (sender.isGeneratingCarrier()) {
/**/  System.out.println("sender:" + sender.getMote().getID() + " is a carrier generator");      
       
        
      /* 
       * Whatever exists within the interference range of the carrier generator it gets
       * interfered. For radios within carrier's transmission range if they are added
       * as destinations we are removing them from Destinations and adding them in the 
       * onyInterfered radios and marking them as interfered following the procedure below.
       * 
       * In case of a tag we do not have to check for the channel because carrier generators 
       * acts irrespective of that. Hence getChannel returns -1 and the usual channel check is 
       * skipped. 
       */  
        
      for (Radio r: newConnection.getAllDestinations()) {
        newConnection.removeDestination(r);
/**/    System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
        newConnection.addInterfered(r);
/**/    System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/    System.out.println("r: " + r.getMote().getID() + " interfereAnyReception");
        r.interfereAnyReception();
      }
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
    } else if (sender.isBackscatterTag()) {
/**/    System.out.println("sender:" + sender.getMote().getID() + " is a tag");      
        if (sender.isListeningCarrier()) {
/**/      System.out.println("sender:" + sender.getMote().getID() + " is listening to the carrier");      
          
          tagTXChannels = getTagTXChannels(sender);
/**/      System.out.println("tagConn: " + newConnection.getID() + " contains: " + tagTXChannels.size() + " channels");          
/**/      System.out.println("which are: " + tagTXChannels.iterator());          
          
          
/**/      System.out.println("tagConn: " + newConnection.getID() + " - AllDestinationsBefore: " + newConnection.getAllDestinations().length);           
          for(Radio r: newConnection.getAllDestinations()) {
            if (!r.isBackscatterTag()) { // Maybe it has to be removed in case a potential receiver is a tag
/**/          System.out.println("radio: " + r.getMote().getID() + " - Ch= "+ r.getChannel());               
              if(!tagTXChannels.contains(r.getChannel())) {
                newConnection.removeDestination(r);
/**/            System.out.println("radio: " + r.getMote().getID() + " was removed by destinations");
              } else {
                  //if recv has the right channel but was added to destinations
                  //although it is gen carrier remove it from destinations and add it to interfered
                  if(r.isGeneratingCarrier()) {
                    newConnection.removeDestination(r);
                    newConnection.addInterfered(r);
/**/                System.out.println("radio: " + r.getMote().getID() + " was removed by destinations");
                  }
              }
            }
          }
/**/      System.out.println("tagConn: " + newConnection.getID() + " - AllDestinationsAfter: " + newConnection.getAllDestinations().length);            
       
/**/      System.out.println("\ntagConn: " + newConnection.getID() + " - InterferedNonDestinationsBefore : " + newConnection.getInterferedNonDestinations().length);
          for(Radio r: newConnection.getInterferedNonDestinations()) {
            if (!r.isBackscatterTag()) { // Maybe it has to be removed in case a potential receiver is a tag
/**/          System.out.println("radio: " + r.getMote().getID() + " - Ch= "+ r.getChannel());                  
              if(!tagTXChannels.contains(r.getChannel())) {
/**/            System.out.println("radio: " + r.getMote().getID() + " signalReceptionEnd");
                r.signalReceptionEnd();  // I do not like 
                newConnection.removeInterfered(r);
/**/            System.out.println("radio: " + r.getMote().getID() + " was removed by Interfered");                
              }
            }
          }
/**/      System.out.println("tagConn: " + newConnection.getID() + " - InterferedNonDestinationsAfter : " + newConnection.getInterferedNonDestinations().length);          
        }
    }

/**/System.out.println("return newConnection");        
    return newConnection;    
  }
  

  public void updateSignalStrengths() {
    super.updateSignalStrengths();
    
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUpdate signal strengths for radios belonging to conections generated by a carrier");    
    RadioConnection[] conns = getActiveConnectionsFromCarrier();
    
    /* Set signal strength to below strong on destinations */  
    for (RadioConnection conn : conns) {
      if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
        conn.getSource().setCurrentSignalStrength(SS_STRONG);
/**/    System.out.printf("UDGMBS.source = %d , signal = %.2f\n", conn.getSource().getMote().getID(), conn.getSource().getCurrentSignalStrength());
      }
      // Because this method concerns connections created by a 
      // carrier generator we should only search in getInterfered  
      for (Radio dstRadio : conn.getDestinations()) {
/**/    System.out.println("UDGMBS.Set signal strength to below strong on destinations");
/**/    System.out.println("UDGMBS.ActiveConnID: " + conn.getID());          
        if (conn.getSource().getChannel() >= 0 &&
            dstRadio.getChannel() >= 0 &&
            conn.getSource().getChannel() != dstRadio.getChannel()) {
              continue;
        }
/**/    System.out.printf("UDGMBS.dstRadio = %d\n", dstRadio.getMote().getID());        

        double dist = conn.getSource().getPosition().getDistanceTo(dstRadio.getPosition());
/**///    System.out.printf("dist = %.2f\n", dist);

        double maxTxDist = TRANSMITTING_RANGE *
          ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
/**///    System.out.printf("maxTxDist = %.2f\n", maxTxDist);
          
        double distFactor = dist/maxTxDist;
/**///    System.out.printf("distFactor = %.2f\n", distFactor);

        double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
        if (dstRadio.getCurrentSignalStrength() < signalStrength) {
          dstRadio.setCurrentSignalStrength(signalStrength);
/**/      System.out.printf("UDGMBS.dstRadio = %d , signal = %.2f\n", dstRadio.getMote().getID(), dstRadio.getCurrentSignalStrength());
        }
      }
    }

      /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
      for (Radio intfRadio : conn.getInterfered()) {
/**/  System.out.println("UDGMBS.Set signal strength to below weak on interfered");
/**/  System.out.println("UDGMBS.ActiveConnID: " + conn.getID());          
        if (conn.getSource().getChannel() >= 0 &&
            intfRadio.getChannel() >= 0 &&
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
        
//        if (!intfRadio.isInterfered()) {
//          /*logger.warn("Radio was not interfered: " + intfRadio);*/
///**/      System.out.printf("UDGMBS.intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
//          intfRadio.interfereAnyReception();
//        }
        
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
    }
 /**/System.out.println();

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
