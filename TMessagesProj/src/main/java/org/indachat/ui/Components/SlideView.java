/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.indachat.ui.Components;

import android.content.Context;
import android.os.Bundle;
import android.widget.LinearLayout;

public class SlideView extends LinearLayout {

    public SlideView(Context context) {
        super(context);
    }

    public String getHeaderName() {
        return "";
    }

    public void setParams(Bundle params, boolean restore) {

    }

    public void onBackPressed() {

    }

    public void onShow() {

    }

    public void onDestroyActivity() {

    }

    public void onNextPressed() {

    }

    public void onCancelPressed() {

    }

    public void saveStateParams(Bundle bundle) {

    }

    public void restoreStateParams(Bundle bundle) {

    }

    public boolean needBackButton() {
        return false;
    }
}
