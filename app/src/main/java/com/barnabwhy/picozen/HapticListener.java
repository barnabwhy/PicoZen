package com.barnabwhy.picozen;

import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

class HapticListener implements View.OnGenericMotionListener {
    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        if(event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER) {
            v.setHapticFeedbackEnabled(true);
            Log.i("Hover enter", "Enabled: " + v.isHapticFeedbackEnabled() + " | Success: " + v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING));
        }
        return false;
    }
}

