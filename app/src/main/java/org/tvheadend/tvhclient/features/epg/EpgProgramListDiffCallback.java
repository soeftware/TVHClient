package org.tvheadend.tvhclient.features.epg;

import android.support.annotation.Nullable;
import android.support.v7.util.DiffUtil;

import org.tvheadend.tvhclient.data.entity.EpgProgram;

import java.util.List;

public class EpgProgramListDiffCallback extends DiffUtil.Callback {
    private final List<EpgProgram> oldList;
    private final List<EpgProgram> newList;

    public EpgProgramListDiffCallback(List<EpgProgram> oldList, List<EpgProgram> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList != null ? oldList.size() : 0;
    }

    @Override
    public int getNewListSize() {
        return newList != null ? newList.size() : 0;
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return newList.get(newItemPosition).getEventId() == oldList.get(oldItemPosition).getEventId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        return newList.get(newItemPosition).equals(oldList.get(oldItemPosition));
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldItemPosition, int newItemPosition) {
        // One can return particular field for changed item.
        return super.getChangePayload(oldItemPosition, newItemPosition);
    }
}
