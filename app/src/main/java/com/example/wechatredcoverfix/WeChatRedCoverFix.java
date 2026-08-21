package com.example.wechatredcoverfix;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 微信红包封面隐藏 + 诊断版 (libxposed API 102, 无 UI)
 *
 * 功能:
 *  1) 拦截 View.setVisibility / ImageView 图片加载, 命中红包封面控件时打印完整信息 + 调用栈
 *  2) 每 2 秒扫描窗口视图树, 报告红包封面控件的实时状态 (vis/alpha/rect/父链)
 *  诊断日志 tag: RedCoverDiag, 用 logcat 拉取
 */
public class WeChatRedCoverFix extends XposedModule {

    private static final String TAG = "RedCoverDiag";

    // 微信 8.0.72 红包消息节点全部绑定控件 id (a4.smali)
    private static final int[] WATCH_IDS = {
            0x7f090fb2, // c7q 封面图
            0x7f090fb3, // c7r 纹理层
            0x7f091085, // ccf 气泡尾巴
            0x7f090fb0, // H
            0x7f090fb1, // G
            0x7f090fb4, // y
            0x7f090fb5, // w
            0x7f090fb6, // x
            0x7f090faf  // I
    };

    private static String nameOf(int id) {
        switch (id) {
            case 0x7f090fb2: return "c7q封面图";
            case 0x7f090fb3: return "c7r纹理";
            case 0x7f091085: return "ccf尾巴";
            case 0x7f090fb0: return "H";
            case 0x7f090fb1: return "G";
            case 0x7f090fb4: return "y";
            case 0x7f090fb5: return "w";
            case 0x7f090fb6: return "x";
            case 0x7f090faf: return "I";
            default: return "0x" + Integer.toHexString(id);
        }
    }

    private static boolean isWatch(int id) {
        for (int w : WATCH_IDS) if (w == id) return true;
        return false;
    }

    private static String stack() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 3; i < Math.min(st.length, 16); i++) {
            sb.append(st[i].toString()).append('\n');
        }
        return sb.toString();
    }

    private static String viewState(View v) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(v.getClass().getName());
            sb.append(" id=").append(nameOf(v.getId()));
            sb.append(" vis=").append(v.getVisibility());
            sb.append(" alpha=").append(String.format("%.2f", v.getAlpha()));
            sb.append(" rect=").append(v.getLeft()).append(',').append(v.getTop())
                    .append('-').append(v.getRight()).append(',').append(v.getBottom());
            sb.append(" (").append(v.getWidth()).append('x').append(v.getHeight()).append(')');
            View p = (View) v.getParent();
            sb.append(" parent=").append(p == null ? "null" : p.getClass().getName() + "#0x" + Integer.toHexString(p.getId()));
            return sb.toString();
        } catch (Throwable t) {
            return "ERR " + t;
        }
    }

    private static String parentChain(View v) {
        StringBuilder sb = new StringBuilder();
        View p = v;
        int depth = 0;
        while (p != null && depth < 8) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(p.getClass().getSimpleName()).append("#0x").append(Integer.toHexString(p.getId()))
                    .append("(vis=").append(p.getVisibility()).append(')');
            p = (View) p.getParent();
            depth++;
        }
        return sb.toString();
    }

    // ---- 定时扫描 ----
    private final Set<String> seenStates = new HashSet<>();

    private void scanWindows() {
        try {
            Class<?> wmgClass = Class.forName("android.view.WindowManagerGlobal");
            Object instance = wmgClass.getMethod("getInstance").invoke(null);
            Object roots = wmgClass.getMethod("getViewRootImpls").invoke(instance);
            List<?> list = (List<?>) roots;
            for (Object vri : list) {
                if (vri == null) continue;
                View root = (View) vri.getClass().getMethod("getView").invoke(vri);
                if (root != null) walkAndScan(root);
            }
        } catch (Throwable t) {
            Log.w(TAG, "scanWindows err: " + t);
        }
    }

    private void walkAndScan(View v) {
        if (v == null) return;
        int id = v.getId();
        if (isWatch(id)) {
            String state = nameOf(id) + "|" + v.getVisibility() + "|" + String.format("%.2f", v.getAlpha())
                    + "|" + v.getLeft() + "," + v.getTop() + "-" + v.getRight() + "," + v.getBottom();
            if (seenStates.add(state)) {
                Log.i(TAG, "[SCAN] " + viewState(v) + " 父链: " + parentChain(v));
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                walkAndScan(vg.getChildAt(i));
            }
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            // 1) setVisibility 拦截
            Method setVisibility = View.class.getMethod("setVisibility", int.class);
            hook(setVisibility)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null && isWatch(v.getId())) {
                            Log.i(TAG, "[HIT-setVisibility] " + viewState(v) + " -> " + chain.getArg(0));
                            Log.i(TAG, "[STACK]\n" + stack());
                            chain.getArgs().set(0, View.GONE); // 强制隐藏
                        }
                        return chain.proceed();
                    });

            // 2) 图片加载拦截
            Class<?> ivClass = Class.forName("android.widget.ImageView");
            Method setBitmap = ivClass.getMethod("setImageBitmap", android.graphics.Bitmap.class);
            hook(setBitmap).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                View v = (View) chain.getThisObject();
                if (v != null && isWatch(v.getId())) {
                    Log.i(TAG, "[HIT-setImageBitmap] " + viewState(v));
                    Log.i(TAG, "[STACK]\n" + stack());
                }
                return chain.proceed();
            });
            Method setRes = ivClass.getMethod("setImageResource", int.class);
            hook(setRes).setPriority(PRIORITY_HIGHEST).intercept(chain -> {
                View v = (View) chain.getThisObject();
                if (v != null && isWatch(v.getId())) {
                    Log.i(TAG, "[HIT-setImageResource] " + viewState(v) + " res=0x" + Integer.toHexString((Integer) chain.getArg(0)));
                }
                return chain.proceed();
            });

            // 3) 定时扫描视图树
            Timer timer = new Timer("RedCoverScan", true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    scanWindows();
                }
            }, 2000, 2000);

            Log.i(TAG, "诊断版已装载: hook setVisibility + ImageView, 定时扫描 2s");
        } catch (Throwable t) {
            Log.e(TAG, "hook 安装失败: " + t, t);
        }
    }
}
