/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2012 Ausenco Engineering Canada Inc.
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
package com.jaamsim.input;

import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.jaamsim.math.Vec3d;

public class TestKeyedVec3dCurve {

	private KeyedVec3dCurve testCurve;

	@Before
	public void setup() {
		testCurve = new KeyedVec3dCurve();
		testCurve.addKey(11, new Vec3d());
		testCurve.addKey(100, new Vec3d(1, 2, 3));
		testCurve.addKey(0, new Vec3d());
		testCurve.addKey(1, new Vec3d(1, 10, 100));

	}

	private void testNear(double a, double b) {
		double diff = Math.abs(a - b);
		assertTrue(diff < 0.00001);
	}

	@Test
	public void testInterpolator() {
		Vec3d half = testCurve.getValAtTime(0.5);
		testNear(half.x, 0.5);
		testNear(half.y, 5);
		testNear(half.z, 50);

		Vec3d six = testCurve.getValAtTime(6);
		testNear(six.x, 0.5);
		testNear(six.y, 5);
		testNear(six.z, 50);

		Vec3d ten = testCurve.getValAtTime(10);
		testNear(ten.x, 0.1);
		testNear(ten.y, 1);
		testNear(ten.z, 10);
	}

	@Test
	public void testEdges() {
		Vec3d negOne = testCurve.getValAtTime(-1);
		testNear(negOne.x, 0);
		testNear(negOne.y, 0);
		testNear(negOne.z, 0);

		Vec3d big = testCurve.getValAtTime(10000);
		testNear(big.x, 1);
		testNear(big.y, 2);
		testNear(big.z, 3);
	}

	@Test
	public void testEquals() {
		Vec3d zero = testCurve.getValAtTime(0);
		testNear(zero.x, 0);
		testNear(zero.y, 0);
		testNear(zero.z, 0);

		Vec3d one = testCurve.getValAtTime(1);
		testNear(one.x, 1);
		testNear(one.y, 10);
		testNear(one.z, 100);

		Vec3d hundred = testCurve.getValAtTime(100);
		testNear(hundred.x, 1);
		testNear(hundred.y, 2);
		testNear(hundred.z, 3);
	}

}
