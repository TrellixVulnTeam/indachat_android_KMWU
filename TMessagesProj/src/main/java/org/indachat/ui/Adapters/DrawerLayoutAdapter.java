/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.indachat.ui.Adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.indachat.messenger.AndroidUtilities;
import org.indachat.messenger.LocaleController;
import org.indachat.messenger.MessagesController;
import org.indachat.messenger.R;
import org.indachat.messenger.UserConfig;
import org.indachat.messenger.support.widget.RecyclerView;
import org.indachat.tgnet.TLRPC;
import org.indachat.ui.ActionBar.Theme;
import org.indachat.ui.Cells.DrawerActionCell;
import org.indachat.ui.Cells.DividerCell;
import org.indachat.ui.Cells.DrawerAddCell;
import org.indachat.ui.Cells.DrawerUserCell;
import org.indachat.ui.Cells.EmptyCell;
import org.indachat.ui.Cells.DrawerProfileCell;
import org.indachat.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArrayList<Item> items = new ArrayList<>(11);
    private ArrayList<Integer> accountNumbers = new ArrayList<>();
    private boolean accountsShowed;
    private DrawerProfileCell profileCell;

    public DrawerLayoutAdapter(Context context) {
        mContext = context;
        accountsShowed = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShowed", true);
        Theme.createDialogsResources(context);
        resetItems();
    }

    private int getAccountRowsCount() {
        int count = accountNumbers.size() + 1;
        if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
            count++;
        }
        return count;
    }

    @Override
    public int getItemCount() {
        int count = items.size() + 2;
        if (accountsShowed) {
            count += getAccountRowsCount();
        }
        return count;
    }

    public void setAccountsShowed(boolean value, boolean animated) {
        if (accountsShowed == value) {
            return;
        }
        accountsShowed = value;
        if (profileCell != null) {
            profileCell.setAccountsShowed(accountsShowed);
        }
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShowed", accountsShowed).commit();
        if (animated) {
            if (accountsShowed) {
                notifyItemRangeInserted(2, getAccountRowsCount());
            } else {
                notifyItemRangeRemoved(2, getAccountRowsCount());
            }
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean isAccountsShowed() {
        return accountsShowed;
    }

    @Override
    public void notifyDataSetChanged() {
        resetItems();
        super.notifyDataSetChanged();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int itemType = holder.getItemViewType();
        return itemType == 3 || itemType == 4 || itemType == 5;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                profileCell = new DrawerProfileCell(mContext);
                profileCell.setOnArrowClickListener(v -> {
                    DrawerProfileCell drawerProfileCell = (DrawerProfileCell) v;
                    setAccountsShowed(drawerProfileCell.isAccountsShowed(), true);
                });
                view = profileCell;
                break;
            case 1:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
            case 2:
                view = new DividerCell(mContext);
                break;
            case 3:
                view = new DrawerActionCell(mContext);
                break;
            case 4:
                view = new DrawerUserCell(mContext);
                break;
            case 5:
                view = new DrawerAddCell(mContext);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                ((DrawerProfileCell) holder.itemView).setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShowed);
                holder.itemView.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
                break;
            }
            case 3: {
                position -= 2;
                if (accountsShowed) {
                    position -= getAccountRowsCount();
                }
                DrawerActionCell drawerActionCell = (DrawerActionCell) holder.itemView;
                Item item = items.get(position);
                if (item == null) {
                    //java.lang.NullPointerException: Attempt to invoke virtual method 'void org.indachat.ui.Adapters.DrawerLayoutAdapter$Item.bind(org.indachat.ui.Cells.DrawerActionCell)' on a null object reference
                    break;
                }
                item.bind(drawerActionCell);
                drawerActionCell.setPadding(0, 0, 0, 0);
                break;
            }
            case 4: {
                DrawerUserCell drawerUserCell = (DrawerUserCell) holder.itemView;
                drawerUserCell.setAccount(accountNumbers.get(position - 2));
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (i == 0) {
            return 0;
        } else if (i == 1) {
            return 1;
        }
        i -= 2;
        if (accountsShowed) {
            if (i < accountNumbers.size()) {
                return 4;
            } else {
                if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
                    if (i == accountNumbers.size()){
                        return 5;
                    } else if (i == accountNumbers.size() + 1) {
                        return 2;
                    }
                } else {
                    if (i == accountNumbers.size()) {
                        return 2;
                    }
                }
            }
            i -= getAccountRowsCount();
        }
        if (i == 3) {
            return 2;
        }
        return 3;
    }

    private void resetItems() {
        accountNumbers.clear();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accountNumbers.add(a);
            }
        }
        Collections.sort(accountNumbers, (o1, o2) -> {
            long l1 = UserConfig.getInstance(o1).loginTime;
            long l2 = UserConfig.getInstance(o2).loginTime;
            if (l1 > l2) {
                return 1;
            } else if (l1 < l2) {
                return -1;
            }
            return 0;
        });

        items.clear();
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }
        items.add(new Item(2, LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.menu_newgroup));
//#ifndef PATCH_BY_NEBULACHAT
//        items.add(new Item(3, LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.menu_secret));
//        items.add(new Item(4, LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.menu_broadcast));
        items.add(new Item(11, LocaleController.getString("SavedMessages", R.string.SavedMessages), R.drawable.menu_saved));
        items.add(new Item(5, LocaleController.getString("SettingsWall", R.string.SettingsWall), R.drawable.menu_saved));
        items.add(null); // divider
//#ifndef PATCH_BY_NEBULACHAT
        items.add(new Item(12, LocaleController.getString("Wallet", R.string.Wallet), R.drawable.proxy_on));
        items.add(new Item(6, LocaleController.getString("Contacts", R.string.Contacts), R.drawable.menu_contacts));
        items.add(new Item(7, LocaleController.getString("InviteFriends", R.string.InviteFriends), R.drawable.menu_invite));
        items.add(new Item(8, LocaleController.getString("Settings", R.string.Settings), R.drawable.menu_settings));
        items.add(new Item(9, LocaleController.getString("TelegramFAQ", R.string.TelegramFAQ), R.drawable.menu_help));
    }

    public int getId(int position) {
        position -= 2;
        if (accountsShowed) {
            position -= getAccountRowsCount();
        }
        if (position < 0 || position >= items.size()) {
            return -1;
        }
        Item item = items.get(position);
        return item != null ? item.id : -1;
    }

    private class Item {
        public int icon;
        public String text;
        public int id;

        public Item(int id, String text, int icon) {
            this.icon = icon;
            this.id = id;
            this.text = text;
        }

        public void bind(DrawerActionCell actionCell) {
            actionCell.setTextAndIcon(text, icon);
        }
    }
}
