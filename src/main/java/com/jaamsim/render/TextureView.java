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

import java.net.URL;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Map;

import javax.media.opengl.GL2GL3;

import com.jaamsim.math.AABB;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.Ray;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec3d;
import com.jaamsim.math.Vec4d;


/**
 * TextureView is a simple renderable that allows a rectangular image to be easily displayed in 3-space
 * @author Matt.Chudleigh
 *
 */
public class TextureView implements Renderable {

	private URL _imageURL;
	private Transform _trans;
	private Vec3d _scale;
	private long _pickingID;

	private AABB _bounds;

	private boolean _isTransparent;
	private boolean _isCompressed;

	private VisibilityInfo _visInfo;

	// Initialize the very simple buffers needed to render this image
	static private boolean staticInit = false;
	static private int vertBuff;
	static private int texCoordBuff;
	static private int normalBuff;
	static private int assetID;

	static private int progHandle;
	static private int modelViewProjMatVar;
	static private int normalMatVar;
	static private int lightDirVar;
	static private int texVar;

	static private int hasTexVar;

	public TextureView(URL imageURL, Transform trans, Vec3d scale, boolean isTransparent, boolean isCompressed,
	                   VisibilityInfo visInfo, long pickingID) {
		_imageURL = imageURL;
		_trans = trans;
		_scale = scale;
		_scale.z = 1; // This object can only be scaled in X, Y
		_pickingID = pickingID;
		_isTransparent = isTransparent;
		_isCompressed = isCompressed;
		_visInfo = visInfo;


		Mat4d modelMat = RenderUtils.mergeTransAndScale(_trans, _scale);

		ArrayList<Vec4d> vs = new ArrayList<Vec4d>(4);
		vs.add(new Vec4d( 0.5,  0.5, 0, 1.0d));
		vs.add(new Vec4d(-0.5,  0.5, 0, 1.0d));
		vs.add(new Vec4d(-0.5, -0.5, 0, 1.0d));
		vs.add(new Vec4d( 0.5, -0.5, 0, 1.0d));

		_bounds = new AABB(vs, modelMat);

	}

	private static void initStaticBuffers(Renderer r) {
		GL2GL3 gl = r.getGL();

		assetID = Renderer.getAssetID();

		int[] buffs = new int[3];
		gl.glGenBuffers(3, buffs, 0);
		vertBuff = buffs[0];
		texCoordBuff = buffs[1];
		normalBuff = buffs[2];

		FloatBuffer verts = FloatBuffer.allocate(6*3); // 2 triangles * 3 coordinates
		verts.put(-0.5f); verts.put(-0.5f); verts.put(0.0f);
		verts.put( 0.5f); verts.put(-0.5f); verts.put(0.0f);
		verts.put( 0.5f); verts.put( 0.5f); verts.put(0.0f);

		verts.put(-0.5f); verts.put(-0.5f); verts.put(0.0f);
		verts.put( 0.5f); verts.put( 0.5f); verts.put(0.0f);
		verts.put(-0.5f); verts.put( 0.5f); verts.put(0.0f);

		verts.flip();
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, vertBuff);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, 6*3*4, verts, GL2GL3.GL_STATIC_DRAW);

		FloatBuffer texCoords = FloatBuffer.allocate(6*2); // 2 triangles * 2 coordinates

		texCoords.put(0.0f); texCoords.put(0.0f);
		texCoords.put(1.0f); texCoords.put(0.0f);
		texCoords.put(1.0f); texCoords.put(1.0f);

		texCoords.put(0.0f); texCoords.put(0.0f);
		texCoords.put(1.0f); texCoords.put(1.0f);
		texCoords.put(0.0f); texCoords.put(1.0f);

		texCoords.flip();
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, texCoordBuff);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, 6*2*4, texCoords, GL2GL3.GL_STATIC_DRAW);

		FloatBuffer normals = FloatBuffer.allocate(6*3); // 2 triangles * 3 coordinates
		for (int i = 0; i < 6; ++i) {
			normals.put(0.0f); normals.put(0.0f); normals.put(1.0f);
		}

		normals.flip();
		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, normalBuff);
		gl.glBufferData(GL2GL3.GL_ARRAY_BUFFER, 6*3*4, normals, GL2GL3.GL_STATIC_DRAW);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, 0);

		// Initialize the shader variables
		progHandle = r.getShader(Renderer.ShaderHandle.MESH).getProgramHandle();

		modelViewProjMatVar = gl.glGetUniformLocation(progHandle, "modelViewProjMat");
		normalMatVar = gl.glGetUniformLocation(progHandle, "normalMat");
		lightDirVar = gl.glGetUniformLocation(progHandle, "lightDir");
		texVar = gl.glGetUniformLocation(progHandle, "tex");
		hasTexVar = gl.glGetUniformLocation(progHandle, "useTex");

		staticInit = true;
	}

	private void setupVAO(Map<Integer, Integer> vaoMap, Renderer renderer) {
		GL2GL3 gl = renderer.getGL();

		int[] vaos = new int[1];
		gl.glGenVertexArrays(1, vaos, 0);
		vaoMap.put(assetID, vaos[0]);

		gl.glBindVertexArray(vaos[0]);


		// Position
		int posVar = gl.glGetAttribLocation(progHandle, "position");
		gl.glEnableVertexAttribArray(posVar);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, vertBuff);
		gl.glVertexAttribPointer(posVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		// Normals
		int normalVar = gl.glGetAttribLocation(progHandle, "normal");
		gl.glEnableVertexAttribArray(normalVar);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, normalBuff);
		gl.glVertexAttribPointer(normalVar, 3, GL2GL3.GL_FLOAT, false, 0, 0);

		// TexCoords
		int texCoordVar = gl.glGetAttribLocation(progHandle, "texCoord");
		gl.glEnableVertexAttribArray(texCoordVar);

		gl.glBindBuffer(GL2GL3.GL_ARRAY_BUFFER, texCoordBuff);
		gl.glVertexAttribPointer(texCoordVar, 2, GL2GL3.GL_FLOAT, false, 0, 0);

		gl.glBindVertexArray(0);

	}


	@Override
	public void render(Map<Integer, Integer> vaoMap, Renderer renderer, Camera cam, Ray pickRay) {
		if (_isTransparent) {
			// Return, this will be handled in the transparent render phase
			return;
		}

		renderImp(vaoMap, renderer, cam, pickRay);
	}

	private void renderImp(Map<Integer, Integer> vaoMap, Renderer renderer, Camera cam, Ray pickRay) {

		if (!staticInit) {
			initStaticBuffers(renderer);
		}

		GL2GL3 gl = renderer.getGL();

		int textureID = renderer.getTexCache().getTexID(gl, _imageURL, _isTransparent, _isCompressed, false);

		if (textureID == TexCache.LOADING_TEX_ID) {
			return; // This texture is not ready yet
		}

		if (!vaoMap.containsKey(assetID)) {
			setupVAO(vaoMap, renderer);
		}

		int vao = vaoMap.get(assetID);
		gl.glBindVertexArray(vao);

		Mat4d projMat = cam.getProjMat4d();

		Mat4d modelViewProjMat = new Mat4d();

		cam.getViewMat4d(modelViewProjMat);
		modelViewProjMat.mult4(_trans.getMat4dRef());
		modelViewProjMat.scaleCols3(_scale);

		modelViewProjMat.mult4(projMat, modelViewProjMat);

		Mat4d normalMat = RenderUtils.getInverseWithScale(_trans, _scale);
		normalMat.transpose4();

		//Debug
		Vec4d transNormal = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
		transNormal.mult4(normalMat, Vec4d.Z_AXIS);

		gl.glUseProgram(progHandle);

		gl.glUniformMatrix4fv(modelViewProjMatVar, 1, false, RenderUtils.MarshalMat4d(modelViewProjMat), 0);
		gl.glUniformMatrix4fv(normalMatVar, 1, false, RenderUtils.MarshalMat4d(normalMat), 0);

		gl.glUniform1i(hasTexVar, 1);

		Vec4d lightVect = new Vec4d(0,  0, -1,  0);
		lightVect.normalize3();

		gl.glUniform4f(lightDirVar, (float)lightVect.x,
		                            (float)lightVect.y,
		                            (float)lightVect.z,
		                            (float)lightVect.w);

		gl.glActiveTexture(GL2GL3.GL_TEXTURE0);
		gl.glBindTexture(GL2GL3.GL_TEXTURE_2D, textureID);
		gl.glUniform1i(texVar, 0);

		if (_isTransparent) {
			gl.glEnable(GL2GL3.GL_BLEND);
			gl.glBlendEquationSeparate(GL2GL3.GL_FUNC_ADD, GL2GL3.GL_MAX);
			gl.glBlendFuncSeparate(GL2GL3.GL_SRC_ALPHA, GL2GL3.GL_ONE_MINUS_SRC_ALPHA, GL2GL3.GL_ONE, GL2GL3.GL_ONE);
		}

		// Draw
		gl.glDisable(GL2GL3.GL_CULL_FACE);
		gl.glDrawArrays(GL2GL3.GL_TRIANGLES, 0, 6);
		gl.glEnable(GL2GL3.GL_CULL_FACE);

		if (_isTransparent) {
			gl.glDisable(GL2GL3.GL_BLEND);
		}

		gl.glBindVertexArray(0);

	}

	@Override
	public long getPickingID() {
		return _pickingID;
	}

	@Override
	public AABB getBoundsRef() {
		return _bounds;
	}

	@Override
	public double getCollisionDist(Ray r)
	{
		return _bounds.collisionDist(r);
	}

	@Override
	public boolean hasTransparent() {
		return _isTransparent;
	}

	@Override
	public void renderTransparent(Map<Integer, Integer> vaoMap, Renderer renderer, Camera cam, Ray pickRay) {
		renderImp(vaoMap, renderer, cam, pickRay);
	}

	@Override
	public boolean renderForView(int viewID, double dist) {
		if (dist < _visInfo.minDist || dist > _visInfo.maxDist) {
			return false;
		}

		if (_visInfo.viewIDs == null || _visInfo.viewIDs.size() == 0) return true; //Default to always visible
		return _visInfo.viewIDs.contains(viewID);
	}

}