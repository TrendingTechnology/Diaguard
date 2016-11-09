package com.faltenreich.diaguard.ui.fragment;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.Spinner;
import android.widget.Toast;

import com.faltenreich.diaguard.R;
import com.faltenreich.diaguard.data.PreferenceHelper;
import com.faltenreich.diaguard.event.Events;
import com.faltenreich.diaguard.event.PermissionDeniedEvent;
import com.faltenreich.diaguard.event.PermissionGrantedEvent;
import com.faltenreich.diaguard.ui.view.CategoryCheckBoxList;
import com.faltenreich.diaguard.util.FileUtils;
import com.faltenreich.diaguard.util.Helper;
import com.faltenreich.diaguard.util.SystemUtils;
import com.faltenreich.diaguard.util.ViewHelper;
import com.faltenreich.diaguard.util.export.Export;
import com.faltenreich.diaguard.util.export.FileListener;

import org.joda.time.DateTime;

import java.io.File;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by Faltenreich on 27.10.2016.
 */

public class ExportFragment extends BaseFragment implements FileListener {

    private static final int PADDING = (int) Helper.getDPI(R.dimen.padding);

    @BindView(R.id.root) ViewGroup rootView;
    @BindView(R.id.button_datestart) Button buttonDateStart;
    @BindView(R.id.button_dateend) Button buttonDateEnd;
    @BindView(R.id.spinner_format) Spinner spinnerFormat;
    @BindView(R.id.checkbox_note) CheckBox checkBoxNotes;
    @BindView(R.id.export_list_categories) CategoryCheckBoxList categoryCheckBoxList;

    private ProgressDialog progressDialog;

    private DateTime dateStart;
    private DateTime dateEnd;

    public ExportFragment() {
        super(R.layout.fragment_export, R.string.export);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initialize();
    }

    @Override
    public void onResume() {
        super.onResume();
        Events.register(this);
        initializeGUI();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Events.unregister(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.form, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                exportIfInputIsValid();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void initialize() {
        dateEnd = DateTime.now();
        dateStart = dateEnd.withDayOfMonth(1);
    }

    public void initializeGUI() {
        buttonDateStart.setText(Helper.getDateFormat().print(dateStart));
        buttonDateEnd.setText(Helper.getDateFormat().print(dateEnd));
        progressDialog = new ProgressDialog(getContext());
        checkBoxNotes.setPadding(PADDING, PADDING, PADDING, PADDING);
        checkBoxNotes.setChecked(PreferenceHelper.getInstance().exportNotes());
        checkBoxNotes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                PreferenceHelper.getInstance().setExportNotes(isChecked);
            }
        });
    }

    private boolean validate() {
        boolean isValid = true;

        if (dateStart.isAfter(dateEnd)) {
            ViewHelper.showSnackbar(rootView, getString(R.string.validator_value_enddate));
            isValid = false;
        } else if (categoryCheckBoxList.getSelectedCategories().length == 0) {
            ViewHelper.showSnackbar(rootView, getString(R.string.validator_value_empty_list));
            isValid = false;
        }

        return isValid;
    }

    private void exportIfInputIsValid() {
        if (validate()) {
            exportIfPermissionGranted();
        }
    }

    private void exportIfPermissionGranted() {
        if (SystemUtils.canWriteExternalStorage(getActivity())) {
            export();
        } else {
            SystemUtils.requestPermissionWriteExternalStorage(getActivity());
        }
    }

    private void export() {
        progressDialog.setMessage(getString(R.string.export_progress));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();

        if (spinnerFormat.getSelectedItemPosition() == 0) {
            Export.exportPdf(this, dateStart, dateEnd, categoryCheckBoxList.getSelectedCategories());
        } else if (spinnerFormat.getSelectedItemPosition() == 1) {
            Export.exportCsv(this, false, dateStart, dateEnd, categoryCheckBoxList.getSelectedCategories());
        }
    }

    @Override
    public void onProgress(String message) {
        progressDialog.setMessage(message);
    }

    @Override
    public void onComplete(File file, String mimeType) {
        progressDialog.dismiss();
        String confirmationText = String.format(getString(R.string.export_complete), file.getAbsolutePath());
        Toast.makeText(getContext(), confirmationText, Toast.LENGTH_LONG).show();
        openFile(file, mimeType);
    }

    private void openFile(File file, String mimeType) {
        try {
            FileUtils.openFile(file, mimeType, getContext());
        } catch (ActivityNotFoundException e) {
            ViewHelper.showSnackbar(rootView, getString(R.string.error_no_app));
            Log.e("Open " + mimeType, e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    @OnClick(R.id.button_datestart)
    public void showStartDatePicker() {
        DatePickerFragment.newInstance(dateStart, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                dateStart = dateStart.withYear(year).withMonthOfYear(month + 1).withDayOfMonth(day);
                buttonDateStart.setText(Helper.getDateFormat().print(dateStart));
            }
        }).show(getFragmentManager());
    }

    @SuppressWarnings("unused")
    @OnClick(R.id.button_dateend)
    public void showEndDatePicker() {
        DatePickerFragment.newInstance(dateEnd, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                dateEnd = dateEnd.withYear(year).withMonthOfYear(month + 1).withDayOfMonth(day);
                buttonDateEnd.setText(Helper.getDateFormat().print(dateEnd));
            }
        }).show(getFragmentManager());
    }

    @SuppressWarnings("unused")
    public void onEvent(PermissionGrantedEvent event) {
        if (event.context.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            export();
        }
    }

    @SuppressWarnings("unused")
    public void onEvent(PermissionDeniedEvent event) {
        if (event.context.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ViewHelper.showToast(getContext(), R.string.permission_required_storage);
        }
    }
}