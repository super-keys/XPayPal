package com.android.system.ui;

import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PTM App 安全检测绕过 Xposed 模块
 * 
 * 核心问题: User-Agent 中 source=unknown
 * 原因: getInstallerPackageName() 返回 null
 * 
 * 解决方案:
 * 1. Hook 具体实现类 android.app.ApplicationPackageManager (不是抽象类 PackageManager)
 * 2. Hook InstallSourceInfo 的 getter 方法
 * 3. Hook xf.AbstractC9287h.l 静态字段 (User-Agent 缓存)
 */
public class PayPal implements IXposedHookLoadPackage {
    private static final String TAG = "[PaytmHook]";
    private static final String TARGET_PACKAGE = "net.one97.paytm";
    private static final String GOOGLE_PLAY_PACKAGE = "com.android.vending";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + " handleLoadPackage: " + lpparam.packageName);
        
        // ==================================================
        // 关键: Hook ApplicationPackageManager (具体实现类)
        // PackageManager 是抽象类，不能直接 hook
        // ==================================================
        hookApplicationPackageManager();
        
        // ==================================================
        // Hook InstallSourceInfo 的 getter 方法
        // ==================================================
        hookInstallSourceInfoGetters();
        
        // ==================================================
        // 只对目标包进行应用层 hook
        // ==================================================
        if (TARGET_PACKAGE.equals(lpparam.packageName)) {
            XposedBridge.log(TAG + " Target package detected, applying app-level hooks");
            
            // Hook 应用内的检测方法
            hookAppMethods(lpparam);
            
            // Hook User-Agent 构建相关
            hookUserAgentBuilder(lpparam);
            
            // Hook 安装来源枚举
            hookInstallSourceEnum(lpparam);
            
            // 显示 Toast 确认 Hook 生效
            hookMainActivityForToast(lpparam);
        }
    }

    /**
     * 核心 Hook 1: Hook ApplicationPackageManager.getInstallerPackageName
     * 这是 PackageManager 的具体实现类
     */
    private void hookApplicationPackageManager() {
        try {
            // Hook android.app.ApplicationPackageManager (具体实现类)
            Class<?> apmClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager", 
                null  // 使用 null 获取系统类加载器
            );
            
            XposedHelpers.findAndHookMethod(
                apmClass,
                "getInstallerPackageName",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String packageName = (String) param.args[0];
                        if (TARGET_PACKAGE.equals(packageName)) {
                            String original = (String) param.getResult();
                            XposedBridge.log(TAG + " getInstallerPackageName(" + packageName + "): " + 
                                    (original == null ? "null" : original) + " -> " + GOOGLE_PLAY_PACKAGE);
                            param.setResult(GOOGLE_PLAY_PACKAGE);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked ApplicationPackageManager.getInstallerPackageName()");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook ApplicationPackageManager: " + e.getMessage());
        }

        // SDK >= 30: Hook getInstallSourceInfo
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Class<?> apmClass = XposedHelpers.findClass(
                    "android.app.ApplicationPackageManager", 
                    null
                );
                
                XposedHelpers.findAndHookMethod(
                    apmClass,
                    "getInstallSourceInfo",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            if (TARGET_PACKAGE.equals(packageName)) {
                                Object result = param.getResult();
                                if (result != null) {
                                    // 修改 InstallSourceInfo 的内部字段
                                    try {
                                        Field installingField = result.getClass().getDeclaredField("mInstallingPackageName");
                                        installingField.setAccessible(true);
                                        String oldValue = (String) installingField.get(result);
                                        installingField.set(result, GOOGLE_PLAY_PACKAGE);
                                        
                                        Field initiatingField = result.getClass().getDeclaredField("mInitiatingPackageName");
                                        initiatingField.setAccessible(true);
                                        initiatingField.set(result, GOOGLE_PLAY_PACKAGE);
                                        
                                        Field originatingField = result.getClass().getDeclaredField("mOriginatingPackageName");
                                        originatingField.setAccessible(true);
                                        originatingField.set(result, GOOGLE_PLAY_PACKAGE);
                                        
                                        XposedBridge.log(TAG + " getInstallSourceInfo: Modified fields -> " + GOOGLE_PLAY_PACKAGE);
                                    } catch (Exception e) {
                                        XposedBridge.log(TAG + " Failed to modify InstallSourceInfo fields: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                );
                XposedBridge.log(TAG + " ✓ Hooked ApplicationPackageManager.getInstallSourceInfo()");
            } catch (Throwable e) {
                XposedBridge.log(TAG + " ✗ Failed to hook getInstallSourceInfo: " + e.getMessage());
            }
        }
    }

    /**
     * 核心 Hook 2: Hook InstallSourceInfo 的所有 getter 方法
     */
    private void hookInstallSourceInfoGetters() {
        if (Build.VERSION.SDK_INT < 30) return;
        
        try {
            Class<?> isiClass = InstallSourceInfo.class;
            
            // Hook getInstallingPackageName
            XposedHelpers.findAndHookMethod(
                isiClass,
                "getInstallingPackageName",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String result = (String) param.getResult();
                        if (result == null || !GOOGLE_PLAY_PACKAGE.equals(result)) {
                            XposedBridge.log(TAG + " InstallSourceInfo.getInstallingPackageName: " + 
                                    result + " -> " + GOOGLE_PLAY_PACKAGE);
                            param.setResult(GOOGLE_PLAY_PACKAGE);
                        }
                    }
                }
            );
            
            // Hook getInitiatingPackageName
            XposedHelpers.findAndHookMethod(
                isiClass,
                "getInitiatingPackageName",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String result = (String) param.getResult();
                        if (result == null || !GOOGLE_PLAY_PACKAGE.equals(result)) {
                            param.setResult(GOOGLE_PLAY_PACKAGE);
                        }
                    }
                }
            );
            
            // Hook getOriginatingPackageName
            XposedHelpers.findAndHookMethod(
                isiClass,
                "getOriginatingPackageName",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String result = (String) param.getResult();
                        if (result == null || !GOOGLE_PLAY_PACKAGE.equals(result)) {
                            param.setResult(GOOGLE_PLAY_PACKAGE);
                        }
                    }
                }
            );
            
            XposedBridge.log(TAG + " ✓ Hooked InstallSourceInfo getters");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook InstallSourceInfo: " + e.getMessage());
        }
    }

    /**
     * Hook 应用内的检测方法
     */
    private void hookAppMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook Pg.AbstractC0768b.b(Context) - 返回 PLAY_STORE (1)
        try {
            Class<?> cls = XposedHelpers.findClass("Pg.AbstractC0768b", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                cls,
                "b",
                Context.class,
                XC_MethodReplacement.returnConstant(1) // PLAY_STORE = 1
            );
            XposedBridge.log(TAG + " ✓ Hooked Pg.AbstractC0768b.b() -> 1");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook AbstractC0768b: " + e.getMessage());
        }

        // Hook AppUtilityKT.getAppInstallerPackageName
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.business.merchant_payments.common.utility.AppUtilityKT", 
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                cls,
                "getAppInstallerPackageName",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + " AppUtilityKT.getAppInstallerPackageName -> " + GOOGLE_PLAY_PACKAGE);
                        param.setResult(GOOGLE_PLAY_PACKAGE);
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked AppUtilityKT.getAppInstallerPackageName()");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook AppUtilityKT: " + e.getMessage());
        }
    }

    /**
     * Hook User-Agent 构建相关代码
     * 关键: xf.AbstractC9287h.l 字段存储了 User-Agent
     */
    private void hookUserAgentBuilder(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook AbstractC8011v.o(Context) - User-Agent 构建方法
        try {
            Class<?> cls = XposedHelpers.findClass(
                "net.one97.paytm.utils.AbstractC8011v", 
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                cls,
                "o",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String userAgent = (String) param.getResult();
                        if (userAgent != null && userAgent.contains("source=unknown")) {
                            // 替换 source=unknown 为 source=com.android.vending
                            String newUserAgent = userAgent.replace(
                                "source=unknown", 
                                "source=" + GOOGLE_PLAY_PACKAGE
                            );
                            XposedBridge.log(TAG + " Fixed User-Agent source: unknown -> " + GOOGLE_PLAY_PACKAGE);
                            param.setResult(newUserAgent);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked AbstractC8011v.o() for User-Agent");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook AbstractC8011v.o: " + e.getMessage());
        }

        // Hook xf.AbstractC9287h.l 字段 (User-Agent 缓存)
        try {
            Class<?> cls = XposedHelpers.findClass("xf.AbstractC9287h", lpparam.classLoader);
            
            // 读取并修改静态字段
            XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    fixUserAgentCache(cls);
                }
            });
            
            XposedBridge.log(TAG + " ✓ Hooked xf.AbstractC9287h constructor");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook AbstractC9287h: " + e.getMessage());
        }

        // Hook PBNetworkModule.getUserAgent
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.paytmbank.networkmodule.utils.PBNetworkModule", 
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                cls,
                "getUserAgent",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String userAgent = (String) param.getResult();
                        if (userAgent != null && userAgent.contains("source=unknown")) {
                            String fixed = userAgent.replace("source=unknown", "source=" + GOOGLE_PLAY_PACKAGE);
                            XposedBridge.log(TAG + " Fixed PBNetworkModule.getUserAgent source");
                            param.setResult(fixed);
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked PBNetworkModule.getUserAgent()");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook PBNetworkModule: " + e.getMessage());
        }

        // Hook PBNetworkModule.setUserAgent
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.paytmbank.networkmodule.utils.PBNetworkModule", 
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                cls,
                "setUserAgent",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String userAgent = (String) param.args[0];
                        if (userAgent != null && userAgent.contains("source=unknown")) {
                            String fixed = userAgent.replace("source=unknown", "source=" + GOOGLE_PLAY_PACKAGE);
                            param.args[0] = fixed;
                            XposedBridge.log(TAG + " Fixed PBNetworkModule.setUserAgent source");
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked PBNetworkModule.setUserAgent()");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook setUserAgent: " + e.getMessage());
        }

        // Hook com.google.android.gms.internal.mlkit_common.y.getUserAgent
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.google.android.gms.internal.mlkit_common.y", 
                lpparam.classLoader
            );
            XposedHelpers.findAndHookMethod(
                cls,
                "getUserAgent",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String userAgent = (String) param.getResult();
                        if (userAgent != null && userAgent.contains("source=unknown")) {
                            String fixed = userAgent.replace("source=unknown", "source=" + GOOGLE_PLAY_PACKAGE);
                            param.setResult(fixed);
                            XposedBridge.log(TAG + " Fixed mlkit_common.y.getUserAgent source");
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " ✓ Hooked mlkit_common.y.getUserAgent()");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook mlkit_common.y: " + e.getMessage());
        }
    }

    /**
     * 修复 User-Agent 缓存中的 source 值
     */
    private void fixUserAgentCache(Class<?> cls) {
        try {
            Field field = cls.getDeclaredField("l");
            field.setAccessible(true);
            String userAgent = (String) field.get(null);
            if (userAgent != null && userAgent.contains("source=unknown")) {
                String fixed = userAgent.replace("source=unknown", "source=" + GOOGLE_PLAY_PACKAGE);
                field.set(null, fixed);
                XposedBridge.log(TAG + " Fixed xf.AbstractC9287h.l cache");
            }
        } catch (Throwable e) {
            // 忽略错误
        }
    }

    /**
     * Hook 安装来源枚举
     */
    private void hookInstallSourceEnum(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass("Pg.AbstractC0768b", lpparam.classLoader);
            // 设置静态字段 f11836a 为 PLAY_STORE (1)
            XposedHelpers.setStaticObjectField(cls, "f11836a", Integer.valueOf(1));
            XposedBridge.log(TAG + " ✓ Set Pg.AbstractC0768b.f11836a = 1 (PLAY_STORE)");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to set install source enum: " + e.getMessage());
        }
    }

    /**
     * Hook MainActivity 显示 Toast 确认
     */
    private void hookMainActivityForToast(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                "net.one97.paytm.landingpage.activity.AJRMainActivity", 
                lpparam.classLoader, 
                "onCreate", 
                "android.os.Bundle", 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Context ctx = (Context) param.thisObject;
                        Toast.makeText(ctx, "Hello Long Sheng !!!", Toast.LENGTH_LONG).show();
                        XposedBridge.log(TAG + " ✓ MainActivity onCreate - Hook confirmed!");
                    }
                }
            );
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ✗ Failed to hook MainActivity: " + e.getMessage());
        }
    }
}
