package com.phoenixcorp.overlay;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

public class SelectCaptureArea {

    public static Optional<Rectangle> selectInteractive() {
        try {
            final Rectangle[] result = { null };
            final Object lock = new Object();

            SwingUtilities.invokeLater(() -> {
                JWindow overlay = new JWindow();
                overlay.setAlwaysOnTop(true);
                overlay.setBackground(new Color(0,0,0,10));

                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle bounds = ge.getMaximumWindowBounds();
                overlay.setBounds(bounds);

                JPanel panel = new JPanel() {
                    { setOpaque(false); }
                    Point start, end;
                    @Override protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (start != null && end != null) {
                            int x = Math.min(start.x, end.x), y = Math.min(start.y, end.y);
                            int w = Math.abs(end.x - start.x), h = Math.abs(end.y - start.y);
                            g.setColor(new Color(0, 120, 215, 80));  g.fillRect(x,y,w,h);
                            g.setColor(new Color(0, 120, 215, 200)); g.drawRect(x,y,w,h);
                        }
                    }
                };

                panel.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { panel.putClientProperty("start", e.getPoint()); }
                    @Override public void mouseReleased(MouseEvent e) {
                        Point s = (Point) panel.getClientProperty("start");
                        if (s != null) {
                            int x = Math.min(s.x, e.getX()), y = Math.min(s.y, e.getY());
                            int w = Math.abs(e.getX() - s.x), h = Math.abs(e.getY() - s.y);
                            result[0] = new Rectangle(x, y, w, h);
                        }
                        overlay.dispose();
                        synchronized (lock) { lock.notifyAll(); }
                    }
                });

                panel.addMouseMotionListener(new MouseAdapter() {
                    @Override public void mouseDragged(MouseEvent e) { panel.putClientProperty("end", e.getPoint()); panel.repaint(); }
                });

                overlay.setContentPane(panel);
                overlay.setVisible(true);
            });

            synchronized (lock) { lock.wait(15000); } // timeout sécu
            return Optional.ofNullable(result[0]);
        } catch (InterruptedException e) {
            return Optional.empty();
        } catch (Throwable t) {
            System.err.println("[SelectCaptureArea] " + t.getMessage());
            return Optional.empty();
        }
    }
}
