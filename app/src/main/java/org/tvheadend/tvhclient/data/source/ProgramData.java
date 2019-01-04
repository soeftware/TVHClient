package org.tvheadend.tvhclient.data.source;

import android.arch.lifecycle.LiveData;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import org.tvheadend.tvhclient.data.db.AppRoomDatabase;
import org.tvheadend.tvhclient.data.entity.EpgProgram;
import org.tvheadend.tvhclient.data.entity.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

public class ProgramData implements DataSourceInterface<Program> {

    private static final int LOAD_LAST_IN_CHANNEL = 1;
    private static final int LOAD_BY_ID = 2;

    private final AppRoomDatabase db;

    @Inject
    public ProgramData(AppRoomDatabase database) {
        this.db = database;
    }

    @Override
    public void addItem(Program item) {
        db.getProgramDao().insert(item);
    }

    public void addItems(@NonNull List<Program> items) {
        db.getProgramDao().insert(items);
    }

    @Override
    public void updateItem(Program item) {
        db.getProgramDao().update(item);
    }

    @Override
    public void removeItem(Program item) {
        db.getProgramDao().delete(item);
    }

    @Override
    public LiveData<Integer> getLiveDataItemCount() {
        return db.getProgramDao().getProgramCount();
    }

    @Override
    public LiveData<List<Program>> getLiveDataItems() {
        return db.getProgramDao().loadPrograms();
    }

    @Override
    public LiveData<Program> getLiveDataItemById(Object id) {
        return db.getProgramDao().loadProgramById((int) id);
    }

    @Override
    public Program getItemById(Object id) {
        try {
            return new ItemLoaderTask(db, (int) id, LOAD_BY_ID).execute().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    @NonNull
    public List<Program> getItems() {
        return new ArrayList<>();
    }

    public LiveData<List<Program>> getLiveDataItemsFromTime(long time) {
        return db.getProgramDao().loadProgramsFromTime(time);
    }

    public LiveData<List<Program>> getLiveDataItemByChannelIdAndTime(int channelId, long time) {
        return db.getProgramDao().loadProgramsFromChannelFromTime(channelId, time);
    }

    public List<EpgProgram> getItemByChannelIdAndBetweenTime(int channelId, long startTime, long endTime) {
        List<EpgProgram> programs = new ArrayList<>();
        try {
            programs.addAll(new ItemsLoaderTask(db, channelId, startTime, endTime).execute().get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return programs;
    }

    public Program getLastItemByChannelId(int channelId) {
        try {
            return new ItemLoaderTask(db, channelId, LOAD_LAST_IN_CHANNEL).execute().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void removeItemsByTime(long time) {
        new Thread(() -> db.getProgramDao().deleteProgramsByTime(time)).start();
    }

    public void removeItemById(int id) {
        new Thread(() -> db.getProgramDao().deleteById(id)).start();
    }

    private static class ItemLoaderTask extends AsyncTask<Void, Void, Program> {
        private final AppRoomDatabase db;
        private final int id;
        private final int type;

        ItemLoaderTask(AppRoomDatabase db, int id, int type) {
            this.db = db;
            this.id = id;
            this.type = type;
        }

        @Override
        protected Program doInBackground(Void... voids) {
            switch (type) {
                case LOAD_LAST_IN_CHANNEL:
                    return db.getProgramDao().loadLastProgramFromChannelSync(id);
                case LOAD_BY_ID:
                    return db.getProgramDao().loadProgramByIdSync(id);
            }
            return null;
        }
    }

    private static class ItemsLoaderTask extends AsyncTask<Void, Void, List<EpgProgram>> {
        private final AppRoomDatabase db;
        private final int channelId;
        private final long startTime;
        private final long endTime;

        ItemsLoaderTask(AppRoomDatabase db, int channelId, long startTime, long endTime) {
            this.db = db;
            this.channelId = channelId;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        protected List<EpgProgram> doInBackground(Void... voids) {
            return db.getProgramDao().loadProgramsFromChannelBetweenTimeSync(channelId, startTime, endTime);
        }
    }
}
