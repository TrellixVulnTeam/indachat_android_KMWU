/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.indachat.ui.Adapters;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import org.indachat.messenger.AndroidUtilities;
import org.indachat.messenger.LocaleController;
import org.indachat.messenger.UserConfig;
import org.indachat.messenger.support.widget.RecyclerView;
import org.indachat.tgnet.TLRPC;
import org.indachat.messenger.ContactsController;
import org.indachat.messenger.MessagesController;
import org.indachat.messenger.R;
import org.indachat.ui.Cells.DividerCell;
import org.indachat.ui.Cells.GraySectionCell;
import org.indachat.ui.Cells.LetterSectionCell;
import org.indachat.ui.Cells.TextCell;
import org.indachat.ui.Cells.UserCell;
import org.indachat.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

public class ContactsAdapter extends RecyclerListView.SectionsAdapter {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private int onlyUsers;
    private boolean needPhonebook;
    private SparseArray<TLRPC.User> ignoreUsers;
    private SparseArray<?> checkedMap;
    private boolean scrolling;
    private boolean isAdmin;

    public ContactsAdapter(Context context, int onlyUsersType, boolean arg2, SparseArray<TLRPC.User> arg3, boolean arg4) {
        mContext = context;
        onlyUsers = onlyUsersType;
        needPhonebook = arg2;
        ignoreUsers = arg3;
        isAdmin = arg4;
    }

    public void setCheckedMap(SparseArray<?> map) {
        checkedMap = map;
    }

    public void setIsScrolling(boolean value) {
        scrolling = value;
    }

    public Object getItem(int section, int position) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            if (section < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
                if (position < arr.size()) {
                    return MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                }
            }
            return null;
        } else {
            if (section == 0) {
                return null;
            } else {
                if (section - 1 < sortedUsersSectionsArray.size()) {
                    ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                    if (position < arr.size()) {
                        return MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                    }
                    return null;
                }
            }
        }
        if (needPhonebook) {
            return ContactsController.getInstance(currentAccount).phoneBookContacts.get(position);
        }
        return null;
    }

    @Override
    public boolean isEnabled(int section, int row) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return row < arr.size();
        } else {
            if (section == 0) {
                if (needPhonebook || isAdmin) {
                    return row != 1;
                } else {
                    return row != 3;
                }
            } else if (section - 1 < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                return row < arr.size();
            }
        }
        return true;
    }

    @Override
    public int getSectionCount() {
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        int count = sortedUsersSectionsArray.size();
        if (onlyUsers == 0) {
            count++;
        }
        if (isAdmin) {
            count++;
        }
        if (needPhonebook) {
            count++;
        }
        return count;
    }

    @Override
    public int getCountForSection(int section) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            if (section < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
                int count = arr.size();
                if (section != (sortedUsersSectionsArray.size() - 1) || needPhonebook) {
                    count++;
                }
                return count;
            }
        } else {
            if (section == 0) {
                if (needPhonebook || isAdmin) {
                    return 2;
                } else {
                    return 4;
                }
            } else if (section - 1 < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                int count = arr.size();
                if (section - 1 != (sortedUsersSectionsArray.size() - 1) || needPhonebook) {
                    count++;
                }
                return count;
            }
        }
        if (needPhonebook) {
            return ContactsController.getInstance(currentAccount).phoneBookContacts.size();
        }
        return 0;
    }

    @Override
    public View getSectionHeaderView(int section, View view) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (view == null) {
            view = new LetterSectionCell(mContext);
        }
        LetterSectionCell cell = (LetterSectionCell) view;
        if (onlyUsers != 0 && !isAdmin) {
            if (section < sortedUsersSectionsArray.size()) {
                cell.setLetter(sortedUsersSectionsArray.get(section));
            } else {
                cell.setLetter("");
            }
        } else {
            if (section == 0) {
                cell.setLetter("");
            } else if (section - 1 < sortedUsersSectionsArray.size()) {
                cell.setLetter(sortedUsersSectionsArray.get(section - 1));
            } else {
                cell.setLetter("");
            }
        }
        return view;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new UserCell(mContext, 58, 1, false);
                break;
            case 1:
                view = new TextCell(mContext);
                break;
            case 2:
                view = new GraySectionCell(mContext);
                ((GraySectionCell) view).setText(LocaleController.getString("Contacts", R.string.Contacts).toUpperCase());
                break;
            case 3:
            default:
                view = new DividerCell(mContext);
                view.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 28 : 72), 0, AndroidUtilities.dp(LocaleController.isRTL ? 72 : 28), 0);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
        switch (holder.getItemViewType()) {
            case 0:
                UserCell userCell = (UserCell) holder.itemView;
                HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
                ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - (onlyUsers != 0 && !isAdmin ? 0 : 1)));
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                userCell.setData(user, null, null, 0);
                if (checkedMap != null) {
                    userCell.setChecked(checkedMap.indexOfKey(user.id) >= 0, !scrolling);
                }
                if (ignoreUsers != null) {
                    if (ignoreUsers.indexOfKey(user.id) >= 0) {
                        userCell.setAlpha(0.5f);
                    } else {
                        userCell.setAlpha(1.0f);
                    }
                }
                break;
            case 1:
                TextCell textCell = (TextCell) holder.itemView;
                if (section == 0) {
                    if (needPhonebook) {
                        textCell.setTextAndIcon(LocaleController.getString("InviteFriends", R.string.InviteFriends), R.drawable.menu_invite);
                    } else if (isAdmin) {
                        textCell.setTextAndIcon(LocaleController.getString("InviteToGroupByLink", R.string.InviteToGroupByLink), R.drawable.menu_invite);
                    } else {
                        if (position == 0) {
                            textCell.setTextAndIcon(LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.menu_newgroup);
                        } else if (position == 1) {
                            textCell.setTextAndIcon(LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.menu_secret);
                        } else if (position == 2) {
                            textCell.setTextAndIcon(LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.menu_broadcast);
                        }
                    }
                } else {
                    ContactsController.Contact contact = ContactsController.getInstance(currentAccount).phoneBookContacts.get(position);
                    if (contact.first_name != null && contact.last_name != null) {
                        textCell.setText(contact.first_name + " " + contact.last_name);
                    } else if (contact.first_name != null && contact.last_name == null) {
                        textCell.setText(contact.first_name);
                    } else {
                        textCell.setText(contact.last_name);
                    }
                }
                break;
        }
    }

    @Override
    public int getItemViewType(int section, int position) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        if (onlyUsers != 0 && !isAdmin) {
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return position < arr.size() ? 0 : 3;
        } else {
            if (section == 0) {
                if ((needPhonebook || isAdmin) && position == 1 || position == 3) {
                    return 2;
                }
            } else if (section - 1 < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                return position < arr.size() ? 0 : 3;
            }
        }
        return 1;
    }

    @Override
    public String getLetter(int position) {
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        int section = getSectionForPosition(position);
        if (section == -1) {
            section = sortedUsersSectionsArray.size() - 1;
        }
        if (section > 0 && section <= sortedUsersSectionsArray.size()) {
            return sortedUsersSectionsArray.get(section - 1);
        }
        return null;
    }

    @Override
    public int getPositionForScrollProgress(float progress) {
        return (int) (getItemCount() * progress);
    }
}
