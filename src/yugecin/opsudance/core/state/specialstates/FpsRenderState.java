/*
 * opsu!dance - fork of opsu! with cursordance auto
 * Copyright (C) 2017-2018 yugecin
 *
 * opsu!dance is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * opsu!dance is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with opsu!dance.  If not, see <http://www.gnu.org/licenses/>.
 */
package yugecin.opsudance.core.state.specialstates;

import itdelatrisu.opsu.ui.Fonts;
import org.newdawn.slick.Color;
import org.newdawn.slick.Graphics;
import yugecin.opsudance.events.ResolutionChangedListener;
import yugecin.opsudance.utils.FPSMeter;

import static yugecin.opsudance.options.Options.*;
import static yugecin.opsudance.core.InstanceContainer.*;

public class FpsRenderState implements ResolutionChangedListener {

	private final static Color GREEN = new Color(171, 218, 25);
	private final static Color ORANGE = new Color(255, 204, 34);
	private final static Color DARKORANGE = new Color(255, 149, 24);

	private final FPSMeter fpsMeter;
	private final FPSMeter upsMeter;

	private int x;
	private int y;
	private int singleHeight;

	public FpsRenderState() {
		fpsMeter = new FPSMeter(10);
		upsMeter = new FPSMeter(10);
	}

	public void update() {
		upsMeter.update(displayContainer.delta);
	}

	public void render(Graphics g) {
		fpsMeter.update(displayContainer.renderDelta);
		if (!OPTION_SHOW_FPS.state) {
			return;
		}
		int x = this.x;
		int fpsDeviation = displayContainer.delta % displayContainer.targetRenderInterval;
		x = drawText(g, getColor((int) (targetFPS[targetFPSIndex] * 0.9f) - fpsDeviation, fpsMeter.getValue()), getText(fpsMeter.getValue(), "fps"), x, this.y);
		drawText(g, getColor((int) (OPTION_TARGET_UPS.val * 0.9f), upsMeter.getValue()), getText(upsMeter.getValue(), "ups"), x, this.y);
	}

	private String getText(int value, String unit) {
		if (OPTION_USE_FPS_DELTAS.state) {
			return String.format("%.2fms", 1000f / value);
		}
		return value + " " + unit;
	}

	private Color getColor(int targetValue, int realValue) {
		if (realValue >= targetValue) {
			return GREEN;
		}
		if (realValue >= targetValue * 0.85f) {
			return ORANGE;
		}
		return DARKORANGE;
	}

	/**
	 * @return x position where the next block can be drawn (right aligned)
	 */
	private int drawText(Graphics g, Color color, String text, int x, int y) {
		int width = Fonts.SMALL.getWidth(text) + 10;
		g.setColor(color);
		g.fillRoundRect(x - width, y, width, singleHeight + 6, 5, 25);
		Fonts.SMALL.drawString(x - width + 3, y + 3, text, Color.black);
		return x - width - 6;
	}

	@Override
	public void onResolutionChanged(int w, int h) {
		singleHeight = Fonts.SMALL.getLineHeight();
		x = width - 3;
		y = height - 3 - singleHeight - 10;
	}

}
