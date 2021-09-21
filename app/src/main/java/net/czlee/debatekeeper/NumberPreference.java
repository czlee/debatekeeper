/*
 * Copyright (C) 2013 Chuan-Zheng Lee
 * Copyright (C) 2010-2012 Mike Novak <michael.novakjr@gmail.com>
 *
 * Licensed under the Apache Licence, Version 2.0 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * NOTE: THE GNU GENERAL PUBLIC LICENCE DOES NOT APPLY TO THIS FILE, BUT
 * IT STILL APPLIES TO THE REST OF THE DEBATEKEEPER APP (except where
 * noted). To this file, the Apache Licence applies instead.
 */

package net.czlee.debatekeeper;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

/**
 * NumberPreference implements a preference using a number picker.
 *
 * Based on what used to be com.michaelnovakjr.numberpicker.NumberPickerPreference.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-02-10
 */
public class NumberPreference extends DialogPreference {

    private static final String TAG = "NumberPreference";

    private NumberPicker mPicker;
    private Context mContext;

    private int minValue;
    private int maxValue;
    private int defaultValue;

    public NumberPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;

        if (attrs == null) return;

        TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.numberpicker);
        minValue = arr.getInteger(R.styleable.numberpicker_startRange, 0);
        maxValue = arr.getInteger(R.styleable.numberpicker_endRange, 200);
        defaultValue = arr.getInteger(R.styleable.numberpicker_defaultValue, 0);

        arr.recycle();

        setDialogLayoutResource(R.layout.number_preference);

    }

    public NumberPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    @Override
    protected void onBindDialogView(final View view) {
        super.onBindDialogView(view);

        mPicker = view.findViewById(R.id.numberPreference_picker);
        mPicker.setMinValue(minValue);
        mPicker.setMaxValue(maxValue);
        mPicker.setValue(getValue());

        // For some reason, the keyboard seems to show both (a) after the user presses the increment
        // or decrement buttons, even if he leaves the EditText alone, and (b) after pressing
        // "enter" in the EditText.  The purpose of this block of code is to counter-act that.
        // It does this by moving the focus to a button that is invisible to the user, and then
        // hiding the keyboard via InputMethodManager.
        //
        // It's really one big hack at the moment.  It kind of works most of the time, but sometimes
        // glitches.  If there's a better way, we should use it instead.  For now, it's the best
        // way I can find.  It's based on the answer marked "not recommended solution" here:
        // http://stackoverflow.com/questions/10996880/numberpicker-in-alertdialog-always-activates-keyboard-how-to-disable-this
        final InputMethodManager imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        mPicker.setOnValueChangedListener(new OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                View invisibleButton = view.findViewById(R.id.numberPreference_invisibleButton);
                boolean result = invisibleButton.requestFocusFromTouch();
                if (!result) Log.d(TAG, "Couldn't pull focus to invisible button");
                try {
                    View focus = getDialog().getWindow().getCurrentFocus();
                    if (focus != null)
                        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    else
                        Log.d(TAG, "Nothing had focus");
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // TODO Auto-generated method stub
        super.onDialogClosed(positiveResult);

        final int originalValue = getValue();
        final int currentValue  = mPicker.getValue();

        if (positiveResult && (currentValue != originalValue)) {
            if (callChangeListener(currentValue)) {
                persistInt(currentValue);
            }
        }
    }

    private int getValue() {
        return getSharedPreferences().getInt(getKey(), defaultValue);
    }

}
