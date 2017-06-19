/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
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

package org.contikios.cooja.mspmote.interfaces;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.*;
import org.contikios.cooja.interfaces.LED;
import org.contikios.cooja.mspmote.BackscatterTag;
import se.sics.mspsim.core.IOPort;
import se.sics.mspsim.core.IOUnit;
import se.sics.mspsim.core.PortListener;
import se.sics.mspsim.platform.sky.BackscatterTagNode;

/**
 * @author Fredrik Osterlind
 */
@ClassDescription("Tag LED")
public class TagLED extends SkyLED {
  private static Logger logger = Logger.getLogger(TagLED.class);

  private BackscatterTag tagMote;
  private boolean blueOn = false;
  private boolean greenOn = false;
  private boolean redOn = false;

  private static final Color DARK_BLUE = new Color(0, 0, 100);
  private static final Color DARK_GREEN = new Color(0, 100, 0);
  private static final Color DARK_RED = new Color(100, 0, 0);
  private static final Color BLUE = new Color(0, 0, 255);
  private static final Color GREEN = new Color(0, 255, 0);
  private static final Color RED = new Color(255, 0, 0);

  public TagLED(Mote mote) {
    super(mote);
/**/System.out.println("TagLED"); 
    tagMote = (BackscatterTag) mote;

    IOUnit unit = tagMote.getCPU().getIOUnit("Port 5");
    if (unit instanceof IOPort) {
      ((IOPort) unit).addPortListener(new PortListener() {
        public void portWrite(IOPort source, int data) {
          blueOn = (data & BackscatterTagNode.BLUE_LED) == 0;
          greenOn = (data & BackscatterTagNode.GREEN_LED) == 0;
          redOn = (data & BackscatterTagNode.RED_LED) == 0;
          setChanged();
          notifyObservers();
        }
      });
    }
  }


}
