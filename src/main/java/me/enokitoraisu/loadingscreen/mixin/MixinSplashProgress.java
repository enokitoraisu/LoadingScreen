package me.enokitoraisu.loadingscreen.mixin;

import me.enokitoraisu.loadingscreen.LoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.FMLLog;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.SharedDrawable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;

@Mixin(value = SplashProgress.class, remap = false)
public class MixinSplashProgress {
    @Shadow private static Thread thread;
    @Shadow private static volatile Throwable threadError;
    @Shadow private static volatile boolean done;
    @Shadow private static volatile boolean pause;
    @Shadow private static boolean enabled;
    @Shadow private static Drawable d;
    @Shadow private static Properties config;
    @Shadow @Final static Semaphore mutex;
    @Shadow @Final private static Lock lock;
    @Shadow private static boolean disableSplash(Exception e) { throw new AssertionError(); }
    @Shadow private static void checkThreadState() { throw new AssertionError(); }
    @Shadow private static boolean getBool(String name, boolean def) { throw new AssertionError(); }

    @Inject(method = "start()V", at = @At(value = "HEAD"), cancellable = true)
    private static void startSplash(CallbackInfo ci) {
        ci.cancel();
        File configFile = new File(Minecraft.getMinecraft().gameDir, "config/splash.properties");
        File parent = configFile.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        config = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(configFile.toPath()), StandardCharsets.UTF_8)) {
            config.load(r);
        } catch(IOException e) {
            FMLLog.log.info("Could not load splash.properties, will create a default one");
        }
        boolean defaultEnabled = true;
        enabled = getBool("enabled", defaultEnabled)/* && (!FMLClientHandler.instance().hasOptifine() || Launch.blackboard.containsKey("optifine.ForgeSplashCompatible"))*/;

        if(!enabled) return;
        try {
            d = new SharedDrawable(Display.getDrawable());
            Display.getDrawable().releaseContext();
            d.makeCurrent();
        } catch (LWJGLException e) {
            FMLLog.log.error("Error starting SplashProgress:", e);
            disableSplash(e);
        }
        thread = new Thread(() -> {
            LoadingScreen.setGL(lock);
            while (!done) {
                LoadingScreen.draw();
                mutex.acquireUninterruptibly();
                Display.update();
                mutex.release();
                if(pause) {
                    LoadingScreen.clearGL(lock);
                    LoadingScreen.setGL(lock);
                }
            }
            LoadingScreen.clearGL(lock);
        });
        thread.setUncaughtExceptionHandler((t, e) -> {
            FMLLog.log.error("Splash thread Exception", e);
            threadError = e;
        });
        thread.start();
        checkThreadState();
    }

    @Inject(method = "finish()V", at = @At(value = "HEAD"), cancellable = true)
    private static void finishSplash(CallbackInfo ci) {
        if (enabled) {
            ci.cancel();
            try {
                checkThreadState();
                done = true;
                LoadingScreen.disable(thread, d);
            } catch (Exception e) {
                FMLLog.log.error("Error finishing SplashProgress:", e);
                disableSplash(e);
            }
        }
    }
}
