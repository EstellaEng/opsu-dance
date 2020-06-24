/*
 * opsu! - an open-source osu! client
 * Copyright (C) 2014, 2015 Jeffrey Han
 *
 * opsu! is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu! is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!.  If not, see <http://www.gnu.org/licenses/>.
 */

package itdelatrisu.opsu.objects;

import itdelatrisu.opsu.objects.curves.Vec2f;

import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import yugecin.opsudance.ObjectColorOverrides;

/**
 * Interface for hit object types used during gameplay.
 */
public abstract class GameObject {

	public Vec2f start;
	public Vec2f end;

	private int time;

	private float hue;

	public void updateStartEndPositions(int startTime) {
		time = startTime;
		start = getPointAt(startTime);
		end = getPointAt(getEndTime());
	}

	/**
	 * Draws the hit object to the graphics context.
	 * @param g the graphics context
	 * @param trackPosition the current track position
	 */
	public abstract void draw(Graphics g, int trackPosition, float mirrorAngle);

	/**
	 * Updates the hit object.
	 * @param overlap true if the next object's start time has already passed
	 * @param delta the delta interval since the last call
	 * @param mouseX the x coordinate of the mouse
	 * @param mouseY the y coordinate of the mouse
	 * @param keyPressed whether or not a game key is currently pressed
	 * @param trackPosition the track position
	 * @return true if object ended
	 */
	public abstract boolean update(boolean overlap, int delta, int mouseX, int mouseY, boolean keyPressed, int trackPosition);

	/**
	 * Processes a mouse click.
	 * @param x the x coordinate of the mouse
	 * @param y the y coordinate of the mouse
	 * @param trackPosition the track position
	 * @return true if a hit result was processed
	 */
	public abstract boolean mousePressed(int x, int y, int trackPosition);

	/**
	 * Returns the coordinates of the hit object at a given track position.
	 * @param trackPosition the track position
	 * @return the position vector
	 */
	public abstract Vec2f getPointAt(int trackPosition);

	public int getTime() {
		return time;
	}

	/**
	 * Returns the end time of the hit object.
	 * @return the end time, in milliseconds
	 */
	public abstract int getEndTime();

	/**
	 * Updates the position of the hit object.
	 */
	public abstract void updatePosition();

	/**
	 * Resets all internal state so that the hit object can be reused.
	 */
	public abstract void reset();

	public abstract boolean isCircle();
	public abstract boolean isSlider();
	public abstract boolean isSpinner();

	public abstract Color getColor();
	public abstract Color getMirroredColor();


	public void updateColor() {
		hue = ObjectColorOverrides.hue;
	}

	// the hue of objectcoloroverrides at the point of this circle..... i know, shit placement... used for sb when hue inc changes / rainbow override
	public float getHue() {
		return hue;
	}

}
