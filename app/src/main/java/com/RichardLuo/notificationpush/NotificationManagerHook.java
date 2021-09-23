package com.RichardLuo.notificationpush;

import static android.app.Notification.FLAG_ONLY_ALERT_ONCE;
import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import android.app.AndroidAppHelper;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.XModuleResources;
import android.os.Binder;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import com.github.kyuubiran.ezxhelper.init.EzXHelperInit;
import com.github.kyuubiran.ezxhelper.init.InitFields;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NotificationManagerHook implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static final String TAG = "GCMForwarding";
    
    Context context;
    String currentPackageName;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        EzXHelperInit.INSTANCE.initZygote(startupParam);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        EzXHelperInit.INSTANCE.initHandleLoadPackage(lpparam);
        EzXHelperInit.INSTANCE.setLogTag("GCM Forwarding Xposed");
        EzXHelperInit.INSTANCE.setToastTag(TAG);

        currentPackageName = BuildConfig.APPLICATION_ID;

        if ("android".equals(InitFields.hostPackageName)) {
            Log.d(TAG, "attach to system");
            attachSystem(lpparam);
        }
    }

    private void attachSystem(XC_LoadPackage.LoadPackageParam lpparam) {
        context = AndroidAppHelper.currentApplication();
        /*int currentUid = 0;
        try {
            currentUid = context.getPackageManager()
                    .getApplicationInfo(currentPackageName, 0).uid;
        } catch (Exception ex) {
            Log.e(TAG, "Cannot get current uid", ex);
        }
        Log.d(TAG, "currentUid: " + currentUid);
        final int finalCurrentUid = currentUid;
        XposedHelpers.findAndHookMethod("com.android.server.AppOpsService",
                lpparam.classLoader,
                "verifyIncomingUid",
                int.class,

                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (Binder.getCallingUid() == finalCurrentUid)
                            param.setResult(null);
                    }
                });
         */

        XposedHelpers.findAndHookMethod("com.android.server.notification.NotificationManagerService",
                lpparam.classLoader,
                "enqueueNotificationInternal",
                String.class,
                String.class,
                int.class,
                int.class,
                String.class,
                int.class,
                Notification.class,
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Binder.clearCallingIdentity(); // 屏蔽权限管理

                            String pkg = (String) param.args[0];
                            String opPkg = (String) param.args[1];
                            int callingUid = (Integer) param.args[2];
                            int callingPid = (Integer) param.args[3];
                            String tag = (String) param.args[4];
                            int id = (Integer) param.args[5];
                            Notification notification = (Notification) param.args[6];
                            int incomingUserId = (Integer) param.args[7];
                            boolean postSilently = (Boolean) param.args[8];
                            // Log.d(TAG, "currentPackageName: " + currentPackageName + ", pkg: " + pkg + ", opPkg: " + pkg);
                            if (currentPackageName.equals(opPkg)) {
                                // 是转发的消息，尝试更改发送方
                                String packageName = notification.getGroup();
                                int uid = getUid(packageName);
                                // Log.d(TAG, "Will send to uid: " + uid);
                                if (!currentPackageName.equals(packageName) && uid >= 0) {
                                    // Log.d(TAG, "Send to user: " + incomingUserId + ", package: " + packageName + ", uid: " + uid);
                                    String channelId = notification.extras.getString("forward_channelId", "default");
                                    Object mPreferencesHelper = XposedHelpers.getObjectField(param.thisObject, "mPreferencesHelper");
                                    NotificationChannel channel = null;
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        if (mPreferencesHelper != null) {
                                            try {
                                                channel = (NotificationChannel) XposedHelpers.callMethod(mPreferencesHelper,
                                                        "getNotificationChannel",
                                                        packageName,
                                                        uid,
                                                        channelId,
                                                        true);
                                                if (channel != null) {
                                                    // Log.d(TAG, "will send to channel: " + channel.toString());
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "cannot get channel", e);
                                            }
                                        }
                                        // 创建channel
                                        if (channel == null && mPreferencesHelper != null) {
                                            try {
                                                channel = new NotificationChannel(channelId,
                                                        notification.extras.getString("forward_channelName", "GCM通知转发"),
                                                        IMPORTANCE_DEFAULT);
                                                XposedHelpers.callMethod(mPreferencesHelper,
                                                        "createNotificationChannel",
                                                        packageName,
                                                        uid,
                                                        channel,
                                                        true,
                                                        true);
                                                // Log.d(TAG, "created channel" + channelId);
                                            } catch (Exception e) {
                                                Log.e(TAG, "cannot create channel", e);
                                            }
                                        }
                                    }
                                    // 生成修改后的notification
                                    Notification newNoti = rebuildNotification(notification, channelId);

                                    // Log.d(TAG, "new notification: " + newNoti.toString());
                                    param.args[0] = packageName;
                                    param.args[1] = packageName;
                                    param.args[2] = uid;
                                    param.args[6] = newNoti;
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Unknown error", e);
                        }
                        super.beforeHookedMethod(param);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Throwable e = param.getThrowable();
                        if (e != null) {
                            Log.e(TAG, "Cannot enqueue notification", e);
                        }
                        super.afterHookedMethod(param);
                    }
                });
    }

    private Notification rebuildNotification(Notification notification, String channelId) {
        Notification.Builder newNotiBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            newNotiBuilder = new Notification.Builder(context, channelId)
                    .setExtras(notification.extras)
                    .setSmallIcon(notification.getSmallIcon());
        } else {
            newNotiBuilder = new Notification.Builder(context)
                    .setExtras(notification.extras);
        }

        newNotiBuilder
                .setStyle(new Notification.BigTextStyle()
                        .setSummaryText("GCM通知转发"))
                .setContentTitle(notification.extras.getString(Notification.EXTRA_TITLE, "无标题"))
                .setContentText(notification.extras.getCharSequence(Notification.EXTRA_TEXT, "无内容").toString())
                .setCategory(notification.category)
                .setGroup(notification.extras.getString("forward_group", notification.getGroup()))
                .setContentIntent(notification.contentIntent)
                .setVisibility(notification.visibility)
                .setAutoCancel(true);

        return newNotiBuilder.build();
    }

    private int getUid(String packageName) {
        if (context == null) return -2;
        try {
            return context.getPackageManager()
                    .getApplicationInfo(packageName, 0).uid;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return -1;
    }
}
