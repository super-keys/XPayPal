package com.android.system.ui;

import android.content.Context;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PayPal implements IXposedHookLoadPackage {
    private static final String PAYTM_PACKAGE = "net.one97.paytm";
    private static final String GOOGLE_PLAY_PACKAGE = "com.android.vending";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        log("进来了");
        ClassLoader classLoader = lpparam.classLoader;


        try {
            XposedHelpers.findAndHookMethod("net.one97.paytm.landingpage.activity.AJRMainActivity", classLoader, "onCreate", "android.os.Bundle", new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Context ctx = (Context) param.thisObject;
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                    Toast.makeText(ctx, "Hello: Long Sheng", Toast.LENGTH_LONG).show();
                }
            });
        } catch (Throwable e) {

        }

        try {
            hookPackageManagerMethods();
            log("hook hookPackageManagerMethods ok");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            hookAppSpecificMethods(lpparam);
            log("hook hookAppSpecificMethods ok");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            hookAppSpecificMethods2(lpparam);
            log("hook hookAppSpecificMethods2 ok");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            hookGetInstallerPackageName();
            log("hook hookGetInstallerPackageName ok");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            hookGetInstallSourceInfo();
            log("hook hookGetInstallSourceInfo ok");
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    /**
     * Hook PackageManager.getInstallerPackageName()
     * 这是最核心的方法，所有检测都依赖它
     */
    private void hookGetInstallerPackageName() {
        try {
            XposedHelpers.findAndHookMethod(
                    PackageManager.class,
                    "getInstallerPackageName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];

                            // 只对Paytm应用本身返回Google Play包名
                            if (PAYTM_PACKAGE.equals(packageName)) {
                                String original = (String) param.getResult();
                                if (original == null || original.isEmpty()) {
                                    XposedBridge.log("[PaytmHook] getInstallerPackageName: null -> " + GOOGLE_PLAY_PACKAGE);
                                    param.setResult(GOOGLE_PLAY_PACKAGE);
                                } else {
                                    XposedBridge.log("[PaytmHook] getInstallerPackageName: " + original + " -> " + GOOGLE_PLAY_PACKAGE);
                                    param.setResult(GOOGLE_PLAY_PACKAGE);
                                }
                            }
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked getInstallerPackageName()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook getInstallerPackageName: " + e);
        }
    }

    /**
     * Hook PackageManager.getInstallSourceInfo() - Android 11+
     * 这个方法在Android 11及以上版本使用
     */
    private void hookGetInstallSourceInfo() {
        try {
            XposedHelpers.findAndHookMethod(
                    PackageManager.class,
                    "getInstallSourceInfo",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];

                            // 只对Paytm应用本身处理
                            if (PAYTM_PACKAGE.equals(packageName)) {
                                InstallSourceInfo original = (InstallSourceInfo) param.getResult();

                                if (original == null ||
                                        original.getInstallingPackageName() == null ||
                                        !GOOGLE_PLAY_PACKAGE.equals(original.getInstallingPackageName())) {

                                    // 创建一个假的InstallSourceInfo对象
                                    try {
                                        InstallSourceInfo fakeInfo = createFakeInstallSourceInfo();
                                        XposedBridge.log("[PaytmHook] getInstallSourceInfo: replaced with Google Play");
                                        param.setResult(fakeInfo);
                                    } catch (Exception e) {
                                        XposedBridge.log("[PaytmHook] Failed to create fake InstallSourceInfo: " + e);
                                    }
                                }
                            }
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked getInstallSourceInfo()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook getInstallSourceInfo: " + e);
        }
    }

    /**
     * 创建假的InstallSourceInfo对象
     * 注意：InstallSourceInfo是final类，无法直接创建，需要反射或使用XposedHelpers
     */
    private InstallSourceInfo createFakeInstallSourceInfo() {
        // 由于InstallSourceInfo是final类，我们需要使用反射或者Hook其getter方法
        // 这里返回null，实际应该Hook InstallSourceInfo的getter方法
        return null;
    }

    private static void log(String log) {
        XposedBridge.log(log);
    }

    /**
     * Hook PackageManager的核心方法
     */
    private void hookPackageManagerMethods() {
        // Hook 1: getInstallerPackageName() - 所有Android版本
        try {
            XposedHelpers.findAndHookMethod(
                    PackageManager.class,
                    "getInstallerPackageName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            if (PAYTM_PACKAGE.equals(packageName)) {
                                String original = (String) param.getResult();
                                if (original == null || original.isEmpty() ||
                                        !GOOGLE_PLAY_PACKAGE.equals(original)) {
                                    XposedBridge.log("[PaytmHook] getInstallerPackageName: " +
                                            (original == null ? "null" : original) + " -> " + GOOGLE_PLAY_PACKAGE);
                                    param.setResult(GOOGLE_PLAY_PACKAGE);
                                }
                            }
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] ✓ Hooked PackageManager.getInstallerPackageName()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] ✗ Failed to hook getInstallerPackageName: " + e);
        }

        // Hook 2: getInstallSourceInfo() - Android 11+
        try {
            XposedHelpers.findAndHookMethod(
                    PackageManager.class,
                    "getInstallSourceInfo",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String packageName = (String) param.args[0];
                            if (PAYTM_PACKAGE.equals(packageName)) {
                                InstallSourceInfo original = (InstallSourceInfo) param.getResult();

                                if (original != null) {
                                    // Hook InstallSourceInfo的getter方法
                                    hookInstallSourceInfoGetters(original);
                                } else {
                                    // 如果返回null，尝试创建一个假的
                                    XposedBridge.log("[PaytmHook] getInstallSourceInfo returned null");
                                }
                            }
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] ✓ Hooked PackageManager.getInstallSourceInfo()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] ✗ Failed to hook getInstallSourceInfo: " + e);
        }
    }

    /**
     * Hook InstallSourceInfo的getter方法
     * 因为InstallSourceInfo是final类，我们Hook它的getter方法
     */
    private void hookInstallSourceInfoGetters(InstallSourceInfo original) {
        try {
            // Hook getInstallingPackageName()
            XposedHelpers.findAndHookMethod(
                    InstallSourceInfo.class,
                    "getInstallingPackageName",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String result = (String) param.getResult();
                            if (result == null || !GOOGLE_PLAY_PACKAGE.equals(result)) {
                                XposedBridge.log("[PaytmHook] InstallSourceInfo.getInstallingPackageName: " +
                                        (result == null ? "null" : result) + " -> " + GOOGLE_PLAY_PACKAGE);
                                param.setResult(GOOGLE_PLAY_PACKAGE);
                            }
                        }
                    }
            );

            // Hook getInitiatingPackageName()
            XposedHelpers.findAndHookMethod(
                    InstallSourceInfo.class,
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

            // Hook getOriginatingPackageName()
            XposedHelpers.findAndHookMethod(
                    InstallSourceInfo.class,
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

            XposedBridge.log("[PaytmHook] ✓ Hooked InstallSourceInfo getters");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] ✗ Failed to hook InstallSourceInfo getters: " + e);
        }
    }

    /**
     * Hook应用中的特定检测方法
     */
    private void hookAppSpecificMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        // 需要Hook的类和方法列表
        String[][] methodsToHook = {
                // {类名, 方法名, 参数类型}
                {"com.business.merchant_payments.common.utility.AppUtilityKT",
                        "getAppInstallerPackageName", "android.content.Context"},
                {"net.one97.paytm.dynamic.module.eduforms.activity.EduformsInitActivity",
                        "isPlayStoreInstall", ""},
                {"net.one97.paytm.finance.FinanceDataProvider",
                        "isPlayStoreInstall", "android.content.Context"},
                {"com.business.merchant_payments.common.utility.AppUtility",
                        "isPlayStoreInstall", "android.content.Context"},
                {"net.one97.paytm.nativesdk.Utils.SDKUtility",
                        "isPlayStoreInstall", "android.content.Context"},
                {"net.one97.paytm.dynamic.module.mall.communicator.helper.CJRDefaultRequestParam",
                        "isPlayStoreInstall", "android.content.Context"},
        };

        for (String[] methodInfo : methodsToHook) {
            String className = methodInfo[0];
            String methodName = methodInfo[1];
            String paramTypes = methodInfo[2];

            try {
                Class<?> targetClass = XposedHelpers.findClass(className, lpparam.classLoader);

                if (paramTypes.isEmpty()) {
                    // 无参数方法
                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            methodName,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (param.getResult() instanceof Boolean) {
                                        Boolean result = (Boolean) param.getResult();
                                        if (!result) {
                                            XposedBridge.log("[PaytmHook] " + className + "." + methodName +
                                                    ": false -> true");
                                            param.setResult(true);
                                        }
                                    } else if (param.getResult() instanceof String) {
                                        String result = (String) param.getResult();
                                        if (result == null || result.isEmpty() ||
                                                !GOOGLE_PLAY_PACKAGE.equals(result)) {
                                            XposedBridge.log("[PaytmHook] " + className + "." + methodName +
                                                    ": " + (result == null ? "null" : result) + " -> " + GOOGLE_PLAY_PACKAGE);
                                            param.setResult(GOOGLE_PLAY_PACKAGE);
                                        }
                                    }
                                }
                            }
                    );
                } else {
                    // 有参数方法（通常是Context）
                    Class<?>[] paramClasses = {android.content.Context.class};
                    XposedHelpers.findAndHookMethod(
                            targetClass,
                            methodName,
                            paramClasses,
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    if (param.getResult() instanceof Boolean) {
                                        Boolean result = (Boolean) param.getResult();
                                        if (!result) {
                                            XposedBridge.log("[PaytmHook] " + className + "." + methodName +
                                                    ": false -> true");
                                            param.setResult(true);
                                        }
                                    } else if (param.getResult() instanceof String) {
                                        String result = (String) param.getResult();
                                        if (result == null || result.isEmpty() ||
                                                !GOOGLE_PLAY_PACKAGE.equals(result)) {
                                            XposedBridge.log("[PaytmHook] " + className + "." + methodName +
                                                    ": " + (result == null ? "null" : result) + " -> " + GOOGLE_PLAY_PACKAGE);
                                            param.setResult(GOOGLE_PLAY_PACKAGE);
                                        }
                                    }
                                }
                            }
                    );
                }

                XposedBridge.log("[PaytmHook] ✓ Hooked " + className + "." + methodName + "()");
            } catch (Throwable e) {
                XposedBridge.log("[PaytmHook] ✗ Failed to hook " + className + "." + methodName +
                        ": " + e.getMessage());
            }
        }
    }

    /**
     * Hook应用中的特定检测方法
     * 这些是应用自己实现的检测逻辑
     */
    private void hookAppSpecificMethods2(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook 1: AppUtilityKT.getAppInstallerPackageName()
        try {
            Class<?> appUtilityKT = XposedHelpers.findClass(
                    "com.business.merchant_payments.common.utility.AppUtilityKT",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    appUtilityKT,
                    "getAppInstallerPackageName",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String result = (String) param.getResult();
                            if (result == null || result.isEmpty()) {
                                XposedBridge.log("[PaytmHook] AppUtilityKT.getAppInstallerPackageName: null -> " + GOOGLE_PLAY_PACKAGE);
                                param.setResult(GOOGLE_PLAY_PACKAGE);
                            } else {
                                XposedBridge.log("[PaytmHook] AppUtilityKT.getAppInstallerPackageName: " + result + " -> " + GOOGLE_PLAY_PACKAGE);
                                param.setResult(GOOGLE_PLAY_PACKAGE);
                            }
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked AppUtilityKT.getAppInstallerPackageName()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook AppUtilityKT: " + e);
        }

        // Hook 2: EduformsInitActivity.isPlayStoreInstall()
        try {
            Class<?> eduformsActivity = XposedHelpers.findClass(
                    "net.one97.paytm.dynamic.module.eduforms.activity.EduformsInitActivity",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    eduformsActivity,
                    "isPlayStoreInstall",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[PaytmHook] EduformsInitActivity.isPlayStoreInstall: false -> true");
                            param.setResult(true);
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked EduformsInitActivity.isPlayStoreInstall()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook EduformsInitActivity: " + e);
        }

        // Hook 3: FinanceDataProvider.isPlayStoreInstall()
        try {
            Class<?> financeProvider = XposedHelpers.findClass(
                    "net.one97.paytm.finance.FinanceDataProvider",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    financeProvider,
                    "isPlayStoreInstall",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[PaytmHook] FinanceDataProvider.isPlayStoreInstall: false -> true");
                            param.setResult(true);
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked FinanceDataProvider.isPlayStoreInstall()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook FinanceDataProvider: " + e);
        }

        // Hook 4: AppUtility.isPlayStoreInstall()
        try {
            Class<?> appUtility = XposedHelpers.findClass(
                    "com.business.merchant_payments.common.utility.AppUtility",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    appUtility,
                    "isPlayStoreInstall",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[PaytmHook] AppUtility.isPlayStoreInstall: false -> true");
                            param.setResult(true);
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked AppUtility.isPlayStoreInstall()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook AppUtility: " + e);
        }

        // Hook 5: SDKUtility中的检测方法
        try {
            Class<?> sdkUtility = XposedHelpers.findClass(
                    "net.one97.paytm.nativesdk.Utils.SDKUtility",
                    lpparam.classLoader
            );

            // 查找所有返回boolean的isPlayStoreInstall相关方法
            XposedHelpers.findAndHookMethod(
                    sdkUtility,
                    "isPlayStoreInstall",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[PaytmHook] SDKUtility.isPlayStoreInstall: false -> true");
                            param.setResult(true);
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked SDKUtility.isPlayStoreInstall()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook SDKUtility: " + e);
        }

        // Hook 6: CJRDefaultRequestParam中的检测
        try {
            Class<?> cjrParam = XposedHelpers.findClass(
                    "net.one97.paytm.dynamic.module.mall.communicator.helper.CJRDefaultRequestParam",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    cjrParam,
                    "isPlayStoreInstall",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log("[PaytmHook] CJRDefaultRequestParam.isPlayStoreInstall: false -> true");
                            param.setResult(true);
                        }
                    }
            );
            XposedBridge.log("[PaytmHook] Successfully hooked CJRDefaultRequestParam.isPlayStoreInstall()");
        } catch (Throwable e) {
            XposedBridge.log("[PaytmHook] Failed to hook CJRDefaultRequestParam: " + e);
        }
    }

}
