package mobisocial.nfc.legacy;

import org.json.JSONArray;
import org.json.JSONException;

import com.android.apps.tag.record.UriRecord;

import mobisocial.ndefexchange.PendingNdefExchange;
import mobisocial.nfc.ConnectionHandoverManager;
import mobisocial.nfc.NdefHandler;
import mobisocial.nfc.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

/**
 * An inefficient but functional activity for storing devices'
 * names and NDEF handover addresses.
 *
 */
public class FriendsActivity extends ListActivity {
    private static final String TAG = "friendDef";
    private static final int ADD_FRIEND_QR = 0;
    private static final int DIALOG_NAME = 0;
    private SharedPreferences mPreferences;
    private String mScannedNdefString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = getSharedPreferences("main", 0);
        setContentView(R.layout.friends);
        setListAdapter(getFriendsListAdapter());
        findViewById(R.id.add_friend).setOnClickListener(mAddFriend);
    }

    View.OnClickListener mAddFriend = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent("com.google.zxing.client.android.SCAN");
            intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
            try {
                startActivityForResult(intent, ADD_FRIEND_QR);
            } catch (Exception e) {
                toast("Please install the barcode scanner.");
            }
        }
    };

    private NdefHandler mNdefHandler = new NdefHandler() {
        @Override
        public int handleNdef(NdefMessage[] ndefMessages) {
            startActivityForNdef(ndefMessages);
            return NDEF_CONSUME;
        }
    };

    private void startActivityForNdef(NdefMessage[] ndefMessages) {
        NdefRecord firstRecord = ndefMessages[0].getRecords()[0];
        Log.d(TAG, "DISCOVERED NDEF " + new String(firstRecord.getPayload()));

        if (UriRecord.isUri(firstRecord)) {
            UriRecord uriRecord = UriRecord.parse(firstRecord);
            Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED, uriRecord.getUri());
            intent.putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, ndefMessages);
            if (null == getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                toast("Could not handle ndef.");
                return;
            }
            startActivity(intent);
        } else {
            toast("Ndef launching needs work.");
        }
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        toast("Grabbing NDEF content...");
        NdefMessage handover = getHandover(position);
        new PendingNdefExchange(handover, mNdefHandler).exchangeNdef(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == ADD_FRIEND_QR) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    String data = intent.getStringExtra("SCAN_RESULT");
                    if (!data.startsWith(ConnectionHandoverManager.USER_HANDOVER_PREFIX)) {
                        throw new Exception();
                    }
                    // make sure it parses
                    new NdefMessage(android.util.Base64.decode(
                            data.substring(ConnectionHandoverManager.USER_HANDOVER_PREFIX.length()),
                            android.util.Base64.URL_SAFE));
                    mScannedNdefString = data.substring(ConnectionHandoverManager.USER_HANDOVER_PREFIX.length());
                    showDialog(DIALOG_NAME);
                } catch (Exception e) {
                    toast("QR code is not a vNFC tag.");
                }
            }
        }
    }

    // TODO: Proper backend.
    private ListAdapter getFriendsListAdapter() {
        String[] mItems;
        String namesStr = mPreferences.getString("names", null);
        if (namesStr == null) {
            mItems = new String[0];
        } else {
            try {
                JSONArray arr = new JSONArray(namesStr);
                mItems = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) {
                    mItems[i] = arr.getString(i);
                }
            } catch (JSONException e) {
                mItems = new String[0];
            }
        }
        return new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mItems);
    }

    private void saveFriend(String name, String handoverStr) {
        try {
            JSONArray names;
            JSONArray handovers;

            String namesStr = mPreferences.getString("names", null);
            String handoversStr = mPreferences.getString("handovers", null);
            if (namesStr == null) {
                names = new JSONArray();
                handovers = new JSONArray();
            } else {
                names = new JSONArray(namesStr);
                handovers = new JSONArray(handoversStr);
            }
            names.put(name);
            handovers.put(handoverStr);

            Editor edit = mPreferences.edit();
            edit.putString("names", names.toString());
            edit.putString("handovers", handovers.toString());
            edit.commit();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private NdefMessage getHandover(int pos) {
        String listStr = mPreferences.getString("handovers", null);
        if (listStr == null) {
            return null;
        }

        try {
            JSONArray handovers = new JSONArray(listStr);
            if (handovers.length() <= pos) {
                return null;
            }

            String handoverStr = handovers.getString(pos);
            return new NdefMessage(android.util.Base64.decode(handoverStr, android.util.Base64.URL_SAFE));
        } catch (FormatException e) {
            throw new IllegalArgumentException(e);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        switch(id) {
        case DIALOG_NAME:
            final EditText input = new EditText(this);
            dialog = new AlertDialog.Builder(this)
                .setTitle("Enter name")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            saveFriend(input.getText().toString(), mScannedNdefString);
                            setListAdapter(getFriendsListAdapter());
                            mScannedNdefString = null;
                        } catch (Exception e) {
                            toast("Error parsing qr code.");
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mScannedNdefString = null;
                    }
                })
                .setView(input)
                .create();
            break;
        default:
            dialog = null;
        }
        return dialog;
    }

    private void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FriendsActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }
}
