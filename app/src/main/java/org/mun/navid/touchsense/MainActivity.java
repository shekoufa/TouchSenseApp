package org.mun.navid.touchsense;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.inputmethodservice.Keyboard;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferType;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;

import org.mun.navid.touchsense.utils.Util;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import weka.classifiers.Classifier;

public class MainActivity extends AppCompatActivity implements SensorListener {

    private static final float SHAKE_THRESHOLD = 40;
    private static final String ACCESS_KEY = "SOMEACCESSKEY";
    private static final String SECRET_KEY = "SOMESECRETKEY";
    private static final String COUNTER_KEY = "COUNTER";
    private static final int INIT_COUNTER = 30;
    private static final String MODELS_BUCKET = "models-info";
    private CustomKeyboardView mKeyboardView;
    private EditText typedText;
    private Keyboard completeKeyboard;
    private InputMethodManager im;
    private TextView textToType;
    private Button nextBtn;
    private TransferUtility transferUtility;
    private SimpleAdapter simpleAdapter;
    private static final int INDEX_NOT_CHECKED = -1;
    private int checkedIndex;
    /**
     * This map is used to provide data to the SimpleAdapter above. See the
     * fillMap() function for how it relates observers to rows in the displayed
     * activity.
     */
    private ArrayList<HashMap<String, Object>> transferRecordMaps = new ArrayList<HashMap<String, Object>>();
    private List<TransferObserver> observers;
    private SensorManager sensorMgr;
    private long lastUpdate = 0;
    private float lastX = 0;
    private float lastY = 0;
    private float lastZ = 0;
    private float speed;
    private TextView tryCounterView;
    private int counter;
    private ArrayList<String> dictionary;
    private Random wordNumberRandomGenerator;
    private Keyboard numberKeyboard;
    private Keyboard selectedKeyboard;
    private Classifier cls;
    private RelativeLayout trainLayout;
    private String selectedTab = "train";
    private Classifier testClassifier;
    private Button nextTestBtn;
    private String androidId;
    private Handler mHandler;



    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    showTrainElements();
                    return true;
                case R.id.navigation_dashboard:
                    hideTrainElements();
                    return true;
            }
            return false;
        }

        private void showTrainElements() {
            selectedTab = "train";
            mKeyboardView.setCurrentPage(selectedTab);
            TextView headerTitle = (TextView) findViewById(R.id.textView5);
            headerTitle.setText("Number of tries remaining:");
            findViewById(R.id.tryCounter).setVisibility(View.VISIBLE);
            findViewById(R.id.nextWordBtn).setVisibility(View.VISIBLE);
            findViewById(R.id.nextTestButton).setVisibility(View.GONE);
            findViewById(R.id.textView).setVisibility(View.VISIBLE);
            findViewById(R.id.typedText).setVisibility(View.VISIBLE);
            findViewById(R.id.shownText).setVisibility(View.VISIBLE);
            findViewById(R.id.textView2).setVisibility(View.VISIBLE);
        }

        private void hideTrainElements() {
            selectedTab = "test";
            mKeyboardView.setCurrentPage(selectedTab);
            TextView headerTitle = (TextView) findViewById(R.id.textView5);
            if (testClassifier == null) {
                headerTitle.setText("No model is yet created for you. Please go back to the \"Training\" tab and complete the experiment, at least once. " +
                        "After you are done with the training. Close the application and run it again to enable the \"Test\" tab.");
                findViewById(R.id.nextTestButton).setVisibility(View.GONE);
                findViewById(R.id.typedText).setVisibility(View.GONE);
                findViewById(R.id.shownText).setVisibility(View.GONE);
                findViewById(R.id.textView2).setVisibility(View.GONE);

            } else {
                headerTitle.setText("Time to test the performance:");
                findViewById(R.id.nextTestButton).setVisibility(View.VISIBLE);
            }
            findViewById(R.id.nextWordBtn).setVisibility(View.GONE);
            findViewById(R.id.tryCounter).setVisibility(View.GONE);
            findViewById(R.id.textView).setVisibility(View.GONE);
        }

    };

    /***
     * Mostra la tastiera a schermo con una animazione di slide dal basso
     */
    private void showKeyboardWithAnimation() {
        mKeyboardView.setPreviewEnabled(false);
        if (mKeyboardView.getVisibility() == View.GONE) {
            Animation animation = AnimationUtils
                    .loadAnimation(MainActivity.this,
                            R.anim.slide_in_buttom);
            mKeyboardView.setVisibility(View.VISIBLE);
            mKeyboardView.showWithAnimation(animation);
        }
    }

    /*
     * Begins to upload the file specified by the file path.
     */
    private void beginUpload(File file) {
        TransferObserver observer = transferUtility.upload(Constants.BUCKET_NAME, file.getName(),
                file);
        /*
         * Note that usually we set the transfer listener after initializing the
         * transfer. However it isn't required in this sample app. The flow is
         * click upload button -> start an activity for image selection
         * startActivityForResult -> onActivityResult -> beginUpload -> onResume
         * -> set listeners to in progress transfers.
         */
        // observer.setTransferListener(new UploadListener());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        savePreferences(COUNTER_KEY, counter);

    }

    private void savePreferences(String key, int value) {
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(key, value);
        editor.commit();
    }

    private void loadPreferences() {
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        counter = sharedPreferences.getInt(COUNTER_KEY, INIT_COUNTER);

    }

    public void sendToAmazon() {
        try {
            String filePath = getApplicationContext().getFilesDir() + "/information.txt";
            File theFile = new File(filePath);
            // Create an S3 client
            CognitoCachingCredentialsProvider sCredProvider = new CognitoCachingCredentialsProvider(
                    getApplicationContext(),
                    "us-east-1:4357f063-6383-41d6-b775-207ef68ed82e",
                    Regions.US_EAST_1);
            AmazonS3Client s3 = new AmazonS3Client(sCredProvider);

            // Set the region of your S3 bucket
            s3.setRegion(Region.getRegion(Regions.US_EAST_1));
            transferUtility = new TransferUtility(s3, getApplicationContext());
            transferRecordMaps.clear();
            // Use TransferUtility to get all upload transfers.
            observers = transferUtility.getTransfersWithType(TransferType.UPLOAD);
            TransferListener listener = new UploadListener();
            for (TransferObserver observer : observers) {

                // For each transfer we will will create an entry in
                // transferRecordMaps which will display
                // as a single row in the UI
                HashMap<String, Object> map = new HashMap<String, Object>();
                Util.fillMap(map, observer, false);
                transferRecordMaps.add(map);
                // Sets listeners to in progress transfers
                if (TransferState.WAITING.equals(observer.getState())
                        || TransferState.WAITING_FOR_NETWORK.equals(observer.getState())
                        || TransferState.IN_PROGRESS.equals(observer.getState())) {
                    observer.setTransferListener(listener);
                }
            }
            TransferObserver observer = transferUtility.upload(Constants.BUCKET_NAME, androidId + "-" + System.currentTimeMillis(),
                    theFile);
        } catch (Exception e) {
            System.out.println(e.getMessage());

        }
    }

    public void deleteTouchFile() {
        try {
            String filePath = getApplicationContext().getFilesDir() + "/information.txt";
            File theFile = new File(filePath);
            theFile.getCanonicalFile().delete();
        } catch (IOException e) {
        }
    }

    private void createDictionary() {
        dictionary = new ArrayList<String>();

        BufferedReader dict = null; //Holds the dictionary file
        AssetManager am = this.getAssets();

        try {
            //dictionary.txt should be in the assets folder.
            dict = new BufferedReader(new InputStreamReader(am.open("dictionary.txt")));

            String word;
            while ((word = dict.readLine()) != null) {
                if (word.length() > 6 && word.length() < 12) {
                    dictionary.add(word.toLowerCase());
                }
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        try {
            dict.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    //Precondition: the dictionary has been created.
    private String getRandomWord() {
        return dictionary.get((int) (Math.random() * dictionary.size()));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
//                TextView headerTitle = (TextView) findViewById(R.id.textView5);
//                headerTitle.setText("Time to test the performance:");
//                findViewById(R.id.nextTestButton).setVisibility(View.VISIBLE);
//                findViewById(R.id.nextWordBtn).setVisibility(View.GONE);
//                findViewById(R.id.tryCounter).setVisibility(View.GONE);
//                findViewById(R.id.textView).setVisibility(View.GONE);
                Toast toast = Toast.makeText(MainActivity.this, "Test Classifier Ready! Head to the \"test\" tab",
                        Toast.LENGTH_SHORT);
                toast.show();
            }
        };
        TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        setContentView(R.layout.activity_main);
        loadPreferences();
        createDictionary();
        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorMgr.registerListener(this,
                SensorManager.SENSOR_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_GAME);
        if (counter == INIT_COUNTER) {
            deleteTouchFile();
        }
        trainLayout = (RelativeLayout) findViewById(R.id.train_layout);
        setContentView(R.layout.activity_main);
        completeKeyboard = new Keyboard(this, R.xml.completekeyboard);
        numberKeyboard = new Keyboard(this, R.xml.keyboard);
        typedText = (EditText) findViewById(R.id.typedText);
        textToType = (TextView) findViewById(R.id.shownText);
        nextBtn = (Button) findViewById(R.id.nextWordBtn);
        nextTestBtn = (Button) findViewById(R.id.nextTestButton);
        wordNumberRandomGenerator = new Random();
        selectedKeyboard = completeKeyboard;
        textToType.setText(getRandomWord());
        typedText.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                showKeyboardWithAnimation();
                return true;
            }
        });
        nextTestBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    List<Boolean> results = mKeyboardView.getClassifyResults();
                    int resultSize = results.size();
                    int countTrue = 0;
                    int countFalse = 0;
                    boolean isSelf = false;
                    for (Boolean result : results) {
                        if(result){
                            countTrue++;
                        }else{
                            countFalse++;
                        }
                    }
                    if(results.size()==0){
                        Toast toast = Toast.makeText(MainActivity.this, "You didn't type anything!",
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    }else{
                        //                    if (countTrue >= Math.ceil(resultSize/2)+1
                        if (countFalse < 2){
                            isSelf = true;
                        }else{
                            isSelf = false;
                        }
                        Log.d("CLASSIFY", "CountTrue: " + countTrue+", CountFalse: " + countFalse);
                        if (isSelf){
                            Toast toast = Toast.makeText(MainActivity.this, "Yes! This was you!\n#True: "+countTrue+", #False: "+countFalse,
                                    Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        }else{
                            Toast toast = Toast.makeText(MainActivity.this, "What?! This was not you!\n#True: "+countTrue+", #False: "+countFalse,
                                    Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                        }
                        results = new ArrayList<Boolean>();
                        initializeAllInputs();
                        mKeyboardView.setClassifyResults(results);

                    }


                    return false;
                }
                return false;
            }
        });
        nextBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (typedText.getText().length() < 4) {
                        Toast toast = Toast.makeText(MainActivity.this, "Please make sure that you have typed in the text correctly then hit 'Next'.",
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                    } else if (nextBtn.getText().toString().equalsIgnoreCase("Start over!")) {
                        tryCounterView.setText(INIT_COUNTER + "");
                        nextBtn.setText("Next");
                        initializeAllInputs();
                        deleteTouchFile();
                    } else {
                        counter--;
                        tryCounterView.setText(counter + "");
                        if (counter > 0) {
                            initializeAllInputs();
                        } else {
                            counter = INIT_COUNTER;
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Touch Data Gathered")
                                    .setMessage("Thank you! Enough touch data gathered. We will send " +
                                            "that to a secure Amazon S3 server if only you confirm this " +
                                            "dialogue. Make sure that you are connected to a WIFI or your " +
                                            "data is on.")
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            sendToAmazon();
                                            initializeAllInputs();
                                            nextBtn.setText("Start over!");
                                        }
                                    })
                                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // do nothing
                                            nextBtn.setText("Start over!");
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                        }

                    }
                    return false;
                }
                return false;
            }
        });
        tryCounterView = (TextView) findViewById(R.id.tryCounter);
        tryCounterView.setText(counter + "");
        mKeyboardView = (CustomKeyboardView) findViewById(R.id.keyboard_view);
        mKeyboardView.setAndroidId(androidId);
        Context theContext = getBaseContext();
        mKeyboardView.setTheContext(theContext);
        assert mKeyboardView != null;
        mKeyboardView.setKeyboard(selectedKeyboard);
        mKeyboardView
                .setOnKeyboardActionListener(new BasicOnKeyboardActionListener(
                        this));
        mKeyboardView.setTouchMajorList(new ArrayList<Float>());
        mKeyboardView.setTouchMinorList(new ArrayList<Float>());
        mKeyboardView.setPressureList(new ArrayList<Float>());
        mKeyboardView.setSizeList(new ArrayList<Float>());
        mKeyboardView.setTimeReleased(0);
        mKeyboardView.setTimePressed(0);
        mKeyboardView.setFlyTimeStart(0);
        mKeyboardView.setFlyTimeEnd(0);
        mKeyboardView.setOrientation(getResources().getConfiguration().orientation);
        mKeyboardView.setWordOrNumber(1);
        mKeyboardView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float touchMajor = event.getTouchMajor();
                float touchMinor = event.getTouchMinor();
                float thePressure = event.getPressure();
                float theSize = event.getSize();
                mKeyboardView.getPressureList().add(thePressure);
                mKeyboardView.getSizeList().add(theSize);
                mKeyboardView.getTouchMajorList().add(touchMajor);
                mKeyboardView.getTouchMinorList().add(touchMinor);
                ActivityManager am = (ActivityManager) getBaseContext()
                        .getSystemService(Activity.ACTIVITY_SERVICE);

                return false;
            }
        });
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    testClassifier = loadModel(androidId);
                } catch (EOFException eof) {
                    testClassifier = null;
                    eof.printStackTrace();
                } catch (Exception e) {
                    testClassifier = null;
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Classifier loadModel(String androidId) throws Exception {
        File classifierFile = new File(getApplicationContext().getFilesDir()+"\\"+androidId+".model");
        AWSCredentials myCredentials = new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY);
        AmazonS3Client s3Client = new AmazonS3Client(myCredentials);
        S3Object modelObject = s3Client.getObject(MODELS_BUCKET, androidId + ".model");
        if (modelObject == null) {
            return null;
        }else{
            try {
                IOUtils.copy(modelObject.getObjectContent(), new FileOutputStream(classifierFile));
            } catch (Exception e) {
                return null;
            }
        }
        FileInputStream fis = new FileInputStream(classifierFile);
        testClassifier = (Classifier) weka.core.SerializationHelper.read(fis);
        if (testClassifier != null) {
            mKeyboardView.setClassifier(testClassifier);
        }
        Message message = mHandler.obtainMessage();
        message.sendToTarget();
        return testClassifier;
    }

    public void initializeAllInputs() {
        typedText.setText("");
        int randomNumber = wordNumberRandomGenerator.nextInt(100);
        if (randomNumber <= 60) {
            selectedKeyboard = completeKeyboard;
            textToType.setText(getRandomWord());
            mKeyboardView.setOrientation(getResources().getConfiguration().orientation);
            mKeyboardView.setWordOrNumber(1);
        } else {
            selectedKeyboard = numberKeyboard;
            textToType.setText(generateRandom(8));
            mKeyboardView.setWordOrNumber(2);
        }
        mKeyboardView.setOrientation(getResources().getConfiguration().orientation);
        mKeyboardView.setKeyboard(selectedKeyboard);
        mKeyboardView.setTimeReleased(0);
        mKeyboardView.setTimePressed(0);
        mKeyboardView.setElapsedFlyTime(0);
        mKeyboardView.setFlyTimeStart(0);
        mKeyboardView.setFlyTimeEnd(0);
        mKeyboardView.setTouchMajorList(new ArrayList<Float>());
        mKeyboardView.setTouchMinorList(new ArrayList<Float>());
        mKeyboardView.setPressureList(new ArrayList<Float>());
        mKeyboardView.setSizeList(new ArrayList<Float>());
    }

    public String generateRandom(int length) {
        Random random = new Random();
        char[] digits = new char[length];
        digits[0] = (char) (random.nextInt(9) + '1');
        for (int i = 1; i < length; i++) {
            digits[i] = (char) (random.nextInt(10) + '0');
        }
        return new String(digits);
    }

    @Override
    public void onSensorChanged(int sensor, float[] values) {
        if (sensor == SensorManager.SENSOR_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            // only allow one update every 100ms.
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float xValue = values[SensorManager.DATA_X];
                float yValue = values[SensorManager.DATA_Y];
                float zValue = values[SensorManager.DATA_Z];
                float speedVal = Math.abs(xValue + yValue + zValue - lastX - lastY - lastZ) / diffTime * 10000;
                if (speedVal != 0) {
                    mKeyboardView.setSpeed(Math.abs(xValue + yValue + zValue - lastX - lastY - lastZ) / diffTime * 10000);
                }

                lastX = xValue;
                lastY = yValue;
                lastZ = zValue;
            }
        }
    }

    @Override
    public void onAccuracyChanged(int sensor, int accuracy) {

    }

    /*
    * A TransferListener class that can listen to a upload task and be notified
    * when the status changes.
    */
    private class UploadListener implements TransferListener {

        private static final String TAG = "touch-info";

        // Simply updates the UI list when notified.
        @Override
        public void onError(int id, Exception e) {
            Log.e(TAG, "Error during upload: " + id, e);
            updateList();
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
            updateList();
        }

        @Override
        public void onStateChanged(int id, TransferState newState) {
            updateList();
        }
    }

    /*
     * Updates the ListView according to the observers.
     */
    private void updateList() {
        TransferObserver observer = null;
        HashMap<String, Object> map = null;
        for (int i = 0; i < observers.size(); i++) {
            observer = observers.get(i);
            map = transferRecordMaps.get(i);
            Util.fillMap(map, observer, i == checkedIndex);
        }
    }

}
