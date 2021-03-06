package com.mobileuni.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.evernote.client.android.AsyncNoteStoreClient;
import com.evernote.client.android.EvernoteSession;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NotesMetadataResultSpec;
import com.evernote.thrift.transport.TTransportException;
import com.mobileuni.R;
import com.mobileuni.helpers.AppStatus;
import com.mobileuni.helpers.DialogHelper;
import com.mobileuni.helpers.MenuHelper;
import com.mobileuni.listeners.CourseChangeListener;
import com.mobileuni.listeners.EvernoteListener;
import com.mobileuni.model.MetaNote;
import com.mobileuni.model.Session;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A simple controller to control the notes related to a certain course. It handles creation, reading and modification
 * @author Joshua W�hle
 */
public class CourseNoteController extends Activity implements OnClickListener,
		CourseChangeListener {

	final int EVERNOTE_CREATED_NOTE = 1;
	final int EVERNOTE_VIEW_NOTE = 2;
	AsyncNoteStoreClient ns;
	EvernoteListener el = new EvernoteListener(this);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		MenuHelper.setContentViewAndSlideMenu(this, R.layout.item_list, R.string.menu_notes);
		Session.setContext(this);
		Session.getCurrentSelectedCourse().addListener(this);
		

		createAddNoteButton();
		if (!Session.getEs().isLoggedIn() && AppStatus.isOnline()) { // If
																		// online
																		// &&
																		// we're
																		// not
																		// logged-in,
																		// it
																		// means
																		// we
																		// need
																		// a new
																		// token
			AlertDialog dialog = DialogHelper.evernoteAuthenticateDialog(this);
			dialog.show();
		}
		findRelatedNotes();
	}

	/**
	 * Simply adds the button responsible for creating new notes
	 */
	public void createAddNoteButton() {
		LinearLayout list = (LinearLayout) findViewById(R.id.item_list);
		Button addNoteButton = new Button(this);
		addNoteButton.setText(R.string.note_add);
		addNoteButton.setWidth(LayoutParams.MATCH_PARENT);
		addNoteButton.setHeight(LayoutParams.WRAP_CONTENT);
		addNoteButton.setTag("add_note");
		addNoteButton.setOnClickListener(this);
		list.addView(addNoteButton);
	}

	@SuppressWarnings("unchecked")
	private void findRelatedNotes() {
		int pageSize = 10;
		NoteFilter filter = new NoteFilter();
		List<String> tags = new ArrayList<String>();
		tags.add(Session.getCurrentSelectedCourse().getShortName());
		filter.setWords(Session.getCurrentSelectedCourse().getShortName());
		NotesMetadataResultSpec spec = new NotesMetadataResultSpec();
		spec.setIncludeTitle(true);
		spec.setIncludeCreated(true);
		if (ns == null) {
			try {
				ns = Session.getEs().getClientFactory().createNoteStoreClient();
			} catch (TTransportException e1) {
				Toast.makeText(this, this.getResources().getString(R.string.notes_evernote_problem), Toast.LENGTH_SHORT).show();
				e1.printStackTrace();
				return;
			} catch(Exception e) {
				Toast.makeText(this, this.getResources().getString(R.string.notes_evernote_problem), Toast.LENGTH_SHORT).show();
				e.printStackTrace();
				return;
			}
		}
		if (AppStatus.isOnline())
			ns.findNotesMetadata(filter, 0, pageSize, spec, el);
		else
			notesChanged();
	}

	private void addNote(MetaNote note) {
		LinearLayout temp = (LinearLayout) getLayoutInflater().inflate(
				R.layout.list_item, null);
		temp.setTag(note);
		temp.setOnClickListener(this);
		TextView title = (TextView) temp.findViewById(R.id.item_title);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
		title.setText(note.getName() + " - Created: "
				+ sdf.format(note.getDateCreated().getTime()));

		Button shareButton = new Button(this);
		shareButton.setText("Share");
		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

		shareButton.setTag("share_note-" + note.getEvernoteId());
		shareButton.setOnClickListener(this);

		((RelativeLayout) temp.findViewById(R.id.list_item_title_layout))
				.addView(shareButton, lp);
		((LinearLayout) findViewById(R.id.item_list)).addView(temp);
	}

	/**
	 * Authenticate to evernote, needed to use it's integration
	 */
	public void evernoteAuthenticate() {
		Session.getEs().authenticate(this);
	}

	/**
	 * Checks if Evernote is installed
	 * 
	 * @return
	 */
	private boolean evernoteInstalled() {
		try {
			getPackageManager().getApplicationInfo("com.evernote", 0);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}

	public void onClick(View v) {

		if (v.getTag().equals("add_note")) {
			Intent intent = new Intent("com.evernote.action.CREATE_NEW_NOTE");
			ArrayList<String> tags = new ArrayList<String>();
			tags.add("Mobile University");
			tags.add(Session.getCurrentSelectedCourse().getShortName());
			tags.add(Session.getCurrentSelectedCourse().getIdNumber());
			intent.putExtra("TAG_NAME_LIST", tags);
			if (evernoteInstalled())
				startActivityForResult(intent, EVERNOTE_CREATED_NOTE);
			else {
				AlertDialog dialog = DialogHelper.evernoteInstallDialog(this);
				dialog.show();
			}
		} else if (v.getTag() instanceof MetaNote) {
			MetaNote note = (MetaNote) v.getTag();
			Intent intent = new Intent("com.evernote.action.VIEW_NOTE");
			intent.putExtra("NOTE_GUID", note.getEvernoteId());
			if (evernoteInstalled())
				startActivityForResult(intent, EVERNOTE_VIEW_NOTE);
			else {
				AlertDialog dialog = DialogHelper.evernoteInstallDialog(this);
				dialog.show();
			}
		} else if (((String) v.getTag()).startsWith("share_note")) {
			String tag = (String) v.getTag();
			String evernoteId = tag.replaceAll("share_note-", "");
			String noteContent = Session.getCurrentSelectedCourse()
					.getNoteFromEvernoteId(evernoteId).getContent();
			if (noteContent != null) {
				Intent sharingIntent = new Intent(Intent.ACTION_SEND);
				sharingIntent.setType("text/plain");
				sharingIntent.putExtra(noteContent,
						android.content.Intent.EXTRA_TEXT);
				startActivity(Intent.createChooser(sharingIntent,
						getResources().getString(R.string.share_using)));
			} else {
				Toast.makeText(
						this,
						getResources().getString(R.string.note_content_not_set),
						Toast.LENGTH_SHORT).show();
			}
		}

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		// Update UI when oauth activity returns result
		case EvernoteSession.REQUEST_CODE_OAUTH:
			if (resultCode == Activity.RESULT_OK)
				findRelatedNotes();
			break;
		case EVERNOTE_CREATED_NOTE:
			if (resultCode == Activity.RESULT_OK)
				findRelatedNotes();
			break;
		default:
			break;
		}
	}

	public void notesChanged() {
		for (MetaNote note : Session.getCurrentSelectedCourse().getNotes()) {
			addNote(note);
		}
	}

	public void courseContentsChanged() {
		// Nothing to do
	}

	public void fileChanged(String filePath) {
		// Nothing to do
	}
}
