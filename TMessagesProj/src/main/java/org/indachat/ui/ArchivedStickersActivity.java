/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.indachat.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import org.indachat.messenger.AndroidUtilities;
import org.indachat.messenger.DataQuery;
import org.indachat.messenger.LocaleController;
import org.indachat.messenger.NotificationCenter;
import org.indachat.messenger.R;
import org.indachat.messenger.support.widget.LinearLayoutManager;
import org.indachat.messenger.support.widget.RecyclerView;
import org.indachat.tgnet.ConnectionsManager;
import org.indachat.tgnet.RequestDelegate;
import org.indachat.tgnet.TLObject;
import org.indachat.tgnet.TLRPC;
import org.indachat.ui.ActionBar.ActionBar;
import org.indachat.ui.ActionBar.BaseFragment;
import org.indachat.ui.ActionBar.Theme;
import org.indachat.ui.ActionBar.ThemeDescription;
import org.indachat.ui.Cells.ArchivedStickerSetCell;
import org.indachat.ui.Cells.LoadingCell;
import org.indachat.ui.Cells.TextInfoPrivacyCell;
import org.indachat.ui.Components.EmptyTextProgressView;
import org.indachat.ui.Components.LayoutHelper;
import org.indachat.ui.Components.RecyclerListView;
import org.indachat.ui.Components.StickersAlert;

import java.util.ArrayList;

public class ArchivedStickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private EmptyTextProgressView emptyView;
    private LinearLayoutManager layoutManager;
    private RecyclerListView listView;

    private ArrayList<TLRPC.StickerSetCovered> sets = new ArrayList<>();
    private boolean firstLoaded;
    private boolean endReached;

    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersLoadingRow;
    private int stickersShadowRow;
    private int rowCount;

    private int currentType;

    private boolean loadingStickers;

    public ArchivedStickersActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getStickers();
        updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needReloadArchivedStickers);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needReloadArchivedStickers);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == DataQuery.TYPE_IMAGE) {
            actionBar.setTitle(LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers));
        } else {
            actionBar.setTitle(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyView = new EmptyTextProgressView(context);
        if (currentType == DataQuery.TYPE_IMAGE) {
            emptyView.setText(LocaleController.getString("ArchivedStickersEmpty", R.string.ArchivedStickersEmpty));
        } else {
            emptyView.setText(LocaleController.getString("ArchivedMasksEmpty", R.string.ArchivedMasksEmpty));
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (loadingStickers) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(final View view, int position) {
                if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                    final TLRPC.StickerSetCovered stickerSet = sets.get(position);
                    TLRPC.InputStickerSet inputStickerSet;
                    if (stickerSet.set.id != 0) {
                        inputStickerSet = new TLRPC.TL_inputStickerSetID();
                        inputStickerSet.id = stickerSet.set.id;
                    } else {
                        inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
                        inputStickerSet.short_name = stickerSet.set.short_name;
                    }
                    inputStickerSet.access_hash = stickerSet.set.access_hash;
                    StickersAlert stickersAlert = new StickersAlert(getParentActivity(), ArchivedStickersActivity.this, inputStickerSet, null, null);
                    stickersAlert.setInstallDelegate(new StickersAlert.StickersAlertInstallDelegate() {
                        @Override
                        public void onStickerSetInstalled() {
                            ArchivedStickerSetCell cell = (ArchivedStickerSetCell) view;
                            cell.setChecked(true);
                        }

                        @Override
                        public void onStickerSetUninstalled() {
                            ArchivedStickerSetCell cell = (ArchivedStickerSetCell) view;
                            cell.setChecked(false);
                        }
                    });
                    showDialog(stickersAlert);
                }
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!loadingStickers && !endReached && layoutManager.findLastVisibleItemPosition() > stickersLoadingRow - 2) {
                    getStickers();
                }
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        if (!sets.isEmpty()) {
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + sets.size();
            rowCount += sets.size();
            if (!endReached) {
                stickersLoadingRow = rowCount++;
                stickersShadowRow = -1;
            } else {
                stickersShadowRow = rowCount++;
                stickersLoadingRow = -1;
            }
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersLoadingRow = -1;
            stickersShadowRow = -1;
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void getStickers() {
        if (loadingStickers || endReached) {
            return;
        }
        loadingStickers = true;
        if (emptyView != null && !firstLoaded) {
            emptyView.showProgress();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
        req.offset_id = sets.isEmpty() ? 0 : sets.get(sets.size() - 1).set.id;
        req.limit = 15;
        req.masks = currentType == DataQuery.TYPE_MASK;
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            TLRPC.TL_messages_archivedStickers res = (TLRPC.TL_messages_archivedStickers) response;
                            sets.addAll(res.sets);
                            endReached = res.sets.size() != 15;
                            loadingStickers = false;
                            firstLoaded = true;
                            if (emptyView != null) {
                                emptyView.showTextView();
                            }
                            updateRows();
                        }
                    }
                });
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.needReloadArchivedStickers) {
            firstLoaded = false;
            endReached = false;
            sets.clear();
            updateRows();
            if (emptyView != null) {
                emptyView.showProgress();
            }
            getStickers();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 0) {
                ArchivedStickerSetCell cell = (ArchivedStickerSetCell) holder.itemView;
                cell.setTag(position);
                TLRPC.StickerSetCovered stickerSet = sets.get(position);
                cell.setStickersSet(stickerSet, position != sets.size() - 1);
                cell.setChecked(DataQuery.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id));
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ArchivedStickerSetCell(mContext, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ArchivedStickerSetCell) view).setOnCheckClick(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            ArchivedStickerSetCell cell = (ArchivedStickerSetCell) buttonView.getParent();
                            int num = (Integer) cell.getTag();
                            if (num >= sets.size()) {
                                return;
                            }
                            TLRPC.StickerSetCovered stickerSet = sets.get(num);
                            DataQuery.getInstance(currentAccount).removeStickersSet(getParentActivity(), stickerSet.set, !isChecked ? 1 : 2, ArchivedStickersActivity.this, false);
                        }
                    });
                    break;
                case 1:
                    view = new LoadingCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == stickersLoadingRow) {
                return 1;
            } else if (i == stickersShadowRow) {
                return 2;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ArchivedStickerSetCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),
                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LoadingCell.class, TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked)
        };
    }
}
