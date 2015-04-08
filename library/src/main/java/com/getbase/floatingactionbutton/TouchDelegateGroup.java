package com.getbase.floatingactionbutton;

import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TouchDelegateGroup extends TouchDelegate {
    private static final Rect USELESS_HACKY_RECT = new Rect();
    private final Map<View, TouchDelegate> mTouchDelegates = new HashMap<View, TouchDelegate>();
    private TouchDelegate mCurrentTouchDelegate;
    private boolean mEnabled;

    public TouchDelegateGroup(View uselessHackyView) {
        super(USELESS_HACKY_RECT, uselessHackyView);
    }

    public void addTouchDelegate(View delegateView, @NonNull TouchDelegate touchDelegate) {
        mTouchDelegates.put(delegateView, touchDelegate);
    }

    public void removeTouchDelegate(TouchDelegate touchDelegate) {
        mTouchDelegates.remove(touchDelegate);
        if (mCurrentTouchDelegate == touchDelegate) {
            mCurrentTouchDelegate = null;
        }
    }

    public void clearTouchDelegates() {
        mTouchDelegates.clear();
        mCurrentTouchDelegate = null;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (mEnabled) {
            TouchDelegate delegate = null;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    for (TouchDelegate item : mTouchDelegates.values()) {
                        if (item.onTouchEvent(event)) {
                            mCurrentTouchDelegate = item;
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    delegate = mCurrentTouchDelegate;
                    break;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    delegate = mCurrentTouchDelegate;
                    mCurrentTouchDelegate = null;
                    break;
            }
            return delegate != null && delegate.onTouchEvent(event);
        } else {
            return false;
        }
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void removeAllTouchDelegates() {
        mTouchDelegates.clear();
        mCurrentTouchDelegate = null;
    }
}