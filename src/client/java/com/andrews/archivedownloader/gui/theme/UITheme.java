package com.andrews.archivedownloader.gui.theme;

public final class UITheme {
	private UITheme() {}

	// Colors
	public static final class Colors {
		public static final int BUTTON_BG = 0xFF3A3A3A;
		public static final int BUTTON_BG_HOVER = 0xFF4A4A4A;
		public static final int BUTTON_BG_DISABLED = 0xFF2A2A2A;
		public static final int BUTTON_BORDER = 0xFF555555;

		public static final int TEXT_PRIMARY = 0xFFFFFFFF;
		public static final int TEXT_DISABLED = 0xFF888888;
		public static final int TEXT_MUTED = 0xFF999999;
		public static final int TEXT_SUBTITLE = 0xFFAAAAAA;
		public static final int TEXT_TAG = 0xFFCCCCCC;

		public static final int FIELD_BG = 0xFF2A2A2A;
		public static final int FIELD_BORDER = 0xFF555555;
		public static final int FIELD_BORDER_FOCUSED = 0xFF888888;

		public static final int PANEL_BG = 0xFF1A1A1A;
		public static final int PANEL_BG_SECONDARY = 0xFF252525;
		public static final int PANEL_BORDER = 0xFF444444;

		public static final int TOAST_BG_SUCCESS = 0xFF2A5F2A;
		public static final int TOAST_BG_ERROR = 0xFF5F2A2A;
		public static final int TOAST_BG_INFO = 0xFF2A3A5F;
		public static final int TOAST_BG_WARNING = 0xFF5F4A2A;
		public static final int TOAST_BORDER = 0xFF555555;
		public static final int TOAST_ACCENT_SUCCESS = 0xFF44FF44;
		public static final int TOAST_ACCENT_ERROR = 0xFFFF4444;
		public static final int TOAST_ACCENT_INFO = 0xFF4488FF;
		public static final int TOAST_ACCENT_WARNING = 0xFFFFAA44;

		public static final int OVERLAY_BG = 0x80000000;
		public static final int CONTAINER_BG = 0xFF333333;
		public static final int DIVIDER = 0xFF000000;

		public static final int SCROLLBAR_BG = 0xFF2A2A2A;
		public static final int SCROLLBAR_THUMB = 0xFF555555;
		public static final int SCROLLBAR_THUMB_HOVER = 0xFF888888;

		public static final int TOGGLE_ON = 0xFF4CAF50;
		public static final int TOGGLE_ON_HOVER = 0xFF5FBF63;
		public static final int TOGGLE_OFF_HOVER = 0xFF5A5A5A;

		public static final int ERROR_TEXT = 0xFFFF5555;
		public static final int SUCCESS_BG = 0xFF66FF66;

		private Colors() {}
	}

	// Dimensions & Spacing
	public static final class Dimensions {
		public static final int PADDING = 10;
		public static final int PADDING_SMALL = 5;
		public static final int BORDER_WIDTH = 1;

		public static final int BUTTON_HEIGHT = 20;
		public static final int BUTTON_HEIGHT_LARGE = 30;
		public static final int SEARCH_BAR_HEIGHT = 20;
		public static final int TOAST_HEIGHT = 70;
		public static final int TOAST_MIN_WIDTH = 200;
		public static final int TOAST_MAX_WIDTH = 300;

		public static final int ICON_SMALL = 12;
		public static final int ICON_MEDIUM = 16;
		public static final int ICON_LARGE = 24;

		public static final int SCROLLBAR_WIDTH = 8;

		private Dimensions() {}
	}

	// Typography
	public static final class Typography {
		public static final int TEXT_HEIGHT = 8;
		public static final int LINE_HEIGHT = 12;

		private Typography() {}
	}

	// Responsive Breakpoints
	public static final class Breakpoints {
		public static final int VERY_COMPACT_THRESHOLD = 180;
		public static final int COMPACT_THRESHOLD = 250;

		private Breakpoints() {}
	}

	// Animation
	public static final class Animation {
		public static final int TOAST_DISPLAY_DURATION = 5000;
		public static final int TOAST_FADE_DURATION = 500;

		private Animation() {}
	}

	// Layout
	public static final class Layout {
		public static final int ITEMS_PER_PAGE = 20;
		public static final int SPLIT_PANEL_RATIO = 2;

		private Layout() {}
	}
}

