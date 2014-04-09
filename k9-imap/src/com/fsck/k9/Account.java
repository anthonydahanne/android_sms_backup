
package com.fsck.k9;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;

import com.fsck.k9.mail.MessagingException;
import com.fsck.k9.mail.Store;
import com.fsck.k9.mail.store.StorageManager;
import com.fsck.k9.mail.store.StorageManager.StorageProvider;
import com.fsck.k9.security.LocalKeyStore;

/**
 * Account stores all of the settings for a single account defined by the user. It is able to save
 * and delete itself given a Preferences to work with. Each account is defined by a UUID.
 */
public class Account implements BaseAccount {
    /**
     * Default value for the inbox folder (never changes for POP3 and IMAP)
     */
    public static final String INBOX = "INBOX";

    /**
     * This local folder is used to store messages to be sent.
     */
    public static final String OUTBOX = "K9MAIL_INTERNAL_OUTBOX";

    public static final String EXPUNGE_IMMEDIATELY = "EXPUNGE_IMMEDIATELY";
    public static final String EXPUNGE_MANUALLY = "EXPUNGE_MANUALLY";
    public static final String EXPUNGE_ON_POLL = "EXPUNGE_ON_POLL";

    public static final int DELETE_POLICY_NEVER = 0;
    public static final int DELETE_POLICY_7DAYS = 1;
    public static final int DELETE_POLICY_ON_DELETE = 2;
    public static final int DELETE_POLICY_MARK_AS_READ = 3;

    public static final String TYPE_WIFI = "WIFI";
    public static final String TYPE_MOBILE = "MOBILE";
    public static final String TYPE_OTHER = "OTHER";
    private static final String[] networkTypes = { TYPE_WIFI, TYPE_MOBILE, TYPE_OTHER };

    public static final MessageFormat DEFAULT_MESSAGE_FORMAT = MessageFormat.HTML;
    public static final boolean DEFAULT_MESSAGE_FORMAT_AUTO = false;
    public static final boolean DEFAULT_MESSAGE_READ_RECEIPT = false;
    public static final QuoteStyle DEFAULT_QUOTE_STYLE = QuoteStyle.PREFIX;
    public static final String DEFAULT_QUOTE_PREFIX = ">";
    public static final boolean DEFAULT_QUOTED_TEXT_SHOWN = true;
    public static final boolean DEFAULT_REPLY_AFTER_QUOTE = false;
    public static final boolean DEFAULT_STRIP_SIGNATURE = true;
    public static final int DEFAULT_REMOTE_SEARCH_NUM_RESULTS = 25;

    public static final String ACCOUNT_DESCRIPTION_KEY = "description";
    public static final String STORE_URI_KEY = "storeUri";
    public static final String TRANSPORT_URI_KEY = "transportUri";

    public static final String IDENTITY_NAME_KEY = "name";
    public static final String IDENTITY_EMAIL_KEY = "email";
    public static final String IDENTITY_DESCRIPTION_KEY = "description";

    /*
     * http://developer.android.com/design/style/color.html
     * Note: Order does matter, it's the order in which they will be picked.
     */
    public static final Integer[] PREDEFINED_COLORS = new Integer[] {
            Color.parseColor("#0099CC"),    // blue
            Color.parseColor("#669900"),    // green
            Color.parseColor("#FF8800"),    // orange
            Color.parseColor("#CC0000"),    // red
            Color.parseColor("#9933CC")     // purple
    };

    public enum SortType {
        SORT_DATE(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_ARRIVAL(R.string.sort_earliest_first, R.string.sort_latest_first, false),
        SORT_SUBJECT(R.string.sort_subject_alpha, R.string.sort_subject_re_alpha, true),
        SORT_SENDER(R.string.sort_sender_alpha, R.string.sort_sender_re_alpha, true),
        SORT_UNREAD(R.string.sort_unread_first, R.string.sort_unread_last, true),
        SORT_FLAGGED(R.string.sort_flagged_first, R.string.sort_flagged_last, true),
        SORT_ATTACHMENT(R.string.sort_attach_first, R.string.sort_unattached_first, true);

        private int ascendingToast;
        private int descendingToast;
        private boolean defaultAscending;

        SortType(int ascending, int descending, boolean ndefaultAscending) {
            ascendingToast = ascending;
            descendingToast = descending;
            defaultAscending = ndefaultAscending;
        }

        public int getToast(boolean ascending) {
            return (ascending) ? ascendingToast : descendingToast;
        }

        public boolean isDefaultAscending() {
            return defaultAscending;
        }
    }

    public static final SortType DEFAULT_SORT_TYPE = SortType.SORT_DATE;
    public static final boolean DEFAULT_SORT_ASCENDING = false;


    /**
     * <pre>
     * 0 - Never (DELETE_POLICY_NEVER)
     * 1 - After 7 days (DELETE_POLICY_7DAYS)
     * 2 - When I delete from inbox (DELETE_POLICY_ON_DELETE)
     * 3 - Mark as read (DELETE_POLICY_MARK_AS_READ)
     * </pre>
     */
    private int mDeletePolicy;

    private final String mUuid;
    private String mStoreUri;

    /**
     * Storage provider ID, used to locate and manage the underlying DB/file
     * storage
     */
    private String mLocalStorageProviderId;
    private String mTransportUri;
    private String mDescription;
    private String mAlwaysBcc;
    private int mAutomaticCheckIntervalMinutes;
    private int mDisplayCount;
    private int mChipColor;
    private long mLastAutomaticCheckTime;
    private long mLatestOldMessageSeenTime;
    private boolean mNotifyNewMail;
    private boolean mNotifySelfNewMail;
    private String mInboxFolderName;
    private String mDraftsFolderName;
    private String mSentFolderName;
    private String mTrashFolderName;
    private String mArchiveFolderName;
    private String mSpamFolderName;
    private String mAutoExpandFolderName;
    private FolderMode mFolderDisplayMode;
    private FolderMode mFolderSyncMode;
    private FolderMode mFolderPushMode;
    private FolderMode mFolderTargetMode;
    private int mAccountNumber;
    private boolean mPushPollOnConnect;
    private boolean mNotifySync;
    private SortType mSortType;
    private HashMap<SortType, Boolean> mSortAscending = new HashMap<SortType, Boolean>();
    private ShowPictures mShowPictures;
    private boolean mIsSignatureBeforeQuotedText;
    private String mExpungePolicy = EXPUNGE_IMMEDIATELY;
    private int mMaxPushFolders;
    private int mIdleRefreshMinutes;
    private boolean goToUnreadMessageSearch;
    private boolean mNotificationShowsUnreadCount;
    private final Map<String, Boolean> compressionMap = new ConcurrentHashMap<String, Boolean>();
    private Searchable searchableFolders;
    private boolean subscribedFoldersOnly;
    private int maximumPolledMessageAge;
    private int maximumAutoDownloadMessageSize;
    // Tracks if we have sent a notification for this account for
    // current set of fetched messages
    private boolean mRingNotified;
    private MessageFormat mMessageFormat;
    private boolean mMessageFormatAuto;
    private boolean mMessageReadReceipt;
    private QuoteStyle mQuoteStyle;
    private String mQuotePrefix;
    private boolean mDefaultQuotedTextShown;
    private boolean mReplyAfterQuote;
    private boolean mStripSignature;
    private boolean mSyncRemoteDeletions;
    private String mCryptoApp;
    private boolean mCryptoAutoSignature;
    private boolean mCryptoAutoEncrypt;
    private boolean mMarkMessageAsReadOnView;
    private boolean mAlwaysShowCcBcc;
    private boolean mAllowRemoteSearch;
    private boolean mRemoteSearchFullText;
    private int mRemoteSearchNumResults;



    /**
     * Indicates whether this account is enabled, i.e. ready for use, or not.
     *
     * <p>
     * Right now newly imported accounts are disabled if the settings file didn't contain a
     * password for the incoming and/or outgoing server.
     * </p>
     */
    private boolean mEnabled;

    /**
     * Name of the folder that was last selected for a copy or move operation.
     *
     * Note: For now this value isn't persisted. So it will be reset when
     *       K-9 Mail is restarted.
     */
    private String lastSelectedFolderName = null;


    public enum FolderMode {
        NONE, ALL, FIRST_CLASS, FIRST_AND_SECOND_CLASS, NOT_SECOND_CLASS
    }

    public enum ShowPictures {
        NEVER, ALWAYS, ONLY_FROM_CONTACTS
    }

    public enum Searchable {
        ALL, DISPLAYABLE, NONE
    }

    public enum QuoteStyle {
        PREFIX, HEADER
    }

    public enum MessageFormat {
        TEXT, HTML, AUTO
    }

    protected Account(Context context) {
        mUuid = UUID.randomUUID().toString();
        // mLocalStorageProviderId =
        // StorageManager.getInstance(K9.app).getDefaultProviderId();
        mAutomaticCheckIntervalMinutes = -1;
        mIdleRefreshMinutes = 24;
        mPushPollOnConnect = true;
        mDisplayCount = K9.DEFAULT_VISIBLE_LIMIT;
        mAccountNumber = -1;
        mNotifyNewMail = true;
        mNotifySync = true;
        mNotifySelfNewMail = true;
        mFolderDisplayMode = FolderMode.NOT_SECOND_CLASS;
        mFolderSyncMode = FolderMode.FIRST_CLASS;
        mFolderPushMode = FolderMode.FIRST_CLASS;
        mFolderTargetMode = FolderMode.NOT_SECOND_CLASS;
        mSortType = DEFAULT_SORT_TYPE;
        mSortAscending.put(DEFAULT_SORT_TYPE, DEFAULT_SORT_ASCENDING);
        mShowPictures = ShowPictures.NEVER;
        mIsSignatureBeforeQuotedText = false;
        mExpungePolicy = EXPUNGE_IMMEDIATELY;
        mAutoExpandFolderName = INBOX;
        mInboxFolderName = INBOX;
        mMaxPushFolders = 10;
        goToUnreadMessageSearch = false;
        mNotificationShowsUnreadCount = true;
        subscribedFoldersOnly = false;
        maximumPolledMessageAge = -1;
        maximumAutoDownloadMessageSize = 32768;
        mMessageFormat = DEFAULT_MESSAGE_FORMAT;
        mMessageFormatAuto = DEFAULT_MESSAGE_FORMAT_AUTO;
        mMessageReadReceipt = DEFAULT_MESSAGE_READ_RECEIPT;
        mQuoteStyle = DEFAULT_QUOTE_STYLE;
        mQuotePrefix = DEFAULT_QUOTE_PREFIX;
        mDefaultQuotedTextShown = DEFAULT_QUOTED_TEXT_SHOWN;
        mReplyAfterQuote = DEFAULT_REPLY_AFTER_QUOTE;
        mStripSignature = DEFAULT_STRIP_SIGNATURE;
        mSyncRemoteDeletions = true;
        mCryptoAutoSignature = false;
        mCryptoAutoEncrypt = false;
        mAllowRemoteSearch = false;
        mRemoteSearchFullText = false;
        mRemoteSearchNumResults = DEFAULT_REMOTE_SEARCH_NUM_RESULTS;
        mEnabled = true;
        mMarkMessageAsReadOnView = true;
        mAlwaysShowCcBcc = false;

        searchableFolders = Searchable.ALL;


    }


    public static int findNewAccountNumber(List<Integer> accountNumbers) {
        int newAccountNumber = -1;
        Collections.sort(accountNumbers);
        for (int accountNumber : accountNumbers) {
            if (accountNumber > newAccountNumber + 1) {
                break;
            }
            newAccountNumber = accountNumber;
        }
        newAccountNumber++;
        return newAccountNumber;
    }


    /**
     * @param context
     * @return <code>null</code> if not available
     * @throws MessagingException
     * @see {@link #isAvailable(Context)}
     */
    public AccountStats getStats(Context context) throws MessagingException {
        if (!isAvailable(context)) {
            return null;
        }

        AccountStats stats = new AccountStats();


        return stats;
    }


    @Override
    public String getUuid() {
        return mUuid;
    }

    public Uri getContentUri() {
        return Uri.parse("content://accounts/" + getUuid());
    }

    public synchronized String getStoreUri() {
        return mStoreUri;
    }

    public synchronized void setStoreUri(String storeUri) {
        this.mStoreUri = storeUri;
    }

    public synchronized String getTransportUri() {
        return mTransportUri;
    }

    public synchronized void setTransportUri(String transportUri) {
        this.mTransportUri = transportUri;
    }

    @Override
    public synchronized String getDescription() {
        return mDescription;
    }

    @Override
    public synchronized void setDescription(String description) {
        this.mDescription = description;
    }


    public synchronized String getAlwaysBcc() {
        return mAlwaysBcc;
    }

    public synchronized void setAlwaysBcc(String alwaysBcc) {
        this.mAlwaysBcc = alwaysBcc;
    }

    /* Have we sent a new mail notification on this account */
    public boolean isRingNotified() {
        return mRingNotified;
    }

    public void setRingNotified(boolean ringNotified) {
        mRingNotified = ringNotified;
    }

    public String getLocalStorageProviderId() {
        return mLocalStorageProviderId;
    }


    /**
     * Returns -1 for never.
     */
    public synchronized int getAutomaticCheckIntervalMinutes() {
        return mAutomaticCheckIntervalMinutes;
    }

    public Account(Context context, String connectionUri) {
        this(context);
        mStoreUri = connectionUri;
    }

    /**
     * @param automaticCheckIntervalMinutes or -1 for never.
     */
    public synchronized boolean setAutomaticCheckIntervalMinutes(int automaticCheckIntervalMinutes) {
        int oldInterval = this.mAutomaticCheckIntervalMinutes;
        this.mAutomaticCheckIntervalMinutes = automaticCheckIntervalMinutes;

        return (oldInterval != automaticCheckIntervalMinutes);
    }

    public synchronized int getDisplayCount() {
        return mDisplayCount;
    }

    public synchronized void setDisplayCount(int displayCount) {
        if (displayCount != -1) {
            this.mDisplayCount = displayCount;
        } else {
            this.mDisplayCount = K9.DEFAULT_VISIBLE_LIMIT;
        }
    }

    public synchronized long getLastAutomaticCheckTime() {
        return mLastAutomaticCheckTime;
    }

    public synchronized void setLastAutomaticCheckTime(long lastAutomaticCheckTime) {
        this.mLastAutomaticCheckTime = lastAutomaticCheckTime;
    }

    public synchronized long getLatestOldMessageSeenTime() {
        return mLatestOldMessageSeenTime;
    }

    public synchronized void setLatestOldMessageSeenTime(long latestOldMessageSeenTime) {
        this.mLatestOldMessageSeenTime = latestOldMessageSeenTime;
    }

    public synchronized boolean isNotifyNewMail() {
        return mNotifyNewMail;
    }

    public synchronized void setNotifyNewMail(boolean notifyNewMail) {
        this.mNotifyNewMail = notifyNewMail;
    }

    public synchronized int getDeletePolicy() {
        return mDeletePolicy;
    }

    public synchronized void setDeletePolicy(int deletePolicy) {
        this.mDeletePolicy = deletePolicy;
    }

    public boolean isSpecialFolder(String folderName) {
        return (folderName != null && (folderName.equalsIgnoreCase(getInboxFolderName()) ||
                folderName.equals(getTrashFolderName()) ||
                folderName.equals(getDraftsFolderName()) ||
                folderName.equals(getArchiveFolderName()) ||
                folderName.equals(getSpamFolderName()) ||
                folderName.equals(getOutboxFolderName()) ||
                folderName.equals(getSentFolderName()) ||
                folderName.equals(getErrorFolderName())));
    }

    public synchronized String getDraftsFolderName() {
        return mDraftsFolderName;
    }

    public synchronized void setDraftsFolderName(String draftsFolderName) {
        mDraftsFolderName = draftsFolderName;
    }

    /**
     * Checks if this account has a drafts folder set.
     * @return true if account has a drafts folder set.
     */
    public synchronized boolean hasDraftsFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mDraftsFolderName);
    }

    public synchronized String getSentFolderName() {
        return mSentFolderName;
    }

    public synchronized String getErrorFolderName() {
        return K9.ERROR_FOLDER_NAME;
    }

    public synchronized void setSentFolderName(String sentFolderName) {
        mSentFolderName = sentFolderName;
    }

    /**
     * Checks if this account has a sent folder set.
     * @return true if account has a sent folder set.
     */
    public synchronized boolean hasSentFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mSentFolderName);
    }


    public synchronized String getTrashFolderName() {
        return mTrashFolderName;
    }

    public synchronized void setTrashFolderName(String trashFolderName) {
        mTrashFolderName = trashFolderName;
    }

    /**
     * Checks if this account has a trash folder set.
     * @return true if account has a trash folder set.
     */
    public synchronized boolean hasTrashFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mTrashFolderName);
    }

    public synchronized String getArchiveFolderName() {
        return mArchiveFolderName;
    }

    public synchronized void setArchiveFolderName(String archiveFolderName) {
        mArchiveFolderName = archiveFolderName;
    }

    /**
     * Checks if this account has an archive folder set.
     * @return true if account has an archive folder set.
     */
    public synchronized boolean hasArchiveFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mArchiveFolderName);
    }

    public synchronized String getSpamFolderName() {
        return mSpamFolderName;
    }

    public synchronized void setSpamFolderName(String spamFolderName) {
        mSpamFolderName = spamFolderName;
    }

    /**
     * Checks if this account has a spam folder set.
     * @return true if account has a spam folder set.
     */
    public synchronized boolean hasSpamFolder() {
        return !K9.FOLDER_NONE.equalsIgnoreCase(mSpamFolderName);
    }

    public synchronized String getOutboxFolderName() {
        return OUTBOX;
    }

    public synchronized String getAutoExpandFolderName() {
        return mAutoExpandFolderName;
    }

    public synchronized void setAutoExpandFolderName(String autoExpandFolderName) {
        mAutoExpandFolderName = autoExpandFolderName;
    }

    public synchronized int getAccountNumber() {
        return mAccountNumber;
    }

    public synchronized FolderMode getFolderDisplayMode() {
        return mFolderDisplayMode;
    }

    public synchronized boolean setFolderDisplayMode(FolderMode displayMode) {
        FolderMode oldDisplayMode = mFolderDisplayMode;
        mFolderDisplayMode = displayMode;
        return oldDisplayMode != displayMode;
    }

    public synchronized FolderMode getFolderSyncMode() {
        return mFolderSyncMode;
    }

    public synchronized boolean setFolderSyncMode(FolderMode syncMode) {
        FolderMode oldSyncMode = mFolderSyncMode;
        mFolderSyncMode = syncMode;

        if (syncMode == FolderMode.NONE && oldSyncMode != FolderMode.NONE) {
            return true;
        }
        if (syncMode != FolderMode.NONE && oldSyncMode == FolderMode.NONE) {
            return true;
        }
        return false;
    }

    public synchronized FolderMode getFolderPushMode() {
        return mFolderPushMode;
    }

    public synchronized boolean setFolderPushMode(FolderMode pushMode) {
        FolderMode oldPushMode = mFolderPushMode;

        mFolderPushMode = pushMode;
        return pushMode != oldPushMode;
    }

    public synchronized boolean isShowOngoing() {
        return mNotifySync;
    }

    public synchronized void setShowOngoing(boolean showOngoing) {
        this.mNotifySync = showOngoing;
    }

    public synchronized SortType getSortType() {
        return mSortType;
    }

    public synchronized void setSortType(SortType sortType) {
        mSortType = sortType;
    }

    public synchronized boolean isSortAscending(SortType sortType) {
        if (mSortAscending.get(sortType) == null) {
            mSortAscending.put(sortType, sortType.isDefaultAscending());
        }
        return mSortAscending.get(sortType);
    }

    public synchronized void setSortAscending(SortType sortType, boolean sortAscending) {
        mSortAscending.put(sortType, sortAscending);
    }

    public synchronized ShowPictures getShowPictures() {
        return mShowPictures;
    }

    public synchronized void setShowPictures(ShowPictures showPictures) {
        mShowPictures = showPictures;
    }

    public synchronized FolderMode getFolderTargetMode() {
        return mFolderTargetMode;
    }

    public synchronized void setFolderTargetMode(FolderMode folderTargetMode) {
        mFolderTargetMode = folderTargetMode;
    }

    public synchronized boolean isSignatureBeforeQuotedText() {
        return mIsSignatureBeforeQuotedText;
    }

    public synchronized void setSignatureBeforeQuotedText(boolean mIsSignatureBeforeQuotedText) {
        this.mIsSignatureBeforeQuotedText = mIsSignatureBeforeQuotedText;
    }

    public synchronized boolean isNotifySelfNewMail() {
        return mNotifySelfNewMail;
    }

    public synchronized void setNotifySelfNewMail(boolean notifySelfNewMail) {
        mNotifySelfNewMail = notifySelfNewMail;
    }

    public synchronized String getExpungePolicy() {
        return mExpungePolicy;
    }

    public synchronized void setExpungePolicy(String expungePolicy) {
        mExpungePolicy = expungePolicy;
    }

    public synchronized int getMaxPushFolders() {
        return mMaxPushFolders;
    }

    public synchronized boolean setMaxPushFolders(int maxPushFolders) {
        int oldMaxPushFolders = mMaxPushFolders;
        mMaxPushFolders = maxPushFolders;
        return oldMaxPushFolders != maxPushFolders;
    }


    public Store getRemoteStore() throws MessagingException {
        return Store.getRemoteInstance(this);
    }

    // It'd be great if this actually went into the store implementation
    // to get this, but that's expensive and not easily accessible
    // during initialization
    public boolean isSearchByDateCapable() {
        return (getStoreUri().startsWith("imap"));
    }


    @Override
    public synchronized String toString() {
        return mDescription;
    }

    public synchronized void setCompression(String networkType, boolean useCompression) {
        compressionMap.put(networkType, useCompression);
    }

    public synchronized boolean useCompression(String networkType) {
        Boolean useCompression = compressionMap.get(networkType);
        if (useCompression == null) {
            return true;
        }

        return useCompression;
    }

    public boolean useCompression(int type) {
        String networkType = TYPE_OTHER;
        switch (type) {
        case ConnectivityManager.TYPE_MOBILE:
            networkType = TYPE_MOBILE;
            break;
        case ConnectivityManager.TYPE_WIFI:
            networkType = TYPE_WIFI;
            break;
        }
        return useCompression(networkType);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Account) {
            return ((Account)o).mUuid.equals(mUuid);
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return mUuid.hashCode();
    }

    public synchronized Searchable getSearchableFolders() {
        return searchableFolders;
    }

    public synchronized void setSearchableFolders(Searchable searchableFolders) {
        this.searchableFolders = searchableFolders;
    }

    public synchronized int getIdleRefreshMinutes() {
        return mIdleRefreshMinutes;
    }

    public synchronized void setIdleRefreshMinutes(int idleRefreshMinutes) {
        mIdleRefreshMinutes = idleRefreshMinutes;
    }

    public synchronized boolean isPushPollOnConnect() {
        return mPushPollOnConnect;
    }

    public synchronized void setPushPollOnConnect(boolean pushPollOnConnect) {
        mPushPollOnConnect = pushPollOnConnect;
    }


    public synchronized boolean goToUnreadMessageSearch() {
        return goToUnreadMessageSearch;
    }

    public synchronized void setGoToUnreadMessageSearch(boolean goToUnreadMessageSearch) {
        this.goToUnreadMessageSearch = goToUnreadMessageSearch;
    }

    public boolean isNotificationShowsUnreadCount() {
        return mNotificationShowsUnreadCount;
    }

    public void setNotificationShowsUnreadCount(boolean notificationShowsUnreadCount) {
        this.mNotificationShowsUnreadCount = notificationShowsUnreadCount;
    }

    public synchronized boolean subscribedFoldersOnly() {
        return subscribedFoldersOnly;
    }

    public synchronized void setSubscribedFoldersOnly(boolean subscribedFoldersOnly) {
        this.subscribedFoldersOnly = subscribedFoldersOnly;
    }

    public synchronized int getMaximumPolledMessageAge() {
        return maximumPolledMessageAge;
    }

    public synchronized void setMaximumPolledMessageAge(int maximumPolledMessageAge) {
        this.maximumPolledMessageAge = maximumPolledMessageAge;
    }

    public synchronized int getMaximumAutoDownloadMessageSize() {
        return maximumAutoDownloadMessageSize;
    }

    public synchronized void setMaximumAutoDownloadMessageSize(int maximumAutoDownloadMessageSize) {
        this.maximumAutoDownloadMessageSize = maximumAutoDownloadMessageSize;
    }

    public Date getEarliestPollDate() {
        int age = getMaximumPolledMessageAge();
        if (age >= 0) {
            Calendar now = Calendar.getInstance();
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            now.set(Calendar.MILLISECOND, 0);
            if (age < 28) {
                now.add(Calendar.DATE, age * -1);
            } else switch (age) {
                case 28:
                    now.add(Calendar.MONTH, -1);
                    break;
                case 56:
                    now.add(Calendar.MONTH, -2);
                    break;
                case 84:
                    now.add(Calendar.MONTH, -3);
                    break;
                case 168:
                    now.add(Calendar.MONTH, -6);
                    break;
                case 365:
                    now.add(Calendar.YEAR, -1);
                    break;
                }

            return now.getTime();
        }

        return null;
    }

    public MessageFormat getMessageFormat() {
        return mMessageFormat;
    }

    public void setMessageFormat(MessageFormat messageFormat) {
        this.mMessageFormat = messageFormat;
    }

    public synchronized boolean isMessageReadReceiptAlways() {
        return mMessageReadReceipt;
    }

    public synchronized void setMessageReadReceipt(boolean messageReadReceipt) {
        mMessageReadReceipt = messageReadReceipt;
    }

    public QuoteStyle getQuoteStyle() {
        return mQuoteStyle;
    }

    public void setQuoteStyle(QuoteStyle quoteStyle) {
        this.mQuoteStyle = quoteStyle;
    }

    public synchronized String getQuotePrefix() {
        return mQuotePrefix;
    }

    public synchronized void setQuotePrefix(String quotePrefix) {
        mQuotePrefix = quotePrefix;
    }

    public synchronized boolean isDefaultQuotedTextShown() {
        return mDefaultQuotedTextShown;
    }

    public synchronized void setDefaultQuotedTextShown(boolean shown) {
        mDefaultQuotedTextShown = shown;
    }

    public synchronized boolean isReplyAfterQuote() {
        return mReplyAfterQuote;
    }

    public synchronized void setReplyAfterQuote(boolean replyAfterQuote) {
        mReplyAfterQuote = replyAfterQuote;
    }

    public synchronized boolean isStripSignature() {
        return mStripSignature;
    }

    public synchronized void setStripSignature(boolean stripSignature) {
        mStripSignature = stripSignature;
    }

    public String getCryptoApp() {
        return mCryptoApp;
    }


    public boolean getCryptoAutoSignature() {
        return mCryptoAutoSignature;
    }

    public void setCryptoAutoSignature(boolean cryptoAutoSignature) {
        mCryptoAutoSignature = cryptoAutoSignature;
    }

    public boolean isCryptoAutoEncrypt() {
        return mCryptoAutoEncrypt;
    }

    public void setCryptoAutoEncrypt(boolean cryptoAutoEncrypt) {
        mCryptoAutoEncrypt = cryptoAutoEncrypt;
    }

    public boolean allowRemoteSearch() {
        return mAllowRemoteSearch;
    }

    public void setAllowRemoteSearch(boolean val) {
        mAllowRemoteSearch = val;
    }

    public int getRemoteSearchNumResults() {
        return mRemoteSearchNumResults;
    }

    public void setRemoteSearchNumResults(int val) {
        mRemoteSearchNumResults = (val >= 0 ? val : 0);
    }

    public String getInboxFolderName() {
        return mInboxFolderName;
    }

    public void setInboxFolderName(String mInboxFolderName) {
        this.mInboxFolderName = mInboxFolderName;
    }

    public synchronized boolean syncRemoteDeletions() {
        return mSyncRemoteDeletions;
    }

    public synchronized void setSyncRemoteDeletions(boolean syncRemoteDeletions) {
        mSyncRemoteDeletions = syncRemoteDeletions;
    }

    public synchronized String getLastSelectedFolderName() {
        return lastSelectedFolderName;
    }

    public synchronized void setLastSelectedFolderName(String folderName) {
        lastSelectedFolderName = folderName;
    }

    /**
     * @return <code>true</code> if our {@link StorageProvider} is ready. (e.g.
     *         card inserted)
     */
    public boolean isAvailable(Context context) {
        String localStorageProviderId = getLocalStorageProviderId();
        if (localStorageProviderId == null) {
            return true; // defaults to internal memory
        }
        return StorageManager.getInstance(K9.app).isReady(localStorageProviderId);
    }

    public synchronized boolean isEnabled() {
        return mEnabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public synchronized boolean isMarkMessageAsReadOnView() {
        return mMarkMessageAsReadOnView;
    }

    public synchronized void setMarkMessageAsReadOnView(boolean value) {
        mMarkMessageAsReadOnView = value;
    }

    public synchronized boolean isAlwaysShowCcBcc() {
        return mAlwaysShowCcBcc;
    }

    public synchronized void setAlwaysShowCcBcc(boolean show) {
        mAlwaysShowCcBcc = show;
    }
    public boolean isRemoteSearchFullText() {
        return false;   // Temporarily disabled
        //return mRemoteSearchFullText;
    }

    public void setRemoteSearchFullText(boolean val) {
        mRemoteSearchFullText = val;
    }

    /**
     * Examine the settings for the account and attempt to delete (possibly non-existent)
     * certificates for the incoming and outgoing servers.
     */
    public void deleteCertificates() {
        LocalKeyStore localKeyStore = LocalKeyStore.getInstance();

        Uri uri = Uri.parse(getStoreUri());
        localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
        uri = Uri.parse(getTransportUri());
        localKeyStore.deleteCertificate(uri.getHost(), uri.getPort());
    }

    @Override
    public String getEmail() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setEmail(String email) {
        // TODO Auto-generated method stub

    }
}
