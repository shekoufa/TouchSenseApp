package org.mun.navid.touchsense;/*
 * Copyright (C) 2011 - Riccardo Ciovati
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

import android.content.Context;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;

import org.mun.navid.touchsense.utils.ContextWrapperFix;
import org.mun.navid.touchsense.utils.FileUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

public class CustomKeyboardView extends KeyboardView {
    private ArrayList<Float> touchMajorList;
    private ArrayList<Float> touchMinorList;
    private ArrayList<Float> pressureList;
    private ArrayList<Float> sizeList;
    private long timePressed;
    private long timeReleased;
    private long flyTimeStart;
    private long flyTimeEnd;
    private long elapsedFlyTime;
    private int orientation;
    private int wordOrNumber;
    private Classifier classifier;
    private String androidId;
    private List<Boolean> classifyResults = new ArrayList<Boolean>();
    private String currentPage = "train";

    public String getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(String currentPage) {
        this.currentPage = currentPage;
    }

    public List<Boolean> getClassifyResults() {
        return classifyResults;
    }

    public void setClassifyResults(List<Boolean> classifyResults) {
        this.classifyResults = classifyResults;
    }

    public String getAndroidId() {
        return androidId;
    }

    public void setAndroidId(String androidId) {
        this.androidId = androidId;
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public void setClassifier(Classifier classifier) {
        this.classifier = classifier;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int getWordOrNumber() {
        return wordOrNumber;
    }

    public void setWordOrNumber(int wordOrNumber) {
        this.wordOrNumber = wordOrNumber;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    private float speed = 0;
    private Context theContext;

    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        int action = me.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            timePressed = System.currentTimeMillis();
            elapsedFlyTime = 0;
            if(flyTimeStart != 0){
                flyTimeEnd = System.currentTimeMillis();
                elapsedFlyTime = flyTimeEnd - flyTimeStart;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            timeReleased = System.currentTimeMillis();
            flyTimeStart = System.currentTimeMillis();
            long elapsedTime = timeReleased - timePressed;

            float pressureAverage = calcAverage(pressureList);
            float sizeAverage = calcAverage(sizeList);
            float touchMajorAverage = calcAverage(touchMajorList);
            float touchMinorAverage = calcAverage(touchMinorList);
            if (currentPage.equalsIgnoreCase("test")){
                testClassifier(pressureAverage, sizeAverage, touchMajorAverage, touchMinorAverage, elapsedTime, elapsedFlyTime, speed, orientation, wordOrNumber);
            }else{
                FileUtil.writeToFile(pressureAverage + "," + sizeAverage + "," + touchMajorAverage + "," + touchMinorAverage + "," + elapsedTime + "," + elapsedFlyTime
                    + "," + speed + "," + orientation + "," + wordOrNumber , theContext);
            }

            timeReleased = 0;
            timePressed = 0;
            touchMajorList = new ArrayList<Float>();
            touchMinorList = new ArrayList<Float>();
            pressureList = new ArrayList<Float>();
            sizeList = new ArrayList<Float>();
        }

        return super.dispatchTouchEvent(me);
    }

    private void testClassifier(float pressureAverage, float sizeAverage, float touchMajorAverage, float touchMinorAverage, long elapsedTime, long elapsedFlyTime, float speed, int orientation, int wordOrNumber) {
        Attribute pressureAttribute = new Attribute("pressure");
        Attribute sizeAttribute = new Attribute("size");
        Attribute touchmajorAttribute = new Attribute("touchmajor");
        Attribute touchminorAttribute = new Attribute("touchminor");
        Attribute durationAttribute = new Attribute("duration");
        Attribute flytimeAttribute = new Attribute("flytime");
        Attribute shakeAttribute = new Attribute("shake");
        Attribute orientationAttribute = new Attribute("orientation");
        Attribute typeAttribute = new Attribute("type");
        ArrayList<String> myClassValues = new ArrayList<String>(2);
        myClassValues.add(androidId);
        myClassValues.add("Others");

        // Create nominal attribute "classAttribute"

        Attribute classAttribute = new Attribute("class", myClassValues);
        // Create vector of the above attributes

        ArrayList<Attribute> attributes = new ArrayList<Attribute>(9);
        attributes.add(pressureAttribute);
        attributes.add(sizeAttribute);
        attributes.add(touchmajorAttribute);
        attributes.add(touchminorAttribute);
        attributes.add(durationAttribute);
        attributes.add(flytimeAttribute);
        attributes.add(shakeAttribute);
        attributes.add(orientationAttribute);
        attributes.add(typeAttribute);
        attributes.add(classAttribute);

        // Create the empty dataset "touch" with above attributes

        Instances touch = new Instances("touch", attributes, 0);

        // Make classAttribute the class attribute

        touch.setClassIndex(classAttribute.index());

        // Create empty instance with three attribute values

        Instance inst = new DenseInstance(10);

        // Set instance's values for the attributes "pressureAttribute", "sizeAttribute", and

        // "classAttribute"

        inst.setValue(pressureAttribute, pressureAverage);
        inst.setValue(sizeAttribute, sizeAverage);
        inst.setValue(touchmajorAttribute, touchMajorAverage);
        inst.setValue(touchminorAttribute, touchMinorAverage);
        inst.setValue(durationAttribute, elapsedTime);
        inst.setValue(flytimeAttribute, elapsedFlyTime);
        inst.setValue(shakeAttribute, speed);
        inst.setValue(orientationAttribute, orientation);
        inst.setValue(typeAttribute, wordOrNumber);
        inst.setValue(classAttribute, androidId);

        // Set instance's dataset to be the dataset "touch"

        inst.setDataset(touch);

        try {
            double pred = classifier.classifyInstance(inst);
            if(pred == 0){
                classifyResults.add(true);
            }else{
                classifyResults.add(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    public CustomKeyboardView(Context context, AttributeSet attrs) {
        super(new ContextWrapperFix(context, true), attrs);
    }

    public void showWithAnimation(Animation animation) {
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // TODO Auto-generated method stub

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                setVisibility(View.VISIBLE);
            }
        });
        setAnimation(animation);
    }
    private float calcAverage(ArrayList<Float> list) {
        float sum = 0;
        for (Float aFloat : list) {
            sum += aFloat;
        }
        return sum / list.size();
    }

    public ArrayList<Float> getTouchMajorList() {
        return touchMajorList;
    }

    public void setTouchMajorList(ArrayList<Float> touchMajorList) {
        this.touchMajorList = touchMajorList;
    }

    public ArrayList<Float> getTouchMinorList() {
        return touchMinorList;
    }

    public void setTouchMinorList(ArrayList<Float> touchMinorList) {
        this.touchMinorList = touchMinorList;
    }

    public ArrayList<Float> getPressureList() {
        return pressureList;
    }

    public void setPressureList(ArrayList<Float> pressureList) {
        this.pressureList = pressureList;
    }

    public ArrayList<Float> getSizeList() {
        return sizeList;
    }

    public void setSizeList(ArrayList<Float> sizeList) {
        this.sizeList = sizeList;
    }

    public long getTimePressed() {
        return timePressed;
    }

    public void setTimePressed(long timePressed) {
        this.timePressed = timePressed;
    }

    public long getTimeReleased() {
        return timeReleased;
    }

    public void setTimeReleased(long timeReleased) {
        this.timeReleased = timeReleased;
    }

    public long getFlyTimeStart() {
        return flyTimeStart;
    }

    public void setFlyTimeStart(long flyTimeStart) {
        this.flyTimeStart = flyTimeStart;
    }

    public long getFlyTimeEnd() {
        return flyTimeEnd;
    }

    public void setFlyTimeEnd(long flyTimeEnd) {
        this.flyTimeEnd = flyTimeEnd;
    }

    public long getElapsedFlyTime() {
        return elapsedFlyTime;
    }

    public void setElapsedFlyTime(long elapsedFlyTime) {
        this.elapsedFlyTime = elapsedFlyTime;
    }

    public Context getTheContext() {
        return theContext;
    }

    public void setTheContext(Context theContext) {
        this.theContext = theContext;
    }
}