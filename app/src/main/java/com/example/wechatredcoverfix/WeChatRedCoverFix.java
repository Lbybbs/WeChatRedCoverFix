package com.example.wechatredcoverfix;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedInterface.Invoker;

/**
 * 微信红包封面隐藏 (libxposed API 102, 无 UI)
 *
 * 原理: 拦截 View.setVisibility 和 ImageView 图片加载, 命中红包封面控件时:
 *  1) 用 Invoker.Type.ORIGIN 调用原始 setVisibility(GONE) (绕过 hook 链)
 *  2) 短路原调用 (return null), 无论微信想设成什么, 控件永远 GONE
 *  3) 封面图片加载 (setImageBitmap/setImageResource) 直接短路, 连图都不加载
 *
 * 适配: 微信 8.0.72 (versionCode 3084/3085)
 * 诊断日志 tag: RedCoverDiag
 */
public class WeChatRedCoverFix extends XposedModule {

    private static final String TAG = "RedCoverDiag";

    // 微信 8.0.72 红包消息节点封面控件 id
    private static final int[] WATCH_IDS = {
            0x7f090fb2, // c7q 封面图
            0x7f090fb3, // c7r 纹理层
            0x7f091085  // ccf 气泡尾巴
    };

    private static String nameOf(int id) {
        switch (id) {
            case 0x7f090fb2: return "c7q封面图";
            case 0x7f090fb3: return "c7r纹理";
            case 0x7f091085: return "ccf尾巴";
            default: return "0x" + Integer.toHexString(id);
        }
    }

    private static boolean isWatch(int id) {
        for (int w : WATCH_IDS) if (w == id) return true;
        return false;
    }

    private static String viewState(View v) {
        try {
            return v.getClass().getName()
                    + " id=" + nameOf(v.getId())
                    + " vis=" + v.getVisibility()
                    + " alpha=" + String.format("%.2f", v.getAlpha())
                    + " rect=" + v.getLeft() + ',' + v.getTop() + '-' + v.getRight() + ',' + v.getBottom();
        } catch (Throwable t) {
            return "ERR " + t;
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            Method setVisibility = View.class.getMethod("setVisibility", int.class);
            hook(setVisibility)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null && isWatch(v.getId())) {
                            int want = (Integer) chain.getArg(0);
                            Log.i(TAG, "[HIT-setVisibility] " + viewState(v) + " want=" + want + " -> FORCE GONE");
                            // ORIGIN 调用原始方法强制 GONE (绕过 hook 链, 避免递归)
                            getInvoker((Method) chain.getExecutable())
                                    .setType(Invoker.Type.ORIGIN)
                                    .invoke(v, View.GONE);
                            return null; // 短路: 微信想要的可见性不生效
                        }
                        return chain.proceed();
                    });

            Method setBitmap = android.widget.ImageView.class.getMethod("setImageBitmap", Bitmap.class);
            hook(setBitmap)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null && isWatch(v.getId())) {
                            Log.i(TAG, "[BLOCK-setImageBitmap] " + viewState(v));
                            return null; // 封面图不加载
                        }
                        return chain.proceed();
                    });

            Method setRes = android.widget.ImageView.class.getMethod("setImageResource", int.class);
            hook(setRes)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null && isWatch(v.getId())) {
                            Log.i(TAG, "[BLOCK-setImageResource] " + viewState(v));
                            return null;
                        }
                        return chain.proceed();
                    });

            Method setDrawable = android.widget.ImageView.class.getMethod("setImageDrawable", android.graphics.drawable.Drawable.class);
            hook(setDrawable)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null && isWatch(v.getId())) {
                            Log.i(TAG, "[BLOCK-setImageDrawable] " + viewState(v));
                            return null;
                        }
                        return chain.proceed();
                    });

            Log.i(TAG, "封面隐藏 v2 已装载: setVisibility短路 + 图片加载短路");
        } catch (Throwable t) {
            Log.e(TAG, "hook 安装失败: " + t, t);
        }
    }
}
