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
import java.util.Map;

import javax.media.opengl.GL2GL3;

import com.jaamsim.math.AABB;
import com.jaamsim.math.Color4d;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec4d;
import com.jaamsim.render.Renderer.ShaderHandle;

/**
 * Miscellaneous debug tools
 * @author Matt.Chudleigh
 *
 */
public class DebugUtils {

	// A vertex buffer of vec3s that draw the lines of a 2*2*2 box at the origin
	private static int _aabbVertBuffer;
	private static int _boxVertBuffer;
	private static int _lineVertBuffer;

	private static int _debugProgHandle;
	private static int _modelViewMatVar;
	private static int _projMatVar;
	private static int _colorVar;
	private static int _posVar;

	private static int _debugVAOKey;


	/**
	 * Initialize the GL assets needed, this should be called with the shared GL context
	 * @param gl
	 */
	public static void init(Renderer r, GL2GL3 gl) {
		int[] is = new int[3];
		gl.glGenBuffers(3, is, 0);
		_aabbVertBuffer = is[0];
		_boxVertBuffer = is[1];
		_lineVertBuffer = is[2];

		Shader s = r.getShader(ShaderHandle.DEBUG);
		_debugProgHandle = s.getProgramHandle();
		gl.glUseProgram(_debugProgHandle);

		_modelViewMatVar = gl.glGetUniformLocation(_debugProgHandle, "modelViewMat");
		_projMatVar = gl.glGetUniformLocation(_debugProgHandle, "projMat");
		_colorVar = gl.glGetUniformLocation(_debugProgHandle, "color");

		_posVar = gl.glGetAttribLocation(_debugProgHandle, "position");

		_debugVAOKey = Renderer.getAssetID();

		// Build up a buffer of vertices for lines in a box
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _aabbVertBuffer);

		FloatBuffer fb = FloatBuffer.allocate(3 * 2 * 12); // 12 line segments
		// Top lines
		app( 1,  1,  1, fb);
		app( 1, -1,  1, fb);

		app( 1, -1,  1, fb);
		app(-1, -1,  1, fb);

		app(-1, -1,  1, fb);
		app(-1,  1,  1, fb);

		app(-1,  1,  1, fb);
		app( 1,  1,  1, fb);

		// Bottom lines
		app( 1,  1, -1, fb);
		app( 1, -1, -1, fb);

		app( 1, -1, -1, fb);
		app(-1, -1, -1, fb);

		app(-1, -1, -1, fb);
		app(-1,  1, -1, fb);

		app(-1,  1, -1, fb);
		app( 1,  1, -1, fb);

		// Side lines
		app( 1,  1,  1, fb);
		app( 1,  1, -1, fb);

		app(-1,  1,  1, fb);
		app(-1,  1, -1, fb);

		app( 1, -1,  1, fb);
		app( 1, -1, -1, fb);

		app(-1, -1,  1, fb);
		app(-1, -1, -1, fb);

		fb.flip();

		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, 3 * 2 * 12 * 4, fb, GL2GL3.GL_STATIC_DRAW);
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		// Create a buffer for drawing rectangles
		// Build up a buffer of vertices for lines in a box
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _boxVertBuffer);

		fb = FloatBuffer.allocate(3 * 2 * 4); // 4 line segments

		// lines
		app( 0.5f,  0.5f,  0, fb);
		app( 0.5f, -0.5f,  0, fb);

		app( 0.5f, -0.5f,  0, fb);
		app(-0.5f, -0.5f,  0, fb);

		app(-0.5f, -0.5f,  0, fb);
		app(-0.5f,  0.5f,  0, fb);

		app(-0.5f,  0.5f,  0, fb);
		app( 0.5f,  0.5f,  0, fb);

		fb.flip();

		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, 3 * 2 * 4 * 4, fb, GL2GL3.GL_STATIC_DRAW);
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

	}

	public static void renderAABB(Map<Integer, Integer> vaoMap, Renderer renderer,
	                              AABB aabb, Color4d color, Camera cam) {

		if (aabb.isEmpty()) {
			return;
		}

		GL2GL3 gl = renderer.getGL();

		if (!vaoMap.containsKey(_debugVAOKey)) {
			setupDebugVAO(vaoMap, renderer);
		}

		int vao = vaoMap.get(_debugVAOKey);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_debugProgHandle);

		// Setup uniforms for this object
		Mat4d projMat = cam.getProjMat4d();
		Mat4d modelViewMat = new Mat4d();
		cam.getViewMat4d(modelViewMat);

		Mat4d aabbCenterMat = new Mat4d();
		aabbCenterMat.setTranslate3(aabb.getCenter());
		modelViewMat.mult4(aabbCenterMat);
		modelViewMat.scaleCols3(aabb.getRadius());

		gl.glUniformMatrix4fv(_modelViewMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewMat), 0);
		gl.glUniformMatrix4fv(_projMatVar, 1, false, RenderUtils.MarshalMat4d(projMat), 0);

		gl.glUniform4fv(_colorVar, 1, color.toFloats(), 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _aabbVertBuffer);
		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glDrawArrays(GL2GL3.GL_LINES, 0, 12 * 2);

		gl.glBindVertexArray(0);

	}

	// Render a 1*1 box in the XY plane centered at the origin
	public static void renderBox(Map<Integer, Integer> vaoMap, Renderer renderer,
            Transform modelTrans, Vec4d scale, Color4d color, Camera cam) {

		GL2GL3 gl = renderer.getGL();

		if (!vaoMap.containsKey(_debugVAOKey)) {
			setupDebugVAO(vaoMap, renderer);
		}

		int vao = vaoMap.get(_debugVAOKey);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_debugProgHandle);

		// Setup uniforms for this object
		Mat4d projMat = cam.getProjMat4d();
		Mat4d modelViewMat = new Mat4d();
		cam.getViewMat4d(modelViewMat);

		modelViewMat.mult4(modelTrans.getMat4dRef());
		modelViewMat.scaleCols3(scale);

		gl.glUniformMatrix4fv(_modelViewMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewMat), 0);
		gl.glUniformMatrix4fv(_projMatVar, 1, false, RenderUtils.MarshalMat4d(projMat), 0);

		gl.glUniform4fv(_colorVar, 1, color.toFloats(), 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _boxVertBuffer);
		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glDrawArrays(GL2GL3.GL_LINES, 0, 4 * 2);

		gl.glBindVertexArray(0);
	}

	/**
	 * Render a number of lines segments from the points provided
	 * @param vaoMap
	 * @param renderer
	 * @param lineSegments - pairs of discontinuous line start and end points
	 * @param color
	 * @param cam
	 */
	public static void renderLine(Map<Integer, Integer> vaoMap, Renderer renderer,
            FloatBuffer lineSegments, float[] color, double lineWidth, Camera cam) {

		GL2GL3 gl = renderer.getGL();

		if (!vaoMap.containsKey(_debugVAOKey)) {
			setupDebugVAO(vaoMap, renderer);
		}

		int vao = vaoMap.get(_debugVAOKey);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_debugProgHandle);

		// Setup uniforms for this object
		Mat4d projMat = cam.getProjMat4d();
		Mat4d modelViewMat = new Mat4d();
		cam.getViewMat4d(modelViewMat);


		gl.glUniformMatrix4fv(_modelViewMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewMat), 0);
		gl.glUniformMatrix4fv(_projMatVar, 1, false, RenderUtils.MarshalMat4d(projMat), 0);

		gl.glUniform4fv(_colorVar, 1, color, 0);

		gl.glLineWidth((float)lineWidth);

		// Build up a float buffer to pass to GL

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _lineVertBuffer);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, lineSegments.limit() * 4, lineSegments, GL2GL3.GL_STATIC_DRAW);

		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		gl.glDrawArrays(GL2GL3.GL_LINES, 0, lineSegments.limit() / 3);

		gl.glLineWidth(1.0f);

		gl.glBindVertexArray(0);
	}

	/**
	 * Render a list of points
	 * @param vaoMap
	 * @param renderer
	 * @param points
	 * @param color
	 * @param pointWidth
	 * @param cam
	 */
	public static void renderPoints(Map<Integer, Integer> vaoMap, Renderer renderer,
            FloatBuffer points, float[] color, double pointWidth, Camera cam) {

		GL2GL3 gl = renderer.getGL();

		if (!vaoMap.containsKey(_debugVAOKey)) {
			setupDebugVAO(vaoMap, renderer);
		}

		int vao = vaoMap.get(_debugVAOKey);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_debugProgHandle);

		// Setup uniforms for this object
		Mat4d projMat = cam.getProjMat4d();
		Mat4d modelViewMat = new Mat4d();
		cam.getViewMat4d(modelViewMat);


		gl.glUniformMatrix4fv(_modelViewMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewMat), 0);
		gl.glUniformMatrix4fv(_projMatVar, 1, false, RenderUtils.MarshalMat4d(projMat), 0);

		gl.glUniform4fv(_colorVar, 1, color, 0);

		gl.glPointSize((float)pointWidth);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, _lineVertBuffer);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, points.limit() * 4, points, GL2GL3.GL_STATIC_DRAW);

		gl.glVertexAttribPointer(_posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		gl.glDrawArrays(GL2GL3.GL_POINTS, 0, points.limit() / 3);

		gl.glPointSize(1.0f);

		gl.glBindVertexArray(0);
	}

	private static void setupDebugVAO(Map<Integer, Integer> vaoMap, Renderer renderer) {
		GL2GL3 gl = renderer.getGL();

		int[] vaos = new int[1];
		gl.glGenVertexArrays(1, vaos, 0);
		int vao = vaos[0];
		vaoMap.put(_debugVAOKey, vao);
		gl.glBindVertexArray(vao);

		gl.glUseProgram(_debugProgHandle);

		gl.glEnableVertexAttribArray(_posVar);

		gl.glBindVertexArray(0);

	}


	/**
	 * Dummy helper to make init less ugly
	 * @param f0
	 * @param f1
	 * @param f2
	 * @param fb
	 */
	private static void app(float f0, float f1, float f2, FloatBuffer fb) {
		fb.put(f0); fb.put(f1); fb.put(f2);
	}
}