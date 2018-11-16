/*
 * Copyright (c) 2018, Uppsala University.
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
import org.contikios.cooja.radiomediums.UDGMCA;

/**
 * Visualizer skin for configuring the Unit Disk Graph radio medium for backscatter communication (UDGMCA).
 *
 * Allows a user to change the collective TX/interference ranges, and the TX/RX
 * success ratio.
 *
 * To also see radio traffic, this skin can be combined with {@link
 * TrafficVisualizerSkin}.
 * 
 * This class is an extension of the UDGMVisualizerSkin class written by Fredrik Osterlind
 * and Enrico Joerns.
 *
 * @see TrafficVisualizerSkin
 * @see UDGMCA
 * @author George Daglaridis
 * @author Carlos Pérez Penichet 
 */
@ClassDescription("Radio environment (UDGMCA)")
@SupportedArguments(radioMediums = {UDGMCA.class})
public class UDGMCAVisualizerSkin extends UDGMVisualizerSkin {

  private static final Logger logger = Logger.getLogger(UDGMCAVisualizerSkin.class);

  private static final Color COLOR_TX = new Color(0, 255, 0, 100);
  private static final Color COLOR_INT = new Color(50, 50, 50, 100);

  private Simulation simulation = null;
  private Visualizer visualizer = null;
  private UDGMCA radioMedium = null;

  private JInternalFrame rrFrame;
  private Box backCOEF;
  private Box freqShift;


  private Hashtable<Integer, Integer> carrierColor = new Hashtable<Integer, Integer>();

  @Override
  public void setActive(Simulation simulation, Visualizer vis) {
    super.setActive(simulation, vis);

    if (!(simulation.getRadioMedium() instanceof UDGMCA)) {
      logger.fatal("Cannot activate UDGMCA skin for unknown radio medium: " + simulation.getRadioMedium());
      return;
    }
    this.simulation = simulation;
    this.visualizer = vis;
    this.radioMedium = (UDGMCA) simulation.getRadioMedium();

    /* Change the title of the visualizer */
    //getVisualizer().setTitle("Network - UDGMCA");

    SpinnerNumberModel backscatterCoefficientModel = new SpinnerNumberModel();
    backscatterCoefficientModel.setValue(new Double(radioMedium.BACKSCATTER_COEFFICIENT));
    backscatterCoefficientModel.setStepSize(new Double(0.1)); // 0.1%
    backscatterCoefficientModel.setMinimum(new Double(0.0));
//    backscatterCoefficientModel.setMaximum(new Double(1.0));

    SpinnerNumberModel frequencyShiftModel = new SpinnerNumberModel();
    frequencyShiftModel.setValue(new Integer(radioMedium.getFREQSHIFT()));
    frequencyShiftModel.setStepSize(new Integer(1));
    frequencyShiftModel.setMinimum(new Integer(0));


    JSpinner.NumberEditor editor;
    final JSpinner backscatterCoefficientSpinner = new JSpinner(backscatterCoefficientModel);
    editor = new JSpinner.NumberEditor(backscatterCoefficientSpinner, "0.0 dBm");
    backscatterCoefficientSpinner.setEditor(editor);
    final JSpinner frequencyShiftModelSpinner = new JSpinner(frequencyShiftModel);
    editor = new JSpinner.NumberEditor(frequencyShiftModelSpinner, "0 Ch");
    frequencyShiftModelSpinner.setEditor(editor);

    backscatterCoefficientSpinner.setToolTipText("Backscatter coefficient (dBm)");
    frequencyShiftModelSpinner.setToolTipText("Channel shift (Ch)");

    backscatterCoefficientSpinner.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        radioMedium.BACKSCATTER_COEFFICIENT = ((SpinnerNumberModel) backscatterCoefficientSpinner.getModel())
                .getNumber().doubleValue();
        visualizer.repaint();
      }
    });

    frequencyShiftModelSpinner.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        radioMedium.setFREQSHIFT(((SpinnerNumberModel) frequencyShiftModelSpinner.getModel())
                .getNumber().intValue());
        visualizer.repaint();
      }
    });

    /* Register menu actions */
    //visualizer.registerSimulationMenuAction(RangeMenuAction.class);
    visualizer.registerSimulationMenuAction(BackscatterCoefficientMenuAction.class);
    visualizer.registerSimulationMenuAction(FrequencyShiftMenuAction.class);

    /* UI components */
    JPanel main = new JPanel();
    main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
    main.setBorder(BorderFactory.createEmptyBorder(9, 9, 9, 9));

    backCOEF = Box.createHorizontalBox();
    backCOEF.add(new JLabel("Back COEF.:"));
    backCOEF.add(Box.createHorizontalStrut(5));
    backCOEF.add(backscatterCoefficientSpinner);
    freqShift = Box.createHorizontalBox();
    freqShift.add(new JLabel("Freq Shift.:"));
    freqShift.add(Box.createHorizontalStrut(5));
    freqShift.add(frequencyShiftModelSpinner);

    backCOEF.setVisible(false);
    freqShift.setVisible(false);

    main.add(backCOEF);
    main.add(freqShift);

    /*
     * Now we have a rrFrame for each one of the motes
     * either it is a cc2420 radio or a tag.
     */

    /* Change the title of the rrFrame of the parent */
    //getRRFrame().setTitle("UDGMCA");

    /* Set the rrFrame for the radio */
    rrFrame = new JInternalFrame("Backscatter Tag", false, true);
    rrFrame.setVisible(false);
    rrFrame.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    rrFrame.addInternalFrameListener(new InternalFrameAdapter() {
      @Override
      public void internalFrameClosing(InternalFrameEvent ife) {
        super.internalFrameClosed(ife);
        backCOEF.setVisible(false);
        freqShift.setVisible(false);
        rrFrame.setVisible(false);
      }
    });

    rrFrame.getContentPane().add(BorderLayout.CENTER, main);
    rrFrame.pack();

    /* Set predefined colors for each one of the
     * sixteen 802.15.4 channels. */
    setColorsForTagTXChannel();
  }

  @Override
  public void setInactive() {
    super.setInactive();

    if (simulation == null) {
      /* Skin was never activated */
      return;
    }

    /* Remove spinners etc */
    visualizer.getCurrentCanvas().remove(rrFrame);

    /* Unregister menu actions */
    visualizer.unregisterSimulationMenuAction(BackscatterCoefficientMenuAction.class);
    visualizer.unregisterSimulationMenuAction(FrequencyShiftMenuAction.class);
  }

  @Override
  public Color[] getColorOf(Mote mote) {
      return null;
  }

  @Override
  public void paintBeforeMotes(Graphics g) {

    Set<Mote> selectedMotes = visualizer.getSelectedMotes();
    if (simulation == null || selectedMotes == null) {
        return;
    }

    for (Mote selectedMote : selectedMotes) {
      if (selectedMote.getInterfaces().getRadio() == null) {
        continue;
      }

      RadioConnection[] conns = radioMedium.getActiveConnections();

      Radio selectedRadio = selectedMote.getInterfaces().getRadio();

      if (selectedRadio.isBackscatterTag()) {
        /* Search among the active connections only for the last one that was created by
         * a carrier generator and paint the Tx and Int ranges of the selectedRadio (tag)
         * that is listening to its carrier. */

        boolean paintRanges = false;
        for(int i=conns.length; i>0; i--) {
          RadioConnection lastConnFromCarrier = conns[i-1];
          if (lastConnFromCarrier.getSource().isGeneratingCarrier()) {
            if (lastConnFromCarrier.isDestination(selectedRadio)) {
              paintRanges = true;
            }
          } else {
            if (lastConnFromCarrier.isInterfered(selectedRadio)) {
              if (selectedRadio.isTransmitting()) {
                paintRanges = true;
              }
            }
          }

          if (paintRanges) {
            int tagTxChannel = lastConnFromCarrier.getSource().getChannel()+radioMedium.getFREQSHIFT();

            /* Paint the Tx and Int range of the tag */
            paintTxAndIxRanges(g, selectedRadio, tagTxChannel);
            showProbability(selectedMotes, g, tagTxChannel);
            break;
          }
        }
      } else if (selectedRadio.isGeneratingCarrier()) {
        super.paintBeforeMotes(g);

        /* When the selected radio is a carrier generator search every connection
         * that was created by it and paint the Tx and Int ranges of every tag that
         * is currently listening to its carrier */
        for (RadioConnection conn: conns) {
          if (conn.getSource() == selectedRadio) {
            for (Radio r : conn.getAllDestinations()) {
              if (conn.getDestinationDelay(r) == 0 ) {
                int tagTxChannel = conn.getSource().getChannel()+radioMedium.getFREQSHIFT();

                /* Paint the TX and INT range of each tag */
                paintTxAndIxRanges(g, r, tagTxChannel);
                showProbability(selectedMotes, g);
              }
            }
          }
        }
      } else {
        /* When the selected radio is a CC2420 refer to the parent method */
        super.paintBeforeMotes(g);

        for (RadioConnection conn: conns) {
          if (conn.getSource() == selectedRadio) {
            for (Radio r : conn.getInterferedNonDestinations()) {
              if (r.isBackscatterTag()) {//&& r.isTransmitting()) {
                int tagTxChannel = conn.getSource().getChannel()+radioMedium.getFREQSHIFT();

                /* Paint the TX and INT range of each tag */
                paintTxAndIxRanges(g, r, tagTxChannel);
                showProbability(selectedMotes, g);
              }
            }
          }
        }
      }
    }

  } /* paintBeforeMotes */

  @Override
  public void paintAfterMotes(Graphics g) {

    Set<Mote> selectedMotes = visualizer.getSelectedMotes();
    if (simulation == null || selectedMotes == null) {
      return;
    }

    for (Mote selectedMote : selectedMotes) {
      if (selectedMote.getInterfaces().getRadio() == null) {
        continue;
      }

      RadioConnection[] conns = radioMedium.getActiveConnections();

      Radio selectedRadio = selectedMote.getInterfaces().getRadio();

      if (selectedRadio.isBackscatterTag()) {
        /* Search among the active connections only for the last one that was created by
         * a carrier generator and paint its internal part with the same color as the
         * color of the TX range of the selectedRadio (tag) that is listening to its
         * carrier. */
        for(int i=conns.length; i>0; i--) {
          /* Last connection among the active ones that was created by a carrier
           * generator. */
          RadioConnection lastConnFromCarrier = conns[i-1];
          int tagTxChannel = lastConnFromCarrier.getSource().getChannel()+radioMedium.getFREQSHIFT();
          RadioConnection connFromMaxPower = null;
          if (lastConnFromCarrier.getSource().isGeneratingCarrier()) {
            if (lastConnFromCarrier.isDestination(selectedRadio)) {

              /* Gives the connection responsible for the tag's maximum output power, in case the carrier
               * generators of more than one connections have the same channel */
              connFromMaxPower = selectedRadio.getConnectionFromMaxOutputPower(tagTxChannel);
            }
          } else {
            if (lastConnFromCarrier.isInterfered(selectedRadio)) {
              if (selectedRadio.isTransmitting()) {
                /* Gives the connection responsible for the tag's maximum output power, in case the carrier
                 * generators of more than one connections have the same channel */
                connFromMaxPower = selectedRadio.getConnectionFromMaxOutputPower(tagTxChannel);
              }
            }
          }

          /* Paint the inner part of the carrier generator (connFromMaxPower.getSource()), whose carrier
           * the selectedMote is listening to, with the same color as the color of the TX range of the
           * selectedMote (tag). */
          if (connFromMaxPower != null) {
            paintCarrierColor(selectedMotes, selectedMote, connFromMaxPower.getSource(), g);
            break;
          }
        }
      } else if (selectedRadio.isGeneratingCarrier()) {
        for (RadioConnection conn: conns) {
          if (conn.getSource() == selectedRadio) {
            for (Radio r: conn.getAllDestinations()) {
              /* Paint the inner part of the carrier generator (selectedRadio) that is currently selected  */
              paintCarrierColor(selectedMotes, selectedRadio.getMote(), r, g);
            }
          }
        }
      } else {
        super.paintAfterMotes(g);
      }
    }

  } /* paintAfterMotes */

  public static class BackscatterCoefficientMenuAction implements SimulationMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Simulation simulation) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Simulation simulation) {
      return "Backscatter Coefficient for tag";
    }

    @Override
    public void doAction(Visualizer visualizer, Simulation simulation) {
      VisualizerSkin[] skins = visualizer.getCurrentSkins();
      for (VisualizerSkin skin : skins) {
        if (skin instanceof UDGMCAVisualizerSkin) {
          UDGMCAVisualizerSkin vskin = ((UDGMCAVisualizerSkin) skin);
          vskin.backCOEF.setVisible(true);
          vskin.updateRatioRangeFrame();
        }
      }
    }
  };

  public static class FrequencyShiftMenuAction implements SimulationMenuAction {

    @Override
    public boolean isEnabled(Visualizer visualizer, Simulation simulation) {
      return true;
    }

    @Override
    public String getDescription(Visualizer visualizer, Simulation simulation) {
      return "Frequency shift for tag";
    }

    @Override
    public void doAction(Visualizer visualizer, Simulation simulation) {
      VisualizerSkin[] skins = visualizer.getCurrentSkins();
      for (VisualizerSkin skin : skins) {
        if (skin instanceof UDGMCAVisualizerSkin) {
          UDGMCAVisualizerSkin vskin = ((UDGMCAVisualizerSkin) skin);
          vskin.freqShift.setVisible(true);
          vskin.updateRatioRangeFrame();
        }
      }
    }
  };

  private void updateRatioRangeFrame() {
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
   * Paint the TX and INT ranges of the given radio which change dynamically based on
   * the output power indexed by the given tag's tx channel.
   *
   * @param g
   * @param radio
   * @param channel
   */
  private void paintTxAndIxRanges(Graphics g, Radio radio, int tagTxChannel) {
    Set<Mote> selectedMotes = visualizer.getSelectedMotes();

    Area intRangeArea = new Area();
    Area intRangeMaxArea = new Area();
    Area trxRangeArea = new Area();
    Area trxRangeMaxArea = new Area();

    /* Paint transmission and interference range for selected radio */
    Position radioPos = radio.getPosition();

    Point pixelCoord = visualizer.transformPositionToPixel(radioPos);
    int x = pixelCoord.x;
    int y = pixelCoord.y;

    double tagCurrentOutputPowerIndicator = 0.0;

    for (Mote selectedMote : selectedMotes) {
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
      if (selectedRadio.isBackscatterTag()) {
        tagCurrentOutputPowerIndicator = radio.getTagCurrentOutputPowerMax(tagTxChannel);
      } else {
        /* Tag's output power given the specific active module (active transmitter or carrier generator) */
        /* In case two active modules operate*/
        tagCurrentOutputPowerIndicator = radio.getTagCurrentOutputPower(selectedRadio, tagTxChannel);
      }
    }

    double tagTransmissionRange = radioMedium.calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);
    double tagInterferenceRange = radioMedium.calculateTagInterferenceRange(tagCurrentOutputPowerIndicator);

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
    int defaultTXColor = COLOR_TX.getRGB();
    /* Use only one of the sixteen 802.15.4 channels */
    if (tagTxChannel < 11 || tagTxChannel > 26) {
      txColor = defaultTXColor;
    } else {
      if (carrierColor.get(tagTxChannel) != null) {
         txColor = carrierColor.get(tagTxChannel);
      } else {
        txColor = defaultTXColor;
      }
    }

    if (radio.getNumberOfConnectionsFromChannel(tagTxChannel) != 0) {

      Graphics2D g2d = (Graphics2D) g;

      if (!radio.isTagTXPowersEmpty()) {
        if (radio.isTXChannelFromActiveTransmitter(tagTxChannel) ||
                radio.getNumberOfConnectionsFromChannel(tagTxChannel) >= 2) {

          if (radio.isTransmitting()) {
            /*
             * Note: This if statement was added for consistency purposes. The interference
             *      range of a tag which accepts signals from both an active transmitter and a
             *      carrier generator at the same time has to be shown only if the tag transmits
             *      at that time. Remove it if you need a more clear view of this range whenever
             *      the carrier generator or the active transmitter is pressed.
             */

            g2d.setColor(Color.WHITE);
            g2d.fill(intRangeArea); // fill the circle with color
            g2d.setColor(COLOR_INT);
            g2d.fill(intRangeArea); // fill the circle with color
            g.setColor(Color.GRAY);
            g2d.draw(intRangeMaxArea);
          }
        } else {
          g2d.setColor(Color.WHITE);
          g2d.fill(intRangeArea); // fill the circle with color
          g2d.setColor(COLOR_INT);
          g2d.fill(intRangeArea); // fill the circle with color
          g.setColor(Color.GRAY);
          g2d.draw(intRangeMaxArea);

          g.setColor(new Color(txColor, true));
          g2d.fill(trxRangeArea);
          g.setColor(Color.GRAY);
          g2d.draw(trxRangeMaxArea); // draw the circle
        }
      }

    }

  } /* paintTxAndIxRanges */


  /**
   * Show the probability beneath each tag of the Set selectedMotes for the given
   * TX channel of the tag and the given graphics g.
   *
   * @param selectedMotes
   * @param g
   * @param channel
   */

  /* Alternative function for showing the probability of the nodes placed within
   * the transmission range of a tag. It is based on the calculation of the
   * tagCurrentOutputPowerIndicator, which, in turn, is based on the current max
   * output power of the tag, indexed by the appropriate backscatter channel.
   * According to the way the power was indexed, this method was mainly used
   * because the probability of an active radio was not shown. Therefore, it was
   * in the beginning but rejected afterwards, because it was not taking into account
   * the variable SUCCESS_RATIO_RX in the calculation of the local variable prob. */

  private void showProbability(Set<Mote> selectedMotes, Graphics g, int channel) {
    FontMetrics fm = g.getFontMetrics();
    g.setColor(Color.BLACK);

    /* Print transmission success probabilities only if single mote is selected */
    if (selectedMotes.size() == 1) {

      Mote selectedMote = selectedMotes.toArray(new Mote[0])[0];
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();

      for (Mote m : simulation.getMotes()) {
        if (m == selectedMote) {
          continue;
        }

        if (selectedRadio.isTXChannelFromActiveTransmitter(channel) ||
                selectedRadio.getNumberOfConnectionsFromChannel(channel) >= 2) {
          break;

        } else {

          double tagCurrentOutputPowerIndicator = selectedRadio.getTagCurrentOutputPowerMax(channel);
          double distanceMax = radioMedium.calculateTagTransmissionRange(tagCurrentOutputPowerIndicator);

          double prob = 0.0;

          double distance = selectedRadio.getPosition().getDistanceTo(m.getInterfaces().getPosition());
          if (distance <= distanceMax) {
            prob = 1.0;
          } else {
            prob = 0.0;
          }

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
    }

  } /* showProbability */

  /**
   * Show the probability beneath each active radio node of the Set selectedMotes for
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

      Mote selectedMote = selectedMotes.toArray(new Mote[0])[0];
      Radio selectedRadio = selectedMote.getInterfaces().getRadio();
      for (Mote m : simulation.getMotes()) {
        if (m == selectedMote) {
          continue;
        }

        double  prob = ((UDGMCA) simulation.getRadioMedium()).getSuccessProbability(selectedRadio,
                m.getInterfaces().getRadio());

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
   * From the given set of motes paint the carrier generator (radio), whose carrier the selectedMote
   * (tag) is listening to.
   *
   * @param selectedMotes
   * @param selectedMote
   * @param radio
   * @param g
   */
  private void paintCarrierColor(Set<Mote> selectedMotes, Mote selectedMote, Radio radio, Graphics g) {

    Radio selectedRadio  = selectedMote.getInterfaces().getRadio();

    Radio tag = null;
    Radio paintedRadio = null;

    if (selectedRadio.isBackscatterTag()) {
      tag = selectedRadio;
      paintedRadio = radio;
    } else {
      tag = radio;
      paintedRadio = selectedRadio;
    }

    /* Get the position of the carrier generator */
    Position activeRadioPos = paintedRadio.getPosition();

    Point pixelCoordin = visualizer.transformPositionToPixel(activeRadioPos);
    int xi = pixelCoordin.x;
    int yi = pixelCoordin.y;

    if (selectedMotes.contains(selectedMote)) {

     /* If mote is selected, highlight with red circle
       and semitransparent gray overlay */
      g.setColor(new Color(51, 102, 255));
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS);
      g.drawOval(xi - Visualizer.MOTE_RADIUS - 1, yi - Visualizer.MOTE_RADIUS - 1, 2 * Visualizer.MOTE_RADIUS + 2, 2 * Visualizer.MOTE_RADIUS + 2);
      
      int tagTxChannel = paintedRadio.getChannel()+radioMedium.getFREQSHIFT();
      
      if (tag.isTXChannelFromActiveTransmitter(tagTxChannel) ||
              tag.getNumberOfConnectionsFromChannel(tagTxChannel) >= 2) {

        g.setColor(new Color(128, 128, 128, 128));
        g.fillOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS);
      } else {
        if (tagTxChannel < 11 || tagTxChannel > 26) {
          g.setColor(Color.BLACK);
          g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS);
        } else {
          int txColor = carrierColor.get(tagTxChannel);
          g.setColor(new Color(txColor, true));
          g.fillOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS);
        }
      }
    } else {
      g.setColor(Color.BLACK);
      g.drawOval(xi - Visualizer.MOTE_RADIUS, yi - Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS, 2 * Visualizer.MOTE_RADIUS);
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

  } /* setColorsForTagTXChannel */

  @Override
  public Visualizer getVisualizer() {
    return visualizer;
  }

}
