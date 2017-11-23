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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

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
@ClassDescription("Unit Disk Graph Medium (UDGM): Distance Loss")
public class UDGM extends AbstractRadioMedium {
  private static Logger logger = Logger.getLogger(UDGM.class);

  public double SUCCESS_RATIO_TX = 1.0; /* Success ratio of TX. If this fails, no radios receive the packet */
  public double SUCCESS_RATIO_RX = 1.0; /* Success ratio of RX. If this fails, the single affected receiver does not receive the packet */
  public double TRANSMITTING_RANGE = 50; /* Transmission range. */
  public double INTERFERENCE_RANGE = 100; /* Interference range. Ignored if below transmission range. */
  
  private DirectedGraphMedium dgrm; /* Used only for efficient destination lookup */

  private Random random = null;
  
  public UDGM(Simulation simulation) {
    super(simulation);
/**/System.out.println("UDGM");
    random = simulation.getRandomGenerator();
    dgrm = new DirectedGraphMedium() {
      protected void analyzeEdges() {
/**/    System.out.println("1.analyzeEdges");          
/**/    System.out.println("1.DirectedGraphMedium: " + dgrm);          
        /* Create edges according to distances.
         * XXX May be slow for mobile networks */
        clearEdges();
        for (Radio source: UDGM.this.getRegisteredRadios()) {
/**/      System.out.println("A.source: " + source.getMote().getID());            
          System.out.println("UDGM.DirectedGraphMedium");  
          Position sourcePos = source.getPosition();
          for (Radio dest: UDGM.this.getRegisteredRadios()) {
            Position destPos = dest.getPosition();
            /* Ignore ourselves */
            if (source == dest) {
              continue;
            }
/**/        System.out.println("A.dest: " + dest.getMote().getID());            
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
        Position pos = (Position) o;
/**/    System.out.println("Position of " +  pos.getMote().getID() + " changed!");
        dgrm.requestEdgeAnalysis();
      }
    };
    /* Re-analyze potential receivers if radios are added/removed. */
    simulation.getEventCentral().addMoteCountListener(new MoteCountListener() {
      public void moteWasAdded(Mote mote) {
/**/    System.out.println("moteWasAdded from UDGM");        
        mote.getInterfaces().getPosition().addObserver(positionObserver);
        dgrm.requestEdgeAnalysis();
      }
      public void moteWasRemoved(Mote mote) {
/**/    System.out.println("moteWasRemoved from UDGM");        
        mote.getInterfaces().getPosition().deleteObserver(positionObserver);
        dgrm.requestEdgeAnalysis();
      }
    });
    for (Mote mote: simulation.getMotes()) {
      mote.getInterfaces().getPosition().addObserver(positionObserver);
    }
/**/System.out.println("requestEdgeAnalysisONCE");    
    dgrm.requestEdgeAnalysis();

    /* Register visualizer skin */
    Visualizer.registerVisualizerSkin(UDGMVisualizerSkin.class);
  }

  public void removed() {
  	super.removed();
  	Visualizer.unregisterVisualizerSkin(UDGMVisualizerSkin.class);
  }
  
  public void setTxRange(double r) {
    TRANSMITTING_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }

  public void setInterferenceRange(double r) {
    INTERFERENCE_RANGE = r;
    dgrm.requestEdgeAnalysis();
  }

  public RadioConnection createConnections(Radio sender) {
    RadioConnection newConnection = new RadioConnection(sender);
/**/System.out.println("\nNewActiveConnID: " + newConnection.getID());
/**/System.out.println(sender.isGeneratingCarrier() ? "Sender=carrierGen: " + sender.getMote().getID() : 
                       sender.isBackscatterTag() ? "Sender=tag: " + sender.getMote().getID() : "Sender=sender: " + sender.getMote().getID());
    

    /* Fail radio transmission randomly - no radios will hear this transmission */
    if (getTxSuccessProbability(sender) < 1.0 && random.nextDouble() > getTxSuccessProbability(sender)) {
      return newConnection;
    }

    /* Calculate ranges: grows with radio output power */
    double moteTransmissionRange = TRANSMITTING_RANGE *
    ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());
    
/**///System.out.println("moteTransmissionRange: " + moteTransmissionRange);
    
    double moteInterferenceRange = INTERFERENCE_RANGE *
    ((double) sender.getCurrentOutputPowerIndicator() / (double) sender.getOutputPowerIndicatorMax());

/**///System.out.println("moteInterferenceRange: " + moteInterferenceRange);
    
    /* Get all potential destination radios */
    DestinationRadio[] potentialDestinations = dgrm.getPotentialDestinations(sender);
    if (potentialDestinations == null) {
      return newConnection;
    }

    /* Loop through all potential destinations */
    Position senderPos = sender.getPosition();
/**/System.out.println("PotentialDestinations: " + potentialDestinations.length);
    for (DestinationRadio dest: potentialDestinations) {
/**/  System.out.printf("PotDest = %d\n", dest.radio.getMote().getID());

      Radio recv = dest.radio;

/**/  System.out.println(recv.isGeneratingCarrier() ? "Recv=carrierGen: " + recv.getMote().getID() : 
                         recv.isBackscatterTag() ? "Recv=tag: " + recv.getMote().getID() : "Recv=sender or carrierGen: " + recv.getMote().getID());      
      
/**/  System.out.println("Sender: " +  sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/  System.out.println("Recv: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());

      /* Fail if radios are on different (but configured) channels */ 
      if (sender.getChannel() >= 0 &&
          recv.getChannel() >= 0 &&
          sender.getChannel() != recv.getChannel()) {
    	  
/**/ 	  System.out.println("Sender - recv: diff channels");
/**/ 	  System.out.println("Sender: " +  sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/ 	  System.out.println("Recv: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());

        /* Add the connection in a dormant state;
           it will be activated later when the radio will bes
           turned on and switched to the right channel. This behavior
           is consistent with the case when receiver is turned off. */
        newConnection.addInterfered(recv);

        continue;
      }
      Position recvPos = recv.getPosition();

      /* Fail if radio is turned off */
//      if (!recv.isReceiverOn()) {
//        /* Special case: allow connection if source is Contiki radio, 
//         * and destination is something else (byte radio).
//         * Allows cross-level communication with power-saving MACs. */
//        if (sender instanceof ContikiRadio &&
//            !(recv instanceof ContikiRadio)) {
//          /*logger.info("Special case: creating connection to turned off radio");*/
//        } else {
//          recv.interfereAnyReception();
//          continue;
//        }
//      }

      double distance = senderPos.getDistanceTo(recvPos);
/**/  System.out.println("senderRecvDistance: " + distance);
      
      if (distance <= moteTransmissionRange) {
/**/	 System.out.println("WithinTR");
        /* Within transmission range */
//		System.out.println("sender: " + sender.getMote().getID() + " isListeningCarrier= " + sender.isListeningCarrier());

        if (!recv.isRadioOn()) {
/**/      System.out.println("recv: " + recv.getMote().getID() + " - radio is off");
          newConnection.addInterfered(recv);
/**/  	  System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
          recv.interfereAnyReception();
        } else if (recv.isInterfered()) {
/**/      System.out.println("recv: " + recv.getMote().getID() + " - isInterfered");
          /* Was interfered: keep interfering */
          newConnection.addInterfered(recv);
/**/  	  System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
        } else if (recv.isTransmitting()) {
/**/      System.out.println("recv: " + recv.getMote().getID() + " - isTransmitting");
          newConnection.addInterfered(recv);
/**/  	  System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
        } else if (recv.isReceiving() ||
            (random.nextDouble() > getRxSuccessProbability(sender, recv))) {
/**/      System.out.println("recv: " + recv.getMote().getID() + " - isReceiving");
          /* Was receiving, or reception failed: start interfering */
          newConnection.addInterfered(recv);
/**/  	  System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
          recv.interfereAnyReception();

          /* Interfere receiver in all other active radio connections */
          for (RadioConnection conn : getActiveConnections()) {
            if (conn.isDestination(recv)) {
/**/  	      System.out.println("recv: " + recv.getMote().getID() + " added as interfered to conn: " + conn.getID());
              conn.addInterfered(recv);
            }
          }

        } else {
          /* Success: radio starts receiving */
          newConnection.addDestination(recv);
/**/      System.out.println("recv: " + recv.getMote().getID() + " added as new destination to newConnection " + newConnection.getID());
        }
      } else if (distance <= moteInterferenceRange) {
/**/  	System.out.println("WithinIR");
        /* Within interference range */
        newConnection.addInterfered(recv);
/**/  	System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
        recv.interfereAnyReception();
      }
    }

    return newConnection;
  }
  
  public double getSuccessProbability(Radio source, Radio dest) {
/**/System.out.println("UDGM.getSuccessProbability");
  	return getTxSuccessProbability(source) * getRxSuccessProbability(source, dest);
  }
  
  public double getTxSuccessProbability(Radio source) {
/**/System.out.println("UDGM.getTxSuccessProbability");
    return SUCCESS_RATIO_TX;
  }
  
  public double getRxSuccessProbability(Radio source, Radio dest) {
/**/System.out.println("UDGM.getRxSuccessProbability");
    
  	double distance = source.getPosition().getDistanceTo(dest.getPosition());
/**/System.out.println("UDGM.distance: " + distance);
    double distanceSquared = Math.pow(distance,2.0);
/**/System.out.println("UDGM.distanceSquared: " + distanceSquared);

/**/System.out.println();
/**/System.out.println("UDGM.Power Indicator");
/**/System.out.println("UDGM.source.getCurrentOutputPowerIndicator(): " + (double)source.getCurrentOutputPowerIndicator());
/**/System.out.println("UDGM.source.getCurrentOutputPowerIndicatorMax(): " + (double) source.getOutputPowerIndicatorMax());

/**/System.out.println("UDGM.TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);

    double distanceMax = TRANSMITTING_RANGE * 
    ((double) source.getCurrentOutputPowerIndicator() / (double) source.getOutputPowerIndicatorMax());
    if (distanceMax == 0.0) {
      return 0.0;
    }

/**/System.out.println("UDGM.distanceMax: " + distanceMax);

    double distanceMaxSquared = Math.pow(distanceMax,2.0);

/**/System.out.println("UDGM.distanceMaxSquared: " + distanceMaxSquared);

    double ratio = distanceSquared / distanceMaxSquared;
    /**/System.out.println("UDGM.ratio: " + ratio);
    
    if (ratio > 1.0) {
    	return 0.0;
    }
    
/**/System.out.println("UDGM.SUCCESS_RATIO_RX: " + SUCCESS_RATIO_RX);

    
    return 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
  }

  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUDGM.Update signal strengths");
    
    /* Reset signal strengths */
/**/System.out.printf("Reset signal strength \n");
    for (Radio radio : getRegisteredRadios()) {
      radio.setCurrentSignalStrength(getBaseRssi(radio));      
/**/  System.out.printf("Reset Radio: %d, Signal strength: %.2f\n", radio.getMote().getID(), radio.getCurrentSignalStrength());
    }

    /* Set signal strength to below strong on destinations */
    RadioConnection[] conns = getActiveConnections();
    for (RadioConnection conn : conns) {
      if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
        conn.getSource().setCurrentSignalStrength(SS_STRONG);
/**/    System.out.printf("source = %d , signal = %.2f\n", conn.getSource().getMote().getID(), conn.getSource().getCurrentSignalStrength());
      }
      for (Radio dstRadio : conn.getDestinations()) {
/**/    System.out.println("\nSet signal strength to below strong on destinations");
/**/    System.out.println("ActiveConnID: " + conn.getID());          
        if (conn.getSource().getChannel() >= 0 &&
            dstRadio.getChannel() >= 0 &&
            conn.getSource().getChannel() != dstRadio.getChannel()) {
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
    }

    /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
      for (Radio intfRadio : conn.getInterfered()) {
/**/    System.out.println("\nSet signal strength to below weak on interfered");
/**/    System.out.println("ActiveConnID: " + conn.getID());          
        if (conn.getSource().getChannel() >= 0 &&
            intfRadio.getChannel() >= 0 &&
            conn.getSource().getChannel() != intfRadio.getChannel()) {
          continue;
        }

/**/    System.out.printf("intfRadio = %d\n", intfRadio.getMote().getID()) ;        
        
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
    }
  }

  public Collection<Element> getConfigXML() {
    Collection<Element> config = super.getConfigXML();
    Element element;

    /* Transmitting range */
    element = new Element("transmitting_range");
    element.setText(Double.toString(TRANSMITTING_RANGE));
    config.add(element);

    /* Interference range */
    element = new Element("interference_range");
    element.setText(Double.toString(INTERFERENCE_RANGE));
    config.add(element);

    /* Transmission success probability */
    element = new Element("success_ratio_tx");
    element.setText("" + SUCCESS_RATIO_TX);
    config.add(element);

    /* Reception success probability */
    element = new Element("success_ratio_rx");
    element.setText("" + SUCCESS_RATIO_RX);
    config.add(element);

    return config;
  }

  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    super.setConfigXML(configXML, visAvailable);
    for (Element element : configXML) {
      if (element.getName().equals("transmitting_range")) {
        TRANSMITTING_RANGE = Double.parseDouble(element.getText());
      }

      if (element.getName().equals("interference_range")) {
        INTERFERENCE_RANGE = Double.parseDouble(element.getText());
      }

      /* Backwards compatibility */
      if (element.getName().equals("success_ratio")) {
        SUCCESS_RATIO_TX = Double.parseDouble(element.getText());
        logger.warn("Loading old Cooja Config, XML element \"sucess_ratio\" parsed at \"sucess_ratio_tx\"");
      }

      if (element.getName().equals("success_ratio_tx")) {
        SUCCESS_RATIO_TX = Double.parseDouble(element.getText());
      }

      if (element.getName().equals("success_ratio_rx")) {
        SUCCESS_RATIO_RX = Double.parseDouble(element.getText());
      }
    }
    return true;
  }
  
  public DirectedGraphMedium getDirectedGraphMedium() {
    return dgrm;
  }
  
  public Random getRandom() {
    return random;
  }
  

}
