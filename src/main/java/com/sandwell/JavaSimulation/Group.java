/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2002-2011 Ausenco Engineering Canada Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.sandwell.JavaSimulation;

import java.util.ArrayList;

import com.jaamsim.input.InputAgent;
import com.jaamsim.input.InputAgent.KeywordIndex;
import com.jaamsim.input.Keyword;

/**
 * Group class - for storing a list of objects
 *
 * For input of the form <object> <keyword> <value>:
 * If the group appears as the object in a line of input, then the keyword and value applies to each member of the group.
 * If the group appears as the value in a line of input, then the list of objects is used as the value.
 */
public class Group extends Entity {
	@Keyword(description = "If TRUE show the members of the group as a seperate table in the output " +
	                "reports, including an entry for \"Total\"",
	         example = "Group1 Reportable { TRUE }")
	private final BooleanInput reportable;

	private Class<?> type;
	private final ArrayList<KeywordIndex> groupKeywordValues;

	private final ArrayList<Entity> list; // list of objects in group

	{
		addEditableKeyword( "List",       "",   "", false, "Key Inputs" );
		addEditableKeyword( "AppendList", "",   "", true,  "Key Inputs" );
		addEditableKeyword( "GroupType",  "",   "", false, "Key Inputs" );

		reportable = new BooleanInput("Reportable", "Key Inputs", true);
		this.addInput(reportable);
	}

	public Group() {
		list = new ArrayList<Entity>();
		type = null;
		groupKeywordValues = new ArrayList<KeywordIndex>();
	}

	/**
	 * Processes the input data corresponding to the specified keyword. If syntaxOnly is true,
	 * checks input syntax only; otherwise, checks input syntax and process the input values.
	 */
	@Override
	public void readData_ForKeyword(StringVector data, String keyword)
	throws InputErrorException {


		try {
			if( "List".equalsIgnoreCase( keyword ) ) {
				ArrayList<Entity> temp = Input.parseEntityList(data, Entity.class, true);
				list.clear();
				list.addAll(temp);
				this.checkType();
				return;
			}
			if( "AppendList".equalsIgnoreCase( keyword ) ) {
				int originalListSize = list.size();
				ArrayList<Entity> temp = Input.parseEntityList(data, Entity.class, true);
				for (Entity each : temp) {
					if (!list.contains(each))
						list.add(each);
				}
				this.checkType();
				// set values of appended objects to the group values
				if ( type != null ) {
					for ( int i = originalListSize; i < list.size(); i ++ ) {
						Entity ent = list.get( i );
						for ( int j = 0; j < groupKeywordValues.size(); j++  ) {
							KeywordIndex kw = groupKeywordValues.get(j);
							InputAgent.apply(ent, kw);
						}
					}
				}

				return;
			}

			if( "GroupType".equalsIgnoreCase( keyword ) ) {
				Input.assertCount(data, 1);
				type = Input.parseEntityType(data.get(0));
				this.checkType();
				return;
			}
		}
		catch( Exception e ) {
			InputAgent.logError("Entity: %s Keyword: %s - %s", this.getName(), keyword, e.getMessage());
		}
	}

	public void saveGroupKeyword(KeywordIndex key) {
		ArrayList<String> toks = new ArrayList<String>(key.end - key.start + 2);
		toks.add(key.input.get(0));
		for (int i = key.start; i <= key.end; i++)
			toks.add(key.input.get(i));

		KeywordIndex saved = new KeywordIndex(toks, 1, toks.size() - 1, key.context);
		groupKeywordValues.add(saved);

		// If there can never be elements in the group, throw a warning
		if( type == null && list.size() == 0 ) {
			InputAgent.logWarning("The group %s has no elements to apply keyword: %s", this, key.keyword);
		}
	}

	private void checkType() {
		if (type == null)
			return;

		for (Entity each : this.getList()) {
			if (!type.isInstance(each))
				throw new InputErrorException("The Entity: %s is not of Type: %s", each, type.getSimpleName());
		}
	}

	// ******************************************************************************************
	// ACCESSING
	// ******************************************************************************************

	public ArrayList<Entity> getList() {
		return list;
	}

	public boolean isReportable() {
		return reportable.getValue();
	}
}
