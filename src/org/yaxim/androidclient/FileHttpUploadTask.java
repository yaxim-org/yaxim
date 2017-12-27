package org.yaxim.androidclient;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.IQ;
import org.yaxim.androidclient.data.YaximConfiguration;
import org.yaxim.androidclient.packet.httpupload.Request;
import org.yaxim.androidclient.packet.httpupload.Slot;
import org.yaxim.androidclient.service.Smackable;
import org.yaxim.androidclient.util.FileHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileHttpUploadTask extends AsyncTask<Void, Void, FileHttpUploadTask.UploadResponse> {
    private static final String TAG = "yaxim.FileHttpUpload";

    public static final int F_RESIZE = 1;

    private Context ctx;
    private YaximConfiguration config;
    private Smackable smackable;
    private Uri path;
    private String user;
    private String text;
    private int flags;

    public FileHttpUploadTask(Context ctx, YaximConfiguration config, Smackable smackable, Uri path, String user, String text, int flags) {
        this.ctx = ctx;
        this.config = config;
        this.smackable = smackable;
        this.path = path;
        this.user = user;
        this.text = text;
        this.flags = flags;
    }

    @Override
    protected UploadResponse doInBackground(Void... params) {
        try {
            if (path == null) {
                return failResponse("path is null", null);
            }
            FileHelper.FileInfo fi = FileHelper.getFileInfo(ctx, path);

            if (fi == null)
                return failResponse("File not found", null);

            XMPPConnection connection = smackable.getConnection();

            if (config.fileUploadDomain == null) {
                return failResponse("No server support", null);
            }

            if (config.fileUploadSizeLimit > 0 && fi.size > config.fileUploadSizeLimit) {
                return failResponse("File too large", null);
            }

            Request request = new Request(fi.displayName, String.valueOf(fi.size), fi.mimeType);
            request.setTo(config.fileUploadDomain);
            request.setType(IQ.Type.GET);

            PacketCollector collector = connection.createPacketCollector(new PacketIDFilter(request.getPacketID()));

            connection.sendPacket(request);

            IQ iq = (IQ) collector.nextResult(10000);
            if (iq != null) {
                if (iq.getType() == IQ.Type.ERROR) {
                    log(iq.toXML());
                    return failResponse(iq.getError().getMessage(), null);
                } else {
                    Slot slot = (Slot) iq;
                    String putUrl = slot.getPutUrl();
                    String getUrl = slot.getGetUrl();

                    HttpURLConnection conn = null;

                    try {
                        byte[] bytes = null;
                        if ((flags & F_RESIZE) != 0)
                            bytes = FileHelper.shrinkPicture(ctx, path);
                        if (bytes == null)
							bytes = readFile(path, fi.size);

                        conn = (HttpURLConnection) new URL(putUrl).openConnection();
                        conn.setDoOutput(true);
                        conn.setDoInput(true);
                        conn.setUseCaches(false);
                        conn.setRequestMethod("PUT");

						DataOutputStream out = new DataOutputStream(conn.getOutputStream());
						out.write(bytes, 0, bytes.length);
						out.flush();
						out.close();

                        int responseCode = conn.getResponseCode();
                        if (responseCode != 200 && responseCode != 201) {
                            return failResponse("Error uploading", new Throwable("HTTP Status Code " + responseCode));
                        } else {
                            return new UploadResponse(true, getUrl, null);
                        }
                    } catch (Exception e) {
                        log(e.getLocalizedMessage());
                        return failResponse("Error uploading", e);
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
            return failResponse("Timeout uploading", null);
        } catch (Exception e) {
			return failResponse("Error uploading", e);
		}
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(UploadResponse response) {
        if (response.success) {
            String message = response.response;
            if (text != null && !text.equals("")) message = text + "\n" + message;

            smackable.sendMessage(user, message);
        } else {
            Toast.makeText(ctx, response.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readFile(Uri path, long size) throws IOException {
        int length = (int) size;
        if (length != size)
            throw new IOException("File size >= 2 GB");
        InputStream is = ctx.getContentResolver().openInputStream(path);
        try {
            // Read file and return data
            byte[] data = new byte[length];
            int offset = 0;
            while (offset < length)
				offset += is.read(data, offset, length-offset);
            return data;
        } finally {
            is.close();
        }
    }

    private void log(String message) {
        Log.e(TAG, message);
    }

    private UploadResponse failResponse(String reason, Throwable exception) {
        return new UploadResponse(false, reason, exception);
    }
    private UploadResponse failResponse(int reason_id, Throwable exception) {
        return new UploadResponse(false, ctx.getString(reason_id), exception);
    }


    public class UploadResponse {
        boolean success;
        String response;
        Throwable exception;

        public UploadResponse(boolean success, String response, Throwable exception) {
            this.success = success;
            this.response = response;
            this.exception = exception;
            if (!success)
                Log.e("yaxim.HttpUpload", this.toString());
        }

        public String toString() {
            if (success || exception == null)
                return response;
            else return String.format("%s: %s", response, exception.getLocalizedMessage());
        }
    }
}