/*
 * Copyright (C) 2022-2024 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 package com.android.internal.util;

 import android.app.ActivityTaskManager;
 import android.app.Application;
 import android.app.TaskStackListener;
 import android.content.ComponentName;
 import android.content.Context;
 import android.content.res.Resources;
 import android.os.Binder;
 import android.os.Build;
 import android.os.Process;
 import android.os.SystemProperties;
 import android.text.TextUtils;
 import android.util.Log;

 import com.android.internal.R;

 import java.lang.reflect.Field;
 import java.util.Arrays;
 import java.util.Set;

 /**
  * @hide
  */
 public class PropImitationHooks {

     private static final String TAG = "PropImitationHooks";
     private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

     private static final String PACKAGE_ARCORE = "com.google.ar.core";
     private static final String PACKAGE_FINSKY = "com.android.vending";
     private static final String PACKAGE_GMS = "com.google.android.gms";
     private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
     private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
     private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

     private static final String PROP_SECURITY_PATCH = "persist.sys.pihooks.security_patch";
     private static final String PROP_FIRST_API_LEVEL = "persist.sys.pihooks.first_api_level";

     private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
             "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

     private static final Set<String> sPixelFeatures = Set.of(
         "PIXEL_2017_PRELOAD",
         "PIXEL_2018_PRELOAD",
         "PIXEL_2019_MIDYEAR_PRELOAD",
         "PIXEL_2019_PRELOAD",
         "PIXEL_2020_EXPERIENCE",
         "PIXEL_2020_MIDYEAR_EXPERIENCE",
         "PIXEL_EXPERIENCE"
     );

     private static volatile String[] sCertifiedProps;
     private static volatile String sStockFp, sNetflixModel;

     private static volatile String sProcessName;
     private static volatile boolean sIsPixelDevice, sIsGms, sIsFinsky, sIsPhotos;

     public static void setProps(Context context) {
         final String packageName = context.getPackageName();
         final String processName = Application.getProcessName();

         if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
             Log.e(TAG, "Null package or process name");
             return;
         }

         final Resources res = context.getResources();
         if (res == null) {
             Log.e(TAG, "Null resources");
             return;
         }

         sCertifiedProps = res.getStringArray(R.array.config_certifiedBuildProperties);
         sStockFp = res.getString(R.string.config_stockFingerprint);
         sNetflixModel = res.getString(R.string.config_netflixSpoofModel);

         sProcessName = processName;
         sIsPixelDevice = Build.MANUFACTURER.equals("Google") && Build.MODEL.contains("Pixel");
         sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
         sIsFinsky = packageName.equals(PACKAGE_FINSKY);
         sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

         setCertifiedPropsForGms();
         if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
             dlog("Setting stock fingerprint for: " + packageName);
             setPropValue("FINGERPRINT", sStockFp);
         } else if (!sNetflixModel.isEmpty() && packageName.equals(PACKAGE_NETFLIX)) {
             dlog("Setting model to " + sNetflixModel + " for Netflix");
             setPropValue("MODEL", sNetflixModel);
         }
     }

     private static void setPropValue(String key, String value) {
         try {
             dlog("Setting prop " + key + " to " + value);
             Class clazz = Build.class;
             if (key.startsWith("VERSION.")) {
                 clazz = Build.VERSION.class;
                 key = key.substring(8);
             }
             Field field = clazz.getDeclaredField(key);
             field.setAccessible(true);
             field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
             field.setAccessible(false);
         } catch (Exception e) {
             Log.e(TAG, "Failed to set prop " + key, e);
         }
     }

     private static void setCertifiedPropsForGms() {
         dlog("Spoofing build for GMS");
         setCertifiedProps();
     }

     private static void setCertifiedProps() {
         for (String entry : sCertifiedProps) {
             final String[] fieldAndProp = entry.split(":", 2);
             if (fieldAndProp.length != 2) {
                 Log.e(TAG, "Invalid entry in certified props: " + entry);
                 continue;
             }
             setPropValue(fieldAndProp[0], fieldAndProp[1]);
         }
         setSystemProperty(PROP_SECURITY_PATCH, Build.VERSION.SECURITY_PATCH);
         setSystemProperty(PROP_FIRST_API_LEVEL,
                 Integer.toString(Build.VERSION.DEVICE_INITIAL_SDK_INT));
     }

     private static void setSystemProperty(String name, String value) {
         try {
             SystemProperties.set(name, value);
             dlog("Set system prop " + name + "=" + value);
         } catch (Exception e) {
             Log.e(TAG, "Failed to set system prop " + name + "=" + value, e);
         }
     }

     public static void dlog(String msg) {
         if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
     }
 }


