/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Dragan Atanasov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.datanasov.wififilter;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Mod implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {
    private static String MODULE_PATH = null;
    private int mFilterItemsId = 0;
    private boolean mHasSwitchBar = true;

    private static final String TARGET_PACKAGE = "com.android.settings";
    private static final String CLASS_SWITCH_BAR = "com.android.settings.widget.SwitchBar";
    private static final String CLASS_WIFI_SETTINGS = "com.android.settings.wifi.WifiSettings";
    private static final String CLASS_WIFI_ENABLER = "com.android.settings.wifi.WifiEnabler";
    private static final String CLASS_SUB_SETTINGS = "com.android.settings.SubSettings";

    private static final int SPINNER_ID = 0x1f0f0100;
    private static final int SECURITY_NONE = 0;
    private static final int WIFI_STATE_ENABLED = 3;
    private static final int WIFI_STATE_DISABLED = 1;

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!loadPackageParam.packageName.equals(TARGET_PACKAGE))
            return;

        initHook(loadPackageParam);
    }

    public void initHook(final XC_LoadPackage.LoadPackageParam lPParam) {
        if (isLollipop()) {
            try {
                // Just to check if we really have SwitchBar implemented.
                XposedHelpers.findAndHookConstructor(CLASS_WIFI_ENABLER, lPParam.classLoader, Context.class, XposedHelpers.findClass(CLASS_SWITCH_BAR, lPParam.classLoader), new XC_MethodHook() {
                });
                XposedHelpers.findAndHookConstructor(CLASS_SWITCH_BAR, lPParam.classLoader, Context.class, AttributeSet.class, int.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Context context = (Context) param.args[0];
                        Resources r = context.getResources();

                        int spinnerPaddingOffset = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, r.getDisplayMetrics()));
                        TextView mTextView = (TextView) XposedHelpers.getObjectField(param.thisObject, "mTextView");
                        mTextView.setPaddingRelative(spinnerPaddingOffset, mTextView.getPaddingTop(), mTextView.getPaddingEnd(), mTextView.getPaddingBottom());

                        ArrayAdapter<CharSequence> adapter =
                                ArrayAdapter.createFromResource(context, mFilterItemsId, android.R.layout.simple_list_item_1);

                        Spinner spinner = new Spinner(context);
                        spinner.setId(SPINNER_ID);
                        spinner.setVisibility(View.GONE);
                        spinner.setY(spinner.getY() - spinner.getHeight());
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) mTextView.getLayoutParams();
                        lp.setMarginStart(lp.getMarginStart() - spinnerPaddingOffset);
                        spinner.setLayoutParams(lp);
                        spinner.setGravity(Gravity.CENTER_VERTICAL);
                        spinner.setAdapter(adapter);

                        ((LinearLayout) param.thisObject).addView(spinner, 0);
                    }
                });
            } catch (Throwable t) {
                mHasSwitchBar = false;
            }
        } else {
            mHasSwitchBar = false;
        }

        if (!mHasSwitchBar) {
            try {
                XposedHelpers.findAndHookMethod(CLASS_WIFI_SETTINGS, lPParam.classLoader, "onActivityCreated", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Fragment fragment = (Fragment) param.thisObject;
                        ActionBar actionBar = fragment.getActivity().getActionBar();
                        SpinnerAdapter mSpinnerAdapter = ArrayAdapter.createFromResource(fragment.getActivity(),
                                mFilterItemsId, android.R.layout.simple_spinner_dropdown_item);
                        actionBar.setListNavigationCallbacks(mSpinnerAdapter, new ActionBar.OnNavigationListener() {
                            @Override
                            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                                XposedHelpers.callMethod(param.thisObject, "updateAccessPoints");
                                return true;
                            }
                        });
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }

        try {
            XposedHelpers.findAndHookMethod(CLASS_WIFI_SETTINGS, lPParam.classLoader, "updateAccessPoints", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final int wifiState = (int) XposedHelpers.callMethod(XposedHelpers.getObjectField(param.thisObject, "mWifiManager"), "getWifiState");
                    switch (wifiState) {
                        case WIFI_STATE_ENABLED:
                            ArrayList<Preference> accessPoints =
                                    new ArrayList<Preference>();
                            int selectedItemPosition = 0;
                            if (mHasSwitchBar) {
                                Spinner spinner = getSpinner(((Fragment) param.thisObject).getActivity());
                                if (spinner != null) {
                                    selectedItemPosition = spinner.getSelectedItemPosition();
                                }
                            } else {
                                selectedItemPosition = ((Fragment) param.thisObject).getActivity().getActionBar().getSelectedNavigationIndex();
                            }
                            if (selectedItemPosition > 0) {
                                List<Preference> allAccessPoints = isLollipop() ? (List<Preference>) XposedHelpers.callMethod(param.thisObject, "constructAccessPoints", ((Fragment) param.thisObject).getActivity(), XposedHelpers.getObjectField(param.thisObject, "mWifiManager"), XposedHelpers.getObjectField(param.thisObject, "mLastInfo"),
                                        null) :
                                        (List<Preference>) XposedHelpers.callMethod(param.thisObject, "constructAccessPoints");
                                for (Preference accessPoint : allAccessPoints) {
                                    switch (selectedItemPosition) {
                                        case 1:
                                            Object config = XposedHelpers.callMethod(accessPoint, "getConfig");
                                            if (config != null) {
                                                if (isLollipop()) {
                                                    if (!XposedHelpers.getBooleanField(config, "selfAdded") && XposedHelpers.getIntField(config, "numAssociation") != 0) {
                                                        accessPoints.add(accessPoint);
                                                    }
                                                } else {
                                                    accessPoints.add(accessPoint);
                                                }
                                            }
                                            break;
                                        case 2:
                                            if (XposedHelpers.getIntField(accessPoint, "security") == SECURITY_NONE) {
                                                accessPoints.add(accessPoint);
                                            }
                                            break;
                                    }
                                }
                                Collections.sort(accessPoints);
                                ((PreferenceScreen) XposedHelpers.callMethod(param.thisObject, "getPreferenceScreen")).removeAll();
                                for (Preference ap : accessPoints) {
                                    // Ignore access points that are out of range.
                                    if (XposedHelpers.callMethod(ap, "getLevel") != -1) {
                                        ((PreferenceScreen) XposedHelpers.callMethod(param.thisObject, "getPreferenceScreen")).addPreference(ap);
                                    }
                                }
                            }
                            break;
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        if (mHasSwitchBar) {
            try {
                XposedHelpers.findAndHookMethod(CLASS_WIFI_SETTINGS, lPParam.classLoader, "initEmptyView", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                        Spinner spinner = getSpinner(((Fragment) param.thisObject).getActivity());
                        if (spinner != null) {
                            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                    XposedHelpers.callMethod(param.thisObject, "updateAccessPoints");
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> parent) {

                                }
                            });
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            try {
                XposedHelpers.findAndHookMethod(CLASS_WIFI_ENABLER, lPParam.classLoader, "handleWifiStateChanged", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int state = (int) param.args[0];
                        LinearLayout switchBar = (LinearLayout) XposedHelpers.getObjectField(param.thisObject, "mSwitchBar");
                        final Spinner spinner = (Spinner) switchBar.findViewById(SPINNER_ID);
                        final TextView mTextView = (TextView) XposedHelpers.getObjectField(switchBar, "mTextView");
                        if (spinner != null && mTextView != null) {
                            switch (state) {
                                case WIFI_STATE_DISABLED:
                                    spinner.animate()
                                            .translationY(-spinner.getHeight())
                                            .alpha(0.0f)
                                            .setInterpolator(new AccelerateDecelerateInterpolator())
                                            .setListener(new AnimatorListenerAdapter() {
                                                @Override
                                                public void onAnimationEnd(Animator animation) {
                                                    super.onAnimationEnd(animation);
                                                    spinner.setVisibility(View.GONE);
                                                    mTextView.setVisibility(View.VISIBLE);
                                                }
                                            });
                                    break;
                                case WIFI_STATE_ENABLED:
                                    spinner.setAlpha(0.0f);
                                    mTextView.setVisibility(View.GONE);
                                    spinner.setVisibility(View.VISIBLE);
                                    spinner.animate()
                                            .translationY(0)
                                            .alpha(1.0f)
                                            .setInterpolator(new AccelerateDecelerateInterpolator())
                                            .setListener(new AnimatorListenerAdapter() {
                                                @Override
                                                public void onAnimationEnd(Animator animation) {
                                                    super.onAnimationEnd(animation);
                                                    spinner.setVisibility(View.VISIBLE);

                                                }
                                            });
                                    break;
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        } else {
            try {
                XposedHelpers.findAndHookMethod(CLASS_WIFI_ENABLER, lPParam.classLoader, "handleWifiStateChanged", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int state = (int) param.args[0];
                        if (XposedHelpers.getObjectField(param.thisObject, "mContext").getClass().getName().equals(CLASS_SUB_SETTINGS)) {
                            ActionBar actionBar = ((Activity) XposedHelpers.getObjectField(param.thisObject, "mContext")).getActionBar();
                            switch (state) {
                                case WIFI_STATE_DISABLED:
                                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                                    actionBar.setDisplayShowTitleEnabled(true);
                                    break;
                                case WIFI_STATE_ENABLED:
                                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
                                    actionBar.setDisplayShowTitleEnabled(false);
                                    break;
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    }

    private boolean isLollipop() {
        return android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public Spinner getSpinner(Activity activity) {
        LinearLayout switchBar = (LinearLayout) XposedHelpers.callMethod(activity, "getSwitchBar");
        return (Spinner) switchBar.findViewById(SPINNER_ID);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam loadPackageParam) throws Throwable {
        if (!loadPackageParam.packageName.equals(TARGET_PACKAGE))
            return;

        XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, loadPackageParam.res);
        mFilterItemsId = loadPackageParam.res.addResource(modRes, R.array.wifi_filter_items);
    }
}