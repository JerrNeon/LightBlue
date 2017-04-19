/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cw.lightblue.activity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.cw.lightblue.R;
import com.cw.lightblue.common.SampleGattAttributes;
import com.cw.lightblue.service.BluetoothLeService;
import com.cw.lightblue.util.ByteUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;


/**
 * For a given BLE device, this Activity provides the user interface to connect,
 * display data, and display GATT services and characteristics supported by the
 * device. The Activity communicates with {@code BluetoothLeService}, which in
 * turn interacts with the Bluetooth LE API.
 */
@SuppressLint("NewApi")
public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private Toolbar mToolbar;
    private TextView mConnectionState;//连接状态
    private TextView mDataField;//数据
    private String mDeviceName;//设备名称
    private String mDeviceAddress;//设备地址
    private ExpandableListView mGattServicesList;//显示设备服务操作列表

    private BluetoothLeService mBluetoothLeService;//蓝牙服务
    //设备信息列表
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private BluetoothGattCharacteristic mNotifyCharacteristic;//设备信息
    private boolean mConnected = false;//连接状态值

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_gattservices_characteristics);
        init();
        initService();
    }

    private void init() {
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        setSupportActionBar(mToolbar);
        mToolbar.setTitle(mDeviceName);
    }

    /**
     * 蓝牙服务
     */
    private void initService() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        boolean serviceResult = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.i(TAG, "init: bindServiceResult" + serviceResult);
    }

    /**
     * 清空ExpandableListView中的数据
     */
    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    /**
     * 显示ExpandListView列表(设备可操作列表)
     *
     * @param gattServices
     */
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null)
            return;
        String uuid = null;
        //默认值
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        //服务数据集合
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        //操作字符数据集合
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            //获取到的蓝牙设备信息列表
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            //要展示的蓝牙设备信息列表
            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<>();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this, gattServiceData, android.R.layout.simple_expandable_list_item_2,
                new String[]{LIST_NAME, LIST_UUID}, new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData, android.R.layout.simple_expandable_list_item_2, new String[]{LIST_NAME, LIST_UUID},
                new int[]{android.R.id.text1, android.R.id.text2});
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * ExpandableListView的点击事件
     */
    private final ExpandableListView.OnChildClickListener servicesListClickListner = new ExpandableListView.OnChildClickListener() {
        @Override
        public boolean onChildClick(ExpandableListView parent, View v,
                                    int groupPosition, int childPosition, long id) {
            if (mGattCharacteristics != null) {
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
                Log.i(TAG, "onChildClick: " + "charaProp = " + charaProp + ",UUID = " + characteristic.getUuid().toString());
                Random r = new Random();
                //FFF1操作
                if (characteristic.getUuid().toString().equals("0000fff1-0000-1000-8000-00805f9b34fb")) {
                    new AlertDialog.Builder(DeviceControlActivity.this).setItems(new CharSequence[]{"打开", "关闭"}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            String openOrCloseOrderData;
                            if (i == 0)
                                openOrCloseOrderData = ByteUtils.toStringHex1("01");
                            else
                                openOrCloseOrderData = ByteUtils.toStringHex1("00");
                            characteristic.setValue(openOrCloseOrderData.getBytes());
                            mBluetoothLeService.wirteCharacteristic(characteristic);
                        }
                    }).create().show();
                }
                //FFF2操作（连接）
                if (characteristic.getUuid().toString().equals("0000fff2-0000-1000-8000-00805f9b34fb")) {
                    new AlertDialog.Builder(DeviceControlActivity.this).setTitle("确认连接").setMessage("开始握手")
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    String connectOrderdata = "12";//连接命令
                                    characteristic.setValue(connectOrderdata.getBytes());
                                    mBluetoothLeService.wirteCharacteristic(characteristic);
                                }
                            }).create().show();
                }
                //FFF3操作
                if (characteristic.getUuid().toString().equals("0000fff3-0000-1000-8000-00805f9b34fb")) {
                    System.out.println("RT");
                    characteristic.setValue("RT".getBytes());
                    mBluetoothLeService.wirteCharacteristic(characteristic);
                }
                //FFF5操作
                if (characteristic.getUuid().toString().equals("0000fff5-0000-1000-8000-00805f9b34fb")) {
                    int R = r.nextInt(255);
                    int G = r.nextInt(255);
                    int B = r.nextInt(255);
                    int BB = r.nextInt(100);
                    String data = R + "," + G + "," + B + "," + BB;
                    while (data.length() < 18) {
                        data += ",";
                    }
                    characteristic.setValue("S".getBytes());
                    mBluetoothLeService.wirteCharacteristic(characteristic);
                    System.out.println("send S");
                } else {
                    if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (mNotifyCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
                            mNotifyCharacteristic = null;
                        }
                        mBluetoothLeService.readCharacteristic(characteristic);

                    }
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    if (characteristic.getUuid().toString().equals("0000fff6-0000-1000-8000-00805f9b34fb") || characteristic.getUuid().toString().equals("0000fff4-0000-1000-8000-00805f9b34fb")) {
                        Log.i(TAG, "onChildClick: enable notification");
                        mNotifyCharacteristic = characteristic;
                        mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                    }
                }
                return true;
            }
            return false;
        }
    };


    /**
     * 连接设备服务
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                return;
            }
            // Automatically connects to the device upon successful start-up
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    /**
     * 连接状态广播
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            System.out.println("action = " + action);
            switch (action) {
                case BluetoothLeService.ACTION_GATT_CONNECTED:
                    //已连接
                    mConnected = true;
                    updateConnectionState(R.string.connected);
                    invalidateOptionsMenu();
                    break;
                case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                    //未连接/断开连接
                    mConnected = false;
                    updateConnectionState(R.string.disconnected);
                    invalidateOptionsMenu();
                    clearUI();
                    break;
                case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                    //GATT_SERVICES已找到
                    displayGattServices(mBluetoothLeService.getSupportedGattServices());
                    break;
                case BluetoothLeService.ACTION_DATA_AVAILABLE:
                    //数据可用
                    displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                    break;
            }
        }
    };
}
