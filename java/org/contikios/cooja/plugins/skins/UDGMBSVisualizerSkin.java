/*
 * Copyright (c) 2009-2013, Swedish Institute of Computer Science, TU Braunscheig
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
package org.contikios.cooja.plugins.skins;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.RadioConnection;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.Visualizer.SimulationMenuAction;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.radiomediums.UDGM;
import org.contikios.cooja.radiomediums.UDGMBS;

/**
 * Visualizer skin for configuring the Unit Disk Graph radio medium for backscaterring communication (UDGMBS).
 *
 * Allows a user to change the collective TX/interference ranges, and the TX/RX
 * success ratio.
 *
 * To also see radio traffic, this skin can be combined with {@link
 * TrafficVisualizerSkin}.
 *
 * @see TrafficVisualizerSkin
 * @see UDGMBS
 * @author Fredrik Osterlind
 * @author Enrico Joerns
 */
@ClassDescription("Radio environment (UDGMBS)")
@SupportedArguments(radioMediums = {UDGMBS.class})
public class UDGMBSVisualizerSkin extends UDGMVisualizerSkin {

  private static final Logger logger = Logger.getLogger(UDGMBSVisualizerSkin.class);

  private static final Color COLOR_TX = new Color(0, 255, 0, 100);
  private static final Color COLOR_INT = new Color(50, 50, 50, 100);
  
  private Simulation simulation = null;
  private Visualizer visualizer = null;
  private UDGMBS radioMedium = null;

  private JInternalFrame rrFrame;
  //private Box ratioRX, ratioTX, rangeTX, rangeINT;
  private Box ratioRX, ratioTX;
  
  private Hashtable<Integer, Integer> carrierColor = new Hashtable<Integer, Integer>();

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    super.setActive(simulation, vis);
/**/System.out.println("UDGMBS.setActive");
    if (!(simulation.getRadioMedium() instanceof UDGMBS)) {
      logger.fatal("Cannot activate UDGMBS skin for unknown radio medium: " + simulation.getRadioMedium());
      return;
    }
    this.simulation = simulation;
    this.visualizer = vis;
    this.radioMedium = (UDGMBS) simulation.getRadioMedium();
    
    /* Change the title of the visualizer */
    getVisualizer().setTitle("Network - UDGMBS");

    /* Spinner GUI components */
//    SpinnerNumberModel transmissionModel = new SpinnerNumberModel();
//    transmissionModel.setValue(new Double(radioMedium.TRANSMITTING_RANGE));
//    transmissionModel.setStepSize(new Double(1.0)); // 1m
//    transmissionModel.setMinimum(new Double(0.0));
//
//    SpinnerNumberModel interferenceModel = new SpinnerNumberModel();
//    interferenceModel.setValue(new Double(radioMedium.INTERFERENCE_RANGE));
//    interferenceModel.setStepSize(new Double(1.0)); // 1m
//    interferenceModel.setMinimum(new Double(0.0));
    
    SpinnerNumberModel successRatioTxModel = new SpinnerNumberModel();
    successRatioTxModel.setValue(new Double(radioMedium.SUCCESS_RATIO_TX));
    successRatioTxModel.setStepSize(new Double(0.001)); // 0.1%
    successRatioTxModel.setMinimum(new Double(0.0));
    successRatioTxModel.setMaximum(new Double(1.0));

    SpinnerNumberModel successRatioRxModel = new SpinnerNumberModel();
    successRatioRxModel.setValue(new Double(radioMedium.SUCCESS_RATIO_RX));
    successRatioRxModel.setStepSize(new Double(0.001)); // 0.1%
    successRatioRxModel.setMinimum(new Double(0.0));
    successRatioRxModel.setMaximum(new Double(1.0));
    
    JSpinner.NumberEditor editor;
//    final JSpinner txRangeSpinner = new JSpinner(transmissionModel);
//    editor = new JSpinner.NumberEditor(txRangeSpinner, "0m");
//    txRangeSpinner.setEditor(editor);
//    final JSpinner interferenceRangeSpinner = new JSpinner(interferenceModel);
//    editor = new JSpinner.NumberEditor(interferenceRangeSpinner, "0m");
//    interferenceRangeSpinner.setEditor(editor);
    final JSpinner successRatioTxSpinner = new JSpinner(successRatioTxModel);
    editor = new JSpinner.NumberEditor(successRatioTxSpinner, "0.0%");
    successRatioTxSpinner.setEditor(editor);
    final JSpinner successRatioRxSpinner = new JSpinner(successRatioRxModel);
    editor = new JSpinner.NumberEditor(successRatioRxSpinner, "0.0%");
    successRatioRxSpinner.setEditor(editor);

//    ((JSpinner.DefaultEditor) txRangeSpinner.getEditor()).getTextField().setColumns(5);
//    ((JSpinner.DefaultEditor) interferenceRangeSpinner.getEditor()).getTextField().setColumns(5);
    ((JSpinner.DefaultEditor) successRatioTxSpinner.getEditor()).getTextField().setColumns(5);
    ((JSpinner.DefaultEditor) successRatioRxSpinner.getEditor()).getTextField().setColumns(5);
//    txRangeSpinner.setToolTipText("Transmitting range (m)");
//    interferenceRangeSpinner.setToolTipText("Interference range (m)");
    successRatioTxSpinner.setToolTipText("Transmission success ratio (%)");
    successRatioRxSpinner.setToolTipText("Reception success ratio (%)");

//    txRangeSpinner.addChangeListener(new ChangeListener() {
//      @Override
//      public void stateChanged(ChangeEvent e) {
//        radioMedium.setTxRange(((SpinnerNumberModel) txRangeSpinner.getModel())
//                .getNumber().doubleValue());
//        visualizer.repaint();
//      }
//    });
//
//    interferenceRangeSpinner.addChangeListener(new ChangeListener() {
//      @Override
//      public void stateChanged(ChangeEvent e) {
//        radioMedium.setInterferenceRange(((SpinnerNumberModel) interferenceRangeSpinner.getModel())
//                .getNumber().doubleValue());
//        visualizer.repaint();
//      }
//    });
    
    successRatioTxSpinner.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        radioMedium.SUCCESS_RATIO_TX = ((SpinnerNumberModel) successRatioTxSpinner.getModel())
                .getNumber().doubleValue();
        visualizer.repaint();
      }
    });

    successRatioRxSpinner.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        radioMedium.SUCCESS_RATIO_RX = ((SpinnerNumberModel) successRatioRxSpinner.getModel())
                .getNumber().doubleValue();
        visualizer.repaint();
      }
    });

    /* Register menu actions */
    //visualizer.registerSimulationMenuAction(RangeMenuAction.class);
    visualizer.registerSimulationMenuAction(SuccessRatioMenuAction.class);

    /* UI components */
    JPanel main = new JPanel();
    main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
    main.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));

//    rangeTX = Box.createHorizontalBox();
//    rangeTX.add(new JLabel("TX range:"));
//    rangeTX.add(Box.createHorizontalStrut(5));
//    rangeTX.add(txRangeSpinner);
//    rangeINT = Box.createHorizontalBox();
//    rangeINT.add(new JLabel("INT range:"));
//    rangeINT.add(Box.createHorizontalStrut(5));
//    rangeINT.add(interferenceRangeSpinner);
//    
    ratioTX = Box.createHorizontalBox();
    ratioTX.add(new JLabel("TX ratio:"));
    ratioTX.add(Box.createHorizontalStrut(5));
    ratioTX.add(successRatioTxSpinner);
    ratioRX = Box.createHorizontalBox();
    ratioRX.add(new JLabel("RX ratio:"));
    ratioRX.add(Box.createHorizontalStrut(5));
    ratioRX.add(successRatioRxSpinner);

//    rangeTX.setVisible(false);
//    rangeINT.setVisible(false);
    ratioTX.setVisible(false);
    ratioRX.setVisible(false);
    
//    main.add(rangeTX);
//    main.add(rangeINT);
    main.add(ratioTX);
    main.add(ratioRX);
    
    /*
     * Now we have a rrFrame for each one of the motes
     * either it is a cc2420 radio or a tag.
     */

    /* Change the title of the rrFrame of the parent */
    getRRFrame().setTitle("CC2420 Radio");
    
    /* Set the rrFrame for the radio */
    rrFrame = new JInternalFrame("Backscatter Tag", false, true);
    rrFrame.setVisible(false);
    rrFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    rrFrame.addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosing(InternalFrameEvent ife) {
        super.internalFrameClosed(ife);
//        rangeTX.setVisible(false);
//        rangeINT.setVisible(false);
        ratioTX.setVisible(false);
        ratioRX.setVisible(false);
        rrFrame.setVisible(false);
      }
    });

    rrFrame.getContentPane().add(BorderLayout.CENTER, main);
    rrFrame.pack();
    
    /* Predefined colors for each one of the sixteen 
     * 802.15.4 channels. */
    setColorsForTagTXChannel();
  }

  @Override
  public void setInactive() {
    super.setInactive();
/**/System.out.println("UDGMBS.setInactive");
      
    if (simulation == null) {
      /* Skin was never activated */
      return;
    }

    /* Remove spinners etc */
    visualizer.getCurrentCanvas().remove(rrFrame);

    /* Unregister menu actions */
    //visualizer.unregisterSimulationMenuAction(RangeMenuAction.class);
    visualizer.unregisterSimulationMenuAction(SuccessRatioMenuAction.class);
  }

  @Override
  public Color[] getColorOf(Mote mote) {
/**/System.out.println("UDGMBS.getColorOf");      
    return null;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {
/**/System.out.println("UDGMBS.paintBeforeMotes");
      
    Set<Mote> selectedMotes = visualizer.getSelectedMotes();
    if (simulation == null || selectedMotes == null) {
/**/System.out.println("simulation || selectedMotes = null");      
      return;
    }
    
/**/System.out.println("UDGMBS.paintBeforeMotesSTART");
    
/**/System.out.println("selectedMotes: " + selectedMotes);

    for (Mote selectedMote : selectedMotes) { 
/**/  System.out.println("selectedMote: " + selectedMote.getID());
      if (selectedMote.getInterfaces().getRadio() == null) {
        continue;
      }
      
/**/  System.out.println("UDGMBS.selectedMoteID: " + selectedMote.getID());
      
      RadioConnection[] conns = radioMedium.getActiveConnections();
/**/  System.out.println("paintBeforeMotes.conns: " + conns);

      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
      
      if (selectedRadio.isBackscatterTag()) {
/**/    System.out.println("selectedTAGMoteID: " + selectedMote.getID());
        /* Search among the active connections only for the last one that was created by 
         * a carrier generator and paint the Tx and Int ranges of the selectedRadio (tag)
         * that is listening to its carrier. */
        for(int i=conns.length; i>0; i--) {
/**/      System.out.println("lenght of conns: " + conns.length);
/**/      System.out.println("i: " + i);
          RadioConnection lastConnFromCarrier = conns[i-1];
          if(lastConnFromCarrier.getSource().isGeneratingCarrier()) {
            if (lastConnFromCarrier.isDestination(selectedRadio)) {
              
              int carrierChannel = lastConnFromCarrier.getSource().getChannel();
              int tagTxChannel = carrierChannel+2;
/**/          System.out.println("tagTxChannel: " + tagTxChannel);

              /* Paint the Tx and Int range of the tag */
              paintTxAndIxRanges(g, selectedRadio, tagTxChannel);
            }
            break;
          }
        }
        showProbability(selectedMotes, g);
      } else if (selectedRadio.isGeneratingCarrier()) {
        super.paintBeforeMotes(g);
/**/    System.out.println();
/**/    System.out.println();
        
        /* When the selected radio is a carrier generator search every connection
         * that was created by it and paint the Tx and Int ranges of every tag that
         * is currently listening to its carrier */
        for (RadioConnection conn: conns) {
          if (conn.getSource() == selectedRadio) {
            if(conn.getSource().isGeneratingCarrier()) {
/**/          System.out.println("selectedCarrierGenID: " + selectedMote.getID());
              for (Radio r : conn.getAllDestinations()) {
                if (conn.getDestinationDelay(r) == 0) {
/**/              System.out.println("tx range of tag " + r.getMote().getID() + " is drawn because of carrier " + conn.getSource().getMote().getID());
                  int carrierChannel = conn.getSource().getChannel();
                  int tagTxChannel = carrierChannel+2;
/**/              System.out.println("tagTxChannel: " + tagTxChannel);

                  /* Paint the Tx and Int ranges of each tag */
                  paintTxAndIxRanges(g, r, tagTxChannel);
                }
              }
            }
          }
        }
        showProbability(selectedMotes, g);
      } else {
        /* When the selected radio is a CC2420 refer to the parent method */
        super.paintBeforeMotes(g);
/**/    System.out.println();
      }
    }
/**/System.out.println("UDGMBS.paintBeforeMotesSTOP");


  } /* paintBeforeMotes */
  
  @Override
  public void paintAfterMotes(Graphics g) {
/**/System.out.println("UDGMBS.paintAfterMotes");

    Set<Mote> selectedMotes = visualizer.getSelectedMotes();
    if (simulation == null || selectedMotes == null) {
    /**/System.out.println("simulation || selectedMotes = null");      
      return;
    }
    
/**/System.out.println("UDGMBS.paintAfterMotesSTART");
    
/**/System.out.println("selectedMotes: " + selectedMotes);
    
    for (Mote selectedMote : selectedMotes) { 
/**/  System.out.println("selectedMote: " + selectedMote.getID());
      if (selectedMote.getInterfaces().getRadio() == null) {
        continue;
      }
      
/**/  System.out.println("UDGMBS.selectedMoteID: " + selectedMote.getID());
      
      RadioConnection[] conns = radioMedium.getActiveConnections();
      
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
    
      if (selectedRadio.isBackscatterTag()) {
/**/    System.out.println("selectedTAGMoteID: " + selectedMote.getID());
        /* Search among the active connections only for the last one that was created by 
         * a carrier generator and paint its internal part with the same color as the 
         * color of the TX range of the selectedRadio (tag) that is listening to its
         * carrier. */
        for(int i=conns.length; i>0; i--) {
/**/      System.out.println("lenght of conns: " + conns.length);
/**/      System.out.println("i: " + i);
          /* Last connection among the active ones that was created by a carrier 
           * generator. */
          RadioConnection lastConnFromCarrier = conns[i-1];
          if(lastConnFromCarrier.getSource().isGeneratingCarrier()) {
            if (lastConnFromCarrier.isDestination(selectedRadio)) {
              
              int carrierChannel = lastConnFromCarrier.getSource().getChannel();
              
              /* Gives the connection responsible for the tag's maximum output power, in case the carrier 
               * generators of more than one connections have the same channel */
              RadioConnection connFromMaxPower = selectedRadio.getConnectionFromMaxOutputPower(carrierChannel);
              
/**/          System.out.println("carrierSource: " +  connFromMaxPower.getSource().getMote().getID() + " is responsible for the max output power");

              /* Paint the inner part of the carrier generator (connFromMaxPower.getSource()), whose carrier 
               * the selectedMote is listening to, with the same color as the color of the TX range of the 
               * selectedMote (tag). */
              paintCarrierColor(selectedMotes, selectedMote, connFromMaxPower.getSource(), g);

              break;
            }
          }
        }
      } else if (selectedRadio.isGeneratingCarrier()) {
        /* Paint the inner part of the carrier generator (selectedRadio) that is currently selected  */
        paintCarrierColor(selectedMotes, selectedRadio.getMote(), g);
      } else {
        super.paintAfterMotes(g);
      }
    }
/**/System.out.println("UDGMBS.paintAfterMotesSTOP");


  } /* paintAfterMotes */

//  public static class RangeMenuAction implements SimulationMenuAction {
//
//    @Override
//    public boolean isEnabled(Visualizer visualizer, Simulation simulation) {
//      return true;
//    }
//
//    @Override
//    public String getDescription(Visualizer visualizer, Simulation simulation) {
//      return "Change transmission ranges for tag";
//    }
//
//    @Override
//    public void doAction(Visualizer visualizer, Simulation simulation) {
//      VisualizerSkin[] skins = visualizer.getCurrentSkins();
//      for (VisualizerSkin skin : skins) {
//        if (skin instanceof UDGMBSVisualizerSkin) {
//          UDGMBSVisualizerSkin vskin = ((UDGMBSVisualizerSkin) skin);
//          vskin.rangeTX.setVisible(true);
//          vskin.rangeINT.setVisible(true);
//          vskin.updateRatioRangeFrame();
//        }
//      }
//    }
//  };

  public static class SuccessRatioMenuAction implements SimulationMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Simulation simulation) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Simulation simulation) {
      return "Change TX/RX success ratio for tag";
    }

    @Override
    public void doAction(Visualizer visualizer, Simulation simulation) {
      VisualizerSkin[] skins = visualizer.getCurrentSkins();
      for (VisualizerSkin skin : skins) {
        if (skin instanceof UDGMBSVisualizerSkin) {
          UDGMBSVisualizerSkin vskin = ((UDGMBSVisualizerSkin) skin);
          vskin.ratioTX.setVisible(true);
          vskin.ratioRX.setVisible(true);
          vskin.updateRatioRangeFrame();
        }
      }
    }
  };
  
  private void updateRatioRangeFrame() {
/**/System.out.println("UDGMBS.updateRatioRangeFrame");         
      
    if (rrFrame.getDesktopPane() == null) {
      visualizer.getDesktopPane().add(rrFrame);
    }
    rrFrame.pack();
    /* Place frame at the upper right corner of the visualizer canvas */
    Point visCanvasPos = SwingUtilities.convertPoint(
            visualizer.getCurrentCanvas(),
            visualizer.getCurrentCanvas().getLocation(),
            visualizer.getDesktopPane());
    rrFrame.setLocation(
            visCanvasPos.x + visualizer.getCurrentCanvas().getWidth() - rrFrame.getWidth(),
            visCanvasPos.y);
    /* Try to place on top with focus */
    rrFrame.setLayer(JLayeredPane.MODAL_LAYER);
    rrFrame.setVisible(true);
    rrFrame.moveToFront();
    try {
      rrFrame.setSelected(true);
    }
    catch (PropertyVetoException ex) {
      logger.warn("Failed getting focus");
    }
  }

  
  /**
   * Paint the Tx and Int ranges of the given radio which change dynamically based on 
   * the output power indexed by the given tag's tx channel.
   *   
   * @param g
   * @param radio
   * @param channel
   */
  private void paintTxAndIxRanges(Graphics g, Radio radio, int tagTxChanel) {
    
    Area intRangeArea = new Area();
    Area intRangeMaxArea = new Area();
    Area trxRangeArea = new Area();
    Area trxRangeMaxArea = new Area();
    
    /* Paint transmission and interference range for selected radio */
    Position radioPos = radio.getPosition();
    
    Point pixelCoord = visualizer.transformPositionToPixel(radioPos);
    int x = pixelCoord.x;
    int y = pixelCoord.y;
    
    double tagCurrentOutputPowerIndicator = radio.getTagCurrentOutputPower(tagTxChanel);
    
    double tagTransmissionRange = (Math.pow(10, (radioMedium.GT + radioMedium.GR + tagCurrentOutputPowerIndicator - radioMedium.STH 
                                         + 20*Math.log10(radioMedium.WAVELENGTH / (4*Math.PI))) / 20));
    
    double tagInterferenceRange = (Math.pow(10, (radioMedium.GT + radioMedium.GR + tagCurrentOutputPowerIndicator - (radioMedium.STH - 3)
                                         + 20*Math.log10(radioMedium.WAVELENGTH / (4*Math.PI))) / 20));
  
    Point translatedZero = visualizer.transformPositionToPixel(0.0, 0.0, 0.0);
    Point translatedInterference
              = visualizer.transformPositionToPixel(tagInterferenceRange, tagInterferenceRange, 0.0);
    Point translatedTransmission
             = visualizer.transformPositionToPixel(tagTransmissionRange, tagTransmissionRange, 0.0);
      
  
    translatedInterference.x = Math.abs(translatedInterference.x - translatedZero.x);
    translatedInterference.y = Math.abs(translatedInterference.y - translatedZero.y);
    translatedTransmission.x = Math.abs(translatedTransmission.x - translatedZero.x);
    translatedTransmission.y = Math.abs(translatedTransmission.y - translatedZero.y);
  
    /* Interference range */
    intRangeArea.add(new Area(new Ellipse2D.Double(
            x - translatedInterference.x,
            y - translatedInterference.y,
            2 * translatedInterference.x,
            2 * translatedInterference.y)));
  
    /* Transmission range */
    trxRangeArea.add(new Area(new Ellipse2D.Double(
            x - translatedTransmission.x,
            y - translatedTransmission.y,
            2 * translatedTransmission.x,
            2 * translatedTransmission.y)));
    
    /* Interference range (MAX) */
    intRangeMaxArea.add(new Area(new Ellipse2D.Double(
            x - translatedInterference.x,
            y - translatedInterference.y,
            2 * translatedInterference.x,
            2 * translatedInterference.y)));
    
    /* Transmission range (MAX) */
    trxRangeMaxArea.add(new Area(new Ellipse2D.Double(
            x - translatedTransmission.x,
            y - translatedTransmission.y,
            2 * translatedTransmission.x,
            2 * translatedTransmission.y)));
    
    /* 
     * Paint the TX range of each tag with a different color for every different tx channel,
     * which is derived from the corresponding carrier generator whose carrier the selectedMote
     * (tag) is listening to. Since, for carrier generators with the same channel the corresponding 
     * TX range of the tag is the one derived from the largest output power (from the carrier gen. 
     * that is closer to the tag), carrier generators with the same channel will produce to the tag
     * that is listening to the its carrier, TX ranges with the same color. 
     */
    int txColor = 0;
    
    /* Use only one of the sixteen 802.15.4 channels */
    if (carrierColor.get(tagTxChanel) !=null) {
/**/  System.out.println("indexChannel: " + tagTxChanel);              
      txColor = carrierColor.get(tagTxChanel);
/**/  System.out.println("txColor: " + txColor);
    } else {
      int defaultTXColor = COLOR_TX.getRGB();
      txColor = defaultTXColor;
    }
/**/System.out.println("2.carrierColor: " + carrierColor);    

  
    Graphics2D g2d = (Graphics2D) g;
/**/System.out.println("UDGMBS.Graphics2D");
    
    g2d.setColor(COLOR_INT);
    g2d.fill(intRangeArea); // fill the circle with color
    g.setColor(Color.GRAY);
    g2d.draw(intRangeMaxArea); 
      
    g.setColor(new Color(txColor, true));
    g2d.fill(trxRangeArea);
    g.setColor(Color.GRAY);
    g2d.draw(trxRangeMaxArea); // draw the circle
    
    
  } /* paintTxAndIxRanges */
  
  
  /**
   * Show the probability beneath each node of the Set selectedMotes for
   * the given graphics g. 
   * 
   * @param selectedMotes
   * @param g
   */
  private void showProbability(Set<Mote> selectedMotes, Graphics g) {
    FontMetrics fm = g.getFontMetrics();
    g.setColor(Color.BLACK);
    
    /* Print transmission success probabilities only if single mote is selected */
    if (selectedMotes.size() == 1) {
/**/  System.out.println("UDGMBS.selectedMotes.size(): " + selectedMotes.size());      
      
      Mote selectedMote = selectedMotes.toArray(new Mote[0])[0];
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
      for (Mote m : simulation.getMotes()) {
        if (m == selectedMote) {
          continue;
        }
/**/    System.out.println("UDGMBS.m: " + m.getID());      
     
        double prob
                = ((UDGMBS) simulation.getRadioMedium()).getSuccessProbability(selectedRadio, m.getInterfaces().getRadio());
       
/**/    System.out.println("UDGMBS.PROB: " + prob);
        
        if (prob == 0.0d) {
          continue;
        }
        String msg = (((int) (1000 * prob)) / 10.0) + "%";
        Position pos = m.getInterfaces().getPosition();
        Point pixel = visualizer.transformPositionToPixel(pos);
        int msgWidth = fm.stringWidth(msg);
        g.drawString(msg, pixel.x - msgWidth / 2, pixel.y + 2 * Visualizer.MOTE_RADIUS + 3);
      }
    }
    

  } /* showProbability */
  

  /**
   * From the given set of motes paint the carrier generator (selectedMote), which is currently
   * selected.
   * 
   * @param selectedMotes
   * @param selectedMote
   * @param g
   */
  private void paintCarrierColor(Set<Mote> selectedMotes, Mote selectedMote, Graphics g) {
    /* Get the position of the carrier generator */
    Position carrierPos = selectedMote.getInterfaces().getPosition();
    
    Point pixelCoordin = visualizer.transformPositionToPixel(carrierPos);
    int xi = pixelCoordin.x;
    int yi = pixelCoordin.y;
    
    Radio selectedRadio  = selectedMote.getInterfaces().getRadio();
    int txColor = carrierColor.get(selectedRadio.getChannel()+2);
    
    if (selectedMotes.contains(selectedMote)) {
/**/            System.out.println("UDGMBS.getSelectedMotes().contains(mote)");        
      /* If mote is selected, highlight with red circle
       and semitransparent gray overlay */
      g.setColor(new Color(51, 102, 255));
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                   2 * Visualizer.MOTE_RADIUS);
      g.drawOval(xi - Visualizer.MOTE_RADIUS - 1, yi - Visualizer.MOTE_RADIUS - 1, 2 * Visualizer.MOTE_RADIUS + 2,
                   2 * Visualizer.MOTE_RADIUS + 2);

      g.setColor(new Color(txColor, true));
      g.fillOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                 2 * Visualizer.MOTE_RADIUS);
      
    } else {
      g.setColor(Color.BLACK);
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                 2 * Visualizer.MOTE_RADIUS);
    }
    
    
  } /* paintCarrierColor */

  
  /**
   * From the given set of motes paint the carrier generator (radio), whose carrier the selectedMote
   * (tag) is listening to.
   * 
   * @param selectedMotes
   * @param selectedMote
   * @param radio
   * @param g
   */
  private void paintCarrierColor(Set<Mote> selectedMotes, Mote selectedMote, Radio radio, Graphics g) {
    /* Get the position of the carrier generator */
    Position carrierPos = radio.getPosition();
    
    Point pixelCoordin = visualizer.transformPositionToPixel(carrierPos);
    int xi = pixelCoordin.x;
    int yi = pixelCoordin.y;
    
    int txColor = carrierColor.get(radio.getChannel()+2);
    
    if (selectedMotes.contains(selectedMote)) {
/**/            System.out.println("UDGMBS.getSelectedMotes().contains(mote)");        
      /* If mote is selected, highlight with red circle
       and semitransparent gray overlay */
      g.setColor(new Color(51, 102, 255));
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                   2 * Visualizer.MOTE_RADIUS);
      g.drawOval(xi - Visualizer.MOTE_RADIUS - 1, yi - Visualizer.MOTE_RADIUS - 1, 2 * Visualizer.MOTE_RADIUS + 2,
                   2 * Visualizer.MOTE_RADIUS + 2);

      g.setColor(new Color(txColor, true));
      g.fillOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                 2 * Visualizer.MOTE_RADIUS);
      
    } else {
      g.setColor(Color.BLACK);
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS,
                 2 * Visualizer.MOTE_RADIUS);
    }
    
    
  } /* paintCarrierColor */
  
  
  /**
   * Set a different color for each one of the 16 available 802.15.4 channels (ch.11-26).
   */
  private void setColorsForTagTXChannel() {
    int tagTXChannel = 11;
    int color = Color.HSBtoRGB((float)(15*15)/(float)360, (float)1.0, (float)1.0);
    carrierColor.put(tagTXChannel, color);
      
    for(int i=17; i<23; i++) {
      tagTXChannel++;
      color = Color.HSBtoRGB((float)(i*15)/(float)360, (float)1.0, (float)1.0);
      carrierColor.put(tagTXChannel, color);
    }
    
    for(int i=0; i<7; i++) {
      tagTXChannel++;
      color = Color.HSBtoRGB((float)(i*15)/(float)360, (float)1.0, (float)1.0);
      carrierColor.put(tagTXChannel, color);
    }
    
    for(int i=13; i<15; i++) {
      tagTXChannel++;
      color = Color.HSBtoRGB((float)(i*15)/(float)360, (float)1.0, (float)1.0);
      carrierColor.put(tagTXChannel, color);
    }
/**/System.out.println("1.carrierColor: " + carrierColor);    
  } /* setColorsForTagTXChannel */
  
  
  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }
    
  
}
