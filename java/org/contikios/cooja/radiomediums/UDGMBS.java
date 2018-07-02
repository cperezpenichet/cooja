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
 * you can address here: .
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
  
  //public double SUCCESS_RATIO_TX = 1.0; /* Success ratio of TX. If this fails, no radios receive the packet */
  //public double SUCCESS_RATIO_RX = 1.0; /* Success ratio of RX. If this fails, the single affected receiver does not receive the packet */
  
  /* Gain of the transmitting antenna */
  public  double GT = 0;
  /* Gain of the receiving antenna */
  public  double GR = 0;
  /* Wavelength */
  public  double WAVELENGTH = 0.122;
  /* Backscatter Coefficient */
  /* Derived from the experimental part of the master thesis of George Daglaridis at UNO Group, at Uppsala University */
  public double BACKSCATTER_COEFFICIENT =  13.4;
  /* Energy loss */
  public  double ENERGYLOSS = 1.4;  
  /* Sensitivity threshold for Tmote sky in dBm */
  /* Derived from the experimental part of the master thesis of George Daglaridis at UNO Group, at Uppsala University */
  public final double STH = -86.4;

//  ArrayList<Double> carrierToTagDist = new ArrayList<Double>();
//  ArrayList<Double> tagToCarrierDist = new ArrayList<Double>();
//  ArrayList<Double> tagToRecvDist = new ArrayList<Double>();
//  ArrayList<Double> receivedPowerLst = new ArrayList<Double>();
//  ArrayList<Integer> prr = new ArrayList<Integer>();

  
  public UDGMBS(Simulation simulation) {
    super(simulation);
/**/System.out.println("UDGMBS");
  
    final Observer positionObserver = new Observer() {
      public void update(Observable o, Object arg) {
/**/    System.out.println("UDGMBS_Position_Change");          
        Mote mote = (Mote) arg;
        Radio radio = mote.getInterfaces().getRadio();
          
       /* Re-calculate the TX range of the tag when the position
          of the tag or the active node (active transmitter or 
          carrier generator) changes. */ 
       if (radio.isBackscatterTag()) {
         for (RadioConnection conn: getActiveConnections()) {
//                if (conn.isDestination(radio)) {
    /**/            System.out.println("2.Start keeping a record of the tagTXPower");
    /**/            System.out.println("2.backTag: " + radio.getMote().getID() + " was moved");
    /**/                  System.out.println("2a.conn.getSource(): " + conn.getSource().getMote().getID());
                /* Calculating the tag's output power does not involve 
                 * the tag as a source of a connection which in fact would 
                 * add an unwanted entry in the tag's hashtable. */
                if (!conn.getSource().isBackscatterTag()) {
/**/                  System.out.println("2b.conn.getSource(): " + conn.getSource().getMote().getID());
                  calculateTagCurrentTxPower(conn.getSource(), radio, conn);
                }
    
    /**/            System.out.println("2.Stop keeping a record of the tagTXPower");
    /**/            System.out.println();
//                }
            }
          } else {
            for (RadioConnection conn: getActiveConnections()) {
              if (conn.getSource() == radio) {
                if (conn.getSource().isGeneratingCarrier()) {
                  for (Radio destRadio: conn.getAllDestinations()) {
                    if (destRadio.isBackscatterTag()) {
/**/                    System.out.println("3.Start keeping a record of the tagTXPower");
/**/                    System.out.println("3.carrierRadio: " + radio.getMote().getID() + " was moved");
/**/                    System.out.println("3.backTag: " + destRadio.getMote().getID());
                    
                      calculateTagCurrentTxPower(radio, destRadio, conn);
                    
/**/                    System.out.println("3.Stop keeping a record of the tagTXPower");
/**/                    System.out.println();
                    }
                  }
                } else {
                  for (Radio intfRadio: conn.getInterfered()) {
                    if (intfRadio.isBackscatterTag()) {
/**/                  System.out.println("4.Start keeping a record of the tagTXPower");
/**/                  System.out.println("4.activeRadio: " + radio.getMote().getID() + " was moved");
/**/                  System.out.println("4.backTag: " + intfRadio.getMote().getID());
                      
                      calculateTagCurrentTxPower(radio, intfRadio, conn);
                      
/**/                  System.out.println("4.Stop keeping a record of the tagTXPower");
/**/                  System.out.println();
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
/**/    System.out.println("moteWasAdded from UDGMBS");        
        mote.getInterfaces().getPosition().addObserver(positionObserver);
      }
      public void moteWasRemoved(Mote mote) {
/**/    System.out.println("moteWasRemoved from UDGMBS");        
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
/**/System.out.println("distance: " + distance);

    double transmitttedPower = 0.0;
    
    /* Transform power level into output power in dBm */
    for (int i = 0; i < source.CC2420OutputPower.length; i++) {
      if((double) source.getCurrentOutputPowerIndicator() == i) {
        transmitttedPower = source.CC2420OutputPower[i];
/**/    System.out.println("transmitttedPower: " + transmitttedPower);
      }
    }
/**/System.out.println("pathLoss: " + pathLoss(distance));
    double incidentPower = transmitttedPower + GT + GR + pathLoss(distance);
    return incidentPower;
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
/**/System.out.println("incidentPower: " + incidentPower);
    /* Reflection loss */
    double reflectionLoss = BACKSCATTER_COEFFICIENT + ENERGYLOSS;
/**/System.out.println("2.reflectionLoss: " + reflectionLoss);
    /* Current power of the tag in dBm */
    double tagCurrentTXPower = incidentPower - reflectionLoss;
/**/System.out.println("tagCurrentTXPower: " + tagCurrentTXPower);
/**/System.out.println(conn);
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
  public HashSet<Integer> getTXChannels(Radio sender) {
    /* Store the channels for an active or backscatter transmission */
    HashSet<Integer> txChannels = new HashSet<Integer>();
      
    /* 
     * Check every active connection generated by an active radio.
     */
    if (sender.isBackscatterTag()) {         
      for (RadioConnection conn : getActiveConnections()) {
/**/    System.out.println("A.conn: " + conn.getID() + " with sender: " + conn.getSource().getMote().getID());                              
        /* ...Then take the channel of the source of that connection, move 
           it two channels apart (+2) and store the new channel to a set. Moving 
           the source's channel by 2 corresponds to a frequency offset of 10 MHz.  */  
/**/      System.out.println("tag: " + sender.getMote().getID() + " belongs to conn: " + conn.getID());
          if (conn.getSource().getChannel() > 0) {
/**/        System.out.println("carier.g: " +  conn.getSource().getMote().getID() + " of conn: " + conn.getID() +  " - Ch= " + conn.getSource().getChannel());                    
            txChannels.add(conn.getSource().getChannel() + 2);
/**/        System.out.println("Ch= " +  (conn.getSource().getChannel() + 2) + " is stored in txChannels");                  
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
/**/  System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length);
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
      if(sender.isGeneratingCarrier()) { 
/**/    System.out.println("sender: " + sender.getMote().getID() + " is a carrier generator");      
        
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
/**/        System.out.println("TAG IS IN DESTINATIONS");            
/**/        System.out.println();
/**/        System.out.println("1.Start keeping a record of the tagTXPower");
/**/        System.out.println("backTag: " + r.getMote().getID());
            double dist = sender.getPosition().getDistanceTo(r.getPosition());
//            carrierToTagDist.add(dist);
/**/        System.out.println("dist: " + dist);

            calculateTagCurrentTxPower(sender, r, newConnection);
/**/        System.out.println("1.Stop keeping a record of the tagTXPower");
/**/        System.out.println();
          } 
        }
        for (Radio r: newConnection.getInterfered()) {
          if (r.isBackscatterTag()) {
/**/        System.out.println("TAG WAS INTERFERED");            
            // NOTE: the node is only removed only from the onlyInterfered ArrayList
            // and not from the allInterfered ArrayList.
            newConnection.addDestination(r);
/**/        System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
/**/        System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length); 
/**/        System.out.println();
/**/        System.out.println("Start keeping a record of the tagTXPower");
/**/        System.out.println("backTag: " + r.getMote().getID());
            double dist = sender.getPosition().getDistanceTo(r.getPosition());
            //carrierToTagDist.add(dist);
/**/        System.out.println("dist: " + dist);
            calculateTagCurrentTxPower(sender, r, newConnection);
/**/        System.out.println("Stop keeping a record of the tagTXPower");
/**/        System.out.println();
          }
        }
      } else {
        /* In case the sender is an active transmitter and the tag is within its TX
         * range the tag is removed from the destinations and added to the interfered. */
/**/    System.out.println("sender:" + sender.getMote().getID() + " is an active sender");      
        for (Radio r: newConnection.getAllDestinations()) {
          if (r.isBackscatterTag()) {
            newConnection.removeDestination(r);
/**/        System.out.println("UDGMBS.AllDestinations: " + newConnection.getAllDestinations().length);
            /* tag (r) is added to Interfered but without setting any flag since the tag 
               has no receiving capabilities and it won't be shown as interfered in the timeline. */
            newConnection.addInterfered(r);
/**/        System.out.println("r:" + r.getMote().getID() + " is removed from destinations");      
/**/        System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
          }    
        }
        for (Radio r: newConnection.getInterferedNonDestinations()) {
          if (r.isBackscatterTag()) {
/**/        System.out.println("r:" + r.getMote().getID() + " is interfered");      
/**/        System.out.println("Start keeping a record of the tagTXPower");
            double dist = sender.getPosition().getDistanceTo(r.getPosition());
/**/        System.out.println("backTag: " + r.getMote().getID());
/**/        System.out.println("dist: " + dist);
            calculateTagCurrentTxPower(sender, r, newConnection);
/**/        System.out.println("Stop keeping a record of the tagTXPower");
/**/        System.out.println();
          }    
        }
      }
/**/  System.out.println("UDGMBS.getInterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);
    } else {
      /* The tag starts a new connection. */   
      newConnection = new RadioConnection(sender);
      
      /* Fail radio transmission randomly - no radios will hear this transmission */
      if (getTxSuccessProbability(sender) < 1.0 && getRandom().nextDouble() > getTxSuccessProbability(sender)) {
        return newConnection;
      }
      
/**/  System.out.println("\nNewBackscatteringConnID: " + newConnection.getID());

/**/  System.out.println("Sender is a tag - isListenningCarrier: " + sender.isListeningCarrier());

      /* Collect the channels for backscattering communication */
      tagTXChannels = getTXChannels(sender);
          
/**/  System.out.println("tagConn: " + newConnection.getID() + " contains: " + tagTXChannels.size() + " channels which are");          

      Iterator<Integer> itr = tagTXChannels.iterator();
      while(itr.hasNext()) {
/**/    System.out.println("channel: " + itr.next());
      }
          
      /* Get all potential destination radios */
      DestinationRadio[] potentialDestinations = getDirectedGraphMedium().getPotentialDestinations(sender);
      if (potentialDestinations == null) {
        return newConnection;
      }
          
      /* Loop through all potential destinations */
      Position senderPos = sender.getPosition();
/**/  System.out.println("PotentialDestinations: " + potentialDestinations.length);
      
      for (DestinationRadio dest: potentialDestinations) {
/**/    System.out.printf("PotDest = %d\n", dest.radio.getMote().getID());

        Radio recv = dest.radio;
        
//        /*                               */
//        if (recv.isGeneratingCarrier()) {
//          double DistanceBetweenTagAndCarrier = senderPos.getDistanceTo(recv.getPosition());
//          tagToCarrierDist.add(DistanceBetweenTagAndCarrier);
///**/      System.out.println("Potenntialrecv: " + recv.getMote().getID());          
///**/      System.out.println("tagToCarrierDist: " + tagToCarrierDist);
//        }
        
/**/    System.out.println("channel: " + recv.getChannel() + " is in txChannels: " + tagTXChannels.contains(recv.getChannel()));

        if(recv.getChannel() >= 0 && !tagTXChannels.contains(recv.getChannel())) {
/**/      System.out.println("------Checking the validity of the channels------");
/**/      System.out.println("sender - recv: diff channels");
/**/      System.out.println(sender.isListeningCarrier() ? "sender(tag): " + sender.getMote().getID() : "sender: " + sender.getMote().getID() + " - Ch= " + sender.getChannel());
/**/      System.out.println("recv: " +  recv.getMote().getID() + " - Ch= " + recv.getChannel());

          /* Add the connection in a dormant state;
             it will be activated later when the radio will bes
             turned on and switched to the right channel. This behavior
             is consistent with the case when receiver is turned off. */
          newConnection.addInterfered(recv);
/**/      System.out.println("-------------------------------------------------");
/**/      System.out.println();
        } else {
/**/      System.out.println("recvID: " + recv.getMote().getID());
/**/      System.out.println("recvChannel: " + recv.getChannel());
          double tagCurrentOutputPowerIndicator = sender.getTagCurrentOutputPowerMax(recv.getChannel());
/**/      System.out.println("tagCurrentOutputPowerIndicator: " + tagCurrentOutputPowerIndicator);
          
          /* Tag's transmission range */
          double tagTransmissionRange = calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);
          
/**/      System.out.println("tagTransmissionRange: " + tagTransmissionRange);
          
          /* Tag's interference range */
          double tagInterferenceRange = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);
          
/**/      System.out.println("tagInterferenceRange: " + tagInterferenceRange);

          Position recvPos = recv.getPosition();
          double distance = senderPos.getDistanceTo(recvPos);
          //tagToRecvDist.add(distance);
/**///      System.out.println("TagRecvDistance: " + distance);
          
/**/      System.out.println("RecvDistance: " + distance);
        
          if (sender.getNumberOfConnectionsFromChannel(recv.getChannel()) != 0) {

          
          /* 
           * When a tag starts a new connection and at least one of the ongoing connections has 
           * an active transmitter as a source treat the receiver as an interfered radio.
           */
          if (sender.isTXChannelFromActiveTransmitter(recv.getChannel()) || 
                            sender.getNumberOfConnectionsFromChannel(recv.getChannel()) >= 2) {
/**/        System.out.println("tag " + sender.getMote().getID() + " reacts beacause of an active transmitter");
            if (distance <= tagInterferenceRange) {
/**/          System.out.println("WithinIR");
              /* Within interference range */
              newConnection.addInterfered(recv);
/**/          System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              recv.interfereAnyReception();
            } 
          } else {
            /* 
             * When a tag starts a new connection and the ongoing connections have as a source only a 
             * carrier generator. */
/**/        System.out.println("tag " + sender.getMote().getID() + " reacts beacause of carrier generator");
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
              } else if (recv.isReceiving() || (getRandom().nextDouble() > getRxSuccessProbability(sender, recv))) {
/**/            System.out.println("recv: " + recv.getMote().getID() + " - isReceiving");
/**/            System.out.println("recv.isReceiving(): " + recv.isReceiving());
/**/            System.out.println("random.nextDouble(): " + getRandom().nextDouble());
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
/**/              System.out.println("recv: " + recv.getMote().getID() + " added as interferred to newConnection " + newConnection.getID());
                }
              }
              /* Everything beyond this range is not considered as a valid destination */
            } else if (distance <= tagInterferenceRange) {
/**/          System.out.println("WithinIR");
              /* Within interference range */
              newConnection.addInterfered(recv);
/**/          System.out.println("recv: " + recv.getMote().getID() + " added as interfered to newConnection: " + newConnection.getID());
              recv.interfereAnyReception();
            }
          }
         }
        }  
      } 
    }
/**/System.out.println("Connection: " + newConnection + " is about to return");        
    return newConnection;
    
  } /* createConnections */
  
  @Override
  public double getTxSuccessProbability(Radio source) {
    /**/System.out.println("UDGMBS.getTxSuccessProbability");
//    /**/System.out.println("UDGMBS.SUCCESS_RATIO_TX: " + SUCCESS_RATIO_TX);
    double txSuccessProbability = 0.0;
    if(!source.isBackscatterTag()) {
      txSuccessProbability = super.getTxSuccessProbability(source);
    } else {
      txSuccessProbability = SUCCESS_RATIO_TX;
    }
/**/System.out.println("UDGMBS.SUCCESS_RATIO_TX: " + txSuccessProbability);

    return txSuccessProbability;
    
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
      
      double tagCurrentOutputPowerIndicator = 0.0;
//      if (dest.isGeneratingCarrier()) {
        /* Usually the appropriate transmitting channel for a tag is already saved when the rx success probability is 
         * calculated for an active transmitter acting as the destination radio. This if statement might look a little bit anorthodox,
         * but it is needed in cases where the destination radio is a carrier generator, which in reality can't be, since this method
         * is also used for the appearance of the probability percentage of these dest radio which are within the source's TX range. */
//        tagCurrentOutputPowerIndicator = source.getTagCurrentOutputPowerMax(dest.getChannel()+2);
//      } else {
        /* Regular active radio acting as a destination radio */
        tagCurrentOutputPowerIndicator = source.getTagCurrentOutputPowerMax(dest.getChannel());
//      }
      
/**/  System.out.println();
/**/  System.out.println("UDGMBS.Power Indicator");
      double distanceMax = calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);
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
/**/  System.out.println("UDGMBS.SUCCESS_RATIO_RX: " + SUCCESS_RATIO_RX);

      rxSuccessProbability = 1.0 - ratio*(1.0-SUCCESS_RATIO_RX);
/**/  System.out.println("UDGMBS.rxSuccessProbability: " + rxSuccessProbability);
    }
    return rxSuccessProbability;
        
  }
 
  @Override
  public void updateSignalStrengths() {
    /* Override: uses distance as signal strength factor */
/**/System.out.println("\nUpdate signal strengths");

    /* Reset signal strengths */
/**/System.out.printf("Reset signal strength \n");
    for (Radio radio : getRegisteredRadios()) {
      /* 
       * Update the Hashtable of the tag in case it is a 
       * destination or an interfered radio to the connection 
       * that just finished. 
       */
      if(radio.isBackscatterTag()) {
/**/    System.out.println("IN" );
        /* Would have been enabled if the tag had receiving capabilities */
        //radio.setCurrentSignalStrength(-100);
        RadioConnection lastConn = getLastConnection();
        if (lastConn != null) {
          if( lastConn.isDestination(radio) || lastConn.isInterfered(radio) ) {
/**/        System.out.println("1.lastConnID: " + lastConn.getID());
            radio.updateTagTXPower(lastConn);
          }
        }
      } else {
        // In the future, do the same for the tag in case it 
        // acquires receiving capabilities.
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
/**/    System.out.println("1.dstRadio: " + dstRadio.getMote().getID() + " channel: " + dstRadio.getChannel());
/**/    System.out.println("channel: " + dstRadio.getChannel() + " is in txChannels: " + txChannels.contains(dstRadio.getChannel()));
        if(dstRadio.getChannel() >= 0 && !txChannels.contains(dstRadio.getChannel())) {
            continue;
        }
        
        /* In case the destination radio is a tag, don't calculate its signal strength
         * since it does not have receiving capabilities. */
        if (!dstRadio.isBackscatterTag()) { //|| !dstRadio.isGeneratingCarrier()) {
        
/**/      System.out.printf("2.dstRadio = %d\n", dstRadio.getMote().getID()) ;        
  
          double dist = conn.getSource().getPosition().getDistanceTo(dstRadio.getPosition());
/**/      System.out.printf("dist = %.2f\n", dist);
  
/**/      System.out.println("source: " + conn.getSource().getMote().getID());
          
          double signalStrength = 0.0;
  
          if (conn.getSource().isBackscatterTag()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(dstRadio.getChannel());
            
            /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
            signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
            //receivedPowerLst.add(signalStrength);
  /**/      //System.out.println("receivedPowerLst: " + receivedPowerLst);
  /**/      //System.out.println("carrierToTagDist: " + carrierToTagDist);
/**/        System.out.println("3.dstRadio: " + dstRadio.getMote().getID() + " - signalStrength: " + signalStrength);          
          } else {
  /**/        System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
              
              double maxTxDist = TRANSMITTING_RANGE
              * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
              
  /**/        System.out.printf("maxTxDist = %.7f\n", maxTxDist);
              
              double distFactor = dist/maxTxDist;
  /**/        System.out.printf("4.distFactor = %.7f\n", distFactor);
              signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
  /**/        System.out.println("5.dstRadio: " + dstRadio.getMote().getID() + " - signalStrength: " + signalStrength);          
          }
          
          if (dstRadio.getCurrentSignalStrength() < signalStrength) {
            dstRadio.setCurrentSignalStrength(signalStrength);
  /**/      System.out.println("6.dstRadio: " + dstRadio.getMote().getID() + " - signalStrength: " + dstRadio.getCurrentSignalStrength());          
          }
        }
        
        /* In case the tag stops listening the carrier from one connection but
         * it is still listening the carrier from another connection keep it signaled. */
        if (dstRadio.isBackscatterTag() && !dstRadio.isListeningCarrier()) {
/**/      System.out.println("7.dstRadio: " + dstRadio.getMote().getID() + " is not listening the carrier...but");
          if (conn.getSource().isGeneratingCarrier()) {
/**/        System.out.println("conn: " + conn.getID() + " is still active");
/**/        System.out.println("and its source: " + conn.getSource().getMote().getID() + " isGeneratingCarrier(): " + conn.getSource().isGeneratingCarrier());
            dstRadio.signalReceptionStart();
          }
        }
        /* If a dstRadio is already receiving a packet transmitted from a tag and a second because of tag which listens to 
         * the carrier radiated by two carrier generators transmitting on the same channel at the same time */
        if (conn.getSource().isListeningCarrier() && conn.getSource().getNumberOfConnectionsFromChannel(dstRadio.getChannel()) >= 2) {
/**/      System.out.println("tag: " + conn.getSource().getMote().getID() + " listens " + conn.getSource().getNumberOfConnectionsFromChannel(dstRadio.getChannel()) 
                              + " simultaneous carriers on the same channel");
          if (dstRadio.isReceiving()) {
/**/        System.out.println("dstRadio: " + dstRadio.getMote().getID() + " is receiving");
            dstRadio.interfereAnyReception();
          }
        }
        /* In case two active transmitters or two carrier generators or one active and one carrier
         * exist, the tag that is simultaneously accepting their signal gets interfered */
        
        if (conn.getSource().isGeneratingCarrier() && dstRadio.isListeningCarrier()) {
          if (dstRadio.getNumberOfConnectionsFromChannel(conn.getSource().getChannel() + 2) >= 2) {
/**/        System.out.println("tag: " + dstRadio.getMote().getID() + " gets interfered because it listens " 
                                 + dstRadio.getNumberOfConnectionsFromChannel(conn.getSource().getChannel() + 2) + " simultaneous carriers on channel " + conn.getSource().getChannel() + 2);
            dstRadio.interfereAnyReception();  
          }
        }
//        if (dstRadio.isBackscatterTag() && dstRadio.getNumberOfConnectionsFromChannel(conn.getSource().getChannel() + 2) >= 2) {
///**/        System.out.println("tag: " + dstRadio.getMote().getID() + " gets interfered because it listens " 
//                                 + dstRadio.getNumberOfConnectionsFromChannel(conn.getSource().getChannel() + 2) + " simultaneous carriers on channel " + conn.getSource().getChannel() + 2);
//            dstRadio.interfereAnyReception();  
//        }
      }
      /* Clear txChannels HashSet for the next connection */
      txChannels.clear();
    }

    /* Set signal strength to below weak on interfered */
    for (RadioConnection conn : conns) {
/**/  System.out.println("\nSet signal strength to below weak on interfered");
/**/  System.out.println("\nconn: " + conn.getID() + " - source: " + conn.getSource().getMote().getID());  
      txChannels = getTXChannels(conn.getSource());
  
      for (Radio intfRadio : conn.getInterfered()) {
/**/    System.out.println("ActiveConnID: " + conn.getID());
/**/    System.out.println("1.intfRadio: " +  intfRadio.getMote().getID() + " channel: " + intfRadio.getChannel());
/**/    System.out.println("channel: " + intfRadio.getChannel() + " is in txChannels: " + txChannels.contains(intfRadio.getChannel()));
        if(intfRadio.getChannel() >= 0 && !txChannels.contains(intfRadio.getChannel())) {
          continue;
        }

        /* In case the interfered radio is a tag, don't calculate its signal strength
         * since it does not have receiving capabilities. */
        if (!intfRadio.isBackscatterTag()) { //|| !intfRadio.isGeneratingCarrier()) {
        
  /**/    System.out.printf("2.intfRadio = %d\n", intfRadio.getMote().getID()) ;        
          
          double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
  /**/    System.out.printf("dist = %.7f\n", dist);
  
          double signalStrength = 0.0;
          
          double distFactor = 0.0;
          
          double ss_weak = STH;
          
  /**/    System.out.println("source: " + conn.getSource().getMote().getID());
          
          if (conn.getSource().isBackscatterTag()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(intfRadio.getChannel());
  
            double maxTxDist = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);
            
            distFactor = dist/maxTxDist;
            
            /* Signal strength of a CC2420 radio that is receiving from a backscatter tag */
            signalStrength = tagCurrentOutputPowerIndicator + GT + GR + pathLoss(dist);
  /**/      System.out.println("3.intfRadio: " + intfRadio.getMote().getID() + " - signalStrength: " + signalStrength);
  
          } else {
            //if (!intfRadio.isBackscatterTag()) {
    /**/      System.out.println("TRANSMITTING_RANGE: " + TRANSMITTING_RANGE);
    
              double maxTxDist = TRANSMITTING_RANGE
              * ((double) conn.getSource().getCurrentOutputPowerIndicator() / (double) conn.getSource().getOutputPowerIndicatorMax());
    /**/      System.out.printf("maxTxDist = %.7f\n", maxTxDist);
    
              distFactor = dist/maxTxDist;
    /**/      System.out.printf("distFactor = %.7f\n", distFactor);
    
              ss_weak = SS_WEAK;
    
              signalStrength = SS_STRONG + distFactor*(ss_weak - SS_STRONG);
    /**/      System.out.println("4.intfRadio: " + intfRadio.getMote().getID() + " - signalStrength: " + signalStrength);
            //}
          }
          
          if (distFactor < 1) { 
/**/        System.out.println("4a.intfRadio: " + intfRadio.getMote().getID() + " - signalStrength: " + intfRadio.getCurrentSignalStrength());
            if (intfRadio.getCurrentSignalStrength() < signalStrength) {
              intfRadio.setCurrentSignalStrength(signalStrength);
  /**/        System.out.println("5.intfRadio: " + intfRadio.getMote().getID() + " - signalStrength: " + intfRadio.getCurrentSignalStrength());          
            }
          } else {
            intfRadio.setCurrentSignalStrength(ss_weak);
  /**/      System.out.printf("6.intfRadio = %d , signal = %.2f\n", intfRadio.getMote().getID(), intfRadio.getCurrentSignalStrength());
            if (intfRadio.getCurrentSignalStrength() < ss_weak) {
  /**/        System.out.println("7.intfRadio: " + intfRadio.getMote().getID() + " - signalStrength: " + intfRadio.getCurrentSignalStrength());          
              intfRadio.setCurrentSignalStrength(ss_weak);
            }
          }
        }
        
        if (!intfRadio.isInterfered()) {
/**/        System.out.printf("8.intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
          
          /* Corner case */
          /* if the tag is already transmitting and the recv is interfered because the appropriate transmitting 
           * channel does not exist, when an active transmitter starts a new connection make sure that the recv 
           * gets interfered due to the reflections caused by the signal coming from that transmitter */
          if(conn.getSource().isBackscatterTag() && conn.getSource().isTransmitting()) {
            double tagCurrentOutputPowerIndicator = conn.getSource().getTagCurrentOutputPowerMax(intfRadio.getChannel());
            double tagInterferenceRange = calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);
/**/        System.out.println("A.tagInterferenceRange: " + tagInterferenceRange);
            double dist = conn.getSource().getPosition().getDistanceTo(intfRadio.getPosition());
/**/        System.out.println("A.tagIntfRadioDist: " + dist);
            if (dist <= tagInterferenceRange) {
/**/            System.out.println("WithinIR");
                intfRadio.interfereAnyReception();
            }
          } else {
            /* Note: The tag cannot be interfered */
            //if (!intfRadio.isBackscatterTag()) { //Can be deleted since the tag has no reception capabilities
              /*logger.warn("Radio was not interfered: " + intfRadio);*/
  /**/        System.out.printf("9.intfRadio %d was not interfered\n" , intfRadio.getMote().getID());
              intfRadio.interfereAnyReception();
            //}
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
/**/          System.out.println("dstRadio: " + dstRadio.getMote().getID() + " removed from conn " + conn);
/**/          System.out.println("dstRadio: " + dstRadio.getMote().getID() + " updates its Hashtable" );
              dstRadio.updateTagTXPower(conn);
/**/          System.out.println("UDGMBS.dstRadio: " + dstRadio.getMote().getID() + " signalReceptionEnd");
              dstRadio.signalReceptionEnd();
            } else {
/**/          System.out.println("EXPERIMENTAL_TRANSMISSION_FINISHED");
              /* EXPERIMENTAL: Simulating propagation delay */
              final Radio delayedRadio = dstRadio;
              TimeEvent delayedEvent = new TimeEvent(0) {
                public void execute(long t) {
/**/              System.out.println("dstRadio: " + dstRadio.getMote().getID() + " removed from conn " + conn);
/**/              System.out.println("dstRadio: " + dstRadio.getMote().getID() + " updates its Hashtable" );
                  delayedRadio.updateTagTXPower(conn);
/**/              System.out.println("UDGMBS.delayedRadio: " + delayedRadio.getMote().getID() + " signalReceptionEnd");
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
/**/          System.out.println("UDGMBS.intfRadio: " + intRadio.getMote().getID() + " signalReceptionEnd");
              intRadio.signalReceptionEnd();
            }
          }
          lastConnection = conn;
          getActiveConnectionsArrayList().remove(conn);
        }
      }
//    }

  } /* removeFromActiveConnections */
  
  @Override
  public void unregisterRadioInterface(Radio radio, Simulation sim) {
/**/System.out.println("UDGMBS.unregisterRadioInterface");
    super.unregisterRadioInterface(radio, sim);
    
    removeFromActiveConnections(radio);
    
    radioMediumObservable.setChangedAndNotify();
    
    /* Update signal strengths */
    updateSignalStrengths();
    
  } /* unregisterRadioInterface */

  
} /* UDGMBS */
