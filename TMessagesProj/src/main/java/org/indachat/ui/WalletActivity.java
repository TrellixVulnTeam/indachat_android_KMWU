/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Web3 Space, 2019.
 */

package org.indachat.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.indachat.messenger.LocaleController;
import org.indachat.messenger.NotificationCenter;
import org.indachat.messenger.R;
import org.indachat.tgnet.TLRPC;
import org.indachat.ui.ActionBar.ActionBar;
import org.indachat.ui.ActionBar.ActionBarMenu;
import org.indachat.ui.ActionBar.BaseFragment;
import org.indachat.ui.ActionBar.Theme;
import org.indachat.ui.ActionBar.ThemeDescription;
import org.indachat.ui.Components.LayoutHelper;

import android.webkit.WebChromeClient;

import org.indachat.jsbridge.BridgeWebView;
import org.indachat.jsbridge.CallBackFunction;
import org.indachat.jsbridge.DefaultHandler;
import org.json.JSONException;

import android.graphics.Rect;


public class WalletActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {


    private BridgeWebView webView;

    public static WalletActivity instance;

    private void loadWallet(Context context) {
        webView = new BridgeWebView(context);

        webView.setDefaultHandler(new DefaultHandler());
        webView.setWebChromeClient(new WebChromeClient() {});
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            webView.getSettings().setDatabasePath("/data/data/" + webView.getContext().getPackageName() + "/databases/");
        }


        /*
        webView.setWebViewClient(new WebViewClient(){
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view.getContext().startActivity(
                            new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    return true;
                } else {
                    return false;
                }
            }
        });
        */
        webView.loadUrl("file:///android_asset/wallet.html");
    }

    public WalletActivity(Bundle args, Context context) {
        super(args);

        loadWallet(context);
        instance = this;


    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        //delegate = null;
    }

    @Override
    public View createView(Context context) {


        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Wallet", R.string.Wallet));



        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    //refresh
                    //presentFragment(new NewContactActivity());
                    refreshPressed();
                } else if (id == 2) {
                    //lock
                    lockPressed();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.ic_refresh);
        menu.addItem(2, R.drawable.ic_lock_white);


        fragmentView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

            }
        };
        FrameLayout frameLayout = (FrameLayout) fragmentView;


        //loadWallet(context);
        frameLayout.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));


        //initKeyBoardListener();

        return fragmentView;
    }

    private Boolean inactiveAPI() {
        if (webView == null) return true;
        return webView.getStartupMessage() != null;
    }


    public void refreshPressed() {
            if (inactiveAPI()) return;
            webView.callHandler("walletRPC", "{ \"method\": \"refresh\", \"args\": [] }", data -> {

            });
    }

    public void lockPressed() {
        if (inactiveAPI()) return;
        webView.callHandler("walletRPC", "{ \"method\": \"lock\", \"args\": [] }", data -> {

        });
    }

    private void walletIsNotCreated(CallBackFunction callback) {
        callback.onCallBack("Wallet's API is not accessible");

    }

    public void chooseNetwork(String net, CallBackFunction callback)  {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }

        webView.callHandler("walletRPC", "{ \"method\": \"use\", \"args\": [\"" + net + "\"] }", callback);
    }



    public void setTheme(String theme, CallBackFunction callback) {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }
        webView.callHandler("walletRPC", "{ \"method\": \"setTheme\", \"args\": [\"" + theme + "\"] }", callback);
    }


    public void getBalance(String token, CallBackFunction callback)  {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }
        webView.callHandler("walletRPC", "{ \"method\": \"getBalance\", \"token\":\"" + token + "\" , \"args\": [] }", callback);
    }

    public void getAddress(String token, CallBackFunction callback)  {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }
        webView.callHandler("walletRPC", "{ \"method\": \"getAddress\", \"token\":\"" + token + "\" , \"args\": [] }", callback);
    }


    public void getSupportedTokens(CallBackFunction callback)  {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }
        webView.callHandler("walletRPC", "{ \"method\": \"getSupportedTokens\", \"args\": [] }", callback);
    }

    public void sendTransaction(String token, String to, String amount, CallBackFunction callback)  {
        if (inactiveAPI()) { walletIsNotCreated(callback); return; }
        //webView.
        webView.callHandler("walletRPC", "{ \"method\": \"sendTransaction\", \"token\":\"" + token + "\" , \"args\": [\"" + to + "\",\"" + amount + "\"] }", callback);
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);

    }



    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }


    private void initKeyBoardListener() {
        // Минимальное значение клавиатуры.
        // Threshold for minimal keyboard height.
        final int MIN_KEYBOARD_HEIGHT_PX = 150;
        // Окно верхнего уровня view.
        // Top-level window decor view.
        final View decorView = this.webView;
        // Регистрируем глобальный слушатель. Register global layout listener.
        decorView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            // Видимый прямоугольник внутри окна.
            // Retrieve visible rectangle inside window.
            private final Rect windowVisibleDisplayFrame = new Rect();
            private int lastVisibleDecorViewHeight;

            @Override
            public void onGlobalLayout() {
                decorView.getWindowVisibleDisplayFrame(windowVisibleDisplayFrame);
                final int visibleDecorViewHeight = windowVisibleDisplayFrame.height();

                if (lastVisibleDecorViewHeight != 0) {
                    if (lastVisibleDecorViewHeight > visibleDecorViewHeight + MIN_KEYBOARD_HEIGHT_PX) {
                        Log.d("Pasha", "SHOW");
                    } else if (lastVisibleDecorViewHeight + MIN_KEYBOARD_HEIGHT_PX < visibleDecorViewHeight) {
                        Log.d("Pasha", "HIDE");
                    }
                }
                // Сохраняем текущую высоту view до следующего вызова.
                // Save current decor view height for the next call.
                lastVisibleDecorViewHeight = visibleDecorViewHeight;
            }
        });
    }


    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
        };

        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),



                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                };
    }
}
