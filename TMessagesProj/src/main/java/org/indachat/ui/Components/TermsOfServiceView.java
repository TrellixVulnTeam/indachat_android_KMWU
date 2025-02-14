package org.indachat.ui.Components;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.indachat.messenger.AndroidUtilities;
import org.indachat.messenger.FileLog;
import org.indachat.messenger.LocaleController;
import org.indachat.messenger.MessageObject;
import org.indachat.messenger.MessagesController;
import org.indachat.messenger.R;
import org.indachat.tgnet.ConnectionsManager;
import org.indachat.tgnet.RequestDelegate;
import org.indachat.tgnet.TLObject;
import org.indachat.tgnet.TLRPC;
import org.indachat.ui.ActionBar.AlertDialog;
import org.indachat.ui.ActionBar.Theme;

public class TermsOfServiceView extends FrameLayout {

    private TextView textView;
    private TermsOfServiceViewDelegate delegate;
    private TLRPC.TL_help_termsOfService currentTos;
    private int currentAccount;

    public interface TermsOfServiceViewDelegate {
        void onAcceptTerms(int account);
        void onDeclineTerms(int account);
    }

    public TermsOfServiceView(final Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int top = Build.VERSION.SDK_INT >= 21 ? (int) (AndroidUtilities.statusBarHeight / AndroidUtilities.density) : 0;
        if (Build.VERSION.SDK_INT >= 21) {
            View view = new View(context);
            view.setBackgroundColor(0xff000000);
            addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.statusBarHeight));
        }

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.logo_middle);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 30 + top, 0, 0));

        TextView titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        titleTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("PrivacyPolicyAndTerms", R.string.PrivacyPolicyAndTerms));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 27, 126 + top, 27, 75));

        ScrollView scrollView = new ScrollView(context);
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, Theme.getColor(Theme.key_actionBarDefault));
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 27, 160 + top, 27, 75));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        scrollView.addView(textView, new ScrollView.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView declineTextView = new TextView(context);
        declineTextView.setText(LocaleController.getString("Decline", R.string.Decline).toUpperCase());
        declineTextView.setGravity(Gravity.CENTER);
        declineTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        declineTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        declineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        declineTextView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        addView(declineTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 16, 16));
        declineTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                builder.setTitle(LocaleController.getString("TermsOfService", R.string.TermsOfService));
                builder.setPositiveButton(LocaleController.getString("DeclineDeactivate", R.string.DeclineDeactivate), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setMessage(LocaleController.getString("TosDeclineDeleteAccount", R.string.TosDeclineDeleteAccount));
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setPositiveButton(LocaleController.getString("Deactivate", R.string.Deactivate), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                final AlertDialog progressDialog = new AlertDialog(getContext(), 1);
                                progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                                progressDialog.setCanceledOnTouchOutside(false);
                                progressDialog.setCancelable(false);

                                TLRPC.TL_account_deleteAccount req = new TLRPC.TL_account_deleteAccount();
                                req.reason = "Decline ToS update";
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(final TLObject response, final TLRPC.TL_error error) {
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    progressDialog.dismiss();
                                                } catch (Exception e) {
                                                    FileLog.e(e);
                                                }
                                                if (response instanceof TLRPC.TL_boolTrue) {
                                                    MessagesController.getInstance(currentAccount).performLogout(0);
                                                } else {
                                                    String errorText = LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred);
                                                    if (error != null) {
                                                        errorText += "\n" + error.text;
                                                    }
                                                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                                    builder.setMessage(errorText);
                                                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                                    builder.show();
                                                }
                                            }
                                        });
                                    }
                                });
                                progressDialog.show();
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.show();
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Back", R.string.Back), null);
                builder.setMessage(LocaleController.getString("TosUpdateDecline", R.string.TosUpdateDecline));
                builder.show();
            }
        });

        TextView acceptTextView = new TextView(context);
        acceptTextView.setText(LocaleController.getString("Accept", R.string.Accept).toUpperCase());
        acceptTextView.setGravity(Gravity.CENTER);
        acceptTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        acceptTextView.setTextColor(0xffffffff);
        acceptTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        acceptTextView.setBackgroundResource(R.drawable.regbtn_states);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(acceptTextView, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(acceptTextView, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            acceptTextView.setStateListAnimator(animator);
        }
        acceptTextView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        addView(acceptTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 16, 0, 16, 16));
        acceptTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentTos.min_age_confirm != 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
                    builder.setTitle(LocaleController.getString("TosAgeTitle", R.string.TosAgeTitle));
                    builder.setPositiveButton(LocaleController.getString("Agree", R.string.Agree), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            accept();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setMessage(LocaleController.formatString("TosAgeText", R.string.TosAgeText, LocaleController.formatPluralString("Years", currentTos.min_age_confirm)));
                    builder.show();
                } else {
                    accept();
                }
            }
        });
    }

    private void accept() {
        delegate.onAcceptTerms(currentAccount);
        TLRPC.TL_help_acceptTermsOfService req = new TLRPC.TL_help_acceptTermsOfService();
        req.id = currentTos.id;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void show(int account, TLRPC.TL_help_termsOfService tos) {
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(tos.text);
        MessageObject.addEntitiesToText(builder, tos.entities, false, 0, false, false, false);
        textView.setText(builder);
        currentTos = tos;
        currentAccount = account;
    }

    public void setDelegate(TermsOfServiceViewDelegate termsOfServiceViewDelegate) {
        delegate = termsOfServiceViewDelegate;
    }
}
