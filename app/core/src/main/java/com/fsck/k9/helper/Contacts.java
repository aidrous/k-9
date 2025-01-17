package com.fsck.k9.helper;


import java.util.HashMap;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.fsck.k9.mail.Address;
import timber.log.Timber;

/**
 * Helper class to access the contacts stored on the device.
 */
public class Contacts {
    /**
     * The order in which the search results are returned by
     * {@link #getContactByAddress(String)}.
     */
    protected static final String SORT_ORDER =
            ContactsContract.CommonDataKinds.Email.TIMES_CONTACTED + " DESC, " +
                    ContactsContract.Contacts.DISPLAY_NAME + ", " +
                    ContactsContract.CommonDataKinds.Email._ID;

    /**
     * Array of columns to load from the database.
     */
    protected static final String PROJECTION[] = {
            ContactsContract.CommonDataKinds.Email._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            Photo.PHOTO_URI,
            ContactsContract.Contacts.LOOKUP_KEY
    };

    /**
     * Index of the name field in the projection. This must match the order in
     * {@link #PROJECTION}.
     */
    protected static final int NAME_INDEX = 1;

    /**
     * Index of the contact id field in the projection. This must match the order in
     * {@link #PROJECTION}.
     */
    protected static final int CONTACT_ID_INDEX = 2;

    protected static final int LOOKUP_KEY_INDEX = 4;


    /**
     * Get instance of the Contacts class.
     *
     * <p>Note: This is left over from the days when we needed to have SDK-specific code to access
     * the contacts.</p>
     *
     * @param context A {@link Context} instance.
     * @return Appropriate {@link Contacts} instance for this device.
     */
    public static Contacts getInstance(Context context) {
        return new Contacts(context);
    }


    protected Context mContext;
    protected ContentResolver mContentResolver;
    private static HashMap<String, String> nameCache = new HashMap<>();


    /**
     * Constructor
     *
     * @param context A {@link Context} instance.
     */
    protected Contacts(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }


    /**
     * Check whether the provided email address belongs to one of the contacts.
     *
     * @param emailAddress The email address to look for.
     * @return <tt>true</tt>, if the email address belongs to a contact.
     *         <tt>false</tt>, otherwise.
     */
    public boolean isInContacts(final String emailAddress) {
        boolean result = false;

        final Cursor c = getContactByAddress(emailAddress);

        if (c != null) {
            if (c.getCount() > 0) {
                result = true;
            }
            c.close();
        }

        return result;
    }

    /**
     * Check whether one of the provided addresses belongs to one of the contacts.
     *
     * @param addresses The addresses to search in contacts
     * @return <tt>true</tt>, if one address belongs to a contact.
     *         <tt>false</tt>, otherwise.
     */
    public boolean isAnyInContacts(final Address[] addresses) {
        if (addresses == null) {
            return false;
        }

        for (Address addr : addresses) {
            if (isInContacts(addr.getAddress())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public Uri getContactUri(String emailAddress) {
        Cursor cursor = getContactByAddress(emailAddress);
        if (cursor == null) {
            return null;
        }

        try (cursor) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            long contactId = cursor.getLong(CONTACT_ID_INDEX);
            String lookupKey = cursor.getString(LOOKUP_KEY_INDEX);
            return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
        }
    }

    /**
     * Get the name of the contact an email address belongs to.
     *
     * @param address The email address to search for.
     * @return The name of the contact the email address belongs to. Or
     *      <tt>null</tt> if there's no matching contact.
     */
    public String getNameForAddress(String address) {
        if (address == null) {
            return null;
        } else if (nameCache.containsKey(address)) {
            return nameCache.get(address);
        }

        final Cursor c = getContactByAddress(address);

        String name = null;
        if (c != null) {
            if (c.getCount() > 0) {
                c.moveToFirst();
                name = c.getString(NAME_INDEX);
            }
            c.close();
        }

        nameCache.put(address, name);
        return name;
    }

    /**
     * Mark contacts with the provided email addresses as contacted.
     */
    public void markAsContacted(final Address[] addresses) {
        //TODO: Keep track of this information in a local database. Then use this information when sorting contacts for
        // auto-completion.
    }


    /**
     * Get URI to the picture of the contact with the supplied email address.
     *
     * @param address
     *         An email address. The contact database is searched for a contact with this email
     *         address.
     *
     * @return URI to the picture of the contact with the supplied email address. {@code null} if
     *         no such contact could be found or the contact doesn't have a picture.
     */
    public Uri getPhotoUri(String address) {
        try {
            final Cursor c = getContactByAddress(address);
            if (c == null) {
                return null;
            }

            try {
                if (!c.moveToFirst()) {
                    return null;
                }
                int columnIndex = c.getColumnIndex(Photo.PHOTO_URI);
                final String uriString = c.getString(columnIndex);
                if (uriString == null)
                    return null;
                return Uri.parse(uriString);
            } catch (IllegalStateException e) {
                return null;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            Timber.e(e, "Couldn't fetch photo for contact with email %s", address);
            return null;
        }
    }

    private boolean hasContactPermission() {
        return ContextCompat.checkSelfPermission(mContext,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Return a {@link Cursor} instance that can be used to fetch information
     * about the contact with the given email address.
     *
     * @param address The email address to search for.
     * @return A {@link Cursor} instance that can be used to fetch information
     *         about the contact with the given email address
     */
    private Cursor getContactByAddress(final String address) {
        final Uri uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(address));

        if (hasContactPermission()) {
            return mContentResolver.query(
                    uri,
                    PROJECTION,
                    null,
                    null,
                    SORT_ORDER);
        } else {
            return new EmptyCursor();
        }
    }

    /**
     * Clears the cache for names and photo uris
     */
    public static void clearCache() {
        nameCache.clear();
    }

}
