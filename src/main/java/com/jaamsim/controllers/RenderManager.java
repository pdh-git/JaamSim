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
package com.jaamsim.controllers;

import java.awt.Frame;
import java.awt.Image;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.jaamsim.DisplayModels.ColladaModel;
import com.jaamsim.DisplayModels.DisplayModel;
import com.jaamsim.DisplayModels.ImageModel;
import com.jaamsim.font.TessFont;
import com.jaamsim.input.InputAgent;
import com.jaamsim.math.AABB;
import com.jaamsim.math.Color4d;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.MathUtils;
import com.jaamsim.math.Plane;
import com.jaamsim.math.Quaternion;
import com.jaamsim.math.Ray;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec3d;
import com.jaamsim.math.Vec4d;
import com.jaamsim.render.CameraInfo;
import com.jaamsim.render.DisplayModelBinding;
import com.jaamsim.render.Future;
import com.jaamsim.render.HasScreenPoints;
import com.jaamsim.render.MeshProtoKey;
import com.jaamsim.render.OffscreenTarget;
import com.jaamsim.render.PreviewCache;
import com.jaamsim.render.RenderProxy;
import com.jaamsim.render.RenderUtils;
import com.jaamsim.render.Renderer;
import com.jaamsim.render.TessFontKey;
import com.jaamsim.render.WindowInteractionListener;
import com.jaamsim.render.util.ExceptionLogger;
import com.jaamsim.ui.FrameBox;
import com.jaamsim.ui.View;
import com.sandwell.JavaSimulation.Entity;
import com.sandwell.JavaSimulation.Input;
import com.sandwell.JavaSimulation.InputErrorException;
import com.sandwell.JavaSimulation.IntegerVector;
import com.sandwell.JavaSimulation.ObjectType;
import com.sandwell.JavaSimulation3D.DisplayEntity;
import com.sandwell.JavaSimulation3D.DisplayModelCompat;
import com.sandwell.JavaSimulation3D.GUIFrame;
import com.sandwell.JavaSimulation3D.Graph;
import com.sandwell.JavaSimulation3D.ObjectSelector;
import com.sandwell.JavaSimulation3D.Region;

/**
 * Top level owner of the JaamSim renderer. This class both owns and drives the Renderer object, but is also
 * responsible for gathering rendering data every frame.
 * @author Matt Chudleigh
 *
 */
public class RenderManager implements DragSourceListener {

	private static RenderManager s_instance = null;
	/**
	 * Basic singleton pattern
	 */
	public static void initialize() {
		s_instance = new RenderManager();
	}

	public static RenderManager inst() { return s_instance; }

	private final Thread _managerThread;
	private final Renderer _renderer;
	private final AtomicBoolean _finished = new AtomicBoolean(false);
	private final AtomicBoolean _fatalError = new AtomicBoolean(false);
	private final AtomicBoolean _redraw = new AtomicBoolean(false);

	private final AtomicBoolean _screenshot = new AtomicBoolean(false);

	// These values are used to limit redraw rate, the stored values are time in milliseconds
	// returned by System.currentTimeMillis()
	private final AtomicLong _lastDraw = new AtomicLong(0);
	private final AtomicLong _scheduledDraw = new AtomicLong(0);

	private final ExceptionLogger _exceptionLogger;

	private final static double FPS = 60;
	private final Timer _timer;

	private final HashMap<Integer, CameraControl> _windowControls = new HashMap<Integer, CameraControl>();
	private final HashMap<Integer, View> _windowToViewMap= new HashMap<Integer, View>();
	private int _activeWindowID = -1;

	private final Object _popupLock;
	private JPopupMenu _lastPopup;

	/**
	 * The last scene rendered
	 */
	private ArrayList<RenderProxy> _cachedScene;

	private DisplayEntity _selectedEntity = null;

	private double _simTime = 0.0d;

	private boolean _isDragging = false;
	private long _dragHandleID = 0;

	// The object type for drag-and-drop operation, if this is null, the user is not dragging
	private ObjectType _dndObjectType;
	private long _dndDropTime = 0;

	private VideoRecorder _recorder;

	private PreviewCache _previewCache = new PreviewCache();

	// Below are special PickingIDs for resizing and dragging handles
	public static final long MOVE_PICK_ID = -1;

	// For now this order is implicitly the same as the handle order in RenderObserver, don't re arrange it without touching
	// the handle list
	public static final long RESIZE_POSX_PICK_ID = -2;
	public static final long RESIZE_NEGX_PICK_ID = -3;
	public static final long RESIZE_POSY_PICK_ID = -4;
	public static final long RESIZE_NEGY_PICK_ID = -5;
	public static final long RESIZE_PXPY_PICK_ID = -6;
	public static final long RESIZE_PXNY_PICK_ID = -7;
	public static final long RESIZE_NXPY_PICK_ID = -8;
	public static final long RESIZE_NXNY_PICK_ID = -9;

	public static final long ROTATE_PICK_ID = -10;

	public static final long LINEDRAG_PICK_ID = -11;

	// Line nodes start at this constant and proceed into the negative range, therefore this should be the lowest defined constant
	public static final long LINENODE_PICK_ID = -12;

	private RenderManager() {
		_renderer = new Renderer();

		_exceptionLogger = new ExceptionLogger();

		_managerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				renderManagerLoop();
			}
		}, "RenderManagerThread");
		_managerThread.start();

		// Start the display timer
		_timer = new Timer("RedrawThread");
		TimerTask displayTask = new TimerTask() {
			@Override
			public void run() {

				// Is a redraw scheduled
				long currentTime = System.currentTimeMillis();
				long scheduledTime = _scheduledDraw.get();
				long lastRedraw = _lastDraw.get();

				// Only draw if the scheduled time is before now and after the last redraw
				if (scheduledTime < lastRedraw || currentTime < scheduledTime) {
					return;
				}

				synchronized(_redraw) {
					if (_renderer.getNumOpenWindows() == 0 && !_screenshot.get()) {
						return; // Do not queue a redraw if there are no open windows
					}
					_redraw.set(true);
					_redraw.notifyAll();
				}
			}
		};

		_timer.scheduleAtFixedRate(displayTask, 0, (long) (1000 / (FPS*2)));

		_popupLock = new Object();
	}

	public static final void updateTime(double simTime) {
		if (!RenderManager.isGood())
			return;

		RenderManager.inst().queueRedraw(simTime);
	}

	private void queueRedraw(double time) {
		_simTime = time;
		queueRedraw();
	}

	public void queueRedraw() {
		long scheduledTime = _scheduledDraw.get();
		long lastRedraw = _lastDraw.get();

		if (scheduledTime > lastRedraw) {
			// A draw is scheduled
			return;
		}

		long newDraw = System.currentTimeMillis();
		long frameTime = (long)(1000.0/FPS);
		if (newDraw - lastRedraw < frameTime) {
			// This would be scheduled too soon
			newDraw = lastRedraw + frameTime;
		}
		_scheduledDraw.set(newDraw);
	}

	public void createWindow(View view) {

		// First see if this window has already been opened
		for (Map.Entry<Integer, CameraControl> entry : _windowControls.entrySet()) {
			if (entry.getValue().getView() == view) {
				// This view has a window, just reshow that one
				focusWindow(entry.getKey());
				return;
			}
		}

		IntegerVector windSize = (IntegerVector)view.getInput("WindowSize").getValue();
		IntegerVector windPos = (IntegerVector)view.getInput("WindowPosition").getValue();

		Image icon = GUIFrame.getWindowIcon();

		CameraControl control = new CameraControl(_renderer, view);
		int windowID = _renderer.createWindow(windPos.get(0), windPos.get(1),
		                                      windSize.get(0), windSize.get(1),
		                                      view.getID(),
		                                      view.getTitle(), view.getInputName(),
		                                      icon, control);

		_windowControls.put(windowID, control);
		_windowToViewMap.put(windowID, view);

		dirtyAllEntities();
		queueRedraw();
	}

	public void closeAllWindows() {
		ArrayList<Integer> windIDs = _renderer.getOpenWindowIDs();
		for (int id : windIDs) {
			_renderer.closeWindow(id);
		}
	}

	public void windowClosed(int windowID) {
		_windowControls.remove(windowID);
		_windowToViewMap.remove(windowID);
	}

	public void setActiveWindow(int windowID) {
		_activeWindowID = windowID;
	}

	public static boolean isGood() {
		return (s_instance != null && !s_instance._finished.get() && !s_instance._fatalError.get());
	}

	/**
	 * Ideally, this states that it is safe to call initialize() (assuming isGood() returned false)
	 * @return
	 */
	public static boolean canInitialize() {
		return s_instance == null;
	}

	private void dirtyAllEntities() {
		for (int i = 0; i < DisplayEntity.getAll().size(); ++i) {
			DisplayEntity.getAll().get(i).setGraphicsDataDirty();
		}
	}

	private void renderManagerLoop() {

		while (!_finished.get() && !_fatalError.get()) {
			try {

				if (_renderer.hasFatalError()) {
					// Well, something went horribly wrong
					_fatalError.set(true);
					System.out.printf("Renderer failed with error: %s\n", _renderer.getErrorString());

					// Do some basic cleanup
					_windowControls.clear();
					_previewCache.clear();

					_timer.cancel();

					break;
				}

				_lastDraw.set(System.currentTimeMillis());

				if (!_renderer.isInitialized()) {
					// Give the renderer a chance to initialize
					try {
						Thread.sleep(100);
					} catch(InterruptedException e) {}
					continue;
				}

				for (CameraControl cc : _windowControls.values()) {
					cc.checkForUpdate();
				}

				_cachedScene = new ArrayList<RenderProxy>();
				DisplayModelBinding.clearCacheCounters();
				DisplayModelBinding.clearCacheMissData();

				double renderTime = _simTime;

				long startNanos = System.nanoTime();

				for (int i = 0; i < View.getAll().size(); i++) {
					View v = View.getAll().get(i);
					v.update(renderTime);
				}

				int numDisplayEntities = DisplayEntity.getAll().size();

				ArrayList<DisplayModelBinding> selectedBindings = new ArrayList<DisplayModelBinding>();

				// Update all graphical entities in the simulation
				for (int i = 0; i < numDisplayEntities; i++) {
					try {
						DisplayEntity de = DisplayEntity.getAll().get(i);
						de.updateGraphics(renderTime);
					}
					// Catch everything so we don't screw up the behavior handling
					catch (Throwable e) {
						//e.printStackTrace();
					}
				}

				long updateNanos = System.nanoTime();

				// Refresh, as this may have changed during the update phase
				numDisplayEntities = DisplayEntity.getAll().size();

				int totalBindings = 0;
				for (int i = 0; i < numDisplayEntities; i++) {
					DisplayEntity de;
					try {
						de = DisplayEntity.getAll().get(i);
					} catch (IndexOutOfBoundsException ex) {
						// This is probably the end of the list, so just move on
						break;
					}

					for (DisplayModelBinding binding : de.getDisplayBindings()) {
						try {
							totalBindings++;
							binding.collectProxies(renderTime, _cachedScene);
							if (binding.isBoundTo(_selectedEntity)) {
								selectedBindings.add(binding);
							}
						} catch (Throwable t) {
							// Log the exception in the exception list
							logException(t);
						}
					}
				}

				// Collect selection proxies second so they always appear on top
				for (DisplayModelBinding binding : selectedBindings) {
					try {
						binding.collectSelectionProxies(renderTime, _cachedScene);
					} catch (Throwable t) {
						// Log the exception in the exception list
						logException(t);
					}
				}

				long endNanos = System.nanoTime();

				_renderer.setScene(_cachedScene);

				String cacheString = " Hits: " + DisplayModelBinding.getCacheHits() + " Misses: " + DisplayModelBinding.getCacheMisses() +
				                     " Total: " + totalBindings;

				double gatherMS = (endNanos - updateNanos) / 1000000.0;
				double updateMS = (updateNanos - startNanos) / 1000000.0;

				String timeString = "Gather time (ms): " + gatherMS + " Update time (ms): " + updateMS;

				// Do some picking debug
				ArrayList<Integer> windowIDs = _renderer.getOpenWindowIDs();
				for (int id : windowIDs) {
					Renderer.WindowMouseInfo mouseInfo = _renderer.getMouseInfo(id);

					if (mouseInfo == null || !mouseInfo.mouseInWindow) {
						// Not currently picking for this window
						_renderer.setWindowDebugInfo(id, cacheString + " Not picking. " + timeString, new ArrayList<Long>());
						continue;
					}

					List<PickData> picks = pickForMouse(id);
					ArrayList<Long> debugIDs = new ArrayList<Long>(picks.size());

					String debugString = cacheString + " Picked " + picks.size() + " entities at (" + mouseInfo.x + ", " + mouseInfo.y + "): ";

					for (PickData pd : picks) {
						debugString += Entity.idToName(pd.id);
						debugString += ", ";
						debugIDs.add(pd.id);
					}

					debugString += timeString;

					_renderer.setWindowDebugInfo(id, debugString, debugIDs);
				}

				if (GUIFrame.getShuttingDownFlag()) {
					shutdown();
				}

				_renderer.queueRedraw();
				_redraw.set(false);

				if (_screenshot.get()) {
					takeScreenShot();
				}

			} catch (Throwable t) {
				// Make a note of it, but try to keep going
				logException(t);
			}

			// Wait for a redraw request
			synchronized(_redraw) {
				while (!_redraw.get()) {
					try {
						_redraw.wait(30);
					} catch (InterruptedException e) {}
				}
			}

		}

		_exceptionLogger.printExceptionLog();

	}

	// Temporary dumping ground until I find a better place for this
	public void popupMenu(int windowID) {
		synchronized (_popupLock) {

			Renderer.WindowMouseInfo mouseInfo = _renderer.getMouseInfo(windowID);
			if (mouseInfo == null) {
				// Somehow this window was closed along the way, just ignore this click
				return;
			}

			final Frame awtFrame = _renderer.getAWTFrame(windowID);
			if (awtFrame == null) {
				return;
			}

			List<PickData> picks = pickForMouse(windowID);

			ArrayList<DisplayEntity> ents = new ArrayList<DisplayEntity>();

			for (PickData pd : picks) {
				if (!pd.isEntity) { continue; }
				Entity ent = Entity.idToEntity(pd.id);
				if (ent == null) { continue; }
				if (!(ent instanceof DisplayEntity)) { continue; }

				DisplayEntity de = (DisplayEntity)ent;

				ents.add(de);
			}

			if (!mouseInfo.mouseInWindow) {
				// Somehow this window does not currently have the mouse over it.... ignore?
				return;
			}

			final JPopupMenu menu = new JPopupMenu();
			_lastPopup = menu;

			menu.setLightWeightPopupEnabled(false);
			final int menuX = mouseInfo.x + awtFrame.getInsets().left;
			final int menuY = mouseInfo.y + awtFrame.getInsets().top;

			if (ents.size() == 0) { return; } // Nothing to show

			if (ents.size() == 1) {
				populateMenu(menu, ObjectSelector.getMenuItems(ents.get(0), menuX, menuY));
			}
			else {
				// Several entities, let the user pick the interesting entity first
				for (final DisplayEntity de : ents) {
					JMenuItem thisItem = new JMenuItem(de.getInputName());
					thisItem.addActionListener( new ActionListener() {

						@Override
						public void actionPerformed( ActionEvent event ) {
							menu.removeAll();
							populateMenu(menu, ObjectSelector.getMenuItems(de, menuX, menuY));
							menu.show(awtFrame, menuX, menuY);
						}
					} );

					menu.add( thisItem );
				}
			}

			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					menu.show(awtFrame, menuX, menuY);
					menu.repaint();
				}
			});

		} // synchronized (_popupLock)
	}

	public void handleSelection(int windowID) {

		List<PickData> picks = pickForMouse(windowID);

		Collections.sort(picks, new SelectionSorter());

		for (PickData pd : picks) {
			// Select the first entity after sorting
			if (pd.isEntity) {
				DisplayEntity ent = (DisplayEntity)Entity.idToEntity(pd.id);
				if (!ent.isMovable()) {
					continue;
				}
				FrameBox.setSelectedEntity(ent);
				queueRedraw();
				return;
			}
		}

		FrameBox.setSelectedEntity(null);
		queueRedraw();
	}

	/**
	 * Utility, convert a window and mouse coordinate into a list of picking IDs for that pixel
	 * @param windowID
	 * @param mouseX
	 * @param mouseY
	 * @return
	 */
	private List<PickData> pickForMouse(int windowID) {
		Renderer.WindowMouseInfo mouseInfo = _renderer.getMouseInfo(windowID);

		View view = _windowToViewMap.get(windowID);
		if (mouseInfo == null || view == null || !mouseInfo.mouseInWindow) {
			// The mouse is not actually in the window, or the window was closed along the way
			return new ArrayList<PickData>(); // empty set
		}

		Ray pickRay = RenderUtils.getPickRay(mouseInfo);

		return pickForRay(pickRay, view.getID());
	}


	/**
	 * PickData represents enough information to sort a list of picks based on a picking preference
	 * metric. For now it holds the object size and distance from pick point to object center
	 *
	 */
	private static class PickData {
		public long id;
		public double size;
		boolean isEntity;

		/**
		 * This pick does not correspond to an entity, and is a handle or other UI element
		 * @param id
		 */
		public PickData(long id) {
			this.id = id;
			size = 0;
			isEntity = false;
		}
		/**
		 * This pick was an entity
		 * @param id - the id
		 * @param ent - the entity
		 */
		public PickData(long id, DisplayEntity ent) {
			this.id = id;
			size = ent.getSize().mag3();

			isEntity = true;
		}
	}

	/**
	 * This Comparator sorts based on entity selection preference
	 */
	private class SelectionSorter implements Comparator<PickData> {

		@Override
		public int compare(PickData p0, PickData p1) {
			if (p0.isEntity && !p1.isEntity) {
				return -1;
			}
			if (!p0.isEntity && p1.isEntity) {
				return 1;
			}
			if (p0.size == p1.size) {
				return 0;
			}
			return (p0.size < p1.size) ? -1 : 1;
		}

	}

	/**
	 * This Comparator sorts based on interaction handle priority
	 */
	private class HandleSorter implements Comparator<PickData> {

		@Override
		public int compare(PickData p0, PickData p1) {
			int p0priority = getHandlePriority(p0.id);
			int p1priority = getHandlePriority(p1.id);
			if (p0priority == p1priority)
				return 0;

			return (p0priority < p1priority) ? 1 : -1;
		}
	}

	/**
	 * This determines the priority for interaction handles if several are selectable at drag time
	 * @param handleID
	 * @return
	 */
	private static int getHandlePriority(long handleID) {
		if (handleID == MOVE_PICK_ID) return 1;
		if (handleID == LINEDRAG_PICK_ID) return 1;

		if (handleID <= LINENODE_PICK_ID) return 2;

		if (handleID == ROTATE_PICK_ID) return 3;

		if (handleID == RESIZE_POSX_PICK_ID) return 4;
		if (handleID == RESIZE_NEGX_PICK_ID) return 4;
		if (handleID == RESIZE_POSY_PICK_ID) return 4;
		if (handleID == RESIZE_NEGY_PICK_ID) return 4;

		if (handleID == RESIZE_PXPY_PICK_ID) return 5;
		if (handleID == RESIZE_PXNY_PICK_ID) return 5;
		if (handleID == RESIZE_NXPY_PICK_ID) return 5;
		if (handleID == RESIZE_NXNY_PICK_ID) return 5;

		return 0;
	}


	/**
	 * Perform a pick from this world space ray
	 * @param pickRay - the ray
	 * @return
	 */
	private List<PickData> pickForRay(Ray pickRay, int viewID) {
		List<Renderer.PickResult> picks = _renderer.pick(pickRay, viewID);

		List<PickData> uniquePicks = new ArrayList<PickData>();

		// IDs that have already been added
		Set<Long> knownIDs = new HashSet<Long>();

		for (Renderer.PickResult pick : picks) {
			if (knownIDs.contains(pick.pickingID)) {
				continue;
			}
			knownIDs.add(pick.pickingID);

			DisplayEntity ent = (DisplayEntity)Entity.idToEntity(pick.pickingID);
			if (ent == null) {
				// This object is not an entity, but may be a picking handle
				uniquePicks.add(new PickData(pick.pickingID));
			} else {
				uniquePicks.add(new PickData(pick.pickingID, ent));
			}
		}

		return uniquePicks;
	}

	/**
	 * Pick on a window at a position other than the current mouse position
	 * @param windowID
	 * @param x
	 * @param y
	 * @return
	 */
	private Ray getRayForMouse(int windowID, int x, int y) {
		Renderer.WindowMouseInfo mouseInfo = _renderer.getMouseInfo(windowID);
		if (mouseInfo == null) {
			return new Ray();
		}

		return RenderUtils.getPickRayForPosition(mouseInfo.cameraInfo, x, y, mouseInfo.width, mouseInfo.height);
	}

	public Vec3d getRenderedStringSize(TessFontKey fontKey, double textHeight, String string) {
		TessFont font = _renderer.getTessFont(fontKey);

		return font.getStringSize(textHeight, string);
	}

	private void logException(Throwable t) {
		_exceptionLogger.logException(t);

		// And print the output
		printExceptionLog();
	}

	private void printExceptionLog() {
		System.out.println("Recoverable Exceptions from RenderManager: ");

		_exceptionLogger.printExceptionLog();

		System.out.println("");
	}

	public void populateMenu(JPopupMenu menu, ArrayList<ObjectSelector.DEMenuItem> menuItems) {

		for (final ObjectSelector.DEMenuItem item : menuItems) {
			JMenuItem mi = new JMenuItem(item.menuName);
			mi.addActionListener( new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					item.action();
				}
			});
			menu.add(mi);
		}
	}

	public static void setSelection(Entity ent) {
		if (!RenderManager.isGood())
			return;

		RenderManager.inst().setSelectEntity(ent);
	}


	private void setSelectEntity(Entity ent) {
		if (ent instanceof DisplayEntity)
			_selectedEntity = (DisplayEntity)ent;
		else
			_selectedEntity = null;

		_isDragging = false;
		queueRedraw();
	}

	/**
	 * This method gives the RenderManager a chance to handle mouse drags before the CameraControl
	 * gets to handle it (note: this may need to be refactored into a proper event handling heirarchy)
	 * @param dragInfo
	 * @return
	 */
	public boolean handleDrag(WindowInteractionListener.DragInfo dragInfo) {

		if (!_isDragging) {
			// We have not cached a drag handle ID, so don't claim this and let CameraControl have control back
			return false;
		}
		// We should have a selected entity
		assert(_selectedEntity != null);

		// Any quick outs go here
		if (!_selectedEntity.isMovable()) {
			return false;
		}

		// We don't drag with control down
		if (dragInfo.controlDown()) {
			return false;
		}

		// Find the start and current world space positions

		Ray currentRay = getRayForMouse(dragInfo.windowID, dragInfo.x, dragInfo.y);
		Ray lastRay = getRayForMouse(dragInfo.windowID,
		                             dragInfo.x - dragInfo.dx,
		                             dragInfo.y - dragInfo.dy);

		Transform trans = _selectedEntity.getGlobalTrans(_simTime);

		Vec3d size = _selectedEntity.getSize();
		Mat4d transMat = _selectedEntity.getTransMatrix(_simTime);
		Mat4d invTransMat = _selectedEntity.getInvTransMatrix(_simTime);

		Plane entityPlane = new Plane(); // Defaults to XY
		entityPlane.transform(trans, entityPlane); // Transform the plane to world space

		double currentDist = entityPlane.collisionDist(currentRay);
		double lastDist = entityPlane.collisionDist(lastRay);

		if (currentDist < 0 || currentDist == Double.POSITIVE_INFINITY ||
		       lastDist < 0 ||    lastDist == Double.POSITIVE_INFINITY)
		{
			// The plane is parallel or behind one of the rays...
			return true; // Just ignore it for now...
		}

		// The points where the previous pick ended and current position. Collision is with the entity's XY plane
		Vec4d currentPoint = currentRay.getPointAtDist(currentDist);
		Vec4d lastPoint = lastRay.getPointAtDist(lastDist);

		Vec4d entSpaceCurrent = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d); // entSpacePoint is the current point in model space
		entSpaceCurrent.mult4(invTransMat, currentPoint);

		Vec4d entSpaceLast = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d); // entSpaceLast is the last point in model space
		entSpaceLast.mult4(invTransMat, lastPoint);

		Vec4d delta = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
		delta.sub3(currentPoint, lastPoint);

		Vec4d entSpaceDelta = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
		entSpaceDelta.sub3(entSpaceCurrent, entSpaceLast);

		// Handle each handle by type...
		if (_dragHandleID == MOVE_PICK_ID) {
			// We are dragging
			if (dragInfo.shiftDown()) {
				Vec4d entPos = _selectedEntity.getGlobalPosition();

				double zDiff = getZDiff(entPos, currentRay, lastRay);

				entPos.z += zDiff;
				_selectedEntity.setGlobalPosition(entPos);

				return true;
			}

			Vec4d pos = new Vec4d(_selectedEntity.getGlobalPosition());
			pos.add3(delta);
			_selectedEntity.setGlobalPosition(pos);
			return true;
		}

		// Handle resize
		if (_dragHandleID <= RESIZE_POSX_PICK_ID &&
		    _dragHandleID >= RESIZE_NXNY_PICK_ID) {

			Vec4d pos = new Vec4d(_selectedEntity.getGlobalPosition());
			Vec3d scale = _selectedEntity.getSize();
			Vec4d fixedPoint = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);

			if (_dragHandleID == RESIZE_POSX_PICK_ID) {
				//scale.x = 2*entSpaceCurrent.x() * size.x();
				scale.x += entSpaceDelta.x * size.x;
				fixedPoint = new Vec4d(-0.5,  0.0, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_POSY_PICK_ID) {
				scale.y += entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d( 0.0, -0.5, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_NEGX_PICK_ID) {
				scale.x -= entSpaceDelta.x * size.x;
				fixedPoint = new Vec4d( 0.5,  0.0, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_NEGY_PICK_ID) {
				scale.y -= entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d( 0.0,  0.5, 0.0, 1.0d);
			}

			if (_dragHandleID == RESIZE_PXPY_PICK_ID) {
				scale.x += entSpaceDelta.x * size.x;
				scale.y += entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d(-0.5, -0.5, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_PXNY_PICK_ID) {
				scale.x += entSpaceDelta.x * size.x;
				scale.y -= entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d(-0.5,  0.5, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_NXPY_PICK_ID) {
				scale.x -= entSpaceDelta.x * size.x;
				scale.y += entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d( 0.5, -0.5, 0.0, 1.0d);
			}
			if (_dragHandleID == RESIZE_NXNY_PICK_ID) {
				scale.x -= entSpaceDelta.x * size.x;
				scale.y -= entSpaceDelta.y * size.y;
				fixedPoint = new Vec4d( 0.5,  0.5, 0.0, 1.0d);
			}

			// Handle the case where the scale is pulled through itself. Fix the scale,
			// and swap the currently selected handle
			if (scale.x <= 0.00005) {
				scale.x = 0.0001;
				if (_dragHandleID == RESIZE_POSX_PICK_ID) { _dragHandleID = RESIZE_NEGX_PICK_ID; }
				else if (_dragHandleID == RESIZE_NEGX_PICK_ID) { _dragHandleID = RESIZE_POSX_PICK_ID; }

				else if (_dragHandleID == RESIZE_PXPY_PICK_ID) { _dragHandleID = RESIZE_NXPY_PICK_ID; }
				else if (_dragHandleID == RESIZE_PXNY_PICK_ID) { _dragHandleID = RESIZE_NXNY_PICK_ID; }
				else if (_dragHandleID == RESIZE_NXPY_PICK_ID) { _dragHandleID = RESIZE_PXPY_PICK_ID; }
				else if (_dragHandleID == RESIZE_NXNY_PICK_ID) { _dragHandleID = RESIZE_PXNY_PICK_ID; }
			}

			if (scale.y <= 0.00005) {
				scale.y = 0.0001;
				if (_dragHandleID == RESIZE_POSY_PICK_ID) { _dragHandleID = RESIZE_NEGY_PICK_ID; }
				else if (_dragHandleID == RESIZE_NEGY_PICK_ID) { _dragHandleID = RESIZE_POSY_PICK_ID; }

				else if (_dragHandleID == RESIZE_PXPY_PICK_ID) { _dragHandleID = RESIZE_PXNY_PICK_ID; }
				else if (_dragHandleID == RESIZE_PXNY_PICK_ID) { _dragHandleID = RESIZE_PXPY_PICK_ID; }
				else if (_dragHandleID == RESIZE_NXPY_PICK_ID) { _dragHandleID = RESIZE_NXNY_PICK_ID; }
				else if (_dragHandleID == RESIZE_NXNY_PICK_ID) { _dragHandleID = RESIZE_NXPY_PICK_ID; }
			}

			Vec4d oldFixed = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			oldFixed.mult4(transMat, fixedPoint);
			_selectedEntity.setSize(scale);
			transMat = _selectedEntity.getTransMatrix(_simTime); // Get the new matrix

			Vec4d newFixed = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			newFixed.mult4(transMat, fixedPoint);

			Vec4d posAdjust = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			posAdjust.sub3(oldFixed, newFixed);

			pos.add3(posAdjust);
			_selectedEntity.setGlobalPosition(pos);

			Vec3d vec = _selectedEntity.getSize();
			InputAgent.processEntity_Keyword_Value(_selectedEntity, "Size", String.format( "%.6f %.6f %.6f %s", vec.x, vec.y, vec.z, "m" ));
			FrameBox.valueUpdate();
			return true;
		}

		if (_dragHandleID == ROTATE_PICK_ID) {

			Vec3d align = _selectedEntity.getAlignment();

			Vec4d rotateCenter = new Vec4d(align.x, align.y, align.z, 1.0d);
			rotateCenter.mult4(transMat, rotateCenter);

			Vec4d a = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			a.sub3(lastPoint, rotateCenter);
			Vec4d b = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			b.sub3(currentPoint, rotateCenter);

			Vec4d aCrossB = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			aCrossB.cross3(a, b);

			double sinTheta = aCrossB.z / a.mag3() / b.mag3();
			double theta = Math.asin(sinTheta);

			Vec3d orient = _selectedEntity.getOrientation();
			orient.z += theta;
			InputAgent.processEntity_Keyword_Value(_selectedEntity, "Orientation", String.format("%f %f %f rad", orient.x, orient.y, orient.z));
			FrameBox.valueUpdate();
			return true;
		}
		if (_dragHandleID == LINEDRAG_PICK_ID) {
			// Dragging a line object

			if (dragInfo.shiftDown()) {
				ArrayList<Vec3d> screenPoints = null;
				if (_selectedEntity instanceof HasScreenPoints)
					screenPoints = ((HasScreenPoints)_selectedEntity).getScreenPoints();
				if (screenPoints == null || screenPoints.size() == 0) return true; // just ignore this
				// Find the geometric median of the points
				Vec4d medPoint = RenderUtils.getGeometricMedian(screenPoints);

				double zDiff = getZDiff(medPoint, currentRay, lastRay);
				_selectedEntity.dragged(new Vec3d(0, 0, zDiff));
				return true;
			}

			Region reg = _selectedEntity.getCurrentRegion();
			Transform regionInvTrans = new Transform();
			if (reg != null) {
				regionInvTrans = reg.getRegionTrans(0.0d);
				regionInvTrans.inverse(regionInvTrans);
			}
			Vec4d localDelta = new Vec4d(0.0d, 0.0d, 0.0d, 1.0d);
			regionInvTrans.apply(delta, localDelta);

			_selectedEntity.dragged(localDelta);
			return true;
		}

		if (_dragHandleID <= LINENODE_PICK_ID) {
			int nodeIndex = (int)(-1*(_dragHandleID - LINENODE_PICK_ID));
			ArrayList<Vec3d> screenPoints = null;
			if (_selectedEntity instanceof HasScreenPoints)
				screenPoints = ((HasScreenPoints)_selectedEntity).getScreenPoints();

			// Note: screenPoints is not a defensive copy, but we'll put it back into itself
			// in a second so everything should be safe
			if (screenPoints == null || nodeIndex >= screenPoints.size()) {
				// huh?
				return false;
			}
			Vec3d point = screenPoints.get(nodeIndex);

			if (dragInfo.shiftDown()) {
				double zDiff = getZDiff(new Vec4d(point.x, point.y, point.z, 1.0d), currentRay, lastRay);
				point.z += zDiff;
			} else {
				Plane pointPlane = new Plane(Vec4d.Z_AXIS, point.z);
				Vec4d diff = RenderUtils.getPlaneCollisionDiff(pointPlane, currentRay, lastRay);
				point.x += diff.x;
				point.y += diff.y;
				point.z += 0;
			}

			Input<?> pointsInput = _selectedEntity.getInput("Points");
			assert(pointsInput != null);
			if (pointsInput == null) {
				_selectedEntity.setGraphicsDataDirty();
				return true;
			}

			StringBuilder sb = new StringBuilder();
			String pointFormatter = " { %.3f %.3f %.3f m }";
			if (pointsInput.getUnits() == "")
				pointFormatter = " { %.3f %.3f %.3f }";

			for(Vec3d pt : screenPoints) {
				sb.append(String.format(pointFormatter, pt.x, pt.y, pt.z));
			}

			InputAgent.processEntity_Keyword_Value(_selectedEntity, pointsInput, sb.toString());
			FrameBox.valueUpdate();
			_selectedEntity.setGraphicsDataDirty();
			return true;
		}

		return false;
	}

	/**
	 * Get the difference in Z height from projecting the two rays onto the vertical plane
	 * defined at the provided centerPoint
	 * @param centerPoint
	 * @param currentRay
	 * @param lastRay
	 * @return
	 */
	private double getZDiff(Vec4d centerPoint, Ray currentRay, Ray lastRay) {

		// Create a plane, orthogonal to the camera, but parallel to the Z axis
		Vec4d normal = currentRay.getDirRef();

		double planeDist = centerPoint.dot3(normal);

		Plane plane = new Plane(normal, planeDist);

		double currentDist = plane.collisionDist(currentRay);
		double lastDist = plane.collisionDist(lastRay);

		if (currentDist < 0 || currentDist == Double.POSITIVE_INFINITY ||
		       lastDist < 0 ||    lastDist == Double.POSITIVE_INFINITY)
		{
			// The plane is parallel or behind one of the rays...
			return 0; // Just ignore it for now...
		}

		// The points where the previous pick ended and current position. Collision is with the entity's XY plane
		Vec4d currentPoint = currentRay.getPointAtDist(currentDist);
		Vec4d lastPoint = lastRay.getPointAtDist(lastDist);

		return currentPoint.z - lastPoint.z;
	}

	private void splitLineEntity(int windowID, int x, int y) {
		Ray currentRay = getRayForMouse(windowID, x, y);

		Mat4d rayMatrix = MathUtils.RaySpace(currentRay);

		HasScreenPoints hsp = (HasScreenPoints)_selectedEntity;
		assert(hsp != null);

		ArrayList<Vec3d> points = hsp.getScreenPoints();

		int splitInd = 0;
		Vec4d nearPoint = null;
		// Find a line segment we are near
		for (;splitInd < points.size() - 1; ++splitInd) {
			Vec4d a = new Vec4d(points.get(splitInd  ).x, points.get(splitInd  ).y, points.get(splitInd  ).z, 1.0d);
			Vec4d b = new Vec4d(points.get(splitInd+1).x, points.get(splitInd+1).y, points.get(splitInd+1).z, 1.0d);

			nearPoint = RenderUtils.rayClosePoint(rayMatrix, a, b);

			double rayAngle = RenderUtils.angleToRay(rayMatrix, nearPoint);

			if (rayAngle > 0 && rayAngle < 0.01309) { // 0.75 degrees in radians
				break;
			}
		}

		if (splitInd == points.size() - 1) {
			// No appropriate point was found
			return;
		}

		// If we are here, we have a segment to split, at index i

		StringBuilder sb = new StringBuilder();
		String pointFormatter = " { %.3f %.3f %.3f m }";

		for(int i = 0; i <= splitInd; ++i) {
			Vec3d pt = points.get(i);
			sb.append(String.format(pointFormatter, pt.x, pt.y, pt.z));
		}

		sb.append(String.format(pointFormatter, nearPoint.x, nearPoint.y, nearPoint.z));

		for (int i = splitInd+1; i < points.size(); ++i) {
			Vec3d pt = points.get(i);
			sb.append(String.format(pointFormatter, pt.x, pt.y, pt.z));
		}

		Input<?> pointsInput = _selectedEntity.getInput("Points");
		InputAgent.processEntity_Keyword_Value(_selectedEntity, pointsInput, sb.toString());
		FrameBox.valueUpdate();
		_selectedEntity.setGraphicsDataDirty();
	}

	private void removeLineNode(int windowID, int x, int y) {
		Ray currentRay = getRayForMouse(windowID, x, y);

		Mat4d rayMatrix = MathUtils.RaySpace(currentRay);

		HasScreenPoints hsp = (HasScreenPoints)_selectedEntity;
		assert(hsp != null);

		ArrayList<Vec3d> points = hsp.getScreenPoints();
		// Find a point that is within the threshold

		if (points.size() <= 2) {
			return;
		}

		int removeInd = 0;
		// Find a line segment we are near
		for ( ;removeInd < points.size(); ++removeInd) {
			Vec4d p = new Vec4d(points.get(removeInd).x, points.get(removeInd).y, points.get(removeInd).z, 1.0d);

			double rayAngle = RenderUtils.angleToRay(rayMatrix, p);

			if (rayAngle > 0 && rayAngle < 0.01309) { // 0.75 degrees in radians
				break;
			}

			if (removeInd == points.size()) {
				// No appropriate point was found
				return;
			}
		}

		StringBuilder sb = new StringBuilder();
		String pointFormatter = " { %.3f %.3f %.3f m }";

		for(int i = 0; i < points.size(); ++i) {
			if (i == removeInd) {
				continue;
			}

			Vec3d pt = points.get(i);
			sb.append(String.format(pointFormatter, pt.x, pt.y, pt.z));
		}

		Input<?> pointsInput = _selectedEntity.getInput("Points");
		InputAgent.processEntity_Keyword_Value(_selectedEntity, pointsInput, sb.toString());
		FrameBox.valueUpdate();
		_selectedEntity.setGraphicsDataDirty();
	}



	private boolean isMouseHandleID(long id) {
		return (id < 0); // For now all negative IDs are mouse handles, this may change
	}

	public boolean handleMouseButton(int windowID, int x, int y, int button, boolean isDown, int modifiers) {

		if (button != 1) { return false; }
		if (!isDown) {
			// Click released
			_isDragging = false;
			return true; // handled
		}

		if ((modifiers & WindowInteractionListener.MOD_CTRL) != 0) {
			// Check if we can split a line segment
			_isDragging = false;
			if (_selectedEntity != null && _selectedEntity instanceof HasScreenPoints) {
				if ((modifiers & WindowInteractionListener.MOD_SHIFT) != 0) {
					removeLineNode(windowID, x, y);
				} else {
					splitLineEntity(windowID, x, y);
				}
				return true;
			}
			return false;
		}

		Ray pickRay = getRayForMouse(windowID, x, y);

		View view = _windowToViewMap.get(windowID);
		if (view == null) {
			return false;
		}

		List<PickData> picks = pickForRay(pickRay, view.getID());

		Collections.sort(picks, new HandleSorter());

		if (picks.size() == 0) {
			return false;
		}

		// See if we are hovering over any interaction handles
		for (PickData pd : picks) {
			if (isMouseHandleID(pd.id)) {
				// this is a mouse handle, remember the handle for future drag events
				_isDragging = true;
				_dragHandleID = pd.id;
				return true;
			}
		}
		return false;
	}

	public void clearSelection() {
		_selectedEntity = null;
		_isDragging = false;

	}

	public void hideExistingPopups() {
		synchronized (_popupLock) {
			if (_lastPopup == null) {
				return;
			}

			_lastPopup.setVisible(false);
			_lastPopup = null;
		}
	}

	public boolean isDragAndDropping() {
		// This is such a brutal hack to work around newt's lack of drag and drop support
		// Claim we are still dragging for up to 10ms after the last drop failed...
		long currTime = System.nanoTime();
		return _dndObjectType != null &&
		       ((currTime - _dndDropTime) < 100000000); // Did the last 'drop' happen less than 100 ms ago?
	}

	public void startDragAndDrop(ObjectType ot) {
		_dndObjectType = ot;
	}

	public void mouseMoved(int windowID, int x, int y) {
		Ray currentRay = getRayForMouse(windowID, x, y);
		double dist = Plane.XY_PLANE.collisionDist(currentRay);

		if (dist == Double.POSITIVE_INFINITY) {
			// I dunno...
			return;
		}

		Vec4d xyPlanePoint = currentRay.getPointAtDist(dist);
		Vec3d tempPoint = new Vec3d(xyPlanePoint.x, xyPlanePoint.y, xyPlanePoint.z);
		GUIFrame.instance().showLocatorPosition(tempPoint);
		queueRedraw();
	}


	public void createDNDObject(int windowID, int x, int y) {
		Ray currentRay = getRayForMouse(windowID, x, y);
		double dist = Plane.XY_PLANE.collisionDist(currentRay);

		if (dist == Double.POSITIVE_INFINITY) {
			// Unfortunate...
			return;
		}

		Vec4d creationPoint = currentRay.getPointAtDist(dist);

		// Create a new instance
		Class<? extends Entity> proto  = _dndObjectType.getJavaClass();
		String name = proto.getSimpleName();
		Entity ent = InputAgent.defineEntityWithUniqueName(proto, name, true);

		// We are no longer drag-and-dropping
		_dndObjectType = null;
		FrameBox.setSelectedEntity(ent);

		if (!(ent instanceof DisplayEntity)) {
			// This object is not a display entity, so the rest of this method does not apply
			return;
		}

		DisplayEntity dEntity  = (DisplayEntity) ent;

		try {
			dEntity.dragged(creationPoint);
		}
		catch (InputErrorException e) {}

		boolean isFlat = false;

		// Shudder....
		ArrayList<DisplayModel> displayModels = dEntity.getDisplayModelList();
		if (displayModels != null && displayModels.size() > 0) {
			DisplayModel dm0 = displayModels.get(0);
			if (dm0 instanceof DisplayModelCompat || dm0 instanceof ImageModel)
				isFlat = true;
		}
		if (dEntity instanceof HasScreenPoints) {
			isFlat = true;
		}
		if (dEntity instanceof Graph) {
			isFlat = true;
		}

		if (isFlat) {
			Vec3d size = dEntity.getSize();
			String sizeString = String.format("%.3f %.3f 0.0 m", size.x, size.y);
			InputAgent.processEntity_Keyword_Value(dEntity, "Size", sizeString);
		} else {
			InputAgent.processEntity_Keyword_Value(dEntity, "Alignment", "0.0 0.0 -0.5");
		}
		FrameBox.valueUpdate();
	}

	@Override
	public void dragDropEnd(DragSourceDropEvent arg0) {
		// Clear the dragging flag
		_dndDropTime = System.nanoTime();
	}

	@Override
	public void dragEnter(DragSourceDragEvent arg0) {}

	@Override
	public void dragExit(DragSourceEvent arg0) {}

	@Override
	public void dragOver(DragSourceDragEvent arg0) {}

	@Override
	public void dropActionChanged(DragSourceDragEvent arg0) {}

	public Vec4d getMeshSize(String shapeString) {

		//TODO: work on meshes that have not been preloaded
		MeshProtoKey key = ColladaModel.getCachedMeshKey(shapeString);
		if (key == null) {
			// Not loaded or bad mesh
			return Vec4d.ONES;
		}
		AABB bounds = getMeshBounds(key, true);

		return bounds.getRadius();
	}

	public AABB getMeshBounds(MeshProtoKey key, boolean block) {
		AABB cachedBounds = _renderer.getProtoBounds(key);
		if (cachedBounds == null) {
			// This has not been loaded yet, queue the renderer to load the asset
			_renderer.loadAsset(key);
			if (!block) {
				return null;
			}

			// Block here until the
			Object notifier = _renderer.getProtoBoundsLock();
			while (cachedBounds == null) {
				synchronized(notifier) {
					try {
						notifier.wait();
					} catch (InterruptedException e) {

					}
				}
				cachedBounds = _renderer.getProtoBounds(key);
			}
		}
		return cachedBounds;
	}

	/**
	 * Set the current windows camera to an isometric view
	 */
	public void setIsometricView() {
		CameraControl control = _windowControls.get(_activeWindowID);
		if (control == null) return;

		// The constant is acos(1/sqrt(3))
		control.setRotationAngles(0.955316, Math.PI/4);
	}

	/**
	 * Set the current windows camera to an XY plane view
	 */
	public void setXYPlaneView() {
		CameraControl control = _windowControls.get(_activeWindowID);
		if (control == null) return;

		control.setRotationAngles(0.0, 0.0);
	}

	public ArrayList<Integer> getOpenWindowIDs() {
		return _renderer.getOpenWindowIDs();
	}

	public String getWindowName(int windowID) {
		return _renderer.getWindowName(windowID);
	}

	public void focusWindow(int windowID) {
		_renderer.focusWindow(windowID);
	}

	/**
	 * Queue up an off screen rendering, this simply passes the call directly to the renderer
	 * @param scene
	 * @param camInfo
	 * @param width
	 * @param height
	 * @return
	 */
	public Future<BufferedImage> renderOffscreen(ArrayList<RenderProxy> scene, CameraInfo camInfo, int viewID,
	                                   int width, int height, Runnable runWhenDone) {
		return _renderer.renderOffscreen(scene, viewID, camInfo, width, height, runWhenDone, null);
	}

	/**
	 * Return a FutureImage of the equivalent screen renderer from the given position looking at the given center
	 * @param cameraPos
	 * @param viewCenter
	 * @param width - width of returned image
	 * @param height - height of returned image
	 * @param target - optional target to prevent re-allocating GPU resources
	 * @return
	 */
	public Future<BufferedImage> renderScreenShot(Vec3d cameraPos, Vec3d viewCenter, int viewID,
	                                              int width, int height, OffscreenTarget target) {

		Vec3d viewDiff = new Vec3d();
		viewDiff.sub3(cameraPos, viewCenter);

		double rotZ = Math.atan2(viewDiff.x, -viewDiff.y);

		double xyDist = Math.hypot(viewDiff.x, viewDiff.y);

		double rotX = Math.atan2(xyDist, viewDiff.z);

		if (Math.abs(rotX) < 0.005) {
			rotZ = 0; // Don't rotate if we are looking straight up or down
		}

		double viewDist = viewDiff.mag3();

		Quaternion rot = Quaternion.Rotation(rotZ, Vec4d.Z_AXIS);
		rot.mult(rot, Quaternion.Rotation(rotX, Vec4d.X_AXIS));

		Transform trans = new Transform(cameraPos, rot, 1);

		CameraInfo camInfo = new CameraInfo(Math.PI/3, viewDist*0.1, viewDist*10, trans);

		return _renderer.renderOffscreen(_cachedScene, viewID, camInfo, width, height, null, target);
	}

	public Future<BufferedImage> getPreviewForDisplayModel(DisplayModel dm, Runnable notifier) {
		return _previewCache.getPreview(dm, notifier);
	}

	public OffscreenTarget createOffscreenTarget(int width, int height) {
		return _renderer.createOffscreenTarget(width, height);
	}

	public void freeOffscreenTarget(OffscreenTarget target) {
		_renderer.freeOffscreenTarget(target);
	}

	public void resetRecorder(ArrayList<View> views, int width, int height, String filePrefix, int numFrames, boolean saveImages, boolean saveVideo, Color4d bgColor) {
		if (_recorder != null) {
			_recorder.freeResources();
		}

		_recorder = new VideoRecorder(views, filePrefix, width, height, numFrames, saveImages, saveVideo, bgColor);
	}

	public void endRecording() {
		_recorder.freeResources();
		_recorder = null;
	}

	private void takeScreenShot() {

		if (_recorder != null)
			_recorder.sample();

		synchronized(_screenshot) {
			_screenshot.set(false);
			_screenshot.notifyAll();
		}
	}

	public void blockOnScreenShot() {
		assert(!_screenshot.get());

		synchronized (_screenshot) {
			_screenshot.set(true);
			queueRedraw();
			while (_screenshot.get()) {
				try {
					_screenshot.wait();
				} catch (InterruptedException ex) {}
			}
		}
	}

	public void shutdown() {
		_finished.set(true);
		if (_renderer != null) {
			_renderer.shutdown();
		}
	}
}
