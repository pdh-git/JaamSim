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
package com.jaamsim.render;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.media.opengl.GL2GL3;

import com.jaamsim.math.AABB;
import com.jaamsim.math.Color4d;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.MathUtils;
import com.jaamsim.math.Ray;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec3d;
import com.jaamsim.math.Vec4d;
import com.jaamsim.render.Renderer.ShaderHandle;

/**
 * A Renderable that draws convex (or relatively convex) polygons. Specifically it draw a triangle fan through all the
 * provided points centered at the median point
 *
 * @author matt.chudleigh
 *
 */
public class Polygon implements Renderable {

	private static int _vertBuffer;
	private static int _progHandle; // The flat, unshaded program handle
	private static int _modelViewMatVar;
	private static int _projMatVar;
	private static int _colorVar;
	private static int _posVar;

	private static int _cVar;
	private static int _fcVar;

	private static HashMap<Integer, Integer> _VAOMap = new HashMap<Integer, Integer>();

	private static boolean _hasInitialized;

	private final ArrayList<Vec3d> _points;
	private VisibilityInfo _visInfo;

	private final float[] colour;
	private final float[] hoverColour;
	private boolean isOutline;
	private double lineWidth; // only meaningful if (isOutline)
	private long pickingID;

	private Transform trans;

	private AABB _bounds;

	FloatBuffer fb;

	public Polygon(List<Vec4d> points, Transform trans, Vec3d scale, Color4d colour,
			Color4d hoverColour, VisibilityInfo visInfo, boolean isOutline, double lineWidth, long pickingID) {
		this.colour = colour.toFloats();
		this.hoverColour = hoverColour.toFloats();
		this.isOutline = isOutline;
		this.lineWidth = lineWidth;
		this.pickingID = pickingID;
		this.trans = trans;
		this._visInfo = visInfo;

		// Points includes the scale, but not the transform
		_points = new ArrayList<Vec3d>(points.size());
		ArrayList<Vec3d> boundsPoints = new ArrayList<Vec3d>(points.size());
		for (Vec4d p : points) {
			Vec3d temp = new Vec3d(p);
			temp.mul3(scale);
			_points.add(temp);

			Vec3d bTemp = new Vec3d();
			trans.multAndTrans(temp, bTemp);
			boundsPoints.add(bTemp);
		}

		_bounds = new AABB(boundsPoints);

		if (this.isOutline) {
			fb = FloatBuffer.allocate(3 * _points.size());
			for (Vec3d vert : _points) {
				RenderUtils.putPointXYZ(fb, vert);
			}
		} else {
			// Otherwise make a triangle fan c
			Vec3d center = new Vec3d();
			for (Vec3d vert : _points) {
				center.add3(vert);
			}
			center.scale3(1.0/_points.size());

			// The vertex list is just the closed loop of points
			int buffSize = 3 * (_points.size() + 2);
			fb = FloatBuffer.allocate(buffSize);
			// Put the center to start the triangle fan
			RenderUtils.putPointXYZ(fb, center);
			for (Vec3d vert : _points) {
				RenderUtils.putPointXYZ(fb, vert);
			}
			RenderUtils.putPointXYZ(fb, _points.get(0));
		}
		fb.flip();
	}

	@Override
	public void render(int contextID, Renderer renderer,
			Camera cam, Ray pickRay) {
		assert(_hasInitialized);

		GL2GL3 gl = renderer.getGL();
		if (_points.size() < 3) {
			return; // Can't actually draw this polygon
		}

		float[] renderColour = colour;
		if (pickRay != null && getCollisionDist(pickRay, false) > 0)
			renderColour = hoverColour;

		if (!_VAOMap.containsKey(contextID)) {
			setupVAO(contextID, renderer);
		}

		int vao = _VAOMap.get(contextID);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_progHandle);

		// Setup uniforms for this object
		Mat4d projMat = cam.getProjMat4d();
		Mat4d modelViewMat = new Mat4d();
		cam.getViewMat4d(modelViewMat);

		modelViewMat.mult4(trans.getMat4dRef());


		gl.glUniformMatrix4fv(_modelViewMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewMat), 0);
		gl.glUniformMatrix4fv(_projMatVar, 1, false, RenderUtils.MarshalMat4d(projMat), 0);

		gl.glUniform1f(_cVar, Camera.C);
		gl.glUniform1f(_fcVar, Camera.FC);

		gl.glUniform4fv(_colorVar, 1, renderColour, 0);

		if (isOutline) {
			renderOutline(gl);
		} else {
			renderFill(gl);
		}

		gl.glBindVertexArray(0);

	}

	private void renderOutline(GL2GL3 gl) {
		// The vertex list is just the closed loop of points
		if (lineWidth == 0) {
			return;
		}

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _vertBuffer);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, fb.limit() * 4, fb, GL2GL3.GL_STATIC_DRAW);

		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		gl.glLineWidth((float)lineWidth);
		gl.glDrawArrays(GL2GL3.GL_LINE_LOOP, 0, _points.size());

		gl.glLineWidth(1.0f);

	}

	private void renderFill(GL2GL3 gl) {

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _vertBuffer);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, fb.limit() * 4, fb, GL2GL3.GL_STATIC_DRAW);

		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		gl.glDrawArrays(GL2GL3.GL_TRIANGLE_FAN, 0, _points.size() + 2);

	}

	@Override
	public long getPickingID() {
		return pickingID;
	}

	@Override
	public AABB getBoundsRef() {
		return _bounds;
	}

	@Override
	public double getCollisionDist(Ray r, boolean precise) {
		double dist =  _bounds.collisionDist(r);

		if (dist < 0) { return dist; } // Does not collide with the bounds

		// Create a ray in the points coordinate space
		Transform invTrans = new Transform();
		trans.inverse(invTrans);
		Ray localRay = r.transform(invTrans);

		return MathUtils.collisionDistPoly(localRay, _points);
	}

	// This should be called from the renderer at initialization
	public static void init(Renderer r, GL2GL3 gl) {
		int[] is = new int[1];
		gl.glGenBuffers(1, is, 0);
		_vertBuffer = is[0];

		Shader s = r.getShader(ShaderHandle.DEBUG);
		_progHandle = s.getProgramHandle();
		gl.glUseProgram(_progHandle);

		_modelViewMatVar = gl.glGetUniformLocation(_progHandle, "modelViewMat");
		_projMatVar = gl.glGetUniformLocation(_progHandle, "projMat");
		_colorVar = gl.glGetUniformLocation(_progHandle, "color");

		_cVar = gl.glGetUniformLocation(_progHandle, "C");
		_fcVar = gl.glGetUniformLocation(_progHandle, "FC");

		_posVar = gl.glGetAttribLocation(_progHandle, "position");

		_hasInitialized = true;
	}

	private static void setupVAO(int contextID, Renderer renderer) {
		GL2GL3 gl = renderer.getGL();

		int vao = renderer.generateVAO(contextID, gl);

		_VAOMap.put(contextID, vao);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_progHandle);

		gl.glEnableVertexAttribArray(_posVar);

		gl.glBindVertexArray(0);

	}

	@Override
	public boolean hasTransparent() {
		return false;
	}

	@Override
	public void renderTransparent(int contextID, Renderer renderer, Camera cam, Ray pickRay) {
	}

	@Override
	public boolean renderForView(int viewID, Camera cam) {
		double dist = cam.distToBounds(getBoundsRef());
		return _visInfo.isVisible(viewID, dist);
	}
}
