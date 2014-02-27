/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.csvreplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openhab.core.events.AbstractEventSubscriber;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.openhab.model.item.binding.BindingConfigReader;

/**
 * <p>This class implements a binding of a CSV file to openHAB.
 * The binding configurations are provided by the {@link GenericItemProvider}.</p>
 * 
 * @author PHAM Manh Linh
 *
 */
public class CSVReplayBinding extends AbstractEventSubscriber implements BindingConfigReader {

	/** Store set of csv files */
	private Map<String, CSVReplayFile> dbFiles = new HashMap<String, CSVReplayFile>();

	/** stores information about the which items are associated to which file. The map has this content structure: itemname -> file */ 
	private Map<String, String> itemMap = new HashMap<String, String>();
	
	/** stores information about the context of items. The map has this content structure: context -> Set of itemNames */ 
	private Map<String, Set<String>> contextMap = new HashMap<String, Set<String>>();

	private EventPublisher eventPublisher = null;
	
	public void setEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
		
		for(CSVReplayFile csvFile : dbFiles.values()) {
			csvFile.setEventPublisher(eventPublisher);
		}
	}
	
	public void unsetEventPublisher(EventPublisher eventPublisher) {
		this.eventPublisher = null;

		for(CSVReplayFile csvFile : dbFiles.values()) {
			csvFile.setEventPublisher(null);
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void receiveCommand(String itemName, Command command) {
		if(itemMap.keySet().contains(itemName)) {
			CSVReplayFile dbFile = dbFiles.get(itemMap.get(itemName));
			if(command instanceof OnOffType) {
				if (OnOffType.ON.equals(command)) {
					dbFile.start();
				}
				else if (OnOffType.OFF.equals(command)) {
					dbFile.close();
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void receiveUpdate(String itemName, State newStatus) {
		// ignore any updates
	}

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "csvReplay";
	}

	/**
	 * {@inheritDoc}
	 */
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof SwitchItem)) {
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only SwitchItems are allowed - please check your *.items configuration");
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void processBindingConfiguration(String context, Item item, String bindingConfig) throws BindingConfigParseException {
		String paramsSplitBy = ":";
		String[] params = bindingConfig.split(paramsSplitBy);
		String filePath = "";
		if (params.length > 0) filePath = params[0];
		String stringItem = "";
		if (params.length > 1)  stringItem = params[1];
		int sleepTime = 500; // default sleep time in milliseconds
		if (params.length > 2) sleepTime = Integer.parseInt(params[2]);
		CSVReplayFile dbFile = dbFiles.get(filePath);
		if (dbFile == null) {
			dbFile = new CSVReplayFile(filePath);
			dbFile.setEventPublisher(eventPublisher);
			try {
				dbFile.initialize();
			} catch (InitializationException e) {
				throw new BindingConfigParseException(
						"Could not open file " + filePath + ": "
								+ e.getMessage());
			} catch (Throwable e) {
				throw new BindingConfigParseException(
						"Could not open file " + filePath + ": "
								+ e.getMessage());
			}
			itemMap.put(item.getName(), filePath);
			dbFiles.put(filePath, dbFile);
		}
		if (item instanceof SwitchItem) {
			if (dbFile.getSwitchItemName() == null) {
				dbFile.setSwitchItemName(item.getName());
				dbFile.setStringItemName(stringItem);
				dbFile.setSleepTime(sleepTime);
			} else {
				throw new BindingConfigParseException(
						"There is already another SwitchItem assigned to the file "
								+ filePath);
			}
		}
		Set<String> itemNames = contextMap.get(context);
		if (itemNames == null) {
			itemNames = new HashSet<String>();
			contextMap.put(context, itemNames);
		}
		itemNames.add(item.getName());
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeConfigurations(String context) {
		Set<String> itemNames = contextMap.get(context);
		if(itemNames!=null) {
			for(String itemName : itemNames) {
				// we remove all information in the files
				CSVReplayFile dbFile = dbFiles.get(itemMap.get(itemName));
				itemMap.remove(itemName);
				if(dbFile==null) {
					continue;
				}
				if(itemName.equals(dbFile.getSwitchItemName())) {
					dbFile.setSwitchItemName(null);
				}
				// if there is no binding left, dispose this file
				if(dbFile.getSwitchItemName()==null) {
					dbFile.close();
					dbFiles.remove(dbFile.getFilePath());
				}
			}
			contextMap.remove(context);
		}
	}

}
