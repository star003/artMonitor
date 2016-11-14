package ru.star003.artMonitor;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
/*
 *  20 октября 2016 года Халиман А А
 *  передача показаний на народный мониторинг
 *  
 */
public class btManager extends Activity {

	goBt mt;
	TextView 	myLabel;
	TextView 	labBtStat;
	TextView 	lbTime;
	TextView 	lbUpload;
	TextView 	tbTablo;
	EditText 	myTextbox;
	//ListView 	lvMain;
	
	BluetoothAdapter 	mBluetoothAdapter;
	BluetoothSocket 	mmSocket;
	BluetoothDevice 	mmDevice;
	OutputStream 		mmOutputStream;
	InputStream 		mmInputStream;
	Thread 				workerThread;
	Button 				btExit;

	byte[] 				readBuffer;
	int 				readBufferPosition;
	int 				counter;
	volatile boolean 	stopWorker;
	private static final String TAG = "btManager";
	String data = "";
	private boolean 	isConnected=true;
	IntentFilter 		filter = new IntentFilter();
	Map<String,String> 	pool= new HashMap<String, String>();
	String[] names = { "1", "2"};
	//ArrayAdapter<String> adapter;
	
	//private static Context mContext;
    public static btManager instace;
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		Log.d(TAG, "старт");
		
		if (android.os.Build.VERSION.SDK_INT > 9) {
			
			StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy); 
			
		}
		
		Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler(this));

		
		setContentView(R.layout.bt_manager);
		
		//**запретим отключать дисплей
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		Typeface face=Typeface.createFromAsset(getAssets(), "Electron.ttf");
		
		//**установим шрифты
		myLabel = (TextView) findViewById(R.id.label);
		myLabel.setTypeface(face);
		
		labBtStat= (TextView) findViewById(R.id.labBtStat);
		labBtStat.setTypeface(face);
		
		lbTime = (TextView) findViewById(R.id.lbCurrTime);
		lbTime.setTypeface(face);
		
		lbUpload= (TextView) findViewById(R.id.lbUpload);
		lbUpload.setTypeface(face);
		
		tbTablo= (TextView) findViewById(R.id.tbTablo);
		
		btExit = (Button) findViewById(R.id.btExit);
		OnClickListener btExitCl = new OnClickListener() { //***создали обработчик нажатия
			
			@Override
			public void onClick(View arg0) {
				
				Intent intent = new Intent(Intent.ACTION_MAIN);
			    intent.addCategory(Intent.CATEGORY_HOME);
			    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			    startActivity(intent);
			    android.os.Process.killProcess(android.os.Process.myPid());
				
			}
		};
		btExit.setOnClickListener(btExitCl); //*** привяжем кнопку button1 к обработчику oclBtnOk 
		
		/*
		lvMain = (ListView) findViewById(R.id.lvMain);
		//lvMain.setTypeface(face);
		
		
		ArrayAdapter<String>adapter = new ArrayAdapter<String>(this,
		        android.R.layout.simple_list_item_1, names);
		 
		    // присваиваем адаптер списку
		lvMain.setAdapter(adapter);
		*/
		//** запустим поиск и подключение блютус в асинхронную задачу,
		//** чтобы не грузить программу
		mt = new goBt ();
	    mt.execute();
	    
	    refreshTime(1000);
	    
	    refreshDisplayTemperature(1000); //**обновление показаний температуры
			
	    reconectBt(10000); //***таймер перезапуска соединения при его потере
	    
	    sendToWeb(301000); //**выгрузка на веб
	    
	    showT(20000); //**отладочные данные в консоль
	    
	    sendToThingspeak(60000); //**отправка на Thingspeak
		
	    //**опишем интенты для получения рассылок от блютус
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mReceiver, filter); //**зарегистрируем слушателя
		
	}//onCreate

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	@Override
    public Context getApplicationContext() {
		
        return super.getApplicationContext();
        
    }//getApplicationContext
 
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
    public static btManager getIntance() {
        return instace;
    }//btManager
    
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	void findBT() throws IOException {

		Log.d(TAG, "поиск BT");
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (mBluetoothAdapter == null) {
			myLabel.setText("No bluetooth adapter available");
			Log.d(TAG, "нет доступных ВТ адаптеров");
		}

		if (!mBluetoothAdapter.isEnabled()) {
			
			Intent enableBluetooth = new Intent(
			BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBluetooth, 0);
				
		}

		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
		
		if (pairedDevices.size() > 0) {

			Log.d(TAG, "pairedDevices.size()=" + pairedDevices.size());
			
			for (BluetoothDevice device : pairedDevices) {
				
				if (device.getName().equals("art")) {
					
					mmDevice = device;
					Log.d(TAG, "нашли ART");
					break;
					
				}
				
			}
			
		}
		
		myLabel.setText("OK");
		Log.d(TAG, "не найдено устройств ВТ");
		
	}//findBT
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	void openBT() throws IOException {
		
		try {
			
		
			UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); // Standard
			mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
			mmSocket.connect();
			mmOutputStream = mmSocket.getOutputStream();
			mmInputStream = mmSocket.getInputStream();
			//beginListenForData();
			Log.d(TAG, "mmSocket.connect()");
		
		}
		
		catch (IOException e) {
			
			Log.d(TAG, "не удачно openBT()"+e);
			
		}
		
	}//openBT

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	void beginListenForData() throws IOException {
		
		Log.d(TAG, "запуск beginListenForData()");
		final Handler handler = new Handler();
		final byte delimiter = 10; // This is the ASCII code for a newline
									// character
		stopWorker = false;
		readBufferPosition = 0;
		readBuffer = new byte[1024];
		
		workerThread = new Thread(new Runnable() {
			
		public void run() {
				
			while (!Thread.currentThread().isInterrupted() && !stopWorker) {
				
				//Log.d(TAG, "beginListenForData() - run()");
					
				try {
						
					int bytesAvailable = mmInputStream.available();
					if (bytesAvailable > 0) {
							
					byte[] packetBytes = new byte[bytesAvailable];
					mmInputStream.read(packetBytes);
							
					for (int i = 0; i < bytesAvailable; i++) {
								
						byte b = packetBytes[i];
								
						if (b == delimiter) {
									
							byte[] encodedBytes = new byte[readBufferPosition];
							System.arraycopy(readBuffer, 0,encodedBytes, 0, encodedBytes.length);
							//final String 
							data = new String(encodedBytes, "US-ASCII");
							/*
							 * запись данных в пул датчиков
							 */
							
							try {
								
								//Log.d(TAG, "DATA  "+data);
								
								if (data.split(":")[0].length()>5 && data.split(":")[0].length()<20) {
									
									if(!data.split(":")[2].equals("85.00")) {
										
										if(!data.split(":")[2].equals("-127.00")) {
									
											/*для совместимости показаний старого датчика
											 * который оказался 281CDA8A04000087
											 * а нужно передавать 280000048ADA1C
											*/
											if(data.split(":")[0].equals("281CDA8A04000087")) {
												
												pool.put("280000048ADA1C", data.split(":")[2].replaceAll("^\\s+", ""));
												
											}
											else {
												
												pool.put(data.split(":")[0].replaceAll("^\\s+", "") , data.split(":")[2].replaceAll("^\\s+", ""));
											
											}
											
										}	
										
									}	
								
								}

							} catch (ArrayIndexOutOfBoundsException e) {

							}

							
							readBufferPosition = 0;
							
							//Log.d(TAG, "...DATA ..."+data);
							
							handler.post(new Runnable() {
											
							public void run() {
											
								//myLabel.setText(data);
											
							}
										
						});
									
					} 
					else {
									
						readBuffer[readBufferPosition++] = b;
									
					}
								
				}
							
			}
						
		} 
					
		catch (IOException ex) {
						
			stopWorker = true;
			Log.d(TAG, "beginListenForData() - run() "+ex);
						
		}
					
		}
			
		}
			
		});

		workerThread.start();
		
	}//beginListenForData

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	void closeBT() throws IOException {
		
		stopWorker = true;
		mmOutputStream.close();
		mmInputStream.close();
		mmSocket.close();
		myLabel.setText("Bluetooth Closed");
		
	}//closeBT

	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	class goBt extends AsyncTask<Void, Void, Void> {
		
		/////////////////////////////////////////////////////////////////////////////////////		

		@Override
		protected void onPreExecute() {
			
		      super.onPreExecute();
		      
		}//protected void onPreExecute()

		/////////////////////////////////////////////////////////////////////////////////////

		@Override
		protected Void doInBackground(Void... params) {
						
			Log.d(TAG, "...findBT();...");
			try {
				
				findBT();
				
			} catch (IOException e1) {
				
				e1.printStackTrace();
				
			}
			
			try {
				
				openBT();
				Log.d(TAG, "...openBT();...");
				
			} catch (IOException e) {
				
				e.printStackTrace();
				
			}
			
		    return null;
	    }//protected Void doInBackground(Void... params)

		/////////////////////////////////////////////////////////////////////////////////////

		@Override
	    protected void onPostExecute(Void result) {
			
	    	super.onPostExecute(result);
	    	try {
	    		
				beginListenForData();
				
			} catch (IOException e) {
				
				e.printStackTrace();
				
			}
			
	    }//protected void onPostExecute(Void result)
		
	}//class goBt extends AsyncTask<Void, Void, Void>
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();

	        //We don't want to reconnect to already connected device
	        if(isConnected==false){
	            // When discovery finds a device
	            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	                // Get the BluetoothDevice object from the Intent
	                //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

	                // Check if the found device is one we had comm with
	                //if(device.getAddress().equals(partnerDevAdd)==true) {}
	                    //connectToExisting(device);
	            }
	        }

	        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

	            // Check if the connected device is one we had comm with
	            if(device.getName().equals("art")==true)
	                isConnected=true;
	        }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
	            // Get the BluetoothDevice object from the Intent
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

	            // Check if the connected device is one we had comm with
	            if(device.getName().equals("art")==true)
	                isConnected=false;
	        }
	    }
	};
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void refreshDisplayTemperature(final int refreshIntervalMls) {

			// **Обновление показаний каждую секунду
			Thread t = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									if (isConnected) {

										try {

											
											myLabel.setText(data.split(":")[2]);
											labBtStat.setText(isConnected ? "ON"
													: "OFF");
																						
											//names.notifyAll()
											//adapter.notifyDataSetChanged();
											/*
											for (Map.Entry<String, String> entry : pool.entrySet()){

											//	Log.d(TAG, "pool entry.getKey() "+entry.getKey()+" entry.getValue() " +entry.getValue());
												
											}
											*/

										} catch (ArrayIndexOutOfBoundsException e) {

											myLabel.setText("E " + (isConnected ? "ON"
													: "OFF"));
											
											//Log.d(TAG, "DATA EMTY "+e);

										}

									} else {

										myLabel.setText("E ");
										labBtStat.setText(isConnected ? "ON"
												: "OFF");
										
										Log.d(TAG, "else isConnected");

									}
								}

							});

						}

					} catch (InterruptedException e) {
					}

				}

			};

			t.start();
		} // refreshDisplayTemperature

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void reconectBt(final int refreshIntervalMls) {
			// ***таймер перезапуска соединения при его потере

			Thread t1 = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									if (!isConnected) {

										Log.d(TAG, "RESET CONNECTION");
										
										try {
											
											findBT();
											
										} catch (IOException e1) {
											// TODO Auto-generated catch block
											Log.d(TAG, "проблема с поиском блютус");
										}

										try {

											openBT();

										} catch (IOException e) {

											Log.d(TAG, "проблема с открытием блютус");

										}

									}

								}

							});

						}

					} catch (InterruptedException e) {
					}

				}

			};

			t1.start();
		}//reconectBt

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void refreshTime(final int refreshIntervalMls) {
			// ***таймер обновления времени

			Thread t1 = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									Calendar currentTime = Calendar.getInstance();
									String h = ((currentTime.get(11)) >= 10 ? String
											.valueOf(currentTime.get(11)) : "0"
											+ String.valueOf(currentTime.get(11)))
											+ ":"
											+ ((currentTime.get(12)) >= 10 ? String
													.valueOf(currentTime.get(12))
													: "0"
															+ String.valueOf(currentTime
																	.get(12)));
									lbTime.setText(h);

								}

							});

						}

					} catch (InterruptedException e) {
					}

				}

			};

			t1.start();
		}//refreshTime
		
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void sendToWeb(final int refreshIntervalMls) {
			
			// *** отправка данных  

			Thread t1 = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									Calendar currentTime = Calendar.getInstance();
									String h = ((currentTime.get(11)) >= 10 ? String
											.valueOf(currentTime.get(11)) : "0"
											+ String.valueOf(currentTime.get(11)))
											+ ":"
											+ ((currentTime.get(12)) >= 10 ? String
													.valueOf(currentTime.get(12))
													: "0"
															+ String.valueOf(currentTime
																	.get(12)));
									lbUpload.setText(h);
									
									//**тут пишем код выгрузки
									//DefaultHttpClient hc = new DefaultHttpClient();
									//ResponseHandler response = new BasicResponseHandler();
																		
									//String tp ="";
									if (isConnected) {
										
										Log.d(TAG, "WEB запрос отправляем");
										HttpURLConnection conn = null;
										int _response = 0;
										URL url = null;
										
										try {
											
											for (Map.Entry<String, String> entry : pool.entrySet()){

												String adr = "http://narodmon.ru/get?ID=02:50:f3:00:00:00&"+entry.getKey()+"="+entry.getValue();
												Log.d(TAG, ".."+adr);
												url = new URL(adr);
												conn = (HttpURLConnection) url.openConnection();
												conn.connect();
												_response = conn.getResponseCode();
												Log.d(TAG, "ответ "+_response);
												
											}
											
											pool.clear(); //**очистим пул после передачи
											Log.d(TAG, "pool очищен ");
										} 
										catch (ArrayIndexOutOfBoundsException e) {
											
											Log.d(TAG, "запрос не отослан ArrayIndexOutOfBoundsException "+e);
											lbUpload.setText("ERR ");
										}
										catch (MalformedURLException e) {
											
											Log.d(TAG, "вэб запрос не отослан MalformedURLException "+e);
											lbUpload.setText("ERR");
										}
											
										catch (IOException e) {
											
											Log.d(TAG, "запрос не отослан IOException "+e);
											Log.d(TAG, "..."+e);
											lbUpload.setText("ERR");
											
										}
										
									}
									else {
										
										Log.d(TAG, "запрос не отослан , нет связи с датчиком");
										
									}
								}

							});

						}

					} catch (InterruptedException e) {
					}

				}

			};

			t1.start();
		}//sendToWeb
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public void sendToThingspeak(final int refreshIntervalMls) {
			
			// *** отправка данных на Thingspeak  

			Thread t1 = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									Calendar currentTime = Calendar.getInstance();
									String h = ((currentTime.get(11)) >= 10 ? String
											.valueOf(currentTime.get(11)) : "0"
											+ String.valueOf(currentTime.get(11)))
											+ ":"
											+ ((currentTime.get(12)) >= 10 ? String
													.valueOf(currentTime.get(12))
													: "0"
															+ String.valueOf(currentTime
																	.get(12)));
									//lbUpload.setText(h);
									
									//**тут пишем код выгрузки
									if (isConnected) {
										
										
										Log.d(TAG, "Thingspeak WEB запрос отправляем");
										HttpURLConnection conn = null;
										int _response = 0;
										URL url = null;
										/*
										pool.clear();
										pool.put("2839F58A0400004D", "10.51");
										pool.put("2819ED8A040000B9", "4.81");
										pool.put("280000048ADA1C", "-5.56");
										*/
										
										try {
											
											String adr 		= "https://api.thingspeak.com/update?api_key=KBSQDPB60RIK2DT8";
											String hvost 	= "";
											String iStr 	= "";
											
											for (Map.Entry<String, String> entry : pool.entrySet()){
												
												if (entry.getKey().equalsIgnoreCase("280000048ADA1C")){
													
													hvost =hvost.concat("&field1="+unReplace(entry.getValue()));
															
												}
												
												else if (entry.getKey().equalsIgnoreCase("2819ED8A040000B9")){
													
													hvost=hvost.concat("&field2="+unReplace(entry.getValue()));
													
												}
												
												else if (entry.getKey().equalsIgnoreCase("2839F58A0400004D")){
													
													hvost=hvost.concat("&field3="+unReplace(entry.getValue()));
													
												}
														
											}
											
											Log.d(TAG, "Thingspeak WEB запрос "+(adr+hvost).replaceAll("^\\s+", ""));
											String toTablo = h+" "+iStr+" "+(adr+hvost).replaceAll("^\\s+", "");
											
											tbTablo.setText(toTablo);
											
											url = new URL((adr+hvost).replaceAll("^\\s+", ""));
											conn = (HttpURLConnection) url.openConnection();
											conn.connect();
											_response = conn.getResponseCode();
											Log.d(TAG, "Thingspeak WEB ответ "+_response);
											tbTablo.setText(toTablo+" "+String.valueOf(_response));
											
										} 
										catch (ArrayIndexOutOfBoundsException e) {
											
											Log.d(TAG, "Thingspeak WEB запрос не отослан ArrayIndexOutOfBoundsException "+e);
											lbUpload.setText("ERR ");
										}
										catch (MalformedURLException e) {
											
											Log.d(TAG, "Thingspeak WEB запрос не отослан MalformedURLException "+e);
											lbUpload.setText("ERR");
										}
											
										catch (IOException e) {
											
											Log.d(TAG, "Thingspeak WEB запрос не отослан IOException "+e);
											Log.d(TAG, "..."+e);
											lbUpload.setText("ERR");
											
										}
										
									}
									else {
										
										Log.d(TAG, "Thingspeak WEB запрос не отослан , нет связи с датчиком");
										
									}
								}

							});

						}

					} catch (InterruptedException e) {
					}

				}

			};

			t1.start();
		}//sendToThingspeak
		
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public void showT(final int refreshIntervalMls) {
			
			// *** в лог текущие данные  

			Thread t1 = new Thread() {
				@Override
				public void run() {

					try {

						while (!isInterrupted()) {

							Thread.sleep(refreshIntervalMls);
							runOnUiThread(new Runnable() {

								@Override
								public void run() {

									for (Map.Entry<String, String> entry : pool.entrySet()){

										Log.d(TAG, "pool "+entry.getKey()+" = " +entry.getValue());
										//Log.d(TAG, "data "+data);
										/*
										int t = 0;
										double r = t/0;
										//эмуляция пиздеца.....
										*/
									}
									//Log.d(TAG, "...вэб запрос не отослан , нет связи с датчиком...");
									
								}

							});

						}

					} catch (InterruptedException e) {
						
						
					}

				}

			};

			t1.start();
		}//showT
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	String unReplace(String en){
		/*
		 * удаляем все что не -.0123456789 из переданой строки
		 */
		
		String st = "-.0123456789";
		String itStr = "";
		for (int i =0 ; i<en.length()-1; i++){
			
			System.out.println(en.substring(i,i+1));
			
			if (st.contains(en.substring(i,i+1))) {
				
				itStr+=en.substring(i,i+1);
				
			}
			
		}
		
		return itStr;
		
	}//unReplace
	
}//btManager

