package biz.binarysolutions.signature.share;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import biz.binarysolutions.signature.share.tasks.ReadCapturedFilesTask;
import biz.binarysolutions.signature.share.util.FileUtil;
import biz.binarysolutions.signature.share.util.PNGFile;
import biz.binarysolutions.signature.share.util.PreferencesHandler;

/**
 *
 *
 */
public class MainActivity extends AppCompatActivity
	implements ReadCapturedFilesTask.Callback {

	private static final int CAPTURE_REQUEST_CODE = 0;

	private String  signaturesFolder = null;
	private String  fileName         = null;

	private final ArrayList<File>    files = new ArrayList<>();
	private 	  ArrayAdapter<File> adapter;

	private PreferencesHandler preferencesHandler;

	/**
	 *
	 * @return
	 */
	private boolean shouldDisplayWarningDialog() {
		return preferencesHandler.getShowWarning();
	}

	/**
	 *
	 * @return
	 */
	private boolean isCaptureLibraryInstalled() {

	    PackageManager pm          = getPackageManager();
	    String         packageName = getString(R.string.package_name);
		PackageInfo    pi          = null;

	    try {
			pi = pm.getPackageInfo(packageName, 0);
		} catch (NameNotFoundException e) {
			// do nothing
		}

		return pi != null;
	}

	/**
	 *
	 * @param errorMessage
	 */
	private void displayErrorMessage(String errorMessage) {

		if (isFinishing()) {
			return;
		}

		new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.Error)
			.setMessage(errorMessage)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	/**
	 *
	 */
	private void installLibrary() {

		Uri    uri    = Uri.parse(getString(R.string.library_url));
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);

		startActivity(intent);
	}

	/**
	 *
	 */
	private void displayInstallLibraryDialog() {

		if (isFinishing()) {
			return;
		}

		DialogInterface.OnClickListener listener =
			new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				installLibrary();
			}
		};

		new AlertDialog.Builder(this)
			.setTitle(R.string.LibraryMissing)
			.setMessage(R.string.LibraryMissingMessage)
			.setPositiveButton(R.string.Install, listener)
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	/**
	 *
	 * @param file
	 */
	private void displayRenameFileDialog(final File file) {

		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_rename, null);

		final EditText editText = view.findViewById(R.id.EditTextFileName);
		final String   fileName = FileUtil.stripExtension(file.getName());
		editText.setText(fileName);


		if (isFinishing()) {
			return;
		}

		DialogInterface.OnClickListener listener =
			new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {

				String newFileName = editText.getText().toString();
				if (!newFileName.equals(fileName) && newFileName.length() > 0) {

					newFileName += getString(R.string.PNG);

					File from = new File(file.getParent(), file.getName());
					File to   = new File(file.getParent(), newFileName);

					try {
						if (from.renameTo(to)) {
							readCapturedFiles();
						}
					} catch (Exception e) {
						// do nothing
					}
				}
			}
		};

		new AlertDialog.Builder(this)
			.setTitle(R.string.Rename)
			.setView(view)
			.setPositiveButton(android.R.string.ok, listener)
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	/**
	 *
	 */
	private void displayErrorDialog() {

		if (isFinishing()) {
			return;
		}

		new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(R.string.Error)
			.setMessage(R.string.ErrorFolder)
			.setPositiveButton(android.R.string.ok, null)
			.show();
	}

	/**
	 * @param id
	 *
	 */
	private void displayWarningDialog(final long id) {

		if (isFinishing()) {
			return;
		}

		LayoutInflater inflater = getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_warning, null);

		CheckBox checkBox = view.findViewById(R.id.checkBoxShowWarning);
		checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
				preferencesHandler.setShowWarning(!isChecked);
			}
		});

		DialogInterface.OnClickListener listener =
			new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					viewFile(id);
				}

		};

		new AlertDialog.Builder(this)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle(android.R.string.dialog_alert_title)
			.setView(view)
			.setPositiveButton(android.R.string.ok, listener)
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	/**
	 *
	 */
	private String getSignaturesFolder() {

		String folder  = getString(R.string.app_folder);
		File   storage = getFilesDir();

		return new File(storage, folder).getAbsolutePath();
	}

	/**
	 *
	 */
	private String getFileName() {

		Calendar c = Calendar.getInstance();

		String fileName = String.format(
			Locale.getDefault(),
			"%04d%02d%02d_%02d%02d%02d_%03d",
			c.get(Calendar.YEAR),
			c.get(Calendar.MONTH) + 1,
			c.get(Calendar.DAY_OF_MONTH),
			c.get(Calendar.HOUR_OF_DAY),
			c.get(Calendar.MINUTE),
			c.get(Calendar.SECOND),
			c.get(Calendar.MILLISECOND)
		);

        return fileName + ".png";
	}

	/**
	 *
	 */
	private void setButtonListener() {

		Button button = findViewById(R.id.ButtonCaptureNewSignature);
		button.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View view) {

				Intent intent =
		        	new Intent("biz.binarysolutions.signature.CAPTURE");

				String keyCode            = "biz.binarysolutions.signature.ActivationCode";
				String keyFileName        = "biz.binarysolutions.signature.FileName";
				String keyTitle           = "biz.binarysolutions.signature.Title";
				String keyStrokeWidth     = "biz.binarysolutions.signature.StrokeWidth";
				String keyStrokeColor     = "biz.binarysolutions.signature.StrokeColor";
				String keyCrop            = "biz.binarysolutions.signature.Crop";
				String keyWidth           = "biz.binarysolutions.signature.Width";
				String keyHeight          = "biz.binarysolutions.signature.Height";
				String keyBackgroundColor = "biz.binarysolutions.signature.BackgroundColor";
				String keyBackgroundImage = "biz.binarysolutions.signature.BackgroundImage";
				String keyKeepSignature   = "biz.binarysolutions.signature.KeepSignature";

				fileName = getFileName();

				String  title           = preferencesHandler.getTitle();
				int     strokeWidth     = preferencesHandler.getStrokeWidth();
				String  strokeColor     = preferencesHandler.getStrokeColor();
				boolean crop            = preferencesHandler.getCrop();
				String  width           = preferencesHandler.getWidth();
				String  height          = preferencesHandler.getHeight();
				String  backgroundColor = preferencesHandler.getBackgroundColor();
				String  backgroundImage = preferencesHandler.getBackgroundImage();
				boolean keepSignature   = true;


		        intent.putExtra(keyCode, "");
		        intent.putExtra(keyFileName, fileName);
		        intent.putExtra(keyTitle, title);
		        intent.putExtra(keyStrokeWidth, strokeWidth);
		        intent.putExtra(keyStrokeColor, strokeColor);
		        intent.putExtra(keyCrop, crop);
		        intent.putExtra(keyWidth, width);
		        intent.putExtra(keyHeight, height);
		        intent.putExtra(keyBackgroundColor, backgroundColor);
		        intent.putExtra(keyBackgroundImage, backgroundImage);
				intent.putExtra(keyKeepSignature, keepSignature);

		        intent.setComponent(
		    		new ComponentName(
						"biz.binarysolutions.signature",
						"biz.binarysolutions.signature.Capture"
					)
		    	);

		        if (isCaptureLibraryInstalled()) {
		        	startActivityForResult(intent, CAPTURE_REQUEST_CODE);
				} else {
					displayInstallLibraryDialog();
				}
			}
		});
	}

	/**
	 *
	 */
	private void readCapturedFiles() {

		if (signaturesFolder == null) {
			return;
		}

		new ReadCapturedFilesTask(this).execute(signaturesFolder);
	}

    /**
	 *
	 * @param readFiles
	 */
	private void populateListView(File[] readFiles) {

		files.clear();
		for (File file : readFiles) {
			files.add(new PNGFile(file));
		}

		Collections.sort(files);

		adapter.notifyDataSetChanged();
	}

	/**
	 *
	 * @return
	 */
	private Uri getUri(File file) {

		String authority = getString(R.string.app_fileprovider);
		return FileProvider.getUriForFile(this, authority, file);
	}

	/**
	 * @param uri
	 * @param chooser
	 * @param permission
	 */
	private void grantToAll
		(
			Uri    uri,
			Intent chooser,
			int    permission
		) {

		List<ResolveInfo> infoList = getPackageManager()
			.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);

		for (ResolveInfo info : infoList) {

			String packageName = info.activityInfo.packageName;
			grantUriPermission(packageName, uri, permission);
		}
	}

	/**
	 * @param id
	 *
	 */
	private void viewFile(long id) {

		Uri uri = getUri(files.get((int) id));

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setDataAndType(uri, "image/png");

		String title  = getString(R.string.View);
		startActivity(Intent.createChooser(intent, title));
	}

	/**
	 *
	 */
	private void shareFile(long id) {

		int permission = Intent.FLAG_GRANT_READ_URI_PERMISSION;
		Uri uri        = getUri(files.get((int) id));

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("image/png");
		intent.putExtra(Intent.EXTRA_STREAM, uri);
		intent.setFlags(permission);

		String title   = getString(R.string.Share);
		Intent chooser = Intent.createChooser(intent, title);
		grantToAll(uri, chooser, permission);

		startActivity(chooser);
	}

	/**
	 *
	 * @param id
	 */
	private void renameFile(long id) {
		displayRenameFileDialog(files.get((int) id));
	}

	/**
	 * @param id
	 *
	 */
	private void deleteFile(long id) {

		try {
			boolean isDeleted = files.get((int) id).delete();
			if (isDeleted) {

				files.remove((int) id);
				adapter.notifyDataSetChanged();
			}
		} catch (SecurityException e) {
			// do nothing
		}
	}

	@Override
    public void onCreate(Bundle savedInstanceState) {

		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adapter = new ArrayAdapter<>(
			this,
			android.R.layout.simple_list_item_1,
			files
		);

		ListView listView = findViewById(R.id.listViewFiles);
		listView.setAdapter(adapter);

        signaturesFolder = getSignaturesFolder();
        setButtonListener();
        registerForContextMenu(listView);

        if (! isCaptureLibraryInstalled()) {
        	displayInstallLibraryDialog();
		}

        preferencesHandler = new PreferencesHandler(MainActivity.this);
    }

	@Override
	public void onResume() {
		super.onResume();
		readCapturedFiles();
	}

	@Override
	public void onCreateContextMenu
		(
			ContextMenu     menu,
			View            view,
			ContextMenuInfo menuInfo
		) {
		super.onCreateContextMenu(menu, view, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {

		AdapterContextMenuInfo info =
			(AdapterContextMenuInfo) item.getMenuInfo();

		int id = item.getItemId();
		if (id == R.id.contextMenuItemView) {
			if (shouldDisplayWarningDialog()) {
				displayWarningDialog(info.id);
			} else {
				viewFile(info.id);
			}
			return true;
		} else if (id == R.id.contextMenuItemShare) {
			shareFile(info.id);
			return true;
		} else if (id == R.id.contextMenuItemRename) {
			renameFile(info.id);
			return true;
		} else if (id == R.id.contextMenuItemDelete) {
			deleteFile(info.id);
			return true;
		} else {
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.options_menu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

	 	if (item.getItemId() == R.id.menuItemSettings) {
        	startActivity(new Intent(this, PreferenceActivity.class));
        	return true;
	    } else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int request, int result, Intent intent) {
		super.onActivityResult(request, result, intent);

		if (request != CAPTURE_REQUEST_CODE) {
			return;
		}
		if (intent == null) {
			return;
		}

		if (result == RESULT_OK) {
			try {
				Uri uri = intent.getData();
				FileDescriptor descriptor =
					getContentResolver().openFileDescriptor(uri, "r")
						.getFileDescriptor();

				FileInputStream input = new FileInputStream(descriptor);
				Path            path  = Paths.get(signaturesFolder, fileName);

				Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
				input.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {

			String errorMessage = intent.getStringExtra("biz.binarysolutions.signature.ErrorMessage");
			if (errorMessage != null) {
				displayErrorMessage(errorMessage);
			}
		}
	}

	/**
	 *
	 * @param readFiles
	 */
	public void onCapturedFilesAvailable(File[] readFiles) {

		if (readFiles != null) {
			populateListView(readFiles);
		} else {
			displayErrorDialog();
		}
	}
}