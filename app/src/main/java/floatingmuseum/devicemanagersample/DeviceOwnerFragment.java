package floatingmuseum.devicemanagersample;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;

import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.util.List;

import floatingmuseum.devicemanagersample.util.RootUtil;
import floatingmuseum.devicemanagersample.util.SPUtil;
import floatingmuseum.devicemanagersample.util.ToastUtil;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by yan on 2016/6/6.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class DeviceOwnerFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    private DevicePolicyManager dpm;
    private PackageManager pm;
    private ComponentName mComponentName;
    private Activity activity;
    private String lastHideApp;
    private ActivityManager am;
    private UserManager um;

    private static final int APP_HIDE = 0;
    private static final int APP_RESTRICTIONS = 1;
    private static final int APP_UNINSTALL_BLOCKED = 2;
    private static final int APP_LOCK_TASK = 3;

    private static final int ADD_USER_RESTRICTION = 0;
    private static final int CLEAR_USER_RESTRICTION = 1;

    private String[] userRestrictionsDisplays = {"禁止音量调节","禁止安装应用","禁止卸载应用"};
    private String[] userRestrictionsKeys = {UserManager.DISALLOW_ADJUST_VOLUME,UserManager.DISALLOW_INSTALL_APPS,UserManager.DISALLOW_UNINSTALL_APPS};
    private String[] globalSettingsDisplays = {"AUTO_TIME,NO","AUTO_TIME,YES","AUTO_TIME_ZONE,NO","AUTO_TIME_ZONE,YES"};
    private String[] globalSettingValues = {"0","1","0","1"};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_deviceowner);
        initListener();
        activity = DeviceOwnerFragment.this.getActivity();
        if (dpm == null) {
            dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        }

        if (pm == null) {
            pm = activity.getPackageManager();
        }
        mComponentName = MyDeviceAdminReceiver.getComponentName(activity);
        lastHideApp = SPUtil.getString(activity,"packageName",null);
        am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        um = (UserManager) activity.getSystemService(Context.USER_SERVICE);
    }

    private void initListener() {
        findPreference("deviceowner_enabled").setOnPreferenceClickListener(this);
        findPreference("remove_deviceowner").setOnPreferenceClickListener(this);
        findPreference("check_root").setOnPreferenceClickListener(this);
        findPreference("enabled_deviceowner_rooted").setOnPreferenceClickListener(this);
        findPreference("hide_app").setOnPreferenceClickListener(this);
        findPreference("show_app").setOnPreferenceClickListener(this);
        findPreference("block_uninstall").setOnPreferenceClickListener(this);
        findPreference("lock_task").setOnPreferenceClickListener(this);
        findPreference("unlock_task").setOnPreferenceClickListener(this);
        findPreference("set_app_restrictions").setOnPreferenceClickListener(this);
        findPreference("add_user_restriction").setOnPreferenceClickListener(this);
        findPreference("clear_user_restriction").setOnPreferenceClickListener(this);
        findPreference("global_setting").setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case "deviceowner_enabled":
                if (checkDeviceOwnerEnabled()) {
                    ToastUtil.show("此应用已激活为DeviceOwner");
                } else {
                    ToastUtil.show("未激活");
                }
                break;
            case "remove_deviceowner":
                removeDeviceOwner();
                break;
            case "check_root":
                if(RootUtil.isRooted()){
                    ToastUtil.show("已root");
                }else{
                    ToastUtil.show("未root");
                }
                break;
            case "enabled_deviceowner_rooted":
                enabledDeviceOwnerOnRooted();
                break;
            case "hide_app":
                selectApp(APP_HIDE);
                break;
            case "show_app":
                showApp(lastHideApp);
            break;
            case "block_uninstall":
                selectApp(APP_UNINSTALL_BLOCKED);
            break;
            case"lock_task":
                selectApp(APP_LOCK_TASK);
            break;
            case "unlock_task":
                unlockTask();
            break;
            case "set_app_restrictions":
//                selectApp(APP_RESTRICTIONS);
                break;
            case "add_user_restriction":
                selectRestriction(ADD_USER_RESTRICTION);
                break;
            case "clear_user_restriction":
                selectRestriction(CLEAR_USER_RESTRICTION);
                break;
            case "global_setting":
//                selectGlobalSetting();
                break;
        }
        return true;
    }

    private boolean checkDeviceOwnerEnabled() {
        return dpm.isDeviceOwnerApp(activity.getPackageName());
    }

    private void removeDeviceOwner() {
        if (dpm.isDeviceOwnerApp(activity.getPackageName())) {
            dpm.clearDeviceOwnerApp(activity.getPackageName());
            if (dpm.isDeviceOwnerApp(activity.getPackageName())) {
                ToastUtil.show("移除失败");
            } else {
                ToastUtil.show("移除成功");
            }
        } else {
            ToastUtil.show("此应用不是DeviceOwner");
        }
    }

    private void enabledDeviceOwnerOnRooted() {
        if (!RootUtil.isRooted()) {
            ToastUtil.show("系统未root");
            return;
        }

        Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> observer) {
                enabledDeviceOwner();
                boolean enabled = checkDeviceOwnerEnabled();
                observer.onNext(enabled);
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Boolean>() {
                    @Override
                    public void onNext(Boolean item) {
                        Logger.d("Next: " + item);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Logger.d("error: ");
                    }

                    @Override
                    public void onCompleted() {
                        Logger.d("onCompleted: ");
                    }
                });
    }

    private void enabledDeviceOwner() {
        try {
            Runtime.getRuntime().exec("dpm set-device-owner floatingmuseum.devicemanagersample");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void selectApp(final int flag) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(
                mainIntent, 0);
        String[] names = new String[resolveInfos.size()];
        for (int i = 0; i < resolveInfos.size(); i++) {
            names[i] = resolveInfos.get(i).loadLabel(pm).toString();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setItems(names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String packgeName = resolveInfos.get(which).activityInfo.packageName;
                switch (flag) {
                    case APP_HIDE:
                        hideApp(packgeName);
                        break;
                    case APP_RESTRICTIONS:
                        setAppRestrictions(packgeName);
                        break;
                    case APP_UNINSTALL_BLOCKED:
                        blockedUninstall(packgeName);
                        break;
                    case APP_LOCK_TASK:
                        setLockTask(packgeName);
                        break;
                }
            }
        }).create().show();
    }

    private void hideApp(String packageName) {
        Logger.d("应用包名" + packageName);
        setAppHide(packageName,true);
    }

    private void showApp(String packageName){
        if(packageName==null){
            ToastUtil.show("不存在上一个被隐藏的应用");
            return;
        }
        setAppHide(packageName,false);
    }

    private void setAppHide(String packageName,boolean hide){
        dpm.setApplicationHidden(mComponentName,packageName,hide);
        boolean isHidden = dpm.isApplicationHidden(mComponentName,packageName);
        if(isHidden){
            ToastUtil.show(packageName+"已隐藏");
            lastHideApp = packageName;
            SPUtil.editString(activity,"packageName",packageName);
        }else{
            ToastUtil.show(packageName+"已显示");
        }
    }

    /**
     *  未找到应用的限制参数
     */
    private void setAppRestrictions(String packageName) {
        Logger.d("限制："+dpm.getApplicationRestrictions(mComponentName,packageName));
//        dpm.setApplicationRestrictions(mComponentName,packageName,);
    }

    private void selectRestriction(final int flag) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setItems(userRestrictionsDisplays, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (flag) {
                    case ADD_USER_RESTRICTION:
                        setUserRestrictions(userRestrictionsKeys[which]);
                        break;
                    case CLEAR_USER_RESTRICTION:
                        removeUserRestrictions(userRestrictionsKeys[which]);
                        break;
                }
            }
        }).create().show();
    }

    /**
     * 添加用户限制
     */
    private void setUserRestrictions(String key){
        dpm.addUserRestriction(mComponentName,key);
        Bundle bundle = um.getUserRestrictions();
        checkUserRestriction((Boolean) bundle.get(key));
    }

    private void removeUserRestrictions(String key){
        dpm.clearUserRestriction(mComponentName,key);
        Bundle bundle = um.getUserRestrictions();
        checkUserRestriction((Boolean) bundle.get(key));
    }

    private void checkUserRestriction(boolean successful) {
        if(successful){
            ToastUtil.show("限制成功");
        }else{
            ToastUtil.show("限制取消");
        }
    }

    private void setPersistentActivity(){
        // TODO: 2016/6/23 未测试 目测文档意思是使某个Activity成为某个IntentFilter的第一接收者
//        dpm.addPersistentPreferredActivity();
    }

    /**
     * 暂时未看出效果
     */
    private void selectGlobalSetting() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setItems(globalSettingsDisplays, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case 0:
                        setDeviceGlobalSetting(Settings.Global.AUTO_TIME,globalSettingValues[which]);
                    break;
                    case 1:
                        setDeviceGlobalSetting(Settings.Global.AUTO_TIME,globalSettingValues[which]);
                        break;
                    case 2:
                        setDeviceGlobalSetting(Settings.Global.AUTO_TIME_ZONE,globalSettingValues[which]);
                        break;
                    case 3:
                        setDeviceGlobalSetting(Settings.Global.AUTO_TIME_ZONE,globalSettingValues[which]);
                        break;
                }
            }
        }).create().show();
    }

    private void setDeviceGlobalSetting(String setting,String value) {
        Logger.d("setting:"+setting+"...value:"+value);
        dpm.setGlobalSetting(mComponentName,setting,value);
    }

    private void setDeviceKeyGuardDisabled() {
        // TODO: 2016/6/17 未测试
//        dpm.setKeyguardDisabled(mComponentName,true);
    }

    private void setDeviceKeyGuardDisabledFeatures() {
        // TODO: 2016/6/17 未测试
//        dpm.setKeyguardDisabledFeatures(mComponentName, DevicePolicyManager.KEYGUARD_DISABLE_WIDGETS_ALL);
    }

    /**
     * 锁定屏幕
     */
    private void setLockTask(String packageName) {
        String[] packages = {packageName};
        dpm.setLockTaskPackages(mComponentName,packages);
        activity.startLockTask();
    }

    private void unlockTask() {
        Logger.d("isIn:"+am.isInLockTaskMode());
        if(am.isInLockTaskMode()){
            activity.stopLockTask();
        }
    }

    /**
     * 静音
     */
    private void setVolumeMuted() {
        // TODO: 2016/6/17 未测试
//        dpm.setMasterVolumeMuted(mComponentName,true);
    }

    /**
     * 应用权限
     */
    private void setPackagePermission(String packageName) {
        // TODO: 2016/6/17 未测试
//        dpm.setPermissionGrantState(mComponentName,packageName, Manifest.permission.INTERNET,DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    /**
     * 动态权限
     */
    private void setDevicePermissionPolicy() {
        // TODO: 2016/6/17 未测试
//        dpm.setPermissionPolicy(mComponentName,DevicePolicyManager.PERMISSION_POLICY_PROMPT);
    }

    /**
     * 辅助功能
     */
    private void setDevicePermittedAccessibilityService() {
        // TODO: 2016/6/17 未测试
//        dpm.setPermittedAccessibilityServices(mComponentName,);
    }

    /**
     * 输入法
     */
    private void setDevicePermittedInputMethod() {
        // TODO: 2016/6/17 未测试
//        dpm.setPermittedInputMethods(mComponentName,)
    }

    private void setDeviceScrennCaptureDisabled() {
        // TODO: 2016/6/17 未测试
//        dpm.setScreenCaptureDisabled(mComponentName,true);
    }

    private void setDeviceSecureSetting(){
        // TODO: 2016/6/17 未测试
//        dpm.setSecureSetting(mComponentName, Settings.Secure.INSTALL_NON_MARKET_APPS,);
    }

    private void disableStatusBar(){
        // TODO: 2016/6/17 未测试
//        dpm.setStatusBarDisabled(mComponentName,true);
    }

    /**
     * 阻止卸载
     */
    private void blockedUninstall(String packageName){
        boolean uninstall = false;
        if(dpm.isUninstallBlocked(mComponentName,packageName)){
            uninstall = false;
        }else{
            uninstall = true;
        }
        dpm.setUninstallBlocked(mComponentName,packageName,uninstall);
        ToastUtil.show(packageName+"...uninstallBlocked："+dpm.isUninstallBlocked(mComponentName,packageName));
    }

    private void setDeviceUserIcon(){
        // TODO: 2016/6/17 未测试
//        dpm.setUserIcon(mComponentName,);
    }

    private void setSwitchUser(){
        // TODO: 2016/6/17 未测试
//        dpm.switchUser(mComponentName,);
    }
}
