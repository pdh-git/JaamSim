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
package com.jaamsim.BasicObjects;

import java.util.ArrayList;
import java.util.Locale;

import com.jaamsim.Samples.SampleInput;
import com.jaamsim.events.ProcessTarget;
import com.jaamsim.input.InputAgent;
import com.jaamsim.input.Keyword;
import com.jaamsim.math.Vec3d;
import com.jaamsim.render.HasScreenPoints;
import com.jaamsim.units.DistanceUnit;
import com.jaamsim.units.TimeUnit;
import com.sandwell.JavaSimulation.ColourInput;
import com.sandwell.JavaSimulation.DoubleInput;
import com.sandwell.JavaSimulation.Input;
import com.sandwell.JavaSimulation.Vec3dListInput;
import com.sandwell.JavaSimulation3D.DisplayEntity;

/**
 * Moves one or more Entities along a path with a specified travel time. Entities can have different travel times, which
 * are represented as varying speeds.
 */
public class EntityDelay extends LinkedComponent implements HasScreenPoints {

	@Keyword(description = "The delay time for the path.\n" +
			"The input can be a constant value, a time series of values, or a probability distribution to be sampled.",
	         example = "Delay-1 Duration { 10.0 s }")
	private final SampleInput duration;

    @Keyword(description = "A list of { x, y, z } coordinates defining the line segments that" +
            "make up the path.  When two coordinates are given it is assumed that z = 0." ,
             example = "Delay-1  Points { { 6.7 2.2 m } { 4.9 2.2 m } { 4.9 3.4 m } }")
	private final Vec3dListInput pointsInput;

	@Keyword(description = "The width of the path in pixels.",
	         example = "Delay-1 Width { 1 }")
	private final DoubleInput widthInput;

	@Keyword(description = "The colour of the path.\n" +
			"The input can be a colour keyword or RGB value.",
	         example = "Delay-1 Color { red }")
	private final ColourInput colorInput;

	private final ArrayList<DisplayEntity> entityList;  // List of the entities being handled
	private final ArrayList<Double> startTimeList;  // List of times at which the entities started their delay
	private final ArrayList<Double> durationList;  // List of durations for the entities

	private double totalLength;  // Graphical length of the path
	private final ArrayList<Double> lengthList;  // Length of each segment of the path
	private final ArrayList<Double> cumLengthList;  // Total length to the end of each segment

	private Object screenPointLock = new Object();
	private HasScreenPoints.PointsInfo[] cachedPointInfo;

	{
		duration = new SampleInput( "Duration", "Key Inputs", null);
		duration.setUnitType(TimeUnit.class);
		this.addInput( duration);

		ArrayList<Vec3d> defPoints =  new ArrayList<Vec3d>();
		defPoints.add(new Vec3d(0.0d, 0.0d, 0.0d));
		defPoints.add(new Vec3d(1.0d, 0.0d, 0.0d));
		pointsInput = new Vec3dListInput("Points", "Key Inputs", defPoints);
		pointsInput.setValidCountRange( 2, Integer.MAX_VALUE );
		pointsInput.setUnitType(DistanceUnit.class);
		this.addInput(pointsInput);

		widthInput = new DoubleInput("Width", "Key Inputs", 1.0d);
		widthInput.setValidRange(1.0d, Double.POSITIVE_INFINITY);
		this.addInput(widthInput);

		colorInput = new ColourInput("Color", "Key Inputs", ColourInput.BLACK);
		this.addInput(colorInput);
		this.addSynonym(colorInput, "Colour");
	}

	public EntityDelay() {
		entityList = new ArrayList<DisplayEntity>();
		startTimeList = new ArrayList<Double>();
		durationList = new ArrayList<Double>();
		lengthList = new ArrayList<Double>();
		cumLengthList = new ArrayList<Double>();
	}

	@Override
	public void earlyInit() {
		super.earlyInit();

		entityList.clear();
		startTimeList.clear();
		durationList.clear();

	    // Initialize the segment length data
		lengthList.clear();
		cumLengthList.clear();
		totalLength = 0.0;
		for( int i = 1; i < pointsInput.getValue().size(); i++ ) {
			// Get length between points
			Vec3d vec = new Vec3d();
			vec.sub3( pointsInput.getValue().get(i), pointsInput.getValue().get(i-1));
			double length = vec.mag3();

			lengthList.add( length);
			totalLength += length;
			cumLengthList.add( totalLength);
		}
	}

	@Override
	public void addDisplayEntity( DisplayEntity ent ) {
		super.addDisplayEntity(ent);

		// Add the entity to the list of entities being delayed
		double simTime = this.getSimTime();
		double dur = duration.getValue().getNextSample(simTime);
		entityList.add( ent );
		startTimeList.add(simTime);
		durationList.add(dur);

		this.scheduleProcess(dur, 5, new RemoveDisplayEntityTarget(this, "removeDisplayEntity", ent));
	}

	private static class RemoveDisplayEntityTarget extends ProcessTarget {
		private final EntityDelay delay;
		private final String method;
		private final DisplayEntity ent;

		RemoveDisplayEntityTarget(EntityDelay d, String m, DisplayEntity e) {
			delay = d;
			method = m;
			ent = e;
		}

		@Override
		public void process() {
			delay.removeDisplayEntity(ent);
		}

		@Override
		public String getDescription() {
			return String.format( "%s.%s(%s)", delay.getInputName(), method, ent.getInputName() );
		}
	}

	public void removeDisplayEntity(DisplayEntity ent) {

		// Remove the entity from the lists
		int index = entityList.indexOf(ent);
		entityList.remove(index);
		startTimeList.remove(index);
		durationList.remove(index);

		// Send the entity to the next component
		this.sendToNextComponent(ent);
	}

	/**
	 * Return the position coordinates for a given distance along the path.
	 * @param dist = distance along the path.
	 * @return position coordinates
	 */
	private Vec3d getPositionForDistance( double dist) {

		// Find the present segment
		int seg = 0;
		for( int i = 0; i < cumLengthList.size(); i++) {
			if( dist <= cumLengthList.get(i)) {
				seg = i;
				break;
			}
		}

		// Interpolate between the start and end of the segment
		double frac = 0.0;
		if( seg == 0 ) {
			frac = dist / lengthList.get(0);
		}
		else {
			frac = ( dist - cumLengthList.get(seg-1) ) / lengthList.get(seg);
		}
		if( frac < 0.0 )  frac = 0.0;
		else if( frac > 1.0 )  frac = 1.0;

		Vec3d vec = new Vec3d();
		vec.interpolate3(pointsInput.getValue().get(seg), pointsInput.getValue().get(seg+1), frac);
		return vec;
	}

	@Override
	public void updateForInput( Input<?> in ) {
		super.updateForInput(in);

		// If Points were input, then use them to set the start and end coordinates
		if( in == pointsInput || in == colorInput || in == widthInput ) {
			synchronized(screenPointLock) {
				cachedPointInfo = null;
			}
			return;
		}
	}

	@Override
	public void updateGraphics( double simTime ) {

		// Loop through the entities on the path
		for( int i = 0; i < entityList.size(); i++) {
			DisplayEntity each = entityList.get( i );

			// Calculate the distance travelled by this entity
			double dist = ( simTime - startTimeList.get(i) ) / durationList.get(i) * totalLength;

			// Set the position for the entity
			each.setPosition( this.getPositionForDistance( dist) );
		}
	}

	@Override
	public HasScreenPoints.PointsInfo[] getScreenPoints() {
		synchronized(screenPointLock) {
			if (cachedPointInfo == null) {
				cachedPointInfo = new HasScreenPoints.PointsInfo[1];
				HasScreenPoints.PointsInfo pi = new HasScreenPoints.PointsInfo();
				cachedPointInfo[0] = pi;

				pi.points = pointsInput.getValue();
				pi.color = colorInput.getValue();
				pi.width = widthInput.getValue().intValue();
				if (pi.width < 1) pi.width = 1;
			}
			return cachedPointInfo;
		}
	}

	@Override
	public boolean selectable() {
		return true;
	}

	/**
	 *  Inform simulation and editBox of new positions.
	 */
	@Override
	public void dragged(Vec3d dist) {
		ArrayList<Vec3d> vec = new ArrayList<Vec3d>(pointsInput.getValue().size());
		for (Vec3d v : pointsInput.getValue()) {
			vec.add(new Vec3d(v.x + dist.x, v.y + dist.y, v.z + dist.z));
		}

		StringBuilder tmp = new StringBuilder();
		Locale loc = null;
		for (Vec3d v : vec) {
			tmp.append(String.format(loc, " { %.3f %.3f %.3f m }", v.x, v.y, v.z));
		}
		InputAgent.processEntity_Keyword_Value(this, pointsInput, tmp.toString());

		super.dragged(dist);
	}

}
