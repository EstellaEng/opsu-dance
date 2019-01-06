// Copyright 2019 yugecin - this source is licensed under GPL
// see the LICENSE file for more details
package yugecin.opsudance.ui.nodelist;

import static itdelatrisu.opsu.Utils.clamp;
import static itdelatrisu.opsu.ui.animations.AnimationEquation.*;

/**
 * main formula from {@link itdelatrisu.opsu.ui.KineticScrolling}
 */
class Scrolling
{
	private float max;

	private boolean lastDirection;
	private long lastOffsetTime;

	float position;
	float positionNorm;
	float scrollProgress;

	private float target, amplitude;
	private int totalDelta;
	private static final int TIME_CONST = 200;

	void setMax(float max)
	{
		this.max = max;
	}

	void addOffset(float offset)
	{
		final long time = System.currentTimeMillis();
		final boolean newDirection = offset > 0;
		if (newDirection ^ this.lastDirection) {
			this.lastDirection = newDirection;
			this.target = this.position + offset;
			this.lastOffsetTime = 0l;
		} else {
			if (time - lastOffsetTime < 75) {
				// boost is only actually intended for mouse wheel invocations,
				// but updates this fast are pretty much only possible when using
				// the mousewheel, soooo...
				final float boost = (1f - ((time - lastOffsetTime) / 75f));
				offset *= 1f + IN_CIRC.calc(boost) * 7.5f;
			}
			this.target += offset;
			this.lastOffsetTime = time;
		}
		this.totalDelta = 0;
		this.amplitude = this.target - this.position;
	}

	void setPosition(float position)
	{
		this.target = position;
		this.position = target;
		this.amplitude = 0f;
	}

	 void scrollToPosition(float position)
	 {
		this.amplitude = position - this.position;
		this.target = position;
		this.totalDelta = 0;
	}

	void update(int delta)
	{
		final float progress = (float) (this.totalDelta += delta) / TIME_CONST;
		this.position = clamp(
			this.target + (float) (-this.amplitude * Math.exp(-progress)),
			0f,
			this.max
		);
		this.positionNorm = this.position / this.max;
	}
}