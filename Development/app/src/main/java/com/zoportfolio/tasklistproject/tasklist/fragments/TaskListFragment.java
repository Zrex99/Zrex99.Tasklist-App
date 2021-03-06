package com.zoportfolio.tasklistproject.tasklist.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.zoportfolio.tasklistproject.alerts.NewTaskAlertFragment;
import com.zoportfolio.tasklistproject.R;
import com.zoportfolio.tasklistproject.tasklist.adapters.EditingTasksAdapter;
import com.zoportfolio.tasklistproject.tasklist.adapters.TasksAdapter;
import com.zoportfolio.tasklistproject.tasklist.dataModels.UserTask;
import com.zoportfolio.tasklistproject.tasklist.dataModels.UserTaskList;

import java.util.ArrayList;

public class TaskListFragment extends Fragment implements TasksAdapter.TasksAdapterListener, EditingTasksAdapter.EditingTasksAdapterListener {

    private static final String TAG = "TaskListFragment.TAG";

    private static final String ARG_USERTASKLIST = "userTaskList";
    private static final String ARG_VIEWS_ENABLED = "viewsEnabled";

    //Views
    private ListView mLvTasks;
    private TextView mTvName;
    private ImageButton mIbEdit;
    private ImageButton mIbTrash;
    private boolean mEditing = false;

    //DataModel
    private UserTaskList mTaskList;

    private static Boolean isAlertUp = false;
    private boolean mViewsEnabled;


    public static TaskListFragment newInstance(UserTaskList _userTaskList, boolean _viewsEnabled) {
        
        Bundle args = new Bundle();
        args.putSerializable(ARG_USERTASKLIST, _userTaskList);
        args.putBoolean(ARG_VIEWS_ENABLED, _viewsEnabled);

        TaskListFragment fragment = new TaskListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private TaskListFragmentListener mListener;
    public interface TaskListFragmentListener {
        void taskTapped(UserTaskList taskList, UserTask task, int position);

        void trashTapped(UserTaskList taskList);

        void deleteTask(UserTaskList taskList, UserTask task, int position);

        void taskListUpdated(UserTaskList updatedTaskList);

        void isNewTaskAlertUp(boolean _alertState);
        void addTask(ArrayList<String> _taskNames, String _taskListName);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if(context instanceof TaskListFragmentListener) {
            mListener = (TaskListFragmentListener)context;
        }
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.fragment_layout_tasklist, container, false);
        mTvName = view.findViewById(R.id.tv_TaskListTitle);
        mIbEdit = view.findViewById(R.id.ib_Edit);
        mIbTrash = view.findViewById(R.id.ib_trash);
        mLvTasks = view.findViewById(R.id.lv_tasks);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(getArguments() != null) {
            mTaskList = (UserTaskList) getArguments().getSerializable(ARG_USERTASKLIST);
            mViewsEnabled = getArguments().getBoolean(ARG_VIEWS_ENABLED);
        }


        if(getActivity() != null && mTaskList != null && mViewsEnabled) {
            Log.i(TAG, "onActivityCreated: loading with views enabled");
            mTvName.setText(mTaskList.getTaskListName());

            //Fill adapter and set it to the listView.
            if(mTaskList.getTasks() != null) {
                updateTaskListView(true);
                mLvTasks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        Log.i(TAG, "onItemClick: Row:" + position + " Task: " + mTaskList.getTasks().get(position).getTaskName());
                    }
                });

                mIbEdit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        //Check the editing state, control flow from there

                        if(!isAlertUp) {
                            //If the tasklist is not empty, handle code.
                            if(!mEditing) { //The list view is not being edited.
                                //Set the fragment to be editing the list view.
                                mEditing = true;
                                //Load the editing adapter.
                                editTaskListView(true);

                                mIbTrash.setVisibility(View.VISIBLE);
                                mIbTrash.setEnabled(true);
                                mIbTrash.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        mListener.trashTapped(mTaskList);
                                    }
                                });

                            }else {//The list view is in the edit state.
                                //Set the fragment back to its natural state.
                                mEditing = false;

                                updateTaskListView(true);
                                mIbTrash.setVisibility(View.GONE);
                                mIbTrash.setEnabled(false);
                            }
                        }
                    }
                });

            }

        }else if(getActivity() != null && mTaskList != null && !mViewsEnabled) {
            Log.i(TAG, "onActivityCreated: loading with views disabled");
            disableAllViews();
        }else {
            Log.i(TAG, "onActivityCreated: Tasklist is null.");
        }
    }

    /**
     * Interface methods
     */

    @Override
    public void checkActionTapped(UserTask userTask, int position) {
        Log.i(TAG, "actionTapped: Task: " + userTask.getTaskName()
                + " \nNotification Time: " + userTask.getTaskNotificationTime()
                + " \nPosition in tasklist: " + position
                + " \nChecked: " + userTask.getTaskChecked());

        //If true set to false, if false set to true.
        //I have a feeling this may give some problems, will have to keep in mind for later.
        userTask.setTaskChecked(!userTask.getTaskChecked());

        //Grab the tasks from the tasklist.
        //Assign the updated task to the tasks.
        //Set the updated tasks to the tasklist.
        ArrayList<UserTask> tasks = mTaskList.getTasks();
        tasks.set(position, userTask);
        mTaskList.setTasks(tasks);

        //Interface to the main activity to save the task.
        mListener.taskListUpdated(mTaskList);

        //Set the adapter.
        updateTaskListView(true);
    }


    @Override
    public void taskTapped(UserTask userTask, int position) {
        mListener.taskTapped(mTaskList, userTask, position);
    }

    @Override
    public void addTaskTapped() {

        if(!isAlertUp) {

            ArrayList<String> taskNames = new ArrayList<>();

            if(mTaskList != null && !mTaskList.getTasks().isEmpty()) {
                for (int i = 0; i < mTaskList.getTasks().size(); i++) {
                    if(mTaskList.getTasks().get(i) != null) {
                        taskNames.add(mTaskList.getTasks().get(i).getTaskName());
                    }
                }
            }

            if(mTaskList != null) {
                mListener.addTask(taskNames, mTaskList.getTaskListName());
            }

        }

    }

    @Override
    public void deleteActionTapped(UserTask userTask, int position) {
        //Delete the task from the tasklist variable on the fragment, and reload the editing list view.
        mListener.deleteTask(mTaskList, userTask, position);

        //After testing, it seems that using the interface to delete the task is all that is needed. Just need to reload the listview.
        editTaskListView(true);
    }

    /**
     * Custom methods
     */


    private void disableAllViews() {
        mTvName.setText(mTaskList.getTaskListName());

        //Fill adapter and set it to the listView.
        if(mTaskList.getTasks() != null) {
            updateTaskListView(false);
            mLvTasks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Log.i(TAG, "onItemClick: Row:" + position + " Task: " + mTaskList.getTasks().get(position).getTaskName());
                }
            });

            mIbEdit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //Check the editing state, control flow from there

                    if(!isAlertUp) {
                        //If the tasklist is not empty, handle code.
                        if(!mEditing) { //The list view is not being edited.
                            //Set the fragment to be editing the list view.
                            mEditing = true;
                            //Load the editing adapter.
                            editTaskListView(true);

                            mIbTrash.setVisibility(View.VISIBLE);
                            mIbTrash.setEnabled(true);
                            mIbTrash.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    mListener.trashTapped(mTaskList);
                                }
                            });

                        }else {//The list view is in the edit state.
                            //Set the fragment back to its natural state.
                            mEditing = false;

                            updateTaskListView(true);
                            mIbTrash.setVisibility(View.GONE);
                            mIbTrash.setEnabled(false);
                        }
                    }
                }
            });
            mIbEdit.setEnabled(false);
        }

    }

    private void updateTaskListView(boolean _listViewEnabled) {
        TasksAdapter ta = new TasksAdapter(getActivity(), mTaskList.getTasks(), this, _listViewEnabled);
        mLvTasks.setAdapter(ta);
    }

    private void editTaskListView(boolean _listViewEnabled) {
        EditingTasksAdapter eta = new EditingTasksAdapter(getActivity(), mTaskList.getTasks(), this, _listViewEnabled);
        mLvTasks.setAdapter(eta);
    }





}
