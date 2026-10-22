package com.whatsapp.ui;

import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * WhatsApp official visual styling engine for J2ME.
 * Implements WhatsApp Teal Green headers, chat wallpaper tint, message bubbles,
 * rounded corners, status badges, and typography.
 */
public class Theme {
    // Official WhatsApp Brand Colors
    public static final int COLOR_PRIMARY_TEAL   = 0x075E54; // WhatsApp Classic Dark Teal
    public static final int COLOR_ACCENT_GREEN   = 0x25D366; // WhatsApp Vivid Accent Green
    public static final int COLOR_HEADER_DARK    = 0x128C7E; // Header Top Bar
    public static final int COLOR_BG_WALLPAPER   = 0xECE5DD; // Classic WhatsApp Chat Wallpaper
    public static final int COLOR_LIST_BG        = 0xF8F9FA; // Background for chat list
    public static final int COLOR_DIVIDER        = 0xE0E0E0; // Subtle divider lines
    public static final int COLOR_BUBBLE_OUT     = 0xDCF8C6; // WhatsApp Outgoing Bubble Green
    public static final int COLOR_BUBBLE_IN      = 0xFFFFFF; // WhatsApp Incoming Bubble White
    public static final int COLOR_TEXT_MAIN      = 0x111111; // Primary text color
    public static final int COLOR_TEXT_MUTED     = 0x777777; // Timestamp & subtitle color
    public static final int COLOR_WHITE          = 0xFFFFFF; // Pure white
    public static final int COLOR_SELECTION      = 0xD0E8E4; // Highlighted row background
    public static final int COLOR_BADGE_BG       = 0x067A46; // Deep WhatsApp Emerald Green for high contrast
    public static final int COLOR_BADGE_TEXT     = 0xFFFFFF; // Pure White

    public static final Font FONT_HEADER_TITLE = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
    public static final Font FONT_CHAT_TITLE   = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    public static final Font FONT_CHAT_MSG     = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    public static final Font FONT_TIMESTAMP    = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    public static final Font FONT_BADGE        = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);

    /**
     * Calculates the proper header height based on whether a subtitle is present.
     */
    public static int getHeaderHeight(boolean hasSubtitle) {
        int titleH = FONT_HEADER_TITLE.getHeight();
        if (hasSubtitle) {
            int subH = FONT_TIMESTAMP.getHeight();
            return Math.max(56, titleH + subH + 18);
        } else {
            return Math.max(46, titleH + 18);
        }
    }

    /**
     * Draws the signature WhatsApp dark teal top header bar using default calculated height.
     */
    public static void drawWhatsAppHeader(Graphics g, int width, String title, String subtitle) {
        int h = getHeaderHeight(subtitle != null);
        drawWhatsAppHeader(g, width, h, title, subtitle);
    }

    /**
     * Draws the signature WhatsApp dark teal top header bar with explicit height.
     */
    public static void drawWhatsAppHeader(Graphics g, int width, int headerHeight, String title, String subtitle) {
        // Gradient / Solid Teal Header
        g.setColor(COLOR_PRIMARY_TEAL);
        g.fillRect(0, 0, width, headerHeight);

        int maxTitleW = width - 24;
        int titleH = FONT_HEADER_TITLE.getHeight();

        if (subtitle != null && subtitle.trim().length() > 0) {
            int subH = FONT_TIMESTAMP.getHeight();
            int totalTextH = titleH + subH + 2;
            int startY = Math.max(4, (headerHeight - totalTextH) / 2);

            // Title
            g.setFont(FONT_HEADER_TITLE);
            g.setColor(COLOR_WHITE);
            String drawTitle = title;
            if (FONT_HEADER_TITLE.stringWidth(drawTitle) > maxTitleW) {
                while (drawTitle.length() > 3 && FONT_HEADER_TITLE.stringWidth(drawTitle + "...") > maxTitleW) {
                    drawTitle = drawTitle.substring(0, drawTitle.length() - 1);
                }
                drawTitle += "...";
            }
            g.drawString(drawTitle, 12, startY, Graphics.TOP | Graphics.LEFT);

            // Subtitle
            g.setFont(FONT_TIMESTAMP);
            g.setColor(COLOR_BUBBLE_OUT);
            String drawSub = subtitle;
            if (FONT_TIMESTAMP.stringWidth(drawSub) > maxTitleW) {
                while (drawSub.length() > 3 && FONT_TIMESTAMP.stringWidth(drawSub + "...") > maxTitleW) {
                    drawSub = drawSub.substring(0, drawSub.length() - 1);
                }
                drawSub += "...";
            }
            g.drawString(drawSub, 12, startY + titleH + 2, Graphics.TOP | Graphics.LEFT);
        } else {
            int startY = Math.max(4, (headerHeight - titleH) / 2);
            g.setFont(FONT_HEADER_TITLE);
            g.setColor(COLOR_WHITE);
            String drawTitle = title;
            if (FONT_HEADER_TITLE.stringWidth(drawTitle) > maxTitleW) {
                while (drawTitle.length() > 3 && FONT_HEADER_TITLE.stringWidth(drawTitle + "...") > maxTitleW) {
                    drawTitle = drawTitle.substring(0, drawTitle.length() - 1);
                }
                drawTitle += "...";
            }
            g.drawString(drawTitle, 12, startY, Graphics.TOP | Graphics.LEFT);
        }

        // Accent bottom hairline
        g.setColor(COLOR_HEADER_DARK);
        g.drawLine(0, headerHeight - 1, width, headerHeight - 1);
    }
}
