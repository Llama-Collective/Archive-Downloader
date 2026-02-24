package com.andrews.archivedownloader.gui.widget;

import com.andrews.archivedownloader.gui.theme.UITheme;
import com.andrews.archivedownloader.util.RenderUtil;
import com.andrews.archivedownloader.wrapper.client.UiMinecraftClient;
import com.andrews.archivedownloader.wrapper.gui.UiEventListener;
import com.andrews.archivedownloader.wrapper.gui.UiFont;
import com.andrews.archivedownloader.wrapper.gui.UiRenderContext;
import com.andrews.archivedownloader.wrapper.input.UiMouseEvent;
import com.andrews.archivedownloader.wrapper.platform.UiPlatform;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownRenderer implements UiEventListener {
    private static final Pattern RAW_URL_PATTERN = Pattern.compile("(?i)\\bhttps?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");
    private static final String DICTIONARY_PATH_PREFIX = "/dictionary/";
    private static final String ARCHIVE_PATH_PREFIX = "/archive/";
    private static final String DISCORD_LINK_PATH_PREFIX = "/discord-link";

    private static final int DEFAULT_LINK_COLOR = 0xFF66B3FF;
    private static final int DEFAULT_HOVERED_LINK_COLOR = 0xFF99C8FF;
    private static final int DEFAULT_CODE_COLOR = 0xFFDCDCDC;
    private static final int DEFAULT_HEADING_COLOR = 0xFFE2E2E2;

    private final Parser parser = Parser.builder().build();
    private final List<LayoutToken> tokens = new ArrayList<>();
    private final List<RenderedLine> renderedLines = new ArrayList<>();
    private final List<Integer> lineOffsets = new ArrayList<>();
    private final List<Integer> lineHeights = new ArrayList<>();
    private final List<LinkHitbox> linkHitboxes = new ArrayList<>();
    private final TextStyle baseStyle = new TextStyle(null, null, 0, false, false, "");

    private Consumer<String> onLinkClicked = UiPlatform::openUri;

    private String markdown = "";
    private Node parsedDocument = null;
    private boolean parseDirty = true;
    private boolean layoutDirty = true;

    private int x;
    private int y;
    private int width;
    private int height;

    private int textColor = DEFAULT_HEADING_COLOR;
    private int headingColor = UITheme.Colors.TEXT_PRIMARY;
    private int quoteColor = UITheme.Colors.TEXT_SUBTITLE;
    private int codeColor = DEFAULT_CODE_COLOR;
    private int linkColor = DEFAULT_LINK_COLOR;
    private int hoveredLinkColor = DEFAULT_HOVERED_LINK_COLOR;

    private int cachedLayoutWidth = -1;
    private int cachedBaseLineHeight = -1;
    private int requiredHeight = 0;
    private String hoveredLink = null;
    private String hoveredLinkTooltip = null;
    private int verticalOffset = 0;

    public void setBounds(int x, int y, int width, int height) {
        if (this.width != width) {
            layoutDirty = true;
        }
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public void setMarkdown(String markdown) {
        String safe = markdown != null ? markdown : "";
        if (Objects.equals(this.markdown, safe)) {
            return;
        }
        this.markdown = safe;
        this.parseDirty = true;
        this.layoutDirty = true;
    }

    public void setOnLinkClicked(Consumer<String> onLinkClicked) {
        this.onLinkClicked = onLinkClicked != null ? onLinkClicked : UiPlatform::openUri;
    }

    public void setTextColors(int textColor, int headingColor, int quoteColor, int codeColor) {
        this.textColor = textColor;
        this.headingColor = headingColor;
        this.quoteColor = quoteColor;
        this.codeColor = codeColor;
    }

    public void setLinkColors(int linkColor, int hoveredLinkColor) {
        this.linkColor = linkColor;
        this.hoveredLinkColor = hoveredLinkColor;
    }

    public int getRequiredHeight(UiFont font) {
        if (font == null) {
            return 0;
        }
        ensureLayout(font);
        return requiredHeight;
    }

    public String getHoveredLink() {
        return hoveredLink;
    }

    public String getHoveredLinkTooltip() {
        return hoveredLinkTooltip;
    }

    public void setVerticalOffset(int verticalOffset) {
        this.verticalOffset = Math.max(0, verticalOffset);
    }

    public void render(UiRenderContext context, UiFont font, int mouseX, int mouseY) {
        if (context == null || font == null || width <= 0 || height <= 0) {
            return;
        }
        ensureLayout(font);
        LinkHitbox hoveredHitbox = findHitboxAt(mouseX, mouseY);
        hoveredLink = hoveredHitbox != null ? hoveredHitbox.url() : null;
        hoveredLinkTooltip = hoveredHitbox != null ? hoveredHitbox.title() : null;

        int bottom = y + height;
        RenderUtil.enableScissor(context, x, y, x + width, bottom);
        for (int lineIndex = 0; lineIndex < renderedLines.size(); lineIndex++) {
            RenderedLine line = renderedLines.get(lineIndex);
            int lineTop = lineIndex < lineOffsets.size() ? lineOffsets.get(lineIndex) : 0;
            int lineHeight = lineIndex < lineHeights.size() ? lineHeights.get(lineIndex) : (Math.max(font.lineHeight(), 1) + 1);
            int drawY = y + lineTop - verticalOffset;
            if (drawY > bottom || drawY + lineHeight < y) {
                continue;
            }
            int drawX = x;
            for (RenderedSegment segment : line.segments()) {
                if (segment.text().isEmpty()) {
                    drawX += segment.width();
                    continue;
                }
                boolean isHoveredLink = segment.style().linkUrl() != null
                    && segment.style().linkUrl().equals(hoveredLink);
                int color = resolveColor(segment.style(), isHoveredLink);
                float scale = textScaleForStyle(segment.style());
                if (Math.abs(scale - 1.0f) > 0.001f) {
                    RenderUtil.drawScaledString(context, segment.text(), drawX, drawY, color, scale);
                    // Simulate heavier weight for headings.
                    // RenderUtil.drawScaledString(context, segment.text(), drawX + 1, drawY, color, scale);
                } else {
                    RenderUtil.drawString(context, font, segment.text(), drawX, drawY, color);
                }
                if (segment.style().linkUrl() != null && segment.width() > 0) {
                    int underlineY = drawY + Math.max((int) Math.ceil(font.lineHeight() * scale) - 1, 0);
                    RenderUtil.fillRect(context, drawX, underlineY, drawX + segment.width(), underlineY + 1, color);
                }
                drawX += segment.width();
            }
        }
        RenderUtil.disableScissor(context);
    }

    @Override
    public boolean mouseClicked(UiMouseEvent click, boolean doubled) {
        if (click == null || click.button() != 0 || !isInside(click.x(), click.y())) {
            return false;
        }
        ensureLayout(UiMinecraftClient.getInstance().uiFont());
        LinkHitbox hitbox = findHitboxAt(click.x(), click.y());
        if (hitbox == null || hitbox.url() == null || hitbox.url().isBlank()) {
            return false;
        }
        onLinkClicked.accept(hitbox.url());
        return true;
    }

    private void ensureLayout(UiFont font) {
        int layoutWidth = Math.max(1, width);
        int baseLineHeight = Math.max(font.lineHeight(), 1) + 1;
        if (!layoutDirty && cachedLayoutWidth == layoutWidth && cachedBaseLineHeight == baseLineHeight) {
            return;
        }

        ensureParsed();
        rebuildTokens();
        rebuildLayout(font, layoutWidth, baseLineHeight);

        cachedLayoutWidth = layoutWidth;
        cachedBaseLineHeight = baseLineHeight;
        layoutDirty = false;
    }

    private void ensureParsed() {
        if (!parseDirty) {
            return;
        }
        parsedDocument = parser.parse(markdown != null ? markdown : "");
        parseDirty = false;
    }

    private void rebuildTokens() {
        tokens.clear();
        if (parsedDocument == null) {
            return;
        }
        appendBlockChildren(parsedDocument, baseStyle, 0);
        while (!tokens.isEmpty() && tokens.get(tokens.size() - 1) instanceof BreakToken) {
            tokens.remove(tokens.size() - 1);
        }
    }

    private void rebuildLayout(UiFont font, int maxWidth, int baseLineHeight) {
        renderedLines.clear();
        lineOffsets.clear();
        lineHeights.clear();
        linkHitboxes.clear();

        List<RenderedSegment> currentLine = new ArrayList<>();
        int lineWidth = 0;
        WrapContinuation continuation = new WrapContinuation();

        for (LayoutToken token : tokens) {
            if (token instanceof BreakToken breakToken) {
                commitLine(currentLine);
                lineWidth = 0;
                continuation.clear();
                for (int i = 1; i < breakToken.lines(); i++) {
                    renderedLines.add(new RenderedLine(new ArrayList<>()));
                }
                continue;
            }
            if (token instanceof TextToken textToken) {
                lineWidth = appendWrappedText(font, maxWidth, textToken.text(), textToken.style(), currentLine, lineWidth, continuation);
            }
        }

        if (!currentLine.isEmpty() || renderedLines.isEmpty()) {
            commitLine(currentLine);
        }

        while (renderedLines.size() > 1 && renderedLines.get(renderedLines.size() - 1).segments().isEmpty()) {
            renderedLines.remove(renderedLines.size() - 1);
        }

        int runningOffset = 0;
        for (int i = 0; i < renderedLines.size(); i++) {
            RenderedLine line = renderedLines.get(i);
            boolean headingLine = isHeadingLine(line);
            boolean previousHeading = i > 0 && isHeadingLine(renderedLines.get(i - 1));
            boolean nextHeading = i + 1 < renderedLines.size() && isHeadingLine(renderedLines.get(i + 1));

            if (headingLine && !previousHeading) {
                runningOffset += headingTopPadding(baseLineHeight);
            }

            lineOffsets.add(runningOffset);
            int lineHeight = measureLineHeight(baseLineHeight, line);
            lineHeights.add(lineHeight);
            runningOffset += lineHeight;

            if (headingLine && !nextHeading) {
                runningOffset += headingBottomPadding(baseLineHeight);
            }
        }

        requiredHeight = Math.max(1, runningOffset);
        rebuildLinkHitboxes();
    }

    private int measureLineHeight(int baseLineHeight, RenderedLine line) {
        float maxScale = 1.0f;
        if (line != null && line.segments() != null) {
            for (RenderedSegment segment : line.segments()) {
                if (segment == null) {
                    continue;
                }
                maxScale = Math.max(maxScale, textScaleForStyle(segment.style()));
            }
        }
        return Math.max(baseLineHeight, (int) Math.ceil(baseLineHeight * maxScale));
    }

    private static int headingTopPadding(int baseLineHeight) {
        return Math.max(0, baseLineHeight);
    }

    private static int headingBottomPadding(int baseLineHeight) {
        return Math.max(1, Math.round(baseLineHeight * 0.5f));
    }

    private static boolean isHeadingLine(RenderedLine line) {
        if (line == null || line.segments() == null) {
            return false;
        }
        for (RenderedSegment segment : line.segments()) {
            if (segment == null || segment.style() == null) {
                continue;
            }
            if (segment.style().headingLevel() > 0 && segment.width() > 0) {
                return true;
            }
        }
        return false;
    }

    private void rebuildLinkHitboxes() {
        linkHitboxes.clear();
        for (int lineIndex = 0; lineIndex < renderedLines.size(); lineIndex++) {
            RenderedLine line = renderedLines.get(lineIndex);
            int lineTop = lineIndex < lineOffsets.size() ? lineOffsets.get(lineIndex) : 0;
            int lineHeight = lineIndex < lineHeights.size() ? lineHeights.get(lineIndex) : 0;
            int cursorX = 0;
            for (RenderedSegment segment : line.segments()) {
                String link = segment.style().linkUrl();
                if (link != null && !link.isBlank() && segment.width() > 0) {
                    linkHitboxes.add(new LinkHitbox(
                        cursorX,
                        lineTop,
                        segment.width(),
                        lineHeight,
                        link,
                        buildTooltipForLink(link, segment.style().linkTitle())
                    ));
                }
                cursorX += segment.width();
            }
        }
    }

    private static String buildTooltipForLink(String linkUrl, String linkTitle) {
        String url = safeTrim(linkUrl);
        String title = safeTrim(linkTitle);
        if (url == null) {
            return title;
        }
        if (isReferenceLink(url)) {
            return title;
        }
        if (title != null) {
            return title + " (" + url + ")";
        }
        return url;
    }

    private int appendWrappedText(
        UiFont font,
        int maxWidth,
        String text,
        TextStyle style,
        List<RenderedSegment> currentLine,
        int lineWidth,
        WrapContinuation continuation
    ) {
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            String piece = matcher.group();
            if (piece == null || piece.isEmpty()) {
                continue;
            }
            if (isWhitespace(piece)) {
                if (lineWidth == 0) {
                    lineWidth = applyContinuationIndent(font, maxWidth, currentLine, lineWidth, continuation);
                }
                if (lineWidth == 0) {
                    continue;
                }
                int pieceWidth = measureWidth(font, piece, style);
                if (lineWidth + pieceWidth <= maxWidth) {
                    currentLine.add(new RenderedSegment(piece, pieceWidth, style));
                    lineWidth += pieceWidth;
                } else {
                    commitLine(currentLine);
                    lineWidth = 0;
                    queueContinuationIndent(style, continuation);
                }
                continue;
            }
            lineWidth = applyContinuationIndent(font, maxWidth, currentLine, lineWidth, continuation);
            lineWidth = appendWord(font, maxWidth, piece, style, currentLine, lineWidth, continuation);
        }
        return lineWidth;
    }

    private int appendWord(
        UiFont font,
        int maxWidth,
        String word,
        TextStyle style,
        List<RenderedSegment> currentLine,
        int lineWidth,
        WrapContinuation continuation
    ) {
        int wordWidth = measureWidth(font, word, style);
        if (wordWidth <= maxWidth) {
            if (lineWidth > 0 && lineWidth + wordWidth > maxWidth) {
                commitLine(currentLine);
                lineWidth = 0;
                queueContinuationIndent(style, continuation);
                lineWidth = applyContinuationIndent(font, maxWidth, currentLine, lineWidth, continuation);
            }
            currentLine.add(new RenderedSegment(word, wordWidth, style));
            return lineWidth + wordWidth;
        }

        String remaining = word;
        if (lineWidth > 0) {
            commitLine(currentLine);
            lineWidth = 0;
            queueContinuationIndent(style, continuation);
            lineWidth = applyContinuationIndent(font, maxWidth, currentLine, lineWidth, continuation);
        }
        while (!remaining.isEmpty()) {
            int availableWidth = Math.max(1, maxWidth - lineWidth);
            int fit = findMaxFittingPrefix(font, remaining, availableWidth, style);
            if (fit <= 0) {
                fit = 1;
            }
            String chunk = remaining.substring(0, fit);
            int chunkWidth = measureWidth(font, chunk, style);
            currentLine.add(new RenderedSegment(chunk, chunkWidth, style));
            lineWidth += chunkWidth;
            remaining = remaining.substring(fit);
            if (!remaining.isEmpty()) {
                commitLine(currentLine);
                lineWidth = 0;
                queueContinuationIndent(style, continuation);
                lineWidth = applyContinuationIndent(font, maxWidth, currentLine, lineWidth, continuation);
            }
        }
        return lineWidth;
    }

    private int applyContinuationIndent(
        UiFont font,
        int maxWidth,
        List<RenderedSegment> currentLine,
        int lineWidth,
        WrapContinuation continuation
    ) {
        if (lineWidth > 0 || !continuation.pending) {
            return lineWidth;
        }
        String indentText = continuation.indentText;
        if (indentText == null || indentText.isEmpty()) {
            continuation.clear();
            return lineWidth;
        }
        TextStyle indentStyle = continuation.style != null
            ? continuation.style.withLink(null, null)
            : baseStyle;
        int indentWidth = measureWidth(font, indentText, indentStyle);
        if (indentWidth <= 0 || indentWidth >= maxWidth) {
            continuation.clear();
            return lineWidth;
        }
        currentLine.add(new RenderedSegment("", indentWidth, indentStyle));
        continuation.clear();
        return indentWidth;
    }

    private static void queueContinuationIndent(TextStyle style, WrapContinuation continuation) {
        if (style == null || continuation == null) {
            return;
        }
        String indent = style.continuationIndent();
        if (indent == null || indent.isEmpty()) {
            continuation.clear();
            return;
        }
        continuation.pending = true;
        continuation.indentText = indent;
        continuation.style = style;
    }

    private static int findMaxFittingPrefix(UiFont font, String text, int maxWidth, TextStyle style) {
        int low = 1;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int width = measureWidth(font, text.substring(0, mid), style);
            if (width <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private void appendBlockChildren(Node parent, TextStyle style, int listDepth) {
        boolean first = true;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (!first) {
                addBreak(1);
            }
            appendBlock(child, style, listDepth);
            first = false;
        }
    }

    private void appendBlock(Node node, TextStyle style, int listDepth) {
        if (node instanceof Paragraph paragraph) {
            appendInlineChildren(paragraph, style);
            return;
        }
        if (node instanceof Heading heading) {
            appendInlineChildren(heading, style.withHeadingLevel(heading.getLevel()));
            return;
        }
        if (node instanceof BulletList bulletList) {
            appendBulletList(bulletList, style, listDepth);
            return;
        }
        if (node instanceof OrderedList orderedList) {
            appendOrderedList(orderedList, style, listDepth);
            return;
        }
        if (node instanceof BlockQuote blockQuote) {
            appendBlockQuote(blockQuote, style, listDepth);
            return;
        }
        if (node instanceof FencedCodeBlock fencedCodeBlock) {
            appendCodeBlock(fencedCodeBlock.getLiteral(), style);
            return;
        }
        if (node instanceof IndentedCodeBlock indentedCodeBlock) {
            appendCodeBlock(indentedCodeBlock.getLiteral(), style);
            return;
        }
        if (node instanceof ThematicBreak) {
            addText("--------------------------------", style.withQuote(true));
            return;
        }
        if (node instanceof HtmlBlock htmlBlock) {
            addText(htmlBlock.getLiteral(), style.withQuote(true));
            return;
        }
        if (node.getFirstChild() != null) {
            appendBlockChildren(node, style, listDepth);
        }
    }

    private void appendBulletList(BulletList list, TextStyle style, int listDepth) {
        int itemIndex = 0;
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem listItem)) {
                continue;
            }
            if (itemIndex > 0) {
                addBreak(1);
            }
            String prefix = "  ".repeat(Math.max(0, listDepth)) + "\u2022 ";
            addText(prefix, style.withQuote(true));
            appendListItem(listItem, style.withContinuationIndent(prefix), listDepth);
            itemIndex++;
        }
    }

    private void appendOrderedList(OrderedList list, TextStyle style, int listDepth) {
        int index = list.getStartNumber();
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof ListItem listItem)) {
                continue;
            }
            if (index > list.getStartNumber()) {
                addBreak(1);
            }
            String prefix = "  ".repeat(Math.max(0, listDepth)) + index + ". ";
            addText(prefix, style.withQuote(true));
            appendListItem(listItem, style.withContinuationIndent(prefix), listDepth);
            index++;
        }
    }

    private void appendListItem(ListItem item, TextStyle style, int listDepth) {
        boolean firstBlock = true;
        for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Paragraph paragraph) {
                if (!firstBlock) {
                    addBreak(1);
                    addText("  ".repeat(Math.max(0, listDepth + 1)), style);
                }
                appendInlineChildren(paragraph, style);
            } else if (child instanceof BulletList bulletList) {
                if (!firstBlock) {
                    addBreak(1);
                }
                appendBulletList(bulletList, style, listDepth + 1);
            } else if (child instanceof OrderedList orderedList) {
                if (!firstBlock) {
                    addBreak(1);
                }
                appendOrderedList(orderedList, style, listDepth + 1);
            } else {
                if (!firstBlock) {
                    addBreak(1);
                }
                appendBlock(child, style, listDepth + 1);
            }
            firstBlock = false;
        }
    }

    private void appendBlockQuote(BlockQuote quote, TextStyle style, int listDepth) {
        boolean first = true;
        TextStyle quoteStyle = style.withQuote(true);
        for (Node child = quote.getFirstChild(); child != null; child = child.getNext()) {
            if (!first) {
                addBreak(1);
            }
            addText("> ", quoteStyle);
            if (child instanceof Paragraph paragraph) {
                appendInlineChildren(paragraph, quoteStyle);
            } else {
                appendBlock(child, quoteStyle, listDepth);
            }
            first = false;
        }
    }

    private void appendCodeBlock(String literal, TextStyle style) {
        if (literal == null || literal.isEmpty()) {
            return;
        }
        String[] lines = literal.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                addBreak(1);
            }
            addText(lines[i], style.withCode(true));
        }
    }

    private void appendInlineChildren(Node parent, TextStyle style) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            appendInline(child, style);
        }
    }

    private void appendInline(Node node, TextStyle style) {
        if (node instanceof Text text) {
            appendTextWithAutoLinks(text.getLiteral(), style);
            return;
        }
        if (node instanceof SoftLineBreak) {
            addText(" ", style);
            return;
        }
        if (node instanceof HardLineBreak) {
            addBreak(1);
            return;
        }
        if (node instanceof Emphasis emphasis) {
            appendInlineChildren(emphasis, style);
            return;
        }
        if (node instanceof StrongEmphasis strongEmphasis) {
            appendInlineChildren(strongEmphasis, style);
            return;
        }
        if (node instanceof Code code) {
            addText(code.getLiteral(), style.withCode(true));
            return;
        }
        if (node instanceof Link link) {
            String destination = normalizeLink(link.getDestination());
            String title = safeTrim(link.getTitle());
            TextStyle linkStyle = destination != null ? style.withLink(destination, title) : style;
            if (link.getFirstChild() == null) {
                if (destination != null) {
                    addText(destination, linkStyle);
                }
            } else {
                appendInlineChildren(link, linkStyle);
            }
            return;
        }
        if (node instanceof Image image) {
            String altText = collectText(image).trim();
            if (altText.isEmpty()) {
                altText = "image";
            }
            addText("[image: " + altText + "]", style.withQuote(true));
            return;
        }
        if (node instanceof HtmlInline htmlInline) {
            addText(htmlInline.getLiteral(), style.withQuote(true));
            return;
        }
        if (node.getFirstChild() != null) {
            appendInlineChildren(node, style);
        }
    }

    private void appendTextWithAutoLinks(String text, TextStyle style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (style.linkUrl() != null) {
            addText(text, style);
            return;
        }

        Matcher matcher = RAW_URL_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                addText(text.substring(cursor, matcher.start()), style);
            }
            String match = matcher.group();
            String url = trimTrailingPunctuation(match);
            if (!url.isEmpty()) {
                addText(url, style.withLink(url, null));
                if (url.length() < match.length()) {
                    addText(match.substring(url.length()), style);
                }
            } else {
                addText(match, style);
            }
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            addText(text.substring(cursor), style);
        }
    }

    private static String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0) {
            char c = url.charAt(end - 1);
            if (c == '.' || c == ',' || c == ';' || c == '!' || c == '?') {
                end--;
            } else {
                break;
            }
        }
        return url.substring(0, end);
    }

    private static String normalizeLink(String destination) {
        if (destination == null) {
            return null;
        }
        String trimmed = destination.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://")
            || lower.startsWith("https://")
            || lower.startsWith("mailto:")
            || lower.startsWith("discord://")
            || trimmed.startsWith("/")) {
            return trimmed;
        }
        return null;
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addText(String text, TextStyle style) {
        if (text == null || text.isEmpty()) {
            return;
        }
        tokens.add(new TextToken(text, style));
    }

    private void addBreak(int lines) {
        if (lines <= 0) {
            return;
        }
        tokens.add(new BreakToken(lines));
    }

    private void commitLine(List<RenderedSegment> currentLine) {
        renderedLines.add(new RenderedLine(new ArrayList<>(currentLine)));
        currentLine.clear();
    }

    private LinkHitbox findHitboxAt(double mouseX, double mouseY) {
        if (!isInside(mouseX, mouseY)) {
            return null;
        }
        for (LinkHitbox hitbox : linkHitboxes) {
            int hitX = x + hitbox.x();
            int hitY = y + hitbox.y() - verticalOffset;
            if (mouseX >= hitX && mouseX < hitX + hitbox.width()
                && mouseY >= hitY && mouseY < hitY + hitbox.height()) {
                return hitbox;
            }
        }
        return null;
    }

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int resolveColor(TextStyle style, boolean hovered) {
        if (style.linkUrl() != null) {
            if (isReferenceLink(style.linkUrl())) {
                return textColor;
            }
            return hovered ? hoveredLinkColor : linkColor;
        }
        if (style.code()) {
            return codeColor;
        }
        if (style.quote()) {
            return quoteColor;
        }
        if (style.headingLevel() > 0) {
            return headingColor;
        }
        return textColor;
    }

    private float textScaleForStyle(TextStyle style) {
        if (style == null) {
            return 1.0f;
        }
        return headingScaleForLevel(style.headingLevel());
    }

    private static float headingScaleForLevel(int level) {
        return switch (Math.max(level, 0)) {
            case 1 -> 1.45f;
            case 2 -> 1.30f;
            case 3 -> 1.18f;
            case 4 -> 1.10f;
            case 5 -> 1.05f;
            default -> 1.0f;
        };
    }

    private static int measureWidth(UiFont font, String text, TextStyle style) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        float scale = headingScaleForLevel(style != null ? style.headingLevel() : 0);
        return (int) Math.ceil(font.width(text) * scale);
    }

    private static boolean isReferenceLink(String linkUrl) {
        String path = extractPath(linkUrl);
        if (!path.isEmpty() && (path.startsWith(DICTIONARY_PATH_PREFIX) || path.startsWith(DISCORD_LINK_PATH_PREFIX))) {
            return true;
        }
        return !extractPostIdFromLink(linkUrl).isEmpty();
    }

    private static String extractPostIdFromLink(String link) {
        String path = extractPath(link);
        if (path.isEmpty() || !path.startsWith(ARCHIVE_PATH_PREFIX)) {
            return "";
        }
        String segment = path.substring(ARCHIVE_PATH_PREFIX.length());
        if (segment.isEmpty()) {
            return "";
        }
        int slash = segment.indexOf('/');
        if (slash >= 0) {
            segment = segment.substring(0, slash);
        }
        return URLDecoder.decode(segment, StandardCharsets.UTF_8).trim();
    }

    private static String extractPath(String link) {
        String trimmed = link != null ? link.trim() : "";
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("/")) {
            int query = trimmed.indexOf('?');
            int hash = trimmed.indexOf('#');
            int cut = -1;
            if (query >= 0 && hash >= 0) {
                cut = Math.min(query, hash);
            } else if (query >= 0) {
                cut = query;
            } else if (hash >= 0) {
                cut = hash;
            }
            return cut >= 0 ? trimmed.substring(0, cut) : trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            return path != null ? path : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String collectText(Node node) {
        if (node == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        collectTextRecursive(node, builder);
        return builder.toString();
    }

    private static void collectTextRecursive(Node node, StringBuilder builder) {
        if (node instanceof Text text) {
            builder.append(text.getLiteral());
        } else if (node instanceof Code code) {
            builder.append(code.getLiteral());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            collectTextRecursive(child, builder);
        }
    }

    private static boolean isWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private interface LayoutToken {
    }

    private record TextToken(String text, TextStyle style) implements LayoutToken {
    }

    private record BreakToken(int lines) implements LayoutToken {
    }

    private static final class WrapContinuation {
        private boolean pending;
        private String indentText;
        private TextStyle style;

        private void clear() {
            pending = false;
            indentText = null;
            style = null;
        }
    }

    private record TextStyle(String linkUrl, String linkTitle, int headingLevel, boolean code, boolean quote, String continuationIndent) {
        private TextStyle withLink(String link, String title) {
            return new TextStyle(link, title, headingLevel, code, quote, continuationIndent);
        }

        private TextStyle withHeadingLevel(int level) {
            return new TextStyle(linkUrl, linkTitle, Math.max(0, level), code, quote, continuationIndent);
        }

        private TextStyle withCode(boolean isCode) {
            return new TextStyle(linkUrl, linkTitle, headingLevel, isCode, quote, continuationIndent);
        }

        private TextStyle withQuote(boolean isQuote) {
            return new TextStyle(linkUrl, linkTitle, headingLevel, code, isQuote, continuationIndent);
        }

        private TextStyle withContinuationIndent(String indent) {
            return new TextStyle(linkUrl, linkTitle, headingLevel, code, quote, indent != null ? indent : "");
        }
    }

    private record RenderedSegment(String text, int width, TextStyle style) {
    }

    private record RenderedLine(List<RenderedSegment> segments) {
    }

    private record LinkHitbox(int x, int y, int width, int height, String url, String title) {
    }
}
