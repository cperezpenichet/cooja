/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Observable;
import java.util.Observer;
import java.util.WeakHashMap;

import org.apache.log4j.Logger;

import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.RadioMedium;
import org.contikios.cooja.RadioPacket;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.interfaces.CustomDataRadio;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.ScnObservable;
import org.jdom.Element;


/**
 * Abstract radio medium provides basic functionality for implementing radio
 * mediums.
 *
 * The radio medium forwards both radio packets and custom data objects.
 *
 * The registered radios' signal strengths are updated whenever the radio medium
 * changes. There are three fixed levels: no surrounding traffic heard, noise
 * heard and data heard.
 *
 * It handles radio registrations, radio loggers, active connections and
 * observes all registered radio interfaces.
 *
 * @author Fredrik Osterlind
 */
public abstract class AbstractRadioMedium extends RadioMedium {
	private static Logger logger = Logger.getLogger(AbstractRadioMedium.class);
	
	/* Signal strengths in dBm.
	 * Approx. values measured on TmoteSky */
	public static final double SS_NOTHING = -100;
	public static final double SS_STRONG = -10;
	public static final double SS_WEAK = -95;
	protected Map<Radio, Double> baseRssi = java.util.Collections.synchronizedMap(new HashMap<Radio, Double>());
	protected Map<Radio, Double> sendRssi = java.util.Collections.synchronizedMap(new HashMap<Radio, Double>());
	
	private ArrayList<Radio> registeredRadios = new ArrayList<Radio>();
	
	private ArrayList<RadioConnection> activeConnections = new ArrayList<RadioConnection>();
	
	private RadioConnection lastConnection = null;
	
	private Simulation simulation = null;
	
	//protected int txChannel;
	
	/* Book-keeping */
	public int COUNTER_TX = 0;
	public int COUNTER_RX = 0;
	public int COUNTER_INTERFERED = 0;
	
	/**
	 * Two Observables to observe the radioMedium and radioTransmissions
	 * @see addRadioTransmissionObserver
	 * @see addRadioMediumObserver
	 */
	protected ScnObservable radioMediumObservable = new ScnObservable();
	protected ScnObservable radioTransmissionObservable = new ScnObservable();
	
	/**
	 * This constructor should always be called from implemented radio mediums.
	 *
	 * @param simulation Simulation
	 */
	public AbstractRadioMedium(Simulation simulation) {
/**/   System.out.println("AbstractRadioMedium");
		this.simulation = simulation;
	}
	
	/**
	 * @return All registered radios
	 */
	public Radio[] getRegisteredRadios() {
	    System.out.println("getRegisteredRadios");
		return registeredRadios.toArray(new Radio[0]);
	}
	
	/**
	 * @return All active connections
	 */
	public RadioConnection[] getActiveConnections() {
		/* NOTE: toArray([0]) creates array and handles synchronization */
		return activeConnections.toArray(new RadioConnection[0]);
	}
	
	
	/**
   * @return All active connections as an ArrayList
   */
	public ArrayList<RadioConnection> getActiveConnectionsArrayList() {
    return activeConnections;
  }
	
	public void setLastConnection(RadioConnection lastConn) {
	  this.lastConnection = lastConn;
	}
	
	
	/**
	 * Creates a new connection from given radio.
	 *
	 * Determines which radios should receive or be interfered by the transmission.
	 *
	 * @param radio Source radio
	 * @return New connection
	 */
	abstract public RadioConnection createConnections(Radio radio);
	
	/**
	 * Updates all radio interfaces' signal strengths according to
	 * the current active connections.
	 */
	public void updateSignalStrengths() {
		
		/* Reset signal strengths */
		for (Radio radio : getRegisteredRadios()) {
			radio.setCurrentSignalStrength(getBaseRssi(radio));
		}
		
		/* Set signal strength to strong on destinations */
		RadioConnection[] conns = getActiveConnections();
		for (RadioConnection conn : conns) {
			if (conn.getSource().getCurrentSignalStrength() < SS_STRONG) {
				conn.getSource().setCurrentSignalStrength(SS_STRONG);
			}
			for (Radio dstRadio : conn.getDestinations()) {
				if (conn.getSource().getChannel() >= 0 &&
						dstRadio.getChannel() >= 0 &&
						conn.getSource().getChannel() != dstRadio.getChannel()) {
					continue;
				}
				if (dstRadio.getCurrentSignalStrength() < SS_STRONG) {
					dstRadio.setCurrentSignalStrength(SS_STRONG);
				}
			}
		}
		
		/* Set signal strength to weak on interfered */
		for (RadioConnection conn : conns) {
			for (Radio intfRadio : conn.getInterfered()) {
				if (intfRadio.getCurrentSignalStrength() < SS_STRONG) {
					intfRadio.setCurrentSignalStrength(SS_STRONG);
				}
				if (conn.getSource().getChannel() >= 0 &&
						intfRadio.getChannel() >= 0 &&
						conn.getSource().getChannel() != intfRadio.getChannel()) {
					continue;
				}
				if (!intfRadio.isInterfered()) {
					/*logger.warn("Radio was not interfered");*/
					intfRadio.interfereAnyReception();
				}
			}
		}
	}
	
	
	/**
	 * Remove given radio from any active connections.
	 * This method can be called if a radio node falls asleep or is removed.
	 *
	 * @param radio Radio
	 */
	private void removeFromActiveConnections(Radio radio) {
/**/System.out.println("Abstract.removeFromActiveConnections");
		/* This radio must not be a connection source */
		RadioConnection connection = getActiveConnectionFrom(radio);
		if (connection != null) {
			logger.fatal("Connection source turned off radio: " + radio);
		}
		
		/* Set interfered if currently a connection destination */
		for (RadioConnection conn : activeConnections) {
			if (conn.isDestination(radio)) {
				conn.addInterfered(radio);
				if (!radio.isInterfered()) {
					radio.interfereAnyReception();
				}
			}
		}
	}
	
	private RadioConnection getActiveConnectionFrom(Radio source) {
		for (RadioConnection conn : activeConnections) {
			if (conn.getSource() == source) {
				return conn;
			}
		}
		return null;
	}
	
	/**
	 * This observer is responsible for detecting radio interface events, for example
	 * new transmissions.
	 */
	private Observer radioEventsObserver = new Observer() {
		public void update(Observable obs, Object obj) {
			if (!(obs instanceof Radio)) {
				logger.fatal("Radio event dispatched by non-radio object");
				return;
			}
			
/**/  System.out.println("ARM.radioEventsObserver");
			
			Radio radio = (Radio) obs;
			
			final Radio.RadioEvent event = radio.getLastEvent();
			
/**/  System.out.println("ARM.radio: " + radio.getMote().getID() + " - event= " + event);
			
			switch (event) {
				case RECEPTION_STARTED:
				case RECEPTION_INTERFERED:
				case RECEPTION_FINISHED:
					break;

				case UNKNOWN:
				case HW_ON: {
					/* Update signal strengths */
					updateSignalStrengths();
				}
				break;
				case HW_OFF: {
					/* Remove any radio connections from this radio */
					removeFromActiveConnections(radio);
					/* Update signal strengths */
					updateSignalStrengths();
				}
				break;
				case TRANSMISSION_STARTED: {
/**/		  System.out.println("ARM.radio= " + radio.getMote().getID() + " - TRANSMISSION_STARTED");

					/* Create new radio connection */
					if (radio.isReceiving()) {
						/*
						 * Radio starts transmitting when it should be
						 * receiving! Ok, but it won't receive the packet
						 */
						radio.interfereAnyReception();
						for (RadioConnection conn : activeConnections) {
							if (conn.isDestination(radio)) {
								conn.addInterfered(radio);
							}
						}
					}
					
					RadioConnection newConnection = createConnections(radio);
					activeConnections.add(newConnection);
					
/**/			System.out.println("ARM.ActiveConnections: " + activeConnections.size());
/**/			System.out.println(radio.isGeneratingCarrier() ? "ARM.newConnectionFromCarrierID: " + newConnection.getID() :
              radio.isBackscatterTag() ? "ARM.newConnectionFromTagID: " + newConnection.getID() : "ARM.newConnectionFromSenderID: " + newConnection.getID());
/**/			System.out.println("ARM.AllDestinations: " + newConnection.getAllDestinations().length);
/**/      System.out.println("ARM.InterferedNonDestinations: " + newConnection.getInterferedNonDestinations().length);

           //if(radio.isListeningCarrier())

  					for (Radio r : newConnection.getAllDestinations()) {
  						if (newConnection.getDestinationDelay(r) == 0) {
  /**/			    System.out.println("ARM.r: " + r.getMote().getID() + " signalReceptionStart");
                r.signalReceptionStart();
  
  						} else {
  						  System.out.println("ARM.EXPERIMENTAL_TRANSMISSION_STARTED");
  							/* EXPERIMENTAL: Simulating propagation delay */
  							final Radio delayedRadio = r;
  							TimeEvent delayedEvent = new TimeEvent(0) {
  								public void execute(long t) {
  /**/						  System.out.println("ARM.delayedRadio: " + delayedRadio.getMote().getID() + " signalReceptionStart");
                    delayedRadio.signalReceptionStart();
  								}
  							};
  							simulation.scheduleEvent(delayedEvent, simulation.getSimulationTime() + newConnection.getDestinationDelay(r));
  							
  						}
  					} 
					
					/* Update signal strengths */
					updateSignalStrengths();
					
					/* Notify observers */
					lastConnection = null;
					radioTransmissionObservable.setChangedAndNotify();
				}
				break;
				case TRANSMISSION_FINISHED: {
/**/			System.out.println("ARM.radio= " + radio.getMote().getID() + " - TRANSMISSION_FINISHED");
					/* Remove radio connection */

					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						logger.fatal("No radio connection found");
						return;
					}
					
/**/      System.out.println("\nActiveConnections: " + getActiveConnections().length);
/**/      System.out.println("conn: " + connection.getID() + " stops"); 

					activeConnections.remove(connection);
					lastConnection = connection;
					COUNTER_TX++;
					for (Radio dstRadio : connection.getAllDestinations()) {
						if (connection.getDestinationDelay(dstRadio) == 0) {
/**/						System.out.println("ARM.dstRadio: " + dstRadio.getMote().getID() + " signalReceptionEnd");
                            dstRadio.signalReceptionEnd();
						} else {
/**/						System.out.println("EXPERIMENTAL_TRANSMISSION_FINISHED");
							/* EXPERIMENTAL: Simulating propagation delay */
							final Radio delayedRadio = dstRadio;
							TimeEvent delayedEvent = new TimeEvent(0) {
								public void execute(long t) {
/**/						  System.out.println("ARM.delayedRadio: " + delayedRadio.getMote().getID() + " signalReceptionEnd");
                  delayedRadio.signalReceptionEnd();
								}
							};
							simulation.scheduleEvent(delayedEvent,
									simulation.getSimulationTime() + connection.getDestinationDelay(dstRadio));
						}
					}
					COUNTER_RX += connection.getDestinations().length;
					COUNTER_INTERFERED += connection.getInterfered().length;
					for (Radio intRadio : connection.getInterferedNonDestinations()) {
					  if (intRadio.isInterfered()) {
/**/				  System.out.println("ARM.intfRadio: " + intRadio.getMote().getID() + " signalReceptionEnd");
              intRadio.signalReceptionEnd();
					  }
					}
					
					/* Update signal strengths */
					updateSignalStrengths();
					System.out.println("ARM.connection: " + lastConnection.getID() + " is about to finish");
					
					/* Notify observers */
					radioTransmissionObservable.setChangedAndNotify();
/**/				System.out.println("ARM.connection: " + lastConnection.getID() + " finished");
				}
				break;
				case CUSTOM_DATA_TRANSMITTED: {
/**/                System.out.println("ARM.radio= " + radio.getMote().getID() + " - CUSTOM_DATA_TRANSMITTED");		
					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						logger.fatal("No radio connection found");
						return;
					}
					
					/* Custom data object */
					Object data = ((CustomDataRadio) radio).getLastCustomDataTransmitted();
					if (data == null) {
						logger.fatal("No custom data objecTransmissiont to forward");
						return;
					}
					
					for (Radio dstRadio : connection.getAllDestinations()) {
/**///                    System.out.println("dstRadio instanceof CustomDataRadio: " + (dstRadio instanceof CustomDataRadio));
/**///                    System.out.println("dstRadio.canReceiveFrom(radio): " + (((CustomDataRadio) dstRadio).canReceiveFrom((CustomDataRadio)radio)) );
/**///                    System.out.println("dstRadio.getClass(): " + ((CustomDataRadio)dstRadio).getClass());
/**///                    System.out.println("radio.getClass(): " + ((CustomDataRadio)radio).getClass());

						if (!(dstRadio instanceof CustomDataRadio) || 
						    !((CustomDataRadio) dstRadio).canReceiveFrom((CustomDataRadio)radio)) {
							/* Radios communicate via radio packets */
/**/     				    System.out.println("ARM.Radios communicate via radio packets");
							continue;
						}

						 
						if (connection.getDestinationDelay(dstRadio) == 0) {
/**/                        System.out.println("ARM.(CustomDataRadio)dstRadio: " + dstRadio.getMote().getID() + " receiveCustomData");
							((CustomDataRadio) dstRadio).receiveCustomData(data);
						} else {
/**/						System.out.println("ARM.EXPERIMENTAL_CUSTOM_DATA_TRANSMITTED");							
							/* EXPERIMENTAL: Simulating propagation delay */
							final CustomDataRadio delayedRadio = (CustomDataRadio) dstRadio;
							final Object delayedData = data;
							TimeEvent delayedEvent = new TimeEvent(0) {
								public void execute(long t) {
/**/                                System.out.println("ARM.(CustomDataRadio)delayedRadio: " + ((Radio)delayedRadio).getMote().getID() + " receiveCustomData");								    
									delayedRadio.receiveCustomData(delayedData);
								}
							};
							simulation.scheduleEvent(delayedEvent,
									simulation.getSimulationTime() + connection.getDestinationDelay(dstRadio));
							
						}
					}
					
				}
				break;
				case PACKET_TRANSMITTED: {
/**/                System.out.println("ARM.radio= " + radio.getMote().getID() + " - PACKET_TRANSMITTED");				    
					/* Connection */
					RadioConnection connection = getActiveConnectionFrom(radio);
					if (connection == null) {
						logger.fatal("No radio connection found");
						return;
					}
					
					/* Radio packet */
					RadioPacket packet = radio.getLastPacketTransmitted();
					if (packet == null) {
						logger.fatal("No radio packet to forward");
						return;
					}
					
					for (Radio dstRadio : connection.getAllDestinations()) {

					  if ((radio instanceof CustomDataRadio) &&
					      (dstRadio instanceof CustomDataRadio) && 
					      ((CustomDataRadio) dstRadio).canReceiveFrom((CustomDataRadio)radio)) {
					    /* Radios instead communicate via custom data objects */
/**/                    System.out.println("ARM.Radios instead communicate via custom data objects");                  
					    continue;
					  }

						
						/* Forward radio packet */
						if (connection.getDestinationDelay(dstRadio) == 0) {
							dstRadio.setReceivedPacket(packet);
/**/                        System.out.println("ARM.dstRadio: " + dstRadio.getMote().getID() + " setReceivedPacket");
						} else {
/**/						System.out.println("EXPERIMENTAL_PACKET_TRANSMITTED");
							/* EXPERIMENTAL: Simulating propagation delay */
							final Radio delayedRadio = dstRadio;
							final RadioPacket delayedPacket = packet;
							TimeEvent delayedEvent = new TimeEvent(0) {
								public void execute(long t) {
									delayedRadio.setReceivedPacket(delayedPacket);
/**/                                System.out.println("ARM.delayedRadio: " + delayedRadio.getMote().getID() + " setReceivedPacket");
								}
							};
							simulation.scheduleEvent(delayedEvent,
									simulation.getSimulationTime() + connection.getDestinationDelay(dstRadio));
						}
						
					}
				}
				break;
				
				/* When a radio interface event is detected both radioEventsObserver observers in UDGMBS and 
	             * AbstractRadioMedium classes are called.
	             * 
	             * These empty additions intend to make the default fatal message disappeared for cases the 
	             * are not being updated by the observer in this class.
	             */
	            case CARRIER_LISTENING_STARTED:
	            case CARRIER_LISTENING_STOPPED:    
	                break; 
				
				default:
					logger.fatal("Unsupported radio event: " + event);
			}
		}
	};
	
	public void registerMote(Mote mote, Simulation sim) {
		registerRadioInterface(mote.getInterfaces().getRadio(), sim);
	}
	
	public void unregisterMote(Mote mote, Simulation sim) {
		unregisterRadioInterface(mote.getInterfaces().getRadio(), sim);
	}
	
	public void registerRadioInterface(Radio radio, Simulation sim) {
		if (radio == null) {
			logger.warn("No radio to register");
			return;
		}
		
/**/    System.out.println("ARM.registerRadioInterface");		
		registeredRadios.add(radio);
		radio.addObserver(radioEventsObserver);
		radioMediumObservable.setChangedAndNotify();
		
		/* Update signal strengths */
		updateSignalStrengths();
	}
	
	public void unregisterRadioInterface(Radio radio, Simulation sim) {
/**/	  System.out.println("Abstract.unregisterRadioInterface");
		if (!registeredRadios.contains(radio)) {
			logger.warn("No radio to unregister: " + radio);
			return;
		}
		
/**/System.out.println("ARM.unregisterRadioInterface");
		
		radio.deleteObserver(radioEventsObserver);
		registeredRadios.remove(radio);
		
		removeFromActiveConnections(radio);
		
		radioMediumObservable.setChangedAndNotify();
		
		/* Update signal strengths */
		updateSignalStrengths();
	}
	
	/**
	* Get the RSSI value that is set when there is "silence"
	* 
	* @param radio
	*          The radio to get the base RSSI for
	* @return The base RSSI value; Default: SS_NOTHING
	*/
	public double getBaseRssi(Radio radio) {
		Double rssi = baseRssi.get(radio);
		if (rssi == null) {
			rssi = SS_NOTHING;
		}
		return rssi;
	}

	/**
	* Set the base RSSI for a radio. This value is set when there is "silence"
	* 
	* @param radio
	*          The radio to set the RSSI value for
	* @param rssi
	*          The RSSI value to set during silence
	*/
	public void setBaseRssi(Radio radio, double rssi) {
		baseRssi.put(radio, rssi);
		simulation.invokeSimulationThread(new Runnable() {				
			@Override
			public void run() {
				updateSignalStrengths();
			}
		});
	}

	
	/**
	* Get the minimum RSSI value that is set when the radio is sending
	* 
	* @param radio
	*          The radio to get the send RSSI for
	* @return The send RSSI value; Default: SS_STRONG
	*/
	public double getSendRssi(Radio radio) {
		Double rssi = sendRssi.get(radio);
		if (rssi == null) {
			rssi = SS_STRONG;
		}
		return rssi;
	}

	/**
	* Set the send RSSI for a radio. This is the minimum value when the radio is
	* sending
	* 
	* @param radio
	*          The radio to set the RSSI value for
	* @param rssi
	*          The minimum RSSI value to set when sending
	*/
	public void setSendRssi(Radio radio, double rssi) {
		sendRssi.put(radio, rssi);
	}
	
	/**
	 * Register an observer that gets notified when the radiotransmissions changed.
	 * E.g. creating new connections.
	 * This does not include changes in the settings and (de-)registration of radios.
	 * @see addRadioMediumObserver
	 * @param observer the Observer to register
	 */
	public void addRadioTransmissionObserver(Observer observer) {
		radioTransmissionObservable.addObserver(observer);
	}
	
	public Observable getRadioTransmissionObservable() {
		return radioTransmissionObservable;
	}
	
	public void deleteRadioTransmissionObserver(Observer observer) {
		radioTransmissionObservable.deleteObserver(observer);
	}
	
	/**
	 * Register an observer that gets notified when the radio medium changed.
	 * This includes changes in the settings and (de-)registration of radios. 
	 * This does not include transmissions, etc as these are part of the radio
	 * and not the radio medium itself.
	 * @see addRadioTransmissionObserver
	 * @param observer the Observer to register
	 */
	public void addRadioMediumObserver(Observer observer) {
		radioMediumObservable.addObserver(observer);
	}
	
	/**
	 * @return the radioMediumObservable
	 */
	public Observable getRadioMediumObservable() {
		return radioMediumObservable;
	}
	
	public void deleteRadioMediumObserver(Observer observer) {
		radioMediumObservable.deleteObserver(observer);
	}
	
	public RadioConnection getLastConnection() {
		return lastConnection;
	}
	
	public Simulation getSimulation() {
    return simulation;
  }
	
	public Collection<Element> getConfigXML() {
		Collection<Element> config = new ArrayList<Element>();
		for(Entry<Radio, Double> ent: baseRssi.entrySet()){
			Element element = new Element("BaseRSSIConfig");
			element.setAttribute("Mote", "" + ent.getKey().getMote().getID());
			element.addContent("" + ent.getValue());
			config.add(element);
		}

		for(Entry<Radio, Double> ent: sendRssi.entrySet()){
			Element element = new Element("SendRSSIConfig");
			element.setAttribute("Mote", "" + ent.getKey().getMote().getID());
			element.addContent("" + ent.getValue());
			config.add(element);
		}

		return config;
	}
	
	private Collection<Element> delayedConfiguration = null;
	
	public boolean setConfigXML(final Collection<Element> configXML, boolean visAvailable) {
		delayedConfiguration = configXML;
		return true;
	}
	
	public void simulationFinishedLoading() {
		if (delayedConfiguration == null) {
			return;
		}

		for (Element element : delayedConfiguration) {
			if (element.getName().equals("BaseRSSIConfig")) {
				Radio r = simulation.getMoteWithID(Integer.parseInt(element.getAttribute("Mote").getValue())).getInterfaces().getRadio();				
				setBaseRssi(r, Double.parseDouble(element.getText()));
			} else 	if (element.getName().equals("SendRSSIConfig")) {
				Radio r = simulation.getMoteWithID(Integer.parseInt(element.getAttribute("Mote").getValue())).getInterfaces().getRadio();				
				setSendRssi(r, Double.parseDouble(element.getText()));
			} 
		}
	}

}
