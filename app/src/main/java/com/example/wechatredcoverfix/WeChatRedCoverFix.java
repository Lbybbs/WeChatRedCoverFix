package com.example.wechatredcoverfix;

import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 微信红包封面隐藏模块 (libxposed API 102, 无 UI)
 *
 * 原理: 拦截 View.setVisibility, 命中红包封面控件 id 的调用一律把参数改为 GONE。
 * 微信代码会在自定义封面红包上把这几个控件 setVisibility(VISIBLE) 并加载封面图,
 * 盖在 Monet 美化背景之上; 本模块强制它们永远不可见。
 *
 * 适配: 微信 8.0.72 (versionCode 3084/3085)
 */
public class WeChatRedCoverFix extends XposedModule {

    private static final String TAG = "WeChatRedCoverFix";

    // 微信 8.0.72 红包消息节点控件 id (来自 base.apk 资源表)
    private static final int ID_COVER_IMAGE = 0x7f090fb2;   // c7q: 自定义封面图
    private static final int ID_COVER_TEXTURE = 0x7f090fb3; // c7r: 封面纹理层
    private static final int ID_BUBBLE_TAIL = 0x7f091085;   // ccf: 气泡尾巴

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        try {
            Method setVisibility = View.class.getMethod("setVisibility", int.class);
            hook(setVisibility)
                    .setPriority(PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        View v = (View) chain.getThisObject();
                        if (v != null) {
                            int id = v.getId();
                            if (id == ID_COVER_IMAGE || id == ID_COVER_TEXTURE || id == ID_BUBBLE_TAIL) {
                                chain.getArgs().set(0, View.GONE);
                            }
                        }
                        return chain.proceed();
                    });
            log(Log.INFO, TAG, "红包封面隐藏 hook 已安装 (微信 8.0.72)");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "hook 安装失败", t);
        }
    }
}
