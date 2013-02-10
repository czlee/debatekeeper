/*
 * Copyright (C) 2013 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import com.michaelnovakjr.numberpicker.R;

/**
 * NumberPreference implements a preference using a number picker.
 *
 * This class is mainly based on {@link com.michaelnovakjr.numberpicker.NumberPickerPreference}.
 * The only difference is that it uses the stock Android {@link NumberPicker} instead of
 * {@link com.michaelnovakjr.numberpicker.NumberPicker}.  Therefore, it can only be used in SDKs 11 and higher.
 *
 * @author Chuan-Zheng Lee
 * @since  2013-02-10
 */
@TargetApi(11)
public class NumberPreference extends DialogPreference {

    private NumberPicker mPicker;

    private int minValue;
    private int maxValue;
    private int defaultValue;

    public NumberPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

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
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mPicker = (NumberPicker) view.findViewById(R.id.numberPreference_picker);
        mPicker.setMinValue(minValue);
        mPicker.setMaxValue(maxValue);
        mPicker.setValue(getValue());

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
