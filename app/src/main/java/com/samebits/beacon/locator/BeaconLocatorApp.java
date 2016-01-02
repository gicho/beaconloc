/*
 *
 *  Copyright (c) 2015 SameBits UG. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.samebits.beacon.locator;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.samebits.beacon.locator.data.DataManager;
import com.samebits.beacon.locator.injection.component.ApplicationComponent;
import com.samebits.beacon.locator.injection.component.DaggerApplicationComponent;
import com.samebits.beacon.locator.injection.component.DataComponent;
import com.samebits.beacon.locator.injection.module.ApplicationModule;
import com.samebits.beacon.locator.model.ActionBeacon;
import com.samebits.beacon.locator.model.DetectedBeacon;
import com.samebits.beacon.locator.util.Constants;
import com.samebits.beacon.locator.util.PreferencesUtil;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.powersave.BackgroundPowerSaver;
import org.altbeacon.beacon.startup.BootstrapNotifier;
import org.altbeacon.beacon.startup.RegionBootstrap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * Created by vitas on 18/10/15.
 */
public class BeaconLocatorApp extends Application implements BootstrapNotifier, RangeNotifier, BeaconConsumer {

    ApplicationComponent applicationComponent;

    private BackgroundPowerSaver mBackgroundPowerSaver;
    private BeaconManager mBeaconManager;
    private DataManager mDataManager;
    private RegionBootstrap mRegionBootstrap;
    List<Region> mRegions;


    public static BeaconLocatorApp from(@NonNull Context context) {
        return (BeaconLocatorApp) context.getApplicationContext();
    }

    public ApplicationComponent getComponent() {
        return applicationComponent;
    }


    @Override
    public void onCreate() {
        super.onCreate();

        applicationComponent = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this))
                .build();

        mBeaconManager = BeaconLocatorApp.from(this).getComponent().beaconManager();
        mDataManager = BeaconLocatorApp.from(this).getComponent().dataManager();

        loadRegions();

        mBeaconManager.bind(this);

    }

    private void initBeaconManager() {
        mBeaconManager.setBackgroundMode(PreferencesUtil.isBackgroundScan(this));

        if (PreferencesUtil.isEddystoneLayoutUID(this)) {
            mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));
        }
        if (PreferencesUtil.isEddystoneLayoutURL(this)) {
            mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
        }
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));

        //konkakt?
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:0-3=4c000215,i:4-19,i:20-21,i:22-23,p:24-24"));

        mBeaconManager.setBackgroundBetweenScanPeriod(PreferencesUtil.getBackgroundScanInterval(this));

        mBeaconManager.setBackgroundScanPeriod(2000L);          // default is 10000L
        mBeaconManager.setForegroundBetweenScanPeriod(0L);      // default is 0L
        mBeaconManager.setForegroundScanPeriod(1100L);          // Default is 1100L

        mBackgroundPowerSaver = new BackgroundPowerSaver(this);

        try {
            mBeaconManager.updateScanPeriods();
        } catch (RemoteException e) {
            Log.e(Constants.TAG,"update error", e);
        }
    }

    private void loadRegions() {
        mRegions = mDataManager.getAllEnabledRegions();

        if (mRegions.size()>0) {
            mRegionBootstrap = new RegionBootstrap(this, mRegions);
        }

        //mBeaconManager.startMonitoringBeaconsInRegion(new Region("myMonitoringUniqueId", null, null, null));

    }

    @Override
    public void onBeaconServiceConnect() {
        initBeaconManager();
    }

    @Override
    public void didEnterRegion(Region region) {
        Log.d(Constants.TAG, "Region Enter " + region);

        Intent intent = new Intent();
        intent.setAction(Constants.NOTIFY_BEACON_ENTERS_REGION);
        intent.putExtra("REGION", region);
        getApplicationContext().sendOrderedBroadcast(intent, null);
    }

    @Override
    public void didExitRegion(Region region) {
        Log.d(Constants.TAG, "Region Exit " + region);

        Intent intent = new Intent();
        intent.setAction(Constants.NOTIFY_BEACON_LEAVES_REGION);
        intent.putExtra("REGION", region);
        getApplicationContext().sendOrderedBroadcast(intent, null);
    }

    @Override
    public void didDetermineStateForRegion(int i, Region region) {
        Log.d(Constants.TAG, "Region State  " + i+ " region " +region);

    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        if (beacons != null) {
            if (beacons.size() > 0 && region != null ) {
                Iterator<Beacon> iterator = beacons.iterator();
                while (iterator.hasNext()) {
                    DetectedBeacon dBeacon = new DetectedBeacon(iterator.next());
                    dBeacon.setTimeLastSeen(System.currentTimeMillis());

                    //this.mBeacons.put(dBeacon.getId(), dBeacon);
                }
            }
        }
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
        // release whatever is needed
        mBeaconManager.unbind(this);
        mBeaconManager = null;
    }


}