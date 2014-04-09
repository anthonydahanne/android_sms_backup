/* Copyright (c) 2009 Christoph Studer <chstuder@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.studer.smssync;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import tv.studer.smssync.SmsSyncService.SmsSyncState;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fsck.k9.Account;
import com.fsck.k9.mail.filter.Hex;
import com.fsck.k9.security.LocalKeyStore;

/**
 * This is the main activity showing the status of the SMS Sync service and
 * providing controls to configure it.
 */
public class SmsSync extends PreferenceActivity implements OnPreferenceChangeListener {
    private static final int DIALOG_MISSING_CREDENTIALS = 1;

    private static final int DIALOG_FIRST_SYNC = 2;
    
    private static final int DIALOG_SYNC_DATA_RESET = 3;
    
    private static final int DIALOG_INVALID_IMAP_FOLDER = 4;

    private static final int DIALOG_NEED_FIRST_MANUAL_SYNC = 5;

    private static final int DIALOG_ABOUT = 6;

    private static final int DIALOG_INVALID_IMAP_SERVER_URI = 7;
    
    private static final int MENU_INFO = 0;
    
    private static final int MENU_SHARE = 1;
    
    private static final int MENU_MARKET = 2;

    private StatusPreference mStatusPref;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager prefMgr = getPreferenceManager();

        addPreferencesFromResource(R.xml.main_screen);

        mStatusPref = new StatusPreference(this);
        mStatusPref.setSelectable(false);
        
        int sdkLevel = 1;
        try {
            sdkLevel = Integer.parseInt(Build.VERSION.SDK);
        } catch (NumberFormatException nfe) {
            // ignore (assume sdkLevel == 1)
        }

        if (sdkLevel < 3) {
            // Older versions don't show the title bar for PreferenceActivity
            PreferenceCategory cat = new PreferenceCategory(this);
            cat.setOrder(0);
            getPreferenceScreen().addPreference(cat);
            cat.setTitle(R.string.ui_status_label);
            cat.addPreference(mStatusPref);
        } else {
            // Newer SDK version show the title bar for PreferenceActivity
            mStatusPref.setOrder(0);
            getPreferenceScreen().addPreference(mStatusPref);
        }
        
        Preference pref = prefMgr.findPreference(PrefStore.PREF_LOGIN_USER);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_IMAP_FOLDER);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_SECURITY_PROTOCOL);
        pref.setOnPreferenceChangeListener(this);

        pref = prefMgr.findPreference(PrefStore.PREF_IMAP_SERVER_URI);
        pref.setOnPreferenceChangeListener(this);

        pref = prefMgr.findPreference(PrefStore.PREF_ENABLE_AUTO_SYNC);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_LOGIN_PASSWORD);
        pref.setOnPreferenceChangeListener(this);
        
        pref = prefMgr.findPreference(PrefStore.PREF_MAX_ITEMS_PER_SYNC);
        pref.setOnPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SmsSyncService.unsetStateChangeListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SmsSyncService.setStateChangeListener(mStatusPref);
        updateUsernameLabelFromPref();
        updateImapFolderLabelFromPref();
        updateImapServerUriLabelFromPref();
        updateSecurityProtocolLabelFromPref();
        updateMaxItemsPerSync(null);
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        
        menu.add(0, MENU_INFO, 0, R.string.menu_info).setIcon(
                android.R.drawable.ic_menu_info_details);
        menu.add(0, MENU_SHARE, 1, R.string.menu_share).setIcon(
                android.R.drawable.ic_menu_share);
        menu.add(0, MENU_MARKET, 2, R.string.menu_market).setIcon(
                R.drawable.ic_menu_update);
        return true;
    }
    

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case MENU_INFO:
                // openLink(Consts.URL_INFO_LINK);
                showDialog(DIALOG_ABOUT);
                return true;
            case MENU_SHARE:
                share();
                return true;
            case MENU_MARKET:
                openLink(Consts.URL_MARKET_SEARCH);
                return true;
        }
        return false;
    }

    private void openLink(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        startActivity(intent);
    }
    
    private void share() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_body,
                Consts.URL_MARKET_SEARCH, Consts.URL_INFO_LINK));
        startActivity(intent);
    }
    
    private void updateUsernameLabelFromPref() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        String username = prefs.getString(PrefStore.PREF_LOGIN_USER,
                getString(R.string.ui_login_label));
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_LOGIN_USER);
        pref.setTitle(username);
    }
    
    private void updateImapFolderLabelFromPref() {
        String imapFolder = PrefStore.getImapFolder(this);
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_IMAP_FOLDER);
        pref.setTitle(imapFolder);
    }

    private void updateImapServerUriLabelFromPref() {
        String imapFolder = PrefStore.getImapServerUri(this);
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_IMAP_SERVER_URI);
        pref.setTitle(imapFolder);
    }

    private void updateSecurityProtocolLabelFromPref() {
        String imapFolder = PrefStore.getSecurityProtocol(this);
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_SECURITY_PROTOCOL);
        pref.setTitle(imapFolder);
    }

    private boolean initiateSync() {
        if (!PrefStore.isLoginInformationSet(this)) {
            showDialog(DIALOG_MISSING_CREDENTIALS);
            return false;
        } else if (PrefStore.isFirstSync(this)) {
            showDialog(DIALOG_FIRST_SYNC);
            return false;
        } else {
            startSync(false);
            return true;
        }
    }
    
    private void startSync(boolean skip) {
        Intent intent = new Intent(this, SmsSyncService.class);
        if (PrefStore.isFirstSync(this)) {
            intent.putExtra(Consts.KEY_SKIP_MESSAGES, skip);
        }
        startService(intent);
    }

    private class StatusPreference extends Preference implements
            SmsSyncService.StateChangeListener, OnClickListener {
        protected static final String LOG_TAG = "StatusPreference";

        private View mView;

        private Button mSyncButton;

        private ImageView mStatusIcon;
        
        private TextView mStatusLabel;

        private View mSyncDetails;
        
        private TextView mErrorDetails;
        
        private TextView mSyncDetailsLabel;
        
        private ProgressBar mProgressBar;
        
        private ProgressBar mProgressBarIndet;
        
        public StatusPreference(Context context) {
            super(context);
        }

        public void update() {
            stateChanged(SmsSyncService.getState(), SmsSyncService.getState());
        }

        @Override
        public void stateChanged(final SmsSyncState oldState, final SmsSyncState newState) {
            if (mView != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int STATUS_IDLE = 0;
                        int STATUS_WORKING = 1;
                        int STATUS_DONE = 2;
                        int STATUS_ERROR = 3;
                        int status = -1;
                        
                        CharSequence statusLabel = null;
                        String statusDetails = null;
                        boolean progressIndeterminate = false;
                        int progressMax = 1;
                        int progressVal = 0;
                        
                        switch (newState) {
                            case AUTH_FAILED:
                                statusLabel = getText(R.string.status_auth_failure);
                                statusDetails = getString(R.string.status_auth_failure_details);
                                status = STATUS_ERROR;
                                break;
                            case CALC:
                                statusLabel = getText(R.string.status_calc);
                                statusDetails = getString(R.string.status_calc_details);
                                progressIndeterminate = true;
                                status = STATUS_WORKING;
                                break;
                            case IDLE:
                                if (oldState == SmsSyncState.SYNC
                                        || oldState == SmsSyncState.CALC) {
                                    statusLabel = getText(R.string.status_done);
                                    int backedUpCount = SmsSyncService.getCurrentSyncedItems();
                                    progressMax = SmsSyncService.getItemsToSyncCount();
                                    progressVal = backedUpCount;
                                    if (backedUpCount ==
                                            PrefStore.getMaxItemsPerSync(SmsSync.this)) {
                                        // Maximum msg per sync reached.
                                        statusDetails = getResources().getString(
                                                R.string.status_done_details_max_per_sync,
                                                backedUpCount);
                                    } else if (backedUpCount > 0) {
                                        statusDetails = getResources().getQuantityString(
                                                R.plurals.status_done_details, backedUpCount,
                                                backedUpCount);
                                    } else {
                                        statusDetails = getString(
                                                R.string.status_done_details_noitems);
                                        progressMax = 1;
                                        progressVal = 1;
                                    }
                                    
                                    progressIndeterminate = false;
                                    
                                    status = STATUS_DONE;
                                } else {
                                    statusLabel = getText(R.string.status_idle);
                                    long lastSync = PrefStore.getLastSync(SmsSync.this);
                                    String lastSyncStr;
                                    if (lastSync == PrefStore.DEFAULT_LAST_SYNC) {
                                        lastSyncStr = 
                                            getString(R.string.status_idle_details_never);
                                    } else {
                                        lastSyncStr = new Date(lastSync).toLocaleString();
                                    }
                                    statusDetails = getString(R.string.status_idle_details,
                                            lastSyncStr);
                                    status = STATUS_IDLE;
                                }
                                break;
                            case LOGIN:
                                statusLabel = getText(R.string.status_login);
                                statusDetails = getString(R.string.status_login_details);
                                progressIndeterminate = true;
                                status = STATUS_WORKING;
                                break;
                            case SYNC:
                                statusLabel = getText(R.string.status_sync);
                                statusDetails = getString(R.string.status_sync_details,
                                        SmsSyncService.getCurrentSyncedItems(),
                                        SmsSyncService.getItemsToSyncCount());
                                progressMax = SmsSyncService.getItemsToSyncCount();
                                progressVal = SmsSyncService.getCurrentSyncedItems();
                                status = STATUS_WORKING;
                                break;
                            case GENERAL_ERROR:
                                statusLabel = getString(R.string.status_unknown_error);
                                statusDetails = getString(R.string.status_unknown_error_details,
                                        SmsSyncService.getErrorDescription());
                                status = STATUS_ERROR;
                                break;
                            // following code copied from K-9, AccountSetupCheckSettings#acceptKeyDialog
                            case MISSING_CERTIFICATE:
                                final X509Certificate[] chain = SmsSyncService
                                        .getMissingCertificateChain();
                                final Account account = SmsSyncService.getAccount(SmsSync.this);
                                StringBuilder chainInfo = new StringBuilder(100);
                                MessageDigest sha1 = null;
                                try {
                                    sha1 = MessageDigest.getInstance("SHA-1");
                                } catch (NoSuchAlgorithmException e) {
                                    Log.e(LOG_TAG, "Error while initializing MessageDigest", e);
                                }

                                // We already know chain != null (tested before
                                // calling this method)
                                for (int i = 0; i < chain.length; i++) {
                                    // display certificate chain information
                                    // TODO: localize this strings
                                    chainInfo.append("Certificate chain[").append(i).append("]:\n");
                                    chainInfo.append("Subject: ")
                                            .append(chain[i].getSubjectDN().toString())
                                            .append("\n");

                                    // display SubjectAltNames too
                                    // (the user may be mislead into mistrusting
                                    // a certificate
                                    // by a subjectDN not matching the server
                                    // even though a
                                    // SubjectAltName matches)
                                    try {
                                        final Collection<List<?>> subjectAlternativeNames = chain[i]
                                                .getSubjectAlternativeNames();
                                        if (subjectAlternativeNames != null) {
                                            // The list of SubjectAltNames may
                                            // be very long
                                            // TODO: localize this string
                                            StringBuilder altNamesText = new StringBuilder();
                                            altNamesText.append("Subject has ")
                                                    .append(subjectAlternativeNames.size())
                                                    .append(" alternative names\n");

                                            // we need these for matching
                                            String storeURIHost = (Uri.parse(account.getStoreUri()))
                                                    .getHost();
                                            String transportURIHost = (Uri.parse(account
                                                    .getTransportUri())).getHost();

                                            for (List<?> subjectAlternativeName : subjectAlternativeNames) {
                                                Integer type = (Integer)subjectAlternativeName
                                                        .get(0);
                                                Object value = subjectAlternativeName.get(1);
                                                String name = "";
                                                switch (type.intValue()) {
                                                    case 0:
                                                        Log.w(LOG_TAG,
                                                                "SubjectAltName of type OtherName not supported.");
                                                        continue;
                                                    case 1: // RFC822Name
                                                        name = (String)value;
                                                        break;
                                                    case 2: // DNSName
                                                        name = (String)value;
                                                        break;
                                                    case 3:
                                                        Log.w(LOG_TAG,
                                                                "unsupported SubjectAltName of type x400Address");
                                                        continue;
                                                    case 4:
                                                        Log.w(LOG_TAG,
                                                                "unsupported SubjectAltName of type directoryName");
                                                        continue;
                                                    case 5:
                                                        Log.w(LOG_TAG,
                                                                "unsupported SubjectAltName of type ediPartyName");
                                                        continue;
                                                    case 6: // Uri
                                                        name = (String)value;
                                                        break;
                                                    case 7: // ip-address
                                                        name = (String)value;
                                                        break;
                                                    default:
                                                        Log.w(LOG_TAG,
                                                                "unsupported SubjectAltName of unknown type");
                                                        continue;
                                                }

                                                // if some of the
                                                // SubjectAltNames match the
                                                // store or transport -host,
                                                // display them
                                                if (name.equalsIgnoreCase(storeURIHost)
                                                        || name.equalsIgnoreCase(transportURIHost)) {
                                                    altNamesText.append("Subject(alt): ")
                                                            .append(name).append(",...\n");
                                                } else if (name.startsWith("*.")
                                                        && (storeURIHost
                                                                .endsWith(name.substring(2)) || transportURIHost
                                                                .endsWith(name.substring(2)))) {
                                                    altNamesText.append("Subject(alt): ")
                                                            .append(name).append(",...\n");
                                                }
                                            }
                                            chainInfo.append(altNamesText);
                                        }
                                    } catch (Exception e1) {
                                        // don't fail just because of
                                        // subjectAltNames
                                        Log.w(LOG_TAG, "cannot display SubjectAltNames in dialog",
                                                e1);
                                    }

                                    chainInfo.append("Issuer: ")
                                            .append(chain[i].getIssuerDN().toString()).append("\n");
                                    if (sha1 != null) {
                                        sha1.reset();
                                        try {
                                            char[] sha1sum = Hex.encodeHex(sha1.digest(chain[i]
                                                    .getEncoded()));
                                            chainInfo.append("Fingerprint (SHA-1): ")
                                                    .append(new String(sha1sum)).append("\n");
                                        } catch (CertificateEncodingException e) {
                                            Log.e(LOG_TAG, "Error while encoding certificate", e);
                                        }
                                    }
                                }

                                new AlertDialog.Builder(SmsSync.this)
                                        .setTitle(
                                                getString(R.string.account_setup_failed_dlg_invalid_certificate_title))
                                        .setMessage(
                                                SmsSync.this
                                                        .getString(R.string.ui_missing_certificate)
                                                        + chainInfo.toString())
                                        .setCancelable(true)
                                        .setPositiveButton(
                                                getString(R.string.account_setup_failed_dlg_invalid_certificate_accept),
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog,
                                                            int which) {
                                                        try {
                                                            LocalKeyStore localKeyStore = LocalKeyStore
                                                                    .getInstance();

                                                            Uri uri = Uri.parse(account
                                                                    .getStoreUri());
                                                            localKeyStore.addCertificate(
                                                                    uri.getHost(), uri.getPort(),
                                                                    chain[0]);
                                                        } catch (CertificateException e) {
                                                            int duration = Toast.LENGTH_LONG;
                                                            Toast toast = Toast.makeText(
                                                                    SmsSync.this,
                                                                    SmsSync.this
                                                                            .getString(R.string.account_setup_failed_dlg_certificate_message_fmt)
                                                                            + (e.getMessage() == null ? ""
                                                                                    : e.getMessage()),
                                                                    duration);
                                                            toast.show();
                                                        }
                                                    }
                                                })
                                        .setNegativeButton(
                                                getString(R.string.account_setup_failed_dlg_invalid_certificate_reject),
                                                new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog,
                                                            int which) {
                                                        finish();
                                                    }
                                                }).show();

                                statusLabel = getString(R.string.ui_missing_certificate);
                                statusDetails = getString(R.string.ui_please_retry);
                                status = STATUS_ERROR;
                                break;

                            case CANCELED:
                                statusLabel = getString(R.string.status_canceled);
                                statusDetails = getString(R.string.status_canceled_details,
                                        SmsSyncService.getCurrentSyncedItems(),
                                        SmsSyncService.getItemsToSyncCount());
                                status = STATUS_IDLE;
                        } // switch (newStatus) { ... }

                        
                        int color;
                        TextView detailTextView;
                        int syncButtonText;
                        int icon;
                        
                        if (status == STATUS_IDLE) {
                            color = R.color.status_idle;
                            detailTextView = mSyncDetailsLabel;
                            syncButtonText = R.string.ui_sync_button_label_idle;
                            icon = R.drawable.ic_idle;
                        } else if (status == STATUS_WORKING) {
                            color = R.color.status_sync;
                            detailTextView = mSyncDetailsLabel;
                            syncButtonText = R.string.ui_sync_button_label_syncing;
                            icon = R.drawable.ic_syncing;
                        } else if (status == STATUS_DONE) {
                            color = R.color.status_done;
                            detailTextView = mSyncDetailsLabel;
                            syncButtonText = R.string.ui_sync_button_label_done;
                            icon = R.drawable.ic_done;
                        } else if (status == STATUS_ERROR) {
                            color = R.color.status_error;
                            detailTextView = mErrorDetails;
                            syncButtonText = R.string.ui_sync_button_label_error;
                            icon = R.drawable.ic_error;
                        } else {
                            Log.w(Consts.TAG, "Illegal state: Unknown status.");
                            return;
                        }
                        
                        if (status != STATUS_ERROR) {
                            mSyncDetails.setVisibility(View.VISIBLE);
                            mErrorDetails.setVisibility(View.INVISIBLE);
                            if (progressIndeterminate) {
                                mProgressBarIndet.setVisibility(View.VISIBLE);
                                mProgressBar.setVisibility(View.GONE);
                            } else {
                                mProgressBar.setVisibility(View.VISIBLE);
                                mProgressBarIndet.setVisibility(View.GONE);
                                mProgressBar.setIndeterminate(progressIndeterminate);
                                mProgressBar.setMax(progressMax);
                                mProgressBar.setProgress(progressVal); 
                            }
                            
                        } else {
                            mErrorDetails.setVisibility(View.VISIBLE);
                            mSyncDetails.setVisibility(View.INVISIBLE);
                        }
                        
                        mStatusLabel.setText(statusLabel);
                        mStatusLabel.setTextColor(getResources().getColor(color));
                        mSyncButton.setText(syncButtonText);
                        mSyncButton.setEnabled(true);
                        detailTextView.setText(statusDetails);
                        mStatusIcon.setImageResource(icon);
                        
                    } // run() { ... }
                }); // runOnUiThread(...)
            } // if (mView != null) { ... }
        }

        @Override
        public void onClick(View v) {
            if (v == mSyncButton) {
                if (!SmsSyncService.isWorking()) {
                    initiateSync();
                } else {
                    SmsSyncService.cancel();
                    // Sync button will be restored on next status update.
                    mSyncButton.setText(R.string.ui_sync_button_label_canceling);
                    mSyncButton.setEnabled(false);
                }
            }
        }

        @Override
        public View getView(View convertView, ViewGroup parent) {
            if (mView == null) {
                mView = getLayoutInflater().inflate(R.layout.status, parent, false);
                mSyncButton = (Button) mView.findViewById(R.id.sync_button);
                mSyncButton.setOnClickListener(this);
                mStatusIcon = (ImageView) mView.findViewById(R.id.status_icon);
                mStatusLabel = (TextView) mView.findViewById(R.id.status_label);
                mSyncDetails = mView.findViewById(R.id.details_sync);
                mSyncDetailsLabel = (TextView) mSyncDetails.findViewById(R.id.details_sync_label);
                mProgressBar = (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress);
                mProgressBarIndet =
                    (ProgressBar) mSyncDetails.findViewById(R.id.details_sync_progress_indet);
                mErrorDetails = (TextView) mView.findViewById(R.id.details_error);
                update();
            }
            return mView;
        }
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        String title;
        String msg;
        Builder builder;
        switch (id) {
            case DIALOG_MISSING_CREDENTIALS:
                title = getString(R.string.ui_dialog_missing_credentials_title);
                msg = getString(R.string.ui_dialog_missing_credentials_msg);
                break;
            case DIALOG_SYNC_DATA_RESET:
                title = getString(R.string.ui_dialog_sync_data_reset_title);
                msg = getString(R.string.ui_dialog_sync_data_reset_msg);
                break;
            case DIALOG_INVALID_IMAP_FOLDER:
                title = getString(R.string.ui_dialog_invalid_imap_folder_title);
                msg = getString(R.string.ui_dialog_invalid_imap_folder_msg);
                break;
            case DIALOG_INVALID_IMAP_SERVER_URI:
                title = getString(R.string.ui_dialog_invalid_imap_server_uri_title);
                msg = getString(R.string.ui_dialog_invalid_imap_server_ui_msg);
                break;

            case DIALOG_NEED_FIRST_MANUAL_SYNC:
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // BUTTON1 == BUTTON_POSITIVE == "Yes"
                        if (which == DialogInterface.BUTTON1) {
                            showDialog(DIALOG_FIRST_SYNC);
                        }
                    }
                };

                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.ui_dialog_need_first_manual_sync_title);
                builder.setMessage(R.string.ui_dialog_need_first_manual_sync_msg);
                builder.setPositiveButton(android.R.string.yes, dialogClickListener);
                builder.setNegativeButton(android.R.string.no, dialogClickListener);
                builder.setCancelable(false);
                return builder.create();
            case DIALOG_FIRST_SYNC:
                DialogInterface.OnClickListener firstSyncListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // BUTTON2 == BUTTON_NEGATIVE == "Skip"
                        startSync(which == DialogInterface.BUTTON2);
                    }
                };

                builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.ui_dialog_first_sync_title);
                builder.setMessage(getString(R.string.ui_dialog_first_sync_msg,
                        PrefStore.getMaxItemsPerSync(this)));
                builder.setPositiveButton(R.string.ui_sync, firstSyncListener);
                builder.setNegativeButton(R.string.ui_skip, firstSyncListener);
                return builder.create();
            case DIALOG_ABOUT:
                builder = new AlertDialog.Builder(this);
                builder.setCustomTitle(null);
                builder.setPositiveButton(android.R.string.ok, null);
                
                DialogInterface.OnClickListener aboutEmailListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(getString(R.string.about_email_uri)));
                        intent.putExtra(Intent.EXTRA_SUBJECT,
                                getString(R.string.about_email_subject,
                                        getString(R.string.app_name),
                                        getString(R.string.app_version)));
                        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.about_email_content));
                        startActivity(intent);
                    }
                };
                builder.setNegativeButton(R.string.about_email_button, aboutEmailListener);
                View contentView = getLayoutInflater().inflate(R.layout.about_dialog, null, false);
                WebView webView = (WebView) contentView.findViewById(R.id.about_content);
                webView.loadData(getAboutText(), "text/html", "utf-8");
                builder.setView(contentView);
                
                Dialog d = builder.create();
                return d;
            default:
                return null;
        }

        return createMessageDialog(id, title, msg);
    }

    private Dialog createMessageDialog(final int id, String title, String msg) {
        Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(msg);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dismissDialog(id);
            }
        });
        return builder.create();
    }
    
    private String getAboutText() {
        try {
            InputStream input = getResources().getAssets().open("about.html");
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuffer buf = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                buf.append(line);
            }
            String aboutText = buf.toString();
            aboutText = String.format(aboutText,
                    getString(R.string.app_name),
                    getString(R.string.app_version),
                    Consts.URL_INFO_LINK,
                    Consts.URL_MARKET_SEARCH);
            aboutText.replaceAll("percent", "%");
            return aboutText;
        } catch (IOException e) {
            return "An error occured while reading about.html";
        }
        
        
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        SharedPreferences prefs = preference.getSharedPreferences();
        if (PrefStore.PREF_LOGIN_USER.equals(preference.getKey())) {
            preference.setTitle(newValue.toString());
            final String oldValue = prefs.getString(PrefStore.PREF_LOGIN_USER, null);
            if (!newValue.equals(oldValue)) {
                clearSyncDataOnValueChange(oldValue);
            }
        } else if (PrefStore.PREF_IMAP_FOLDER.equals(preference.getKey())) {
            String imapFolder = newValue.toString();
            if (PrefStore.isValidImapFolder(imapFolder)) {
                preference.setTitle(imapFolder);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showDialog(DIALOG_INVALID_IMAP_FOLDER);
                    }
                });
                return false;
            }
        } else if (PrefStore.PREF_ENABLE_AUTO_SYNC.equals(preference.getKey())) {
            boolean isEnabled = (Boolean) newValue;
            ComponentName componentName = new ComponentName(this, SmsBroadcastReceiver.class);
            PackageManager pkgMgr = getPackageManager();
            if (isEnabled) {
                pkgMgr.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
                initiateSync();
            } else {
                pkgMgr.setComponentEnabledSetting(componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                Alarms.cancel(this);
            }
        } else if (PrefStore.PREF_LOGIN_PASSWORD.equals(preference.getKey())) {
            //            final String oldValue = prefs.getString(PrefStore.PREF_LOGIN_PASSWORD, null);
            // if (PrefStore.isFirstSync(this) &&
            // PrefStore.isLoginUsernameSet(this)) {
            // showDialog(DIALOG_NEED_FIRST_MANUAL_SYNC);
            // }
        } else if (PrefStore.PREF_MAX_ITEMS_PER_SYNC.equals(preference.getKey())) {
            updateMaxItemsPerSync((String) newValue);
        } else if (PrefStore.PREF_SECURITY_PROTOCOL.equals(preference.getKey())) {
            String securityProtocol = newValue.toString();
            preference.setTitle(securityProtocol);
        } else if (PrefStore.PREF_IMAP_SERVER_URI.equals(preference.getKey())) {
            String imapServerUri = newValue.toString();
            if (PrefStore.isValidImapServerUri(imapServerUri)) {
                preference.setTitle(imapServerUri);
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showDialog(DIALOG_INVALID_IMAP_SERVER_URI);
                    }
                });
                return false;
            }
            final String oldValue = prefs.getString(PrefStore.PREF_IMAP_SERVER_URI, null);
            if (!newValue.equals(oldValue)) {
                clearSyncDataOnValueChange(oldValue);
            }

        }
        return true;
    }

    private void clearSyncDataOnValueChange(final String oldValue) {
        // We need to post the reset of sync state such that we do not interfere
        // with the current transaction of the SharedPreference.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PrefStore.clearSyncData(SmsSync.this);
                if (oldValue != null) {
                    showDialog(DIALOG_SYNC_DATA_RESET);
                }
            }
        });
    }

    private void updateMaxItemsPerSync(String newValue) {
        Preference pref = getPreferenceManager().findPreference(PrefStore.PREF_MAX_ITEMS_PER_SYNC);
        if (newValue == null) {
            newValue = String.valueOf(PrefStore.getMaxItemsPerSync(this));
        }
        pref.setTitle(newValue);
    }
}
