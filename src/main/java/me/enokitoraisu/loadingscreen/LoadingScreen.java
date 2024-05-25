package me.enokitoraisu.loadingscreen;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.ProgressManager;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;

import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import static org.lwjgl.opengl.GL11.*;

public class LoadingScreen {
    public static void disable(Thread thread, Drawable d) throws LWJGLException, InterruptedException {
        thread.join();
        glFlush();
        d.releaseContext();
        Display.getDrawable().makeCurrent();
    }

    public static void draw() {
        ProgressManager.ProgressBar first = null, penult = null, last = null;
        Iterator<ProgressManager.ProgressBar> i = ProgressManager.barIterator();
        while (i.hasNext()) {
            if (first == null) first = i.next();
            else {
                penult = last;
                last = i.next();
            }
        }

        glClear(GL_COLOR_BUFFER_BIT);
        int width = Display.getWidth();
        int height = Display.getHeight();
        glViewport(0, 0, width, height);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        int w = 500;
        int h = 10;

        float x = width / 2f - w / 2f;
        float y = height / 2f - h / 2f;

        drawProgressBar(first, x, y - 50, w, h, 0xFF28897F);
        drawProgressBar(penult, x, y, w, h, 0xFF20a59b);
        drawProgressBar(last, x, y + 50, w, h, 0xFFb6d7ce);
    }

    public static void drawProgressBar(ProgressManager.ProgressBar progressBar, float x, float y, float w, float h, int color) {
        if (progressBar != null) {
            glColor3ub((byte)((color >> 16) & 0xFF), (byte)((color >> 8) & 0xFF), (byte)(color & 0xFF));
            drawBox(x, y, w * ((float) progressBar.getStep() / progressBar.getSteps()), h);
        }
    }

    private static void drawBox(float x, float y, float w, float h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x, y + h);
        glVertex2f(x + w, y + h);
        glVertex2f(x + w, y);
        glEnd();
    }

    public static void setGL(Lock lock) {
        lock.lock();
        try {
            Display.getDrawable().makeCurrent();
        } catch (LWJGLException e) {
            FMLLog.log.error("Error setting GL context:", e);
            throw new RuntimeException(e);
        }
        glClearColor(0.1F, 0.1F, 0.1F, 1F);
        glDisable(GL_LIGHTING);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public static void clearGL(Lock lock) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayWidth = Display.getWidth();
        mc.displayHeight = Display.getHeight();
        mc.resize(mc.displayWidth, mc.displayHeight);
        glClearColor(1, 1, 1, 1);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, .1f);
        try {
            Display.getDrawable().releaseContext();
        } catch (LWJGLException e) {
            FMLLog.log.error("Error releasing GL context:", e);
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }
}
