package com.zoportfolio.tasklistproject.alerts;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.zoportfolio.tasklistproject.R;
import com.zoportfolio.tasklistproject.tasklist.dataModels.UserTask;

import java.util.ArrayList;

public class NewTaskAlertFragment extends Fragment {

    private static final String TAG = "TAlertFragment.TAG";

    private static final String ARG_TASKNAMES = "taskNames";
    private static final String ARG_TASKLISTNAME = "taskListName";

    private EditText mEtNameField;
    private TimePicker mTpNotificationTime;
    private TextView mTvConfirmAction;
    private TextView mTvCancelAction;

    private String mNotificationTime;

    public static NewTaskAlertFragment newInstance(ArrayList<String> _taskNames, String _taskListName) {

        Bundle args = new Bundle();
        args.putStringArrayList(ARG_TASKNAMES, _taskNames);
        args.putString(ARG_TASKLISTNAME, _taskListName);

        NewTaskAlertFragment fragment = new NewTaskAlertFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private NewTaskAlertFragmentListener mListener;
    public interface NewTaskAlertFragmentListener {
        void cancelTappedNewTaskAlert();
        void saveTappedNewTaskAlert(String taskName, String taskNotificationTime, String taskListName);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if(context instanceof NewTaskAlertFragmentListener) {
            mListener = (NewTaskAlertFragmentListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.fragment_layout_alert_task, container, false);
        mEtNameField = view.findViewById(R.id.et_NewTaskListName);
        mTpNotificationTime = view.findViewById(R.id.tp_NotificationTime);
        mTpNotificationTime.setIs24HourView(false);
        mTvConfirmAction = view.findViewById(R.id.tv_AlertConfirmText);
        mTvCancelAction = view.findViewById(R.id.tv_AlertCancelText);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(getActivity() != null) {
            mTpNotificationTime.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
                @Override
                public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                    String meridies;
                    if(hourOfDay < 12) {
                        meridies = "AM";
                    } else {
                        meridies = "PM";
                    }

                    Log.i(TAG, "onTimeChanged: Hour: " + hourOfDay + " Minute: " + minute + " Meridies: " + meridies);
                    mNotificationTime = UserTask.formatNotificationTime(hourOfDay, minute, meridies);
                }
            });

            mTvConfirmAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(ValidateField(mEtNameField)) {

                        //Check that the entered task name is not already used for this task list.
                        String newTaskName = mEtNameField.getText().toString();

                        ArrayList<String> taskNames = (getArguments() != null ? getArguments().getStringArrayList(ARG_TASKNAMES) : null);
                        boolean nameTaken = false;
                        if(taskNames != null) {
                            for (int i = 0; i < taskNames.size(); i++) {
                                if(newTaskName.equals(taskNames.get(i))) {
                                    nameTaken = true;
                                }
                            }
                        }
                        String taskListName = (getArguments() != null ? getArguments().getString(ARG_TASKLISTNAME) : null);
                        if(!nameTaken && taskListName != null) {
                            if(mNotificationTime == null) {
                                mNotificationTime = "8/00/AM";
                            }
                            mListener.saveTappedNewTaskAlert(mEtNameField.getText().toString(), mNotificationTime, taskListName);
                        }else {
                            String toastString = getResources().getString(R.string.toast_Task_NameTaken1) + " \"" + newTaskName + "\" " + getResources().getString(R.string.toast_Task_NameTaken2);

                            Toast toastNameTaken = Toast.makeText(getActivity(),toastString,Toast.LENGTH_LONG);
                            toastNameTaken.show();
                        }

                    }
                }
            });

            mTvCancelAction.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mListener.cancelTappedNewTaskAlert();
                }
            });


        }

    }

    /**
     * Custom Methods
     */

    private boolean ValidateField(EditText editText) {
        //Get the text and trim whitespace from it.
        String text = editText.getText().toString().trim();
        //If there is no text after trimming whitespace, return false.
        if(text.isEmpty()) {
            Toast toastNameTaken = Toast.makeText(getActivity(),getResources().getString(R.string.toast_TextInvalid),Toast.LENGTH_LONG);
            toastNameTaken.show();
            return false;
        }else {
            return true;
        }

    }


}
