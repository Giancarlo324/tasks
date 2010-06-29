/*
 * Copyright (c) 2009, Todoroo Inc
 * All Rights Reserved
 * http://www.todoroo.com
 */
package com.todoroo.astrid.model;


import android.content.ContentValues;

import com.timsu.astrid.data.enums.RepeatInterval;
import com.timsu.astrid.data.task.AbstractTaskModel.RepeatInfo;
import com.todoroo.andlib.data.AbstractModel;
import com.todoroo.andlib.data.Property;
import com.todoroo.andlib.data.Table;
import com.todoroo.andlib.data.TodorooCursor;
import com.todoroo.andlib.data.Property.IntegerProperty;
import com.todoroo.andlib.data.Property.LongProperty;
import com.todoroo.andlib.data.Property.StringProperty;
import com.todoroo.andlib.utility.DateUtilities;

/**
 * Data Model which represents a task users need to accomplish.
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
@SuppressWarnings("nls")
public final class Task extends AbstractModel {

    // --- table

    public static final Table TABLE = new Table("tasks", Task.class);

    // --- properties

    /** ID */
    public static final LongProperty ID = new LongProperty(
            TABLE, ID_PROPERTY_NAME);

    /** Name of Task */
    public static final StringProperty TITLE = new StringProperty(
            TABLE, "title");

    /** Urgency of Task (see urgency flags) */
    public static final IntegerProperty URGENCY = new IntegerProperty(
            TABLE, "urgency");

    /** Importance of Task (see importance flags) */
    public static final IntegerProperty IMPORTANCE = new IntegerProperty(
            TABLE, "importance");

    /** Unixtime Task is due, 0 if not set */
    public static final LongProperty DUE_DATE = new LongProperty(
            TABLE, "dyeDate");

    /** Unixtime Task should be hidden until */
    public static final LongProperty HIDE_UNTIL = new LongProperty(
            TABLE, "hideUntil");

    /** Unixtime Task was created */
    public static final LongProperty CREATION_DATE = new LongProperty(
            TABLE, "created");

    /** Unixtime Task was last touched */
    public static final LongProperty MODIFICATION_DATE = new LongProperty(
            TABLE, "modified");

    /** Unixtime Task was completed. 0 means active */
    public static final LongProperty COMPLETION_DATE = new LongProperty(
            TABLE, "completed");

    /** Unixtime Task was deleted. 0 means not deleted */
    public static final LongProperty DELETION_DATE = new LongProperty(
            TABLE, "deleted");

    // --- for migration purposes from astrid 2 (eventually we will want to
    //     move these into the metadata table and treat them as plug-ins

    public static final StringProperty NOTES = new StringProperty(
            TABLE, "notes");

    public static final IntegerProperty ESTIMATED_SECONDS = new IntegerProperty(
            TABLE, "estimatedSeconds");

    public static final IntegerProperty ELAPSED_SECONDS = new IntegerProperty(
            TABLE, "elapsedSeconds");

    public static final IntegerProperty TIMER_START = new IntegerProperty(
            TABLE, "timerStart");

    public static final IntegerProperty PREFERRED_DUE_DATE = new IntegerProperty(
            TABLE, "preferredDueDate");

    public static final IntegerProperty POSTPONE_COUNT = new IntegerProperty(
            TABLE, "postponeCount");

    public static final IntegerProperty NOTIFICATIONS = new IntegerProperty(
            TABLE, "notifications");

    public static final IntegerProperty NOTIFICATION_FLAGS = new IntegerProperty(
            TABLE, "notificationFlags");

    public static final IntegerProperty LAST_NOTIFIED = new IntegerProperty(
            TABLE, "lastNotified");

    public static final IntegerProperty REPEAT = new IntegerProperty(
            TABLE, "repeat");

    public static final IntegerProperty FLAGS = new IntegerProperty(
            TABLE, "flags");

    public static final StringProperty CALENDAR_URI = new StringProperty(
            TABLE, "calendarUri");

    /** List of all properties for this model */
    public static final Property<?>[] PROPERTIES = generateProperties(Task.class);

    // --- urgency settings

    public static final int URGENCY_NONE = 0;
    public static final int URGENCY_TODAY = 1;
    public static final int URGENCY_THIS_WEEK = 2;
    public static final int URGENCY_THIS_MONTH = 3;
    public static final int URGENCY_WITHIN_THREE_MONTHS = 4;
    public static final int URGENCY_WITHIN_SIX_MONTHS = 5;
    public static final int URGENCY_WITHIN_A_YEAR = 6;
    public static final int URGENCY_SPECIFIC_DAY = 7;
    public static final int URGENCY_SPECIFIC_DAY_TIME = 8;

    // --- importance settings

    public static final int IMPORTANCE_DO_OR_DIE = 0;
    public static final int IMPORTANCE_MUST_DO = 1;
    public static final int IMPORTANCE_SHOULD_DO = 2;
    public static final int IMPORTANCE_NONE = 3;

    // --- defaults

    /** Default values container */
    private static final ContentValues defaultValues = new ContentValues();

    static {
        defaultValues.put(TITLE.name, "");
        defaultValues.put(DUE_DATE.name, 0);
        defaultValues.put(HIDE_UNTIL.name, 0);
        defaultValues.put(COMPLETION_DATE.name, 0);
        defaultValues.put(DELETION_DATE.name, 0);
        defaultValues.put(URGENCY.name, URGENCY_NONE);
        defaultValues.put(IMPORTANCE.name, IMPORTANCE_NONE);
    }

    private static boolean defaultValuesLoaded = false;

    public static ContentValues getStaticDefaultValues() {
        return defaultValues;
    }

    /**
     * Call to load task default values from preferences.
     */
    public static void refreshDefaultValues() {
        /*defaultValues.put(URGENCY.name,
                Preferences.getIntegerFromString(R.string.EPr_default_urgency_key));
        defaultValues.put(IMPORTANCE.name,
                Preferences.getIntegerFromString(R.string.EPr_default_importance_key));*/
        defaultValuesLoaded = true;
    }

    @Override
    public ContentValues getDefaultValues() {
        // if refreshDefaultValues has never been called, call it
        if(!defaultValuesLoaded) {
            refreshDefaultValues();
        }

        return defaultValues;
    }

    // --- data access boilerplate

    public Task() {
        super();
    }

    public Task(TodorooCursor<Task> cursor) {
        this();
        readPropertiesFromCursor(cursor);
    }

    public void readFromCursor(TodorooCursor<Task> cursor) {
        super.readPropertiesFromCursor(cursor);
    }

    @Override
    public long getId() {
        return getIdHelper(ID);
    }

    // --- parcelable helpers

    private static final Creator<Task> CREATOR = new ModelCreator<Task>(Task.class);

    @Override
    protected Creator<? extends AbstractModel> getCreator() {
        return CREATOR;
    }

    // --- data access methods

    /** Checks whether task is done. Requires COMPLETION_DATE */
    public boolean isCompleted() {
        return getValue(COMPLETION_DATE) > 0;
    }

    /** Checks whether task is deleted. Will return false if DELETION_DATE not read */
    public boolean isDeleted() {
        try {
            return getValue(DELETION_DATE) > 0;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /** Checks whether task is hidden. Requires HIDDEN_UNTIL */
    public boolean isHidden() {
    	return getValue(HIDE_UNTIL) > DateUtilities.now();
    }

    /** Checks whether task is done. Requires DUE_DATE */
    public boolean hasDueDate() {
        return getValue(DUE_DATE) > 0;
    }

    // --- data access methods for migration properties

    /** Number of bits to shift repeat value by */
    public static final int REPEAT_VALUE_OFFSET = 3;

    /**
     * @return RepeatInfo corresponding to
     */
    public RepeatInfo getRepeatInfo() {
        int repeat = getValue(REPEAT);
        if(repeat == 0)
            return null;
        int value = repeat >> REPEAT_VALUE_OFFSET;
        RepeatInterval interval = RepeatInterval.values()
            [repeat - (value << REPEAT_VALUE_OFFSET)];

        return new RepeatInfo(interval, value);
    }

}