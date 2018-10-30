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

public class FileHttpUploadTask extends AsyncTask<Void, String, FileHttpUploadTask.UploadResponse> {
    private static final String TAG = "yaxim.FileHttpUpload";

    public static final int F_RESIZE = 1;

    private Context ctx;
    private YaximConfiguration config;
    private Smackable smackable;
    private Uri path;
    private String user;
    private int flags;
    private Toast status;

    public FileHttpUploadTask(Context ctx, YaximConfiguration config, Smackable smackable, Uri path, String user, int flags) {
        this.ctx = ctx;
        this.config = config;
        this.smackable = smackable;
        this.path = path;
        this.user = user;
        this.flags = flags;
    }

    private void publishProgress(int res_id) {
        publishProgress(ctx.getString(res_id));
    }
    @Override
    protected UploadResponse doInBackground(Void... params) {
        try {
            if (path == null) {
                return failResponse("path is null");
            }
            FileHelper.FileInfo fi = FileHelper.getFileInfo(ctx, path);

            if (fi == null || fi.size == 0)
                return failResponse("File not found");

            XMPPConnection connection = smackable.getConnection();

            if (config.fileUploadDomain == null) {
                return failResponse("No server support");
            }

			byte[] bytes = null;
            try {
                if ((flags & F_RESIZE) != 0) {
                    publishProgress(R.string.upload_compress);
                    bytes = FileHelper.shrinkPicture(ctx, path, config.fileUploadSizeLimit);
                }
                if (bytes == null)
                    bytes = readFile(path, fi.size);
                if (config.fileUploadSizeLimit > 0 && bytes.length > config.fileUploadSizeLimit) {
                    return failResponse(ctx.getString(R.string.upload_too_large));
                }
            } catch (Exception e) {
                return failResponse(e);
            }

            Request request = new Request(fi.displayName, String.valueOf(bytes.length), fi.mimeType);
            request.setTo(config.fileUploadDomain);
            request.setType(IQ.Type.GET);

            PacketCollector collector = connection.createPacketCollector(new PacketIDFilter(request.getPacketID()));

            publishProgress(R.string.upload_uploading);
            connection.sendPacket(request);

            IQ iq = (IQ) collector.nextResult(10000);
            if (iq != null) {
                if (iq.getType() == IQ.Type.ERROR) {
                    log(iq.toXML());
                    return failResponse(iq.getError().getMessage());
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
                        conn.setRequestProperty("Content-Type", fi.mimeType);

						DataOutputStream out = new DataOutputStream(conn.getOutputStream());
						out.write(bytes, 0, bytes.length);
						out.flush();
						out.close();

                        int responseCode = conn.getResponseCode();
                        if (responseCode != 200 && responseCode != 201) {
                            return failResponse(new Throwable("HTTP Status Code " + responseCode));
                        } else {
                            return new UploadResponse(true, getUrl);
                        }
                    } catch (Exception e) {
                        log(e.getLocalizedMessage());
                        return failResponse(e);
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
            return failResponse("Timeout uploading");
        } catch (Exception e) {
			return failResponse(e);
		}
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if (status != null)
            status.cancel();
        status = Toast.makeText(ctx, values[0], Toast.LENGTH_LONG);
        status.show();
    }

    @Override
    protected void onPreExecute() { }

    @Override
    protected void onPostExecute(UploadResponse response) {
        if (status != null)
            status.cancel();
        if (response.success) {
            String message = response.response;
            smackable.sendMessage(user, message, null, message, -1);
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

    private UploadResponse failResponse(String reason) {
        return new UploadResponse(false, reason);
    }
    private UploadResponse failResponse(Throwable exception) {
        exception.printStackTrace();
        return new UploadResponse(false, exception.getLocalizedMessage());
    }


    public class UploadResponse {
        boolean success;
        String response;

        public UploadResponse(boolean success, String response) {
            this.success = success;
            this.response = response;
            if (!success)
                Log.e("yaxim.HttpUpload", this.toString());
        }

        public String toString() {
            if (success)
                return response;
            else return ctx.getString(R.string.conn_error, response);
        }
    }
}