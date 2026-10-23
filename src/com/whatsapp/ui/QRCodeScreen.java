package com.whatsapp.ui;

import com.whatsapp.i18n.Text;

import com.whatsapp.net.BaileysBridgeClient;
import com.whatsapp.net.NetworkUtil;
import com.whatsapp.storage.AccountStore;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.lcdui.*;
import java.io.InputStream;

/**
 * Startup WhatsApp Web Pairing Screen.
 * Shows WhatsApp green header and fetches & displays QR Code from Baileys Bridge
 * for instant scanning from WhatsApp on Android or iPhone.
 */
public class QRCodeScreen extends Canvas implements CommandListener, Runnable {

    private ScreenController controller;
    private Image qrImage;
    private Image[] loadingFrames;
    private int currentFrame = 0;
    private Thread animThread;
    private String statusText = Text.get("Conectando ao servidor (") + AccountStore.BRIDGE_HOST + ")...";
    private boolean isPolling = false;
    private Thread pollThread;

    private Command refreshCmd;
    private Command skipCmd;
    private Command exitCmd;

    public QRCodeScreen(ScreenController controller) {
        this.controller = controller;

        refreshCmd = new Command(Text.get("Atualizar QR"), Command.OK, 1);
        skipCmd = new Command(Text.get("Pular QR"), Command.SCREEN, 2);
        exitCmd = new Command(Text.get("Sair"), Command.EXIT, 3);

        addCommand(refreshCmd);
        addCommand(skipCmd);
        addCommand(exitCmd);

        setCommandListener(this);

        loadLoadingAssets();
        startPolling();
    }

    private void loadLoadingAssets() {
        loadingFrames = new Image[12];
        for (int i = 0; i < 12; i++) {
            try {
                loadingFrames[i] = Image.createImage("/loading_" + i + ".png");
            } catch (Exception e) {
                try {
                    loadingFrames[i] = Image.createImage("/loading.gif");
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void startAnimation() {
        if (animThread == null) {
            animThread = new Thread(new Runnable() {
                public void run() {
                    while (isPolling && qrImage == null) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            break;
                        }
                        currentFrame = (currentFrame + 1) % 12;
                        repaint();
                    }
                    animThread = null;
                }
            });
            animThread.start();
        }
    }

    public synchronized void startPolling() {
        if (!isPolling) {
            isPolling = true;
            pollThread = new Thread(this);
            pollThread.start();
            startAnimation();
        }
    }

    public synchronized void stopPolling() {
        isPolling = false;
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        if (animThread != null) {
            animThread.interrupt();
            animThread = null;
        }
    }

    public synchronized void reset(String message) {
        qrImage = null;
        currentFrame = 0;
        if (message != null && message.length() > 0) {
            statusText = message;
        } else {
            statusText = Text.get("Gerando QR Code...");
        }
        startPolling();
        repaint();
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        // Background
        g.setColor(Theme.COLOR_WHITE);
        g.fillRect(0, 0, width, height);

        // Header (WhatsApp Green Bar)
        int headerHeight = 34;
        g.setColor(Theme.COLOR_PRIMARY_TEAL);
        g.fillRect(0, 0, width, headerHeight);

        // Header Title
        g.setColor(Theme.COLOR_WHITE);
        Font titleFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        g.setFont(titleFont);
        g.drawString("WhatsApp Web", 10, (headerHeight - titleFont.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);

        Font boldFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
        Font smallFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        if (width >= 360) {
            // Landscape layout (BlackBerry Bold, Curve, Symbian E71)
            int leftX = 12;
            int startY = headerHeight + 12;

            g.setColor(Theme.COLOR_TEXT_MAIN);
            g.setFont(boldFont);
            g.drawString(Text.get("Conectar aparelho:"), leftX, startY, Graphics.TOP | Graphics.LEFT);

            g.setFont(smallFont);
            g.setColor(Theme.COLOR_TEXT_MUTED);
            int stepY = startY + boldFont.getHeight() + 6;
            g.drawString(Text.get("1. Abra o WhatsApp"), leftX, stepY, Graphics.TOP | Graphics.LEFT);
            stepY += smallFont.getHeight() + 3;
            g.drawString(Text.get("2. Aparelhos Conectados"), leftX, stepY, Graphics.TOP | Graphics.LEFT);
            stepY += smallFont.getHeight() + 3;
            g.drawString(Text.get("3. Aponte para esta tela"), leftX, stepY, Graphics.TOP | Graphics.LEFT);

            // Status message bottom-left
            int statusY = height - smallFont.getHeight() - 10;
            g.setFont(boldFont);
            if (statusText != null && statusText.startsWith(Text.get("Erro"))) {
                g.setColor(0xCC0000);
            } else {
                g.setColor(Theme.COLOR_PRIMARY_TEAL);
            }
            g.drawString(statusText, leftX, statusY, Graphics.TOP | Graphics.LEFT);

            // Right column: QR code pushed cleanly to the right edge
            int rightMargin = 15;
            if (qrImage != null) {
                int imgW = qrImage.getWidth();
                int imgH = qrImage.getHeight();
                int qrX = width - imgW - rightMargin;
                int qrY = headerHeight + (height - headerHeight - imgH) / 2;
                g.drawImage(qrImage, qrX, qrY, Graphics.TOP | Graphics.LEFT);
            } else {
                int boxSize = 130;
                int qrX = width - boxSize - rightMargin;
                int qrY = headerHeight + (height - headerHeight - boxSize) / 2;
                g.setColor(0xEEEEEE);
                g.fillRect(qrX, qrY, boxSize, boxSize);
                g.setColor(Theme.COLOR_DIVIDER);
                g.drawRect(qrX, qrY, boxSize, boxSize);

                int centerX = qrX + boxSize / 2;
                int centerY = qrY + boxSize / 2;
                if (loadingFrames != null && loadingFrames[currentFrame] != null) {
                    g.drawImage(loadingFrames[currentFrame], centerX, centerY, Graphics.HCENTER | Graphics.VCENTER);
                } else {
                    g.setColor(Theme.COLOR_TEXT_MUTED);
                    g.setFont(smallFont);
                    g.drawString(Text.get("Carregando QR..."), centerX, centerY - smallFont.getHeight() / 2, Graphics.TOP | Graphics.HCENTER);
                }
            }
        } else {
            // Portrait layout
            int qrCenterY = headerHeight + (height - headerHeight - 40) / 2;
            int qrCenterX = width / 2;

            if (qrImage != null) {
                g.drawImage(qrImage, qrCenterX, qrCenterY, Graphics.VCENTER | Graphics.HCENTER);
            } else {
                int boxSize = 130;
                int boxX = qrCenterX - boxSize / 2;
                int boxY = qrCenterY - boxSize / 2;
                g.setColor(0xEEEEEE);
                g.fillRect(boxX, boxY, boxSize, boxSize);
                g.setColor(Theme.COLOR_DIVIDER);
                g.drawRect(boxX, boxY, boxSize, boxSize);

                if (loadingFrames != null && loadingFrames[currentFrame] != null) {
                    g.drawImage(loadingFrames[currentFrame], qrCenterX, qrCenterY, Graphics.HCENTER | Graphics.VCENTER);
                } else {
                    g.setColor(Theme.COLOR_TEXT_MUTED);
                    g.setFont(smallFont);
                    g.drawString(Text.get("Carregando QR..."), qrCenterX, qrCenterY - smallFont.getHeight() / 2, Graphics.TOP | Graphics.HCENTER);
                }
            }

            g.setFont(smallFont);
            if (statusText != null && statusText.startsWith(Text.get("Erro"))) {
                g.setColor(0xCC0000);
            } else {
                g.setColor(Theme.COLOR_PRIMARY_TEAL);
            }
            g.drawString(statusText, width / 2, height - smallFont.getHeight() - 6, Graphics.TOP | Graphics.HCENTER);
        }
    }

    public void run() {
        while (isPolling) {
            try {
                AccountStore acc = AccountStore.getInstance();
                String devId = acc.getDeviceId();

                // 1. Fetch QR Image if not yet loaded
                if (qrImage == null) {
                    String qrUrl = acc.getBaseUrl() + "/api/device/" + devId + "/qr.png";
                    System.out.println(">>> [QR POLL] Fetching: " + qrUrl);
                    HttpConnection conn = null;
                    InputStream is = null;
                    try {
                        conn = NetworkUtil.openAndConnect(qrUrl, HttpConnection.GET, "Mozilla/5.0 (BlackBerry; U; BlackBerry 9000; en) AppleWebKit/534.11");
                        int resCode = conn.getResponseCode();
                        System.out.println(">>> [QR POLL] Response code: " + resCode);
                        if (resCode == HttpConnection.HTTP_OK) {
                            is = conn.openInputStream();
                            Image rawImg = Image.createImage(is);
                            if (rawImg != null && rawImg.getWidth() > 150) {
                                qrImage = scaleImage(rawImg, 146, 146);
                            } else {
                                qrImage = rawImg;
                            }
                            statusText = Text.get("Pronto para escanear!");
                            repaint();
                        } else if (resCode == 404) {
                            statusText = Text.get("Gerando QR Code...");
                            repaint();
                        } else {
                            statusText = Text.get("Servidor HTTP ") + resCode;
                            repaint();
                        }
                    } catch (Exception e) {
                        System.out.println(">>> [QR POLL EXCEPTION]: " + e.getClass().getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        String errMsg = e.getMessage() != null ? e.getMessage() : "";
                        if (errMsg.indexOf("404") >= 0) {
                            statusText = Text.get("Gerando QR Code...");
                        } else {
                            statusText = Text.get("Erro: ") + (errMsg.length() > 0 ? errMsg : e.getClass().getName());
                        }
                        repaint();
                    } finally {
                        if (is != null) { try { is.close(); } catch (Exception ignored) {} }
                        if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
                    }
                }

                // 2. Check Device Status
                String status = BaileysBridgeClient.getInstance().checkStatus();
                if ("connected".equals(status) || "open".equals(status)) {
                    statusText = Text.get("Conectado com sucesso!");
                    repaint();
                    stopPolling();
                    acc.setRegistered(true);
                    acc.save();
                    BaileysBridgeClient.getInstance().startBackgroundSync();
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                    controller.showChatList();
                    break;
                }

                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                break;
            } catch (Exception e) {
                try { Thread.sleep(4000); } catch (Exception ignored) {}
            }
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == refreshCmd) {
            qrImage = null;
            currentFrame = 0;
            statusText = Text.get("Buscando novo QR...");
            startAnimation();
            repaint();
        } else if (c == skipCmd) {
            stopPolling();
            AccountStore.getInstance().setRegistered(true);
            AccountStore.getInstance().save();
            BaileysBridgeClient.getInstance().startBackgroundSync();
            controller.showChatList();
        } else if (c == exitCmd) {
            stopPolling();
            controller.exitApp();
        }
    }

    private Image scaleImage(Image src, int targetW, int targetH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int[] srcRgb = new int[srcW * srcH];
        src.getRGB(srcRgb, 0, srcW, 0, 0, srcW, srcH);

        int[] dstRgb = new int[targetW * targetH];
        for (int y = 0; y < targetH; y++) {
            int srcY = (y * srcH) / targetH;
            int srcRow = srcY * srcW;
            int dstRow = y * targetW;
            for (int x = 0; x < targetW; x++) {
                int srcX = (x * srcW) / targetW;
                dstRgb[dstRow + x] = srcRgb[srcRow + srcX];
            }
        }
        return Image.createRGBImage(dstRgb, targetW, targetH, false);
    }
}
