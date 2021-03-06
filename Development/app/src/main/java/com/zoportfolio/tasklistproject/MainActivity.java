package com.zoportfolio.tasklistproject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Calendar;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.zoportfolio.tasklistproject.alerts.NewTaskAlertFragment;
import com.zoportfolio.tasklistproject.alerts.NewTaskListAlertFragment;
import com.zoportfolio.tasklistproject.contracts.PublicContracts;
import com.zoportfolio.tasklistproject.notifications.receivers.TaskReminderBroadcast;
import com.zoportfolio.tasklistproject.notifications.receivers.TasklistsRefreshBroadcast;
import com.zoportfolio.tasklistproject.settings.SettingsActivity;
import com.zoportfolio.tasklistproject.task.TaskInfoActivity;
import com.zoportfolio.tasklistproject.tasklist.adapters.TaskListFragmentPagerAdapter;
import com.zoportfolio.tasklistproject.tasklist.dataModels.UserTask;
import com.zoportfolio.tasklistproject.tasklist.dataModels.UserTaskList;
import com.zoportfolio.tasklistproject.tasklist.fragments.TaskListFragment;
import com.zoportfolio.tasklistproject.utility.FileUtility;
import com.zoportfolio.tasklistproject.utility.IOUtility;
import com.zoportfolio.tasklistproject.utility.KeyboardUtility;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements NewTaskListAlertFragment.NewTaskListAlertFragmentListener,
        TaskListFragment.TaskListFragmentListener,
        NewTaskAlertFragment.NewTaskAlertFragmentListener {

    public static final String TAG = "MainActivity.TAG";

    private static final String FRAGMENT_ALERT_NEWTASKLIST_TAG = "FRAGMENT_ALERT_NEWTASKLIST";
    private static final String FRAGMENT_ALERT_NEWTASK_TAG = "FRAGMENT_ALERT_NEWTASK";

    public static final String KEY_TASKLISTS = "KEY_TASKLISTS";

    public static final String EXTRA_TASK = "EXTRA_TASK";
    public static final String EXTRA_TASKLISTPOSITION = "EXTRA_TASKLISTPOSITION";
    public static final String EXTRA_TASKLISTS = "EXTRA_TASKLISTS";

    public static final int RESULT_CODE_TASK_CHANGED = 10;
    public static final int RESULT_CODE_TASK_UNCHANGED = 20;
    public static final int REQUEST_CODE_TASK_VIEWING = 3;

    public static final String NOTIFICATION_CHANNELID_TASKREMINDER = "TASKREMINDER_100";

    public static final String ACTION_TASK_VIEW_ACTIVITY = "ACTION_TASK_VIEW_ACTIVITY";

    public static final String FILE_REFRESH_BROADCAST_ACTIVE = "FILE_REFRESH_BROADCAST_ACTIVE";

    private ViewPager mPager;
    private PagerAdapter pagerAdapter;

    private ArrayList<UserTaskList> mTaskLists;

    private static Boolean isAlertUp = false;

    private int pagerLastPosition = 0;

    private final TaskCheckedReceiver taskCheckedReceiver = new TaskCheckedReceiver();

    /**
     * Lifecycle methods
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Create the notification channel.
        createNotificationChannel();

        //Set the users current tasklist limit.
        saveUserTasklistLimitToSharedPreferences(5);

        //Check for action bar and hide it if it is up.
        if(getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        isStoragePermissionGranted();

        loadOnFreshAppOpen();
        //Yolo

        //Grab the current date text view to get the date.
        TextView tvCurrentDate = findViewById(R.id.tv_currentDate);
        loadCurrentDate(tvCurrentDate);

        mPager = findViewById(R.id.vp_Tasklist);

        if(mTaskLists == null || mTaskLists.isEmpty()) {
            //Hide the view pager to display a textview to tell the user to input a new tasklist.
            mPager.setVisibility(View.INVISIBLE);
        }else {
            mPager.setVisibility(View.VISIBLE);
            pagerAdapter = new TaskListFragmentPagerAdapter(getSupportFragmentManager(), FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT, mTaskLists, true);
            mPager.setAdapter(pagerAdapter);
            mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                }

                @Override
                public void onPageSelected(int position) {
                    pagerLastPosition = position;
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
        }

        Context context = this;

        FloatingActionButton fabAddTaskList = findViewById(R.id.fab_newTaskList);
        fabAddTaskList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(mTaskLists != null) {
                    if(mTaskLists.size() == loadUserTasklistLimitFromSharedPreferences()) {
                        //Should toast that limit was hit
                        Toast toast = Toast.makeText(context, "Tasklist limit has been reached.", Toast.LENGTH_LONG);
                        toast.show();
                    }else {
                        if(!isAlertUp) {
                            //When the fab is clicked the new task list alert should pop up.
                            loadNewTaskListAlertFragment();
                        }
                    }
                }else {
                    if(!isAlertUp) {
                        //When the fab is clicked the new task list alert should pop up.
                        loadNewTaskListAlertFragment();
                    }
                }
            }
        });


        ImageButton ibSettings = findViewById(R.id.ib_Settings);
        ibSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isAlertUp) {
                    loadSettingsActivity();
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Reload the mTaskLists from storage and then reload the Tasklist fragment.
        loadOnResumeAppOpen();

        //Register the receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(PublicContracts.ACTION_TASK_CHECKED_NOTIFICATION);

        this.registerReceiver(taskCheckedReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Unregister the receiver
        this.unregisterReceiver(taskCheckedReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        //Get the tasklists as an arraylist of strings, and then save it to the outState.
        ArrayList<String> taskListsJSON = convertTasklistsForSaving();
        outState.putStringArrayList(KEY_TASKLISTS,taskListsJSON);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onRestoreInstanceState(savedInstanceState, persistentState);
        ArrayList<String> taskListsJSON = savedInstanceState.getStringArrayList(KEY_TASKLISTS);

        if(taskListsJSON != null && !taskListsJSON.isEmpty()) {
            if(mTaskLists == null) {
                mTaskLists = new ArrayList<>();
            }else {
                mTaskLists.clear();
            }
            mTaskLists = convertTasklistsFromLoading(taskListsJSON);
            reloadViewPager(0, true);
        }

        //Load the UI.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_TASK_VIEWING) {
            if(resultCode == RESULT_CODE_TASK_CHANGED) {
                loadTasklistsFromStorage();
            }
        }
    }


    /**
     * Interface methods
     */

    //NewTaskListAlertFragment Callbacks

    @Override
    public void cancelTapped() {
        closeNewTaskListAlertFragment(mPager.getCurrentItem());
        KeyboardUtility.hideKeyboard(this);

        if(mTaskLists == null || mTaskLists.isEmpty()) {
            TextView textView = findViewById(R.id.tv_noData);
            textView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void saveTapped(String taskListName) {
        Log.i(TAG, "saveTapped: New taskList name: " + taskListName);
        if(taskListName != null) {
            Log.i(TAG, "saveTapped: New taskList name: " + taskListName);
            //Create a new taskList
            UserTaskList newTaskList = new UserTaskList(taskListName);

            if(mTaskLists == null) {
                //For the first time adding a tasklist.
                mTaskLists = new ArrayList<UserTaskList>();
                mTaskLists.add(newTaskList);


                //TODO: Have to refine this super if block (if(mTaskLists == null)) to be cleaner and not as messy as it is right now.
                //Check that the tasklist refresh broadcast is not active, and then set it up.
                if(!loadTasklistRefreshBroadcastStateFromSharedPreferences()) {
                    Log.i(TAG, "saveTapped: New task list: state was false, setting up tasklists refresh broadcast");
                    setupTasklistsRefreshBroadcast();
                }

            }else {
                mTaskLists.add(newTaskList);
                if(!loadTasklistRefreshBroadcastStateFromSharedPreferences()) {
                    setupTasklistsRefreshBroadcast();
                }
            }

            //Save the tasklists to storage.
            saveTasklistsToStorage();

            closeNewTaskListAlertFragment(mTaskLists.size() - 1);

        }else {
            //If this happens I need to display to the user that something went wrong.
            //A toast that the saving went wrong.
            Toast toastSaveNewTasklistFailed = Toast.makeText(this, R.string.toast_TaskListSavingFailed, Toast.LENGTH_LONG);
            toastSaveNewTasklistFailed.show();
        }

        KeyboardUtility.hideKeyboard(this);
    }

    //TaskListFragment Callbacks

    @Override
    public void taskTapped(UserTaskList taskList, UserTask task, int taskPosition) {
        //Call method to load the taskInfoActivity.
        loadTaskInfoActivity(task, taskList.getTaskListName());
    }

    @Override
    public void trashTapped(UserTaskList taskList) {
        int taskListPosition;
        //Get the tasklist to delete.
        for (int i = 0; i < mTaskLists.size(); i++) {
            if(mTaskLists.get(i).equals(taskList)) {
                taskListPosition = i;
                deleteTaskList(taskListPosition);
            }
        }

        for (int i = 0; i < taskList.getTasks().size(); i++) {
            cancelAlarmForTask(this, taskList.getTasks().get(i), taskList.getTasks().get(i).getTaskId());
        }
    }

    @Override
    public void deleteTask(UserTaskList taskList, UserTask task, int position) {
        int taskListPosition;
        //Get the tasklist and task to delete, and then delete it from the tasklist and make sure the tasklist is up to date.
        for (int i = 0; i < mTaskLists.size(); i++) {
            if(mTaskLists.get(i).equals(taskList)) {
                taskListPosition = i;
                deleteTaskFromTaskList(taskListPosition, position);
            }
        }

        cancelAlarmForTask(this, task, task.getTaskId());
    }

    @Override
    public void taskListUpdated(UserTaskList updatedTaskList) {
        int indexPositionForTasklist = -1;
        for (int i = 0; i < mTaskLists.size(); i++) {
            if(mTaskLists.get(i).getTaskListName().equals(updatedTaskList.getTaskListName())) {
                indexPositionForTasklist = i;
            }
        }

        if(indexPositionForTasklist != -1) {
            //Set the updated tasklist and save it.
            mTaskLists.set(indexPositionForTasklist, updatedTaskList);
            saveTasklistsToStorage();
            Toast toast = Toast.makeText(this, getResources().getString(R.string.toast_TaskListSavingSuccesful), Toast.LENGTH_LONG);
            toast.show();
        }
    }

    @Override
    public void addTask(ArrayList<String> _taskNames, String _taskListName) {
        loadNewTaskAlertFragment(_taskNames, _taskListName);
    }

    @Override
    public void isNewTaskAlertUp(boolean _alertState) {
        isAlertUp = _alertState;
    }

    //NewTaskAlertFragment Callbacks

    @Override
    public void cancelTappedNewTaskAlert() {
        closeNewTaskAlertFragment(mPager.getCurrentItem());
        KeyboardUtility.hideKeyboard(this);
    }

    @Override
    public void saveTappedNewTaskAlert(String taskName, String taskNotificationTime, String taskListName) {
        int id = UUID.randomUUID().hashCode();
        UserTask newTask = new UserTask(id, taskName, taskNotificationTime);
        int taskPosition = -1;
        for (int i = 0; i < mTaskLists.size(); i++) {
            if(mTaskLists.get(i).getTaskListName().equals(taskListName)) {
                //Add the new task.
                mTaskLists.get(i).getTasks().add(newTask);
                for (int j = 0; j < mTaskLists.get(i).getTasks().size(); j++) {
                    if(mTaskLists.get(i).getTasks().get(j).equals(newTask)) {
                        taskPosition = j;
                    }
                }
                //Save the updated tasklists
                saveTasklistsToStorage();
                closeNewTaskAlertFragment(i);
                break;
            }
        }

        Toast toast = Toast.makeText(this, getResources().getString(R.string.toast_TaskSavingSuccesful), Toast.LENGTH_LONG);
        toast.show();

        if(checkIfNotificationTimeIsAfterCurrentTime(newTask)) {
            Log.i(TAG, "saveTappedNewTaskAlert: Notification time is after current time.");
            setAlarmForTask(this, newTask, newTask.getTaskId());
        }
        KeyboardUtility.hideKeyboard(this);
    }


    /**
     * Custom methods
     */


    private void setAlarmForTask(Context _context, UserTask _task, int _ID) {
        AlarmManager taskAlarmManager = (AlarmManager) _context.getSystemService(Context.ALARM_SERVICE);
        //IMPORTANT, Had to convert the task data into byte data in order to get this to work properly.
        // Filling the intent with the byte array of the task data,
        // implementing SerializationUtils from Apache commons lang3,
        // and adding compileOptions to utilize java 1.8 in gradle.
        Intent taskIntent = new Intent(_context, TaskReminderBroadcast.class);

        byte[] userTaskByteData = UserTask.serializeUserTask(_task);
        taskIntent.putExtra(PublicContracts.EXTRA_TASK_BYTEDATA, userTaskByteData);

        PendingIntent taskAlarmIntent = PendingIntent.getBroadcast(_context.getApplicationContext(),
                _ID,
                taskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        if(taskAlarmManager != null) {

            String notificationTime = _task.getTaskNotificationTime();
            String[] notificationTimeSplit = notificationTime.split("/");

            String hour = notificationTimeSplit[0];
            String minute = notificationTimeSplit[1];

            java.util.Calendar taskAlarmTime = java.util.Calendar.getInstance();
            taskAlarmTime.setTimeInMillis(System.currentTimeMillis());
            taskAlarmTime.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(hour));
            taskAlarmTime.set(java.util.Calendar.MINUTE, Integer.parseInt(minute));
            taskAlarmTime.set(java.util.Calendar.SECOND, 0);

            taskAlarmManager.set(AlarmManager.RTC_WAKEUP,
                    taskAlarmTime.getTimeInMillis(),
                    taskAlarmIntent);
        }
    }

    private void cancelAlarmForTask(Context _context, UserTask _task, int _ID) {
        AlarmManager taskAlarmManager = (AlarmManager) _context.getSystemService(Context.ALARM_SERVICE);

        Intent taskIntent = new Intent(_context, TaskReminderBroadcast.class);

        byte[] userTaskByteData = UserTask.serializeUserTask(_task);
        taskIntent.putExtra(PublicContracts.EXTRA_TASK_BYTEDATA, userTaskByteData);

        PendingIntent taskAlarmIntent = PendingIntent.getBroadcast(_context.getApplicationContext(),
                _ID,
                taskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        if(taskAlarmManager != null) {
            taskAlarmManager.cancel(taskAlarmIntent);
        }
    }

    private boolean checkIfNotificationTimeIsAfterCurrentTime(UserTask _userTask) {
        boolean afterCurrentTime = false;

        String notificationTime = _userTask.getTaskNotificationTime();
        String[] notificationTimeSplit = notificationTime.split("/");
        String hour = notificationTimeSplit[0];
        String minute = notificationTimeSplit[1];

        java.util.Calendar rightNow = java.util.Calendar.getInstance();
        int currentHour24Format = rightNow.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = rightNow.get(java.util.Calendar.MINUTE);

        if(Integer.parseInt(hour) > currentHour24Format) {
            //If the hour is past the current hour, then it is true.
            afterCurrentTime = true;
        }else if(Integer.parseInt(hour) == currentHour24Format && Integer.parseInt(minute) > currentMinute) {
            //If the hour is the current hour, and the minute is greater than the current minute, then it is true.
            afterCurrentTime = true;
        }

        return afterCurrentTime;
    }

    private void loadCurrentDate(TextView _tvCurrentDateDisplay) {

        //Create the variable instances needed for getting the date.
        Calendar calendar;
        SimpleDateFormat simpleDateFormat;
        String currentDate;

        //Get the calendar instance, set the date pattern, grab the time from the calendar instance, finally set the date to the textview.
        calendar = Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("EEEE d, yyyy");
        currentDate = simpleDateFormat.format(calendar.getTime());
        _tvCurrentDateDisplay.setText(currentDate);
    }


    private void loadNewTaskListAlertFragment() {
        FrameLayout frameLayout = findViewById(R.id.fragment_Container_AlertNewTaskList);
        frameLayout.setVisibility(View.VISIBLE);

        ArrayList<String> taskListNames = new ArrayList<>();
        //Check that mTaskLists is available and instantiated.
        if(mTaskLists != null && !mTaskLists.isEmpty()) {
            for (int i = 0; i < mTaskLists.size(); i++) {
                taskListNames.add(mTaskLists.get(i).getTaskListName());
            }
        }

        NewTaskListAlertFragment fragment = NewTaskListAlertFragment.newInstance(taskListNames);

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_up);
        animation.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));

        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                try {
                    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.fragment_Container_AlertNewTaskList, fragment, FRAGMENT_ALERT_NEWTASKLIST_TAG);
                    fragmentTransaction.commit();
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        frameLayout.startAnimation(animation);

        isAlertUp = true;
        if(mTaskLists != null) {
            reloadViewPager(mPager.getCurrentItem(), false);
        }
    }

    private void closeNewTaskListAlertFragment(int _tasklistPosition) {
        //Get the fragment by its tag, and null check it.
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_ALERT_NEWTASKLIST_TAG);
        if(fragment != null) {
            FrameLayout frameLayout = findViewById(R.id.fragment_Container_AlertNewTaskList);

            Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_down);
            animation.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));

            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    try {
                        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                        fragmentTransaction.remove(fragment);
                        fragmentTransaction.commitAllowingStateLoss();
                        //Hide the frame layout.
                        frameLayout.setVisibility(View.GONE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            frameLayout.startAnimation(animation);

            //Set the bool to false, so a new alert can appear.
            isAlertUp = false;
            if (mTaskLists != null) {
                reloadViewPager(_tasklistPosition, true);
            }
        }
    }


    private void loadNewTaskAlertFragment(ArrayList<String> _taskNames, String _taskListName) {


        FrameLayout frameLayout = findViewById(R.id.fragment_Container_AlertNewTask);
        frameLayout.setVisibility(View.VISIBLE);

        NewTaskAlertFragment fragment = NewTaskAlertFragment.newInstance(_taskNames, _taskListName);

        Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_up);
        animation.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));

        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                try {
                    FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                    fragmentTransaction.replace(R.id.fragment_Container_AlertNewTask, fragment, FRAGMENT_ALERT_NEWTASK_TAG);
                    fragmentTransaction.commit();
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        frameLayout.startAnimation(animation);

        isAlertUp = true;
        reloadViewPager(mPager.getCurrentItem(), false);
    }

    private void closeNewTaskAlertFragment(int _tasklistPosition) {

        //Get the fragment by its tag, and null check it.
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_ALERT_NEWTASK_TAG);
        if(fragment != null) {

            //Get the frame layout that holds the fragment.
            FrameLayout frameLayout = findViewById(R.id.fragment_Container_AlertNewTask);

            Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_down);
            animation.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));

            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    try {
                        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
                        fragmentTransaction.remove(fragment);
                        fragmentTransaction.commitAllowingStateLoss();
                        //Hide the frame layout.
                        frameLayout.setVisibility(View.GONE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            frameLayout.startAnimation(animation);

            isAlertUp = false;
            reloadViewPager(_tasklistPosition, true);
        }

    }

    private void reloadViewPager(int _positionOfTaskList, boolean _shouldViewsBeEnabled) {
        TextView textView = findViewById(R.id.tv_noData);
        textView.setVisibility(View.GONE);
        mPager = findViewById(R.id.vp_Tasklist);
        mPager.setVisibility(View.VISIBLE);
        pagerAdapter = new TaskListFragmentPagerAdapter(getSupportFragmentManager(), FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT, mTaskLists, _shouldViewsBeEnabled);
        mPager.setAdapter(pagerAdapter);
        mPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                pagerLastPosition = position;
                //TODO: Call a method to update the pagination when I include that into the app.
//                loadPaginationDots(mTaskLists.size(), position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        mPager.setCurrentItem(_positionOfTaskList);
    }

    //TODO: Needs more work on it, before I can implement it.
//    private void loadPaginationDots(int _count, int _selectedPosition) {
//        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv_PageControl);
//        recyclerView.setHasFixedSize(false);
//
//        RecyclerView.LayoutManager horizontalLayoutManager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
//        recyclerView.setLayoutManager(horizontalLayoutManager);
//
//        PaginationDotsAdapter adapter = new PaginationDotsAdapter(_count, _selectedPosition);
//        recyclerView.setAdapter(adapter);
//    }

    private void loadTaskInfoActivity(UserTask selectedTask, String taskListName) {
        //The tasklist name will be how we identify the tasklist that holds the selected task.

        //Find the tasklist position.
        int taskListPosition = -1;
        for (int i = 0; i < mTaskLists.size(); i++) {
            if(mTaskLists.get(i).getTaskListName().equals(taskListName)) {
                //Position found.
                taskListPosition = i;
            }
        }

        ArrayList<String> taskListsJSON = convertTasklistsForSaving();

        Intent intent = new Intent(this, TaskInfoActivity.class);
        intent.setAction(ACTION_TASK_VIEW_ACTIVITY);
        intent.putExtra(EXTRA_TASK, selectedTask);
        intent.putExtra(EXTRA_TASKLISTPOSITION, taskListPosition);

        intent.putExtra(EXTRA_TASKLISTS, taskListsJSON);


        startActivityForResult(intent, REQUEST_CODE_TASK_VIEWING);
    }


    private void loadSettingsActivity() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void setupTasklistsRefreshBroadcast() {

        //Going to null check the mTasklists global variable just to be safe.
        if(mTaskLists != null && !mTaskLists.isEmpty()) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent intent = new Intent(this, TasklistsRefreshBroadcast.class);
            intent.setAction(PublicContracts.ACTION_RESET_TASKLISTS_BROADCAST);

            //Using flag update current for this pending intent, so that whenever it gets created it just updates the intent data.
            PendingIntent alarmIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            if(alarmManager != null) {
                Calendar alarmTime = Calendar.getInstance();
                alarmTime.setTimeInMillis(System.currentTimeMillis());
                alarmTime.set(Calendar.HOUR_OF_DAY, 0);
                alarmTime.set(Calendar.MINUTE, 0);
                alarmTime.set(Calendar.SECOND, 0);

                //Testing alarmManager block
//                alarmManager.set(AlarmManager.RTC,
//                        alarmTime.getTimeInMillis(),
//                        alarmIntent);

                alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                        alarmTime.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        alarmIntent);


                //To keep from continually adding and setting this alarmManager whenever the main activity runs,
                // utilize the shared preferences to check if it needs to be set.
                saveTasklistRefreshBroadcastStateToSharedPreferences(true);
            }
        }else {
            saveTasklistRefreshBroadcastStateToSharedPreferences(false);
        }

    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNELID_TASKREMINDER, "Notification channel for reminding user of tasks that need to be completed.", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("This channel is used to remind user of tasks that need to be completed. These notifications will happen based on the time the user sets in the app.");

            //Set the lights for the channel.
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);

            //Set the vibration to the channel.
            channel.enableVibration(true);

            long VIBRATION_DURATION = 500L;
            long WAITING_DURATION = 500L;
            long[] vibrationPattern = {WAITING_DURATION, VIBRATION_DURATION, WAITING_DURATION, VIBRATION_DURATION};

            channel.setVibrationPattern(vibrationPattern);

            //Set the sound to the channel as well.
            //using the default notification sound and the audio attribute of SONIFICATION, which has to be built.
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(alarmSound, attributes);

            //Set the visibility of the notification to public.
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            //Create the notification channel.
            manager.createNotificationChannel(channel);
        }
    }

    private void saveTasklistRefreshBroadcastStateToSharedPreferences(Boolean _state) {
        SharedPreferences preferences = getSharedPreferences(FILE_REFRESH_BROADCAST_ACTIVE, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean(PublicContracts.PREF_TASKLIST_REFRESH_ACTIVE_KEY, _state);
        editor.apply();
    }

    private boolean loadTasklistRefreshBroadcastStateFromSharedPreferences() {
        boolean returnBool = false;
        SharedPreferences preferences = getSharedPreferences(FILE_REFRESH_BROADCAST_ACTIVE, MODE_PRIVATE);
        if(preferences.contains(PublicContracts.PREF_TASKLIST_REFRESH_ACTIVE_KEY)) {
            returnBool = preferences.getBoolean(PublicContracts.PREF_TASKLIST_REFRESH_ACTIVE_KEY, false);
        }
        //Will return true if the alarm manager is already active, otherwise will return false
        return returnBool;
    }

    private void saveUserTasklistLimitToSharedPreferences(int _limit) {
        SharedPreferences preferences = getSharedPreferences(PublicContracts.FILE_USER_TASKLIST_LIMIT, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        editor.putInt(PublicContracts.PREF_USER_TASKLIST_LIMIT_KEY, _limit);
        editor.apply();
    }

    private int loadUserTasklistLimitFromSharedPreferences() {
        int returnInt = 5;
        SharedPreferences preferences = getSharedPreferences(PublicContracts.FILE_USER_TASKLIST_LIMIT, MODE_PRIVATE);
        if(preferences.contains(PublicContracts.PREF_USER_TASKLIST_LIMIT_KEY)) {
            returnInt = preferences.getInt(PublicContracts.PREF_USER_TASKLIST_LIMIT_KEY, 5);
        }
        return returnInt;
    }

    /**
     * Custom methods - FILE I/O
     */

    private void isStoragePermissionGranted() {
        if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "isStoragePermissionGranted: permission not granted.");
            //Request permission to use file storage.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 65);
        }else {
            Log.i(TAG, "isStoragePermissionGranted: permission granted.");
        }
    }

    private ArrayList<String> convertTasklistsForSaving() {
        ArrayList<String> taskListsJSON = new ArrayList<>();
        for (int i = 0; i < mTaskLists.size(); i++) {
            UserTaskList taskList = mTaskLists.get(i);
            //Add the JSON tasklist to the arrayList.
            taskListsJSON.add(taskList.toJSONString());
        }
        return taskListsJSON;
    }

    private ArrayList<UserTaskList> convertTasklistsFromLoading(ArrayList<String> taskListJSONList) {
        ArrayList<UserTaskList> taskLists = new ArrayList<>();
        if(!taskListJSONList.isEmpty()) {
            for (int i = 0; i < taskListJSONList.size(); i++) {
                String taskListJSONString = taskListJSONList.get(i);
                UserTaskList userTaskList = UserTaskList.fromJSONString(taskListJSONString);
                if(userTaskList != null) {
                    taskLists.add(userTaskList);
                }
            }
        }
        return taskLists;
    }

    private void saveTasklistsToStorage() {
        //Convert all the tasklists to JSON for saving.
        ArrayList<String> taskListsJSON = convertTasklistsForSaving();

        //Once all tasklists have been added to the string array, save them to storage.
        FileUtility.saveToProtectedStorage(this, PublicContracts.FILE_TASKLIST_NAME, PublicContracts.FILE_TASKLIST_FOLDER, taskListsJSON);
    }

    private boolean checkForTasklistsInStorage() {
        //If this returns 0, that means there are no files
        int fileCount = FileUtility.getCountOfFolderFromProtectedStorage(this, PublicContracts.FILE_TASKLIST_FOLDER);
        return fileCount > 0;
    }

    //No null task names anymore, so the tasks are saving fine. I think I fixed this unintentionally when I was adding in null checks elsewhere.
    //NOTE: I can make this method better by having it return the taskLists, and not handling the loading of the tasklist fragment.
    private void loadTasklistsFromStorage() {
        //Check that the mTaskList is not null,
        // and if it isn't clear it so that the stored data can overwrite it.
        if(mTaskLists == null) {
            mTaskLists = new ArrayList<>();
        }else {
            mTaskLists.clear();
        }

        ArrayList<String> taskListJSONList = new ArrayList<>();
        Object obj = FileUtility.retrieveFromStorage(this, PublicContracts.FILE_TASKLIST_NAME);
        if(obj instanceof ArrayList<?>) {
            ArrayList<?> arrayList = (ArrayList<?>) obj;
            if(arrayList.size() > 0) {
                for (int i = 0; i < arrayList.size(); i++) {
                    Object o = arrayList.get(i);
                    if(o instanceof String) {
                        taskListJSONList.add((String) o);
                    }
                }
            }
        }

        //Convert the tasklists from ArrayList<String> JSON
        mTaskLists = convertTasklistsFromLoading(taskListJSONList);

        if(!mTaskLists.isEmpty()) {
//            loadTaskListFragment(mTaskLists.get(0));
            reloadViewPager(0, true);
        }
    }

    private void loadOnFreshAppOpen() {
        if(checkForTasklistsInStorage()) {
            loadTasklistsFromStorage();

            //If the tasklist refresh broadcast is not active, set it up.
            if(!loadTasklistRefreshBroadcastStateFromSharedPreferences()) {
                setupTasklistsRefreshBroadcast();
            }

        }else {
            //If there are no files saved, display the no data text view.
            TextView textView = findViewById(R.id.tv_noData);
            textView.setVisibility(View.VISIBLE);
        }
    }

    private void loadOnResumeAppOpen() {
        if(IOUtility.checkForTasklistsInStorage(this)) {
            if(mTaskLists == null) {
                mTaskLists = new ArrayList<>();
            }else {
                mTaskLists.clear();
            }
            mTaskLists = IOUtility.loadTasklistsFromStorage(this);
            if(!mTaskLists.isEmpty()) {
//                loadTaskListFragment(mTaskLists.get(0));
                reloadViewPager(0, true);
            }
        }
    }

    private void testStorageFeatures() {
//        UserTaskList newTaskList = new UserTaskList("Test");
//        UserTask newTask1 = new UserTask("Code daily","333", false);
//        UserTask newTask2 = new UserTask("ayayaya","222", true);
//        newTaskList.addTaskToList(newTask1);
//        newTaskList.addTaskToList(newTask2);
//
//        mTaskLists = new ArrayList<UserTaskList>();
//        mTaskLists.add(newTaskList);
//
//        boolean tasklistStored = checkForTasklistsInStorage();
//        Log.i(TAG, "onCreate: Tasklist in storage: " + tasklistStored);
//
//        saveTasklistsToStorage();
//
//        tasklistStored = checkForTasklistsInStorage();
//        Log.i(TAG, "onCreate: Tasklist in storage: " + tasklistStored);
//
//        loadTasklistsFromStorage();
    }

    private void deleteTaskFromTaskList(int taskListPosition, int taskPosition) {
        if(taskListPosition == -1) {
            //Toast for an error deleting.
            Toast toast = Toast.makeText(this, getResources().getString(R.string.toast_TaskDeletingFailed), Toast.LENGTH_LONG);
            toast.show();
        }else {
            //The tasklist fragment needs to update itself independently for now.
            //Ideally i could just reload the tasklist fragment.
            mTaskLists.get(taskListPosition).getTasks().remove(taskPosition);
            saveTasklistsToStorage();

            Toast toast = Toast.makeText(this, getResources().getString(R.string.toast_TaskDeletingSuccesful), Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private void deleteTaskList(int taskListPosition) {
        if(taskListPosition == -1) {
            //Toast for an error deleting.
        }else {
            mTaskLists.remove(taskListPosition);
            //Save the updated tasklists.
            saveTasklistsToStorage();
            //Check for any remaining tasklists, if not then load the no data text view.
            if(mTaskLists.isEmpty()) {

                mPager.setVisibility(View.GONE);

                TextView textView = findViewById(R.id.tv_noData);
                textView.setVisibility(View.VISIBLE);

            }else {
                //Reload view pager.
                reloadViewPager(mTaskLists.size()-1, true);

            }
        }

        if(mTaskLists.isEmpty()) {
            saveTasklistRefreshBroadcastStateToSharedPreferences(false);
        }

    }

    class TaskCheckedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction() != null && intent.getAction().equals(PublicContracts.ACTION_TASK_CHECKED_NOTIFICATION)) {
                if(mTaskLists == null) {
                    mTaskLists = new ArrayList<UserTaskList>();
                }else {
                    mTaskLists.clear();
                }
                mTaskLists = IOUtility.loadTasklistsFromStorage(context);
                if(!mTaskLists.isEmpty()) {
//                    loadTaskListFragment(mTaskLists.get(0));
                    reloadViewPager(0, true);
                }
            }
        }
    }

}
