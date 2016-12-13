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

import itdelatrisu.opsu.GameData;
import itdelatrisu.opsu.GameData.HitObjectType;
import itdelatrisu.opsu.GameImage;
import itdelatrisu.opsu.GameMod;
import itdelatrisu.opsu.Options;
import itdelatrisu.opsu.Utils;
import itdelatrisu.opsu.beatmap.Beatmap;
import itdelatrisu.opsu.beatmap.HitObject;
import itdelatrisu.opsu.objects.curves.Curve;
import itdelatrisu.opsu.objects.curves.Vec2f;
import itdelatrisu.opsu.states.Game;
import itdelatrisu.opsu.ui.Colors;
import itdelatrisu.opsu.ui.animations.AnimationEquation;

import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import yugecin.opsudance.Dancer;

/**
 * Data type representing a slider object.
 */
public class Slider extends GameObject {
	/** Slider ball frames. */
	private static Image[] sliderBallImages;

	/** Slider movement speed multiplier. */
	private static float sliderMultiplier = 1.0f;

	/** Rate at which slider ticks are placed. */
	private static float sliderTickRate = 1.0f;

	/** Follow circle radius. */
	private static float followRadius;

	/** The diameter of hit circles. */
	private static float diameter;

	/** The associated HitObject. */
	private HitObject hitObject;

	/** The scaled starting x, y coordinates. */
	protected float x, y;

	/** The associated Game object. */
	private Game game;

	/** The associated GameData object. */
	private GameData data;

	/** The color of this slider. */
	private Color color;
	private Color mirrorColor;

	/** The underlying Curve. */
	private Curve curve;

	/** The time duration of the slider, in milliseconds. */
	private float sliderTime = 0f;

	/** The time duration of the slider including repeats, in milliseconds. */
	private float sliderTimeTotal = 0f;

	/** Whether or not the result of the initial hit circle has been processed. */
	private boolean sliderClickedInitial = false;

	/** Whether or not the slider was held to the end. */
	private boolean sliderHeldToEnd = false;

	/** Whether or not to show the follow circle. */
	private boolean followCircleActive = false;

	/** Whether or not the slider result ends the combo streak. */
	private boolean comboEnd;

	/** The number of repeats that have passed so far. */
	private int currentRepeats = 0;

	/** The t values of the slider ticks. */
	private float[] ticksT;

	/** The tick index in the ticksT[] array. */
	private int tickIndex = 0;

	/** Number of ticks hit and tick intervals so far. */
	private int ticksHit = 0, tickIntervals = 1;

	/** The current tick time for the follow circle expanding animation. */
	private int tickExpandTime = 0;

	/** The duration of the follow circle expanding animation on ticks. */
	private static final int TICK_EXPAND_TIME = 200;

	/** Container dimensions. */
	private static int containerWidth, containerHeight;

	private int repeats;

	private static Color curveColor = new Color(0, 0, 0, 20);

	public float pixelLength;

	private int comboColorIndex;

	public int baseSliderFrom;

	/**
	 * Initializes the Slider data type with images and dimensions.
	 * @param container the game container
	 * @param circleDiameter the circle diameter
	 * @param beatmap the associated beatmap
	 */
	public static void init(GameContainer container, float circleDiameter, Beatmap beatmap) {
		containerWidth = container.getWidth();
		containerHeight = container.getHeight();

		diameter = circleDiameter * HitObject.getXMultiplier();  // convert from Osupixels (640x480)
		int diameterInt = (int) diameter;

		followRadius = diameter / 2 * 3f;

		// slider ball
		if (GameImage.SLIDER_BALL.hasBeatmapSkinImages() ||
		    (!GameImage.SLIDER_BALL.hasBeatmapSkinImage() && GameImage.SLIDER_BALL.getImages() != null))
			sliderBallImages = GameImage.SLIDER_BALL.getImages();
		else
			sliderBallImages = new Image[]{ GameImage.SLIDER_BALL.getImage() };
		for (int i = 0; i < sliderBallImages.length; i++)
			sliderBallImages[i] = sliderBallImages[i].getScaledCopy(diameterInt, diameterInt);

		GameImage.SLIDER_FOLLOWCIRCLE.setImage(GameImage.SLIDER_FOLLOWCIRCLE.getImage().getScaledCopy(diameterInt * 259 / 128, diameterInt * 259 / 128));
		GameImage.REVERSEARROW.setImage(GameImage.REVERSEARROW.getImage().getScaledCopy(diameterInt, diameterInt));
		GameImage.SLIDER_TICK.setImage(GameImage.SLIDER_TICK.getImage().getScaledCopy(diameterInt / 4, diameterInt / 4));

		sliderMultiplier = beatmap.sliderMultiplier;
		sliderTickRate = beatmap.sliderTickRate;
	}

	/**
	 * Constructor.
	 * @param hitObject the associated HitObject
	 * @param game the associated Game object
	 * @param data the associated GameData object
	 * @param comboColorIndex index of the combo color of this slider
	 * @param comboEnd true if this is the last hit object in the combo
	 */
	public Slider(HitObject hitObject, Game game, GameData data, int comboColorIndex, boolean comboEnd) {
		this.hitObject = hitObject;
		this.game = game;
		this.data = data;
		this.comboEnd = comboEnd;
		this.comboColorIndex = comboColorIndex;
		updateColor();
		updatePosition();

		this.pixelLength = hitObject.getPixelLength();

		// slider time calculations
		this.sliderTime = hitObject.getSliderTime(sliderMultiplier, game.getBeatLength());
		this.sliderTimeTotal = sliderTime * hitObject.getRepeatCount();

		// ticks
		float tickLengthDiv = 100f * sliderMultiplier / sliderTickRate / game.getTimingPointMultiplier();
		int tickCount = (int) Math.ceil(hitObject.getPixelLength() / tickLengthDiv) - 1;
		if (tickCount > 0) {
			this.ticksT = new float[tickCount];
			float tickTOffset = 1f / (tickCount + 1);
			float t = tickTOffset;
			for (int i = 0; i < tickCount; i++, t += tickTOffset)
				ticksT[i] = t;
		}

		repeats = hitObject.getRepeatCount();
	}

	@Override
	public void draw(Graphics g, int trackPosition, boolean mirror) {
		if (trackPosition > getEndTime()) {
			return;
		}
		Color orig = color;
		if (mirror) {
			color = mirrorColor;
		}

		int timeDiff = hitObject.getTime() - trackPosition;
		final int approachTime = game.getApproachTime();
		final int fadeInTime = game.getFadeInTime();
		float scale = timeDiff / (float) approachTime;
		float approachScale = 1 + scale * 3;
		double fadeinScale = (timeDiff - approachTime + fadeInTime) / (double) fadeInTime;
		float alpha = Utils.clamp(1 - (float) fadeinScale, 0, 1);
		float decorationsAlpha = Utils.clamp(-2.0f * (float) fadeinScale, 0, 1);
		boolean overlayAboveNumber = Options.getSkin().isHitCircleOverlayAboveNumber();
		float oldAlpha = Colors.WHITE_FADE.a;
		Colors.WHITE_FADE.a = color.a = alpha;
		Image hitCircleOverlay = GameImage.HITCIRCLE_OVERLAY.getImage();
		Image hitCircle = GameImage.HITCIRCLE.getImage();
		Vec2f endPos = curve.pointAt(1);

		float curveAlpha = 1f;
		if (GameMod.HIDDEN.isActive() && trackPosition > getTime()) {
			curveAlpha = Math.max(0f, 1f - ((float) (trackPosition - getTime()) / (getEndTime() - getTime())) * 1.05f);
		}

		curveColor.a = curveAlpha;
		boolean isCurveCompletelyDrawn = drawSliderTrack(trackPosition, Utils.clamp(1d - fadeinScale, 0d, 1d));
		color.a = alpha;

		g.pushTransform();
		if (mirror) {
			g.rotate(x, y, -180f);
		}

		/*
		// end circle
		Vec2f endCircPos = curve.pointAt(curveInterval);
		hitCircle.drawCentered(endCircPos.x, endCircPos.y, color);
		hitCircleOverlay.drawCentered(endCircPos.x, endCircPos.y, Colors.WHITE_FADE);
		*/

		// start circle, don't draw if already clicked
		if (!sliderClickedInitial) {
			hitCircle.drawCentered(x, y, color);
			if (!overlayAboveNumber)
				hitCircleOverlay.drawCentered(x, y, Colors.WHITE_FADE);
		}

		g.popTransform();

		// ticks
		if (ticksT != null) {
			drawSliderTicks(g, trackPosition, alpha, decorationsAlpha, mirror);
			Colors.WHITE_FADE.a = alpha;
		}

		g.pushTransform();
		if (mirror) {
			g.rotate(x, y, -180f);
		}

		if (GameMod.HIDDEN.isActive()) {
			final int hiddenDecayTime = game.getHiddenDecayTime();
			final int hiddenTimeDiff = game.getHiddenTimeDiff();
			if (fadeinScale <= 0f && timeDiff < hiddenTimeDiff + hiddenDecayTime) {
				float hiddenAlpha = (timeDiff < hiddenTimeDiff) ? 0f : (timeDiff - hiddenTimeDiff) / (float) hiddenDecayTime;
				alpha = Math.min(alpha, hiddenAlpha);
			}
		}

		if (!sliderClickedInitial) {
			data.drawSymbolNumber(hitObject.getComboNumber(), x, y,
				hitCircle.getWidth() * 0.40f / data.getDefaultSymbolImage(0).getHeight(), alpha);
			if (overlayAboveNumber)
				hitCircleOverlay.drawCentered(x, y, Colors.WHITE_FADE);
		}

		g.popTransform();

		// repeats
		if (isCurveCompletelyDrawn) {
			for (int tcurRepeat = currentRepeats; tcurRepeat <= currentRepeats + 1; tcurRepeat++) {
				if (hitObject.getRepeatCount() - 1 > tcurRepeat) {
					Image arrow = GameImage.REVERSEARROW.getImage();
					arrow = arrow.getScaledCopy((float) (1 + 0.2d * ((trackPosition + sliderTime * tcurRepeat) % 292) / 292));
					Color arrowColor = Color.white;
					if (tcurRepeat != currentRepeats) {
						if (sliderTime == 0)
							continue;
						float t = Math.max(getT(trackPosition, true), 0);
						arrow.setAlpha((float) (t - Math.floor(t)));
					} else
						arrow.setAlpha(Options.isSliderSnaking() ? decorationsAlpha : 1f);
					if (tcurRepeat % 2 == 0) {
						// last circle
						arrow.setRotation(curve.getEndAngle());
						arrow.drawCentered(endPos.x, endPos.y, arrowColor);
					} else {
						// first circle
						arrow.setRotation(curve.getStartAngle());
						arrow.drawCentered(x, y, arrowColor);
					}
				}
			}
		}

		if (timeDiff >= 0) {
			// approach circle
			g.pushTransform();
			if (mirror) {
				g.rotate(x, y, -180f);
			}
			if (!GameMod.HIDDEN.isActive() && Options.isDrawApproach()) {
				GameImage.APPROACHCIRCLE.getImage().getScaledCopy(approachScale).drawCentered(x, y, color);
			}
			g.popTransform();
		} else {
			// Since update() might not have run before drawing during a replay, the
			// slider time may not have been calculated, which causes NAN numbers and flicker.
			if (sliderTime == 0)
				return;

			// Don't draw follow ball if already done
			if (trackPosition > hitObject.getTime() + sliderTimeTotal)
				return;

			Vec2f c = curve.pointAt(getT(trackPosition, false));
			Vec2f c2 = curve.pointAt(getT(trackPosition, false) + 0.01f);

			float t = getT(trackPosition, false);
//			float dis = hitObject.getPixelLength() * HitObject.getXMultiplier() * (t - (int) t);
//			Image sliderBallFrame = sliderBallImages[(int) (dis / (diameter * Math.PI) * 30) % sliderBallImages.length];
			Image sliderBallFrame = sliderBallImages[(int) (t * sliderTime * 60 / 1000) % sliderBallImages.length];
			float angle = (float) (Math.atan2(c2.y - c.y, c2.x - c.x) * 180 / Math.PI);
			sliderBallFrame.setRotation(angle);
			if (Options.getSkin().isAllowSliderBallTint()) {
				sliderBallFrame.drawCentered(c.x, c.y, color);
			} else {
				sliderBallFrame.drawCentered(c.x, c.y);
			}

			// follow circle
			if (followCircleActive) {
				float followCircleScale = 1f + (tickExpandTime / (float) TICK_EXPAND_TIME) * 0.1f;
				GameImage.SLIDER_FOLLOWCIRCLE.getImage().getScaledCopy(followCircleScale).drawCentered(c.x, c.y);

				// "flashlight" mod: dim the screen
				if (GameMod.FLASHLIGHT.isActive()) {
					float oldAlphaBlack = Colors.BLACK_ALPHA.a;
					Colors.BLACK_ALPHA.a = 0.75f;
					g.setColor(Colors.BLACK_ALPHA);
					g.fillRect(0, 0, containerWidth, containerHeight);
					Colors.BLACK_ALPHA.a = oldAlphaBlack;
				}
			}
		}

		Colors.WHITE_FADE.a = oldAlpha;

		color = orig;
	}

	/**
	 * Draws slider ticks.
	 * @param g graphics
	 * @param trackPosition the track position
	 * @param curveAlpha the curve alpha level
	 * @param decorationsAlpha the decorations alpha level
	 * @param mirror true to draw mirrored
	 */
	private void drawSliderTicks(Graphics g, int trackPosition, float curveAlpha, float decorationsAlpha, boolean mirror) {
		float tickScale = 0.5f + 0.5f * AnimationEquation.OUT_BACK.calc(decorationsAlpha);
		Image tick = GameImage.SLIDER_TICK.getImage().getScaledCopy(tickScale);

		// calculate which ticks need to be drawn (don't draw if sliderball crossed it)
		int min = 0;
		int max = ticksT.length;
		if (trackPosition > getTime()) {
			for (int i = 0; i < ticksT.length; ) {
				if (((trackPosition - getTime()) % sliderTime) / sliderTime < ticksT[i]) {
					break;
				}
				min = ++i;
			}
		}
		if (currentRepeats % 2 == 1) {
			max -= min;
			min = 0;
		}

		// calculate the tick alpha level
		float sliderTickAlpha;
		if (currentRepeats == 0) {
			sliderTickAlpha = decorationsAlpha;
		} else {
			float t = getT(trackPosition, false);
			if (currentRepeats % 2 == 1) {
				t = 1f - t;
			}
			sliderTickAlpha = Utils.clamp(t * ticksT.length * 2, 0f, 1f);
		}

		// draw ticks
		Colors.WHITE_FADE.a = Math.min(curveAlpha, sliderTickAlpha);
		for (int i = min; i < max; i++) {
			Vec2f c = curve.pointAt(ticksT[i]);
			g.pushTransform();
			if (mirror) {
				g.rotate(c.x, c.y, -180f);
			}
			tick.drawCentered(c.x, c.y, Colors.WHITE_FADE);
			g.popTransform();
		}
	}

	private boolean drawSliderTrack(int trackPosition, double snakingSliderProgress) {
		double curveIntervalTo = Options.isSliderSnaking() ? snakingSliderProgress : 1d;
		double curveIntervalFrom = 0d;
		if (Options.isShrinkingSliders()) {
			double sliderprogress = (trackPosition - getTime() - ((double) sliderTime * (repeats - 1))) / (double) sliderTime;
			if (sliderprogress > 0) {
				curveIntervalFrom = sliderprogress;
			}
		}
		int curvelen = curve.getCurvePoints().length;
		if (Options.isMergingSliders()) {
			if (Options.isShrinkingSliders() && curveIntervalFrom > 0) {
				if (repeats % 2 == 0) {
					game.spliceSliderCurve(baseSliderFrom + (int) ((1d - curveIntervalFrom) * curvelen) - 1, baseSliderFrom + curvelen);
				} else {
					game.setSlidercurveFrom(baseSliderFrom + (int) (curveIntervalFrom * curvelen));
				}
			}
			game.setSlidercurveTo(baseSliderFrom + (int) (curveIntervalTo * curve.getCurvePoints().length));
		} else {
			if (Options.isShrinkingSliders() && curveIntervalFrom > 0 && repeats % 2 == 0) {
				if (Options.isFallbackSliders()) {
					curveIntervalTo = 1d - curveIntervalFrom;
				} else {
					curve.splice((int) ((1d - curveIntervalFrom) * curvelen), curvelen);
				}
				curveIntervalFrom = 0d;
			}
			curve.draw(curveColor, (int) (curveIntervalFrom * curvelen), (int) (curveIntervalTo * curvelen));
		}
		return curveIntervalTo == 1d;
	}

	/**
	 * Calculates the slider hit result.
	 * @return the hit result (GameData.HIT_* constants)
	 */
	private int hitResult() {
		/*
			time     scoredelta score-hit-initial-tick= unaccounted
			(1/4   - 1)		396 - 300 - 30	 		46
			(1+1/4 - 2)		442 - 300 - 30 - 10
			(2+1/4 - 3)		488 - 300 - 30 - 2*10	896 (408)5x
			(3+1/4 - 4)		534 - 300 - 30 - 3*10
			(4+1/4 - 5)		580 - 300 - 30 - 4*10
			(5+1/4 - 6) 	626	- 300 - 30 - 5*10
			(6+1/4 - 7)		672	- 300 - 30 - 6*10

			difficultyMulti = 3	(+36 per combo)

			score =
			(t)ticks(10) * nticks +
			(h)hitValue
			(c)combo (hitValue/25 * difficultyMultiplier*(combo-1))
			(i)initialHit (30) +
			(f)finalHit(30) +

			s     t       h          c     i     f
			626 - 10*5 - 300  - 276(-216 - 30 - 30) (all)(7x)
			240 - 10*5 - 100  - 90 (-60     <- 30>) (no final or initial)(6x)

			218 - 10*4 - 100  - 78 (-36       - 30) (4 tick no initial)(5x)
			196 - 10*3 - 100  - 66 (-24       - 30 ) (3 tick no initial)(4x)
			112 - 10*2 - 50   - 42 (-12       - 30 ) (2 tick no initial)(3x)
			96  - 10   - 50   - 36 ( -6       - 30 ) (1 tick no initial)(2x)

			206 - 10*4 - 100  - 66 (-36       - 30 ) (4 tick no initial)(4x)
			184 - 10*3 - 100  - 54 (-24       - 30 ) (3 tick no initial)(3x)
			90  - 10   - 50   - 30 (          - 30 ) (1 tick no initial)(0x)

			194 - 10*4 - 100  - 54 (-24       - 30 ) (4 tick no initial)(3x)

			170 - 10*4 - 100  - 30 (     - 30      ) (4 tick no final)(0x)
			160 - 10*3 - 100  - 30 (     - 30      ) (3 tick no final)(0x)
			100 - 10*2 - 50   - 30 (     - 30      ) (2 tick no final)(0x)

			198 - 10*5 - 100  - 48 (-36            ) (no initial and final)(5x)
			110        - 50   -    (     - 30 - 30 ) (final and initial no tick)(0x)
			80         - 50   -    (       <- 30>  ) (only final or initial)(0x)

			140 - 10*4 - 100  - 0                    (4 ticks only)(0x)
			80  - 10*3 - 50   - 0                    (3 tick only)(0x)
			70  - 10*2 - 50   - 0                    (2 tick only)(0x)
			60  - 10   - 50   - 0                    (1 tick only)(0x)
		*/
		float tickRatio = (float) ticksHit / tickIntervals;

		int result;
		if (tickRatio >= 1.0f)
			result = GameData.HIT_300;
		else if (tickRatio >= 0.5f)
			result = GameData.HIT_100;
		else if (tickRatio > 0f)
			result = GameData.HIT_50;
		else
			result = GameData.HIT_MISS;

		float cx, cy;
		HitObjectType type;
		if (currentRepeats % 2 == 0) {  // last circle
			Vec2f lastPos = curve.pointAt(1);
			cx = lastPos.x;
			cy = lastPos.y;
			type = HitObjectType.SLIDER_LAST;
		} else {  // first circle
			cx = x;
			cy = y;
			type = HitObjectType.SLIDER_FIRST;
		}
		data.hitResult(hitObject.getTime() + (int) sliderTimeTotal, result,
				cx, cy, color, comboEnd, hitObject, type, sliderHeldToEnd,
				currentRepeats + 1, curve, sliderHeldToEnd);
		if (Options.isMirror() && GameMod.AUTO.isActive()) {
			float[] m = Utils.mirrorPoint(cx, cy);
			data.hitResult(hitObject.getTime() + (int) sliderTimeTotal, result,
				m[0], m[1], mirrorColor, comboEnd, hitObject, type, sliderHeldToEnd,
				currentRepeats + 1, curve, sliderHeldToEnd, false);
		}

		return result;
	}

	@Override
	public boolean mousePressed(int x, int y, int trackPosition) {
		if (sliderClickedInitial)  // first circle already processed
			return false;

		double distance = Math.hypot(this.x - x, this.y - y);
		if (distance < diameter / 2) {
			int timeDiff = Math.abs(trackPosition - hitObject.getTime());
			int[] hitResultOffset = game.getHitResultOffsets();

			int result = -1;
			if (timeDiff < hitResultOffset[GameData.HIT_50]) {
				result = GameData.HIT_SLIDER30;
				ticksHit++;
			} else if (timeDiff < hitResultOffset[GameData.HIT_MISS])
				result = GameData.HIT_MISS;
			//else not a hit

			if (result > -1) {
				data.sendInitialSliderResult(trackPosition, this.x, this.y, color, mirrorColor);
				data.addHitError(hitObject.getTime(), x,y,trackPosition - hitObject.getTime());
				sliderClickedInitial = true;
				data.sliderTickResult(hitObject.getTime(), result, this.x, this.y, hitObject, currentRepeats);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean update(boolean overlap, int delta, int mouseX, int mouseY, boolean keyPressed, int trackPosition) {
		int repeatCount = hitObject.getRepeatCount();
		int[] hitResultOffset = game.getHitResultOffsets();
		boolean isAutoMod = GameMod.AUTO.isActive();

		if (!sliderClickedInitial) {
			int time = hitObject.getTime();

			// start circle time passed
			if (trackPosition > time + hitResultOffset[GameData.HIT_50]) {
				sliderClickedInitial = true;
				if (isAutoMod) {  // "auto" mod: catch any missed notes due to lag
					ticksHit++;
					data.sliderTickResult(time, GameData.HIT_SLIDER30, x, y, hitObject, currentRepeats);
				} else
					data.sliderTickResult(time, GameData.HIT_MISS, x, y, hitObject, currentRepeats);
			}

			// "auto" mod: send a perfect hit result
			else if (isAutoMod) {
				if (Math.abs(trackPosition - time) < hitResultOffset[GameData.HIT_300]) {
					ticksHit++;
					sliderClickedInitial = true;
					data.sliderTickResult(time, GameData.HIT_SLIDER30, x, y, hitObject, currentRepeats);
					data.sendInitialSliderResult(time, x, y, color, mirrorColor);
				}
			}

			// "relax" mod: click automatically
			else if (GameMod.RELAX.isActive() && trackPosition >= time)
				mousePressed(mouseX, mouseY, trackPosition);
		}

		// end of slider
		if (trackPosition > hitObject.getTime() + sliderTimeTotal) {
			tickIntervals++;
			tickExpandTime = TICK_EXPAND_TIME;

			// check if cursor pressed and within end circle
			if (keyPressed || GameMod.RELAX.isActive()) {
				Vec2f c = curve.pointAt(getT(trackPosition, false));
				double distance = Math.hypot(c.x - mouseX, c.y - mouseY);
				if (distance < followRadius)
					sliderHeldToEnd = true;
			}

			// final circle hit
			if (sliderHeldToEnd)
				ticksHit++;

			// "auto" mod: always send a perfect hit result
			if (isAutoMod)
				ticksHit = tickIntervals;

			// calculate and send slider result
			hitResult();
			if (Options.isMergingSliders()) {
				game.setSlidercurveFrom(baseSliderFrom + curve.getCurvePoints().length + 1);
			}
			return true;
		}

		// update tick expand time
		if (tickExpandTime > 0) {
			tickExpandTime -= delta;
			if (tickExpandTime < 0)
				tickExpandTime = 0;
		}

		// repeats
		boolean isNewRepeat = false;
		if (repeatCount - 1 > currentRepeats) {
			float t = getT(trackPosition, true);
			if (Math.floor(t) > currentRepeats) {
				currentRepeats++;
				tickIndex = 0;
				isNewRepeat = true;
				tickExpandTime = TICK_EXPAND_TIME;

				if (Options.isReverseArrowAnimationEnabled()) {
					// send hit result, to fade out reversearrow
					HitObjectType type;
					float posX, posY;
					if (currentRepeats % 2 == 1) {
						type = HitObjectType.SLIDER_LAST;
						Vec2f endPos = curve.pointAt(1);
						posX = endPos.x;
						posY = endPos.y;
					} else {
						type = HitObjectType.SLIDER_FIRST;
						posX = this.x;
						posY = this.y;
					}
					data.sendRepeatSliderResult(trackPosition, posX, posY, Color.white, curve, type);
				}
			}
		}

		// ticks
		boolean isNewTick = false;
		if (ticksT != null &&
			tickIntervals < (ticksT.length * (currentRepeats + 1)) + repeatCount &&
			tickIntervals < (ticksT.length * repeatCount) + repeatCount) {
			float t = getT(trackPosition, true);
			if (t - Math.floor(t) >= ticksT[tickIndex]) {
				tickIntervals++;
				tickIndex = (tickIndex + 1) % ticksT.length;
				isNewTick = true;
				tickExpandTime = TICK_EXPAND_TIME;
			}
		}

		// holding slider...
		Vec2f c = curve.pointAt(getT(trackPosition, false));
		double distance = Math.hypot(c.x - mouseX, c.y - mouseY);
		if (((keyPressed || GameMod.RELAX.isActive()) && distance < followRadius) || isAutoMod) {
			// mouse pressed and within follow circle
			followCircleActive = true;

			// held during new repeat
			if (isNewRepeat) {
				ticksHit++;
				if (currentRepeats % 2 > 0) {  // last circle
					int lastIndex = hitObject.getSliderX().length;
					data.sliderTickResult(trackPosition, GameData.HIT_SLIDER30,
							curve.getX(lastIndex), curve.getY(lastIndex), hitObject, currentRepeats);
				} else  // first circle
					data.sliderTickResult(trackPosition, GameData.HIT_SLIDER30,
							c.x, c.y, hitObject, currentRepeats);
			}

			// held during new tick
			if (isNewTick) {
				ticksHit++;
				data.sliderTickResult(trackPosition, GameData.HIT_SLIDER10,
						c.x, c.y, hitObject, currentRepeats);
			}

			// held near end of slider
			if (!sliderHeldToEnd && trackPosition > hitObject.getTime() + sliderTimeTotal - hitResultOffset[GameData.HIT_300])
				sliderHeldToEnd = true;
		} else {
			followCircleActive = false;

			if (isNewRepeat)
				data.sliderTickResult(trackPosition, GameData.HIT_MISS, 0, 0, hitObject, currentRepeats);
			if (isNewTick)
				data.sliderTickResult(trackPosition, GameData.HIT_MISS, 0, 0, hitObject, currentRepeats);
		}

		return false;
	}

	@Override
	public void updatePosition() {
		this.x = hitObject.getScaledX();
		this.y = hitObject.getScaledY();
		this.curve = hitObject.getSliderCurve(true);
	}

	@Override
	public Vec2f getPointAt(int trackPosition) {
		if (trackPosition <= hitObject.getTime())
			return new Vec2f(x, y);
		else if (trackPosition >= hitObject.getTime() + sliderTimeTotal) {
			if (hitObject.getRepeatCount() % 2 == 0)
				return new Vec2f(x, y);
			else
				return curve.pointAt(1);
		} else
			return curve.pointAt(getT(trackPosition, false));
	}

	@Override
	public int getEndTime() { return hitObject.getTime() + (int) sliderTimeTotal; }

	/**
	 * Returns the t value based on the given track position.
	 * @param trackPosition the current track position
	 * @param raw if false, ensures that the value lies within [0, 1] by looping repeats
	 * @return the t value: raw [0, repeats] or looped [0, 1]
	 */
	private float getT(int trackPosition, boolean raw) {
		float t = (trackPosition - hitObject.getTime()) / sliderTime;
		if (raw)
			return t;
		else {
			float floor = (float) Math.floor(t);
			return (floor % 2 == 0) ? t - floor : floor + 1 - t;
		}
	}

	@Override
	public void reset() {
		sliderClickedInitial = false;
		sliderHeldToEnd = false;
		followCircleActive = false;
		currentRepeats = 0;
		tickIndex = 0;
		ticksHit = 0;
		tickIntervals = 1;
		tickExpandTime = 0;
	}

	public Curve getCurve() {
		return curve;
	}

	public int getRepeats() {
		return repeats;
	}

	@Override
	public boolean isCircle() {
		return false;
	}

	@Override
	public boolean isSlider() {
		return true;
	}

	@Override
	public boolean isSpinner() {
		return false;
	}

	@Override
	public Color getColor() {
		return color;
	}

	@Override
	public Color getMirroredColor() {
		return mirrorColor;
	}

	public Circle[] getTickPositionCircles() {
		float tickLengthDiv = 100f * sliderMultiplier / sliderTickRate / (game.getBeatLength() / game.getBeatLengthBase());
		int tickCount = (int) Math.ceil(pixelLength / tickLengthDiv) - 1;
		Circle[] ticks = new Circle[1 + ( tickCount + 1 ) * repeats];
		Vec2f pos;
		pos = getPointAt( getTime() );
		pos.set( HitObject.unscaleX( pos.x ), HitObject.unscaleY( pos.y ) );
		ticks[0] = new Circle(pos.x, pos.y, getTime() );
		float tickTOffset = 1f / (tickCount + 1) / repeats;
		float t = tickTOffset;
		for( int i = 0; i < (tickCount + 1) * repeats; i++, t += tickTOffset ) {
			pos = getPointAt( getTime() + (int) (t * sliderTimeTotal ) );
			pos.set( HitObject.unscaleX( pos.x ), HitObject.unscaleY( pos.y ) );
			ticks[1 + i] = new Circle(pos.x, pos.y, getTime() + (int) (t * sliderTimeTotal));
		}

		for(Circle c : ticks) {
			c.updatePosition();
		}

		return ticks;
	}

	@Override
	public void updateColor() {
		super.updateColor();
		color = Dancer.colorOverride.getColor(comboColorIndex);
		mirrorColor = Dancer.colorMirrorOverride.getColor(comboColorIndex);
	}

}
