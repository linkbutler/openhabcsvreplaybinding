/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.csvreplay;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a csv file that is linked to exactly one Switch item.
 * 
 * @author PHAM Manh Linh
 *
 */
public class CSVReplayFile {

	private static final Logger logger = LoggerFactory.getLogger(CSVReplayFile.class);

	private String filePath;
	private String stringItemName;
	private String switchItemName;
	private int sleepTime;
	static int POS_ID = 0;
    static int POS_TIMESTAMP = 1;
    static int POS_VALUE = 2;
	static int POS_PROPERTY = 3; // 0 for work or 1 for load [boolean]
    static int POS_PLUGID = 4;
    static int POS_HOUSEHOLDID = 5;
    static int POS_HOUSEID = 6;
    
    long lineAtStop = 0;
    long relativeStartTime = 0;
    int accRatio = 1;
    BufferedReader br = null;

	private EventPublisher eventPublisher;

	public CSVReplayFile(String filePath) {
		this.filePath = filePath;
	}

    void setFilePath(String filePath) {
             this.filePath = filePath;
    }

    void setRelativeStartTime(long startTime) {
             this.relativeStartTime = startTime;
    }

    void setTimeAcceleration(int accRatio) {
             this.accRatio = accRatio;
    }
	
	public void setEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	public void unsetEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = null;
	}

	public String getStringItemName() {
		return stringItemName;
	}

	public void setStringItemName(String stringItemName) {
		this.stringItemName = stringItemName;
	}

	public String getSwitchItemName() {
		return switchItemName;
	}

	public void setSwitchItemName(String switchItemName) {
		this.switchItemName = switchItemName;
	}
	
	public void setSleepTime(int sleepTime) {
		this.sleepTime = sleepTime;
	}

	public String getFilePath() {
		return filePath;
	}
	
	public long getLineAtStop() {
		return lineAtStop;
	}

	/**
	 * Initialize this file and open the the file
	 * 
	 * @throws InitializationException if port can not be opened
	 */
	public void initialize() throws InitializationException {
		try {
            br = new BufferedReader(new FileReader(this.filePath));
		} catch (FileNotFoundException e) {
			throw new InitializationException(e);
		}
	}
	
	protected void start() {
		Thread thread1=null;
		this.readFromFile(this.filePath, thread1);            
	}

	public void readFromFile(final String f, Thread thread) {
	    Runnable readRun = new Runnable() {
	      public void run() {
	    	  String line = "";
	          String csvSplitBy = ",";

	          long firstTimeStampInFile = 0;
	          long firstTimeStampInSystem = System.currentTimeMillis();

	          boolean end = false;
	          try {
	                  br = new BufferedReader(new FileReader(f));
	                  long currentStartLine = lineAtStop+1;
	                  System.out.println(stringItemName + " starts reading from line " + currentStartLine + ".");
	                  for (int i = 0; i < currentStartLine; i++) { br.readLine(); }	//skip already read lines
	                  while (((line = br.readLine()) != null) && !end) {
	                  	String[] data = line.split(csvSplitBy);
	                  	long currentTime = System.currentTimeMillis();
	                      long timeStamp = Long.parseLong(data[POS_TIMESTAMP]);
	                  	
	                          if (firstTimeStampInFile == 0)
	                                  firstTimeStampInFile = timeStamp;

	                          long diff = (timeStamp - firstTimeStampInFile)/accRatio
	                                          - (currentTime - firstTimeStampInSystem);
	                          while (diff > 0 && !end) {
	                                  try {
	                                          Thread.sleep(diff);
	                                  } catch (InterruptedException e) {
	                                          end = true;
	                                  }
	                                  currentTime = System.currentTimeMillis();
	                                  diff = (timeStamp - firstTimeStampInFile)/accRatio
	                                                  - (currentTime - firstTimeStampInSystem);
	                          }
	                          lineAtStop ++; 
	                          try {
                                  Thread.sleep(sleepTime);
	                          } catch (InterruptedException e) {
                                  end = true;
	                          }
	                          // send data to the bus
	          				logger.debug("Received message '{}' on the file {}", new String[] { line, f });
	          				if (eventPublisher != null && switchItemName != null && stringItemName != null) {
	          					eventPublisher.postUpdate(stringItemName, new StringType(line));
	          				}
	                  }
	                  eventPublisher.postCommand(switchItemName, OnOffType.OFF);
	                  System.out.println(stringItemName + " stoped & closed the file!");
	                  lineAtStop = 0;
	          } catch (IOException e) {
	  			logger.debug("Error reading the file!");
	          } finally {
	                  if (br != null) {
	                          try {
	                                  br.close();
	                          } catch (IOException e) {
	              				logger.debug("Unexpected error while reading the file!");
	                          }
	                          br = null;
	                  }
	          }  
	      }
	    };
	    thread = new Thread(readRun);
	    thread.start();
	}

	/**
	 * Close this csv file
	 */
	public void close() {
		if (br != null) {
            try {  
                    br.close();
                    System.out.println(stringItemName + " paused at line " + lineAtStop + ".");
            } catch (IOException e) {
            	logger.debug("Unexpected error while pause at " + lineAtStop + " & closing the file!");
            }
		} else {
			try {
                br.close();
                System.out.println(stringItemName + " stoped & closed the file!");
			} catch (IOException e) {
        	logger.debug("Unexpected error while stop & closing the file!");
        }
		}
	}
}
