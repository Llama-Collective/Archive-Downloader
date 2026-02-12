package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.gui.UiRenderable;

public class LoadingSpinner implements UiRenderable {
	private static final int SPINNER_SIZE = 32;
	private static final int BLOCK_SIZE = 3;
	private static final int NUM_BLOCKS = 8;
	private static final float FADE_SPEED = 0.03f;
	private static final float FULL_OPACITY = 1.0f;
	private static final float MEDIUM_OPACITY = 0.5f;
	private static final float DIM_OPACITY = 0.15f;
	private static final float CLOSE_DISTANCE = 0.5f;
	private static final float TRAIL_DISTANCE = 1.5f;
	private static final float TRAIL_FADE = 0.3f;
	private static final int WHITE_COLOR = 0x00FFFFFF;

	private int x;
	private int y;
	private float animationTime = 0.0f;

	public LoadingSpinner(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	@Override
	public void render(UiRenderContext context, int mouseX, int mouseY, float delta) {
		animationTime += FADE_SPEED;
		if (animationTime >= NUM_BLOCKS) {
			animationTime -= NUM_BLOCKS;
		}

		int centerX = x + SPINNER_SIZE / 2;
		int centerY = y + SPINNER_SIZE / 2;
		int radius = SPINNER_SIZE / 2 - BLOCK_SIZE;

		for (int i = 0; i < NUM_BLOCKS; i++) {
			float angle = (i * 360.0f / NUM_BLOCKS);
			double angleRad = Math.toRadians(angle);
			int blockX = (int) (centerX + Math.cos(angleRad) * radius) - BLOCK_SIZE / 2;
			int blockY = (int) (centerY + Math.sin(angleRad) * radius) - BLOCK_SIZE / 2;

			float distance = calculateDistance(i);
			float opacity = calculateOpacity(distance);

			int alpha = (int) (opacity * 255);
			int color = (alpha << 24) | WHITE_COLOR;

			RenderUtil.fillRect(context, blockX, blockY, blockX + BLOCK_SIZE, blockY + BLOCK_SIZE, color);
		}
	}

	public int getWidth() {
		return SPINNER_SIZE;
	}

	public int getHeight() {
		return SPINNER_SIZE;
	}

	private float calculateDistance(int blockIndex) {
		float distance = Math.abs(animationTime - blockIndex);
		if (distance > NUM_BLOCKS / 2.0f) {
			distance = NUM_BLOCKS - distance;
		}
		return distance;
	}

	private float calculateOpacity(float distance) {
		if (distance < CLOSE_DISTANCE) {
			return FULL_OPACITY;
		} else if (distance < TRAIL_DISTANCE) {
			return MEDIUM_OPACITY - (distance - CLOSE_DISTANCE) * TRAIL_FADE;
		} else {
			return DIM_OPACITY;
		}
	}
}
