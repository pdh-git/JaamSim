/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2013 Ausenco Engineering Canada Inc.
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
package com.jaamsim.CalculationObjects;

import com.jaamsim.input.Keyword;
import com.sandwell.JavaSimulation.EntityInput;

/**
 * The NotOperator implements the Boolean "Not" operation.
 * @author Harry King
 *
 */
public class NotOperator extends BooleanCalculation {

	@Keyword(description = "The BooleanCalculation entity that is the input to this operation.",
	         example = "OrOperator1 Entity { Bool1 }")
	private final EntityInput<BooleanCalculation> entity;
	{
	entity = new EntityInput<BooleanCalculation>( BooleanCalculation.class, "Entity", "Key Inputs", null);
		this.addInput( entity);
	}

	@Override
	public void update(double simTime) {

		// Set the present value
		this.setValue( ! entity.getValue().getNextSample(simTime) );
		return;
	}
}
