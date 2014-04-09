
package com.fsck.k9;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Application;
import android.content.SharedPreferences;

import com.fsck.k9.K9.ApplicationAware;


public class K9 {
    /**
     * Components that are interested in knowing when the K9 instance is
     * available and ready (Android invokes Application.onCreate() after other
     * components') should implement this interface and register using
     * {@link K9#registerApplicationAware(ApplicationAware)}.
     */
    public static interface ApplicationAware {
        /**
         * Called when the Application instance is available and ready.
         *
         * @param application
         *            The application instance. Never <code>null</code>.
         * @throws Exception
         */
        void initializeComponent(Application application);
    }

    public static Application app = null;
    public static File tempDirectory;
    public static final String LOG_TAG = "k9";

    /**
     * Name of the {@link SharedPreferences} file used to store the last known version of the
     * accounts' databases.
     *
     * <p>
     * See {@link UpgradeDatabases} for a detailed explanation of the database upgrade process.
     * </p>
     */
    private static final String DATABASE_VERSION_CACHE = "database_version_cache";

    /**
     * Key used to store the last known database version of the accounts' databases.
     *
     * @see #DATABASE_VERSION_CACHE
     */
    private static final String KEY_LAST_ACCOUNT_DATABASE_VERSION = "last_account_database_version";

    /**
     * Components that are interested in knowing when the K9 instance is
     * available and ready.
     *
     * @see ApplicationAware
     */
    private static List<ApplicationAware> observers = new ArrayList<ApplicationAware>();

    /**
     * This will be {@code true} once the initialization is complete and {@link #notifyObservers()}
     * was called.
     * Afterwards calls to {@link #registerApplicationAware(com.fsck.k9.K9.ApplicationAware)} will
     * immediately call {@link com.fsck.k9.K9.ApplicationAware#initializeComponent(K9)} for the
     * supplied argument.
     */
    private static boolean sInitialized = false;

    public enum BACKGROUND_OPS {
        WHEN_CHECKED, ALWAYS, NEVER, WHEN_CHECKED_AUTO_SYNC
    }

    private static String language = "";

    private static BACKGROUND_OPS backgroundOps = BACKGROUND_OPS.WHEN_CHECKED;
    /**
     * Some log messages can be sent to a file, so that the logs
     * can be read using unprivileged access (eg. Terminal Emulator)
     * on the phone, without adb.  Set to null to disable
     */
    public static final String logFile = null;
    //public static final String logFile = Environment.getExternalStorageDirectory() + "/k9mail/debug.log";

    /**
     * If this is enabled, various development settings will be enabled
     * It should NEVER be on for Market builds
     * Right now, it just governs strictmode
     **/
    public static boolean DEVELOPER_MODE = true;


    /**
     * If this is enabled there will be additional logging information sent to
     * Log.d, including protocol dumps.
     * Controlled by Preferences at run-time
     */
    public static boolean DEBUG = false;

    /**
     * Should K-9 log the conversation it has over the wire with
     * SMTP servers?
     */

    public static boolean DEBUG_PROTOCOL_SMTP = true;

    /**
     * Should K-9 log the conversation it has over the wire with
     * IMAP servers?
     */

    public static boolean DEBUG_PROTOCOL_IMAP = true;


    /**
     * Should K-9 log the conversation it has over the wire with
     * POP3 servers?
     */

    public static boolean DEBUG_PROTOCOL_POP3 = true;

    /**
     * Should K-9 log the conversation it has over the wire with
     * WebDAV servers?
     */

    public static boolean DEBUG_PROTOCOL_WEBDAV = true;



    /**
     * If this is enabled than logging that normally hides sensitive information
     * like passwords will show that information.
     */
    public static boolean DEBUG_SENSITIVE = false;

    /**
     * Can create messages containing stack traces that can be forwarded
     * to the development team.
     *
     * Feature is enabled when DEBUG == true
     */
    public static String ERROR_FOLDER_NAME = "K9mail-errors";

    /**
     * A reference to the {@link SharedPreferences} used for caching the last known database
     * version.
     *
     * @see #checkCachedDatabaseVersion()
     * @see #setDatabasesUpToDate(boolean)
     */
    private static SharedPreferences sDatabaseVersionCache;

    /**
     * {@code true} if this is a debuggable build.
     */
    private static boolean sIsDebuggable;

    private static boolean mAnimations = true;

    private static boolean mConfirmDelete = false;
    private static boolean mConfirmDeleteStarred = false;
    private static boolean mConfirmSpam = false;
    private static boolean mConfirmDeleteFromNotification = true;

    private static NotificationHideSubject sNotificationHideSubject = NotificationHideSubject.NEVER;

    /**
     * Controls when to hide the subject in the notification area.
     */
    public enum NotificationHideSubject {
        ALWAYS,
        WHEN_LOCKED,
        NEVER
    }

    private static NotificationQuickDelete sNotificationQuickDelete = NotificationQuickDelete.NEVER;

    /**
     * Controls behaviour of delete button in notifications.
     */
    public enum NotificationQuickDelete {
        ALWAYS,
        FOR_SINGLE_MSG,
        NEVER
    }

    /**
     * Controls when to use the message list split view.
     */
    public enum SplitViewMode {
        ALWAYS,
        NEVER,
        WHEN_IN_LANDSCAPE
    }

    private static boolean mMessageListCheckboxes = true;
    private static boolean mMessageListStars = true;
    private static int mMessageListPreviewLines = 2;

    private static boolean mShowCorrespondentNames = true;
    private static boolean mMessageListSenderAboveSubject = false;
    private static boolean mShowContactName = false;
    private static boolean mChangeContactNameColor = false;
    private static int mContactNameColor = 0xff00008f;
    private static boolean sShowContactPicture = true;
    private static boolean mMessageViewFixedWidthFont = false;
    private static boolean mMessageViewReturnToList = false;
    private static boolean mMessageViewShowNext = false;

    private static boolean mGesturesEnabled = true;
    private static boolean mUseVolumeKeysForNavigation = false;
    private static boolean mUseVolumeKeysForListNavigation = false;
    private static boolean mStartIntegratedInbox = false;
    private static boolean mMeasureAccounts = true;
    private static boolean mCountSearchMessages = true;
    private static boolean mHideSpecialAccounts = false;
    private static boolean mMobileOptimizedLayout = false;
    private static boolean mAutofitWidth;
    private static boolean mQuietTimeEnabled = false;
    private static String mQuietTimeStarts = null;
    private static String mQuietTimeEnds = null;
    private static String mAttachmentDefaultPath = "";
    private static boolean mWrapFolderNames = false;

    private static boolean useGalleryBugWorkaround = false;
    private static boolean galleryBuggy;


    private static boolean sUseBackgroundAsUnreadIndicator = true;
    private static boolean sThreadedViewEnabled = true;
    private static SplitViewMode sSplitViewMode = SplitViewMode.NEVER;
    private static boolean sColorizeMissingContactPictures = true;

    private static boolean sMessageViewArchiveActionVisible = false;
    private static boolean sMessageViewDeleteActionVisible = true;
    private static boolean sMessageViewMoveActionVisible = false;
    private static boolean sMessageViewCopyActionVisible = false;
    private static boolean sMessageViewSpamActionVisible = false;


    /**
     * @see #areDatabasesUpToDate()
     */
    private static boolean sDatabasesUpToDate = false;


    /**
     * The MIME type(s) of attachments we're willing to view.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_VIEW_TYPES = new String[] {
        "*/*",
    };

    /**
     * The MIME type(s) of attachments we're not willing to view.
     */
    public static final String[] UNACCEPTABLE_ATTACHMENT_VIEW_TYPES = new String[] {
    };

    /**
     * The MIME type(s) of attachments we're willing to download to SD.
     */
    public static final String[] ACCEPTABLE_ATTACHMENT_DOWNLOAD_TYPES = new String[] {
        "*/*",
    };

    /**
     * The MIME type(s) of attachments we're not willing to download to SD.
     */
    public static final String[] UNACCEPTABLE_ATTACHMENT_DOWNLOAD_TYPES = new String[] {
    };

    /**
     * For use when displaying that no folder is selected
     */
    public static final String FOLDER_NONE = "-NONE-";

    public static final String LOCAL_UID_PREFIX = "K9LOCAL:";

    public static final String REMOTE_UID_PREFIX = "K9REMOTE:";

    public static final String IDENTITY_HEADER = "X-K9mail-Identity";

    /**
     * Specifies how many messages will be shown in a folder by default. This number is set
     * on each new folder and can be incremented with "Load more messages..." by the
     * VISIBLE_LIMIT_INCREMENT
     */
    public static int DEFAULT_VISIBLE_LIMIT = 25;

    /**
     * The maximum size of an attachment we're willing to download (either View or Save)
     * Attachments that are base64 encoded (most) will be about 1.375x their actual size
     * so we should probably factor that in. A 5MB attachment will generally be around
     * 6.8MB downloaded but only 5MB saved.
     */
    public static final int MAX_ATTACHMENT_DOWNLOAD_SIZE = (128 * 1024 * 1024);


    /* How many times should K-9 try to deliver a message before giving up
     * until the app is killed and restarted
     */

    public static int MAX_SEND_ATTEMPTS = 5;

    /**
     * Max time (in millis) the wake lock will be held for when background sync is happening
     */
    public static final int WAKE_LOCK_TIMEOUT = 600000;

    public static final int MANUAL_WAKE_LOCK_TIMEOUT = 120000;

    public static final int PUSH_WAKE_LOCK_TIMEOUT = 60000;

    public static final int MAIL_SERVICE_WAKE_LOCK_TIMEOUT = 60000;

    public static final int BOOT_RECEIVER_WAKE_LOCK_TIMEOUT = 60000;

    /**
     * Time the LED is on/off when blinking on new email notification
     */
    public static final int NOTIFICATION_LED_ON_TIME = 500;
    public static final int NOTIFICATION_LED_OFF_TIME = 2000;

    public static final boolean NOTIFICATION_LED_WHILE_SYNCING = false;
    public static final int NOTIFICATION_LED_FAST_ON_TIME = 100;
    public static final int NOTIFICATION_LED_FAST_OFF_TIME = 100;


    public static final int NOTIFICATION_LED_BLINK_SLOW = 0;
    public static final int NOTIFICATION_LED_BLINK_FAST = 1;



    public static final int NOTIFICATION_LED_FAILURE_COLOR = 0xffff0000;

    // Must not conflict with an account number
    public static final int FETCHING_EMAIL_NOTIFICATION      = -5000;
    public static final int SEND_FAILED_NOTIFICATION      = -1500;
    public static final int CERTIFICATE_EXCEPTION_NOTIFICATION_INCOMING = -2000;
    public static final int CERTIFICATE_EXCEPTION_NOTIFICATION_OUTGOING = -2500;
    public static final int CONNECTIVITY_ID = -3;

}
