package com.cw.bluetooth.receiver;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cw.bluetooth.util.ClsUtils;


/**
 * @version V1.0
 * @ClassName: ${CLASS_NAME}
 * @Description: (这里用一句话描述这个类的作用)
 * @create by: chenwei
 * @date 2017/4/18 17:00
 */
public class BluetoothReceiver extends BroadcastReceiver {

    String pin = "12";  //此处为你要连接的蓝牙设备的初始密钥，一般为1234或0000
    String maxAddress = "00:02:5B:00:15:22";
    //String maxAddress = "67:D7:E0:E9:DA:FF";
    //String maxAddress = "D4:97:0B:EA:E3:FB";

    public BluetoothReceiver() {

    }

    //广播接收器，当远程蓝牙设备被发现时，回调函数onReceiver()会被执行
    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction(); //得到action
        Log.e("action1=", action);
        BluetoothDevice btDevice = null;  //创建一个蓝牙device对象
        // 从Intent中获取设备对象
        btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (BluetoothDevice.ACTION_FOUND.equals(action)) {  //发现设备
            Log.e("发现设备:", "[" + btDevice.getName() + "]" + ":" + btDevice.getAddress());

            if (btDevice.getAddress().equalsIgnoreCase(maxAddress))//HC-05设备如果有多个，第一个搜到的那个会被尝试。
            {
                if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {

                    Log.e("ywq", "attemp to bond:" + "[" + btDevice.getName() + "]");
                    try {
                        //通过工具类ClsUtils,调用createBond方法
                        ClsUtils.createBond(btDevice.getClass(), btDevice);
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else
                Log.e("error", "Is faild");
        } else if (action.equals("android.bluetooth.device.action.PAIRING_REQUEST")) //再次得到的action，会等于PAIRING_REQUEST
        {
            Log.e("mylog", "action2=" + action);
            if (btDevice.getAddress().equalsIgnoreCase(maxAddress)) {
                Log.e("mylog", "OKOKOK");

                try {

                    //1.确认配对
                    //ClsUtils.setPairingConfirmation(btDevice.getClass(), btDevice, true);
                    //2.终止有序广播
                    Log.i("mylog", "isOrderedBroadcast:" + isOrderedBroadcast() + ",isInitialStickyBroadcast:" + isInitialStickyBroadcast());
                    abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
                    //3.调用setPin方法进行配对...
                    boolean ret = ClsUtils.setPin(btDevice.getClass(), btDevice, pin);
                    Log.e("mylog", "ret: " + ret);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else
                Log.e("mylog", "这个设备不是目标蓝牙设备");

        }//状态改变时
        else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
            switch (btDevice.getBondState()) {
                case BluetoothDevice.BOND_BONDING://正在配对
                    Log.d("mylog", "正在配对......");
                    break;
                case BluetoothDevice.BOND_BONDED://配对结束
                    Log.d("mylog", "完成配对");
                    break;
                case BluetoothDevice.BOND_NONE://取消配对/未配对
                    Log.d("mylog", "取消配对");
                default:
                    break;
            }
        }
    }
}
