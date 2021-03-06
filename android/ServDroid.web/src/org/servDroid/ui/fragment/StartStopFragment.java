/*
 * Copyright (C) 2013 Joan Puig Sanz
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

package org.servDroid.ui.fragment;

import org.servDroid.db.LogHelper;
import org.servDroid.helper.IPreferenceHelper;
import org.servDroid.helper.IServiceHelper;
import org.servDroid.helper.IServiceHelper.ServerStatusListener;
import org.servDroid.helper.ServiceHelper;
import org.servDroid.server.service.ServerValues;
import org.servDroid.server.service.ServiceController;
import org.servDroid.util.Logger;
import org.servDroid.util.NetworkIp;
import org.servDroid.web.R;

import roboguice.inject.InjectView;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ToggleButton;

import com.google.inject.Inject;

public class StartStopFragment extends ServDroidBaseFragment implements OnCheckedChangeListener,
		ServerStatusListener {

	@InjectView(R.id.toggleButtonStartStop)
	private ToggleButton mStartStopButton;

	@InjectView(R.id.textViewUrl)
	private TextView mTextViewUrl;

	@Inject
	private LogHelper mLogAdapter;

	@Inject
	private Context mContex;

	@Inject
	private IServiceHelper serviceHelper;

	@Inject
	private IPreferenceHelper mPreferenceHelper;
	
	private OnStartStopButtonPressed mOnStartStopButtonPressed;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.start_stop_fragment, container, false);

		if (getActivity() instanceof OnStartStopButtonPressed){
			mOnStartStopButtonPressed = (OnStartStopButtonPressed) getActivity();
		}
		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mStartStopButton.setOnCheckedChangeListener(this);

	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		if (isChecked) {
			startServer();
		} else {
			stopService();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		serviceHelper.connect(new Runnable() {
			@Override
			public void run() {
				try {
					serviceHelper.addServerStatusListener(StartStopFragment.this);
					if (serviceHelper.getServiceController().getStatus() == ServerValues.STATUS_RUNNING) {
						mStartStopButton.setChecked(true);
						setUrlText(true);
					} else {
						mStartStopButton.setChecked(false);
						setUrlText(false);
					}
				} catch (RemoteException e) {
					Logger.e("Error resuming the connection to the service", e);
					setErrorConnectingService();
				}
			}
		});
		serviceHelper.addServerStatusListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		serviceHelper.removeServerStatusListener(this);
		serviceHelper.disconnect();
	}

	private void startServer() {
		try {
			serviceHelper.startServer(mPreferenceHelper.getServerParameters());
			serviceHelper.getServiceController().setVibrate(mPreferenceHelper.getVibrate());
			Thread.sleep(500);
			setUrlText (serviceHelper.getServiceController().getStatus() == ServerValues.STATUS_RUNNING);
		} catch (RemoteException e) {
			Logger.e("Error starting the server", e);
			setErrorConnectingService();
		} catch (InterruptedException e) {
			Logger.e("Warning starting the server", e);
		} catch (Exception e) {
			Logger.e("Error starting the server", e);
			setErrorConnectingService();
		}
		if (mOnStartStopButtonPressed != null){
			mOnStartStopButtonPressed.onStartStopButtonPressed(mStartStopButton.isChecked());
		}
	}

	private void setUrlText(boolean running) {
		if (running) {
			WifiManager wifiManager = (WifiManager) mContex.getSystemService(Context.WIFI_SERVICE);
			int port;
			try {
				// We make sure that this is the port in use
				port = serviceHelper.getServiceController().getCurrentParams().getPort();
			} catch (RemoteException e) {
				port = mPreferenceHelper.getPort();
				Logger.e("Error getting the port in use", e);
			}
			if (isAdded()){
				mTextViewUrl.setText(getText(R.string.server_url) + " "
						+ NetworkIp.getWifiIp(wifiManager) + ":" + port);
			}
		} else {
			if (isAdded()){
				mTextViewUrl.setText(R.string.text_stopped);
			}
		}
	}

	private void setErrorConnectingService() {
		mStartStopButton.setChecked(false);
		mTextViewUrl.setText(R.string.error_connecting_service);
	}

	private void stopService() {
		try {
			serviceHelper.stopServer();
			setUrlText(false);
			Thread.sleep(500);
		} catch (RemoteException e) {
			Logger.e("Error stoping the server", e);
			setErrorConnectingService();
		} catch (InterruptedException e) {
			Logger.e("Warning stoping the server", e);
		}
		if (mOnStartStopButtonPressed != null){
			mOnStartStopButtonPressed.onStartStopButtonPressed(mStartStopButton.isChecked());
		}
	}

	@Override
	public void onServerStatusChanged(ServiceController serviceController, final int status) {
		if (getActivity() == null)
			return;

		switch (status) {
		case ServiceHelper.STATUS_RUNNING:
		case ServiceHelper.STATUS_STOPPED:
			Activity activity = getActivity();
			if (activity == null){
				return;
			}
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mStartStopButton.setChecked(status == ServiceHelper.STATUS_RUNNING);
					setUrlText(status == ServiceHelper.STATUS_RUNNING);
				}
			});
			break;
		case ServiceHelper.STATUS_DISCONNECTED:
			serviceHelper.connect();
			break;
		default:
			break;
		}

	}
	
	public static interface OnStartStopButtonPressed{
		public void onStartStopButtonPressed(boolean pressed);
	}

}
