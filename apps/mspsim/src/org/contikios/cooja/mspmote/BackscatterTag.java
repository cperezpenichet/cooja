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

package org.contikios.cooja.mspmote;

import java.io.File;

import org.apache.log4j.Logger;

import org.contikios.cooja.Simulation;
import org.contikios.cooja.mspmote.interfaces.CoojaM25P80;
import org.contikios.cooja.mspmote.interfaces.SkyCoffeeFilesystem;
import se.sics.mspsim.platform.sky.BackscatterTagNode;
import se.sics.mspsim.chip.TagModule;
import se.sics.mspsim.chip.BackscatterTagRadio;

/**
 * @author Fredrik Osterlind
 */
public class BackscatterTag extends SkyMote {
  private static Logger logger = Logger.getLogger(BackscatterTag.class);

  public BackscatterTagNode backscatterTagNode = null;
  public BackscatterTagRadio tagModule = null;

  public BackscatterTag(MspMoteType moteType, Simulation sim) {
    super(moteType, sim);
/**/System.out.println("BackscatterTag");
    //this.tagModule = new BackscatterTagRadio();
/**///System.out.println("tagModule.hashCode " + tagModule.hashCode());

  }

  public BackscatterTagRadio getTag() {
      return tagModule;
  }
  
  protected boolean initEmulator(File fileELF) {
    try {
/**/  System.out.println("BackscatterTag.initEmulator");
      backscatterTagNode = new BackscatterTagNode();
      registry = backscatterTagNode.getRegistry();
      backscatterTagNode.setFlash(new CoojaM25P80(backscatterTagNode.getCPU()));
      
      prepareMote(fileELF, backscatterTagNode);
    } catch (Exception e) {
      logger.fatal("Error when creating Backscatter Tag: ", e);
      return false;
    }
    return true;
  }

  @Override
  public void idUpdated(int newID) {
      backscatterTagNode.setNodeID(newID);

    /* Statically configured MAC addresses */
    /*configureWithMacAddressesTxt(newID);*/
  }
  
  public String toString() {
      return "Tag " + getID();
    }

} /* BackscatterTag */
