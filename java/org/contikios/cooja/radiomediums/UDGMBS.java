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

import javax.swing.text.html.HTML.Tag;

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
@ClassDescription("Unit Disk Graph Medium for Backscattering (UDGMBS)")
public class UDGMBS extends AbstractRadioMedium {
	
	private static Logger logger = Logger.getLogger(UDGMBS.class);
	
	public double SUCCESS_RATIO_TX = 1.0;
	public double SUCCESS_RATIO_RX = 1.0;
	public double TRANSMITTING_RANGE = 50;
	public double INTERFERENCE_RANGE = 100;
	
	public double TAG_TRANSMITTING_RANGE = 50;
	
	/* New addition */
	/* Variables for Radar range equation */
	public final double GainTX = 10;
	public final double GainRX = 10;
	public final double waveLength = 0.12;
	public final double distanceFromTX = 50;  // This won't be a constant - it will be calculated from a function as soon as I'll define the B.T  
	public final double distanceFromRX = 100; // This won't be a constant - it will be calculated from a function as soon as I'll define the B.T 
	public final double radarCrossSection = 20;
	
	public double powerRX;
	
	private DirectedGraphMedium dgrm;
	
	private Random random = null;
	
	/* Constructor */
	public UDGMBS(Simulation simulation){
		super(simulation);
		
		random = simulation.getRandomGenerator();
		dgrm = new DirectedGraphMedium() {
			
			protected void analyzeEdges() {				
				clearEdges();
				
				for(Radio source: UDGMBS.this.getRegisteredRadios()){
					Position sourcePos = source.getPosition();
					
					for(Radio dest: UDGMBS.this.getRegisteredRadios()){
						Position destPos = dest.getPosition();
						
						if(sourcePos == destPos){
							continue;							
						}
						
						double distance = sourcePos.getDistanceTo(destPos);
						if(distance < Math.max(TRANSMITTING_RANGE, INTERFERENCE_RANGE)){
							addEdge(new DirectedGraphMedium.Edge( source, new DGRMDestinationRadio(dest) ));					
							
						}
					}
					
				}
				super.analyzeEdges();
			}
		};
		
		/* Register as position observer */
		final Observer positionObserver = new Observer() {
			
			public void update(Observable o, Object arg){
				dgrm.requestEdgeAnalysis();				
			}
		};
		
		simulation.getEventCentral().addMoteCountListener(new MoteCountListener(){
			
			public void moteWasAdded(Mote mote){
				mote.getInterfaces().getPosition().addObserver(positionObserver);
				dgrm.requestEdgeAnalysis();
			}
			
			public void moteWasRemoved(Mote mote){
				mote.getInterfaces().getPosition().addObserver(positionObserver);
				dgrm.requestEdgeAnalysis();
			}
		});
		
		for(Mote mote: simulation.getMotes()){
			mote.getInterfaces().getPosition().addObserver(positionObserver);
		}
		dgrm.requestEdgeAnalysis();
		
		/* Register visualizer skin */
		Visualizer.registerVisualizerSkin(UDGMBSVisualizerSkin.class);	
	
	}//end of Constructor
	
	public void removed(){
		super.removed();
		
		Visualizer.unregisterVisualizerSkin(UDGMBSVisualizerSkin.class);
	}
	
	public void setTxRange(double r){
		TRANSMITTING_RANGE = r;
		dgrm.requestEdgeAnalysis();
	}
	
	
	public void setInterferenceRange(double r){
		INTERFERENCE_RANGE = r;
		dgrm.requestEdgeAnalysis();
	}
	
	//the tag that creates the new connection will be of type Tag which is not yet implemented !!
	public RadioConnection createConnections(Radio tag){
		RadioConnection newConnection = new RadioConnection(tag);
		
		/* Fail connection transmission randomly - no radios will hear this transmission */
		if( getTxSuccessProbability(tag) < 1.0 && random.nextDouble() > getTxSuccessProbability(tag) ){			
			return newConnection;
		}
		
		/* Calculate ranges: grows with radio output power */
//		double moteTransmissionRange = TRANSMITTING_RANGE * ((double)tag.getCurrentOutputPowerIndicator() / (double)
//										tag.getOutputPowerIndicatorMax());
		
		double tagTransmissionRange = TAG_TRANSMITTING_RANGE * ((double)tag.getCurrentOutputPowerIndicator() / (double)
									 tag.getOutputPowerIndicatorMax());
		
		double moteInterferenceRange = INTERFERENCE_RANGE * ((double)tag.getCurrentOutputPowerIndicator() / (double)
										tag.getOutputPowerIndicatorMax());
		
		
		DestinationRadio[] potentialCarriersAndDestinations = dgrm.getPotentialDestinations(tag);
		
		if (potentialCarriersAndDestinations == null) {
			return newConnection;
		}
		
		Position tagPos = tag.getPosition();
		
		for(DestinationRadio carrier: potentialCarriersAndDestinations) {
			
			Radio carrierGen = carrier.radio;
			
			double carrierGenTransmissionRange = TRANSMITTING_RANGE * ((double)carrierGen.getCurrentOutputPowerIndicator()
												 / (double) carrierGen.getOutputPowerIndicatorMax());
			
			if(carrierGen.getChannel() >= 0 && tag.getChannel() >= 0 && carrierGen.getChannel() != tag.getChannel()) {
				continue;
				
			}	
			

//			/* New addition */
//			/* Radar Range Equation */
//			powerRX = radarCrossSection * ((GainTX * GainRX) / 4 * Math.PI ) * 
//					  Math.pow( (waveLength / 4 * Math.PI * distanceFromTX * distanceFromRX), 2.0 ) * 
//					  ( (double) carrierGen.getCurrentOutputPowerIndicator() );
			
			Position carrierPos = carrierGen.getPosition();			
			double carrierToTagDist = carrierPos.getDistanceTo(tagPos);
			
			if(carrierToTagDist <= carrierGenTransmissionRange) {
				
				if(!carrierGen.isRadioOn()){
					newConnection.addInterfered(carrierGen);
				}
				else if(carrierGen.isInterfered()) {
					newConnection.addInterfered(carrierGen);
				}/* New addition */
				else if(carrierGen.isGeneratingCarrier()) {
					newConnection.addInterfered(carrierGen);
				}
				else if(carrierGen.isTransmitting()) {
					newConnection.addInterfered(carrierGen);
				} 
				else if(carrierGen.isReceiving()) {
					newConnection.addInterfered(carrierGen);
					carrierGen.interfereAnyReception();
					
					 /* Interfere receiver in all other active radio connections */
					for(RadioConnection conn: getActiveConnections()){
						if(conn.isDestination(carrierGen)) {
							conn.addInterfered(carrierGen);
						}
					}
				}
				else {
					newConnection.addDestination(carrierGen);;
				}
			}	
			else if(carrierToTagDist <= moteInterferenceRange) {
				
				newConnection.addInterfered(carrierGen);
				carrierGen.interfereAnyReception();
			}
			
		
			for(DestinationRadio dest: potentialCarriersAndDestinations) {
				
				Radio recv = dest.radio;
				
				if(tag.getChannel() >= 0 && recv.getChannel() >=0  && tag.getChannel() != recv.getChannel()
				   && carrierGen.getChannel() + 2 != recv.getChannel() ) {
					
					continue;
				}
				
				Position recvPos = recv.getPosition();				
				double tagToRecvDist = tagPos.getDistanceTo(recvPos);
				
				if(tagToRecvDist <= tagTransmissionRange) {
					
					if(!recv.isRadioOn()) {
						newConnection.addInterfered(recv);
					}
					else if(recv.isInterfered()) {
						/* Was interfered: keep interfering */
						newConnection.addInterfered(recv);
					}
					else if(recv.isTransmitting()) {
						newConnection.addInterfered(recv);
					} 
					else if(recv.isReceiving()) {
						/* Was receiving: start interfering */
						newConnection.addInterfered(recv);
						recv.interfereAnyReception();
						
						 /* Interfere receiver in all other active radio connections */
						for(RadioConnection conn: getActiveConnections()){
							if(conn.isDestination(recv)) {
								conn.addInterfered(recv);
							}
						}
					}
					else {
						/* Success: radio starts receiving */
						newConnection.addDestination(recv);
					}
				}
				else if(tagToRecvDist <= moteInterferenceRange) {
					newConnection.addInterfered(recv);
					recv.interfereAnyReception();
				}
			}
		}
		return newConnection;
	}
		
		
	
	public double getSuccessProbability(Radio source, Radio dest){
		return getTxSuccessProbability(source) * getRxSuccessProbability(source, dest);
	}
	
	
	public double getTxSuccessProbability(Radio source){
		return SUCCESS_RATIO_TX;
	}
	
	
	public double getRxSuccessProbability(Radio source, Radio dest){
		double distance = source.getPosition().getDistanceTo( dest.getPosition() );
		double distanceSquared = Math.pow(distance, 2.0);
		double distanceMax = TRANSMITTING_RANGE * ( (double)source.getCurrentOutputPowerIndicator() / (double)
							source.getOutputPowerIndicatorMax() );
		
		if(distanceMax == 0.0){
			return 0.0;
		}
		
		double distanceMaxSquared = Math.pow(distanceMax, 2.0);
		double ratio  = distanceMax / distanceMaxSquared ;
		if(ratio > 1.0){
			return 0.0;
		}
		return 1.0 * ratio*(1.0 - SUCCESS_RATIO_RX);
	}
	
	
	/* Uses distance as a signal strength factor */
	public void updateSignalsStrengths(){
		
		/* Reset signal strengths */
		for(Radio radio: getRegisteredRadios()) {
			radio.setCurrentSignalStrength(getBaseRssi(radio));
		}
		
		/* Set signal strength to below strong on destinations */
		RadioConnection[] conns = getActiveConnections();
		for(RadioConnection conn: conns){
			if(conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
				conn.getSource().setCurrentSignalStrength(SS_STRONG);
			}
			
			/* getDestinations -> non-interfered destinations */
			for(Radio dstRadio: conn.getDestinations()) {
				if(conn.getSource().getChannel() >=0 && dstRadio.getChannel() >= 0 && 
						conn.getSource().getChannel() != dstRadio.getChannel()) {
					
					continue;
				}
				
				double dist = conn.getSource().getPosition().getDistanceTo( dstRadio.getPosition() );
				
				double maxTxDist = TRANSMITTING_RANGE * ( (double)conn.getSource().getCurrentOutputPowerIndicator() / (double)
						conn.getSource().getOutputPowerIndicatorMax() );
				
				double distFactor = dist / maxTxDist;
				
				double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
				if(dstRadio.getCurrentSignalStrength() < signalStrength){
					dstRadio.setCurrentSignalStrength(signalStrength);
				}
			}
		}
		
		/* getInterfered -> All radios interfered by this connection including destinations */
		for(RadioConnection conn: conns){
			for(Radio intfRadio: conn.getInterfered()) {
				if(conn.getSource().getChannel() >= 0 && intfRadio.getChannel() >= 0 
						&& conn.getSource().getChannel() != intfRadio.getChannel() ) {
					
					continue;
				}
				
				double dist = conn.getSource().getPosition().getDistanceTo( intfRadio.getPosition() );
				
				double maxTxDist = TRANSMITTING_RANGE * ( (double)conn.getSource().getCurrentOutputPowerIndicator() / (double)
						conn.getSource().getOutputPowerIndicatorMax() );
				
				double distFactor = dist / maxTxDist;
				
				if(distFactor < 1){
					double signalStrength = SS_STRONG + distFactor*(SS_WEAK - SS_STRONG);
					if(intfRadio.getCurrentSignalStrength() < signalStrength){
						intfRadio.setCurrentSignalStrength(signalStrength);
					}
				}
				else{
					intfRadio.setCurrentSignalStrength(SS_WEAK);
					if(intfRadio.getCurrentSignalStrength() < SS_WEAK){
						intfRadio.setCurrentSignalStrength(SS_WEAK);
					}
				}
			
				if( !intfRadio.isInterfered() ) {
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

	}
