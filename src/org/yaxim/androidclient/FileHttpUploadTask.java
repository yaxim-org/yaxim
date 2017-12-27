package org.yaxim.androidclient;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DataForm;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.yaxim.androidclient.packet.httpupload.Request;
import org.yaxim.androidclient.packet.httpupload.Slot;
import org.yaxim.androidclient.service.Smackable;
import org.yaxim.androidclient.util.FileHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;

public class FileHttpUploadTask extends AsyncTask<Void, Void, String> {
    private static final String TAG = "yaxim.FileHttpUpload";

    private Smackable smackable;
    private Uri path;
    private File file;
    private String user;
    private String text;
    private boolean ismuc;
    private int maxSize = 0;

    public FileHttpUploadTask(Context ctx, Smackable smackable, Uri path, String user, String text, boolean ismuc) {
        this.smackable = smackable;
        this.path = path;
        this.user = user;
        this.text = text;
        this.ismuc = ismuc;
        this.file = new File(FileHelper.getPath(ctx, path));
    }

    @Override
    protected String doInBackground(Void... params) {
        if (path == null) {
            log("Error: path is null");
            return null;
        }

        String service = null;

        if (file.exists()) {
            XMPPConnection connection = smackable.getConnection();
            try {
                ServiceDiscoveryManager serviceDiscoveryManager = ServiceDiscoveryManager.getInstanceFor(connection);
                DiscoverItems items = serviceDiscoveryManager.discoverItems(StringUtils.parseServer(StringUtils.parseServer(connection.getUser())));

                Iterator<DiscoverItems.Item> it = items.getItems();
                if (it.hasNext()) {
                    while (it.hasNext() && service == null) {
                        DiscoverItems.Item item = it.next();
                        String jid = item.getEntityID();
                        DiscoverInfo info = serviceDiscoveryManager.discoverInfo(jid);
                        Iterator<DiscoverInfo.Identity> identities = info.getIdentities();
                        while(identities.hasNext()) {
                            DiscoverInfo.Identity identity = identities.next();
                            if (identity.getCategory().equals("store") && identity.getType().equals("file")) {
                                service = jid;
                            }
                        }
                        if (service != null) {
                            DataForm dataForm = (DataForm) info.getExtension("x", "jabber:x:data");
                            if (dataForm != null) {
                                Iterator<FormField> fields = dataForm.getFields();
                                while(fields.hasNext()) {
                                    FormField field = fields.next();
                                    if (field.getVariable().equals("max-file-size")) {
                                        try {
                                            maxSize = Integer.parseInt(field.getValues().next());
                                        } catch (NumberFormatException nfe) {
                                            maxSize = 0;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log("Error discovering service: " + e.getLocalizedMessage());
                return null;
            }

            if (service == null) {
                log("Server no support http upload.");
                return null;
            }

            if (maxSize > 0 && file.length() > maxSize) {
                log("Large file");
                return null;
            }

            String id = "HFU_SLOT" + System.currentTimeMillis();
            String mimeType = URLConnection.guessContentTypeFromName(file.getName());

            Request request = new Request(file.getName(), String.valueOf(file.length()), mimeType);
            request.setPacketID(id);
            request.setTo(service);
            request.setType(IQ.Type.GET);

            PacketCollector collector = connection.createPacketCollector(new PacketIDFilter(id));

            connection.sendPacket(request);

            IQ iq = (IQ) collector.nextResult(10000);
            if (iq != null) {
                if (iq.getType() == IQ.Type.ERROR) {
                    log(iq.toXML());
                    return null;
                } else {
                    Slot slot = (Slot) iq;
                    String putUrl = slot.getPutUrl();
                    String getUrl = slot.getGetUrl();

                    HttpURLConnection conn = null;

                    try {
                        conn = (HttpURLConnection) new URL(putUrl).openConnection();
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setRequestMethod("PUT");

                        try {
                            DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                            byte[] bytes = readFile(file);
                            out.write(bytes, 0, bytes.length);
                            out.flush();
                            out.close();
                        } catch (Exception e) {
                            log("Error sending file");
                            return null;
                        }

                        int responseCode = conn.getResponseCode();
                        if (responseCode != 200 && responseCode != 201) {
                            log("Error uploading file");
                        } else {
                            return getUrl;
                        }
                    } catch (Exception e) {
                        log(e.getLocalizedMessage());
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }
            } else {
                log("SLOT is NULL");
            }
            collector.cancel();
            return null;
        } else {
            log("File not found");
            return null;
        }
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(String result) {
        if (result == null || result.equals("")) return;

        String message = result;
        if (text != null && !text.equals("")) message = text + "\n" + message;

        if (!ismuc) smackable.sendMessage(user, message);
        else smackable.sendMucMessage(user, message);
    }

    private byte[] readFile(File file) throws IOException {
        // Open file
        RandomAccessFile f = new RandomAccessFile(file, "r");
        try {
            // Get and check length
            long longlength = f.length();
            int length = (int) longlength;
            if (length != longlength)
                throw new IOException("File size >= 2 GB");
            // Read file and return data
            byte[] data = new byte[length];
            f.readFully(data);
            return data;
        } finally {
            f.close();
        }
    }

    private void log(String message) {
        Log.e(TAG, message);
    }

}