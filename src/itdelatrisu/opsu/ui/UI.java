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

package itdelatrisu.opsu.ui;

import itdelatrisu.opsu.GameImage;
import itdelatrisu.opsu.Utils;
import itdelatrisu.opsu.audio.SoundController;
import itdelatrisu.opsu.beatmap.BeatmapParser;
import itdelatrisu.opsu.ui.animations.AnimatedValue;
import itdelatrisu.opsu.ui.animations.AnimationEquation;

import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import yugecin.opsudance.core.DisplayContainer;
import yugecin.opsudance.ui.BackButton;

import static yugecin.opsudance.options.Options.*;
import static yugecin.opsudance.core.InstanceContainer.*;

/**
 * Draws common UI components.
 */
public class UI {

	/** Back button. */
	private static BackButton backButton;

	/** Time to show volume image, in milliseconds. */
	private static final int VOLUME_DISPLAY_TIME = 1500;

	/** Volume display elapsed time. */
	private static int volumeDisplay = -1;

	/** The current tooltip. */
	private static String tooltip;

	/** Whether or not to check the current tooltip for line breaks. */
	private static boolean tooltipNewlines;

	/** The alpha level of the current tooltip (if any). */
	private static AnimatedValue tooltipAlpha = new AnimatedValue(200, 0f, 1f, AnimationEquation.LINEAR);

	// game-related variables
	private static DisplayContainer displayContainer;

	// This class should not be instantiated.
	private UI() {}

	/**
	 * Initializes UI data.
	 */
	public static void init(DisplayContainer displayContainer) {
		UI.displayContainer = displayContainer;
		backButton = new BackButton(displayContainer);
	}

	/**
	 * Updates all UI components by a delta interval.
	 * @param delta the delta interval since the last call.
	 */
	public static void update(int delta) {
		updateVolumeDisplay(delta);
		tooltipAlpha.update(-delta);
	}

	/**
	 * Draws the global UI components: cursor, FPS, volume bar, tooltips, bar notifications.
	 * @param g the graphics context
	 */
	public static void draw(Graphics g) {
		drawVolume(g);
	}

	/**
	 * Resets the necessary UI components upon entering a state.
	 */
	public static void enter() {
		backButton.resetHover();
		resetTooltip();
	}

	/**
	 * Returns the 'menu-back' MenuButton.
	 */
	public static BackButton getBackButton() { return backButton; }

	/**
	 * Draws a tab image and text centered at a location.
	 * @param x the center x coordinate
	 * @param y the center y coordinate
	 * @param text the text to draw inside the tab
	 * @param selected whether the tab is selected (white) or not (red)
	 * @param isHover whether to include a hover effect (unselected only)
	 */
	public static void drawTab(float x, float y, String text, boolean selected, boolean isHover) {
		Image tabImage = GameImage.MENU_TAB.getImage();
		float tabTextX = x - (Fonts.MEDIUM.getWidth(text) / 2);
		float tabTextY = y - (tabImage.getHeight() / 2);
		Color filter, textColor;
		if (selected) {
			filter = Color.white;
			textColor = Color.black;
		} else {
			filter = (isHover) ? Colors.RED_HOVER : Color.red;
			textColor = Color.white;
		}
		tabImage.drawCentered(x, y, filter);
		Fonts.MEDIUM.drawString(tabTextX, tabTextY, text, textColor);
	}

	/**
	 * Draws the volume bar on the middle right-hand side of the game container.
	 * Only draws if the volume has recently been changed using with {@link #changeVolume(int)}.
	 * @param g the graphics context
	 */
	public static void drawVolume(Graphics g) {
		if (volumeDisplay == -1)
			return;

		Image img = GameImage.VOLUME.getImage();

		// move image in/out
		float xOffset = 0;
		float ratio = (float) volumeDisplay / VOLUME_DISPLAY_TIME;
		if (ratio <= 0.1f)
			xOffset = img.getWidth() * (1 - (ratio * 10f));
		else if (ratio >= 0.9f)
			xOffset = img.getWidth() * (1 - ((1 - ratio) * 10f));

		img.drawCentered(displayContainer.width - img.getWidth() / 2f + xOffset, displayContainer.height / 2f);
		float barHeight = img.getHeight() * 0.9f;
		float volume = OPTION_MASTER_VOLUME.val / 100f;
		g.setColor(Color.white);
		g.fillRoundRect(
				displayContainer.width - (img.getWidth() * 0.368f) + xOffset,
				(displayContainer.height / 2f) - (img.getHeight() * 0.47f) + (barHeight * (1 - volume)),
				img.getWidth() * 0.15f, barHeight * volume, 3
		);
	}

	/**
	 * Updates volume display by a delta interval.
	 * @param delta the delta interval since the last call
	 */
	private static void updateVolumeDisplay(int delta) {
		if (volumeDisplay == -1)
			return;

		volumeDisplay += delta;
		if (volumeDisplay > VOLUME_DISPLAY_TIME)
			volumeDisplay = -1;
	}

	/**
	 * Changes the master volume by a unit (positive or negative).
	 * @param units the number of units
	 */
	public static void changeVolume(int units) {
		final float UNIT_OFFSET = 0.05f;
		float volume = Utils.clamp(OPTION_MASTER_VOLUME.val / 100f + (UNIT_OFFSET * units), 0f, 1f);
		OPTION_MASTER_VOLUME.setValue((int) (volume * 100f));
		if (volumeDisplay == -1)
			volumeDisplay = 0;
		else if (volumeDisplay >= VOLUME_DISPLAY_TIME / 10)
			volumeDisplay = VOLUME_DISPLAY_TIME / 10;
	}

	/**
	 * Draws loading progress (OSZ unpacking, beatmap parsing, replay importing, sound loading)
	 * at the bottom of the screen.
	 * @param g the graphics context
	 */
	public static void drawLoadingProgress(Graphics g) {
		String text, file;
		int progress;

		// determine current action
		if ((file = oszunpacker.getCurrentFileName()) != null) {
			text = "Unpacking new beatmaps...";
			progress = oszunpacker.getUnpackerProgress();
		} else if ((file = beatmapParser.getCurrentFileName()) != null) {
			text = (beatmapParser.getStatus() == BeatmapParser.Status.INSERTING) ?
					"Updating database..." : "Loading beatmaps...";
			progress = beatmapParser.getParserProgress();
		} else if ((file = replayImporter.getCurrentFileName()) != null) {
			text = "Importing replays...";
			progress = replayImporter.getLoadingProgress();
		} else if ((file = SoundController.getCurrentFileName()) != null) {
			text = "Loading sounds...";
			progress = SoundController.getLoadingProgress();
		} else
			return;

		// draw loading info
		float marginX = displayContainer.width * 0.02f, marginY = displayContainer.height * 0.02f;
		float lineY = displayContainer.height - marginY;
		int lineOffsetY = Fonts.MEDIUM.getLineHeight();
		if (OPTION_LOAD_VERBOSE.state) {
			// verbose: display percentages and file names
			Fonts.MEDIUM.drawString(
					marginX, lineY - (lineOffsetY * 2),
					String.format("%s (%d%%)", text, progress), Color.white);
			Fonts.MEDIUM.drawString(marginX, lineY - lineOffsetY, file, Color.white);
		} else {
			// draw loading bar
			Fonts.MEDIUM.drawString(marginX, lineY - (lineOffsetY * 2), text, Color.white);
			g.setColor(Color.white);
			g.fillRoundRect(marginX, lineY - (lineOffsetY / 2f),
					(displayContainer.width - (marginX * 2f)) * progress / 100f, lineOffsetY / 4f, 4
			);
		}
	}

	/**
	 * Draws a scroll bar.
	 * @param g the graphics context
	 * @param position the position in the virtual area
	 * @param totalLength the total length of the virtual area
	 * @param lengthShown the length of the virtual area shown
	 * @param unitBaseX the base x coordinate
	 * @param unitBaseY the base y coordinate
	 * @param unitWidth the width of a unit
	 * @param scrollAreaHeight the height of the scroll area
	 * @param bgColor the scroll bar area background color (null if none)
	 * @param scrollbarColor the scroll bar color
	 * @param right whether or not to place the scroll bar on the right side of the unit
	 */
	public static void drawScrollbar(
			Graphics g, float position, float totalLength, float lengthShown,
			float unitBaseX, float unitBaseY, float unitWidth, float scrollAreaHeight,
			Color bgColor, Color scrollbarColor, boolean right
	) {
		float scrollbarWidth = displayContainer.width * 0.00347f;
		float scrollbarHeight = scrollAreaHeight * lengthShown / totalLength;
		float offsetY = (scrollAreaHeight - scrollbarHeight) * (position / (totalLength - lengthShown));
		float scrollbarX = unitBaseX + unitWidth - ((right) ? scrollbarWidth : 0);
		if (bgColor != null) {
			g.setColor(bgColor);
			g.fillRect(scrollbarX, unitBaseY, scrollbarWidth, scrollAreaHeight);
		}
		g.setColor(scrollbarColor);
		g.fillRect(scrollbarX, unitBaseY + offsetY, scrollbarWidth, scrollbarHeight);
	}

	/**
	 * Sets or updates a tooltip for drawing.
	 * Must be called with {@link #drawTooltip(Graphics)}.
	 * @param delta the delta interval since the last call
	 * @param s the tooltip text
	 * @param newlines whether to check for line breaks ('\n')
	 */
	public static void updateTooltip(int delta, String s, boolean newlines) {
		if (s != null) {
			tooltip = s;
			tooltipNewlines = newlines;
			tooltipAlpha.update(delta * 2);
		}
	}

	/**
	 * Draws a tooltip, if any, near the current mouse coordinates,
	 * bounded by the container dimensions.
	 * @param g the graphics context
	 */
	public static void drawTooltip(Graphics g) {
		if (tooltipAlpha.getTime() == 0 || tooltip == null)
			return;

		int margin = displayContainer.width / 100, textMarginX = 2;
		int offset = GameImage.CURSOR_MIDDLE.getImage().getWidth() / 2;
		int lineHeight = Fonts.SMALL.getLineHeight();
		int textWidth = textMarginX * 2, textHeight = lineHeight;
		if (tooltipNewlines) {
			String[] lines = tooltip.split("\\n");
			int maxWidth = Fonts.SMALL.getWidth(lines[0]);
			for (int i = 1; i < lines.length; i++) {
				int w = Fonts.SMALL.getWidth(lines[i]);
				if (w > maxWidth)
					maxWidth = w;
			}
			textWidth += maxWidth;
			textHeight += lineHeight * (lines.length - 1);
		} else
			textWidth += Fonts.SMALL.getWidth(tooltip);

		// get drawing coordinates
		int x = displayContainer.mouseX + offset;
		int y = displayContainer.mouseY + offset;
		if (x + textWidth > displayContainer.width - margin)
			x = displayContainer.width - margin - textWidth;
		else if (x < margin)
			x = margin;
		if (y + textHeight > displayContainer.height - margin)
			y = displayContainer.height - margin - textHeight;
		else if (y < margin)
			y = margin;

		// draw tooltip text inside a filled rectangle
		float alpha = tooltipAlpha.getValue();
		float oldAlpha = Colors.BLACK_ALPHA.a;
		Colors.BLACK_ALPHA.a = alpha;
		g.setColor(Colors.BLACK_ALPHA);
		Colors.BLACK_ALPHA.a = oldAlpha;
		g.fillRect(x, y, textWidth, textHeight);
		oldAlpha = Colors.DARK_GRAY.a;
		Colors.DARK_GRAY.a = alpha;
		g.setColor(Colors.DARK_GRAY);
		g.setLineWidth(1);
		g.drawRect(x, y, textWidth, textHeight);
		Colors.DARK_GRAY.a = oldAlpha;
		oldAlpha = Colors.WHITE_ALPHA.a;
		Colors.WHITE_ALPHA.a = alpha;
		Fonts.SMALL.drawString(x + textMarginX, y, tooltip, Colors.WHITE_ALPHA);
		Colors.WHITE_ALPHA.a = oldAlpha;
	}

	/**
	 * Resets the tooltip.
	 */
	public static void resetTooltip() {
		tooltipAlpha.setTime(0);
		tooltip = null;
	}

}
