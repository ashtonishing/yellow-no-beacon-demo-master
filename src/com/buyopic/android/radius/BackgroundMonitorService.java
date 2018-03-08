package com.buyopic.android.radius;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.buyopic.android.beacon.R;
import com.buyopic.android.database.BuyopicConsumerDatabase;
import com.buyopic.android.fragments.CloseByListFragment;
import com.buyopic.android.fragments.OffersListFragment;
import com.buyopic.android.models.Beacon;
import com.buyopic.android.models.OrderStatus;
import com.buyopic.android.network.BuyopicNetworkServiceManager;
import com.buyopic.android.utils.Utils;
import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconManager;

/*removed beacon functionality*/
@SuppressLint("HandlerLeak")
public class BackgroundMonitorService extends Service 
//implements IBeaconConsumer, IBeaconDataNotifier, BuyopicNetworkCallBack 

		{

	public static final String KEY_ISFROM_NOTIFICATION = "isfromNotification";
	public static final String KEY_ISFROM_ORDER_NOTIFICATION = "isfromOrderNotification";
	public static final String KEY_ISFROM_NEAREST_BEACON_DATA = "isfromnearestbeacondata";
	private IBeaconManager iBeaconManager = IBeaconManager
			.getInstanceForApplication(this);
	private BuyopicNetworkServiceManager buyopicNetworkServiceManager;
	private IBeacon currentBeacon = null;
	protected boolean isBeaconAvailable = false;
	private BuyopicConsumerDatabase buyopicDatabase;

	private static final int BEACON_FOUND = 200;
	private static final int BEACON_NOT_FOUND = 201;

	// private static final int DEFAULT_RSSI_POWER = -90; //June 25
	private static final int DEFAULT_RSSI_POWER = -70; // June 28
	public static final String KEY_REQUESTING_SERVER = "requesting_server";
	public static final String KEY_BEACON_CHANGED = "beacon_changed";
	public static final String KEY_LATITUDE = "latitude";
	public static final String KEY_LONGITUDE = "longitude";

	private BuyOpic buyOpic;
	protected int count = 1;
	private int mDeviceState;

	@Override
	public void onCreate() {
		super.onCreate();
		//setBluetooth(true);
		buyOpic = (BuyOpic) getApplication();
		
		buyopicNetworkServiceManager = BuyopicNetworkServiceManager
				.getInstance(this);
		buyopicDatabase = BuyopicConsumerDatabase.shareInstance(this);
		
		buyopicDatabase.truncateDatabase();
	/*	iBeaconManager.bind(BackgroundMonitorService.this);
		if (iBeaconManager.isBound(BackgroundMonitorService.this))
			iBeaconManager.setBackgroundMode(BackgroundMonitorService.this,
					true);*/
		setAlarmForCheckUpdates(this);

	}

	public void setAlarmForCheckUpdates(Context context) {
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
		Intent checkUpdatesIntent = new Intent(context,
				CheckUpdatesBroadcast.class);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
				checkUpdatesIntent, 0);

		if (alarmManager != null) {
			alarmManager.cancel(pendingIntent);
		}

		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, 15);
		alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
				calendar.getTimeInMillis(),
				BuyopicNetworkServiceManager.INTERVAL_CHECK_FOR_NEW_ALERTS,
				pendingIntent);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		//iBeaconManager.unBind(this);
	}

	public List<IBeacon> isBeaconsFound(HashMap<Integer, List<IBeacon>> hashmap) {
		List<IBeacon> firstIterationBeacons = null;
		if (hashmap != null && !hashmap.isEmpty()) {
			firstIterationBeacons = hashmap.get(1);
			List<IBeacon> secondIterationBeacons = hashmap.get(2);
			List<IBeacon> thirdIterationBeacons = hashmap.get(3);
			secondIterationBeacons.retainAll(thirdIterationBeacons);
			firstIterationBeacons.retainAll(secondIterationBeacons);
		}
		return firstIterationBeacons;
	}

/*	@Override
	public void onIBeaconServiceConnect() {

		//final long BACKGROUND_SCAN_PERIOD = 5 * 1000l;        //June 25
		//final long BACKGROUND_BETWEEN_SCAN_PERIOD = 5 * 1000l;// June 25
		//final long BACKGROUND_SCAN_PERIOD = 5 * 1000l; 			// June 28
		//final long BACKGROUND_BETWEEN_SCAN_PERIOD = 30 * 1000l;	// June 28
		
		final long BACKGROUND_SCAN_PERIOD = 2 * 1000l; 			// June 29
		final long BACKGROUND_BETWEEN_SCAN_PERIOD = 600 * 1000l;	// June 29
		
		iBeaconManager
				.setBackgroundBetweenScanPeriod(BACKGROUND_BETWEEN_SCAN_PERIOD);
		iBeaconManager.setBackgroundScanPeriod(BACKGROUND_SCAN_PERIOD);

		iBeaconManager
				.setForegroundBetweenScanPeriod(BACKGROUND_BETWEEN_SCAN_PERIOD);
		iBeaconManager.setForegroundScanPeriod(BACKGROUND_SCAN_PERIOD);

		try {
			iBeaconManager.updateScanPeriods();
		} catch (RemoteException e1) {
			e1.printStackTrace();
		}

		iBeaconManager.setRangeNotifier(new RangeNotifier() {

			@Override
			public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons,
					Region region) {
				if (iBeacons != null && iBeacons.size() > 0) {
					Utils.showLog("Beacons Size:" + iBeacons.size());
					Iterator<IBeacon> beaconIterator = iBeacons.iterator();
					count++;

					while ((null != beaconIterator)
							&& (beaconIterator.hasNext())) {
						IBeacon ibeacon = beaconIterator.next();
						if (ibeacon.getRssi() >= DEFAULT_RSSI_POWER) {
							printLog(ibeacon);
							buyOpic.setBeaconAvailable(true);
							buyOpic.setCurrentBeacon(ibeacon);
							currentBeacon = ibeacon;
							isBeaconAvailable = true;
							boolean isExists = buyopicDatabase
									.checkBeaconExistsOrNot(
											String.valueOf(ibeacon.getMajor()),
											String.valueOf(ibeacon.getMinor()),
											ibeacon.getProximityUuid());
							if (!isExists) {
								Message.obtain(handler, BEACON_FOUND, ibeacon)
										.sendToTarget();
							}
						}

					}
				}

				if (count == 3) {
					List<IBeacon> iBeaconsList = new ArrayList<IBeacon>(
							iBeacons);
					Message.obtain(handler, 105, iBeaconsList).sendToTarget();
					count = 1;
				}

			}

		});

		iBeaconManager.setMonitorNotifier(new MonitorNotifier() {
			@Override
			public void didEnterRegion(Region region) {
				try {
					iBeaconManager.startRangingBeaconsInRegion(region);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void didExitRegion(Region region) {
				isBeaconAvailable = false;
				buyOpic.setBeaconAvailable(false);
				buyOpic.setCurrentBeacon(currentBeacon);
				Message.obtain(handler, BEACON_NOT_FOUND).sendToTarget();
				Utils.showLog("No Beacons Found");
			}

			@Override
			public void didDetermineStateForRegion(int state, Region region) {
			}

		});

		try {
			iBeaconManager.startMonitoringBeaconsInRegion(new Region(
					"myMonitoringUniqueId",
					null, null, null));
			iBeaconManager.startMonitoringBeaconsInRegion(new Region(
					"myMonitoringUniqueId",
					"8deefbb9-f738-4297-8040-96668bb44281", null, null));
		} catch (RemoteException e) {
		}
	}*/

	private void printLog(IBeacon ibeacon) {
		Utils.showLog("Beacon Minor -->"+ibeacon.getMinor()+",Rssi-->"+ibeacon.getRssi());
	}

/*	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case BEACON_FOUND:
				IBeacon iBeacon = (IBeacon) msg.obj;
				Beacon beacon = new Beacon();
				beacon.setIsNotified("f");
				beacon.setmBeaconUUID(iBeacon.getProximityUuid());
				beacon.setmMajor(String.valueOf(iBeacon.getMajor()));
				beacon.setmMinor(String.valueOf(iBeacon.getMinor()));
				beacon.setmRssi(String.valueOf(iBeacon.getRssi()));
				beacon.setmEnteredTime(getFormattedTime());
				beacon.setmBeaconId_Minor_Major(beacon.getmMajor() + "_"
						+ beacon.getmMinor());
				long id = buyopicDatabase.insertItem(beacon);
				if (id != -1) {
					buyopicNetworkServiceManager.sendSaveConsumerEntryRequest(
							Constants.REQUEST_SEND_ENTRY_REQUEST,
							buyOpic.getmConsumerId(), beacon.getmBeaconUUID(),
							beacon.getmMajor(), beacon.getmMinor(),
							getFormattedTime(), beacon.getmRssi(),BackgroundMonitorService.this);
					notifyBeaconDataChanged("string");
					buyopicNetworkServiceManager.sendCheckBeaconAlertsRequest(
							Constants.REQUEST_CHECK_BEACON_ALERTS,
							beacon.getmBeaconUUID(), beacon.getmMinor(),
							beacon.getmMajor(), BackgroundMonitorService.this);

				}
				break;
			case BEACON_NOT_FOUND:
				truncateTheBeaconInfo();
				break;
			case 105:
				@SuppressWarnings("unchecked")
				List<IBeacon> iBeaconsList = (List<IBeacon>) msg.obj;
				if (iBeaconsList != null && !iBeaconsList.isEmpty()) {
					String majors_and_minors = "";
					for (IBeacon iBeacon2 : iBeaconsList) {
						buyopicNetworkServiceManager.sendUpdateBeaconRssiLevelsRequest(Constants.REQUEST_RSSI_UPDATE, buyOpic.getmConsumerId(), iBeacon2, BackgroundMonitorService.this);
						if (TextUtils.isEmpty(majors_and_minors)) {
							majors_and_minors = "\'" + iBeacon2.getMajor()
									+ "_" + iBeacon2.getMinor() + "\'";
						} else {
							majors_and_minors += "," + "\'"
									+ iBeacon2.getMajor() + "_"
									+ iBeacon2.getMinor() + "\'";
						}
					}
					List<Beacon> notInBeaconList = buyopicDatabase
							.getNotInBeaconDetails(majors_and_minors);
					if (notInBeaconList != null && !notInBeaconList.isEmpty()) {

//						printBeaconsInformation(notInBeaconList);
						sendExitTimeRequests(notInBeaconList);

						int rowsAffected = buyopicDatabase
								.deleteExitedBeacons(majors_and_minors);
						if (rowsAffected > 0) {
							notifyBeaconDataChanged("string");
						}
					}

					Utils.showLog("All Records Which are in database");
					List<Beacon> beacons = buyopicDatabase
							.getAllBeaconDetails();
					printBeaconsInformation(beacons);
				}
				break;
			default:
				break;
			}

		}

	};*/

	private void notifyBeaconDataChanged(Object object) {
		Intent intent = new Intent(
				CloseByListFragment.ACTION__BEACON_DATA_CHANGED_INTENT);
		if (object instanceof Boolean) {
			boolean status = (Boolean) object;
			intent.putExtra(KEY_REQUESTING_SERVER, status);
		} else if (object instanceof String) {
			intent.putExtra(KEY_BEACON_CHANGED, true);
		}
		sendBroadcast(intent);
	}

	/*protected void sendExitTimeRequests(List<Beacon> beacons) {
		if (beacons != null && !beacons.isEmpty()) {
			for (Beacon beacon : beacons) {
				buyopicNetworkServiceManager.sendSaveConsumerExitRequest(
						Constants.REQUEST_SEND_ENTRY_REQUEST,
						buyOpic.getmConsumerId(), beacon.getmBeaconUUID(),
						beacon.getmMajor(), beacon.getmMinor(),
						beacon.getmEnteredTime(), getFormattedTime(),beacon.getmRssi(),
						BackgroundMonitorService.this);

			}
		}
	}*/

	private static String getFormattedTime() {
		SimpleDateFormat dateFormat = new SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss Z", Locale.US);
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		return dateFormat.format(new Date());
	}


	/*private void truncateTheBeaconInfo() {
		isBeaconAvailable = false;
		currentBeacon = null;
		buyOpic.setBeaconAvailable(false);
		buyOpic.setCurrentBeacon(currentBeacon);

		List<Beacon> beaconsList = buyopicDatabase.getAllBeaconDetails();
		if (beaconsList != null && !beaconsList.isEmpty()) {
			sendExitTimeRequests(beaconsList);

		}
		buyopicDatabase.truncateDatabase();
		notifyBeaconDataChanged("string");
	}*/

	protected void printBeaconsInformation(List<Beacon> beacons) {

		if (beacons != null && !beacons.isEmpty()) {
			for (Beacon beacon : beacons) {
				Utils.showLog(beacon.getmMajor() + " and " + beacon.getmMinor()
						+ " Found In Database");
			}
		} else {
			Utils.showLog("No Records In Data base");
		}
	}

	public void sendNotification(String msg, String storeId, String retailerId,
			String storeName) {
		NotificationManager mNotificationManager = (NotificationManager) this
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Intent intent = new Intent(this, HomePageSetupActivity.class);
		intent.putExtra(KEY_ISFROM_NOTIFICATION, true);
		intent.putExtra(OffersListFragment.KEY_STORE_NAME, storeName);
		intent.putExtra(OffersListFragment.KEY_STORE_ID, storeId);
		intent.putExtra(OffersListFragment.KEY_RETAILER_ID, retailerId);
		PendingIntent pendingIntent = null;
		int id = (int) System.currentTimeMillis();
		pendingIntent = PendingIntent.getActivity(this, id, intent, 0);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				this).setSmallIcon(R.drawable.logo)
				.setContentTitle(getString(R.string.app_name))
				.setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
				.setAutoCancel(true).setContentText(msg);
		if (pendingIntent != null) {
			mBuilder.setContentIntent(pendingIntent);
		}
		@SuppressWarnings("resource")
		Scanner in = new Scanner(storeId).useDelimiter("[^0-9]+");
		long integer = in.nextLong();
		mNotificationManager.notify((int)integer, mBuilder.build());
	}
	public void sendOrderNotification(String msg,Context mContext,OrderStatus mOrderStatusObj) {
		NotificationManager mNotificationManager = (NotificationManager) mContext
				.getSystemService(Context.NOTIFICATION_SERVICE);
		Log.i("NOTIFY", "mNotificationManager"+mNotificationManager);
		Intent intent = new Intent(mContext, HomePageSetupActivity.class);
		intent.putExtra(KEY_ISFROM_ORDER_NOTIFICATION, true);
		PendingIntent pendingIntent = null;
		int id = (int) System.currentTimeMillis();
		pendingIntent = PendingIntent.getActivity(mContext, id, intent, 0);
		Log.i("CLOSEBY", "sendOrderNotification"+msg);
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				mContext).setSmallIcon(R.drawable.logo)
				.setContentTitle(mContext.getString(R.string.app_name))
				.setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
				.setAutoCancel(true).setContentText(msg);
		if (pendingIntent != null) {
			mBuilder.setContentIntent(pendingIntent);
		}
		@SuppressWarnings("resource")
		Scanner in = new Scanner(mOrderStatusObj.getStoreId()).useDelimiter("[^0-9]+");
		long integer = in.nextLong();
		mNotificationManager.notify((int) integer, mBuilder.build());
	}

	private boolean setBluetooth(boolean enable) {
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		boolean isEnabled = bluetoothAdapter.isEnabled();
		if (enable && !isEnabled) {
			return bluetoothAdapter.enable();
		} else if (!enable && isEnabled) {
			return bluetoothAdapter.disable();
		}
		return true;
	}

	/*@Override
	public void iBeaconDataUpdate(IBeacon arg0, IBeaconData arg1,
			DataProviderException arg2) {
		Utils.showLog("Major:" + arg0.getMajor() + "Minor:" + arg0.getMinor());
	}*/

/*	@Override
	public void onSuccess(int requestCode, Object object) {
		switch (requestCode) {
		case Constants.REQUEST_CHECK_BEACON_ALERTS:
			HashMap<String, String> hashMap = JsonResponseParser
					.parseCheckBeaconAlertResponse((String) object);
			if (hashMap != null && !hashMap.isEmpty()) {
				String storeId = hashMap.get(OffersListFragment.KEY_STORE_ID);
				String retailerId = hashMap
						.get(OffersListFragment.KEY_RETAILER_ID);
				String storeName = hashMap.get("store_name");
				sendNotification(
						"The " + storeName + " has something for you!",
						storeId, retailerId, storeName);
				if (currentBeacon != null) {
					Beacon beacon = buyopicDatabase.getBeaconDetails(
							String.valueOf(currentBeacon.getMajor()),
							String.valueOf(currentBeacon.getMinor()));
					if (beacon != null
							&& beacon.getIsNotified().equalsIgnoreCase("f")) {

						beacon.setIsNotified("t");
						// buyopicDatabase.insertItem(beacon);
					}
				}

			}
		default:
			break;
		}
	}

	@Override
	public void onFailure(int requestCode, String message) {

	}*/

}
